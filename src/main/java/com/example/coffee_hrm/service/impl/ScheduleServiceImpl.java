package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.ApprovalStatus;
import com.example.coffee_hrm.common.enums.AssignmentStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.request.AssignShiftRequest;
import com.example.coffee_hrm.dto.request.CreateShiftChangeRequestDto;
import com.example.coffee_hrm.dto.response.WeeklyScheduleView;
import com.example.coffee_hrm.entity.*;
import com.example.coffee_hrm.repository.*;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ScheduleServiceImpl implements ScheduleService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DAY_MONTH_FORMATTER = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final StoreRepository storeRepository;
    private final ShiftRepository shiftRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final ShiftChangeRequestRepository shiftChangeRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public WeeklyScheduleView getWeeklyScheduleForManager(AuthenticatedUser user, LocalDate dateInWeek) {
        Store store = resolveManagerStore(user);
        LocalDate monday = resolveMonday(dateInWeek);
        LocalDate sunday = monday.plusDays(6);

        List<Shift> shifts = shiftRepository.findByStore_IdOrderByStartTimeAsc(store.getId());
        List<ShiftAssignment> assignments = shiftAssignmentRepository.findWeeklyAssignmentsForStore(
                store.getId(), monday, sunday, AssignmentStatus.ASSIGNED);

        List<Employee> activeEmployees = employeeRepository.findByStore_IdAndStatus(store.getId(), EmployeeStatus.ACTIVE);
        List<ShiftChangeRequest> pendingRequests = shiftChangeRequestRepository.findByStoreAndStatus(
                store.getId(), ApprovalStatus.PENDING);

        return buildWeeklyScheduleView(store, monday, sunday, shifts, assignments, activeEmployees, pendingRequests, List.of(), user, true);
    }

    @Override
    @Transactional(readOnly = true)
    public WeeklyScheduleView getWeeklyScheduleForStaff(AuthenticatedUser user, LocalDate dateInWeek) {
        Employee currentEmployee = resolveStaffEmployee(user);
        Store store = currentEmployee.getStore();
        if (store == null) {
            throw new BusinessException("Nhân viên chưa được liên kết với cửa hàng nào.");
        }

        LocalDate monday = resolveMonday(dateInWeek);
        LocalDate sunday = monday.plusDays(6);

        List<Shift> shifts = shiftRepository.findByStore_IdOrderByStartTimeAsc(store.getId());
        List<ShiftAssignment> assignments = shiftAssignmentRepository.findWeeklyAssignmentsForStore(
                store.getId(), monday, sunday, AssignmentStatus.ASSIGNED);

        List<Employee> activeEmployees = employeeRepository.findByStore_IdAndStatus(store.getId(), EmployeeStatus.ACTIVE);
        List<ShiftChangeRequest> staffRequests = shiftChangeRequestRepository.findAllByEmployee(currentEmployee.getId());
        List<ShiftChangeRequest> incomingRequests = shiftChangeRequestRepository.findIncomingRequestsForEmployee(currentEmployee.getId());

        return buildWeeklyScheduleView(store, monday, sunday, shifts, assignments, activeEmployees, staffRequests, incomingRequests, user, false);
    }

    @Override
    @Transactional
    public void assignShift(AssignShiftRequest request, AuthenticatedUser user) {
        Store store = resolveManagerStore(user);

        Shift shift = shiftRepository.findByIdAndStore_Id(request.getShiftId(), store.getId())
                .orElseThrow(() -> new BusinessException("Ca làm việc không tồn tại hoặc không thuộc cửa hàng bạn quản lý."));

        Employee employee = employeeRepository.findById(request.getEmployeeId())
                .orElseThrow(() -> new BusinessException("Nhân viên không tồn tại."));

        if (!Objects.equals(employee.getStore().getId(), store.getId())) {
            throw new BusinessException("Nhân viên không thuộc cửa hàng này.");
        }

        if (employee.getStatus() != EmployeeStatus.ACTIVE) {
            throw new BusinessException("Chỉ có thể phân ca cho nhân viên đang hoạt động (Active).");
        }

        if (shiftAssignmentRepository.existsByEmployee_IdAndShift_IdAndWorkDateAndStatus(
                employee.getId(), shift.getId(), request.getWorkDate(), AssignmentStatus.ASSIGNED)) {
            throw new BusinessException("Nhân viên " + employee.getFullName() + " đã được phân công vào ca này trong ngày "
                    + request.getWorkDate().format(DATE_FORMATTER) + ".");
        }

        // Kiểm tra xem nhân viên đã có ca làm việc nào khác bị trùng giờ trong cùng ngày không
        List<ShiftAssignment> existingSameDay = shiftAssignmentRepository.findByEmployee_IdAndWorkDateAndStatus(
                employee.getId(), request.getWorkDate(), AssignmentStatus.ASSIGNED);

        for (ShiftAssignment existing : existingSameDay) {
            Shift otherShift = existing.getShift();
            boolean overlaps = shift.getStartTime().isBefore(otherShift.getEndTime())
                    && shift.getEndTime().isAfter(otherShift.getStartTime());
            if (overlaps) {
                throw new BusinessException("Nhân viên " + employee.getFullName() + " đã có ca ("
                        + otherShift.getShiftName() + " " + otherShift.getStartTime().format(TIME_FORMATTER)
                        + " - " + otherShift.getEndTime().format(TIME_FORMATTER) + ") trùng giờ trong ngày "
                        + request.getWorkDate().format(DATE_FORMATTER) + ".");
            }
        }

        ShiftAssignment assignment = ShiftAssignment.builder()
                .shift(shift)
                .employee(employee)
                .workDate(request.getWorkDate())
                .status(AssignmentStatus.ASSIGNED)
                .isPublished(false)
                .build();

        shiftAssignmentRepository.save(assignment);
    }

    @Override
    @Transactional
    public void cancelAssignment(Integer assignmentId, AuthenticatedUser user) {
        Store store = resolveManagerStore(user);

        ShiftAssignment assignment = shiftAssignmentRepository.findByIdWithDetails(assignmentId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy phân công ca làm việc."));

        if (!Objects.equals(assignment.getShift().getStore().getId(), store.getId())) {
            throw new BusinessException("Bạn không có quyền quản lý ca làm việc của cửa hàng này.");
        }

        shiftAssignmentRepository.delete(assignment);
    }

    @Override
    @Transactional
    public void publishWeeklySchedule(LocalDate weekStartDate, AuthenticatedUser user) {
        Store store = resolveManagerStore(user);
        LocalDate monday = resolveMonday(weekStartDate);
        LocalDate sunday = monday.plusDays(6);

        List<ShiftAssignment> assignments = shiftAssignmentRepository.findAssignmentsToPublish(store.getId(), monday, sunday);
        if (assignments.isEmpty()) {
            throw new BusinessException("Không có ca làm việc nào trong tuần từ "
                    + monday.format(DATE_FORMATTER) + " đến " + sunday.format(DATE_FORMATTER) + " để công bố.");
        }

        LocalDateTime now = LocalDateTime.now();
        for (ShiftAssignment assignment : assignments) {
            assignment.setIsPublished(true);
            assignment.setPublishedAt(now);
        }

        shiftAssignmentRepository.saveAll(assignments);
    }

    @Override
    @Transactional
    public void requestShiftChange(CreateShiftChangeRequestDto request, AuthenticatedUser user) {
        Employee currentEmployee = resolveStaffEmployee(user);

        ShiftAssignment assignment = shiftAssignmentRepository.findByIdWithDetails(request.getAssignmentId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy ca làm việc."));

        if (!Objects.equals(assignment.getEmployee().getId(), currentEmployee.getId())) {
            throw new BusinessException("Bạn chỉ có thể gửi yêu cầu đổi cho ca làm việc của chính mình.");
        }

        if (!Boolean.TRUE.equals(assignment.getIsPublished())) {
            throw new BusinessException("Ca làm việc này chưa được quản lý công bố, không thể yêu cầu đổi ca.");
        }

        if (assignment.getWorkDate().isBefore(LocalDate.now())) {
            throw new BusinessException("Không thể gửi yêu cầu đổi cho ca làm việc đã qua.");
        }

        if (shiftChangeRequestRepository.existsByAssignment_IdAndStatus(assignment.getId(), ApprovalStatus.PENDING)) {
            throw new BusinessException("Ca làm việc này đang có một yêu cầu đổi ca chờ quản lý duyệt.");
        }

        String changeType = request.getChangeType() != null ? request.getChangeType().trim() : "SWAP";
        ShiftAssignment targetAssignment = null;
        Employee targetEmployee = null;
        String reasonPrefix = "";

        if ("SWAP".equalsIgnoreCase(changeType)) {
            if (request.getTargetAssignmentId() == null) {
                throw new BusinessException("Vui lòng chọn ca làm việc của đồng nghiệp mà bạn muốn đổi chéo cùng.");
            }
            targetAssignment = shiftAssignmentRepository.findByIdWithDetails(request.getTargetAssignmentId())
                    .orElseThrow(() -> new BusinessException("Không tìm thấy ca làm việc của đồng nghiệp được chọn."));

            if (Objects.equals(targetAssignment.getId(), assignment.getId())) {
                throw new BusinessException("Không thể đổi chéo với chính ca làm việc của mình.");
            }
            if (Objects.equals(targetAssignment.getEmployee().getId(), currentEmployee.getId())) {
                throw new BusinessException("Bạn không thể chọn đổi chéo với một ca khác cũng do chính bạn đảm nhiệm.");
            }
            if (!Objects.equals(targetAssignment.getShift().getStore().getId(), assignment.getShift().getStore().getId())) {
                throw new BusinessException("Ca làm việc của đồng nghiệp không thuộc cùng một cửa hàng.");
            }
            if (!Boolean.TRUE.equals(targetAssignment.getIsPublished())) {
                throw new BusinessException("Ca làm việc của đồng nghiệp chưa được công bố.");
            }
            targetEmployee = targetAssignment.getEmployee();
            reasonPrefix = "[Đổi chéo ca với: " + targetEmployee.getFullName() + " - "
                    + targetAssignment.getShift().getShiftName() + " ("
                    + targetAssignment.getWorkDate().format(DATE_FORMATTER) + ")] ";
        } else if ("TRANSFER".equalsIgnoreCase(changeType)) {
            if (request.getTargetEmployeeId() == null) {
                throw new BusinessException("Vui lòng chọn đồng nghiệp sẽ nhận ca thay bạn.");
            }
            targetEmployee = employeeRepository.findById(request.getTargetEmployeeId())
                    .orElseThrow(() -> new BusinessException("Không tìm thấy đồng nghiệp nhận ca."));

            if (Objects.equals(targetEmployee.getId(), currentEmployee.getId())) {
                throw new BusinessException("Bạn không thể chuyển ca cho chính mình.");
            }
            if (!Objects.equals(targetEmployee.getStore().getId(), currentEmployee.getStore().getId())) {
                throw new BusinessException("Đồng nghiệp được chọn không cùng cửa hàng.");
            }
            reasonPrefix = "[Chuyển ca cho: " + targetEmployee.getFullName() + " nhận thay] ";
        } else {
            reasonPrefix = "[Nhờ Quản lý sắp xếp người thay thế] ";
        }

        String fullReason = reasonPrefix + request.getReason().trim();
        Boolean isTargetAgreed = "MANAGER_ASSIGN".equalsIgnoreCase(changeType) ? Boolean.TRUE : null;
        LocalDateTime targetAgreedAt = "MANAGER_ASSIGN".equalsIgnoreCase(changeType) ? LocalDateTime.now() : null;

        ShiftChangeRequest changeRequest = ShiftChangeRequest.builder()
                .assignment(assignment)
                .employee(currentEmployee)
                .targetAssignment(targetAssignment)
                .targetEmployee(targetEmployee)
                .isTargetAgreed(isTargetAgreed)
                .targetAgreedAt(targetAgreedAt)
                .reason(fullReason)
                .status(ApprovalStatus.PENDING)
                .build();

        shiftChangeRequestRepository.save(changeRequest);
    }

    @Override
    @Transactional
    public void respondToIncomingShiftChange(Integer requestId, boolean agreed, AuthenticatedUser user) {
        Employee currentEmployee = resolveStaffEmployee(user);

        ShiftChangeRequest changeRequest = shiftChangeRequestRepository.findByIdWithDetails(requestId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy yêu cầu đổi ca."));

        if (changeRequest.getTargetEmployee() == null
                || !Objects.equals(changeRequest.getTargetEmployee().getId(), currentEmployee.getId())) {
            throw new BusinessException("Bạn không phải là người nhận trong yêu cầu đổi ca này.");
        }

        if (changeRequest.getStatus() != ApprovalStatus.PENDING || changeRequest.getIsTargetAgreed() != null) {
            throw new BusinessException("Yêu cầu này đã được phản hồi trước đó hoặc không còn ở trạng thái chờ.");
        }

        changeRequest.setTargetAgreedAt(LocalDateTime.now());

        if (agreed) {
            changeRequest.setIsTargetAgreed(true);
            // Giữ status = PENDING để chuyển tiếp đơn lên cho Quản lý cửa hàng duyệt
        } else {
            changeRequest.setIsTargetAgreed(false);
            changeRequest.setStatus(ApprovalStatus.REJECTED);
            changeRequest.setReason(changeRequest.getReason() + " [Đồng nghiệp từ chối hoán đổi]");
        }

        shiftChangeRequestRepository.save(changeRequest);
    }

    @Override
    @Transactional
    public void resolveShiftChange(Integer requestId, boolean approved, Integer replacementEmployeeId, AuthenticatedUser user) {
        Store store = resolveManagerStore(user);
        User managerUser = userRepository.findById(user.getUserId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy tài khoản quản lý."));

        ShiftChangeRequest changeRequest = shiftChangeRequestRepository.findByIdWithDetails(requestId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy yêu cầu đổi ca."));

        if (!Objects.equals(changeRequest.getEmployee().getStore().getId(), store.getId())) {
            throw new BusinessException("Yêu cầu này không thuộc nhân viên của cửa hàng bạn quản lý.");
        }

        if (changeRequest.getStatus() != ApprovalStatus.PENDING) {
            throw new BusinessException("Yêu cầu này đã được xử lý trước đó.");
        }

        if (!approved) {
            changeRequest.setStatus(ApprovalStatus.REJECTED);
            changeRequest.setResolvedBy(managerUser);
            changeRequest.setResolvedDate(LocalDateTime.now());
            shiftChangeRequestRepository.save(changeRequest);
            return;
        }

        ShiftAssignment assignment = changeRequest.getAssignment();
        Employee requester = changeRequest.getEmployee();

        // 1. Trường hợp ĐỔI CHÉO CA (Swap 2 chiều)
        if (changeRequest.getTargetAssignment() != null) {
            ShiftAssignment targetAssignment = changeRequest.getTargetAssignment();
            Employee targetEmp = targetAssignment.getEmployee();

            // Hoán đổi nhân viên giữa 2 ca
            assignment.setEmployee(targetEmp);
            targetAssignment.setEmployee(requester);

            shiftAssignmentRepository.save(assignment);
            shiftAssignmentRepository.save(targetAssignment);
        }
        // 2. Trường hợp CHUYỂN CA (Nhường ca 1 chiều)
        else if (changeRequest.getTargetEmployee() != null) {
            Employee targetEmp = changeRequest.getTargetEmployee();
            if (replacementEmployeeId != null) {
                targetEmp = employeeRepository.findById(replacementEmployeeId)
                        .orElseThrow(() -> new BusinessException("Không tìm thấy nhân viên thay thế được chỉ định."));
            }
            if (!Objects.equals(targetEmp.getStore().getId(), store.getId())) {
                throw new BusinessException("Nhân viên thay thế không thuộc cửa hàng này.");
            }
            if (targetEmp.getStatus() != com.example.coffee_hrm.common.enums.EmployeeStatus.ACTIVE) {
                throw new BusinessException("Nhân viên thay thế hiện không ở trạng thái làm việc.");
            }
            if (!Objects.equals(targetEmp.getId(), requester.getId())
                    && shiftAssignmentRepository.existsByEmployee_IdAndShift_IdAndWorkDateAndStatus(
                            targetEmp.getId(), assignment.getShift().getId(), assignment.getWorkDate(), AssignmentStatus.ASSIGNED)) {
                throw new BusinessException("Nhân viên " + targetEmp.getFullName() + " đã có ca làm việc này trên lịch rồi.");
            }
            assignment.setEmployee(targetEmp);
            shiftAssignmentRepository.save(assignment);
            changeRequest.setTargetEmployee(targetEmp);
        }
        // 3. Trường hợp NHỜ QUẢN LÝ TỰ SẮP XẾP
        else {
            if (replacementEmployeeId == null) {
                throw new BusinessException("Vui lòng chọn nhân viên thay thế vào ca này trước khi duyệt.");
            }
            Employee replacementEmp = employeeRepository.findById(replacementEmployeeId)
                    .orElseThrow(() -> new BusinessException("Không tìm thấy nhân viên thay thế được chỉ định."));

            if (!Objects.equals(replacementEmp.getStore().getId(), store.getId())) {
                throw new BusinessException("Nhân viên thay thế không thuộc cửa hàng này.");
            }
            if (replacementEmp.getStatus() != com.example.coffee_hrm.common.enums.EmployeeStatus.ACTIVE) {
                throw new BusinessException("Nhân viên thay thế hiện không ở trạng thái làm việc.");
            }
            if (shiftAssignmentRepository.existsByEmployee_IdAndShift_IdAndWorkDateAndStatus(
                    replacementEmp.getId(), assignment.getShift().getId(), assignment.getWorkDate(), AssignmentStatus.ASSIGNED)) {
                throw new BusinessException("Nhân viên " + replacementEmp.getFullName() + " đã có ca làm việc này trên lịch rồi.");
            }

            assignment.setEmployee(replacementEmp);
            shiftAssignmentRepository.save(assignment);
            changeRequest.setTargetEmployee(replacementEmp);
        }

        changeRequest.setStatus(ApprovalStatus.APPROVED);
        changeRequest.setResolvedBy(managerUser);
        changeRequest.setResolvedDate(LocalDateTime.now());
        shiftChangeRequestRepository.save(changeRequest);
    }

    private WeeklyScheduleView buildWeeklyScheduleView(
            Store store,
            LocalDate monday,
            LocalDate sunday,
            List<Shift> shifts,
            List<ShiftAssignment> assignments,
            List<Employee> activeEmployees,
            List<ShiftChangeRequest> requests,
            List<ShiftChangeRequest> incomingRequests,
            AuthenticatedUser user,
            boolean isManagerView) {

        LocalDate today = LocalDate.now();
        List<WeeklyScheduleView.DayHeader> dayHeaders = new ArrayList<>();
        String[] vietnameseDays = {"Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7", "Chủ nhật"};

        for (int i = 0; i < 7; i++) {
            LocalDate d = monday.plusDays(i);
            dayHeaders.add(WeeklyScheduleView.DayHeader.builder()
                    .date(d)
                    .dayOfWeekName(vietnameseDays[i])
                    .formattedDate(d.format(DAY_MONTH_FORMATTER))
                    .isToday(d.equals(today))
                    .build());
        }

        // Map assignments: key = ShiftId + "_" + WorkDate
        Map<String, List<WeeklyScheduleView.AssignmentItem>> assignmentMap = new HashMap<>();
        List<WeeklyScheduleView.ColleagueShiftOption> colleagueShifts = new ArrayList<>();
        Integer currentEmpId = user.getEmployeeId();

        long totalCount = 0;
        long publishedCount = 0;
        long draftCount = 0;

        for (ShiftAssignment sa : assignments) {
            totalCount++;
            boolean isPub = Boolean.TRUE.equals(sa.getIsPublished());
            if (isPub) {
                publishedCount++;
            } else {
                draftCount++;
            }

            boolean isCurrentStaff = user.getEmployeeId() != null
                    && Objects.equals(user.getEmployeeId(), sa.getEmployee().getId());

            WeeklyScheduleView.AssignmentItem item = WeeklyScheduleView.AssignmentItem.builder()
                    .assignmentId(sa.getId())
                    .employeeId(sa.getEmployee().getId())
                    .employeeName(sa.getEmployee().getFullName())
                    .employeePhone(sa.getEmployee().getPhone())
                    .status(sa.getStatus().getDbValue())
                    .isPublished(isPub)
                    .isCurrentStaff(isCurrentStaff)
                    .canCancel(isManagerView)
                    .canRequestChange(!isManagerView && isCurrentStaff && isPub && !sa.getWorkDate().isBefore(today))
                    .build();

            String key = sa.getShift().getId() + "_" + sa.getWorkDate().toString();
            assignmentMap.computeIfAbsent(key, k -> new ArrayList<>()).add(item);

            // Thu thập các ca của đồng nghiệp trong tuần để nhân viên có thể chọn đổi chéo
            boolean isColleague = currentEmpId != null && !Objects.equals(sa.getEmployee().getId(), currentEmpId);
            if (isPub && isColleague && !sa.getWorkDate().isBefore(today)) {
                String timeRange = sa.getShift().getStartTime().format(TIME_FORMATTER) + " – " + sa.getShift().getEndTime().format(TIME_FORMATTER);
                String formattedDate = sa.getWorkDate().format(DAY_MONTH_FORMATTER);
                String display = sa.getEmployee().getFullName() + " — " + sa.getShift().getShiftName()
                        + " (" + timeRange + ") ngày " + formattedDate;
                colleagueShifts.add(WeeklyScheduleView.ColleagueShiftOption.builder()
                        .assignmentId(sa.getId())
                        .employeeId(sa.getEmployee().getId())
                        .employeeName(sa.getEmployee().getFullName())
                        .shiftId(sa.getShift().getId())
                        .shiftName(sa.getShift().getShiftName())
                        .timeRange(timeRange)
                        .workDate(sa.getWorkDate())
                        .formattedDate(formattedDate)
                        .displayText(display)
                        .build());
            }
        }

        List<WeeklyScheduleView.ShiftRow> shiftRows = new ArrayList<>();
        for (Shift shift : shifts) {
            List<WeeklyScheduleView.DayCell> cells = new ArrayList<>();
            for (WeeklyScheduleView.DayHeader dh : dayHeaders) {
                String key = shift.getId() + "_" + dh.getDate().toString();
                List<WeeklyScheduleView.AssignmentItem> cellAssignments = assignmentMap.getOrDefault(key, List.of());
                cells.add(WeeklyScheduleView.DayCell.builder()
                        .date(dh.getDate())
                        .shiftId(shift.getId())
                        .assignments(cellAssignments)
                        .build());
            }

            shiftRows.add(WeeklyScheduleView.ShiftRow.builder()
                    .shiftId(shift.getId())
                    .shiftName(shift.getShiftName())
                    .startTime(shift.getStartTime())
                    .endTime(shift.getEndTime())
                    .formattedTime(shift.getStartTime().format(TIME_FORMATTER) + " – " + shift.getEndTime().format(TIME_FORMATTER))
                    .dayCells(cells)
                    .build());
        }

        List<WeeklyScheduleView.EmployeeOption> employeeOptions = activeEmployees.stream()
                .map(emp -> WeeklyScheduleView.EmployeeOption.builder()
                        .employeeId(emp.getId())
                        .fullName(emp.getFullName())
                        .email(emp.getEmail())
                        .build())
                .toList();

        List<WeeklyScheduleView.ShiftChangeItem> changeItems = requests.stream()
                .map(this::mapToShiftChangeItem)
                .toList();

        List<WeeklyScheduleView.ShiftChangeItem> incomingItems = incomingRequests != null
                ? incomingRequests.stream().map(this::mapToShiftChangeItem).toList()
                : List.of();

        return WeeklyScheduleView.builder()
                .storeId(store.getId())
                .storeName(store.getStoreName())
                .storeAddress(store.getAddress())
                .weekStartDate(monday)
                .weekEndDate(sunday)
                .prevWeekDate(monday.minusWeeks(1))
                .nextWeekDate(monday.plusWeeks(1))
                .days(dayHeaders)
                .shiftRows(shiftRows)
                .storeEmployees(employeeOptions)
                .colleagueShifts(colleagueShifts)
                .pendingShiftChanges(changeItems)
                .incomingRequests(incomingItems)
                .totalAssignments(totalCount)
                .publishedAssignments(publishedCount)
                .draftAssignments(draftCount)
                .fullyPublished(totalCount > 0 && draftCount == 0)
                .build();
    }

    private WeeklyScheduleView.ShiftChangeItem mapToShiftChangeItem(ShiftChangeRequest req) {
        String changeType = "MANAGER_ASSIGN";
        String changeTypeLabel = "Quản lý tự sắp";
        String proposalSummary = "Cần quản lý phân công người thay thế";

        if (req.getTargetAssignment() != null) {
            changeType = "SWAP";
            changeTypeLabel = "Đổi chéo ca";
            ShiftAssignment tsa = req.getTargetAssignment();
            proposalSummary = "Đổi chéo với: " + tsa.getEmployee().getFullName()
                    + " (" + tsa.getShift().getShiftName() + " " + tsa.getWorkDate().format(DAY_MONTH_FORMATTER) + ")";
        } else if (req.getTargetEmployee() != null) {
            changeType = "TRANSFER";
            changeTypeLabel = "Chuyển ca";
            proposalSummary = "Chuyển cho: " + req.getTargetEmployee().getFullName() + " nhận thay";
        }

        String stageLabel;
        if (req.getStatus() == ApprovalStatus.APPROVED) {
            stageLabel = "Quản lý đã duyệt";
        } else if (req.getStatus() == ApprovalStatus.REJECTED) {
            if (Boolean.FALSE.equals(req.getIsTargetAgreed())) {
                stageLabel = "Đồng nghiệp từ chối";
            } else {
                stageLabel = "Quản lý từ chối";
            }
        } else {
            if (req.getTargetEmployee() == null || "MANAGER_ASSIGN".equals(changeType)) {
                stageLabel = "Chờ Quản lý duyệt";
            } else if (Boolean.TRUE.equals(req.getIsTargetAgreed())) {
                stageLabel = "Đã đồng thuận — Chờ QL duyệt";
            } else {
                stageLabel = "Chờ đồng nghiệp đồng ý";
            }
        }

        return WeeklyScheduleView.ShiftChangeItem.builder()
                .requestId(req.getId())
                .assignmentId(req.getAssignment().getId())
                .employeeId(req.getEmployee().getId())
                .employeeName(req.getEmployee().getFullName())
                .shiftName(req.getAssignment().getShift().getShiftName())
                .workDate(req.getAssignment().getWorkDate())
                .timeRange(req.getAssignment().getShift().getStartTime().format(TIME_FORMATTER)
                        + " – " + req.getAssignment().getShift().getEndTime().format(TIME_FORMATTER))
                .reason(req.getReason())
                .status(req.getStatus().getDbValue())
                .requestDate(req.getRequestDate() != null ? req.getRequestDate().format(DATE_TIME_FORMATTER) : "—")
                .resolvedByName(req.getResolvedBy() != null ? req.getResolvedBy().getUsername() : null)
                .resolvedDate(req.getResolvedDate() != null ? req.getResolvedDate().format(DATE_TIME_FORMATTER) : null)
                .targetEmployeeId(req.getTargetEmployee() != null ? req.getTargetEmployee().getId() : null)
                .targetEmployeeName(req.getTargetEmployee() != null ? req.getTargetEmployee().getFullName() : null)
                .targetAssignmentId(req.getTargetAssignment() != null ? req.getTargetAssignment().getId() : null)
                .targetShiftName(req.getTargetAssignment() != null ? req.getTargetAssignment().getShift().getShiftName() : null)
                .targetWorkDate(req.getTargetAssignment() != null ? req.getTargetAssignment().getWorkDate() : null)
                .targetTimeRange(req.getTargetAssignment() != null
                        ? (req.getTargetAssignment().getShift().getStartTime().format(TIME_FORMATTER) + " – "
                        + req.getTargetAssignment().getShift().getEndTime().format(TIME_FORMATTER)) : null)
                .changeType(changeType)
                .changeTypeLabel(changeTypeLabel)
                .proposalSummary(proposalSummary)
                .isTargetAgreed(req.getIsTargetAgreed())
                .targetAgreedAt(req.getTargetAgreedAt() != null ? req.getTargetAgreedAt().format(DATE_TIME_FORMATTER) : null)
                .stageLabel(stageLabel)
                .build();
    }

    private Store resolveManagerStore(AuthenticatedUser user) {
        if (user.getEmployeeId() == null) {
            throw new BusinessException("Tài khoản quản lý chưa được gắn hồ sơ nhân viên.");
        }
        return storeRepository.findByManager_Id(user.getEmployeeId())
                .orElseThrow(() -> new BusinessException("Bạn chưa được phân công quản lý cửa hàng nào."));
    }

    private Employee resolveStaffEmployee(AuthenticatedUser user) {
        if (user.getEmployeeId() == null) {
            throw new BusinessException("Tài khoản chưa liên kết với hồ sơ nhân viên.");
        }
        return employeeRepository.findByIdWithStore(user.getEmployeeId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy thông tin hồ sơ nhân viên."));
    }

    private LocalDate resolveMonday(LocalDate input) {
        LocalDate date = input != null ? input : LocalDate.now();
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}

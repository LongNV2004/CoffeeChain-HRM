package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.ApprovalStatus;
import com.example.coffee_hrm.common.enums.AssignmentStatus;
import com.example.coffee_hrm.common.enums.CertificationStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.NotificationType;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.request.AssignShiftRequest;
import com.example.coffee_hrm.dto.request.CreateOvertimeRequestDto;
import com.example.coffee_hrm.dto.response.OvertimePageView;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.EmploymentContract;
import com.example.coffee_hrm.entity.OvertimeRequest;
import com.example.coffee_hrm.entity.Shift;
import com.example.coffee_hrm.entity.ShiftAssignment;
import com.example.coffee_hrm.entity.ShiftSlotLimit;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.entity.WorkAvailability;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.EmploymentContractRepository;
import com.example.coffee_hrm.repository.OvertimeRequestRepository;
import com.example.coffee_hrm.repository.ShiftAssignmentRepository;
import com.example.coffee_hrm.repository.ShiftRepository;
import com.example.coffee_hrm.repository.ShiftSlotLimitRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.repository.UserRepository;
import com.example.coffee_hrm.repository.WorkAvailabilityRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.security.StaffEmploymentGuard;
import com.example.coffee_hrm.service.NotificationService;
import com.example.coffee_hrm.service.OvertimeRequestService;
import com.example.coffee_hrm.service.ScheduleService;
import com.example.coffee_hrm.service.support.ApprovedLeaveCoverage;
import com.example.coffee_hrm.service.support.AvailabilityCoverage;
import com.example.coffee_hrm.service.support.ContractShiftQuota;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OvertimeRequestServiceImpl implements OvertimeRequestService {

    private static final DateTimeFormatter FULL_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final String[] VI_DAYS = {"Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7", "Chủ nhật"};
    private static final int MAX_SHIFTS_PER_DAY = 2;
    private static final String OVERTIME_REF = "OVERTIME_REQUEST";
    private static final List<ApprovalStatus> ACTIVE_OVERTIME_STATUSES =
            List.of(ApprovalStatus.PENDING, ApprovalStatus.APPROVED);

    private final OvertimeRequestRepository overtimeRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final StoreRepository storeRepository;
    private final ShiftRepository shiftRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final WorkAvailabilityRepository workAvailabilityRepository;
    private final ShiftSlotLimitRepository shiftSlotLimitRepository;
    private final EmploymentContractRepository employmentContractRepository;
    private final UserRepository userRepository;
    private final ApprovedLeaveCoverage approvedLeaveCoverage;
    private final NotificationService notificationService;
    private final ScheduleService scheduleService;

    @Override
    public OvertimePageView getStaffOvertimePage(AuthenticatedUser staff, LocalDate weekStart) {
        Employee employee = requireStaffEmployee(staff);
        Store store = employee.getStore();
        LocalDate currentMonday = currentWeekMonday();
        LocalDate monday = resolveStaffWeek(employee, weekStart, currentMonday);
        LocalDate sunday = monday.plusDays(6);

        List<EmploymentContract> contracts = contractsOf(employee.getId());
        ContractShiftQuota.WeekQuota quota = ContractShiftQuota.describe(contracts, monday);

        List<Shift> shifts = shiftRepository.findByStore_IdOrderByStartTimeAsc(store.getId());
        List<OvertimeRequest> allMyRequests = overtimeRequestRepository.findAllByEmployeeId(employee.getId());
        WeekShiftSnapshot weekSnapshot = buildEmployeeWeekSnapshot(employee.getId(), monday, sunday, allMyRequests);

        int regularInWeek = weekSnapshot.regularSlotKeys().size();
        int approvedOtInWeek = weekSnapshot.approvedOvertimeSlotKeys().size();
        int pendingOtInWeek = weekSnapshot.pendingOvertimeSlotKeys().size();
        Integer required = quota.required();
        boolean quotaReached = required != null && regularInWeek >= required;
        boolean certified = isCertified(employee);
        boolean canSubmitInWeek = certified && !quota.blocked() && quotaReached;

        String blockMessage = !certified
                ? StaffAvailabilityServiceImpl.NOT_CERTIFIED_MESSAGE
                : quota.blockMessage();
        String quotaStatusMessage = buildStaffQuotaStatusMessage(
                blockMessage, required, regularInWeek, approvedOtInWeek, pendingOtInWeek, monday);

        Map<String, Integer> limitBySlot = loadStoreLimits(store.getId());
        Map<String, Integer> occupiedBySlot = loadStoreOccupancyForWeek(store.getId(), monday, sunday);

        List<OvertimePageView.DayHeader> days = buildDayHeaders(monday);
        List<OvertimePageView.ShiftRow> shiftRows = buildStaffShiftRows(
                shifts, days, weekSnapshot, limitBySlot, occupiedBySlot, canSubmitInWeek);

        Map<LocalDate, WeekShiftSnapshot> snapshotCache = new HashMap<>();
        snapshotCache.put(monday, weekSnapshot);
        List<OvertimePageView.RequestItem> requestItems = allMyRequests.stream()
                .map(req -> toRequestItem(req, contracts, snapshotCache, limitBySlot, occupiedBySlot))
                .toList();

        return OvertimePageView.builder()
                .storeName(store.getStoreName())
                .employeeName(employee.getFullName())
                .weekStart(monday)
                .weekEnd(sunday)
                .previousWeek(monday.minusWeeks(1))
                .nextWeek(monday.plusWeeks(1))
                .currentWeek(currentMonday)
                .formattedWeekRange(formatWeekRange(monday, sunday))
                .contractWeeklyQuota(required)
                .contractBlockMessage(blockMessage)
                .regularShiftsInWeek(regularInWeek)
                .approvedOvertimeInWeek(approvedOtInWeek)
                .pendingOvertimeInWeek(pendingOtInWeek)
                .quotaReached(quotaReached)
                .canSubmitOvertimeInWeek(canSubmitInWeek)
                .quotaStatusMessage(quotaStatusMessage)
                .days(days)
                .shiftRows(shiftRows)
                .requests(requestItems)
                .totals(summarizeRequests(allMyRequests))
                .build();
    }

    @Override
    @Transactional
    public void submitOvertimeRequest(CreateOvertimeRequestDto request, AuthenticatedUser staff) {
        Employee employee = requireStaffEmployeeWithManager(staff);
        rejectIfEmploymentEnded(employee);
        employeeRepository.lockById(employee.getId());
        if (!isCertified(employee)) {
            throw new BusinessException(StaffAvailabilityServiceImpl.NOT_CERTIFIED_MESSAGE);
        }
        if (request == null || request.getShiftId() == null || request.getWorkDate() == null) {
            throw new BusinessException("Vui lòng chọn ngày và ca làm việc muốn xin tăng ca.");
        }
        String reason = request.getReason() != null ? request.getReason().trim() : "";
        if (reason.isEmpty()) {
            throw new BusinessException("Vui lòng nhập lý do xin tăng ca.");
        }
        if (reason.length() > 255) {
            reason = reason.substring(0, 255);
        }

        Store store = employee.getStore();
        Shift shift = shiftRepository.findByIdAndStore_Id(request.getShiftId(), store.getId())
                .orElseThrow(() -> new BusinessException("Ca làm việc không tồn tại hoặc không thuộc cửa hàng của bạn."));
        LocalDate workDate = request.getWorkDate();
        if (!isShiftStillUpcoming(workDate, shift.getStartTime())) {
            throw new BusinessException("Không thể gửi đơn xin tăng ca cho ca làm việc đã qua.");
        }

        List<EmploymentContract> contracts = contractsOf(employee.getId());
        String afterTermination = ContractShiftQuota.shiftAfterTermination(contracts, workDate);
        if (afterTermination != null) {
            throw new BusinessException(afterTermination);
        }
        ContractShiftQuota.WeekQuota quota = ContractShiftQuota.describe(contracts, workDate);
        if (quota.blocked()) {
            throw new BusinessException(quota.blockMessage());
        }

        LocalDate weekStart = workDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);
        List<OvertimeRequest> existingOvertimeInWeek = overtimeRequestRepository.findByEmployeeAndDateRangeAndStatuses(
                employee.getId(), weekStart, weekEnd, ACTIVE_OVERTIME_STATUSES);
        WeekShiftSnapshot weekSnapshot = buildEmployeeWeekSnapshot(
                employee.getId(), weekStart, weekEnd, existingOvertimeInWeek);

        int regularShiftsInWeek = weekSnapshot.regularSlotKeys().size();
        int requiredQuota = quota.required();
        if (regularShiftsInWeek < requiredQuota) {
            throw new BusinessException("Tuần " + formatWeekRange(weekStart, weekEnd)
                    + " bạn mới đăng ký " + regularShiftsInWeek + "/" + requiredQuota
                    + " ca theo hợp đồng. Nhân viên chỉ làm đơn xin tăng ca khi đã đăng ký đủ tối đa "
                    + requiredQuota + " ca/tuần theo hợp đồng tại mục Đăng kí lịch làm việc.");
        }

        String targetSlotKey = slotDateKey(shift.getId(), workDate);
        if (weekSnapshot.regularSlotKeys().contains(targetSlotKey)) {
            throw new BusinessException("Bạn đã có lịch làm việc hoặc đăng ký định kỳ cho "
                    + shift.getShiftName() + " ngày " + workDate.format(FULL_DATE) + ".");
        }
        if (overtimeRequestRepository.existsByEmployee_IdAndShift_IdAndWorkDateAndStatusIn(
                employee.getId(), shift.getId(), workDate, ACTIVE_OVERTIME_STATUSES)) {
            throw new BusinessException("Bạn đã gửi đơn xin tăng ca cho "
                    + shift.getShiftName() + " ngày " + workDate.format(FULL_DATE) + ".");
        }
        if (approvedLeaveCoverage != null
                && approvedLeaveCoverage.blocksShift(employee.getId(), shift.getId(), workDate)) {
            throw new BusinessException("Bạn đang có đơn nghỉ phép đã duyệt trùng vào "
                    + shift.getShiftName() + " ngày " + workDate.format(FULL_DATE) + ".");
        }

        List<Shift> shiftsOnSameDay = weekSnapshot.shiftsByDate().getOrDefault(workDate, List.of());
        for (Shift other : shiftsOnSameDay) {
            if (overlaps(shift, other)) {
                throw new BusinessException("Ca xin tăng ca trùng giờ với " + other.getShiftName()
                        + " (" + formatTimeRange(other) + ") bạn đã có trong ngày " + workDate.format(FULL_DATE) + ".");
            }
        }
        if (shiftsOnSameDay.size() >= MAX_SHIFTS_PER_DAY) {
            throw new BusinessException("Ngày " + workDate.format(FULL_DATE)
                    + " bạn đã có " + shiftsOnSameDay.size()
                    + " ca. Mỗi ngày chỉ được làm tối đa " + MAX_SHIFTS_PER_DAY + " ca (bao gồm cả ca tăng ca).");
        }

        int dayOfWeek = workDate.getDayOfWeek().getValue();
        ShiftSlotLimit slotLimit = shiftSlotLimitRepository.findByShift_IdAndDayOfWeek(shift.getId(), dayOfWeek)
                .orElse(null);
        if (slotLimit != null && slotLimit.getMaxEmployees() != null) {
            int occupied = countSlotOccupiedOnDate(store.getId(), shift.getId(), workDate);
            if (occupied >= slotLimit.getMaxEmployees()) {
                throw new BusinessException(shift.getShiftName() + " ngày " + workDate.format(FULL_DATE)
                        + " đã đủ số lượng nhân viên tối đa (" + occupied + "/" + slotLimit.getMaxEmployees() + ").");
            }
        }

        OvertimeRequest entity = OvertimeRequest.builder()
                .employee(employee)
                .store(store)
                .shift(shift)
                .workDate(workDate)
                .contractWeeklyQuota(requiredQuota)
                .reason(reason)
                .status(ApprovalStatus.PENDING)
                .build();
        OvertimeRequest saved = overtimeRequestRepository.save(entity);
        notifyStoreManagerOnSubmit(employee, saved);
    }

    @Override
    @Transactional
    public void cancelOvertimeRequest(Integer requestId, AuthenticatedUser staff) {
        Employee employee = requireStaffEmployee(staff);
        rejectIfEmploymentEnded(employee);
        OvertimeRequest request = overtimeRequestRepository.findByIdWithDetails(requestId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy đơn xin tăng ca."));
        if (!Objects.equals(request.getEmployee().getId(), employee.getId())) {
            throw new BusinessException("Bạn chỉ có thể hủy đơn xin tăng ca của chính mình.");
        }
        if (request.getStatus() != ApprovalStatus.PENDING) {
            throw new BusinessException("Chỉ có thể hủy đơn xin tăng ca khi đang ở trạng thái chờ duyệt.");
        }
        request.setStatus(ApprovalStatus.CANCELLED);
        overtimeRequestRepository.save(request);
    }

    @Override
    public OvertimePageView getManagerOvertimePage(AuthenticatedUser manager, LocalDate weekStart, String statusFilter) {
        Store store = resolveManagerStore(manager);
        LocalDate currentMonday = currentWeekMonday();
        LocalDate monday = weekStart == null
                ? currentMonday.plusWeeks(1)
                : weekStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sunday = monday.plusDays(6);

        List<OvertimeRequest> allStoreRequests = overtimeRequestRepository.findAllByStoreId(store.getId());
        OvertimePageView.SummaryTotals totals = summarizeRequests(allStoreRequests);

        Map<String, Integer> limitBySlot = loadStoreLimits(store.getId());
        Map<Integer, List<EmploymentContract>> contractsByEmp = new HashMap<>();
        Map<String, WeekShiftSnapshot> empWeekCache = new HashMap<>();

        List<OvertimePageView.RequestItem> filteredItems = new ArrayList<>();
        for (OvertimeRequest req : allStoreRequests) {
            if (!matchesStatusFilter(req.getStatus(), statusFilter)) {
                continue;
            }
            Integer empId = req.getEmployee().getId();
            List<EmploymentContract> contracts = contractsByEmp.computeIfAbsent(empId, this::contractsOf);
            LocalDate reqMonday = req.getWorkDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate reqSunday = reqMonday.plusDays(6);
            String cacheKey = empId + "_" + reqMonday;
            WeekShiftSnapshot snapshot = empWeekCache.computeIfAbsent(cacheKey, ignored ->
                    buildEmployeeWeekSnapshot(empId, reqMonday, reqSunday,
                            overtimeRequestRepository.findByEmployeeAndDateRangeAndStatuses(
                                    empId, reqMonday, reqSunday, ACTIVE_OVERTIME_STATUSES)));
            int dayOfWeek = req.getWorkDate().getDayOfWeek().getValue();
            Integer maxEmp = limitBySlot.get(req.getShift().getId() + ":" + dayOfWeek);
            int occupied = countSlotOccupiedOnDate(store.getId(), req.getShift().getId(), req.getWorkDate());
            filteredItems.add(buildRequestItem(req, contracts, snapshot, maxEmp, occupied));
        }

        return OvertimePageView.builder()
                .storeName(store.getStoreName())
                .employeeName(manager.getDisplayName())
                .weekStart(monday)
                .weekEnd(sunday)
                .previousWeek(monday.minusWeeks(1))
                .nextWeek(monday.plusWeeks(1))
                .currentWeek(currentMonday)
                .formattedWeekRange(formatWeekRange(monday, sunday))
                .days(buildDayHeaders(monday))
                .shiftRows(List.of())
                .requests(filteredItems)
                .totals(totals)
                .build();
    }

    @Override
    @Transactional
    public void reviewOvertimeRequest(Integer requestId,
                                      boolean approved,
                                      String reviewNote,
                                      AuthenticatedUser manager) {
        Store store = resolveManagerStore(manager);
        OvertimeRequest request = overtimeRequestRepository.findByIdWithDetails(requestId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy đơn xin tăng ca."));
        if (!Objects.equals(request.getStore().getId(), store.getId())) {
            throw new BusinessException("Đơn xin tăng ca không thuộc cửa hàng bạn quản lý.");
        }
        Employee employee = request.getEmployee();
        employeeRepository.lockById(employee.getId());
        request = overtimeRequestRepository.findByIdWithDetails(requestId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy đơn xin tăng ca."));
        if (request.getStatus() != ApprovalStatus.PENDING) {
            throw new BusinessException("Đơn xin tăng ca này đã được xử lý trước đó.");
        }

        String cleanedNote = cleanNote(reviewNote);
        User reviewer = manager.getUserId() != null
                ? userRepository.findById(manager.getUserId()).orElse(null)
                : null;

        if (!approved) {
            request.setStatus(ApprovalStatus.REJECTED);
            request.setReviewedBy(reviewer);
            request.setReviewedAt(VietnamTime.now());
            request.setReviewNote(cleanedNote);
            overtimeRequestRepository.save(request);
            notifyEmployeeOnReview(employee, request, false);
            return;
        }

        Shift shift = request.getShift();
        LocalDate workDate = request.getWorkDate();
        if (!isCertified(employee)) {
            throw new BusinessException(StaffAvailabilityServiceImpl.NOT_CERTIFIED_APPROVAL_MESSAGE);
        }
        if (!isShiftStillUpcoming(workDate, shift.getStartTime())) {
            throw new BusinessException("Không thể duyệt đơn xin tăng ca cho ca làm việc đã qua.");
        }

        int dayOfWeek = workDate.getDayOfWeek().getValue();
        ShiftSlotLimit slotLimit = shiftSlotLimitRepository.lockByShiftAndDay(shift.getId(), dayOfWeek)
                .orElse(null);
        if (slotLimit != null && slotLimit.getMaxEmployees() != null) {
            int occupied = countSlotOccupiedOnDate(store.getId(), shift.getId(), workDate);
            if (occupied >= slotLimit.getMaxEmployees()) {
                throw new BusinessException("Không thể duyệt vì " + shift.getShiftName()
                        + " ngày " + workDate.format(FULL_DATE)
                        + " đã đủ chỉ tiêu tối đa (" + occupied + "/" + slotLimit.getMaxEmployees() + " nhân viên).");
            }
        }

        LocalDate weekStart = workDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);
        List<OvertimeRequest> otherActiveOt = overtimeRequestRepository.findByEmployeeAndDateRangeAndStatuses(
                        employee.getId(), weekStart, weekEnd, List.of(ApprovalStatus.APPROVED))
                .stream()
                .filter(item -> !Objects.equals(item.getId(), requestId))
                .toList();
        WeekShiftSnapshot snapshot = buildEmployeeWeekSnapshot(employee.getId(), weekStart, weekEnd, otherActiveOt);
        List<Shift> shiftsOnDay = snapshot.shiftsByDate().getOrDefault(workDate, List.of());
        if (shiftsOnDay.size() >= MAX_SHIFTS_PER_DAY) {
            throw new BusinessException("Không thể duyệt vì nhân viên " + employee.getFullName()
                    + " đã có " + shiftsOnDay.size() + " ca trong ngày " + workDate.format(FULL_DATE)
                    + " (tối đa " + MAX_SHIFTS_PER_DAY + " ca/ngày).");
        }

        scheduleService.assignShift(
                AssignShiftRequest.builder()
                        .employeeId(employee.getId())
                        .shiftId(shift.getId())
                        .workDate(workDate)
                        .build(),
                manager);

        List<ShiftAssignment> sameDayAssignments = shiftAssignmentRepository.findByEmployee_IdAndWorkDateAndStatus(
                employee.getId(), workDate, AssignmentStatus.ASSIGNED);
        Integer createdAssignmentId = sameDayAssignments.stream()
                .filter(sa -> sa.getShift() != null && Objects.equals(sa.getShift().getId(), shift.getId()))
                .map(ShiftAssignment::getId)
                .findFirst()
                .orElse(null);

        request.setStatus(ApprovalStatus.APPROVED);
        request.setReviewedBy(reviewer);
        request.setReviewedAt(VietnamTime.now());
        request.setReviewNote(cleanedNote);
        request.setCreatedAssignmentId(createdAssignmentId);
        overtimeRequestRepository.save(request);
        notifyEmployeeOnReview(employee, request, true);
    }

    @Override
    @Transactional
    public int reviewAllPending(boolean approved, LocalDate weekStart, AuthenticatedUser manager) {
        Store store = resolveManagerStore(manager);
        List<OvertimeRequest> pending = overtimeRequestRepository.findAllByStoreId(store.getId()).stream()
                .filter(req -> req.getStatus() == ApprovalStatus.PENDING)
                .filter(req -> {
                    if (weekStart == null) {
                        return true;
                    }
                    LocalDate monday = weekStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                    LocalDate sunday = monday.plusDays(6);
                    return !req.getWorkDate().isBefore(monday) && !req.getWorkDate().isAfter(sunday);
                })
                .toList();
        if (pending.isEmpty()) {
            throw new BusinessException("Không có đơn xin tăng ca nào đang chờ duyệt.");
        }
        for (OvertimeRequest req : pending) {
            reviewOvertimeRequest(req.getId(), approved, null, manager);
        }
        return pending.size();
    }

    private LocalDate resolveStaffWeek(Employee employee, LocalDate requestedWeek, LocalDate currentMonday) {
        if (requestedWeek != null) {
            return requestedWeek.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        }
        LocalDate nextMonday = currentMonday.plusWeeks(1);
        WeekShiftSnapshot currentSnap = buildEmployeeWeekSnapshot(
                employee.getId(), currentMonday, currentMonday.plusDays(6), List.of());
        ContractShiftQuota.WeekQuota currentQuota = ContractShiftQuota.describe(contractsOf(employee.getId()), currentMonday);
        if (currentQuota.required() != null && currentSnap.regularSlotKeys().size() >= currentQuota.required()) {
            return currentMonday;
        }
        return nextMonday;
    }

    private WeekShiftSnapshot buildEmployeeWeekSnapshot(Integer employeeId,
                                                        LocalDate monday,
                                                        LocalDate sunday,
                                                        List<OvertimeRequest> overtimeRequests) {
        Set<String> approvedOvertimeSlotKeys = new LinkedHashSet<>();
        Set<String> pendingOvertimeSlotKeys = new LinkedHashSet<>();
        Map<LocalDate, Map<Integer, Shift>> shiftsByDateMap = new LinkedHashMap<>();

        if (overtimeRequests != null) {
            for (OvertimeRequest ot : overtimeRequests) {
                if (ot.getWorkDate() == null || ot.getShift() == null || ot.getShift().getId() == null) {
                    continue;
                }
                if (ot.getWorkDate().isBefore(monday) || ot.getWorkDate().isAfter(sunday)) {
                    continue;
                }
                String key = slotDateKey(ot.getShift().getId(), ot.getWorkDate());
                if (ot.getStatus() == ApprovalStatus.APPROVED) {
                    approvedOvertimeSlotKeys.add(key);
                    shiftsByDateMap.computeIfAbsent(ot.getWorkDate(), ignored -> new LinkedHashMap<>())
                            .put(ot.getShift().getId(), ot.getShift());
                } else if (ot.getStatus() == ApprovalStatus.PENDING) {
                    pendingOvertimeSlotKeys.add(key);
                    shiftsByDateMap.computeIfAbsent(ot.getWorkDate(), ignored -> new LinkedHashMap<>())
                            .put(ot.getShift().getId(), ot.getShift());
                }
            }
        }

        Set<String> regularSlotKeys = new LinkedHashSet<>();
        List<ShiftAssignment> assigned = shiftAssignmentRepository.findWeeklyAssignmentsForEmployee(
                employeeId, monday, sunday, AssignmentStatus.ASSIGNED);
        if (assigned != null) {
            for (ShiftAssignment sa : assigned) {
                if (sa.getShift() == null || sa.getShift().getId() == null || sa.getWorkDate() == null) {
                    continue;
                }
                if (approvedLeaveCoverage != null
                        && approvedLeaveCoverage.blocksShift(employeeId, sa.getShift().getId(), sa.getWorkDate())) {
                    continue;
                }
                String key = slotDateKey(sa.getShift().getId(), sa.getWorkDate());
                shiftsByDateMap.computeIfAbsent(sa.getWorkDate(), ignored -> new LinkedHashMap<>())
                        .put(sa.getShift().getId(), sa.getShift());
                if (!approvedOvertimeSlotKeys.contains(key)) {
                    regularSlotKeys.add(key);
                }
            }
        }

        List<WorkAvailability> availabilities = workAvailabilityRepository.findVisibleByEmployee(
                employeeId, VietnamTime.today(), ApprovalStatus.PENDING);
        if (availabilities != null) {
            for (WorkAvailability wa : availabilities) {
                if (wa.getShift() == null || wa.getShift().getId() == null) {
                    continue;
                }
                ApprovalStatus status = wa.getStatus() == null ? ApprovalStatus.PENDING : wa.getStatus();
                if (status != ApprovalStatus.APPROVED && status != ApprovalStatus.PENDING) {
                    continue;
                }
                for (int i = 0; i < 7; i++) {
                    LocalDate date = monday.plusDays(i);
                    if (!AvailabilityCoverage.appliesOn(wa, date)) {
                        continue;
                    }
                    if (approvedLeaveCoverage != null
                            && approvedLeaveCoverage.blocksShift(employeeId, wa.getShift().getId(), date)) {
                        continue;
                    }
                    String key = slotDateKey(wa.getShift().getId(), date);
                    shiftsByDateMap.computeIfAbsent(date, ignored -> new LinkedHashMap<>())
                            .put(wa.getShift().getId(), wa.getShift());
                    if (!approvedOvertimeSlotKeys.contains(key)) {
                        regularSlotKeys.add(key);
                    }
                }
            }
        }

        Map<LocalDate, List<Shift>> shiftsByDate = new LinkedHashMap<>();
        shiftsByDateMap.forEach((date, map) -> shiftsByDate.put(date, List.copyOf(map.values())));
        return new WeekShiftSnapshot(regularSlotKeys, approvedOvertimeSlotKeys, pendingOvertimeSlotKeys, shiftsByDate);
    }

    private List<OvertimePageView.DayHeader> buildDayHeaders(LocalDate monday) {
        LocalDate today = VietnamTime.today();
        List<OvertimePageView.DayHeader> days = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            LocalDate date = monday.plusDays(i);
            days.add(OvertimePageView.DayHeader.builder()
                    .dayOfWeek(i + 1)
                    .dayName(VI_DAYS[i])
                    .date(date)
                    .formattedDate(date.format(SHORT_DATE))
                    .today(date.isEqual(today))
                    .build());
        }
        return days;
    }

    private List<OvertimePageView.ShiftRow> buildStaffShiftRows(List<Shift> shifts,
                                                                List<OvertimePageView.DayHeader> days,
                                                                WeekShiftSnapshot snapshot,
                                                                Map<String, Integer> limitBySlot,
                                                                Map<String, Integer> occupiedBySlot,
                                                                boolean canSubmitInWeek) {
        List<OvertimePageView.ShiftRow> rows = new ArrayList<>();
        if (shifts == null) {
            return rows;
        }
        for (Shift shift : shifts) {
            String formattedTime = formatTimeRange(shift);
            List<OvertimePageView.SlotCell> cells = new ArrayList<>(days.size());
            for (OvertimePageView.DayHeader day : days) {
                LocalDate date = day.getDate();
                String slotKey = slotDateKey(shift.getId(), date);
                Integer maxEmp = limitBySlot.get(shift.getId() + ":" + day.getDayOfWeek());
                int occupied = occupiedBySlot.getOrDefault(slotKey, 0);
                String capacityLabel = maxEmp != null
                        ? occupied + "/" + maxEmp + " người"
                        : occupied + " người";

                boolean upcoming = isShiftStillUpcoming(date, shift.getStartTime());
                boolean isRegular = snapshot.regularSlotKeys().contains(slotKey);
                boolean isApprovedOt = snapshot.approvedOvertimeSlotKeys().contains(slotKey);
                boolean isPendingOt = snapshot.pendingOvertimeSlotKeys().contains(slotKey);
                List<Shift> shiftsOnDay = snapshot.shiftsByDate().getOrDefault(date, List.of());
                boolean dayMaxReached = shiftsOnDay.size() >= MAX_SHIFTS_PER_DAY;
                boolean timeOverlap = shiftsOnDay.stream().anyMatch(other ->
                        !Objects.equals(other.getId(), shift.getId()) && overlaps(shift, other));
                boolean slotFull = maxEmp != null && occupied >= maxEmp;

                String stateKey;
                String stateLabel;
                String stateHint;
                boolean selectable = false;

                if (isApprovedOt) {
                    stateKey = "ot-approved";
                    stateLabel = "Tăng ca đã duyệt";
                    stateHint = "Đã có trên lịch làm việc";
                } else if (isPendingOt) {
                    stateKey = "ot-pending";
                    stateLabel = "Chờ duyệt tăng ca";
                    stateHint = "Đang chờ Manager duyệt";
                } else if (isRegular) {
                    stateKey = "regular";
                    stateLabel = "Ca theo hợp đồng";
                    stateHint = "Đã đăng ký / phân công";
                } else if (!upcoming) {
                    stateKey = "past";
                    stateLabel = "Đã qua";
                    stateHint = "Không thể đăng ký";
                } else if (slotFull) {
                    stateKey = "full";
                    stateLabel = "Đầy chỗ";
                    stateHint = "Đã đủ chỉ tiêu nhân sự";
                } else if (dayMaxReached) {
                    stateKey = "day-limit";
                    stateLabel = "Đủ 2 ca/ngày";
                    stateHint = "Tối đa 2 ca mỗi ngày";
                } else if (timeOverlap) {
                    stateKey = "overlap";
                    stateLabel = "Trùng giờ";
                    stateHint = "Trùng giờ với ca khác trong ngày";
                } else if (!canSubmitInWeek) {
                    stateKey = "locked";
                    stateLabel = "Chưa đủ ca HĐ";
                    stateHint = "Cần đăng ký đủ số ca hợp đồng trước";
                } else {
                    stateKey = "open";
                    stateLabel = "Có thể xin tăng ca";
                    stateHint = "Nhấn để chọn ca này";
                    selectable = true;
                }

                cells.add(OvertimePageView.SlotCell.builder()
                        .shiftId(shift.getId())
                        .shiftName(shift.getShiftName())
                        .formattedTime(formattedTime)
                        .dayOfWeek(day.getDayOfWeek())
                        .dayName(day.getDayName())
                        .workDate(date)
                        .formattedDate(date.format(FULL_DATE))
                        .maxEmployees(maxEmp)
                        .occupiedCount(occupied)
                        .capacityLabel(capacityLabel)
                        .stateKey(stateKey)
                        .stateLabel(stateLabel)
                        .stateHint(stateHint)
                        .selectable(selectable)
                        .build());
            }
            rows.add(OvertimePageView.ShiftRow.builder()
                    .shiftId(shift.getId())
                    .shiftName(shift.getShiftName())
                    .formattedTime(formattedTime)
                    .cells(cells)
                    .build());
        }
        return rows;
    }

    private OvertimePageView.RequestItem toRequestItem(OvertimeRequest req,
                                                       List<EmploymentContract> contracts,
                                                       Map<LocalDate, WeekShiftSnapshot> snapshotCache,
                                                       Map<String, Integer> limitBySlot,
                                                       Map<String, Integer> occupiedBySlot) {
        LocalDate reqMonday = req.getWorkDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate reqSunday = reqMonday.plusDays(6);
        WeekShiftSnapshot snapshot = snapshotCache.computeIfAbsent(reqMonday, ignored ->
                buildEmployeeWeekSnapshot(req.getEmployee().getId(), reqMonday, reqSunday,
                        overtimeRequestRepository.findByEmployeeAndDateRangeAndStatuses(
                                req.getEmployee().getId(), reqMonday, reqSunday, ACTIVE_OVERTIME_STATUSES)));
        int dayOfWeek = req.getWorkDate().getDayOfWeek().getValue();
        Integer maxEmp = limitBySlot.get(req.getShift().getId() + ":" + dayOfWeek);
        String slotKey = slotDateKey(req.getShift().getId(), req.getWorkDate());
        int occupied = occupiedBySlot.containsKey(slotKey)
                ? occupiedBySlot.get(slotKey)
                : countSlotOccupiedOnDate(req.getStore().getId(), req.getShift().getId(), req.getWorkDate());
        return buildRequestItem(req, contracts, snapshot, maxEmp, occupied);
    }

    private OvertimePageView.RequestItem buildRequestItem(OvertimeRequest req,
                                                          List<EmploymentContract> contracts,
                                                          WeekShiftSnapshot snapshot,
                                                          Integer slotMaxEmployees,
                                                          int slotOccupiedCount) {
        ApprovalStatus status = req.getStatus() == null ? ApprovalStatus.PENDING : req.getStatus();
        Integer quota = req.getContractWeeklyQuota();
        if (quota == null) {
            quota = ContractShiftQuota.describe(contracts, req.getWorkDate()).required();
        }
        int dayIndex = req.getWorkDate().getDayOfWeek().getValue() - 1;
        String dayName = dayIndex >= 0 && dayIndex < VI_DAYS.length ? VI_DAYS[dayIndex] : "";
        boolean upcoming = isShiftStillUpcoming(req.getWorkDate(), req.getShift().getStartTime());

        return OvertimePageView.RequestItem.builder()
                .id(req.getId())
                .employeeId(req.getEmployee().getId())
                .employeeName(req.getEmployee().getFullName())
                .employeePhone(req.getEmployee().getPhone())
                .shiftId(req.getShift().getId())
                .shiftName(req.getShift().getShiftName())
                .shiftTime(formatTimeRange(req.getShift()))
                .workDate(req.getWorkDate())
                .formattedWorkDate(req.getWorkDate().format(FULL_DATE))
                .dayOfWeekName(dayName)
                .contractWeeklyQuota(quota)
                .employeeRegularShiftsInWeek(snapshot.regularSlotKeys().size())
                .employeeApprovedOvertimeInWeek(snapshot.approvedOvertimeSlotKeys().size())
                .slotMaxEmployees(slotMaxEmployees)
                .slotOccupiedCount(slotOccupiedCount)
                .reason(req.getReason())
                .statusKey(status.name().toLowerCase())
                .statusLabel(status.getLabel())
                .pending(status == ApprovalStatus.PENDING)
                .canCancel(status == ApprovalStatus.PENDING && upcoming)
                .reviewedByName(req.getReviewedBy() != null ? req.getReviewedBy().getUsername() : null)
                .reviewedAt(req.getReviewedAt())
                .formattedReviewedAt(req.getReviewedAt() != null ? req.getReviewedAt().format(DATE_TIME) : null)
                .reviewNote(req.getReviewNote())
                .createdAt(req.getCreatedAt())
                .formattedCreatedAt(req.getCreatedAt() != null ? req.getCreatedAt().format(DATE_TIME) : "")
                .build();
    }

    private String buildStaffQuotaStatusMessage(String blockMessage,
                                                Integer required,
                                                int regularInWeek,
                                                int approvedOtInWeek,
                                                int pendingOtInWeek,
                                                LocalDate monday) {
        if (blockMessage != null) {
            return blockMessage;
        }
        if (required == null) {
            return "Chưa xác định được định mức số ca/tuần trong hợp đồng.";
        }
        if (regularInWeek < required) {
            int missing = required - regularInWeek;
            return "Hợp đồng quy định tối đa " + required + " ca/tuần. Tuần "
                    + formatWeekRange(monday, monday.plusDays(6))
                    + " bạn mới đăng ký " + regularInWeek + "/" + required
                    + " ca (còn thiếu " + missing + " ca). Vui lòng đăng ký đủ "
                    + required + " ca tại mục Đăng kí lịch làm việc trước khi làm đơn xin tăng ca.";
        }
        String extra = "";
        if (approvedOtInWeek > 0 || pendingOtInWeek > 0) {
            extra = " (Đã duyệt +" + approvedOtInWeek + " ca tăng ca, chờ duyệt " + pendingOtInWeek + " ca).";
        }
        return "Bạn đã đăng ký đủ " + regularInWeek + "/" + required
                + " ca tối đa theo hợp đồng trong tuần này" + extra
                + " Bạn có thể chọn ca trống bên dưới để gửi đơn xin tăng ca.";
    }

    private Map<String, Integer> loadStoreLimits(Integer storeId) {
        Map<String, Integer> map = new HashMap<>();
        List<ShiftSlotLimit> limits = shiftSlotLimitRepository.findByStoreId(storeId);
        if (limits != null) {
            for (ShiftSlotLimit limit : limits) {
                if (limit.getShift() != null && limit.getShift().getId() != null
                        && limit.getDayOfWeek() != null && limit.getMaxEmployees() != null) {
                    map.put(limit.getShift().getId() + ":" + limit.getDayOfWeek(), limit.getMaxEmployees());
                }
            }
        }
        return map;
    }

    private Map<String, Integer> loadStoreOccupancyForWeek(Integer storeId, LocalDate monday, LocalDate sunday) {
        Map<String, Set<Integer>> employeesBySlot = new HashMap<>();
        List<ShiftAssignment> assigned = shiftAssignmentRepository.findWeeklyAssignmentsForStore(
                storeId, monday, sunday, AssignmentStatus.ASSIGNED);
        if (assigned != null) {
            for (ShiftAssignment sa : assigned) {
                if (sa.getShift() == null || sa.getEmployee() == null || sa.getWorkDate() == null) {
                    continue;
                }
                if (approvedLeaveCoverage != null
                        && approvedLeaveCoverage.blocksShift(sa.getEmployee().getId(), sa.getShift().getId(), sa.getWorkDate())) {
                    continue;
                }
                employeesBySlot.computeIfAbsent(slotDateKey(sa.getShift().getId(), sa.getWorkDate()), ignored -> new HashSet<>())
                        .add(sa.getEmployee().getId());
            }
        }
        List<WorkAvailability> approvedWa = workAvailabilityRepository.findApprovedCovering(
                storeId, monday, sunday, ApprovalStatus.APPROVED);
        if (approvedWa != null) {
            for (WorkAvailability wa : approvedWa) {
                if (wa.getShift() == null || wa.getEmployee() == null) {
                    continue;
                }
                for (int i = 0; i < 7; i++) {
                    LocalDate date = monday.plusDays(i);
                    if (!AvailabilityCoverage.appliesOn(wa, date)) {
                        continue;
                    }
                    if (approvedLeaveCoverage != null
                            && approvedLeaveCoverage.blocksShift(wa.getEmployee().getId(), wa.getShift().getId(), date)) {
                        continue;
                    }
                    employeesBySlot.computeIfAbsent(slotDateKey(wa.getShift().getId(), date), ignored -> new HashSet<>())
                            .add(wa.getEmployee().getId());
                }
            }
        }
        Map<String, Integer> counts = new HashMap<>();
        employeesBySlot.forEach((k, v) -> counts.put(k, v.size()));
        return counts;
    }

    private int countSlotOccupiedOnDate(Integer storeId, Integer shiftId, LocalDate workDate) {
        Map<String, Integer> weekOccupancy = loadStoreOccupancyForWeek(storeId, workDate, workDate);
        return weekOccupancy.getOrDefault(slotDateKey(shiftId, workDate), 0);
    }

    private OvertimePageView.SummaryTotals summarizeRequests(List<OvertimeRequest> requests) {
        int pending = 0;
        int approved = 0;
        int rejected = 0;
        int total = 0;
        if (requests != null) {
            for (OvertimeRequest req : requests) {
                ApprovalStatus status = req.getStatus() == null ? ApprovalStatus.PENDING : req.getStatus();
                if (status == ApprovalStatus.CANCELLED) {
                    continue;
                }
                total++;
                if (status == ApprovalStatus.PENDING) {
                    pending++;
                } else if (status == ApprovalStatus.APPROVED) {
                    approved++;
                } else if (status == ApprovalStatus.REJECTED) {
                    rejected++;
                }
            }
        }
        return OvertimePageView.SummaryTotals.builder()
                .total(total)
                .pending(pending)
                .approved(approved)
                .rejected(rejected)
                .build();
    }

    private boolean matchesStatusFilter(ApprovalStatus status, String filter) {
        ApprovalStatus effective = status == null ? ApprovalStatus.PENDING : status;
        if (filter == null || filter.isBlank() || "all".equalsIgnoreCase(filter)) {
            return effective != ApprovalStatus.CANCELLED;
        }
        return effective.name().equalsIgnoreCase(filter.trim());
    }

    private void notifyStoreManagerOnSubmit(Employee employee, OvertimeRequest request) {
        Store store = employee.getStore();
        if (store == null || store.getManager() == null || store.getManager().getId() == null) {
            return;
        }
        userRepository.findByEmployee_Id(store.getManager().getId()).ifPresent(managerUser -> {
            if (!Boolean.TRUE.equals(managerUser.getIsActive())) {
                return;
            }
            String message = employee.getFullName() + " đã gửi đơn xin tăng ca "
                    + request.getShift().getShiftName() + " ngày "
                    + request.getWorkDate().format(FULL_DATE)
                    + " (vượt định mức " + request.getContractWeeklyQuota() + " ca/tuần). Lý do: "
                    + request.getReason();
            notificationService.notifyUsers(
                    List.of(managerUser),
                    "Đơn xin tăng ca mới",
                    message,
                    NotificationType.OVERTIME_REQUEST_SUBMITTED,
                    OVERTIME_REF,
                    request.getId());
        });
    }

    private void notifyEmployeeOnReview(Employee employee, OvertimeRequest request, boolean approved) {
        if (employee == null || employee.getId() == null) {
            return;
        }
        userRepository.findByEmployee_Id(employee.getId()).ifPresent(user -> {
            if (!Boolean.TRUE.equals(user.getIsActive())) {
                return;
            }
            String noteSuffix = request.getReviewNote() != null && !request.getReviewNote().isBlank()
                    ? " Ghi chú quản lý: " + request.getReviewNote()
                    : "";
            String message = approved
                    ? "Quản lý đã duyệt đơn xin tăng ca " + request.getShift().getShiftName()
                    + " ngày " + request.getWorkDate().format(FULL_DATE)
                    + ". Ca làm việc đã được thêm vào Lịch làm việc của bạn." + noteSuffix
                    : "Quản lý đã từ chối đơn xin tăng ca " + request.getShift().getShiftName()
                    + " ngày " + request.getWorkDate().format(FULL_DATE) + "." + noteSuffix;
            notificationService.notifyUsers(
                    List.of(user),
                    approved ? "Đơn xin tăng ca đã được duyệt" : "Đơn xin tăng ca bị từ chối",
                    message,
                    approved ? NotificationType.OVERTIME_REQUEST_APPROVED : NotificationType.OVERTIME_REQUEST_REJECTED,
                    OVERTIME_REF,
                    request.getId());
        });
    }

    private List<EmploymentContract> contractsOf(Integer employeeId) {
        List<EmploymentContract> list = employmentContractRepository.findByEmployeeId(employeeId);
        return list == null ? List.of() : list;
    }

    private Employee requireStaffEmployee(AuthenticatedUser user) {
        if (user == null || user.getEmployeeId() == null) {
            throw new BusinessException("Tài khoản chưa được liên kết với hồ sơ nhân viên.");
        }
        Employee employee = employeeRepository.findByIdWithStore(user.getEmployeeId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy hồ sơ nhân viên."));
        if (employee.getStore() == null || employee.getStore().getId() == null) {
            throw new BusinessException("Nhân viên chưa được phân công cửa hàng.");
        }
        return employee;
    }

    private Employee requireStaffEmployeeWithManager(AuthenticatedUser user) {
        if (user == null || user.getEmployeeId() == null) {
            throw new BusinessException("Tài khoản chưa được liên kết với hồ sơ nhân viên.");
        }
        Employee employee = employeeRepository.findByIdWithStoreAndManager(user.getEmployeeId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy hồ sơ nhân viên."));
        if (employee.getStore() == null || employee.getStore().getId() == null) {
            throw new BusinessException("Nhân viên chưa được phân công cửa hàng.");
        }
        return employee;
    }

    private Store resolveManagerStore(AuthenticatedUser manager) {
        if (manager == null || manager.getEmployeeId() == null) {
            throw new BusinessException("Tài khoản quản lý chưa được gắn hồ sơ nhân viên.");
        }
        return storeRepository.findByManager_Id(manager.getEmployeeId())
                .orElseThrow(() -> new BusinessException("Bạn chưa được phân công quản lý cửa hàng nào."));
    }

    private static void rejectIfEmploymentEnded(Employee employee) {
        if (employee != null && employee.getStatus() == EmployeeStatus.TERMINATED) {
            throw new BusinessException(StaffEmploymentGuard.DENIED_MESSAGE);
        }
    }

    private static boolean isCertified(Employee employee) {
        return employee != null && employee.getCertificationStatus() == CertificationStatus.CERTIFIED;
    }

    private static boolean isShiftStillUpcoming(LocalDate workDate, LocalTime shiftStart) {
        if (workDate == null) {
            return false;
        }
        LocalDate today = VietnamTime.today();
        if (workDate.isAfter(today)) {
            return true;
        }
        if (workDate.isBefore(today)) {
            return false;
        }
        return shiftStart == null || shiftStart.isAfter(VietnamTime.now().toLocalTime());
    }

    private static boolean overlaps(Shift a, Shift b) {
        if (a == null || b == null || a.getStartTime() == null || a.getEndTime() == null
                || b.getStartTime() == null || b.getEndTime() == null) {
            return false;
        }
        return a.getStartTime().isBefore(b.getEndTime()) && a.getEndTime().isAfter(b.getStartTime());
    }

    private static String slotDateKey(Integer shiftId, LocalDate date) {
        return shiftId + "_" + date;
    }

    private static String formatTimeRange(Shift shift) {
        if (shift == null || shift.getStartTime() == null || shift.getEndTime() == null) {
            return "";
        }
        return shift.getStartTime().format(TIME) + " – " + shift.getEndTime().format(TIME);
    }

    private static String formatWeekRange(LocalDate monday, LocalDate sunday) {
        return monday.format(SHORT_DATE) + " – " + sunday.format(FULL_DATE);
    }

    private static LocalDate currentWeekMonday() {
        return VietnamTime.today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private static String cleanNote(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > 255 ? trimmed.substring(0, 255) : trimmed;
    }

    private record WeekShiftSnapshot(Set<String> regularSlotKeys,
                                     Set<String> approvedOvertimeSlotKeys,
                                     Set<String> pendingOvertimeSlotKeys,
                                     Map<LocalDate, List<Shift>> shiftsByDate) {
    }
}

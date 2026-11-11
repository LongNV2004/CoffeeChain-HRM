package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.ApprovalStatus;
import com.example.coffee_hrm.common.enums.AssignmentStatus;
import com.example.coffee_hrm.common.enums.CertificationStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.NotificationType;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.response.OvertimePageView;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.EmploymentContract;
import com.example.coffee_hrm.entity.OvertimeRequest;
import com.example.coffee_hrm.entity.Shift;
import com.example.coffee_hrm.entity.ShiftAssignment;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.entity.WorkAvailability;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.EmploymentContractRepository;
import com.example.coffee_hrm.repository.OvertimeRequestRepository;
import com.example.coffee_hrm.repository.ShiftAssignmentRepository;
import com.example.coffee_hrm.repository.ShiftRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.repository.UserRepository;
import com.example.coffee_hrm.repository.WorkAvailabilityRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.NotificationService;
import com.example.coffee_hrm.service.OvertimeService;
import com.example.coffee_hrm.service.support.ApprovedLeaveCoverage;
import com.example.coffee_hrm.service.support.AvailabilityCoverage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OvertimeServiceImpl implements OvertimeService {

    static final String REFERENCE_TYPE = "OVERTIME_REQUEST";
    /** Nhân viên được gửi đơn trước tối đa bao nhiêu ngày. */
    static final int MAX_DAYS_AHEAD = 60;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final List<ApprovalStatus> ACTIVE = List.of(ApprovalStatus.PENDING, ApprovalStatus.APPROVED);

    private final OvertimeRequestRepository overtimeRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final StoreRepository storeRepository;
    private final ShiftRepository shiftRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final WorkAvailabilityRepository workAvailabilityRepository;
    private final EmploymentContractRepository employmentContractRepository;
    private final ApprovedLeaveCoverage approvedLeaveCoverage;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Override
    @Transactional(readOnly = true)
    public OvertimePageView staffPage(AuthenticatedUser user) {
        Employee employee = requireStaff(user);
        Store store = employee.getStore();
        List<OvertimeRequest> mine = overtimeRequestRepository.findByEmployee(employee.getId());
        List<OvertimePageView.Item> pending = new ArrayList<>();
        List<OvertimePageView.Item> history = new ArrayList<>();
        for (OvertimeRequest request : mine) {
            boolean open = request.getStatus() == ApprovalStatus.PENDING;
            OvertimePageView.Item item = toItem(request, store.getId(), open, false);
            (open ? pending : history).add(item);
        }
        return base(store, mine)
                .shifts(shiftOptions(store.getId()))
                .pending(pending)
                .history(history)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public OvertimePageView managerPage(AuthenticatedUser user) {
        Store store = requireManagedStore(user);
        List<OvertimeRequest> all = overtimeRequestRepository.findByStore(store.getId());
        List<OvertimePageView.Item> pending = new ArrayList<>();
        List<OvertimePageView.Item> history = new ArrayList<>();
        for (OvertimeRequest request : all) {
            boolean open = request.getStatus() == ApprovalStatus.PENDING;
            OvertimePageView.Item item = toItem(request, store.getId(), false, open);
            (open ? pending : history).add(item);
        }
        return base(store, all)
                .pending(pending)
                .history(history)
                .build();
    }

    @Override
    @Transactional
    public void submit(AuthenticatedUser user, Integer shiftId, LocalDate workDate, String reason) {
        Employee employee = requireStaff(user);
        employeeRepository.lockByIdForLeaveRequest(employee.getId());
        Store store = employee.getStore();
        if (shiftId == null || workDate == null) {
            throw new BusinessException("Vui lòng chọn ngày và ca muốn tăng ca.");
        }
        String note = reason == null ? "" : reason.trim();
        if (note.isEmpty()) {
            throw new BusinessException("Vui lòng nhập lý do tăng ca.");
        }
        if (note.length() > 255) {
            throw new BusinessException("Lý do tăng ca tối đa 255 ký tự.");
        }
        if (workDate.isAfter(VietnamTime.today().plusDays(MAX_DAYS_AHEAD))) {
            throw new BusinessException("Chỉ gửi đơn tăng ca trong vòng " + MAX_DAYS_AHEAD + " ngày tới.");
        }
        Shift shift = shiftRepository.findByIdAndStore_Id(shiftId, store.getId())
                .orElseThrow(() -> new BusinessException("Ca làm việc không thuộc cửa hàng của bạn."));
        List<OvertimeRequest> sameDay = overtimeRequestRepository.findActiveOnDate(employee.getId(), workDate, ACTIVE);
        if (sameDay.stream().anyMatch(other -> other.getShift() != null && Objects.equals(other.getShift().getId(), shiftId))) {
            throw new BusinessException("Bạn đã có đơn tăng ca cho ca này trong ngày " + workDate.format(DATE) + ".");
        }
        long pendingOthers = sameDay.stream().filter(other -> other.getStatus() == ApprovalStatus.PENDING).count();
        validateSlot(employee, store, shift, workDate, (int) pendingOthers, "Bạn");

        OvertimeRequest saved = overtimeRequestRepository.save(OvertimeRequest.builder()
                .employee(employee)
                .store(store)
                .shift(shift)
                .workDate(workDate)
                .reason(note)
                .status(ApprovalStatus.PENDING)
                .build());
        notifyManager(store, "Đơn tăng ca mới",
                employee.getFullName() + " xin tăng ca " + slotText(shift, workDate) + ".",
                NotificationType.OVERTIME_REQUEST_SUBMITTED, saved.getId());
    }

    @Override
    @Transactional
    public void cancel(AuthenticatedUser user, Integer requestId) {
        Employee employee = requireStaff(user);
        OvertimeRequest request = overtimeRequestRepository.lockById(requestId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy đơn tăng ca."));
        if (request.getEmployee() == null || !Objects.equals(request.getEmployee().getId(), employee.getId())) {
            throw new BusinessException("Bạn chỉ hủy được đơn tăng ca của chính mình.");
        }
        if (request.getStatus() != ApprovalStatus.PENDING) {
            throw new BusinessException("Chỉ hủy được đơn đang chờ duyệt.");
        }
        request.setStatus(ApprovalStatus.CANCELLED);
        request.setResolvedAt(VietnamTime.now());
        overtimeRequestRepository.save(request);
    }

    @Override
    @Transactional
    public void approve(AuthenticatedUser user, Integer requestId) {
        Store store = requireManagedStore(user);
        OvertimeRequest request = requireStoreRequest(requestId, store);
        Employee employee = employeeRepository.lockByIdForLeaveRequest(request.getEmployee().getId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy nhân viên."));
        Shift shift = request.getShift();
        LocalDate workDate = request.getWorkDate();
        if (!Objects.equals(employee.getStore() == null ? null : employee.getStore().getId(), store.getId())) {
            throw new BusinessException("Nhân viên không còn thuộc cửa hàng của bạn.");
        }
        validateSlot(employee, store, shift, workDate, 0, "Nhân viên " + employee.getFullName());

        ShiftAssignment assignment = shiftAssignmentRepository.save(ShiftAssignment.builder()
                .shift(shift)
                .employee(employee)
                .workDate(workDate)
                .status(AssignmentStatus.ASSIGNED)
                .isPublished(true)
                .publishedAt(VietnamTime.now())
                .build());
        request.setAssignment(assignment);
        request.setStatus(ApprovalStatus.APPROVED);
        request.setRejectReason(null);
        request.setResolvedAt(VietnamTime.now());
        request.setResolvedBy(reviewer(user));
        overtimeRequestRepository.save(request);
        notifyEmployee(employee, "Đơn tăng ca đã được duyệt",
                "Bạn được tăng ca " + slotText(shift, workDate) + ". Ca đã có trên lịch làm việc.",
                NotificationType.OVERTIME_REQUEST_APPROVED, request.getId());
    }

    @Override
    @Transactional
    public void reject(AuthenticatedUser user, Integer requestId, String reason) {
        Store store = requireManagedStore(user);
        String note = reason == null ? "" : reason.trim();
        if (note.isEmpty()) {
            throw new BusinessException("Vui lòng nhập lý do từ chối.");
        }
        if (note.length() > 500) {
            throw new BusinessException("Lý do từ chối tối đa 500 ký tự.");
        }
        OvertimeRequest request = requireStoreRequest(requestId, store);
        request.setStatus(ApprovalStatus.REJECTED);
        request.setRejectReason(note);
        request.setResolvedAt(VietnamTime.now());
        request.setResolvedBy(reviewer(user));
        overtimeRequestRepository.save(request);
        notifyEmployee(request.getEmployee(), "Đơn tăng ca bị từ chối",
                "Đơn tăng ca " + slotText(request.getShift(), request.getWorkDate()) + " bị từ chối. Lý do: " + note,
                NotificationType.OVERTIME_REQUEST_REJECTED, request.getId());
    }

    /**
     * Quy tắc chung khi gửi và khi duyệt: ca chưa bắt đầu, nhân viên đủ điều kiện, không nghỉ phép, không trùng
     * giờ với ca khác và tổng số ca trong ngày (kể cả ca tăng ca này) không quá {@link #OVERTIME_DAILY_LIMIT}.
     */
    private void validateSlot(Employee employee, Store store, Shift shift, LocalDate workDate,
                              int pendingOthers, String subject) {
        if (shift == null || shift.getStartTime() == null || shift.getEndTime() == null) {
            throw new BusinessException("Không xác định được giờ của ca làm việc.");
        }
        if (VietnamTime.isBeforeNow(workDate, shift.getStartTime())) {
            throw new BusinessException("Ca " + slotText(shift, workDate) + " đã bắt đầu hoặc đã qua.");
        }
        if (employee.getStatus() != EmployeeStatus.ACTIVE) {
            throw new BusinessException(subject + " không ở trạng thái đang làm việc nên không thể tăng ca.");
        }
        if (employee.getCertificationStatus() != CertificationStatus.CERTIFIED) {
            throw new BusinessException(subject + " chưa có chứng chỉ nên không thể tăng ca.");
        }
        requireContract(employee, workDate, subject);
        if (approvedLeaveCoverage.blocksShift(employee.getId(), shift.getId(), workDate)) {
            throw new BusinessException(subject + " đã được duyệt nghỉ ca này, không thể tăng ca.");
        }
        Map<Integer, Shift> dayShifts = dayShifts(employee.getId(), store.getId(), workDate);
        if (dayShifts.containsKey(shift.getId())) {
            throw new BusinessException(subject + " đã có ca " + shift.getShiftName() + " ngày " + workDate.format(DATE) + " trên lịch.");
        }
        for (Shift other : dayShifts.values()) {
            if (other.getStartTime() != null && other.getEndTime() != null
                    && shift.getStartTime().isBefore(other.getEndTime())
                    && shift.getEndTime().isAfter(other.getStartTime())) {
                throw new BusinessException("Ca tăng ca trùng giờ với " + other.getShiftName() + " ("
                        + other.getStartTime().format(TIME) + " – " + other.getEndTime().format(TIME) + ") cùng ngày.");
            }
        }
        if (dayShifts.size() + pendingOthers + 1 > OVERTIME_DAILY_LIMIT) {
            throw new BusinessException("Mỗi ngày chỉ được làm tối đa " + OVERTIME_DAILY_LIMIT
                    + " ca khi tăng ca. " + subject + " đã có " + dayShifts.size() + " ca"
                    + (pendingOthers > 0 ? " và " + pendingOthers + " đơn tăng ca đang chờ" : "")
                    + " ngày " + workDate.format(DATE) + ".");
        }
    }

    /** Ca của nhân viên trong ngày: ca đã phân công cộng ca đăng ký đã duyệt (không tính trùng). */
    private Map<Integer, Shift> dayShifts(Integer employeeId, Integer storeId, LocalDate workDate) {
        Map<Integer, Shift> shifts = new LinkedHashMap<>();
        List<ShiftAssignment> assigned = shiftAssignmentRepository.findByEmployee_IdAndWorkDateAndStatus(
                employeeId, workDate, AssignmentStatus.ASSIGNED);
        if (assigned != null) {
            for (ShiftAssignment assignment : assigned) {
                if (assignment.getShift() != null && assignment.getShift().getId() != null) {
                    shifts.putIfAbsent(assignment.getShift().getId(), assignment.getShift());
                }
            }
        }
        List<WorkAvailability> approved = workAvailabilityRepository.findApprovedCovering(
                storeId, workDate, workDate, ApprovalStatus.APPROVED);
        if (approved != null) {
            for (WorkAvailability availability : approved) {
                if (availability.getEmployee() != null && Objects.equals(availability.getEmployee().getId(), employeeId)
                        && availability.getShift() != null && availability.getShift().getId() != null
                        && (availability.getStatus() == null || availability.getStatus() == ApprovalStatus.APPROVED)
                        && AvailabilityCoverage.appliesOn(availability, workDate)
                        && !approvedLeaveCoverage.blocksShift(employeeId, availability.getShift().getId(), workDate)) {
                    shifts.putIfAbsent(availability.getShift().getId(), availability.getShift());
                }
            }
        }
        return shifts;
    }

    private void requireContract(Employee employee, LocalDate workDate, String subject) {
        if (employmentContractRepository == null) {
            return;
        }
        List<EmploymentContract> contracts = employmentContractRepository.findByEmployeeId(employee.getId());
        if (contracts == null || contracts.stream().noneMatch(contract -> ContractValidityRules.coversWorkDay(contract, workDate))) {
            throw new BusinessException(subject + " không có hợp đồng còn hiệu lực ngày " + workDate.format(DATE) + ".");
        }
    }

    private OvertimePageView.OvertimePageViewBuilder base(Store store, List<OvertimeRequest> requests) {
        LocalDate today = VietnamTime.today();
        return OvertimePageView.builder()
                .storeName(store.getStoreName())
                .normalDailyLimit(NORMAL_DAILY_LIMIT)
                .overtimeDailyLimit(OVERTIME_DAILY_LIMIT)
                .minDate(today)
                .maxDate(today.plusDays(MAX_DAYS_AHEAD))
                .pendingCount((int) requests.stream().filter(r -> r.getStatus() == ApprovalStatus.PENDING).count())
                .approvedCount((int) requests.stream().filter(r -> r.getStatus() == ApprovalStatus.APPROVED).count());
    }

    private List<OvertimePageView.ShiftOption> shiftOptions(Integer storeId) {
        List<OvertimePageView.ShiftOption> options = new ArrayList<>();
        for (Shift shift : shiftRepository.findByStore_IdOrderByStartTimeAsc(storeId)) {
            if (shift.getStartTime() == null || shift.getEndTime() == null) {
                continue;
            }
            options.add(OvertimePageView.ShiftOption.builder()
                    .shiftId(shift.getId())
                    .label(shift.getShiftName() + " (" + shift.getStartTime().format(TIME) + " – " + shift.getEndTime().format(TIME) + ")")
                    .startTime(shift.getStartTime().format(TIME))
                    .build());
        }
        return options;
    }

    private OvertimePageView.Item toItem(OvertimeRequest request, Integer storeId, boolean staffView, boolean managerView) {
        Shift shift = request.getShift();
        Employee employee = request.getEmployee();
        boolean pending = request.getStatus() == ApprovalStatus.PENDING;
        int dayCount = employee == null || request.getWorkDate() == null
                ? 0 : dayShifts(employee.getId(), storeId, request.getWorkDate()).size();
        return OvertimePageView.Item.builder()
                .id(request.getId())
                .employeeId(employee == null ? null : employee.getId())
                .employeeName(employee == null ? "—" : employee.getFullName())
                .employeeCode(employee == null ? "—" : String.format("NV%03d", employee.getId()))
                .dateLabel(request.getWorkDate() == null ? "—" : request.getWorkDate().format(DATE))
                .shiftName(shift == null ? "—" : shift.getShiftName())
                .timeRange(shift == null || shift.getStartTime() == null || shift.getEndTime() == null
                        ? "—" : shift.getStartTime().format(TIME) + " – " + shift.getEndTime().format(TIME))
                .reason(request.getReason() == null ? "" : request.getReason())
                .statusKey(request.getStatus() == null ? "PENDING" : request.getStatus().name())
                .statusLabel(request.getStatus() == null ? "Chờ duyệt" : request.getStatus().getLabel())
                .createdLabel(format(request.getCreatedAt()))
                .resolvedLabel(format(request.getResolvedAt()))
                .resolverName(resolverName(request.getResolvedBy()))
                .rejectReason(request.getRejectReason())
                .dayShiftCount(dayCount)
                .canCancel(staffView && pending)
                .canReview(managerView && pending)
                .build();
    }

    private Employee requireStaff(AuthenticatedUser user) {
        if (user == null || user.getRoleName() != RoleName.STAFF || user.getEmployeeId() == null) {
            throw new BusinessException("Chỉ nhân viên mới gửi được đơn tăng ca.");
        }
        Employee employee = employeeRepository.findByIdWithStore(user.getEmployeeId())
                .orElseThrow(() -> new BusinessException("Tài khoản chưa gắn nhân viên."));
        if (employee.getStore() == null) {
            throw new BusinessException("Nhân viên chưa thuộc cửa hàng nào.");
        }
        return employee;
    }

    private Store requireManagedStore(AuthenticatedUser user) {
        if (user == null || user.getRoleName() != RoleName.MANAGER || user.getEmployeeId() == null) {
            throw new BusinessException("Chỉ quản lý cửa hàng mới duyệt được đơn tăng ca.");
        }
        return storeRepository.findByManager_Id(user.getEmployeeId())
                .orElseThrow(() -> new BusinessException("Bạn chưa được phân công quản lý cửa hàng nào."));
    }

    private OvertimeRequest requireStoreRequest(Integer requestId, Store store) {
        OvertimeRequest request = overtimeRequestRepository.lockById(requestId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy đơn tăng ca."));
        if (request.getStore() == null || !Objects.equals(request.getStore().getId(), store.getId())) {
            throw new BusinessException("Đơn tăng ca không thuộc cửa hàng của bạn.");
        }
        if (request.getStatus() != ApprovalStatus.PENDING) {
            throw new BusinessException("Đơn tăng ca đã được xử lý.");
        }
        return request;
    }

    private User reviewer(AuthenticatedUser user) {
        return user.getUserId() == null ? null : userRepository.getReferenceById(user.getUserId());
    }

    private void notifyManager(Store store, String title, String message, NotificationType type, Integer id) {
        if (notificationService == null || store.getManager() == null) {
            return;
        }
        Optional<User> manager = userRepository.findByEmployee_Id(store.getManager().getId());
        manager.ifPresent(recipient -> notificationService.notifyUsers(
                List.of(recipient), title, message, type, REFERENCE_TYPE, id));
    }

    private void notifyEmployee(Employee employee, String title, String message, NotificationType type, Integer id) {
        if (notificationService == null || employee == null) {
            return;
        }
        Optional<User> recipient = userRepository.findByEmployee_Id(employee.getId());
        recipient.ifPresent(target -> notificationService.notifyUsers(
                List.of(target), title, message, type, REFERENCE_TYPE, id));
    }

    private static String slotText(Shift shift, LocalDate workDate) {
        String time = shift.getStartTime() == null || shift.getEndTime() == null
                ? "" : " (" + shift.getStartTime().format(TIME) + " – " + shift.getEndTime().format(TIME) + ")";
        return shift.getShiftName() + time + " ngày " + workDate.format(DATE);
    }

    private static String resolverName(User user) {
        if (user == null) {
            return null;
        }
        if (user.getEmployee() != null && user.getEmployee().getFullName() != null) {
            return user.getEmployee().getFullName();
        }
        return user.getUsername();
    }

    private static String format(LocalDateTime time) {
        return time == null ? "—" : time.format(DATE_TIME);
    }
}

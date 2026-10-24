package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.ApprovalStatus;
import com.example.coffee_hrm.common.enums.AssignmentStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.LeaveType;
import com.example.coffee_hrm.common.enums.NotificationType;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.request.CreateLeaveRequest;
import com.example.coffee_hrm.dto.response.AdminLeaveReport;
import com.example.coffee_hrm.dto.response.LeaveFormContext;
import com.example.coffee_hrm.dto.response.LeaveHistoryPage;
import com.example.coffee_hrm.dto.response.LeavePreviewResponse;
import com.example.coffee_hrm.dto.response.LeaveRequestResponse;
import com.example.coffee_hrm.dto.response.LeaveSlotItem;
import com.example.coffee_hrm.dto.response.LeaveSlotOption;
import com.example.coffee_hrm.dto.response.LeaveSlotPage;
import com.example.coffee_hrm.entity.Attendance;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.LeaveBalance;
import com.example.coffee_hrm.entity.LeaveRequest;
import com.example.coffee_hrm.entity.LeaveRequestSlot;
import com.example.coffee_hrm.entity.Shift;
import com.example.coffee_hrm.entity.ShiftAssignment;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.AttendanceRepository;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.LeaveBalanceRepository;
import com.example.coffee_hrm.repository.LeaveRequestRepository;
import com.example.coffee_hrm.repository.LeaveRequestSlotRepository;
import com.example.coffee_hrm.repository.ShiftAssignmentRepository;
import com.example.coffee_hrm.repository.UserRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.LeaveRequestService;
import com.example.coffee_hrm.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LeaveRequestServiceImpl implements LeaveRequestService {
    static final String PROCESS_NOTE = "Mỗi ca được chọn được tính riêng. "
            + "Đơn sau khi gửi ở trạng thái Chờ duyệt và chưa trừ phép. "
            + "Phép năm chỉ bị trừ đúng số ca khi quản lý duyệt. "
            + "Nghỉ không hưởng lương, nghỉ ốm và nghỉ việc riêng không trừ ca phép năm. "
            + "Các ca khác trong cùng ngày vẫn giữ lịch và chấm công bình thường.";
    private static final int HISTORY_PAGE_SIZE = 20;
    private static final int MAX_RANGE_DAYS = 42;
    private static final int MAX_LISTED_SLOTS = 200;
    private static final String NOT_YOURS = "Một hoặc nhiều ca không thuộc lịch làm việc của bạn.";
    private static final Set<ApprovalStatus> BLOCKING_STATUSES =
            EnumSet.of(ApprovalStatus.PENDING, ApprovalStatus.APPROVED);

    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveRequestSlotRepository leaveRequestSlotRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Override
    public LeaveFormContext formContext(AuthenticatedUser user) {
        Employee employee = requireReadableEmployee(user);
        Store store = employee.getStore();
        int year = VietnamTime.today().getYear();
        Integer allowance = store.getTotalLeaveSlots();
        boolean configured = allowance != null && allowance >= 0;
        int total = configured ? allowance : 0;
        int used = usedSlotCount(employee.getId(), year);
        int pending = Math.toIntExact(reservedSlots(employee.getId(), year, null));
        String managerName = store.getManager() == null || store.getManager().getFullName() == null
                ? "Chưa gán quản lý"
                : store.getManager().getFullName();
        String note = configured
                ? "Chọn một hoặc nhiều ca đã được phân. Tuần này và tuần sau chỉ để xem các ca, không tạo đơn theo khoảng ngày."
                : "Admin chưa thiết lập số ca phép năm cho cửa hàng. Nghỉ phép năm chưa gửi được; các loại nghỉ khác không trừ ca phép năm.";
        return new LeaveFormContext(employee.getId(), employee.getFullName(), store.getStoreName(),
                managerName, total, used, pending, Math.max(total - used - pending, 0), false, configured,
                note);
    }

    @Override
    public LeaveSlotPage listSlots(LocalDate fromDate, LocalDate toDate, AuthenticatedUser user) {
        Employee employee = requireReadableEmployee(user);
        LocalDate[] range = resolveRange(fromDate, toDate);
        List<ShiftAssignment> rows = shiftAssignmentRepository.findScheduleForLeavePicker(
                employee.getId(), range[0], range[1]);
        if (rows == null) {
            rows = List.of();
        }
        List<ShiftAssignment> upcoming = rows.stream().filter(this::notStarted).toList();
        boolean truncated = upcoming.size() > MAX_LISTED_SLOTS;
        if (truncated) {
            upcoming = upcoming.subList(0, MAX_LISTED_SLOTS);
        }
        Map<Integer, ApprovalStatus> blocking = blockingByAssignment(upcoming);
        Set<String> attended = attendedKeys(employee.getId(), range[0], range[1]);
        List<LeaveSlotOption> slots = upcoming.stream()
                .map(row -> toOption(row, blocking.get(row.getId()), attended))
                .toList();
        return new LeaveSlotPage(slots, range[0], range[1], truncated);
    }

    @Override
    public LeavePreviewResponse preview(List<Integer> assignmentIds, LeaveType leaveType, AuthenticatedUser user) {
        Employee employee = requireReadableEmployee(user);
        return describe(employee, loadOwnedSlots(employee, assignmentIds, false), leaveType);
    }

    @Override
    @Transactional
    public LeaveRequestResponse create(CreateLeaveRequest request, AuthenticatedUser user) {
        if (request == null || request.getLeaveType() == null) {
            throw new BusinessException("Vui lòng chọn loại nghỉ.");
        }
        if (request.getReason() == null || request.getReason().isBlank()) {
            throw new BusinessException("Vui lòng nhập lý do xin nghỉ.");
        }
        String reason = request.getReason().trim();
        if (reason.length() > 255) {
            throw new BusinessException("Lý do không được vượt quá 255 ký tự.");
        }
        String phone = normalizePhone(request.getContactPhone());
        Employee employee = requireEmployee(user, true);
        List<ShiftAssignment> selected = loadOwnedSlots(employee, request.getAssignmentIds(), true);
        LeavePreviewResponse preview = describe(employee, selected, request.getLeaveType());
        LocalDate start = selected.stream().map(ShiftAssignment::getWorkDate).min(LocalDate::compareTo).orElseThrow();
        LocalDate end = selected.stream().map(ShiftAssignment::getWorkDate).max(LocalDate::compareTo).orElseThrow();
        LeaveRequest created = leaveRequestRepository.saveAndFlush(LeaveRequest.builder()
                .employee(employee)
                .leaveType(request.getLeaveType())
                .startDate(start)
                .endDate(end)
                .requestedWorkDays(preview.requestedWorkDays())
                .contactPhone(phone)
                .reason(reason)
                .status(ApprovalStatus.PENDING)
                .build());
        for (ShiftAssignment slot : selected) {
            leaveRequestSlotRepository.save(LeaveRequestSlot.builder()
                    .leaveRequest(created)
                    .assignment(slot)
                    .build());
        }
        leaveRequestSlotRepository.flush();
        notifyManager(employee, created);
        return toResponse(created, toItems(selected));
    }

    @Override
    public LeaveHistoryPage history(int page, ApprovalStatus status, LocalDate fromDate, LocalDate toDate,
                                    AuthenticatedUser user) {
        Employee employee = requireReadableEmployee(user);
        assertPage(page);
        assertDateRange(fromDate, toDate);
        Page<LeaveRequest> result = leaveRequestRepository.searchByEmployee(employee.getId(), status, fromDate,
                toDate, PageRequest.of(page, HISTORY_PAGE_SIZE,
                        Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        Map<Integer, List<LeaveSlotItem>> slots = slotsByRequest(result.getContent());
        List<LeaveRequestResponse> items = result.getContent().stream()
                .map(item -> toResponse(item, slots.getOrDefault(item.getId(), List.of())))
                .toList();
        return new LeaveHistoryPage(items, result.hasNext(), result.getTotalElements(), page,
                result.getTotalPages());
    }

    @Override
    public LeaveRequestResponse detail(Integer id, AuthenticatedUser user) {
        Employee employee = requireReadableEmployee(user);
        LeaveRequest request = requireOwnRequest(id, employee.getId());
        return toResponse(request, slotsByRequest(List.of(request)).getOrDefault(request.getId(), List.of()));
    }

    @Override
    @Transactional
    public LeaveRequestResponse cancel(Integer id, AuthenticatedUser user) {
        Employee employee = requireEmployee(user, true);
        LeaveRequest request = leaveRequestRepository.lockById(id)
                .orElseThrow(() -> new BusinessException("Không tìm thấy đơn nghỉ phép."));
        if (request.getEmployee() == null || !Objects.equals(request.getEmployee().getId(), employee.getId())) {
            throw new BusinessException("Không tìm thấy đơn nghỉ phép.");
        }
        if (request.getStatus() == ApprovalStatus.CANCELLED || request.getStatus() == ApprovalStatus.REJECTED) {
            throw new BusinessException("Đơn này đã được xử lý, không thể hủy lần nữa.");
        }
        if (request.getStatus() != ApprovalStatus.PENDING && request.getStatus() != ApprovalStatus.APPROVED) {
            throw new BusinessException("Chỉ hủy được đơn đang chờ duyệt hoặc đã duyệt còn khôi phục được ca.");
        }
        List<LeaveRequestSlot> links = request.getId() == null
                ? List.of()
                : leaveRequestSlotRepository.findDetailedByRequestIds(List.of(request.getId()));
        if (links == null) {
            links = List.of();
        }
        boolean refund = false;
        if (request.getStatus() == ApprovalStatus.APPROVED) {
            restoreApprovedSlots(employee, request, links);
            refund = request.getLeaveType() == null || request.getLeaveType().isConsumesAnnualLeave();
        }
        User actor = userRepository.findById(user.getUserId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy tài khoản."));
        request.setStatus(ApprovalStatus.CANCELLED);
        request.setApprovedBy(actor);
        request.setResolvedDate(VietnamTime.now());
        request.setRejectReason(null);
        leaveRequestRepository.saveAndFlush(request);
        notifyManagerOfCancel(employee, request, refund);
        return toResponse(request, toItems(links.stream().map(LeaveRequestSlot::getAssignment).toList()));
    }

    @Override
    public AdminLeaveReport adminReport(int page, ApprovalStatus status, LocalDate fromDate, LocalDate toDate) {
        assertPage(page);
        assertDateRange(fromDate, toDate);
        Page<LeaveRequest> result = leaveRequestRepository.searchForAdmin(status, fromDate, toDate,
                PageRequest.of(page, HISTORY_PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        List<AdminLeaveReport.Row> rows = result.getContent().stream().map(item -> {
            LeaveType type = item.getLeaveType() == null ? LeaveType.ANNUAL : item.getLeaveType();
            Employee owner = item.getEmployee();
            Store store = owner == null ? null : owner.getStore();
            return new AdminLeaveReport.Row(
                    item.getId(),
                    owner == null ? null : owner.getId(),
                    owner == null ? null : owner.getFullName(),
                    store == null ? null : store.getStoreName(),
                    type.getLabel(),
                    item.getStartDate(),
                    item.getEndDate(),
                    item.getRequestedWorkDays(),
                    item.getStatus() == null ? "" : item.getStatus().getLabel(),
                    item.getCreatedAt());
        }).toList();
        return new AdminLeaveReport(
                leaveRequestRepository.countByStatus(ApprovalStatus.PENDING),
                leaveRequestRepository.countByStatus(ApprovalStatus.APPROVED),
                leaveRequestRepository.countByStatus(ApprovalStatus.REJECTED),
                leaveRequestRepository.countByStatus(ApprovalStatus.CANCELLED),
                rows, page, result.getTotalPages(), result.getTotalElements());
    }

    private void restoreApprovedSlots(Employee employee, LeaveRequest request, List<LeaveRequestSlot> links) {
        if (links.isEmpty()) {
            throw new BusinessException("Đơn đã duyệt không gắn ca cụ thể, không thể hoàn phép.");
        }
        List<Integer> ids = links.stream().map(link -> link.getAssignment().getId()).toList();
        shiftAssignmentRepository.lockByIds(ids);
        Map<Integer, Long> refundByYear = new TreeMap<>();
        for (LeaveRequestSlot link : links) {
            ShiftAssignment assignment = link.getAssignment();
            if (assignment == null || assignment.getShift() == null
                    || !Objects.equals(assignment.getEmployee().getId(), employee.getId())
                    || assignment.getCancelledByLeaveRequest() == null
                    || !Objects.equals(assignment.getCancelledByLeaveRequest().getId(), request.getId())
                    || assignment.getStatus() != AssignmentStatus.CANCELLED) {
                throw new BusinessException("Ca không còn ở trạng thái nghỉ của đơn này, không thể hoàn phép.");
            }
            if (VietnamTime.isBeforeNow(assignment.getWorkDate(), assignment.getShift().getStartTime())) {
                throw new BusinessException("Ca đã bắt đầu hoặc đã qua, không thể khôi phục lịch và hoàn phép.");
            }
            if (shiftAssignmentRepository.existsByReplacesAssignment_Id(assignment.getId())) {
                throw new BusinessException("Ca đã có người thay, không thể hủy đơn đã duyệt.");
            }
            Optional<Attendance> attendance = attendanceRepository.findByEmployee_IdAndShift_IdAndWorkDate(
                    employee.getId(), assignment.getShift().getId(), assignment.getWorkDate());
            if (attendance != null && attendance.isPresent()) {
                throw new BusinessException("Ca đã có dữ liệu chấm công, không thể hủy đơn đã duyệt.");
            }
            assignment.setStatus(AssignmentStatus.ASSIGNED);
            assignment.setCancelledByLeaveRequest(null);
            refundByYear.merge(assignment.getWorkDate().getYear(), 1L, Long::sum);
        }
        shiftAssignmentRepository.saveAll(links.stream().map(LeaveRequestSlot::getAssignment).toList());
        if (request.getLeaveType() != null && !request.getLeaveType().isConsumesAnnualLeave()) {
            return;
        }
        for (Map.Entry<Integer, Long> entry : refundByYear.entrySet()) {
            LeaveBalance balance = leaveBalanceRepository.lockByEmployeeIdAndYear(employee.getId(), entry.getKey())
                    .orElseThrow(() -> new BusinessException("Không tìm thấy số ca phép đã trừ để hoàn lại."));
            int used = balance.getUsedSlots() == null ? 0 : balance.getUsedSlots();
            int refund = Math.toIntExact(entry.getValue());
            if (used < refund) {
                throw new BusinessException("Số ca phép đã trừ không đủ để hoàn lại.");
            }
            int dayUsed = balance.getUsedDays();
            int dayTotal = balance.getTotalDays();
            int dayRemaining = balance.getRemainingDays();
            balance.setUsedSlots(used - refund);
            if (balance.getTotalSlots() != null) {
                balance.setRemainingSlots(balance.getTotalSlots() - balance.getUsedSlots());
            }
            balance.setUsedDays(dayUsed);
            balance.setTotalDays(dayTotal);
            balance.setRemainingDays(dayRemaining);
            leaveBalanceRepository.save(balance);
        }
    }

    private List<ShiftAssignment> loadOwnedSlots(Employee employee, List<Integer> rawIds, boolean lock) {
        if (rawIds == null || rawIds.isEmpty()) {
            throw new BusinessException("Vui lòng chọn ít nhất một ca làm việc.");
        }
        if (rawIds.size() > 60) {
            throw new BusinessException("Một đơn chỉ nhận tối đa 60 ca.");
        }
        LinkedHashSet<Integer> unique = new LinkedHashSet<>();
        for (Integer id : rawIds) {
            if (id == null || !unique.add(id)) {
                throw new BusinessException("Danh sách ca bị trùng hoặc không hợp lệ.");
            }
        }
        List<ShiftAssignment> loaded = lock
                ? shiftAssignmentRepository.lockByIds(unique)
                : shiftAssignmentRepository.findForLeaveSelection(unique);
        if (!lock) {
            if (loaded.size() != unique.size()) {
                throw new BusinessException(NOT_YOURS);
            }
        } else if (loaded == null || loaded.size() != unique.size()) {
            throw new BusinessException(NOT_YOURS);
        }
        Map<Integer, ShiftAssignment> byId = new HashMap<>();
        for (ShiftAssignment row : loaded) {
            byId.put(row.getId(), row);
        }
        if (byId.size() != unique.size()) {
            throw new BusinessException(NOT_YOURS);
        }
        List<ShiftAssignment> ordered = new ArrayList<>();
        for (Integer id : unique) {
            ShiftAssignment row = byId.get(id);
            assertSelectable(employee, row);
            ordered.add(row);
        }
        List<Integer> blocked = leaveRequestSlotRepository.findBlockedAssignmentIds(unique, BLOCKING_STATUSES);
        if (blocked != null && !blocked.isEmpty()) {
            throw new BusinessException("Ca này đã có đơn nghỉ đang chờ hoặc đã duyệt.");
        }
        ordered.sort(Comparator.comparing(ShiftAssignment::getWorkDate)
                .thenComparing(row -> row.getShift().getStartTime()));
        return ordered;
    }

    private void assertSelectable(Employee employee, ShiftAssignment row) {
        if (row == null || row.getEmployee() == null || row.getShift() == null
                || !Objects.equals(row.getEmployee().getId(), employee.getId())
                || row.getShift().getStore() == null
                || !Objects.equals(row.getShift().getStore().getId(), employee.getStore().getId())) {
            throw new BusinessException(NOT_YOURS);
        }
        if (row.getStatus() != AssignmentStatus.ASSIGNED) {
            throw new BusinessException("Ca đã hủy hoặc không còn hiệu lực, không thể xin nghỉ.");
        }
        if (VietnamTime.isBeforeNow(row.getWorkDate(), row.getShift().getStartTime())) {
            throw new BusinessException("Không thể xin nghỉ ca đã bắt đầu hoặc đã qua.");
        }
        Optional<Attendance> attendance = attendanceRepository.findByEmployee_IdAndShift_IdAndWorkDate(
                employee.getId(), row.getShift().getId(), row.getWorkDate());
        if (attendance != null && attendance.isPresent()) {
            throw new BusinessException("Ca đã có dữ liệu chấm công, không thể xin nghỉ.");
        }
    }

    private LeavePreviewResponse describe(Employee employee, List<ShiftAssignment> selected, LeaveType leaveType) {
        if (leaveType == null) {
            throw new BusinessException("Vui lòng chọn loại nghỉ.");
        }
        Map<Integer, Long> requestedByYear = selected.stream()
                .collect(Collectors.groupingBy(row -> row.getWorkDate().getYear(), TreeMap::new, Collectors.counting()));
        List<LeavePreviewResponse.YearDetail> years = new ArrayList<>();
        if (leaveType.isConsumesAnnualLeave()) {
            Integer allowance = employee.getStore().getTotalLeaveSlots();
            if (allowance == null) {
                throw new BusinessException(
                        "Cửa hàng chưa được admin thiết lập số ca phép năm. Không thể gửi đơn nghỉ phép năm.");
            }
            if (allowance < 0) {
                throw new BusinessException("Số ca phép năm của cửa hàng chưa hợp lệ.");
            }
            for (Map.Entry<Integer, Long> entry : requestedByYear.entrySet()) {
                int year = entry.getKey();
                int requested = Math.toIntExact(entry.getValue());
                int used = usedSlotCount(employee.getId(), year);
                int pending = Math.toIntExact(reservedSlots(employee.getId(), year, null));
                int available = allowance - used - pending;
                if (requested > available) {
                    throw new BusinessException("Năm " + year + " chỉ còn "
                            + Math.max(available, 0) + " ca phép, không đủ " + requested + " ca.");
                }
                years.add(new LeavePreviewResponse.YearDetail(year, requested, allowance, used, pending, available));
            }
        }
        return new LeavePreviewResponse(selected.size(), years, leaveType.isConsumesAnnualLeave(),
                leaveType.getLabel(), PROCESS_NOTE);
    }

    private int usedSlotCount(Integer employeeId, int year) {
        Optional<Integer> used = leaveBalanceRepository.findUsedSlots(employeeId, year);
        if (used == null || used.isEmpty()) {
            return 0;
        }
        return used.get();
    }

    private long reservedSlots(Integer employeeId, int year, Integer excludeRequestId) {
        return leaveRequestSlotRepository.countReservedSlots(
                employeeId, ApprovalStatus.PENDING, LeaveType.ANNUAL,
                LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), excludeRequestId);
    }

    private LocalDate[] resolveRange(LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null && toDate == null) {
            LocalDate monday = VietnamTime.today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            return new LocalDate[]{monday, monday.plusDays(6)};
        }
        if (fromDate == null || toDate == null) {
            throw new BusinessException("Chọn đủ ngày bắt đầu và ngày kết thúc của bộ lọc ca.");
        }
        if (toDate.isBefore(fromDate)) {
            throw new BusinessException("Ngày kết thúc bộ lọc phải từ ngày bắt đầu trở đi.");
        }
        if (fromDate.plusDays(MAX_RANGE_DAYS).isBefore(toDate)) {
            throw new BusinessException("Chỉ tra cứu tối đa 42 ngày mỗi lần.");
        }
        return new LocalDate[]{fromDate, toDate};
    }

    private Map<Integer, ApprovalStatus> blockingByAssignment(List<ShiftAssignment> rows) {
        if (rows.isEmpty()) {
            return Map.of();
        }
        List<Integer> ids = rows.stream().map(ShiftAssignment::getId).toList();
        List<LeaveRequestSlotRepository.BlockingSlot> blocking =
                leaveRequestSlotRepository.findBlockingSlots(ids, BLOCKING_STATUSES);
        Map<Integer, ApprovalStatus> result = new HashMap<>();
        if (blocking != null) {
            for (LeaveRequestSlotRepository.BlockingSlot slot : blocking) {
                result.putIfAbsent(slot.getAssignmentId(), slot.getStatus());
            }
        }
        return result;
    }

    private Set<String> attendedKeys(Integer employeeId, LocalDate fromDate, LocalDate toDate) {
        List<AttendanceRepository.ShiftWorkDate> rows = attendanceRepository.findShiftDates(employeeId, fromDate, toDate);
        Set<String> keys = new java.util.HashSet<>();
        if (rows != null) {
            for (AttendanceRepository.ShiftWorkDate row : rows) {
                keys.add(row.getShiftId() + "_" + row.getWorkDate());
            }
        }
        return keys;
    }

    private boolean notStarted(ShiftAssignment row) {
        Shift shift = row.getShift();
        return shift != null && !VietnamTime.isBeforeNow(row.getWorkDate(), shift.getStartTime());
    }

    private LeaveSlotOption toOption(ShiftAssignment row, ApprovalStatus leaveStatus, Set<String> attended) {
        Shift shift = row.getShift();
        String storeName = shift.getStore() == null ? "" : shift.getStore().getStoreName();
        String scheduleStatus = row.getStatus() == AssignmentStatus.ASSIGNED ? "Đã phân công" : "Đã hủy";
        String leaveLabel = leaveStatus == null ? "" : leaveStatus.getLabel();
        String blocked = null;
        boolean selectable = row.getStatus() == AssignmentStatus.ASSIGNED && leaveStatus == null
                && !VietnamTime.isBeforeNow(row.getWorkDate(), shift.getStartTime())
                && !attended.contains(shift.getId() + "_" + row.getWorkDate());
        if (row.getStatus() != AssignmentStatus.ASSIGNED) {
            blocked = "Ca không còn hiệu lực.";
        } else if (leaveStatus != null) {
            blocked = "Ca đã có đơn nghỉ " + leaveLabel.toLowerCase() + ".";
        } else if (VietnamTime.isBeforeNow(row.getWorkDate(), shift.getStartTime())) {
            blocked = "Ca đã bắt đầu hoặc đã qua.";
        } else if (attended.contains(shift.getId() + "_" + row.getWorkDate())) {
            blocked = "Ca đã có dữ liệu chấm công.";
        }
        return new LeaveSlotOption(row.getId(), row.getWorkDate(), weekday(row.getWorkDate()),
                shift.getShiftName(), shift.getStartTime(), shift.getEndTime(), storeName,
                scheduleStatus, leaveLabel, selectable, blocked);
    }

    private Map<Integer, List<LeaveSlotItem>> slotsByRequest(Collection<LeaveRequest> requests) {
        List<Integer> ids = requests.stream().map(LeaveRequest::getId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<LeaveRequestSlot> links = leaveRequestSlotRepository.findDetailedByRequestIds(ids);
        Map<Integer, List<LeaveSlotItem>> grouped = new HashMap<>();
        if (links == null) {
            return grouped;
        }
        for (LeaveRequestSlot link : links) {
            if (link.getLeaveRequest() == null || link.getAssignment() == null) {
                continue;
            }
            grouped.computeIfAbsent(link.getLeaveRequest().getId(), ignored -> new ArrayList<>())
                    .add(toItem(link.getAssignment()));
        }
        return grouped;
    }

    private List<LeaveSlotItem> toItems(List<ShiftAssignment> assignments) {
        return assignments.stream().filter(Objects::nonNull).map(this::toItem).toList();
    }

    private LeaveSlotItem toItem(ShiftAssignment row) {
        Shift shift = row.getShift();
        String storeName = shift == null || shift.getStore() == null ? "" : shift.getStore().getStoreName();
        return new LeaveSlotItem(row.getId(), row.getWorkDate(), weekday(row.getWorkDate()),
                shift == null ? "" : shift.getShiftName(),
                shift == null ? null : shift.getStartTime(),
                shift == null ? null : shift.getEndTime(),
                storeName,
                row.getStatus() == AssignmentStatus.ASSIGNED ? "Đã phân công" : "Đã hủy");
    }

    static String weekday(LocalDate date) {
        if (date == null) {
            return "";
        }
        return switch (date.getDayOfWeek()) {
            case MONDAY -> "Thứ hai";
            case TUESDAY -> "Thứ ba";
            case WEDNESDAY -> "Thứ tư";
            case THURSDAY -> "Thứ năm";
            case FRIDAY -> "Thứ sáu";
            case SATURDAY -> "Thứ bảy";
            case SUNDAY -> "Chủ nhật";
        };
    }

    private Employee requireReadableEmployee(AuthenticatedUser user) {
        if (user == null || user.getEmployeeId() == null || user.getStoreId() == null) {
            throw new BusinessException("Tài khoản chưa được gắn với nhân viên/cửa hàng.");
        }
        Employee employee = employeeRepository.findByIdWithStoreAndManager(user.getEmployeeId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy nhân viên."));
        assertEmployment(user, employee);
        return employee;
    }

    private Employee requireEmployee(AuthenticatedUser user, boolean writeLock) {
        if (user == null || user.getEmployeeId() == null || user.getStoreId() == null) {
            throw new BusinessException("Tài khoản chưa được gắn với nhân viên/cửa hàng.");
        }
        Employee employee = (writeLock
                ? employeeRepository.lockByIdForLeaveRequest(user.getEmployeeId())
                : employeeRepository.findByIdWithStoreAndManager(user.getEmployeeId()))
                .orElseThrow(() -> new BusinessException("Không tìm thấy nhân viên."));
        assertEmployment(user, employee);
        return employee;
    }

    private void assertEmployment(AuthenticatedUser user, Employee employee) {
        if (!user.isActive()) {
            throw new BusinessException("Tài khoản đã bị khóa, không thể thao tác đơn nghỉ phép.");
        }
        if (employee.getStatus() == EmployeeStatus.TERMINATED) {
            throw new BusinessException("Nhân viên đã nghỉ việc, không thể gửi hoặc hủy đơn.");
        }
        if (employee.getStore() == null
                || !Objects.equals(employee.getStore().getId(), user.getStoreId())
                || employee.getStatus() != EmployeeStatus.ACTIVE
                || !Boolean.TRUE.equals(employee.getStore().getIsActive())) {
            throw new BusinessException("Nhân viên hoặc cửa hàng không còn hợp lệ. Vui lòng đăng nhập lại.");
        }
    }

    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String digits = phone.replaceAll("[\\s.\\-]", "");
        if (!digits.matches("^[0-9]{9,15}$")) {
            throw new BusinessException("Số điện thoại gồm 9 đến 15 chữ số.");
        }
        return digits;
    }

    private LeaveRequest requireOwnRequest(Integer id, Integer employeeId) {
        if (id == null) {
            throw new BusinessException("Không tìm thấy đơn nghỉ phép.");
        }
        LeaveRequest request = leaveRequestRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Không tìm thấy đơn nghỉ phép."));
        if (request.getEmployee() == null || !Objects.equals(request.getEmployee().getId(), employeeId)) {
            throw new BusinessException("Không tìm thấy đơn nghỉ phép.");
        }
        return request;
    }

    private void notifyManager(Employee employee, LeaveRequest request) {
        Employee manager = employee.getStore() == null ? null : employee.getStore().getManager();
        if (manager == null || Objects.equals(manager.getId(), employee.getId())) {
            return;
        }
        notifyUser(manager.getId(),
                "Có đơn xin nghỉ mới",
                employee.getFullName() + " gửi đơn " + request.getLeaveType().getLabel()
                        + " gồm " + request.getRequestedWorkDays() + " ca. Đơn đang chờ duyệt.",
                NotificationType.LEAVE_REQUEST_SUBMITTED,
                request.getId());
    }

    private void notifyManagerOfCancel(Employee employee, LeaveRequest request, boolean refunded) {
        Employee manager = employee.getStore() == null ? null : employee.getStore().getManager();
        if (manager == null) {
            return;
        }
        notifyUser(manager.getId(),
                "Nhân viên đã hủy đơn nghỉ",
                employee.getFullName() + " đã hủy đơn #" + request.getId()
                        + (refunded ? ". Ca phép năm đã được hoàn lại." : "."),
                NotificationType.LEAVE_REQUEST_CANCELLED,
                request.getId());
    }

    private void notifyUser(Integer employeeId, String title, String message, NotificationType type,
                            Integer requestId) {
        if (employeeId == null || notificationService == null) {
            return;
        }
        Optional<User> recipient = userRepository.findByEmployee_Id(employeeId);
        if (recipient == null || recipient.isEmpty()) {
            return;
        }
        notificationService.notifyUsers(List.of(recipient.get()), title, message, type,
                "LEAVE_REQUEST", requestId);
    }

    private void assertPage(int page) {
        if (page < 0 || page > 100_000) {
            throw new BusinessException("Trang danh sách không hợp lệ.");
        }
    }

    private void assertDateRange(LocalDate fromDate, LocalDate toDate) {
        if (fromDate != null && toDate != null && toDate.isBefore(fromDate)) {
            throw new BusinessException("Ngày kết thúc bộ lọc phải từ ngày bắt đầu trở đi.");
        }
    }

    private LeaveRequestResponse toResponse(LeaveRequest item, List<LeaveSlotItem> slots) {
        LeaveType type = item.getLeaveType() == null ? LeaveType.ANNUAL : item.getLeaveType();
        ApprovalStatus status = item.getStatus();
        boolean cancellable = status == ApprovalStatus.PENDING || status == ApprovalStatus.APPROVED;
        return LeaveRequestResponse.builder()
                .id(item.getId())
                .employeeId(item.getEmployee().getId())
                .employeeName(item.getEmployee().getFullName())
                .startDate(item.getStartDate())
                .endDate(item.getEndDate())
                .leaveType(type)
                .leaveTypeLabel(type.getLabel())
                .requestedWorkDays(item.getRequestedWorkDays())
                .deductsAnnualLeave(type.isConsumesAnnualLeave())
                .contactPhone(item.getContactPhone())
                .reason(item.getReason())
                .status(status)
                .statusLabel(status == null ? "" : status.getLabel())
                .cancellable(cancellable)
                .approvedById(item.getApprovedBy() != null ? item.getApprovedBy().getId() : null)
                .approvedByName(reviewerName(item))
                .createdAt(item.getCreatedAt())
                .resolvedDate(item.getResolvedDate())
                .rejectReason(item.getRejectReason())
                .slots(slots)
                .build();
    }

    private String reviewerName(LeaveRequest item) {
        if (item.getApprovedBy() == null) {
            return null;
        }
        if (item.getApprovedBy().getEmployee() != null
                && item.getApprovedBy().getEmployee().getFullName() != null) {
            return item.getApprovedBy().getEmployee().getFullName();
        }
        return item.getApprovedBy().getUsername();
    }
}

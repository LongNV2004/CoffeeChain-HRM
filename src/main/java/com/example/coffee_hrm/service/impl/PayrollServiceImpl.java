package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.ApprovalStatus;
import com.example.coffee_hrm.common.enums.AssignmentStatus;
import com.example.coffee_hrm.common.enums.AttendanceReportStatus;
import com.example.coffee_hrm.common.enums.PayrollStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.response.PayrollBoardView;
import com.example.coffee_hrm.dto.response.PayrollDetailView;
import com.example.coffee_hrm.entity.Attendance;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.MonthlyAttendanceReport;
import com.example.coffee_hrm.entity.EmploymentContract;
import com.example.coffee_hrm.entity.MonthlyPayroll;
import com.example.coffee_hrm.entity.OvertimeRequest;
import com.example.coffee_hrm.entity.PayrollEmployeeLine;
import com.example.coffee_hrm.entity.PayrollShiftLine;
import com.example.coffee_hrm.entity.Shift;
import com.example.coffee_hrm.entity.ShiftAssignment;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.repository.AttendanceRepository;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.EmploymentContractRepository;
import com.example.coffee_hrm.repository.MonthlyAttendanceReportRepository;
import com.example.coffee_hrm.repository.MonthlyPayrollRepository;
import com.example.coffee_hrm.repository.OvertimeRequestRepository;
import com.example.coffee_hrm.repository.ShiftAssignmentRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.repository.UserRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.PayrollService;
import com.example.coffee_hrm.service.impl.ContractValidityRules;
import com.example.coffee_hrm.service.support.PayrollCalculator;
import com.example.coffee_hrm.service.support.OvertimeShifts;
import com.example.coffee_hrm.service.support.PayrollEmployeeDraft;
import com.example.coffee_hrm.service.support.PeriodLeave;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PayrollServiceImpl implements PayrollService {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final StoreRepository storeRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final AttendanceRepository attendanceRepository;
    private final EmploymentContractRepository employmentContractRepository;
    private final MonthlyPayrollRepository payrollRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final MonthlyAttendanceReportRepository reportRepository;
    private final OvertimeRequestRepository overtimeRequestRepository;

    static final String REPORT_SENT_MESSAGE =
            "Báo cáo cửa hàng kỳ này đã gửi Admin. Chỉ được tính lại lương khi Admin trả lại báo cáo.";

    @Override
    @Transactional(readOnly = true)
    public PayrollBoardView board(AuthenticatedUser user,
                                  Integer year,
                                  Integer month,
                                  String period,
                                  Integer storeId,
                                  Integer employeeId,
                                  String status) {
        return prepare(user, year, month, period, storeId, employeeId, status).board();
    }

    @Override
    @Transactional(readOnly = true)
    public PayrollDetailView detail(AuthenticatedUser user, int year, int month, Integer employeeId) {
        if (employeeId == null) {
            throw new BusinessException("Thiếu nhân viên cần xem.");
        }
        if (user.getRoleName() == RoleName.STAFF && !employeeId.equals(user.getEmployeeId())) {
            throw new BusinessException("Bạn chỉ xem được bảng lương của chính mình.");
        }
        Employee employee = employeeRepository.findByIdWithStore(employeeId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy nhân viên."));
        Store store = employee.getStore();
        if (store == null || store.getId() == null) {
            throw new BusinessException("Nhân viên chưa thuộc cửa hàng.");
        }
        if (user.getRoleName() == RoleName.MANAGER) {
            Store managed = managerStore(user);
            if (!managed.getId().equals(store.getId())) {
                throw new BusinessException("Nhân viên không thuộc cửa hàng bạn quản lý.");
            }
        }
        YearMonth selected = requirePeriod(year, month);
        PeriodSnapshot snapshot = load(store, selected);
        PayrollEmployeeDraft draft = snapshot.employees().stream()
                .filter(row -> employeeId.equals(row.employeeId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Nhân viên không có lịch phân ca trong kỳ này."));
        return toDetail(user, selected, store, snapshot.status(), draft, snapshot.overtimeIds());
    }

    @Override
    @Transactional
    public void calculate(AuthenticatedUser user, int year, int month) {
        if (user.getRoleName() != RoleName.MANAGER) {
            throw new BusinessException("Chỉ quản lý cửa hàng mới được tính lương.");
        }
        YearMonth selected = requirePeriod(year, month);
        Store store = managerStore(user);
        requireReportOpen(store, selected);
        MonthlyPayroll payroll = payrollRepository.findPeriod(store.getId(), selected.getYear(), selected.getMonthValue())
                .orElseGet(() -> MonthlyPayroll.builder()
                        .store(store)
                        .payrollYear(selected.getYear())
                        .payrollMonth(selected.getMonthValue())
                        .employeeLines(new ArrayList<>())
                        .build());
        if (payroll.getStatus() == PayrollStatus.DA_CHOT) {
            throw new BusinessException("Bảng lương đã chốt, không tính lại.");
        }
        payroll.setStatus(PayrollStatus.DA_TINH);
        payroll.setCalculatedAt(VietnamTime.now());
        if (user.getUserId() != null) {
            payroll.setCalculatedBy(userRepository.getReferenceById(user.getUserId()));
        }
        List<PayrollEmployeeLine> freshLines = toLines(buildLive(store.getId(), selected));
        if (payroll.getId() != null && payroll.getEmployeeLines() != null && !payroll.getEmployeeLines().isEmpty()) {
            // Tính lại: xóa hẳn các dòng cũ xuống DB trước. Hibernate chạy INSERT trước DELETE khi flush,
            // nên nếu thay danh sách trong một lần sẽ vi phạm UX_PayrollEmployeeLines_PeriodEmployee.
            payroll.replaceEmployees(List.of());
            payrollRepository.saveAndFlush(payroll);
        }
        payroll.replaceEmployees(freshLines);
        payrollRepository.save(payroll);
    }

    @Override
    @Transactional
    public void lock(AuthenticatedUser user, int year, int month) {
        if (user.getRoleName() != RoleName.MANAGER) {
            throw new BusinessException("Chỉ quản lý cửa hàng mới được chốt lương.");
        }
        YearMonth selected = requirePeriod(year, month);
        Store store = managerStore(user);
        requireReportOpen(store, selected);
        lockPayroll(user, store, selected, false);
    }

    @Override
    @Transactional
    public void lockForReport(AuthenticatedUser user, int year, int month) {
        if (user.getRoleName() != RoleName.MANAGER) {
            throw new BusinessException("Chỉ quản lý cửa hàng mới được gửi báo cáo lương.");
        }
        YearMonth selected = requirePeriod(year, month);
        lockPayroll(user, managerStore(user), selected, true);
    }

    @Override
    @Transactional
    public void reopenForRevision(Integer storeId, int year, int month) {
        if (storeId == null) {
            return;
        }
        payrollRepository.findPeriod(storeId, year, month).ifPresent(payroll -> {
            if (payroll.getStatus() == PayrollStatus.DA_CHOT) {
                payroll.setStatus(PayrollStatus.DA_TINH);
                payroll.setLockedAt(null);
                payroll.setLockedBy(null);
                payrollRepository.save(payroll);
            }
        });
    }

    /** Manager không sửa được bảng lương khi báo cáo đang chờ Admin duyệt hoặc đã được duyệt. */
    private void requireReportOpen(Store store, YearMonth selected) {
        if (reportSent(store.getId(), selected)) {
            throw new BusinessException(REPORT_SENT_MESSAGE);
        }
    }

    private boolean reportSent(Integer storeId, YearMonth selected) {
        if (reportRepository == null || storeId == null) {
            return false;
        }
        Optional<MonthlyAttendanceReport> report = reportRepository.findCurrent(
                storeId, selected.getYear(), selected.getMonthValue());
        if (report == null || report.isEmpty() || report.get().getSubmittedAt() == null) {
            return false;
        }
        AttendanceReportStatus status = report.get().getStatus();
        return status == AttendanceReportStatus.SUBMITTED || status == AttendanceReportStatus.APPROVED;
    }

    private void lockPayroll(AuthenticatedUser user, Store store, YearMonth selected, boolean forReport) {
        String verb = forReport ? "gửi báo cáo" : "chốt lương";
        MonthlyPayroll payroll = payrollRepository.findPeriod(store.getId(), selected.getYear(), selected.getMonthValue())
                .orElseThrow(() -> new BusinessException("Hãy tính lương trước khi " + (forReport ? "gửi báo cáo." : "chốt.")));
        if (payroll.getStatus() == PayrollStatus.DA_CHOT) {
            if (forReport) {
                return;
            }
            throw new BusinessException("Bảng lương đã chốt.");
        }
        if (payroll.getStatus() != PayrollStatus.DA_TINH) {
            throw new BusinessException("Hãy tính lương trước khi " + (forReport ? "gửi báo cáo." : "chốt."));
        }
        List<PayrollEmployeeDraft> live = buildLive(store.getId(), selected);
        if (!matches(payroll, live)) {
            throw new BusinessException("Dữ liệu chấm công hoặc hợp đồng đã thay đổi. Hãy tính lương lại trước khi "
                    + (forReport ? "gửi báo cáo." : "chốt."));
        }
        int review = live.stream().mapToInt(PayrollEmployeeDraft::reviewShifts).sum();
        if (review > 0) {
            throw new BusinessException("Còn " + review + " ca thiếu dữ liệu, chưa thể " + verb + ".");
        }
        payroll.setStatus(PayrollStatus.DA_CHOT);
        payroll.setLockedAt(VietnamTime.now());
        if (user.getUserId() != null) {
            payroll.setLockedBy(userRepository.getReferenceById(user.getUserId()));
        }
        payrollRepository.save(payroll);
    }

    @Override
    public PayrollBoardView empty(AuthenticatedUser user) {
        YearMonth current = YearMonth.from(VietnamTime.today());
        return baseBoard(user, current, null, null, "")
                .statusKey("CHUA_TINH")
                .statusLabel("Chưa tính")
                .payableHoursLabel("0 giờ")
                .payrollFundLabel("0 đ")
                .build();
    }

    private Prepared prepare(AuthenticatedUser user,
                             Integer year,
                             Integer month,
                             String period,
                             Integer storeId,
                             Integer employeeId,
                             String status) {
        YearMonth selected = resolvePeriod(year, month, period);
        Integer requestedEmployee = employeeId;
        if (user.getRoleName() == RoleName.STAFF) {
            if ((employeeId != null && !employeeId.equals(user.getEmployeeId()))
                    || (storeId != null && user.getStoreId() != null && !storeId.equals(user.getStoreId()))) {
                throw new BusinessException("Bạn chỉ xem được bảng lương của chính mình.");
            }
            requestedEmployee = user.getEmployeeId();
        }
        List<Store> dropdown = List.of();
        List<Store> queried;
        if (user.getRoleName() == RoleName.ADMIN) {
            dropdown = storeRepository.findAllWithManager().stream()
                    .sorted(Comparator.comparing(store -> store.getStoreName() == null ? "" : store.getStoreName(),
                            String.CASE_INSENSITIVE_ORDER))
                    .toList();
            if (storeId != null) {
                Store chosen = dropdown.stream()
                        .filter(store -> storeId.equals(store.getId()))
                        .findFirst()
                        .orElseThrow(() -> new BusinessException("Không tìm thấy cửa hàng."));
                queried = List.of(chosen);
            } else {
                queried = dropdown;
            }
        } else if (user.getRoleName() == RoleName.MANAGER) {
            queried = List.of(managerStore(user));
        } else {
            Employee self = employeeRepository.findByIdWithStore(user.getEmployeeId())
                    .orElseThrow(() -> new BusinessException("Tài khoản chưa gắn nhân viên."));
            if (self.getStore() == null) {
                throw new BusinessException("Nhân viên chưa thuộc cửa hàng.");
            }
            queried = List.of(self.getStore());
        }
        List<PeriodSnapshot> snapshots = new ArrayList<>();
        for (Store store : queried) {
            snapshots.add(load(store, selected));
        }
        List<Listed> listed = new ArrayList<>();
        for (PeriodSnapshot snapshot : snapshots) {
            for (PayrollEmployeeDraft draft : snapshot.employees()) {
                listed.add(new Listed(
                        toRow(snapshot.store(), snapshot.status(), draft, snapshot.overtimeIds()),
                        toDetail(user, selected, snapshot.store(), snapshot.status(), draft, snapshot.overtimeIds()),
                        draft.expectedSalary(),
                        draft.overtimeSalary(snapshot.overtimeIds()),
                        draft.reviewShifts()));
            }
        }
        listed.sort(Comparator.comparing((Listed item) -> item.row().getStoreName(), Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(item -> item.row().getEmployeeName(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(item -> item.row().getEmployeeId()));
        String statusFilter = normalizeStatus(status);
        if (user.getRoleName() == RoleName.STAFF) {
            // Nhân viên chỉ thấy dòng của chính mình: không lộ tên, số ca thiếu dữ liệu hay lương của người khác.
            Integer self = requestedEmployee;
            listed.removeIf(item -> !Objects.equals(self, item.row().getEmployeeId()));
        }
        List<PayrollBoardView.EmployeeOption> employeeOptions = listed.stream()
                .map(item -> PayrollBoardView.EmployeeOption.builder()
                        .id(item.row().getEmployeeId())
                        .label(item.row().getEmployeeCode() + " — " + item.row().getEmployeeName())
                        .build())
                .toList();
        List<PayrollBoardView.Row> shown = new ArrayList<>();
        List<PayrollDetailView> shownDetails = new ArrayList<>();
        BigDecimal fund = BigDecimal.ZERO;
        BigDecimal overtimeFund = BigDecimal.ZERO;
        int review = 0;
        for (Listed item : listed) {
            review += item.reviewShifts();
            if (requestedEmployee != null && !requestedEmployee.equals(item.row().getEmployeeId())) {
                continue;
            }
            if (!statusFilter.isEmpty() && !statusFilter.equals(item.row().getStatusKey())) {
                continue;
            }
            shown.add(item.row());
            shownDetails.add(item.detail());
            fund = fund.add(item.salary() == null ? BigDecimal.ZERO : item.salary());
            overtimeFund = overtimeFund.add(item.overtimeSalary() == null ? BigDecimal.ZERO : item.overtimeSalary());
        }
        int hours = shown.stream().mapToInt(PayrollBoardView.Row::getPayableHours).sum();
        int overtimeShifts = shown.stream().mapToInt(PayrollBoardView.Row::getOvertimeShifts).sum();
        PayrollStatus singleStatus = queried.size() == 1 ? snapshots.getFirst().status() : null;
        boolean locked = singleStatus == PayrollStatus.DA_CHOT;
        boolean reportLocked = user.getRoleName() == RoleName.MANAGER && queried.size() == 1
                && reportSent(queried.getFirst().getId(), selected);
        PayrollBoardView board = baseBoard(user, selected, user.getRoleName() == RoleName.ADMIN ? storeId : null, requestedEmployee, statusFilter)
                .canCalculate(user.getRoleName() == RoleName.MANAGER && !locked && !reportLocked)
                .canLock(user.getRoleName() == RoleName.MANAGER && singleStatus == PayrollStatus.DA_TINH && !reportLocked)
                .reportLocked(reportLocked)
                .reportLockMessage(reportLocked ? REPORT_SENT_MESSAGE : null)
                .locked(locked)
                .statusKey(singleStatus == null ? "CHUA_TINH" : singleStatus.name())
                .statusLabel(queried.size() == 1
                        ? (singleStatus == null ? "Chưa tính" : singleStatus.label())
                        : "Toàn chuỗi")
                .employeeCount(shown.size())
                .payableHours(hours)
                .payableHoursLabel(hours + " giờ")
                .payrollFundLabel(money(fund))
                .regularFundLabel(money(fund.subtract(overtimeFund)))
                .overtimeFundLabel(money(overtimeFund))
                .overtimeShifts(overtimeShifts)
                .reviewShifts(review)
                .stores(dropdown.stream()
                        .map(store -> PayrollBoardView.StoreOption.builder()
                                .id(store.getId())
                                .name(store.getStoreName())
                                .build())
                        .toList())
                .employees(employeeOptions)
                .rows(shown)
                .build();
        return new Prepared(board, shownDetails);
    }

    private PayrollBoardView.PayrollBoardViewBuilder baseBoard(AuthenticatedUser user,
                                                               YearMonth selected,
                                                               Integer storeId,
                                                               Integer employeeId,
                                                               String status) {
        return PayrollBoardView.builder()
                .year(selected.getYear())
                .month(selected.getMonthValue())
                .monthValue(monthValue(selected))
                .periodLabel("Tháng " + selected.getMonthValue() + "/" + selected.getYear())
                .homePath(home(user))
                .navRole(nav(user))
                .canFilterStore(user.getRoleName() == RoleName.ADMIN)
                .selectedStoreId(storeId)
                .selectedEmployeeId(employeeId)
                .selectedStatus(status == null ? "" : status)
                .employeeCount(0)
                .payableHours(0)
                .reviewShifts(0)
                .rows(List.of())
                .stores(List.of())
                .employees(List.of());
    }

    private PeriodSnapshot load(Store store, YearMonth selected) {
        Optional<MonthlyPayroll> found = payrollRepository.findPeriod(
                store.getId(), selected.getYear(), selected.getMonthValue());
        Set<Integer> overtimeIds = overtimeAssignmentIds(store.getId(), selected);
        if (found == null || found.isEmpty()) {
            return new PeriodSnapshot(store, null, buildLive(store.getId(), selected), overtimeIds);
        }
        MonthlyPayroll payroll = found.get();
        return new PeriodSnapshot(store, payroll.getStatus(), fromSaved(payroll), overtimeIds);
    }

    /**
     * Ca tăng ca của kỳ. Ca tăng ca đã duyệt là ca phân công đã công bố nên đã được {@link #buildLive} tính lương
     * một lần theo quy tắc ca thường; ở đây chỉ nhận diện để tách "lương tăng ca" khỏi tổng, không cộng thêm.
     */
    private Set<Integer> overtimeAssignmentIds(Integer storeId, YearMonth selected) {
        if (overtimeRequestRepository == null) {
            return Set.of();
        }
        LocalDate start = selected.atDay(1);
        LocalDate end = selected.atEndOfMonth();
        List<OvertimeRequest> approved = overtimeRequestRepository.findByStoreAndDateRangeAndStatuses(
                storeId, start, end, List.of(ApprovalStatus.APPROVED));
        if (approved == null || approved.isEmpty()) {
            return Set.of();
        }
        // Chỉ đơn cũ chưa lưu id ca mới cần tra danh sách ca để khớp theo nhân viên + ca + ngày.
        List<ShiftAssignment> assignments = OvertimeShifts.needsAssignmentLookup(approved)
                ? shiftAssignmentRepository.findPublishedAssignedForStoreBetween(storeId, start, end, AssignmentStatus.ASSIGNED)
                : null;
        return OvertimeShifts.assignmentIds(approved, assignments);
    }

    private List<PayrollEmployeeDraft> buildLive(Integer storeId, YearMonth selected) {
        LocalDate start = selected.atDay(1);
        LocalDate end = selected.atEndOfMonth();
        List<ShiftAssignment> assignments = shiftAssignmentRepository.findPublishedAssignedForStoreBetween(
                storeId, start, end, AssignmentStatus.ASSIGNED);
        List<Attendance> records = attendanceRepository.findForMonthlyReport(storeId, start, end);
        List<EmploymentContract> contracts = employmentContractRepository.findByStoreId(storeId);
        Map<String, List<Attendance>> bySlot = new HashMap<>();
        if (records != null) {
            for (Attendance attendance : records) {
                String key = slotKey(attendance);
                if (key != null) {
                    bySlot.computeIfAbsent(key, ignored -> new ArrayList<>()).add(attendance);
                }
            }
        }
        Map<Integer, List<PayrollEmployeeDraft.Shift>> shifts = new LinkedHashMap<>();
        Map<Integer, String> names = new LinkedHashMap<>();
        List<ShiftAssignment> ordered = assignments == null ? List.of() : assignments.stream()
                .filter(assignment -> assignment.getEmployee() != null && assignment.getEmployee().getId() != null)
                .filter(assignment -> assignment.getShift() != null && assignment.getShift().getId() != null)
                .filter(assignment -> assignment.getWorkDate() != null)
                .sorted(Comparator.comparing(ShiftAssignment::getWorkDate)
                        .thenComparing(assignment -> assignment.getShift().getStartTime(), Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(assignment -> assignment.getId() == null ? 0 : assignment.getId()))
                .toList();
        for (ShiftAssignment assignment : ordered) {
            Integer employeeId = assignment.getEmployee().getId();
            names.putIfAbsent(employeeId, assignment.getEmployee().getFullName() == null
                    ? "—" : assignment.getEmployee().getFullName());
            String key = employeeId + ":" + assignment.getShift().getId() + ":" + assignment.getWorkDate();
            List<Attendance> matches = bySlot.getOrDefault(key, List.of());
            boolean duplicate = matches.size() > 1;
            Attendance attendance = matches.size() == 1 ? matches.getFirst() : null;
            LocalTime startTime = scheduledStart(assignment, attendance);
            LocalTime endTime = scheduledEnd(assignment, attendance);
            PayrollCalculator.Outcome outcome = PayrollCalculator.evaluate(
                    assignment.getWorkDate(),
                    startTime,
                    endTime,
                    duplicate,
                    attendance != null && attendance.getCheckInTime() != null,
                    attendance != null && attendance.getCheckOutTime() != null,
                    minutes(attendance == null ? null : attendance.getLateMinutes()),
                    minutes(attendance == null ? null : attendance.getEarlyLeaveMinutes()),
                    rateFor(contracts, employeeId, assignment.getWorkDate()));
            shifts.computeIfAbsent(employeeId, ignored -> new ArrayList<>()).add(new PayrollEmployeeDraft.Shift(
                    assignment.getId(),
                    attendance == null ? null : attendance.getId(),
                    assignment.getWorkDate(),
                    shiftName(assignment, attendance),
                    startTime,
                    endTime,
                    attendance == null ? null : attendance.getCheckInTime(),
                    attendance == null ? null : attendance.getCheckOutTime(),
                    attendanceLabel(attendance, duplicate),
                    outcome.scheduledHours(),
                    outcome.lateDeducted(),
                    outcome.earlyDeducted(),
                    outcome.payableHours(),
                    outcome.hourlyRate(),
                    outcome.shiftSalary(),
                    outcome.payable(),
                    outcome.needsReview(),
                    outcome.unpaidReason()));
        }
        // Ca nghỉ phép đã duyệt: dùng chung PeriodLeave với báo cáo chấm công để số liệu khớp nhau.
        for (PeriodLeave.Slot slot : PeriodLeave.collect(
                shiftAssignmentRepository.findApprovedLeaveSlotsForStoreBetween(storeId, start, end),
                assignments, records)) {
            names.putIfAbsent(slot.employeeId(), slot.employeeName() == null ? "—" : slot.employeeName());
            PayrollCalculator.Outcome outcome = PayrollCalculator.leave(
                    slot.workDate(), slot.start(), slot.end(), slot.paid(), slot.label(),
                    slot.paid() ? rateFor(contracts, slot.employeeId(), slot.workDate()) : null);
            shifts.computeIfAbsent(slot.employeeId(), ignored -> new ArrayList<>()).add(new PayrollEmployeeDraft.Shift(
                    slot.assignmentId(),
                    null,
                    slot.workDate(),
                    slot.shiftName() == null || slot.shiftName().isBlank() ? "Ca làm" : slot.shiftName(),
                    slot.start(),
                    slot.end(),
                    null,
                    null,
                    slot.label(),
                    outcome.scheduledHours(),
                    false,
                    false,
                    outcome.payableHours(),
                    outcome.hourlyRate(),
                    outcome.shiftSalary(),
                    outcome.payable(),
                    outcome.needsReview(),
                    outcome.unpaidReason()));
        }
        List<PayrollEmployeeDraft> drafts = new ArrayList<>();
        for (Map.Entry<Integer, List<PayrollEmployeeDraft.Shift>> entry : shifts.entrySet()) {
            List<PayrollEmployeeDraft.Shift> sorted = new ArrayList<>(entry.getValue());
            sorted.sort(Comparator.comparing(PayrollEmployeeDraft.Shift::workDate)
                    .thenComparing(PayrollEmployeeDraft.Shift::scheduledStart, Comparator.nullsLast(Comparator.naturalOrder())));
            drafts.add(new PayrollEmployeeDraft(entry.getKey(), names.get(entry.getKey()), List.copyOf(sorted)));
        }
        drafts.sort(Comparator.comparing(PayrollEmployeeDraft::employeeName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(PayrollEmployeeDraft::employeeId));
        return drafts;
    }

    private List<PayrollEmployeeDraft> fromSaved(MonthlyPayroll payroll) {
        List<PayrollEmployeeDraft> drafts = new ArrayList<>();
        List<PayrollEmployeeLine> lines = payroll.getEmployeeLines() == null ? List.of() : payroll.getEmployeeLines();
        for (PayrollEmployeeLine line : lines) {
            if (line.getEmployee() == null || line.getEmployee().getId() == null) {
                continue;
            }
            List<PayrollShiftLine> stored = line.getShiftLines() == null
                    ? List.of() : new ArrayList<>(line.getShiftLines());
            stored.sort(Comparator.comparing(PayrollShiftLine::getWorkDate, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(PayrollShiftLine::getScheduledStartTime, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(shift -> shift.getId() == null ? 0 : shift.getId()));
            List<PayrollEmployeeDraft.Shift> shifts = new ArrayList<>();
            for (PayrollShiftLine shift : stored) {
                shifts.add(new PayrollEmployeeDraft.Shift(
                        shift.getAssignmentId(),
                        shift.getAttendanceId(),
                        shift.getWorkDate(),
                        shift.getShiftName(),
                        shift.getScheduledStartTime(),
                        shift.getScheduledEndTime(),
                        shift.getCheckInTime(),
                        shift.getCheckOutTime(),
                        shift.getAttendanceStatusLabel(),
                        shift.getScheduledHours() == null ? 0 : shift.getScheduledHours(),
                        Boolean.TRUE.equals(shift.getLateDeducted()),
                        Boolean.TRUE.equals(shift.getEarlyDeducted()),
                        shift.getPayableHours() == null ? 0 : shift.getPayableHours(),
                        shift.getHourlyRate(),
                        shift.getShiftSalary() == null ? BigDecimal.ZERO : shift.getShiftSalary(),
                        Boolean.TRUE.equals(shift.getPayable()),
                        needsReview(shift),
                        shift.getUnpaidReason()));
            }
            String name = line.getEmployee().getFullName() == null ? "—" : line.getEmployee().getFullName();
            drafts.add(new PayrollEmployeeDraft(line.getEmployee().getId(), name, List.copyOf(shifts)));
        }
        drafts.sort(Comparator.comparing(PayrollEmployeeDraft::employeeName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(PayrollEmployeeDraft::employeeId));
        return drafts;
    }

    /** Ca có lý do không trả lương cần xử lý, trừ ca nghỉ không hưởng lương (đúng chính sách, không cần xem lại). */
    private boolean needsReview(PayrollShiftLine shift) {
        if (shift.getUnpaidReason() == null || shift.getUnpaidReason().isBlank()) {
            return false;
        }
        boolean leave = shift.getAttendanceId() == null && PeriodLeave.isLeaveLabel(shift.getAttendanceStatusLabel());
        return !leave || Boolean.TRUE.equals(shift.getPayable()) || !shift.getUnpaidReason().contains("không hưởng lương");
    }

    private List<PayrollEmployeeLine> toLines(List<PayrollEmployeeDraft> drafts) {
        List<PayrollEmployeeLine> lines = new ArrayList<>();
        for (PayrollEmployeeDraft draft : drafts) {
            PayrollEmployeeLine line = PayrollEmployeeLine.builder()
                    .employee(employeeRepository.getReferenceById(draft.employeeId()))
                    .assignedShiftCount(draft.assignedShifts())
                    .validShiftCount(draft.validShifts())
                    .reviewShiftCount(draft.reviewShifts())
                    .payableHours(draft.payableHours())
                    .hourlyRate(draft.hourlyRate())
                    .expectedSalary(draft.expectedSalary())
                    .shiftLines(new ArrayList<>())
                    .build();
            for (PayrollEmployeeDraft.Shift shift : draft.shifts()) {
                line.addShift(PayrollShiftLine.builder()
                        .assignmentId(shift.assignmentId())
                        .attendanceId(shift.attendanceId())
                        .workDate(shift.workDate())
                        .shiftName(shift.shiftName() == null ? "Ca làm" : shift.shiftName())
                        .scheduledStartTime(shift.scheduledStart())
                        .scheduledEndTime(shift.scheduledEnd())
                        .checkInTime(shift.checkIn())
                        .checkOutTime(shift.checkOut())
                        .attendanceStatusLabel(shift.attendanceStatus())
                        .scheduledHours(shift.scheduledHours())
                        .lateDeducted(shift.lateDeducted())
                        .earlyDeducted(shift.earlyDeducted())
                        .payableHours(shift.payableHours())
                        .hourlyRate(shift.hourlyRate())
                        .shiftSalary(shift.shiftSalary())
                        .payable(shift.payable())
                        .unpaidReason(shift.unpaidReason())
                        .build());
            }
            lines.add(line);
        }
        return lines;
    }

    private boolean matches(MonthlyPayroll payroll, List<PayrollEmployeeDraft> live) {
        Map<Integer, PayrollEmployeeLine> saved = new HashMap<>();
        if (payroll.getEmployeeLines() != null) {
            for (PayrollEmployeeLine line : payroll.getEmployeeLines()) {
                if (line.getEmployee() != null && line.getEmployee().getId() != null) {
                    saved.put(line.getEmployee().getId(), line);
                }
            }
        }
        if (saved.size() != live.size()) {
            return false;
        }
        for (PayrollEmployeeDraft draft : live) {
            PayrollEmployeeLine line = saved.get(draft.employeeId());
            if (line == null
                    || !Objects.equals(line.getAssignedShiftCount(), draft.assignedShifts())
                    || !Objects.equals(line.getPayableHours(), draft.payableHours())
                    || !Objects.equals(line.getReviewShiftCount(), draft.reviewShifts())
                    || !moneyEquals(line.getExpectedSalary(), draft.expectedSalary())
                    || !shiftsMatch(line, draft)) {
                return false;
            }
        }
        return true;
    }

    private boolean shiftsMatch(PayrollEmployeeLine line, PayrollEmployeeDraft draft) {
        List<PayrollShiftLine> stored = line.getShiftLines() == null ? List.of() : line.getShiftLines();
        if (stored.size() != draft.shifts().size()) {
            return false;
        }
        Map<Integer, PayrollShiftLine> byAssignment = new HashMap<>();
        for (PayrollShiftLine shift : stored) {
            if (shift.getAssignmentId() != null) {
                byAssignment.put(shift.getAssignmentId(), shift);
            }
        }
        for (PayrollEmployeeDraft.Shift shift : draft.shifts()) {
            PayrollShiftLine saved = byAssignment.get(shift.assignmentId());
            if (saved == null
                    || !Objects.equals(saved.getPayableHours(), shift.payableHours())
                    || !moneyEquals(saved.getShiftSalary(), shift.shiftSalary())
                    || !moneyEquals(saved.getHourlyRate(), shift.hourlyRate())
                    || !Objects.equals(blankToNull(saved.getUnpaidReason()), blankToNull(shift.unpaidReason()))) {
                return false;
            }
        }
        return true;
    }

    private PayrollBoardView.Row toRow(Store store, PayrollStatus status, PayrollEmployeeDraft draft, Set<Integer> overtimeIds) {
        return PayrollBoardView.Row.builder()
                .employeeId(draft.employeeId())
                .storeId(store.getId())
                .employeeCode(employeeCode(draft.employeeId()))
                .employeeName(draft.employeeName())
                .storeName(store.getStoreName())
                .assignedShifts(draft.assignedShifts())
                .leaveShifts(draft.leaveShifts())
                .paidLeaveShifts(draft.paidLeaveShifts())
                .payableHours(draft.payableHours())
                .hourlyRateLabel(rateLabel(draft))
                .expectedSalaryLabel(money(draft.expectedSalary()))
                .overtimeShifts(draft.overtimeShifts(overtimeIds))
                .regularSalaryLabel(money(draft.regularSalary(overtimeIds)))
                .overtimeSalaryLabel(money(draft.overtimeSalary(overtimeIds)))
                .statusKey(status == null ? "CHUA_TINH" : status.name())
                .statusLabel(status == null ? "Chưa tính" : status.label())
                .build();
    }

    private PayrollDetailView toDetail(AuthenticatedUser user,
                                       YearMonth selected,
                                       Store store,
                                       PayrollStatus status,
                                       PayrollEmployeeDraft draft,
                                       Set<Integer> overtimeIds) {
        List<PayrollDetailView.ShiftRow> shifts = new ArrayList<>();
        for (PayrollEmployeeDraft.Shift shift : draft.shifts()) {
            shifts.add(PayrollDetailView.ShiftRow.builder()
                    .dateLabel(shift.workDate() == null ? "—" : shift.workDate().format(DAY))
                    .shiftName(shift.shiftName() == null ? "Ca làm" : shift.shiftName())
                    .scheduleLabel(scheduleLabel(shift.scheduledStart(), shift.scheduledEnd()))
                    .checkInLabel(clock(shift.workDate(), shift.checkIn()))
                    .checkOutLabel(clock(shift.workDate(), shift.checkOut()))
                    .attendanceStatus(shift.attendanceStatus() == null ? "—" : shift.attendanceStatus())
                    .payableHours(shift.payableHours())
                    .hourlyRateLabel(shift.hourlyRate() == null ? "—" : money(shift.hourlyRate()) + "/giờ")
                    .shiftSalaryLabel(money(shift.shiftSalary()))
                    .payable(shift.payable())
                    .unpaidReason(shift.unpaidReason() == null || shift.unpaidReason().isBlank() ? "—" : shift.unpaidReason())
                    .overtime(shift.overtime(overtimeIds))
                    .build());
        }
        return PayrollDetailView.builder()
                .year(selected.getYear())
                .month(selected.getMonthValue())
                .periodLabel("Tháng " + selected.getMonthValue() + "/" + selected.getYear())
                .homePath(home(user))
                .navRole(nav(user))
                .employeeCode(employeeCode(draft.employeeId()))
                .employeeName(draft.employeeName())
                .storeName(store.getStoreName())
                .statusKey(status == null ? "CHUA_TINH" : status.name())
                .statusLabel(status == null ? "Chưa tính" : status.label())
                .locked(status == PayrollStatus.DA_CHOT)
                .assignedShifts(draft.assignedShifts())
                .validShifts(draft.validShifts())
                .leaveShifts(draft.leaveShifts())
                .paidLeaveShifts(draft.paidLeaveShifts())
                .paidLeaveHours(draft.paidLeaveHours())
                .reviewShifts(draft.reviewShifts())
                .payableHours(draft.payableHours())
                .hourlyRateLabel(rateLabel(draft))
                .expectedSalaryLabel(money(draft.expectedSalary()))
                .overtimeShifts(draft.overtimeShifts(overtimeIds))
                .overtimeHours(draft.overtimeHours(overtimeIds))
                .regularSalaryLabel(money(draft.regularSalary(overtimeIds)))
                .overtimeSalaryLabel(money(draft.overtimeSalary(overtimeIds)))
                .shifts(shifts)
                .build();
    }

    private BigDecimal rateFor(List<EmploymentContract> contracts, Integer employeeId, LocalDate day) {
        if (contracts == null || employeeId == null || day == null) {
            return null;
        }
        List<EmploymentContract> owned = new ArrayList<>();
        for (EmploymentContract contract : contracts) {
            if (contract.getEmployee() != null && employeeId.equals(contract.getEmployee().getId())) {
                owned.add(contract);
            }
        }
        EmploymentContract applicable = ContractValidityRules.applicable(owned, day);
        if (applicable == null || applicable.getSalary() == null || applicable.getSalary().signum() <= 0) {
            return null;
        }
        return applicable.getSalary();
    }

    private Store managerStore(AuthenticatedUser user) {
        if (user.getEmployeeId() == null) {
            throw new BusinessException("Bạn chưa được phân công quản lý cửa hàng nào.");
        }
        return storeRepository.findByManager_Id(user.getEmployeeId())
                .orElseThrow(() -> new BusinessException("Bạn chưa được phân công quản lý cửa hàng nào."));
    }

    private YearMonth resolvePeriod(Integer year, Integer month, String period) {
        if (period != null && !period.isBlank()) {
            try {
                return YearMonth.parse(period.trim());
            } catch (DateTimeParseException ex) {
                throw new BusinessException("Tháng không hợp lệ.");
            }
        }
        if (year == null && month == null) {
            return YearMonth.from(VietnamTime.today());
        }
        return requirePeriod(year == null ? 0 : year, month == null ? 0 : month);
    }

    private YearMonth requirePeriod(int year, int month) {
        if (month < 1 || month > 12 || year < 2000 || year > 2100) {
            throw new BusinessException("Tháng hoặc năm không hợp lệ.");
        }
        return YearMonth.of(year, month);
    }

    private String rateLabel(PayrollEmployeeDraft draft) {
        if (draft.multipleRates()) {
            return "Nhiều mức";
        }
        if (draft.hourlyRate() == null) {
            return "—";
        }
        return money(draft.hourlyRate()) + "/giờ";
    }

    private String money(BigDecimal value) {
        if (value == null) {
            return "—";
        }
        DecimalFormat format = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.forLanguageTag("vi-VN")));
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(value) + " đ";
    }

    private String scheduleLabel(LocalTime start, LocalTime end) {
        if (start == null || end == null) {
            return "—";
        }
        return start.format(CLOCK) + " – " + end.format(CLOCK);
    }

    private String clock(LocalDate workDate, LocalDateTime time) {
        if (time == null) {
            return "—";
        }
        if (workDate != null && time.toLocalDate().equals(workDate)) {
            return time.format(CLOCK);
        }
        return time.format(CLOCK) + " " + time.format(DAY);
    }

    private String attendanceLabel(Attendance attendance, boolean duplicate) {
        if (duplicate) {
            return "Bản ghi bị trùng";
        }
        if (attendance == null || attendance.getStatus() == null) {
            return "Chưa có bản ghi";
        }
        return switch (attendance.getStatus()) {
            case PRESENT -> "Có mặt";
            case ON_TIME -> "Đúng giờ";
            case LATE -> "Đi muộn";
            case EARLY_LEAVE -> "Về sớm";
            case LATE_AND_EARLY -> "Đi muộn và về sớm";
            case LATE_CHECKOUT -> "Check-out muộn";
            case LATE_AND_LATE_CHECKOUT -> "Đi muộn và check-out muộn";
            case NOT_PARTICIPATED -> "Không tham gia";
            case ABSENT -> "Vắng mặt";
        };
    }

    private LocalTime scheduledStart(ShiftAssignment assignment, Attendance attendance) {
        if (attendance != null && attendance.getScheduledStartTime() != null) {
            return attendance.getScheduledStartTime();
        }
        Shift shift = assignment.getShift();
        return shift == null ? null : shift.getStartTime();
    }

    private LocalTime scheduledEnd(ShiftAssignment assignment, Attendance attendance) {
        if (attendance != null && attendance.getScheduledEndTime() != null) {
            return attendance.getScheduledEndTime();
        }
        Shift shift = assignment.getShift();
        return shift == null ? null : shift.getEndTime();
    }

    private String shiftName(ShiftAssignment assignment, Attendance attendance) {
        if (attendance != null && attendance.getScheduledShiftName() != null && !attendance.getScheduledShiftName().isBlank()) {
            return attendance.getScheduledShiftName();
        }
        Shift shift = assignment.getShift();
        if (shift == null || shift.getShiftName() == null || shift.getShiftName().isBlank()) {
            return "Ca làm";
        }
        return shift.getShiftName();
    }

    private String slotKey(Attendance attendance) {
        if (attendance.getEmployee() == null || attendance.getEmployee().getId() == null
                || attendance.getShift() == null || attendance.getShift().getId() == null
                || attendance.getWorkDate() == null) {
            return null;
        }
        return attendance.getEmployee().getId() + ":" + attendance.getShift().getId() + ":" + attendance.getWorkDate();
    }

    private int minutes(Integer value) {
        return value == null ? 0 : value;
    }

    private String employeeCode(Integer employeeId) {
        return employeeId == null ? "—" : String.format("NV%03d", employeeId);
    }

    private String home(AuthenticatedUser user) {
        return switch (user.getRoleName()) {
            case ADMIN -> "/admin/salary";
            case MANAGER -> "/manager/salary";
            case STAFF -> "/staff/salary";
        };
    }

    private String nav(AuthenticatedUser user) {
        return user.getRoleName().name().toLowerCase(Locale.ROOT);
    }

    private String monthValue(YearMonth month) {
        return String.format("%04d-%02d", month.getYear(), month.getMonthValue());
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            return "";
        }
        if ("CHUA_TINH".equals(status) || "DA_TINH".equals(status) || "DA_CHOT".equals(status)) {
            return status;
        }
        return "";
    }

    private String optionalQuery(String name, Integer value) {
        return value == null ? "" : "&" + name + "=" + value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private boolean moneyEquals(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.compareTo(right) == 0;
    }

    private record PeriodSnapshot(Store store,
                                  PayrollStatus status,
                                  List<PayrollEmployeeDraft> employees,
                                  Set<Integer> overtimeIds) {
    }

    private record Prepared(PayrollBoardView board, List<PayrollDetailView> details) {
    }

    private record Listed(PayrollBoardView.Row row,
                          PayrollDetailView detail,
                          BigDecimal salary,
                          BigDecimal overtimeSalary,
                          int reviewShifts) {
    }
}

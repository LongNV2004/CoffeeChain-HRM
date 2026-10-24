package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.ApprovalStatus;
import com.example.coffee_hrm.common.enums.AssignmentStatus;
import com.example.coffee_hrm.common.enums.AttendanceReportStatus;
import com.example.coffee_hrm.common.enums.CertificationStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.enums.StoreStatus;
import com.example.coffee_hrm.common.enums.TrainingClassStatus;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.response.AdminDashboardView;
import com.example.coffee_hrm.dto.response.AttendanceClockView;
import com.example.coffee_hrm.dto.response.EmployeeSkillStatusLine;
import com.example.coffee_hrm.dto.response.EmployeeTrainingClassResponse;
import com.example.coffee_hrm.dto.response.ManagerDashboardView;
import com.example.coffee_hrm.dto.response.StaffDashboardView;
import com.example.coffee_hrm.dto.response.StaffDayAttendanceView;
import com.example.coffee_hrm.entity.Attendance;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.Shift;
import com.example.coffee_hrm.entity.ShiftAssignment;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.WorkAvailability;
import com.example.coffee_hrm.repository.AttendanceRepository;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.LeaveRequestRepository;
import com.example.coffee_hrm.repository.MonthlyAttendanceReportRepository;
import com.example.coffee_hrm.repository.ShiftAssignmentRepository;
import com.example.coffee_hrm.repository.ShiftChangeRequestRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.repository.TrainingClassRepository;
import com.example.coffee_hrm.repository.WorkAvailabilityRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.AttendanceService;
import com.example.coffee_hrm.service.DashboardService;
import com.example.coffee_hrm.service.ManagerDayAttendanceService;
import com.example.coffee_hrm.service.StaffAvailabilityService;
import com.example.coffee_hrm.service.TrainingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {

    private static final DateTimeFormatter TODAY_LABEL =
            DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy", Locale.forLanguageTag("vi-VN"));
    private static final DateTimeFormatter DAY_LABEL =
            DateTimeFormatter.ofPattern("EEEE, dd/MM", Locale.forLanguageTag("vi-VN"));
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MM/yyyy");
    private static final DateTimeFormatter SHIFT_TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final int TREND_MONTHS = 6;
    private static final int UPCOMING_DAYS = 7;
    private static final String NO_ATTENDANCE = "Bạn không có ca làm việc cần chấm công hôm nay.";

    private final StoreRepository storeRepository;
    private final EmployeeRepository employeeRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final ShiftChangeRequestRepository shiftChangeRequestRepository;
    private final AttendanceRepository attendanceRepository;
    private final TrainingClassRepository trainingClassRepository;
    private final MonthlyAttendanceReportRepository monthlyAttendanceReportRepository;
    private final WorkAvailabilityRepository workAvailabilityRepository;
    private final AttendanceService attendanceService;
    private final ManagerDayAttendanceService managerDayAttendanceService;
    private final TrainingService trainingService;
    private final StaffAvailabilityService staffAvailabilityService;

    @Override
    public AdminDashboardView buildAdminDashboard(AuthenticatedUser user) {
        LocalDate today = VietnamTime.today();
        YearMonth period = YearMonth.from(today);
        long managers = employeeRepository.countWorkingByRole(EmployeeStatus.TERMINATED, RoleName.MANAGER);
        long staff = employeeRepository.countWorkingByRole(EmployeeStatus.TERMINATED, RoleName.STAFF);
        long unassigned = employeeRepository.countWorkingWithoutAccount(EmployeeStatus.TERMINATED);
        long admins = employeeRepository.countWorkingByRole(EmployeeStatus.TERMINATED, RoleName.ADMIN);
        long headcount = employeeRepository.countByStatusNot(EmployeeStatus.TERMINATED);
        long other = Math.max(0, headcount - managers - staff - unassigned - admins);

        return AdminDashboardView.builder()
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .today(today)
                .todayLabel(TODAY_LABEL.format(today))
                .totalStores(storeRepository.count())
                .activeStores(storeRepository.countByStatus(StoreStatus.ACTIVE))
                .totalEmployees(managers + staff + unassigned + other)
                .pendingTrainingClassCount(trainingClassRepository.countByStatus(TrainingClassStatus.PENDING_APPROVAL))
                .waitingChainReportCount(monthlyAttendanceReportRepository
                        .countByReportYearAndReportMonthAndSubmittedAtIsNotNullAndStatusNotIn(
                                period.getYear(),
                                period.getMonthValue(),
                                EnumSet.of(AttendanceReportStatus.APPROVED, AttendanceReportStatus.REVISION)))
                .managerCount(managers)
                .staffCount(staff)
                .unassignedRoleCount(unassigned)
                .otherRoleCount(other)
                .trend(headcountTrend(today))
                .build();
    }

    /**
     * Sáu tháng gần nhất, tính theo ngày Việt Nam. Tuyển mới là HireDate, nghỉ việc là TerminationDate
     * của hồ sơ đã ở trạng thái Terminated. Hồ sơ Admin không nằm trong hai chuỗi này.
     */
    private List<AdminDashboardView.TrendPoint> headcountTrend(LocalDate today) {
        YearMonth end = YearMonth.from(today);
        YearMonth start = end.minusMonths(TREND_MONTHS - 1L);
        LocalDate from = start.atDay(1);
        LocalDate toExclusive = end.plusMonths(1).atDay(1);
        Map<YearMonth, long[]> buckets = new LinkedHashMap<>();
        for (YearMonth month = start; !month.isAfter(end); month = month.plusMonths(1)) {
            buckets.put(month, new long[2]);
        }
        addMonthCounts(buckets, employeeRepository.countHiresByMonth(from, toExclusive, RoleName.ADMIN), 0);
        addMonthCounts(
                buckets,
                employeeRepository.countTerminationsByMonth(
                        from, toExclusive, EmployeeStatus.TERMINATED, RoleName.ADMIN),
                1);

        List<AdminDashboardView.TrendPoint> points = new ArrayList<>();
        buckets.forEach((month, values) -> points.add(AdminDashboardView.TrendPoint.builder()
                .label(month.format(MONTH_LABEL))
                .hired(values[0])
                .resigned(values[1])
                .build()));
        return points;
    }

    private static void addMonthCounts(Map<YearMonth, long[]> buckets, List<Object[]> rows, int index) {
        if (rows == null) {
            return;
        }
        for (Object[] row : rows) {
            if (row == null || row.length < 3 || !(row[0] instanceof Number year) || !(row[1] instanceof Number month)) {
                continue;
            }
            int yearValue = year.intValue();
            int monthValue = month.intValue();
            if (yearValue < 1 || monthValue < 1 || monthValue > 12 || !(row[2] instanceof Number count)) {
                continue;
            }
            long[] values = buckets.get(YearMonth.of(yearValue, monthValue));
            if (values != null) {
                values[index] += count.longValue();
            }
        }
    }

    @Override
    public ManagerDashboardView buildManagerDashboard(AuthenticatedUser user) {
        LocalDate today = VietnamTime.today();
        Store store = user.getEmployeeId() == null
                ? null
                : storeRepository.findByManager_Id(user.getEmployeeId()).orElse(null);

        long workingEmployees = 0;
        long pendingLeaves = 0;
        long pendingRegistrations = 0;
        long pendingShiftChanges = 0;
        DaySnapshot day = DaySnapshot.empty();

        if (store != null && store.getId() != null && user.getEmployeeId() != null) {
            Integer storeId = store.getId();
            workingEmployees = employeeRepository.countActiveStaffAtStore(
                    storeId, EmployeeStatus.ACTIVE, user.getEmployeeId(), RoleName.MANAGER);
            pendingLeaves = leaveRequestRepository.countByEmployee_Store_IdAndStatus(storeId, ApprovalStatus.PENDING);
            pendingRegistrations = countPendingRegistrations(storeId);
            pendingShiftChanges = shiftChangeRequestRepository.countPendingForManager(storeId);
            day = summarizePublishedDay(storeId, today);
        }

        return ManagerDashboardView.builder()
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .storeId(store != null ? store.getId() : null)
                .storeName(store != null ? store.getStoreName() : null)
                .storeStatusLabel(store != null && store.getStatus() != null ? store.getStatus().getLabel() : null)
                .storeStatusKey(storeStatusKey(store))
                .today(today)
                .todayLabel(TODAY_LABEL.format(today))
                .workingEmployeeCount(workingEmployees)
                .todaySlotCount(day.slots())
                .todayEmployeeCount(day.employees())
                .completedSlotCount(day.completed())
                .completionRate(day.slots() == 0 ? null : (int) Math.round(day.completed() * 100.0 / day.slots()))
                .violationSlotCount(day.violations())
                .pendingRequestCount(pendingLeaves + pendingRegistrations + pendingShiftChanges)
                .pendingLeaveCount(pendingLeaves)
                .pendingRegistrationCount(pendingRegistrations)
                .pendingShiftChangeCount(pendingShiftChanges)
                .todayShifts(day.shifts())
                .build();
    }

    /**
     * Một đơn đăng ký ca là một registrationKey. Bản cũ không có key thì mỗi dòng là một đơn.
     */
    private long countPendingRegistrations(Integer storeId) {
        List<Object[]> rows = workAvailabilityRepository.findPendingRegistrationIdentities(
                storeId, ApprovalStatus.PENDING);
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        Set<String> requests = new LinkedHashSet<>();
        for (Object[] row : rows) {
            if (row == null || row.length < 2 || row[1] == null) {
                continue;
            }
            String key = row[0] instanceof String text && !text.isBlank() ? text : "legacy-" + row[1];
            requests.add(key);
        }
        return requests.size();
    }

    /**
     * Cùng phạm vi với trang chấm công theo ngày: ca Assigned đã công bố thuộc cửa hàng.
     * Hoàn thành khi đã check-out. Vi phạm là đi muộn hoặc về sớm, mỗi ca một lần.
     */
    private DaySnapshot summarizePublishedDay(Integer storeId, LocalDate today) {
        List<ShiftAssignment> assignments = shiftAssignmentRepository.findPublishedAssignedForStoreOnDate(
                storeId, today, AssignmentStatus.ASSIGNED);
        if (assignments == null || assignments.isEmpty()) {
            return DaySnapshot.empty();
        }
        Map<String, Attendance> attendanceBySlot = new LinkedHashMap<>();
        List<Attendance> records = attendanceRepository.findStoreDayAttendance(storeId, today);
        if (records != null) {
            for (Attendance attendance : records) {
                if (attendance.getEmployee() == null || attendance.getShift() == null
                        || attendance.getEmployee().getId() == null || attendance.getShift().getId() == null) {
                    continue;
                }
                attendanceBySlot.putIfAbsent(slotKey(attendance.getEmployee().getId(), attendance.getShift().getId()), attendance);
            }
        }

        long slots = 0;
        long completed = 0;
        long violations = 0;
        Set<Integer> employees = new LinkedHashSet<>();
        Map<Integer, ShiftAccumulator> shifts = new LinkedHashMap<>();
        for (ShiftAssignment assignment : assignments) {
            Shift shift = assignment.getShift();
            Employee employee = assignment.getEmployee();
            if (shift == null || employee == null || shift.getId() == null || employee.getId() == null) {
                continue;
            }
            slots++;
            employees.add(employee.getId());
            Attendance attendance = attendanceBySlot.get(slotKey(employee.getId(), shift.getId()));
            if (attendance != null && attendance.getCheckOutTime() != null) {
                completed++;
            }
            int late = attendance == null || attendance.getLateMinutes() == null ? 0 : attendance.getLateMinutes();
            int early = attendance == null || attendance.getEarlyLeaveMinutes() == null ? 0 : attendance.getEarlyLeaveMinutes();
            if (late > 0 || early > 0) {
                violations++;
            }
            shifts.computeIfAbsent(shift.getId(), ignored -> new ShiftAccumulator(shift)).add(employee.getId());
        }
        List<ManagerDashboardView.ShiftSummary> summaries = shifts.values().stream()
                .sorted(Comparator
                        .comparing(ShiftAccumulator::start, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ShiftAccumulator::name, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(ShiftAccumulator::id, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(ShiftAccumulator::toSummary)
                .toList();
        return new DaySnapshot(slots, employees.size(), completed, violations, summaries);
    }

    private static String slotKey(Integer employeeId, Integer shiftId) {
        return employeeId + ":" + shiftId;
    }

    private static String storeStatusKey(Store store) {
        if (store == null || store.getStatus() == null) {
            return null;
        }
        return switch (store.getStatus()) {
            case ACTIVE -> "is-active";
            case PRE_OPENING -> "is-opening";
            case INACTIVE -> "is-inactive";
        };
    }

    private record DaySnapshot(long slots, long employees, long completed, long violations,
                               List<ManagerDashboardView.ShiftSummary> shifts) {
        private static DaySnapshot empty() {
            return new DaySnapshot(0, 0, 0, 0, List.of());
        }
    }

    private static final class ShiftAccumulator {
        private final Integer id;
        private final String name;
        private final LocalTime start;
        private final LocalTime end;
        private final Set<Integer> employees = new LinkedHashSet<>();
        private int slotCount;

        private ShiftAccumulator(Shift shift) {
            this.id = shift.getId();
            this.name = shift.getShiftName() == null || shift.getShiftName().isBlank()
                    ? "Ca làm" : shift.getShiftName();
            this.start = shift.getStartTime();
            this.end = shift.getEndTime();
        }

        private void add(Integer employeeId) {
            employees.add(employeeId);
            slotCount++;
        }

        private Integer id() {
            return id;
        }

        private String name() {
            return name;
        }

        private LocalTime start() {
            return start;
        }

        private ManagerDashboardView.ShiftSummary toSummary() {
            return ManagerDashboardView.ShiftSummary.builder()
                    .shiftId(id)
                    .shiftName(name)
                    .timeLabel(timeLabel(start, end))
                    .slotCount(slotCount)
                    .employeeCount(employees.size())
                    .build();
        }

        private static String timeLabel(LocalTime start, LocalTime end) {
            if (start == null || end == null) {
                return "—";
            }
            return start.format(SHIFT_TIME) + " – " + end.format(SHIFT_TIME);
        }
    }

    @Override
    public StaffDashboardView buildStaffDashboard(AuthenticatedUser user) {
        LocalDate today = VietnamTime.today();
        LocalDate windowEnd = today.plusDays(UPCOMING_DAYS);
        Integer employeeId = user == null ? null : user.getEmployeeId();
        Employee employee = employeeId == null ? null : employeeRepository.findByIdWithStore(employeeId).orElse(null);

        List<ShiftAssignment> confirmed = confirmedAssignments(employeeId, today, windowEnd);
        long todaySlots = confirmed.stream().filter(item -> today.equals(item.getWorkDate())).count();
        long upcomingSlots = confirmed.stream()
                .filter(item -> item.getWorkDate() != null && item.getWorkDate().isAfter(today))
                .count();
        List<StaffDashboardView.ScheduleItem> upcoming = confirmed.stream()
                .filter(item -> !VietnamTime.isBeforeNow(item.getWorkDate(), item.getShift().getStartTime()))
                .map(this::toScheduleItem)
                .toList();

        long pendingLeaves = employeeId == null
                ? 0
                : leaveRequestRepository.countByEmployee_IdAndStatus(employeeId, ApprovalStatus.PENDING);
        long pendingShiftChanges = employeeId == null
                ? 0
                : shiftChangeRequestRepository.countByEmployee_IdAndStatus(employeeId, ApprovalStatus.PENDING);
        long incomingShiftChanges = employeeId == null
                ? 0
                : shiftChangeRequestRepository.findIncomingRequestsForEmployee(employeeId).size();

        RegistrationSummary registrations = summarizeRegistrations(employeeId, today);
        AttendanceSummary attendance = summarizeAttendance(user, employeeId);
        TrainingSummary training = summarizeTraining(user, employee);
        String storeName = storeName(employee, user);
        String personName = personName(employee, user);

        return StaffDashboardView.builder()
                .greeting(personName == null ? "Xin chào." : "Xin chào, " + personName + ".")
                .storeName(storeName)
                .today(today)
                .todayLabel(today.format(TODAY_LABEL))
                .todaySlotCount(todaySlots)
                .upcomingSlotCount(upcomingSlots)
                .upcomingWindowLabel("7 ngày tiếp theo, từ " + today.plusDays(1).format(SHORT_DATE)
                        + " đến " + windowEnd.format(SHORT_DATE) + ", không gồm hôm nay")
                .scheduleWindowLabel("Ca đã xác nhận và chưa bắt đầu, đến " + windowEnd.format(SHORT_DATE))
                .pendingRegistrationCount(registrations.pending())
                .submittedRegistrationCount(registrations.submitted())
                .approvedRegistrationCount(registrations.approved())
                .registrationPeriodLabel(registrations.periodLabel())
                .pendingPersonalCount(pendingLeaves + pendingShiftChanges + incomingShiftChanges)
                .pendingLeaveCount(pendingLeaves)
                .pendingShiftChangeCount(pendingShiftChanges)
                .incomingShiftChangeCount(incomingShiftChanges)
                .canRegister(training.canRegister())
                .registerBlockedMessage(training.blockedMessage())
                .certificationKnown(training.certificationKnown())
                .certified(training.certified())
                .certificationLabel(training.certificationLabel())
                .attendanceMessage(attendance.message())
                .upcomingShifts(upcoming)
                .todayAttendance(attendance.slots())
                .skills(training.skills())
                .upcomingClasses(training.classes())
                .build();
    }

    private List<ShiftAssignment> confirmedAssignments(Integer employeeId, LocalDate from, LocalDate to) {
        if (employeeId == null) {
            return List.of();
        }
        List<ShiftAssignment> rows = shiftAssignmentRepository.findAssignmentsForAttendance(
                employeeId, from, to, AssignmentStatus.ASSIGNED);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .filter(item -> item.getEmployee() != null && employeeId.equals(item.getEmployee().getId()))
                .filter(item -> item.getStatus() == AssignmentStatus.ASSIGNED)
                .filter(this::isConfirmedAssignment)
                .filter(item -> item.getShift() != null
                        && item.getShift().getStartTime() != null
                        && item.getShift().getEndTime() != null
                        && item.getWorkDate() != null)
                .sorted(Comparator
                        .comparing(ShiftAssignment::getWorkDate)
                        .thenComparing(item -> item.getShift().getStartTime())
                        .thenComparing(ShiftAssignment::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private boolean isConfirmedAssignment(ShiftAssignment assignment) {
        return assignment.getIsPublished() == null || Boolean.TRUE.equals(assignment.getIsPublished());
    }

    private StaffDashboardView.ScheduleItem toScheduleItem(ShiftAssignment assignment) {
        Shift shift = assignment.getShift();
        String storeName = shift.getStore() == null ? null : blankToNull(shift.getStore().getStoreName());
        return StaffDashboardView.ScheduleItem.builder()
                .workDate(assignment.getWorkDate())
                .dateLabel(assignment.getWorkDate().format(DAY_LABEL))
                .shiftName(blankToNull(shift.getShiftName()) == null ? "Ca làm việc" : shift.getShiftName())
                .timeLabel(shift.getStartTime().format(SHIFT_TIME) + " – " + shift.getEndTime().format(SHIFT_TIME))
                .storeName(storeName)
                .statusLabel("Đã phân công")
                .build();
    }

    private RegistrationSummary summarizeRegistrations(Integer employeeId, LocalDate today) {
        if (employeeId == null) {
            return RegistrationSummary.empty();
        }
        List<WorkAvailability> rows = workAvailabilityRepository.findVisibleByEmployee(
                employeeId, today, ApprovalStatus.PENDING);
        if (rows == null || rows.isEmpty()) {
            return RegistrationSummary.empty();
        }
        Map<String, List<WorkAvailability>> groups = new LinkedHashMap<>();
        for (WorkAvailability row : rows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            if (row.getStatus() != ApprovalStatus.PENDING && row.getStatus() != ApprovalStatus.APPROVED) {
                continue;
            }
            String key = row.getRegistrationKey() != null && !row.getRegistrationKey().isBlank()
                    ? row.getRegistrationKey()
                    : "legacy-" + row.getId();
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
        }
        long pending = 0;
        long approved = 0;
        List<WorkAvailability> pendingRows = new ArrayList<>();
        List<WorkAvailability> approvedRows = new ArrayList<>();
        for (List<WorkAvailability> group : groups.values()) {
            boolean waiting = group.stream().anyMatch(row -> row.getStatus() == ApprovalStatus.PENDING);
            if (waiting) {
                pending++;
                group.stream().filter(row -> row.getStatus() == ApprovalStatus.PENDING).forEach(pendingRows::add);
            } else if (group.stream().anyMatch(row -> row.getStatus() == ApprovalStatus.APPROVED)) {
                approved++;
                group.stream().filter(row -> row.getStatus() == ApprovalStatus.APPROVED).forEach(approvedRows::add);
            }
        }
        List<WorkAvailability> periodRows = pending > 0 ? pendingRows : approvedRows;
        return new RegistrationSummary(pending, approved, periodLabel(earliestFrom(periodRows), latestTo(periodRows)));
    }

    private LocalDate earliestFrom(List<WorkAvailability> rows) {
        return rows.stream()
                .map(WorkAvailability::getValidFrom)
                .filter(date -> date != null)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    private LocalDate latestTo(List<WorkAvailability> rows) {
        return rows.stream()
                .map(WorkAvailability::getValidTo)
                .filter(date -> date != null)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    private String periodLabel(LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return null;
        }
        if (from != null && to != null) {
            return "Kỳ " + from.format(SHORT_DATE) + " – " + to.format(SHORT_DATE);
        }
        LocalDate only = from != null ? from : to;
        return "Kỳ " + only.format(SHORT_DATE);
    }

    private AttendanceSummary summarizeAttendance(AuthenticatedUser user, Integer employeeId) {
        if (user == null || employeeId == null) {
            return new AttendanceSummary(List.of(), NO_ATTENDANCE);
        }
        AttendanceClockView clock;
        try {
            clock = attendanceService.getClock(user);
        } catch (BusinessException ex) {
            return new AttendanceSummary(List.of(), ex.getMessage());
        }
        List<AttendanceClockView.Slot> slots = clock == null || clock.getSlots() == null ? List.of() : clock.getSlots();
        if (slots.isEmpty()) {
            return new AttendanceSummary(List.of(), NO_ATTENDANCE);
        }
        List<StaffDayAttendanceView.Slot> details = List.of();
        try {
            StaffDayAttendanceView day = managerDayAttendanceService.getOwnDay(user, VietnamTime.today());
            if (day != null && day.getSlots() != null) {
                details = day.getSlots();
            }
        } catch (BusinessException ignored) {
            details = List.of();
        }
        List<StaffDashboardView.AttendanceSlot> mapped = new ArrayList<>();
        for (AttendanceClockView.Slot slot : slots) {
            StaffDayAttendanceView.Slot match = matchAttendanceDetail(details, slot);
            boolean showLate = match != null && match.isLate()
                    && !"upcoming".equals(slot.getStatusKey())
                    && !"ready".equals(slot.getStatusKey());
            boolean showEarly = match != null && match.isEarly()
                    && ("done".equals(slot.getStatusKey()) || "closed".equals(slot.getStatusKey()));
            String worked = null;
            if (match != null && "done".equals(slot.getStatusKey()) && hasWorkedTime(match.getWorkedLabel())) {
                worked = match.getWorkedLabel();
            }
            mapped.add(StaffDashboardView.AttendanceSlot.builder()
                    .shiftName(blankToNull(slot.getShiftName()) == null ? "Ca làm việc" : slot.getShiftName())
                    .scheduleLabel(slot.getScheduleLabel())
                    .statusKey(slot.getStatusKey() == null ? "upcoming" : slot.getStatusKey())
                    .statusLabel(blankToNull(slot.getStatusLabel()) == null ? "Chưa đến giờ" : slot.getStatusLabel())
                    .checkInLabel(slot.getCheckInLabel() == null ? "—" : slot.getCheckInLabel())
                    .checkOutLabel(slot.getCheckOutLabel() == null ? "—" : slot.getCheckOutLabel())
                    .lateNote(showLate ? "Đi muộn · " + match.getLateLabel() : null)
                    .earlyNote(showEarly ? "Về sớm · " + match.getEarlyLabel() : null)
                    .workedLabel(worked)
                    .build());
        }
        return new AttendanceSummary(mapped, null);
    }

    private StaffDayAttendanceView.Slot matchAttendanceDetail(List<StaffDayAttendanceView.Slot> details,
                                                              AttendanceClockView.Slot slot) {
        for (StaffDayAttendanceView.Slot detail : details) {
            if (detail.getShiftName() != null && detail.getShiftName().equals(slot.getShiftName())
                    && sameSchedule(detail.getScheduleLabel(), slot.getScheduleLabel())) {
                return detail;
            }
        }
        return null;
    }

    private boolean sameSchedule(String left, String right) {
        return left != null && right != null && left.replace(" ", "").equals(right.replace(" ", ""));
    }

    private boolean hasWorkedTime(String workedLabel) {
        return workedLabel != null && !workedLabel.isBlank() && !"—".equals(workedLabel);
    }

    private TrainingSummary summarizeTraining(AuthenticatedUser user, Employee employee) {
        if (employee == null) {
            return new TrainingSummary(false, "Tài khoản chưa liên kết với hồ sơ nhân viên.",
                    false, false, null, List.of(), List.of());
        }
        String blocked;
        try {
            blocked = staffAvailabilityService.registrationBlockedMessage(user);
        } catch (BusinessException ex) {
            blocked = ex.getMessage();
        }
        CertificationStatus status = employee.getCertificationStatus() == null
                ? CertificationStatus.NOTCERTIFIED
                : employee.getCertificationStatus();
        List<StaffDashboardView.SkillItem> skills = List.of();
        try {
            List<EmployeeSkillStatusLine> lines = trainingService.listCurrentSkillStatuses(employee.getId());
            if (lines != null) {
                skills = lines.stream()
                        .filter(line -> line != null)
                        .map(line -> StaffDashboardView.SkillItem.builder()
                                .skillName(blankToNull(line.getSkillName()) == null ? "Kỹ năng" : line.getSkillName())
                                .certificationLabel(line.getCertificationLabel())
                                .latestSummary(line.getLatestSummary())
                                .build())
                        .toList();
            }
        } catch (BusinessException ignored) {
            skills = List.of();
        }
        List<StaffDashboardView.TrainingItem> classes = List.of();
        try {
            List<EmployeeTrainingClassResponse> enrolled = trainingService.listClassesForEmployee(user);
            if (enrolled != null) {
                classes = enrolled.stream()
                        .filter(item -> item != null && !item.isEnded())
                        .limit(4)
                        .map(this::toTrainingItem)
                        .toList();
            }
        } catch (BusinessException ignored) {
            classes = List.of();
        }
        return new TrainingSummary(blocked == null, blocked, true,
                status == CertificationStatus.CERTIFIED, status.getLabel(), skills, classes);
    }

    private StaffDashboardView.TrainingItem toTrainingItem(EmployeeTrainingClassResponse item) {
        String timeLabel = null;
        if (item.getStartTime() != null && item.getEndTime() != null) {
            timeLabel = item.getStartTime().format(SHIFT_TIME) + " – " + item.getEndTime().format(SHIFT_TIME);
        } else if (item.getStartTime() != null) {
            timeLabel = item.getStartTime().format(SHIFT_TIME);
        }
        return StaffDashboardView.TrainingItem.builder()
                .className(blankToNull(item.getClassName()) == null ? "Lớp đào tạo" : item.getClassName())
                .dateLabel(item.getDateLabel())
                .timeLabel(timeLabel)
                .placeLabel(blankToNull(item.getPlaceLabel()))
                .statusLabel(blankToNull(item.getParticipationStatus()))
                .build();
    }

    private String personName(Employee employee, AuthenticatedUser user) {
        if (employee != null && employee.getFullName() != null && !employee.getFullName().isBlank()) {
            return employee.getFullName().trim();
        }
        if (user != null && user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
            return user.getDisplayName().trim();
        }
        return null;
    }

    private String storeName(Employee employee, AuthenticatedUser user) {
        if (employee != null && employee.getStore() != null) {
            return blankToNull(employee.getStore().getStoreName());
        }
        return user == null ? null : blankToNull(user.getStoreName());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record RegistrationSummary(long pending, long approved, String periodLabel) {
        private static RegistrationSummary empty() {
            return new RegistrationSummary(0, 0, null);
        }

        private long submitted() {
            return pending + approved;
        }
    }

    private record AttendanceSummary(List<StaffDashboardView.AttendanceSlot> slots, String message) {
    }

    private record TrainingSummary(boolean canRegister,
                                   String blockedMessage,
                                   boolean certificationKnown,
                                   boolean certified,
                                   String certificationLabel,
                                   List<StaffDashboardView.SkillItem> skills,
                                   List<StaffDashboardView.TrainingItem> classes) {
    }
}

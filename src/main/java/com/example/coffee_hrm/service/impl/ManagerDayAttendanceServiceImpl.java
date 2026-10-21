package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.AssignmentStatus;
import com.example.coffee_hrm.common.enums.AttendanceStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.TrainingClassStatus;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.response.ManagerDayAttendanceView;
import com.example.coffee_hrm.dto.response.ManagerMonthAttendanceView;
import com.example.coffee_hrm.dto.response.StaffDayAttendanceView;
import com.example.coffee_hrm.entity.Attendance;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.Shift;
import com.example.coffee_hrm.entity.ShiftAssignment;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.TrainingAttendance;
import com.example.coffee_hrm.entity.TrainingClass;
import com.example.coffee_hrm.entity.TrainingClassEnrollment;
import com.example.coffee_hrm.repository.AttendanceRepository;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.ShiftAssignmentRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.repository.TrainingAttendanceRepository;
import com.example.coffee_hrm.repository.TrainingClassEnrollmentRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.ManagerDayAttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ManagerDayAttendanceServiceImpl implements ManagerDayAttendanceService {

    private static final DateTimeFormatter SCHEDULE_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter CLOCK_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final StoreRepository storeRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final AttendanceRepository attendanceRepository;
    private final TrainingAttendanceRepository trainingAttendanceRepository;
    private final TrainingClassEnrollmentRepository trainingClassEnrollmentRepository;
    private final EmployeeRepository employeeRepository;

    @Override
    @Transactional(readOnly = true)
    public ManagerDayAttendanceView getDay(AuthenticatedUser user, LocalDate date) {
        Store store = storeRepository.findByManager_Id(user.getEmployeeId())
                .orElseThrow(() -> new BusinessException("Bạn chưa được phân công quản lý cửa hàng nào."));
        LocalDate day = date == null ? VietnamTime.today() : date;
        if (day.isAfter(VietnamTime.today())) {
            throw new BusinessException("Không thể xem chấm công sau ngày hiện tại theo giờ Việt Nam.");
        }
        List<ShiftAssignment> assignments = shiftAssignmentRepository.findPublishedAssignedForStoreOnDate(
                store.getId(), day, AssignmentStatus.ASSIGNED);
        Map<String, Attendance> attendanceBySlot = attendanceBySlot(
                attendanceRepository.findHistoryByStore(store.getId(), day, day));
        List<ManagerDayAttendanceView.Slot> slots = new ArrayList<>();
        Map<Integer, ManagerDayAttendanceView.ShiftCount> shifts = new LinkedHashMap<>();
        int violations = 0;
        for (ShiftAssignment assignment : assignments) {
            if (assignment.getShift() == null || assignment.getEmployee() == null) {
                continue;
            }
            Attendance attendance = attendanceBySlot.get(slotKey(
                    assignment.getEmployee().getId(), assignment.getShift().getId()));
            ManagerDayAttendanceView.Slot slot = toSlot(assignment, attendance);
            slots.add(slot);
            if (slot.isLate() || slot.isEarly()) {
                violations++;
            }
            shifts.merge(assignment.getShift().getId(),
                    ManagerDayAttendanceView.ShiftCount.builder()
                            .shiftId(assignment.getShift().getId())
                            .shiftName(assignment.getShift().getShiftName())
                            .slots(1)
                            .build(),
                    (current, ignored) -> ManagerDayAttendanceView.ShiftCount.builder()
                            .shiftId(current.getShiftId())
                            .shiftName(current.getShiftName())
                            .slots(current.getSlots() + 1)
                            .build());
        }
        return ManagerDayAttendanceView.builder()
                .workDate(day)
                .dateLabel(dateLabel(day))
                .storeName(store.getStoreName())
                .violationSlots(violations)
                .shifts(List.copyOf(shifts.values()))
                .slots(slots)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public StaffDayAttendanceView getOwnDay(AuthenticatedUser user, LocalDate date) {
        if (user == null || user.getEmployeeId() == null) {
            throw new BusinessException("Bạn không có quyền xem lịch sử chấm công.");
        }
        LocalDate day = date == null ? VietnamTime.today() : date;
        if (day.isAfter(VietnamTime.today())) {
            throw new BusinessException("Không thể xem lịch sử chấm công sau ngày hiện tại theo giờ Việt Nam.");
        }
        List<ShiftAssignment> assignments = shiftAssignmentRepository
                .findAssignmentsForAttendance(user.getEmployeeId(), day, day, AssignmentStatus.ASSIGNED)
                .stream()
                .filter(assignment -> assignment.getIsPublished() == null || Boolean.TRUE.equals(assignment.getIsPublished()))
                .filter(assignment -> assignment.getShift() != null)
                .toList();
        Map<Integer, Attendance> attendanceByShift = new LinkedHashMap<>();
        for (Attendance attendance : attendanceRepository.findHistoryByEmployee(user.getEmployeeId(), day, day)) {
            if (attendance.getShift() == null || attendance.getShift().getId() == null) {
                continue;
            }
            attendanceByShift.putIfAbsent(attendance.getShift().getId(), attendance);
        }
        List<StaffDayAttendanceView.Slot> slots = new ArrayList<>();
        for (ShiftAssignment assignment : assignments) {
            slots.add(toOwnSlot(assignment, attendanceByShift.remove(assignment.getShift().getId())));
        }
        for (Attendance attendance : attendanceByShift.values()) {
            slots.add(toOwnSlot(null, attendance));
        }
        slots.addAll(trainingSlots(user.getEmployeeId(), day));
        return StaffDayAttendanceView.builder()
                .workDate(day)
                .dateLabel(dateLabel(day))
                .slots(slots)
                .build();
    }

    @Override
    public StaffDayAttendanceView emptyOwnDay(LocalDate date) {
        LocalDate day = date == null ? VietnamTime.today() : date;
        return StaffDayAttendanceView.builder()
                .workDate(day)
                .dateLabel(dateLabel(day))
                .slots(List.of())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ManagerMonthAttendanceView getMonth(AuthenticatedUser user, String monthValue) {
        Store store = storeRepository.findByManager_Id(user.getEmployeeId())
                .orElseThrow(() -> new BusinessException("Bạn chưa được phân công quản lý cửa hàng nào."));
        YearMonth selected = parseMonth(monthValue);
        LocalDate start = selected.atDay(1);
        LocalDate end = selected.atEndOfMonth();
        List<Employee> roster = employeeRepository.findByStoreIdWithAccount(store.getId()).stream()
                .filter(employee -> employee.getStatus() != EmployeeStatus.TERMINATED)
                .sorted((left, right) -> {
                    String leftName = left.getFullName() == null ? "" : left.getFullName();
                    String rightName = right.getFullName() == null ? "" : right.getFullName();
                    int byName = leftName.compareToIgnoreCase(rightName);
                    return byName != 0 ? byName : Integer.compare(left.getId(), right.getId());
                })
                .toList();
        Map<Integer, Integer> scheduled = new LinkedHashMap<>();
        for (ShiftAssignment assignment : shiftAssignmentRepository.findPublishedAssignedForStoreBetween(
                store.getId(), start, end, AssignmentStatus.ASSIGNED)) {
            if (assignment.getEmployee() == null || assignment.getEmployee().getId() == null) {
                continue;
            }
            scheduled.merge(assignment.getEmployee().getId(), 1, Integer::sum);
        }
        Map<Integer, MonthStats> stats = new LinkedHashMap<>();
        Map<Integer, List<MonthLine>> lines = new LinkedHashMap<>();
        List<Attendance> records = attendanceRepository.findForMonthlyReport(store.getId(), start, end);
        if (records != null) {
            for (Attendance attendance : records) {
                if (attendance.getEmployee() == null || attendance.getEmployee().getId() == null) {
                    continue;
                }
                MonthStats row = stats.computeIfAbsent(attendance.getEmployee().getId(), ignored -> new MonthStats());
                lines.computeIfAbsent(attendance.getEmployee().getId(), ignored -> new ArrayList<>())
                        .add(toMonthLine(attendance));
                if (attendance.getStatus() == AttendanceStatus.ABSENT) {
                    row.absentSlots++;
                    continue;
                }
                if (attendance.getCheckInTime() != null) {
                    row.workedSlots++;
                }
                int late = attendance.getLateMinutes() == null ? 0 : attendance.getLateMinutes();
                int early = attendance.getEarlyLeaveMinutes() == null ? 0 : attendance.getEarlyLeaveMinutes();
                if (late > 0) {
                    row.lateSlots++;
                    row.lateMinutes += late;
                }
                if (early > 0) {
                    row.earlySlots++;
                    row.earlyMinutes += early;
                }
            }
        }
        List<ManagerMonthAttendanceView.EmployeeMonth> employees = new ArrayList<>();
        int scheduledTotal = 0;
        for (Employee employee : roster) {
            int scheduledSlots = scheduled.getOrDefault(employee.getId(), 0);
            scheduledTotal += scheduledSlots;
            MonthStats row = stats.getOrDefault(employee.getId(), new MonthStats());
            List<ManagerMonthAttendanceView.DayLine> days = lines.getOrDefault(employee.getId(), List.of()).stream()
                    .sorted(Comparator.comparing(MonthLine::date).thenComparing(MonthLine::start, Comparator.nullsLast(Comparator.naturalOrder())))
                    .map(MonthLine::line)
                    .toList();
            String code = employeeCode(employee.getId());
            String name = employee.getFullName() == null ? "—" : employee.getFullName();
            employees.add(ManagerMonthAttendanceView.EmployeeMonth.builder()
                    .employeeId(employee.getId())
                    .employeeName(name)
                    .employeeCode(code)
                    .roleLabel(roleLabel(employee))
                    .employmentKey(employmentKey(employee))
                    .searchText((code + " " + employee.getId() + " " + name).toLowerCase(Locale.ROOT))
                    .scheduledSlots(scheduledSlots)
                    .workedSlots(row.workedSlots)
                    .lateSlots(row.lateSlots)
                    .earlySlots(row.earlySlots)
                    .absentSlots(row.absentSlots)
                    .lateDurationLabel(durationLabel(row.lateMinutes))
                    .earlyDurationLabel(durationLabel(row.earlyMinutes))
                    .late(row.lateSlots > 0)
                    .early(row.earlySlots > 0)
                    .days(days)
                    .build());
        }
        LocalDate today = VietnamTime.today();
        LocalDate detailTo = end.isAfter(today) ? today : end;
        return ManagerMonthAttendanceView.builder()
                .year(selected.getYear())
                .month(selected.getMonthValue())
                .monthValue(String.format("%04d-%02d", selected.getYear(), selected.getMonthValue()))
                .monthLabel("Tháng " + selected.getMonthValue() + "/" + selected.getYear())
                .storeName(store.getStoreName())
                .employeeCount(employees.size())
                .scheduledSlots(scheduledTotal)
                .detailFrom(start.toString())
                .detailTo(detailTo.toString())
                .detailReady(!start.isAfter(detailTo))
                .employees(employees)
                .build();
    }

    @Override
    public ManagerMonthAttendanceView emptyMonth() {
        YearMonth current = YearMonth.from(VietnamTime.today());
        return ManagerMonthAttendanceView.builder()
                .year(current.getYear())
                .month(current.getMonthValue())
                .monthValue(String.format("%04d-%02d", current.getYear(), current.getMonthValue()))
                .monthLabel("Tháng " + current.getMonthValue() + "/" + current.getYear())
                .employeeCount(0)
                .scheduledSlots(0)
                .detailFrom(current.atDay(1).toString())
                .detailTo(VietnamTime.today().toString())
                .detailReady(true)
                .employees(List.of())
                .build();
    }

    @Override
    public ManagerDayAttendanceView empty(LocalDate date) {
        LocalDate day = date == null ? VietnamTime.today() : date;
        return ManagerDayAttendanceView.builder()
                .workDate(day)
                .dateLabel(dateLabel(day))
                .shifts(List.of())
                .slots(List.of())
                .violationSlots(0)
                .build();
    }

    private Map<String, Attendance> attendanceBySlot(List<Attendance> records) {
        Map<String, Attendance> matched = new LinkedHashMap<>();
        if (records == null) {
            return matched;
        }
        for (Attendance attendance : records) {
            if (attendance.getEmployee() == null || attendance.getShift() == null
                    || attendance.getEmployee().getId() == null || attendance.getShift().getId() == null) {
                continue;
            }
            matched.putIfAbsent(slotKey(attendance.getEmployee().getId(), attendance.getShift().getId()), attendance);
        }
        return matched;
    }

    private String slotKey(Integer employeeId, Integer shiftId) {
        return employeeId + ":" + shiftId;
    }

    private ManagerDayAttendanceView.Slot toSlot(ShiftAssignment assignment, Attendance attendance) {
        Shift shift = assignment.getShift();
        int late = attendance == null || attendance.getLateMinutes() == null ? 0 : attendance.getLateMinutes();
        int early = attendance == null || attendance.getEarlyLeaveMinutes() == null ? 0 : attendance.getEarlyLeaveMinutes();
        boolean checkedIn = attendance != null && attendance.getCheckInTime() != null;
        boolean checkedOut = attendance != null && attendance.getCheckOutTime() != null;
        AttendanceStatus status = attendance == null ? null : attendance.getStatus();
        boolean absent = status == AttendanceStatus.ABSENT;
        String statusKey = statusKey(checkedIn, absent, late, early);
        String code = employeeCode(assignment.getEmployee().getId());
        String name = assignment.getEmployee().getFullName() == null ? "—" : assignment.getEmployee().getFullName();
        LocalTime start = attendance != null && attendance.getScheduledStartTime() != null
                ? attendance.getScheduledStartTime() : shift.getStartTime();
        LocalTime end = attendance != null && attendance.getScheduledEndTime() != null
                ? attendance.getScheduledEndTime() : shift.getEndTime();
        String shiftName = attendance != null && attendance.getScheduledShiftName() != null
                && !attendance.getScheduledShiftName().isBlank()
                ? attendance.getScheduledShiftName() : shift.getShiftName();
        return ManagerDayAttendanceView.Slot.builder()
                .employeeName(name)
                .employeeCode(code)
                .searchText((code + " " + assignment.getEmployee().getId() + " " + name).toLowerCase(Locale.ROOT))
                .shiftId(shift.getId())
                .shiftName(shiftName)
                .scheduleLabel(scheduleLabel(start, end))
                .checkInLabel(checkedIn ? attendance.getCheckInTime().format(CLOCK_FORMATTER) : "—")
                .checkOutLabel(attendance != null && attendance.getCheckOutTime() != null
                        ? attendance.getCheckOutTime().format(CLOCK_FORMATTER) : "—")
                .lateLabel(late + " phút")
                .earlyLabel(early + " phút")
                .late(late > 0)
                .early(early > 0)
                .statusKey(statusKey)
                .statusLabel(statusLabel(statusKey))
                .workedLabel(checkedIn && checkedOut
                        ? minuteText((int) Math.max(0, ChronoUnit.MINUTES.between(
                        attendance.getCheckInTime(), attendance.getCheckOutTime())))
                        : "—")
                .attendanceId(attendance == null ? null : attendance.getId())
                .photoBase(attendance == null ? null : "/attendance/photos")
                .hasCheckInPhoto(hasPhoto(attendance == null ? null : attendance.getCheckInPhotoName()))
                .hasCheckOutPhoto(hasPhoto(attendance == null ? null : attendance.getCheckOutPhotoName()))
                .build();
    }

    private String statusKey(boolean checkedIn, boolean absent, int late, int early) {
        if (!checkedIn && absent) {
            return "absent";
        }
        if (!checkedIn) {
            return "missing";
        }
        if (late > 0 && early > 0) {
            return "late-early";
        }
        if (late > 0) {
            return "late";
        }
        if (early > 0) {
            return "early";
        }
        return "on-time";
    }

    private String statusLabel(String statusKey) {
        return switch (statusKey) {
            case "absent" -> "Vắng";
            case "missing" -> "Chưa check-in";
            case "late-early" -> "Đi muộn và về sớm";
            case "late" -> "Đi muộn";
            case "early" -> "Về sớm";
            default -> "Đúng giờ";
        };
    }

    private String scheduleLabel(LocalTime start, LocalTime end) {
        if (start == null || end == null) {
            return "—";
        }
        return start.format(SCHEDULE_FORMATTER) + "–" + end.format(SCHEDULE_FORMATTER);
    }

    private String employeeCode(Integer employeeId) {
        if (employeeId == null) {
            return "—";
        }
        return String.format("NV%03d", employeeId);
    }

    private String dateLabel(LocalDate day) {
        return weekday(day.getDayOfWeek()) + ", " + day.format(DAY_FORMATTER);
    }

    private StaffDayAttendanceView.Slot toOwnSlot(ShiftAssignment assignment, Attendance attendance) {
        Shift shift = assignment != null ? assignment.getShift() : attendance.getShift();
        boolean checkedIn = attendance != null && attendance.getCheckInTime() != null;
        boolean checkedOut = attendance != null && attendance.getCheckOutTime() != null;
        boolean absent = attendance != null && attendance.getStatus() == AttendanceStatus.ABSENT;
        int late = attendance == null || attendance.getLateMinutes() == null ? 0 : attendance.getLateMinutes();
        int early = attendance == null || attendance.getEarlyLeaveMinutes() == null ? 0 : attendance.getEarlyLeaveMinutes();
        LocalTime start = attendance != null && attendance.getScheduledStartTime() != null
                ? attendance.getScheduledStartTime()
                : shift == null ? null : shift.getStartTime();
        LocalTime end = attendance != null && attendance.getScheduledEndTime() != null
                ? attendance.getScheduledEndTime()
                : shift == null ? null : shift.getEndTime();
        String shiftName = attendance != null && attendance.getScheduledShiftName() != null
                && !attendance.getScheduledShiftName().isBlank()
                ? attendance.getScheduledShiftName()
                : shift == null || shift.getShiftName() == null ? "Ca làm" : shift.getShiftName();
        String statusKey = ownStatusKey(checkedIn, checkedOut, absent);
        String worked = "—";
        if (checkedIn && checkedOut) {
            int minutes = (int) Math.max(0, ChronoUnit.MINUTES.between(
                    attendance.getCheckInTime(), attendance.getCheckOutTime()));
            worked = minuteText(minutes);
        }
        return StaffDayAttendanceView.Slot.builder()
                .shiftId(shift == null ? null : shift.getId())
                .shiftName(shiftName)
                .scheduleLabel(scheduleLabel(start, end).replace("–", " – "))
                .checkInLabel(checkedIn ? attendance.getCheckInTime().format(CLOCK_FORMATTER) : "—")
                .checkOutLabel(checkedOut ? attendance.getCheckOutTime().format(CLOCK_FORMATTER) : "—")
                .lateLabel(late + " phút")
                .earlyLabel(checkedOut || absent ? early + " phút" : "—")
                .late(late > 0)
                .workedLabel(worked)
                .statusKey(statusKey)
                .statusLabel(ownStatusLabel(statusKey))
                .slotKey(shift == null || shift.getId() == null ? "shift" : "shift-" + shift.getId())
                .attendanceId(attendance == null ? null : attendance.getId())
                .photoBase(attendance == null ? null : "/attendance/photos")
                .hasCheckInPhoto(hasPhoto(attendance == null ? null : attendance.getCheckInPhotoName()))
                .hasCheckOutPhoto(hasPhoto(attendance == null ? null : attendance.getCheckOutPhotoName()))
                .build();
    }

    private List<StaffDayAttendanceView.Slot> trainingSlots(Integer employeeId, LocalDate day) {
        Map<Integer, TrainingAttendance> byClass = new LinkedHashMap<>();
        for (TrainingAttendance attendance : trainingAttendanceRepository.findHistoryByEmployee(employeeId, day, day)) {
            if (attendance.getTrainingClass() == null || attendance.getTrainingClass().getId() == null) {
                continue;
            }
            byClass.putIfAbsent(attendance.getTrainingClass().getId(), attendance);
        }
        List<StaffDayAttendanceView.Slot> slots = new ArrayList<>();
        for (TrainingClassEnrollment enrollment : trainingClassEnrollmentRepository
                .findByEmployeeAndStatusWithClass(employeeId, TrainingClassStatus.APPROVED)) {
            TrainingClass trainingClass = enrollment.getTrainingClass();
            if (trainingClass == null || trainingClass.getStatus() != TrainingClassStatus.APPROVED || !covers(trainingClass, day)) {
                continue;
            }
            slots.add(toTrainingSlot(trainingClass, byClass.remove(trainingClass.getId())));
        }
        for (TrainingAttendance attendance : byClass.values()) {
            slots.add(toTrainingSlot(attendance.getTrainingClass(), attendance));
        }
        return slots;
    }

    private StaffDayAttendanceView.Slot toTrainingSlot(TrainingClass trainingClass, TrainingAttendance attendance) {
        String name = trainingClass != null && trainingClass.getClassName() != null
                ? trainingClass.getClassName()
                : (attendance != null && attendance.getClassName() != null ? attendance.getClassName() : "Đào tạo");
        LocalTime start = trainingClass != null && trainingClass.getStartTime() != null
                ? trainingClass.getStartTime()
                : (attendance == null ? null : attendance.getScheduledStartTime());
        LocalTime end = trainingClass != null && trainingClass.getEndTime() != null
                ? trainingClass.getEndTime()
                : (attendance == null ? null : attendance.getScheduledEndTime());
        boolean checkedIn = attendance != null && attendance.getCheckInTime() != null;
        boolean checkedOut = attendance != null && attendance.getCheckOutTime() != null;
        boolean absent = attendance != null && attendance.getStatus() == AttendanceStatus.ABSENT;
        int late = attendance == null || attendance.getLateMinutes() == null ? 0 : attendance.getLateMinutes();
        String worked = "—";
        if (checkedIn && checkedOut) {
            worked = minuteText((int) Math.max(0, ChronoUnit.MINUTES.between(
                    attendance.getCheckInTime(), attendance.getCheckOutTime())));
        }
        String statusKey = ownStatusKey(checkedIn, checkedOut, absent);
        Integer classId = trainingClass == null ? null : trainingClass.getId();
        return StaffDayAttendanceView.Slot.builder()
                .shiftId(classId)
                .shiftName(name)
                .scheduleLabel(scheduleLabel(start, end).replace("–", " – "))
                .checkInLabel(checkedIn ? attendance.getCheckInTime().format(CLOCK_FORMATTER) : "—")
                .checkOutLabel(checkedOut ? attendance.getCheckOutTime().format(CLOCK_FORMATTER) : "—")
                .lateLabel(late + " phút")
                .earlyLabel(checkedOut ? "0 phút" : "—")
                .late(late > 0)
                .workedLabel(worked)
                .statusKey(statusKey)
                .statusLabel(ownStatusLabel(statusKey))
                .slotKey(classId == null ? "training" : "training-" + classId)
                .attendanceId(attendance == null ? null : attendance.getId())
                .photoBase(attendance == null ? null : "/attendance/training-photos")
                .hasCheckInPhoto(hasPhoto(attendance == null ? null : attendance.getCheckInPhotoName()))
                .hasCheckOutPhoto(hasPhoto(attendance == null ? null : attendance.getCheckOutPhotoName()))
                .build();
    }

    private boolean covers(TrainingClass trainingClass, LocalDate day) {
        return trainingClass.getStartDate() != null
                && trainingClass.getEndDate() != null
                && !day.isBefore(trainingClass.getStartDate())
                && !day.isAfter(trainingClass.getEndDate());
    }

    private boolean hasPhoto(String name) {
        return name != null && !name.isBlank();
    }

    private MonthLine toMonthLine(Attendance attendance) {
        boolean checkedIn = attendance.getCheckInTime() != null;
        boolean checkedOut = attendance.getCheckOutTime() != null;
        int late = attendance.getLateMinutes() == null ? 0 : attendance.getLateMinutes();
        int early = attendance.getEarlyLeaveMinutes() == null ? 0 : attendance.getEarlyLeaveMinutes();
        boolean absent = attendance.getStatus() == AttendanceStatus.ABSENT;
        String shiftName = attendance.getScheduledShiftName() != null && !attendance.getScheduledShiftName().isBlank()
                ? attendance.getScheduledShiftName()
                : (attendance.getShift() == null || attendance.getShift().getShiftName() == null
                ? "Ca làm" : attendance.getShift().getShiftName());
        LocalTime start = attendance.getScheduledStartTime() != null
                ? attendance.getScheduledStartTime()
                : (attendance.getShift() == null ? null : attendance.getShift().getStartTime());
        LocalTime end = attendance.getScheduledEndTime() != null
                ? attendance.getScheduledEndTime()
                : (attendance.getShift() == null ? null : attendance.getShift().getEndTime());
        String worked = "—";
        if (checkedIn && checkedOut) {
            worked = minuteText((int) Math.max(0, ChronoUnit.MINUTES.between(
                    attendance.getCheckInTime(), attendance.getCheckOutTime())));
        }
        String key = statusKey(checkedIn, absent, late, early);
        ManagerMonthAttendanceView.DayLine line = ManagerMonthAttendanceView.DayLine.builder()
                .dateLabel(attendance.getWorkDate() == null ? "—" : attendance.getWorkDate().format(DAY_FORMATTER))
                .shiftName(shiftName)
                .scheduleLabel(scheduleLabel(start, end).replace("–", " – "))
                .checkInLabel(checkedIn ? attendance.getCheckInTime().format(CLOCK_FORMATTER) : "—")
                .checkOutLabel(checkedOut ? attendance.getCheckOutTime().format(CLOCK_FORMATTER) : "—")
                .lateLabel(late + " phút")
                .earlyLabel(checkedOut || absent ? early + " phút" : "—")
                .workedLabel(worked)
                .statusLabel(statusLabel(key))
                .attendanceId(attendance.getId())
                .hasCheckInPhoto(hasPhoto(attendance.getCheckInPhotoName()))
                .hasCheckOutPhoto(hasPhoto(attendance.getCheckOutPhotoName()))
                .late(late > 0)
                .earlyLeave(checkedOut && early > 0)
                .absent(absent)
                .build();
        return new MonthLine(attendance.getWorkDate() == null ? LocalDate.MIN : attendance.getWorkDate(), start, line);
    }

    private record MonthLine(LocalDate date, LocalTime start, ManagerMonthAttendanceView.DayLine line) {
    }

    private String ownStatusKey(boolean checkedIn, boolean checkedOut, boolean absent) {
        if (checkedOut) {
            return "done";
        }
        if (checkedIn) {
            return "working";
        }
        if (absent) {
            return "absent";
        }
        return "missing";
    }

    private String ownStatusLabel(String statusKey) {
        return switch (statusKey) {
            case "done" -> "Đã hoàn thành";
            case "working" -> "Đang làm";
            case "absent" -> "Vắng";
            default -> "Chưa check-in";
        };
    }

    private YearMonth parseMonth(String monthValue) {
        if (monthValue == null || monthValue.isBlank()) {
            return YearMonth.from(VietnamTime.today());
        }
        try {
            YearMonth month = YearMonth.parse(monthValue);
            if (month.getYear() < 2000 || month.getYear() > 2100) {
                throw new BusinessException("Tháng cần xem không hợp lệ.");
            }
            return month;
        } catch (DateTimeParseException ex) {
            throw new BusinessException("Tháng cần xem không hợp lệ.");
        }
    }

    private String employmentKey(Employee employee) {
        return "full-time";
    }

    private String roleLabel(Employee employee) {
        return "full-time".equals(employmentKey(employee)) ? "Full-time" : "Part-time";
    }

    private String durationLabel(int minutes) {
        return minuteText(minutes) + " tổng cộng";
    }

    private String minuteText(int minutes) {
        if (minutes <= 0) {
            return "0 phút";
        }
        int hours = minutes / 60;
        int remain = minutes % 60;
        if (hours == 0) {
            return remain + " phút";
        }
        if (remain == 0) {
            return hours + " giờ";
        }
        return hours + " giờ " + remain + " phút";
    }

    private String weekday(DayOfWeek day) {
        return switch (day) {
            case MONDAY -> "Thứ Hai";
            case TUESDAY -> "Thứ Ba";
            case WEDNESDAY -> "Thứ Tư";
            case THURSDAY -> "Thứ Năm";
            case FRIDAY -> "Thứ Sáu";
            case SATURDAY -> "Thứ Bảy";
            case SUNDAY -> "Chủ Nhật";
        };
    }

    private static final class MonthStats {
        private int workedSlots;
        private int lateSlots;
        private int earlySlots;
        private int absentSlots;
        private int lateMinutes;
        private int earlyMinutes;
    }
}

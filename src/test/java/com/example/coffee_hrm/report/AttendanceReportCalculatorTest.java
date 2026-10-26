package com.example.coffee_hrm.report;

import com.example.coffee_hrm.common.enums.AttendanceStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.enums.TrainingType;
import com.example.coffee_hrm.entity.Attendance;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.TrainingAttendance;
import com.example.coffee_hrm.entity.TrainingClass;
import com.example.coffee_hrm.entity.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttendanceReportCalculatorTest {

    private final Store store = Store.builder().id(1).storeName("Cầu Giấy").build();
    private final Store otherStore = Store.builder().id(2).storeName("Khác").build();
    private final Employee lan = employee(5, "Lan", RoleName.STAFF, store);
    private final Employee minh = employee(6, "Minh", RoleName.STAFF, store);

    @Test
    void countsEachShiftAsASlotAndDoesNotTreatMissingDaysAsAbsent() {
        LocalDate day1 = LocalDate.of(2026, 10, 1);
        LocalDate day2 = LocalDate.of(2026, 10, 2);
        Attendance morning = shift(11, lan, day1, AttendanceStatus.LATE, 25, 0, 240,
                time(day1, 8, 25), time(day1, 12, 0));
        Attendance afternoon = shift(12, lan, day1, AttendanceStatus.PRESENT, 0, 0, 120,
                time(day1, 13, 0), time(day1, 15, 0));
        Attendance absent = shift(13, lan, day2, AttendanceStatus.ABSENT, 0, 0, null, null, null);
        Attendance secondAbsent = shift(14, lan, day2, AttendanceStatus.ABSENT, 0, 0, null, null, null);
        Attendance open = shift(15, minh, LocalDate.of(2026, 10, 3), AttendanceStatus.PRESENT, 0, 0, null,
                time(LocalDate.of(2026, 10, 3), 8, 0), null);
        Attendance outside = shift(16, lan, LocalDate.of(2026, 9, 30), AttendanceStatus.ABSENT, 0, 0, null, null, null);
        Attendance otherStoreRow = shift(17, employee(8, "Khác", RoleName.STAFF, otherStore), day1,
                AttendanceStatus.ABSENT, 40, 0, 100, time(day1, 8, 0), time(day1, 9, 0));
        otherStoreRow.setStore(otherStore);

        AttendanceStatus before = morning.getStatus();
        LocalDateTime checkInBefore = morning.getCheckInTime();
        AttendanceReportWorkbook workbook = AttendanceReportCalculator.buildForPeriod(
                store, 2026, 10,
                List.of(lan, minh, employee(8, "Khác", RoleName.STAFF, otherStore)),
                List.of(morning, afternoon, absent, secondAbsent, open, outside, otherStoreRow),
                List.of(training(lan, day1)),
                "Nguyễn Văn Quản Lý",
                LocalDate.of(2026, 10, 10));

        assertEquals(before, morning.getStatus());
        assertEquals(checkInBefore, morning.getCheckInTime());
        assertEquals(2, workbook.getEmployees().size());

        AttendanceReportWorkbook.SummaryRow lanRow = workbook.getEmployees().stream()
                .filter(row -> row.getEmployeeId() == 5)
                .findFirst()
                .orElseThrow();
        assertEquals("Staff", lanRow.getRoleLabel());
        assertEquals(4, lanRow.getWorkDays());
        assertEquals(2, lanRow.getAttendedDays());
        assertEquals(1, lanRow.getLateRecords());
        assertEquals(25, lanRow.getLateMinutes());
        assertEquals(2, lanRow.getAbsentDays());
        assertEquals(2, lanRow.getAbsentRecords());
        assertEquals(360, lanRow.getWorkedMinutes());
        assertEquals(0, lanRow.getOpenCheckouts());

        AttendanceReportWorkbook.SummaryRow minhRow = workbook.getEmployees().stream()
                .filter(row -> row.getEmployeeId() == 6)
                .findFirst()
                .orElseThrow();
        assertEquals(1, minhRow.getWorkDays());
        assertEquals(1, minhRow.getAttendedDays());
        assertEquals(0, minhRow.getAbsentDays());
        assertEquals(0, minhRow.getAbsentRecords());
        assertEquals(0, minhRow.getWorkedMinutes());
        assertEquals(1, minhRow.getOpenCheckouts());
        assertTrue(minhRow.getNote().contains("Chưa Check-out"));

        AttendanceReportWorkbook.ShiftRow openRow = workbook.getShifts().stream()
                .filter(row -> row.getEmployeeId() == 6)
                .findFirst()
                .orElseThrow();
        assertNull(openRow.getCheckOut());
        assertNull(openRow.getWorkedMinutes());

        assertEquals(5, workbook.getPreview().getShiftRecordCount());
        assertEquals(1, workbook.getPreview().getTrainingRecordCount());
        assertEquals(2, workbook.getPreview().getAbsentCount());
        assertEquals(1, workbook.getPreview().getShiftLateCount());
        assertEquals(1, workbook.getPreview().getTrainingLateCount());
        assertEquals(2, workbook.getPreview().getLateCount());
        assertEquals(3, workbook.getPreview().getWorkDayCount());
        assertEquals(1, workbook.getPreview().getViolationCount());
        assertEquals(2, workbook.getPreview().getMissingCount());
        assertTrue(workbook.getShifts().stream().noneMatch(row -> row.getEmployeeId() == 8));
        assertTrue(workbook.getShifts().stream().noneMatch(row -> row.getWorkDate().getMonthValue() == 9));
        assertEquals("Pha chế", workbook.getTrainings().getFirst().getClassName());
        assertEquals("4", workbook.getTrainings().getFirst().getClassCode());
        assertEquals("Cô Hoa", workbook.getTrainings().getFirst().getTrainerName());
    }

    @Test
    void emptyPeriodStillBuildsPreview() {
        AttendanceReportWorkbook workbook = AttendanceReportCalculator.buildForPeriod(
                store, 2026, 3, List.of(), List.of(), List.of(), "Admin", LocalDate.of(2026, 3, 1));
        assertEquals(0, workbook.getPreview().getEmployeeCount());
        assertEquals(0, workbook.getPreview().getAbsentCount());
        assertEquals("03/2026", workbook.periodLabel());
    }

    private TrainingAttendance training(Employee employee, LocalDate date) {
        User trainer = User.builder()
                .id(20)
                .username("hoa")
                .passwordHash("x")
                .employee(Employee.builder().id(20).fullName("Cô Hoa").store(store).build())
                .build();
        TrainingClass trainingClass = TrainingClass.builder()
                .id(4)
                .className("Pha chế")
                .trainingType(TrainingType.STORE_TRAINING)
                .trainer(trainer)
                .build();
        return TrainingAttendance.builder()
                .id(30)
                .employee(employee)
                .store(store)
                .trainingClass(trainingClass)
                .className("Pha chế")
                .workDate(date)
                .status(AttendanceStatus.LATE)
                .lateMinutes(10)
                .earlyLeaveMinutes(0)
                .checkInTime(time(date, 9, 10))
                .checkOutTime(time(date, 11, 0))
                .build();
    }

    private Attendance shift(int id,
                             Employee employee,
                             LocalDate date,
                             AttendanceStatus status,
                             int late,
                             int early,
                             Integer worked,
                             LocalDateTime checkIn,
                             LocalDateTime checkOut) {
        return Attendance.builder()
                .id(id)
                .employee(employee)
                .store(employee.getStore())
                .workDate(date)
                .status(status)
                .lateMinutes(late)
                .earlyLeaveMinutes(early)
                .workingMinutes(worked)
                .checkInTime(checkIn)
                .checkOutTime(checkOut)
                .scheduledShiftName("Ca sáng")
                .scheduledStartTime(LocalTime.of(8, 0))
                .scheduledEndTime(LocalTime.of(12, 0))
                .build();
    }

    private Employee employee(int id, String name, RoleName roleName, Store home) {
        Role role = Role.builder().roleName(roleName).build();
        User user = User.builder().id(id).username("u" + id).passwordHash("x").role(role).build();
        Employee employee = Employee.builder().id(id).fullName(name).store(home).build();
        employee.setUser(user);
        user.setEmployee(employee);
        return employee;
    }

    private LocalDateTime time(LocalDate date, int hour, int minute) {
        return LocalDateTime.of(date, LocalTime.of(hour, minute));
    }
}

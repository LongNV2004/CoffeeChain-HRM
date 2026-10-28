package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.AssignmentStatus;
import com.example.coffee_hrm.common.enums.AttendanceStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.enums.TrainingClassStatus;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.response.ManagerDayAttendanceView;
import com.example.coffee_hrm.entity.Attendance;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.Shift;
import com.example.coffee_hrm.entity.ShiftAssignment;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.TrainingAttendance;
import com.example.coffee_hrm.entity.TrainingClass;
import com.example.coffee_hrm.entity.TrainingClassEnrollment;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.dto.response.ManagerMonthAttendanceView;
import com.example.coffee_hrm.dto.response.StaffDayAttendanceView;
import com.example.coffee_hrm.repository.AttendanceRepository;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.ShiftAssignmentRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.repository.TrainingAttendanceRepository;
import com.example.coffee_hrm.repository.TrainingClassEnrollmentRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManagerDayAttendanceServiceImplTest {

    @Mock
    private StoreRepository storeRepository;
    @Mock
    private ShiftAssignmentRepository shiftAssignmentRepository;
    @Mock
    private AttendanceRepository attendanceRepository;
    @Mock
    private TrainingAttendanceRepository trainingAttendanceRepository;
    @Mock
    private TrainingClassEnrollmentRepository trainingClassEnrollmentRepository;
    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private ManagerDayAttendanceServiceImpl service;

    private Store store;
    private AuthenticatedUser manager;

    @BeforeEach
    void setUp() {
        Employee managerEmployee = Employee.builder()
                .id(10).fullName("Manager A").status(EmployeeStatus.ACTIVE).build();
        store = Store.builder().id(1).storeName("The Coffee House - Cầu Giấy").manager(managerEmployee).build();
        managerEmployee.setStore(store);
        Role role = Role.builder().id(2).roleName(RoleName.MANAGER).build();
        manager = AuthenticatedUser.from(User.builder()
                .id(2).username("manager1").passwordHash("hash").role(role)
                .employee(managerEmployee).isActive(true).build());
    }

    @Test
    void dayBoardUsesAssignedSlotsAndRealAttendance() {
        LocalDate day = VietnamTime.today().minusDays(1);
        Employee lateEmployee = Employee.builder().id(3).fullName("Lê Thị Thu").store(store).build();
        Employee missingEmployee = Employee.builder().id(4).fullName("Phạm Đức Long").store(store).build();
        Shift morning = Shift.builder().id(1).shiftName("Ca sáng").store(store)
                .startTime(LocalTime.of(7, 0)).endTime(LocalTime.of(11, 0)).build();
        Shift night = Shift.builder().id(2).shiftName("Ca tối").store(store)
                .startTime(LocalTime.of(18, 0)).endTime(LocalTime.of(22, 0)).build();
        ShiftAssignment lateSlot = ShiftAssignment.builder()
                .id(11).employee(lateEmployee).shift(morning).workDate(day)
                .status(AssignmentStatus.ASSIGNED).build();
        ShiftAssignment missingSlot = ShiftAssignment.builder()
                .id(12).employee(missingEmployee).shift(night).workDate(day)
                .status(AssignmentStatus.ASSIGNED).build();
        Attendance attendance = Attendance.builder()
                .id(30).employee(lateEmployee).shift(morning).workDate(day)
                .scheduledShiftName("Ca sáng")
                .scheduledStartTime(LocalTime.of(7, 0))
                .scheduledEndTime(LocalTime.of(11, 0))
                .checkInTime(day.atTime(7, 10, 5))
                .checkOutTime(day.atTime(11, 0, 0))
                .lateMinutes(10)
                .earlyLeaveMinutes(0)
                .status(AttendanceStatus.LATE)
                .build();
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(shiftAssignmentRepository.findPublishedAssignedForStoreOnDate(1, day, AssignmentStatus.ASSIGNED))
                .thenReturn(List.of(lateSlot, missingSlot));
        when(attendanceRepository.findHistoryByStore(1, day, day)).thenReturn(List.of(attendance));

        ManagerDayAttendanceView view = service.getDay(manager, day);

        assertEquals(store.getStoreName(), view.getStoreName());
        assertEquals(2, view.getShifts().size());
        assertEquals(1, view.getShifts().get(0).getSlots());
        assertEquals("Ca sáng", view.getShifts().get(0).getShiftName());
        assertEquals(1, view.getViolationSlots());
        assertEquals(2, view.getSlots().size());
        ManagerDayAttendanceView.Slot late = view.getSlots().get(0);
        assertEquals("NV003", late.getEmployeeCode());
        assertEquals("07:10", late.getCheckInLabel());
        assertEquals("11:00", late.getCheckOutLabel());
        assertEquals("Đi muộn", late.getStatusLabel());
        assertEquals("10 phút", late.getLateLabel());
        assertTrue(late.isLate());
        ManagerDayAttendanceView.Slot missing = view.getSlots().get(1);
        assertEquals("Thiếu check-in", missing.getStatusLabel());
        assertEquals("—", missing.getCheckInLabel());
        assertEquals("0 phút", missing.getLateLabel());
    }

    @Test
    void futureDayIsRejected() {
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.getDay(manager, VietnamTime.today().plusDays(1)));

        assertTrue(ex.getMessage().contains("sau ngày hiện tại"));
    }

    @Test
    void monthBoardCountsSlotsAndMinuteTotalsWithoutSeconds() {
        LocalDate today = VietnamTime.today();
        LocalDate start = today.withDayOfMonth(1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        Employee active = Employee.builder().id(3).fullName("Lê Thị Thu").store(store)
                .status(EmployeeStatus.ACTIVE).build();
        Employee waiting = Employee.builder().id(4).fullName("Phạm Đức Long").store(store)
                .status(EmployeeStatus.ACTIVE).build();
        Employee terminated = Employee.builder().id(9).fullName("Đã nghỉ").store(store)
                .status(EmployeeStatus.TERMINATED).build();
        Shift morning = Shift.builder().id(1).shiftName("Ca sáng").store(store)
                .startTime(LocalTime.of(7, 0)).endTime(LocalTime.of(11, 0)).build();
        Shift afternoon = Shift.builder().id(2).shiftName("Ca chiều").store(store)
                .startTime(LocalTime.of(13, 0)).endTime(LocalTime.of(17, 0)).build();
        ShiftAssignment worked = ShiftAssignment.builder()
                .id(1).employee(active).shift(morning).workDate(today).isPublished(true)
                .status(AssignmentStatus.ASSIGNED).build();
        ShiftAssignment missed = ShiftAssignment.builder()
                .id(2).employee(active).shift(afternoon).workDate(today).isPublished(true)
                .status(AssignmentStatus.ASSIGNED).build();
        ShiftAssignment untouched = ShiftAssignment.builder()
                .id(3).employee(waiting).shift(morning).workDate(today).isPublished(true)
                .status(AssignmentStatus.ASSIGNED).build();
        ShiftAssignment old = ShiftAssignment.builder()
                .id(4).employee(terminated).shift(morning).workDate(today).isPublished(true)
                .status(AssignmentStatus.ASSIGNED).build();
        Attendance late = Attendance.builder()
                .id(30).employee(active).shift(morning).store(store).workDate(today)
                .checkInTime(today.atTime(7, 10, 40))
                .checkOutTime(today.atTime(10, 50, 20))
                .lateMinutes(75)
                .earlyLeaveMinutes(20)
                .status(AttendanceStatus.LATE_AND_EARLY)
                .build();
        Attendance absent = Attendance.builder()
                .id(31).employee(active).shift(afternoon).store(store).workDate(today)
                .lateMinutes(0)
                .earlyLeaveMinutes(0)
                .status(AttendanceStatus.ABSENT)
                .build();
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(employeeRepository.findByStoreIdWithAccount(1)).thenReturn(List.of(active, waiting, terminated));
        when(shiftAssignmentRepository.findPublishedAssignedForStoreBetween(1, start, end, AssignmentStatus.ASSIGNED))
                .thenReturn(List.of(worked, missed, untouched, old));
        when(attendanceRepository.findForMonthlyReport(1, start, end)).thenReturn(List.of(late, absent));

        ManagerMonthAttendanceView view = service.getMonth(manager, null);

        assertEquals(2, view.getEmployeeCount());
        assertEquals(3, view.getScheduledSlots());
        ManagerMonthAttendanceView.EmployeeMonth first = view.getEmployees().get(0);
        assertEquals("NV003", first.getEmployeeCode());
        assertEquals(2, first.getScheduledSlots());
        assertEquals(1, first.getWorkedSlots());
        assertEquals(1, first.getLateSlots());
        assertEquals(1, first.getEarlySlots());
        assertEquals(1, first.getAbsentSlots());
        assertEquals("1 giờ 15 phút tổng cộng", first.getLateDurationLabel());
        assertEquals("20 phút tổng cộng", first.getEarlyDurationLabel());
        assertEquals(2, first.getDays().size());
        assertEquals("Ca sáng", first.getDays().get(0).getShiftName());
        assertTrue(first.getDays().get(0).isLate());
        assertTrue(first.getDays().get(0).isEarlyLeave());
        assertEquals("07:10", first.getDays().get(0).getCheckInLabel());
        assertEquals("3 giờ 39 phút", first.getDays().get(0).getWorkedLabel());
        assertEquals("Thiếu check-in", first.getDays().get(1).getStatusLabel());
        ManagerMonthAttendanceView.EmployeeMonth second = view.getEmployees().get(1);
        assertEquals(1, second.getScheduledSlots());
        assertEquals(0, second.getWorkedSlots());
        assertEquals(0, second.getAbsentSlots());
    }

    @Test
    void managerWithoutStoreIsRejected() {
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> service.getDay(manager, VietnamTime.today()));
        verify(shiftAssignmentRepository, org.mockito.Mockito.never())
                .findPublishedAssignedForStoreOnDate(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    void staffSeesOnlyOwnSlotsForTheSelectedDay() {
        LocalDate day = VietnamTime.today();
        Employee self = Employee.builder().id(20).fullName("Lê Thị Thu").store(store)
                .status(EmployeeStatus.ACTIVE).build();
        Role role = Role.builder().id(3).roleName(RoleName.STAFF).build();
        AuthenticatedUser staff = AuthenticatedUser.from(User.builder()
                .id(8).username("staff2").passwordHash("hash").role(role)
                .employee(self).isActive(true).build());
        Shift morning = Shift.builder().id(1).shiftName("Ca sáng").store(store)
                .startTime(LocalTime.of(7, 0)).endTime(LocalTime.of(11, 0)).build();
        Shift afternoon = Shift.builder().id(2).shiftName("Ca chiều").store(store)
                .startTime(LocalTime.of(13, 0)).endTime(LocalTime.of(17, 0)).build();
        Shift hidden = Shift.builder().id(3).shiftName("Ca tối").store(store)
                .startTime(LocalTime.of(18, 0)).endTime(LocalTime.of(22, 0)).build();
        ShiftAssignment done = ShiftAssignment.builder()
                .id(1).employee(self).shift(morning).workDate(day).isPublished(true)
                .status(AssignmentStatus.ASSIGNED).build();
        ShiftAssignment open = ShiftAssignment.builder()
                .id(2).employee(self).shift(afternoon).workDate(day).isPublished(true)
                .status(AssignmentStatus.ASSIGNED).build();
        ShiftAssignment draft = ShiftAssignment.builder()
                .id(3).employee(self).shift(hidden).workDate(day).isPublished(false)
                .status(AssignmentStatus.ASSIGNED).build();
        Attendance finished = Attendance.builder()
                .id(40).employee(self).shift(morning).workDate(day)
                .scheduledShiftName("Ca sáng")
                .scheduledStartTime(LocalTime.of(7, 0))
                .scheduledEndTime(LocalTime.of(11, 0))
                .checkInTime(day.atTime(7, 10))
                .checkOutTime(day.atTime(11, 0))
                .lateMinutes(10)
                .earlyLeaveMinutes(0)
                .checkInPhotoName("40-in.jpg")
                .checkOutPhotoName("40-out.jpg")
                .status(AttendanceStatus.LATE)
                .build();
        Attendance working = Attendance.builder()
                .id(41).employee(self).shift(afternoon).workDate(day)
                .checkInTime(day.atTime(12, 55))
                .lateMinutes(0)
                .earlyLeaveMinutes(0)
                .status(AttendanceStatus.PRESENT)
                .build();
        when(shiftAssignmentRepository.findAssignmentsForAttendance(20, day, day, AssignmentStatus.ASSIGNED))
                .thenReturn(List.of(done, open, draft));
        when(attendanceRepository.findHistoryByEmployee(20, day, day)).thenReturn(List.of(finished, working));

        StaffDayAttendanceView view = service.getOwnDay(staff, day);

        assertEquals(2, view.getSlots().size());
        StaffDayAttendanceView.Slot first = view.getSlots().get(0);
        assertEquals("Ca sáng", first.getShiftName());
        assertEquals("Đi muộn", first.getStatusLabel());
        assertEquals("late", first.getStatusKey());
        assertEquals("07:10", first.getCheckInLabel());
        assertEquals("11:00", first.getCheckOutLabel());
        assertEquals("10 phút", first.getLateLabel());
        assertEquals("3 giờ 50 phút", first.getWorkedLabel());
        assertTrue(first.isLate());
        StaffDayAttendanceView.Slot second = view.getSlots().get(1);
        assertEquals("Đang làm", second.getStatusLabel());
        assertEquals("12:55", second.getCheckInLabel());
        assertEquals("—", second.getCheckOutLabel());
        assertEquals("—", second.getWorkedLabel());
        assertEquals("—", second.getEarlyLabel());
        assertTrue(first.isHasCheckInPhoto());
        assertTrue(first.isHasCheckOutPhoto());
        assertEquals("/attendance/photos", first.getPhotoBase());
        assertFalse(second.isHasCheckInPhoto());
    }

    @Test
    void staffDayIncludesOwnTrainingSlotAndItsPhotos() {
        LocalDate day = VietnamTime.today();
        Employee self = Employee.builder().id(20).fullName("Lê Thị Thu").store(store)
                .status(EmployeeStatus.ACTIVE).build();
        Role role = Role.builder().id(3).roleName(RoleName.STAFF).build();
        AuthenticatedUser staff = AuthenticatedUser.from(User.builder()
                .id(8).username("staff2").passwordHash("hash").role(role)
                .employee(self).isActive(true).build());
        TrainingClass lesson = TrainingClass.builder()
                .id(7).className("Pha chế cơ bản").status(TrainingClassStatus.APPROVED)
                .startDate(day).endDate(day)
                .startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(11, 0))
                .build();
        TrainingAttendance attendance = TrainingAttendance.builder()
                .id(3).employee(self).trainingClass(lesson).workDate(day)
                .className("Pha chế cơ bản")
                .scheduledStartTime(LocalTime.of(9, 0))
                .scheduledEndTime(LocalTime.of(11, 0))
                .checkInTime(day.atTime(9, 5))
                .checkOutTime(day.atTime(11, 0))
                .lateMinutes(0)
                .earlyLeaveMinutes(0)
                .checkInPhotoName("t3-in.jpg")
                .checkOutPhotoName("t3-out.jpg")
                .status(AttendanceStatus.PRESENT)
                .build();
        when(shiftAssignmentRepository.findAssignmentsForAttendance(20, day, day, AssignmentStatus.ASSIGNED))
                .thenReturn(List.of());
        when(attendanceRepository.findHistoryByEmployee(20, day, day)).thenReturn(List.of());
        when(trainingAttendanceRepository.findHistoryByEmployee(20, day, day)).thenReturn(List.of(attendance));
        when(trainingClassEnrollmentRepository.findByEmployeeAndStatusWithClass(20, TrainingClassStatus.APPROVED))
                .thenReturn(List.of(TrainingClassEnrollment.builder().id(1).employee(self).trainingClass(lesson).build()));

        StaffDayAttendanceView view = service.getOwnDay(staff, day);

        assertEquals(1, view.getSlots().size());
        StaffDayAttendanceView.Slot slot = view.getSlots().get(0);
        assertEquals("training-7", slot.getSlotKey());
        assertEquals("Pha chế cơ bản", slot.getShiftName());
        assertEquals("09:00 – 11:00", slot.getScheduleLabel());
        assertEquals("09:05", slot.getCheckInLabel());
        assertEquals("11:00", slot.getCheckOutLabel());
        assertEquals("1 giờ 55 phút", slot.getWorkedLabel());
        assertTrue(slot.isHasCheckInPhoto());
        assertTrue(slot.isHasCheckOutPhoto());
        assertEquals("/attendance/training-photos", slot.getPhotoBase());
    }
}

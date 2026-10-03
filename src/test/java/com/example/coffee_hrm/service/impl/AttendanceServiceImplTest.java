package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.AssignmentStatus;
import com.example.coffee_hrm.common.enums.AttendanceStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.entity.Attendance;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.Shift;
import com.example.coffee_hrm.entity.ShiftAssignment;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.AttendanceRepository;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.ShiftAssignmentRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.AttendancePolicyProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceImplTest {

    private static final LocalDate WORK_DATE = LocalDate.of(2026, 10, 4);

    @Mock
    private AttendanceRepository attendanceRepository;
    @Mock
    private ShiftAssignmentRepository shiftAssignmentRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private StoreRepository storeRepository;

    private AttendancePolicyProperties policy;
    private AttendanceServiceImpl attendanceService;
    private Store store;
    private Employee staffEmployee;
    private AuthenticatedUser staffUser;
    private Shift morning;
    private ShiftAssignment morningAssignment;

    @BeforeEach
    void setUp() {
        policy = new AttendancePolicyProperties();
        policy.setCheckInLeadMinutes(60);
        policy.setLateThresholdMinutes(20);
        policy.setAllowEarlyCheckout(true);
        attendanceService = new AttendanceServiceImpl(
                attendanceRepository,
                shiftAssignmentRepository,
                employeeRepository,
                storeRepository,
                policy);

        store = Store.builder().id(1).storeName("Cửa hàng 1").currentIp("113.0.0.1").build();
        staffEmployee = Employee.builder()
                .id(20).fullName("Trần Văn Tuấn").store(store).status(EmployeeStatus.ACTIVE).build();
        staffUser = AuthenticatedUser.from(buildUser(3, "tuanth", RoleName.STAFF, staffEmployee));
        morning = Shift.builder()
                .id(1).shiftName("Ca sáng").store(store)
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(17, 0))
                .build();
        morningAssignment = ShiftAssignment.builder()
                .id(9).employee(staffEmployee).shift(morning).workDate(WORK_DATE)
                .status(AssignmentStatus.ASSIGNED).isPublished(true)
                .build();
    }

    @Test
    void checkInCreatesOneRecordWhenIpAndShiftAreValid() {
        stubShift(List.of(morningAssignment));
        when(attendanceRepository.findByEmployee_IdAndShift_IdAndWorkDate(20, 1, WORK_DATE))
                .thenReturn(Optional.empty());
        when(storeRepository.findById(1)).thenReturn(Optional.of(store));
        when(attendanceRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        attendanceService.checkIn(staffUser, "113.0.0.1", WORK_DATE.atTime(7, 30));

        ArgumentCaptor<Attendance> captor = ArgumentCaptor.forClass(Attendance.class);
        verify(attendanceRepository).saveAndFlush(captor.capture());
        Attendance saved = captor.getValue();
        assertEquals(20, saved.getEmployee().getId());
        assertEquals(WORK_DATE, saved.getWorkDate());
        assertEquals(LocalTime.of(8, 0), saved.getScheduledStartTime());
        assertEquals(LocalTime.of(17, 0), saved.getScheduledEndTime());
        assertEquals("113.0.0.1", saved.getCheckInIp());
        assertEquals(0, saved.getLateMinutes());
        assertEquals(AttendanceStatus.PRESENT, saved.getStatus());
        assertNull(saved.getCheckOutTime());
    }

    @Test
    void checkInRejectsWrongIpBeforeCreatingAttendance() {
        stubShift(List.of(morningAssignment));
        when(attendanceRepository.findByEmployee_IdAndShift_IdAndWorkDate(20, 1, WORK_DATE))
                .thenReturn(Optional.empty());
        when(storeRepository.findById(1)).thenReturn(Optional.of(store));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> attendanceService.checkIn(staffUser, "10.0.0.8", WORK_DATE.atTime(7, 30)));

        assertEquals(AttendanceServiceImpl.WRONG_IP, ex.getMessage());
        verify(attendanceRepository, never()).saveAndFlush(any());
    }

    @Test
    void checkInRejectsWhenTooEarly() {
        stubShift(List.of(morningAssignment));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> attendanceService.checkIn(staffUser, "113.0.0.1", WORK_DATE.atTime(6, 0)));

        assertEquals(AttendanceServiceImpl.TOO_EARLY, ex.getMessage());
        verify(attendanceRepository, never()).saveAndFlush(any());
    }

    @Test
    void checkInRejectsWhenNoShift() {
        stubShift(List.of());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> attendanceService.checkIn(staffUser, "113.0.0.1", WORK_DATE.atTime(8, 0)));

        assertEquals(AttendanceServiceImpl.NO_SHIFT, ex.getMessage());
    }

    @Test
    void checkInRejectsSecondCheckInForSameShift() {
        stubShift(List.of(morningAssignment));
        when(attendanceRepository.findByEmployee_IdAndShift_IdAndWorkDate(20, 1, WORK_DATE))
                .thenReturn(Optional.of(Attendance.builder().id(5).employee(staffEmployee).build()));
        when(storeRepository.findById(1)).thenReturn(Optional.of(store));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> attendanceService.checkIn(staffUser, "113.0.0.1", WORK_DATE.atTime(9, 0)));

        assertEquals(AttendanceServiceImpl.ALREADY_IN, ex.getMessage());
        verify(attendanceRepository, never()).saveAndFlush(any());
    }

    @Test
    void checkInRecordsLateOnlyFromTwentyMinutes() {
        stubShift(List.of(morningAssignment));
        when(attendanceRepository.findByEmployee_IdAndShift_IdAndWorkDate(20, 1, WORK_DATE))
                .thenReturn(Optional.empty());
        when(storeRepository.findById(1)).thenReturn(Optional.of(store));
        when(attendanceRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        attendanceService.checkIn(staffUser, "113.0.0.1", WORK_DATE.atTime(8, 19));
        ArgumentCaptor<Attendance> early = ArgumentCaptor.forClass(Attendance.class);
        verify(attendanceRepository).saveAndFlush(early.capture());
        assertEquals(0, early.getValue().getLateMinutes());
        assertEquals(AttendanceStatus.PRESENT, early.getValue().getStatus());

        attendanceService.checkIn(staffUser, "113.0.0.1", WORK_DATE.atTime(8, 20));
        ArgumentCaptor<Attendance> late = ArgumentCaptor.forClass(Attendance.class);
        verify(attendanceRepository, org.mockito.Mockito.times(2)).saveAndFlush(late.capture());
        Attendance lateRecord = late.getAllValues().get(1);
        assertEquals(20, lateRecord.getLateMinutes());
        assertEquals(AttendanceStatus.LATE, lateRecord.getStatus());
    }

    @Test
    void checkOutUpdatesTheSameRecordAndStoresEarlyLeave() {
        Attendance open = openAttendance(0);
        when(employeeRepository.findByIdWithStore(20)).thenReturn(Optional.of(staffEmployee));
        when(attendanceRepository.findOpenByEmployeeId(20)).thenReturn(List.of(open));
        when(storeRepository.findById(1)).thenReturn(Optional.of(store));

        attendanceService.checkOut(staffUser, "113.0.0.1", WORK_DATE.atTime(16, 30));

        assertEquals(WORK_DATE.atTime(16, 30), open.getCheckOutTime());
        assertEquals("113.0.0.1", open.getCheckOutIp());
        assertEquals(510, open.getWorkingMinutes());
        assertEquals(30, open.getEarlyLeaveMinutes());
        assertEquals(AttendanceStatus.EARLY_LEAVE, open.getStatus());
        verify(attendanceRepository).save(open);
        verify(attendanceRepository, never()).saveAndFlush(any());
    }

    @Test
    void checkOutMarksLateAndEarly() {
        Attendance open = openAttendance(30);
        when(employeeRepository.findByIdWithStore(20)).thenReturn(Optional.of(staffEmployee));
        when(attendanceRepository.findOpenByEmployeeId(20)).thenReturn(List.of(open));
        when(storeRepository.findById(1)).thenReturn(Optional.of(store));

        attendanceService.checkOut(staffUser, "113.0.0.1", WORK_DATE.atTime(16, 30));

        assertEquals(AttendanceStatus.LATE_AND_EARLY, open.getStatus());
        assertEquals(30, open.getLateMinutes());
        assertEquals(30, open.getEarlyLeaveMinutes());
    }

    @Test
    void checkOutCanBeBlockedByPolicy() {
        policy.setAllowEarlyCheckout(false);
        Attendance open = openAttendance(0);
        when(employeeRepository.findByIdWithStore(20)).thenReturn(Optional.of(staffEmployee));
        when(attendanceRepository.findOpenByEmployeeId(20)).thenReturn(List.of(open));
        when(storeRepository.findById(1)).thenReturn(Optional.of(store));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> attendanceService.checkOut(staffUser, "113.0.0.1", WORK_DATE.atTime(16, 30)));

        assertEquals(AttendanceServiceImpl.EARLY_CHECKOUT_BLOCKED, ex.getMessage());
        assertNull(open.getCheckOutTime());
    }

    @Test
    void checkOutRejectsWrongIp() {
        Attendance open = openAttendance(0);
        when(employeeRepository.findByIdWithStore(20)).thenReturn(Optional.of(staffEmployee));
        when(attendanceRepository.findOpenByEmployeeId(20)).thenReturn(List.of(open));
        when(storeRepository.findById(1)).thenReturn(Optional.of(store));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> attendanceService.checkOut(staffUser, "10.1.1.1", WORK_DATE.atTime(17, 0)));

        assertEquals(AttendanceServiceImpl.WRONG_IP, ex.getMessage());
        assertNull(open.getCheckOutTime());
    }

    @Test
    void checkOutRejectsWhenAlreadyCheckedOut() {
        Attendance closed = openAttendance(0);
        closed.setCheckOutTime(WORK_DATE.atTime(17, 0));
        when(employeeRepository.findByIdWithStore(20)).thenReturn(Optional.of(staffEmployee));
        when(attendanceRepository.findOpenByEmployeeId(20)).thenReturn(List.of());
        stubShift(List.of(morningAssignment));
        when(attendanceRepository.findByEmployee_IdAndShift_IdAndWorkDate(20, 1, WORK_DATE))
                .thenReturn(Optional.of(closed));
        when(storeRepository.findById(1)).thenReturn(Optional.of(store));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> attendanceService.checkOut(staffUser, "113.0.0.1", WORK_DATE.atTime(17, 10)));

        assertEquals(AttendanceServiceImpl.ALREADY_OUT, ex.getMessage());
    }

    @Test
    void checkOutRejectsWhenNotCheckedIn() {
        when(employeeRepository.findByIdWithStore(20)).thenReturn(Optional.of(staffEmployee));
        when(attendanceRepository.findOpenByEmployeeId(20)).thenReturn(List.of());
        stubShift(List.of(morningAssignment));
        when(attendanceRepository.findByEmployee_IdAndShift_IdAndWorkDate(20, 1, WORK_DATE))
                .thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> attendanceService.checkOut(staffUser, "113.0.0.1", WORK_DATE.atTime(9, 0)));

        assertEquals(AttendanceServiceImpl.NOT_CHECKED_IN, ex.getMessage());
    }

    @Test
    void secondShiftOnTheSameDayCreatesAnotherAttendance() {
        Shift afternoon = Shift.builder()
                .id(2).shiftName("Ca chiều").store(store)
                .startTime(LocalTime.of(14, 0)).endTime(LocalTime.of(18, 0))
                .build();
        ShiftAssignment afternoonAssignment = ShiftAssignment.builder()
                .id(10).employee(staffEmployee).shift(afternoon).workDate(WORK_DATE)
                .status(AssignmentStatus.ASSIGNED).isPublished(true)
                .build();
        stubShift(List.of(morningAssignment, afternoonAssignment));
        when(attendanceRepository.findByEmployee_IdAndShift_IdAndWorkDate(20, 1, WORK_DATE))
                .thenReturn(Optional.of(Attendance.builder().id(5).build()));
        when(attendanceRepository.findByEmployee_IdAndShift_IdAndWorkDate(20, 2, WORK_DATE))
                .thenReturn(Optional.empty());
        when(storeRepository.findById(1)).thenReturn(Optional.of(store));
        when(attendanceRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        attendanceService.checkIn(staffUser, "113.0.0.1", WORK_DATE.atTime(13, 30));

        ArgumentCaptor<Attendance> captor = ArgumentCaptor.forClass(Attendance.class);
        verify(attendanceRepository).saveAndFlush(captor.capture());
        assertEquals(2, captor.getValue().getShift().getId());
        assertEquals(LocalTime.of(14, 0), captor.getValue().getScheduledStartTime());
    }

    @Test
    void duplicateInsertIsReportedAsAlreadyCheckedIn() {
        stubShift(List.of(morningAssignment));
        when(attendanceRepository.findByEmployee_IdAndShift_IdAndWorkDate(20, 1, WORK_DATE))
                .thenReturn(Optional.empty());
        when(storeRepository.findById(1)).thenReturn(Optional.of(store));
        when(attendanceRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> attendanceService.checkIn(staffUser, "113.0.0.1", WORK_DATE.atTime(8, 0)));

        assertEquals(AttendanceServiceImpl.ALREADY_IN, ex.getMessage());
    }

    @Test
    void checkOutUsesCurrentStoreIpNotTheCheckInIp() {
        Attendance open = openAttendance(0);
        open.setCheckInIp("113.0.0.1");
        store.setCurrentIp("14.0.0.5");
        when(employeeRepository.findByIdWithStore(20)).thenReturn(Optional.of(staffEmployee));
        when(attendanceRepository.findOpenByEmployeeId(20)).thenReturn(List.of(open));
        when(storeRepository.findById(1)).thenReturn(Optional.of(store));

        attendanceService.checkOut(staffUser, "14.0.0.5", WORK_DATE.atTime(17, 0));

        assertEquals("113.0.0.1", open.getCheckInIp());
        assertEquals("14.0.0.5", open.getCheckOutIp());
        assertEquals(AttendanceStatus.PRESENT, open.getStatus());
    }

    @Test
    void staffCannotUpdateStoreIp() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> attendanceService.updateStoreIp(staffUser, "118.68.6.70"));

        assertEquals("Bạn không có quyền cập nhật IP cửa hàng.", ex.getMessage());
        verify(storeRepository, never()).save(any());
    }

    @Test
    void managerUpdatesOnlyTheManagedStoreIp() {
        Employee managerEmployee = Employee.builder().id(10).fullName("Manager A").store(store).build();
        store.setManager(managerEmployee);
        AuthenticatedUser manager = AuthenticatedUser.from(buildUser(2, "manager1", RoleName.MANAGER, managerEmployee));
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));

        attendanceService.updateStoreIp(manager, "::ffff:14.0.0.5");

        assertEquals("14.0.0.5", store.getCurrentIp());
        verify(storeRepository).save(store);
    }

    @Test
    void managerUpdateReplacesTheStoredIpWhenTheNetworkChanges() {
        Employee managerEmployee = Employee.builder().id(10).fullName("Manager A").store(store).build();
        store.setManager(managerEmployee);
        store.setCurrentIp("118.68.6.70");
        AuthenticatedUser manager = AuthenticatedUser.from(buildUser(2, "manager1", RoleName.MANAGER, managerEmployee));
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));

        attendanceService.updateStoreIp(manager, "14.1.2.3");

        assertEquals("14.1.2.3", store.getCurrentIp());
    }

    @Test
    void managerUpdateDoesNotKeepLoopbackAsTheStoreIp() {
        Employee managerEmployee = Employee.builder().id(10).fullName("Manager A").store(store).build();
        store.setManager(managerEmployee);
        AuthenticatedUser manager = AuthenticatedUser.from(buildUser(2, "manager1", RoleName.MANAGER, managerEmployee));
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> attendanceService.updateStoreIp(manager, "::1"));

        assertEquals("Không xác định được địa chỉ IP.", ex.getMessage());
        assertEquals("113.0.0.1", store.getCurrentIp());
        verify(storeRepository, never()).save(any());
    }

    @Test
    void endedShiftWithoutAttendanceIsMarkedAbsent() {
        when(shiftAssignmentRepository.findPublishedAssignedOnDate(WORK_DATE, AssignmentStatus.ASSIGNED))
                .thenReturn(List.of(morningAssignment));
        when(attendanceRepository.findByEmployee_IdAndShift_IdAndWorkDate(20, 1, WORK_DATE))
                .thenReturn(Optional.empty());
        when(attendanceRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        attendanceService.markAbsences(WORK_DATE, WORK_DATE.atTime(18, 0));

        ArgumentCaptor<Attendance> captor = ArgumentCaptor.forClass(Attendance.class);
        verify(attendanceRepository).saveAndFlush(captor.capture());
        assertEquals(AttendanceStatus.ABSENT, captor.getValue().getStatus());
        assertEquals(LocalTime.of(8, 0), captor.getValue().getScheduledStartTime());
        assertNull(captor.getValue().getCheckInTime());
    }

    @Test
    void runningShiftIsNotMarkedAbsent() {
        when(shiftAssignmentRepository.findPublishedAssignedOnDate(eq(WORK_DATE), eq(AssignmentStatus.ASSIGNED)))
                .thenReturn(List.of(morningAssignment));

        attendanceService.markAbsences(WORK_DATE, WORK_DATE.atTime(10, 0));

        verify(attendanceRepository, never()).saveAndFlush(any());
    }

    private void stubShift(List<ShiftAssignment> assignments) {
        when(employeeRepository.findByIdWithStore(20)).thenReturn(Optional.of(staffEmployee));
        when(shiftAssignmentRepository.findAssignmentsForAttendance(
                eq(20), eq(WORK_DATE.minusDays(1)), eq(WORK_DATE.plusDays(1)), eq(AssignmentStatus.ASSIGNED)))
                .thenReturn(assignments);
    }

    private Attendance openAttendance(int lateMinutes) {
        return Attendance.builder()
                .id(101)
                .employee(staffEmployee)
                .store(store)
                .shift(morning)
                .workDate(WORK_DATE)
                .scheduledShiftName("Ca sáng")
                .scheduledStartTime(LocalTime.of(8, 0))
                .scheduledEndTime(LocalTime.of(17, 0))
                .checkInTime(WORK_DATE.atTime(8, 0))
                .lateMinutes(lateMinutes)
                .earlyLeaveMinutes(0)
                .status(lateMinutes > 0 ? AttendanceStatus.LATE : AttendanceStatus.PRESENT)
                .build();
    }

    private User buildUser(Integer id, String username, RoleName roleName, Employee employee) {
        Role role = Role.builder().id(1).roleName(roleName).build();
        return User.builder()
                .id(id)
                .username(username)
                .passwordHash("hash")
                .role(role)
                .employee(employee)
                .isActive(true)
                .build();
    }
}

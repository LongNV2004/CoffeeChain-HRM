package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.AssignmentStatus;
import com.example.coffee_hrm.common.enums.AttendanceStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.response.AttendanceHistoryView;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceHistoryServiceImplTest {

    @Mock
    private AttendanceRepository attendanceRepository;
    @Mock
    private ShiftAssignmentRepository shiftAssignmentRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private StoreRepository storeRepository;

    @InjectMocks
    private AttendanceHistoryServiceImpl attendanceHistoryService;

    private Store store;
    private Store otherStore;
    private Employee managerEmployee;
    private Employee staffEmployee;
    private AuthenticatedUser managerUser;
    private AuthenticatedUser staffUser;

    @BeforeEach
    void setUp() {
        store = Store.builder().id(1).storeName("Cửa hàng 1").build();
        otherStore = Store.builder().id(2).storeName("Cửa hàng 2").build();
        managerEmployee = Employee.builder()
                .id(10).fullName("Manager A").store(store).status(EmployeeStatus.ACTIVE).build();
        staffEmployee = Employee.builder()
                .id(20).fullName("Trần Văn Tuấn").store(store).status(EmployeeStatus.ACTIVE).build();
        store.setManager(managerEmployee);
        managerUser = AuthenticatedUser.from(buildUser(2, "manager1", RoleName.MANAGER, managerEmployee));
        staffUser = AuthenticatedUser.from(buildUser(3, "tuanth", RoleName.STAFF, staffEmployee));
    }

    @Test
    void staffSeesOnlyOwnAttendance() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 2);
        Attendance ownRecord = Attendance.builder()
                .id(5)
                .employee(staffEmployee)
                .workDate(from)
                .checkInTime(LocalDateTime.of(2026, 9, 1, 8, 5))
                .checkOutTime(LocalDateTime.of(2026, 9, 1, 17, 0))
                .totalHours(new BigDecimal("8.50"))
                .status(AttendanceStatus.LATE)
                .build();
        Shift shift = Shift.builder()
                .id(1).shiftName("Ca sáng").store(store)
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(12, 0))
                .build();
        ShiftAssignment assignment = ShiftAssignment.builder()
                .id(9).employee(staffEmployee).shift(shift).workDate(from)
                .status(AssignmentStatus.ASSIGNED)
                .build();

        when(employeeRepository.findByIdWithStore(20)).thenReturn(Optional.of(staffEmployee));
        when(attendanceRepository.findHistoryByEmployee(20, from, to)).thenReturn(List.of(ownRecord));
        when(shiftAssignmentRepository.findWeeklyAssignmentsForEmployee(20, from, to, AssignmentStatus.ASSIGNED))
                .thenReturn(List.of(assignment));

        AttendanceHistoryView view = attendanceHistoryService.getStaffHistory(staffUser, from, to);

        assertEquals(20, view.getEmployeeId());
        assertEquals(1, view.getRecordCount());
        assertEquals("8.5 giờ", view.getTotalHoursLabel());
        assertEquals("Đi muộn", view.getRows().getFirst().getStatusLabel());
        assertEquals("Ca sáng (08:00–12:00)", view.getRows().getFirst().getShiftLabel());
        assertEquals("08:05", view.getRows().getFirst().getCheckInLabel());
        verify(attendanceRepository).findHistoryByEmployee(eq(20), eq(from), eq(to));
        verify(attendanceRepository, never()).findHistoryByStore(any(), any(), any());
    }

    @Test
    void staffWithoutEmployeeProfileIsRejected() {
        AuthenticatedUser orphan = AuthenticatedUser.from(buildUser(9, "orphan", RoleName.STAFF, null));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> attendanceHistoryService.getStaffHistory(orphan, null, null));

        assertTrue(ex.getMessage().contains("hồ sơ nhân viên"));
        verify(attendanceRepository, never()).findHistoryByEmployee(any(), any(), any());
    }

    @Test
    void managerSeesOnlyStoreEmployeesAndCanFilter() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);
        Attendance record = Attendance.builder()
                .id(6)
                .employee(staffEmployee)
                .workDate(from)
                .checkInTime(LocalDateTime.of(2026, 9, 1, 7, 0))
                .totalHours(new BigDecimal("4.00"))
                .status(AttendanceStatus.ON_TIME)
                .build();

        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(employeeRepository.findByIdWithStore(20)).thenReturn(Optional.of(staffEmployee));
        when(employeeRepository.findByStore_IdAndStatus(eq(1), any())).thenReturn(List.of(staffEmployee));
        when(attendanceRepository.findHistoryByStoreAndEmployee(1, 20, from, to)).thenReturn(List.of(record));
        when(shiftAssignmentRepository.findWeeklyAssignmentsForStore(1, from, to, AssignmentStatus.ASSIGNED))
                .thenReturn(List.of());

        AttendanceHistoryView view = attendanceHistoryService.getManagerHistory(managerUser, 20, from, to);

        assertEquals(1, view.getRecordCount());
        assertEquals(20, view.getFilterEmployeeId());
        assertEquals("Đúng giờ", view.getRows().getFirst().getStatusLabel());
        assertEquals("—", view.getRows().getFirst().getCheckOutLabel());
        assertEquals("Chưa phân ca", view.getRows().getFirst().getShiftLabel());
        assertTrue(view.getEmployees().stream().anyMatch(option -> option.getEmployeeId().equals(20)));
        verify(attendanceRepository, never()).findHistoryByStore(any(), any(), any());
    }

    @Test
    void managerCannotViewEmployeeFromAnotherStore() {
        Employee outsider = Employee.builder()
                .id(99).fullName("Người khác").store(otherStore).status(EmployeeStatus.ACTIVE).build();
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(employeeRepository.findByIdWithStore(99)).thenReturn(Optional.of(outsider));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> attendanceHistoryService.getManagerHistory(
                        managerUser, 99, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2)));

        assertTrue(ex.getMessage().contains("không thuộc cửa hàng"));
        verify(attendanceRepository, never()).findHistoryByStoreAndEmployee(any(), any(), any(), any());
        verify(attendanceRepository, never()).findHistoryByStore(any(), any(), any());
    }

    @Test
    void managerWithoutAssignedStoreIsRejected() {
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class,
                () -> attendanceHistoryService.getManagerHistory(managerUser, null, null, null));

        verify(attendanceRepository, never()).findHistoryByStore(any(), any(), any());
    }

    @Test
    void invalidDateRangeIsRejected() {
        when(employeeRepository.findByIdWithStore(20)).thenReturn(Optional.of(staffEmployee));

        assertThrows(BusinessException.class, () -> attendanceHistoryService.getStaffHistory(
                staffUser, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 1)));

        verify(attendanceRepository, never()).findHistoryByEmployee(any(), any(), any());
    }

    private User buildUser(Integer id, String username, RoleName roleName, Employee employee) {
        Role role = Role.builder()
                .id(roleName == RoleName.ADMIN ? 1 : (roleName == RoleName.MANAGER ? 2 : 3))
                .roleName(roleName)
                .build();
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

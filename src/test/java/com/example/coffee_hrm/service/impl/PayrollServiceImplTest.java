package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.AssignmentStatus;
import com.example.coffee_hrm.common.enums.AttendanceStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.PayrollStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.response.PayrollBoardView;
import com.example.coffee_hrm.entity.Attendance;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.EmploymentContract;
import com.example.coffee_hrm.entity.MonthlyPayroll;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.Shift;
import com.example.coffee_hrm.entity.ShiftAssignment;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.AttendanceRepository;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.EmploymentContractRepository;
import com.example.coffee_hrm.repository.MonthlyPayrollRepository;
import com.example.coffee_hrm.repository.ShiftAssignmentRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.repository.UserRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayrollServiceImplTest {

    @Mock
    private StoreRepository storeRepository;
    @Mock
    private ShiftAssignmentRepository shiftAssignmentRepository;
    @Mock
    private AttendanceRepository attendanceRepository;
    @Mock
    private EmploymentContractRepository employmentContractRepository;
    @Mock
    private MonthlyPayrollRepository payrollRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private PayrollServiceImpl service;

    private Store store;
    private Employee staffEmployee;
    private AuthenticatedUser manager;
    private AuthenticatedUser staff;
    private MonthlyPayroll saved;

    @BeforeEach
    void setUp() {
        Employee managerEmployee = Employee.builder()
                .id(10).fullName("Quản lý").status(EmployeeStatus.ACTIVE).build();
        store = Store.builder().id(1).storeName("The Coffee House - Cầu Giấy").manager(managerEmployee).build();
        managerEmployee.setStore(store);
        staffEmployee = Employee.builder().id(3).fullName("Lê Thị Thu").status(EmployeeStatus.ACTIVE).store(store).build();
        Role managerRole = Role.builder().id(2).roleName(RoleName.MANAGER).build();
        Role staffRole = Role.builder().id(3).roleName(RoleName.STAFF).build();
        manager = AuthenticatedUser.from(User.builder()
                .id(2).username("manager1").passwordHash("hash").role(managerRole)
                .employee(managerEmployee).isActive(true).build());
        staff = AuthenticatedUser.from(User.builder()
                .id(3).username("staff2").passwordHash("hash").role(staffRole)
                .employee(staffEmployee).isActive(true).build());
    }

    @Test
    void calculatingTwiceReplacesTheDraftInsteadOfAddingAnotherLine() {
        rememberSavedPayroll();
        stubOctoberShift(Attendance.builder()
                .id(30).employee(staffEmployee).shift(morning()).store(store).workDate(LocalDate.of(2026, 10, 6))
                .checkInTime(LocalDate.of(2026, 10, 6).atTime(8, 0))
                .checkOutTime(LocalDate.of(2026, 10, 6).atTime(16, 0))
                .lateMinutes(0).earlyLeaveMinutes(0).status(AttendanceStatus.ON_TIME).build(), new BigDecimal("30000"));

        service.calculate(manager, 2026, 10);
        service.calculate(manager, 2026, 10);

        assertEquals(PayrollStatus.DA_TINH, saved.getStatus());
        assertEquals(1, saved.getEmployeeLines().size());
        assertEquals(8, saved.getEmployeeLines().getFirst().getPayableHours());
        assertEquals(0, new BigDecimal("240000").compareTo(saved.getEmployeeLines().getFirst().getExpectedSalary()));
    }

    @Test
    void hourlyRateFollowsTheContractOfEachMonth() {
        rememberSavedPayroll();
        when(employeeRepository.getReferenceById(3)).thenReturn(staffEmployee);
        when(employmentContractRepository.findByStoreId(1)).thenReturn(java.util.List.of(
                contract(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), new BigDecimal("30000")),
                contract(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), new BigDecimal("40000"))));
        when(shiftAssignmentRepository.findPublishedAssignedForStoreBetween(
                eq(1), eq(LocalDate.of(2026, 10, 1)), eq(LocalDate.of(2026, 10, 31)), eq(AssignmentStatus.ASSIGNED)))
                .thenReturn(java.util.List.of(assignment(LocalDate.of(2026, 10, 6))));
        when(shiftAssignmentRepository.findPublishedAssignedForStoreBetween(
                eq(1), eq(LocalDate.of(2026, 11, 1)), eq(LocalDate.of(2026, 11, 30)), eq(AssignmentStatus.ASSIGNED)))
                .thenReturn(java.util.List.of(assignment(LocalDate.of(2026, 11, 3))));
        when(attendanceRepository.findForMonthlyReport(eq(1), eq(LocalDate.of(2026, 10, 1)), eq(LocalDate.of(2026, 10, 31))))
                .thenReturn(java.util.List.of(worked(LocalDate.of(2026, 10, 6))));
        when(attendanceRepository.findForMonthlyReport(eq(1), eq(LocalDate.of(2026, 11, 1)), eq(LocalDate.of(2026, 11, 30))))
                .thenReturn(java.util.List.of(worked(LocalDate.of(2026, 11, 3))));

        service.calculate(manager, 2026, 10);
        BigDecimal october = saved.getEmployeeLines().getFirst().getHourlyRate();
        saved = null;
        service.calculate(manager, 2026, 11);
        BigDecimal november = saved.getEmployeeLines().getFirst().getHourlyRate();

        assertEquals(0, new BigDecimal("30000").compareTo(october));
        assertEquals(0, new BigDecimal("40000").compareTo(november));
    }

    @Test
    void lockedPayrollCannotBeCalculatedAgain() {
        MonthlyPayroll locked = MonthlyPayroll.builder()
                .store(store).payrollYear(2026).payrollMonth(10).status(PayrollStatus.DA_CHOT)
                .employeeLines(new java.util.ArrayList<>()).build();
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(payrollRepository.findPeriod(1, 2026, 10)).thenReturn(Optional.of(locked));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.calculate(manager, 2026, 10));
        assertEquals("Bảng lương đã chốt, không tính lại.", ex.getMessage());
        verify(payrollRepository, never()).save(any());
    }

    @Test
    void lockRejectsAPeriodThatStillHasAMissingPunch() {
        rememberSavedPayroll();
        stubOctoberShift(Attendance.builder()
                .id(31).employee(staffEmployee).shift(morning()).store(store).workDate(LocalDate.of(2026, 10, 6))
                .checkInTime(LocalDate.of(2026, 10, 6).atTime(8, 0))
                .lateMinutes(0).earlyLeaveMinutes(0).status(AttendanceStatus.LATE).build(), new BigDecimal("30000"));
        service.calculate(manager, 2026, 10);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.lock(manager, 2026, 10));
        assertEquals("Còn 1 ca thiếu dữ liệu, chưa thể chốt lương.", ex.getMessage());
        assertEquals(PayrollStatus.DA_TINH, saved.getStatus());
    }

    @Test
    void staffCannotOpenAnotherEmployeesPayroll() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.detail(staff, 2026, 10, 4));
        assertEquals("Bạn chỉ xem được bảng lương của chính mình.", ex.getMessage());
        verify(employeeRepository, never()).findByIdWithStore(any());
    }

    @Test
    void managerCannotOpenAnotherStore() {
        Store other = Store.builder().id(2).storeName("Cửa hàng khác").build();
        Employee outsider = Employee.builder().id(4).fullName("Người khác").store(other).build();
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(employeeRepository.findByIdWithStore(4)).thenReturn(Optional.of(outsider));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.detail(manager, 2026, 10, 4));
        assertEquals("Nhân viên không thuộc cửa hàng bạn quản lý.", ex.getMessage());
    }

    @Test
    void managerBoardIgnoresAStoreIdFromTheBrowser() {
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));

        PayrollBoardView board = service.board(manager, 2026, 10, null, 99, null, null);

        assertEquals(0, board.getEmployeeCount());
        verify(payrollRepository).findPeriod(1, 2026, 10);
        verify(shiftAssignmentRepository).findPublishedAssignedForStoreBetween(
                eq(1), eq(LocalDate.of(2026, 10, 1)), eq(LocalDate.of(2026, 10, 31)), eq(AssignmentStatus.ASSIGNED));
    }

    @Test
    void employeeWithoutAPublishedShiftIsNotTreatedAsUnpaidLeave() {
        when(employeeRepository.findByIdWithStore(3)).thenReturn(Optional.of(staffEmployee));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.detail(staff, 2026, 10, 3));
        assertEquals("Nhân viên không có lịch phân ca trong kỳ này.", ex.getMessage());
    }

    @Test
    void adminCannotCalculate() {
        Role adminRole = Role.builder().id(1).roleName(RoleName.ADMIN).build();
        AuthenticatedUser admin = AuthenticatedUser.from(User.builder()
                .id(1).username("admin").passwordHash("hash").role(adminRole).isActive(true).build());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.calculate(admin, 2026, 10));
        assertEquals("Chỉ quản lý cửa hàng mới được tính lương.", ex.getMessage());
        verify(storeRepository, never()).findByManager_Id(anyInt());
    }

    private void rememberSavedPayroll() {
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(payrollRepository.findPeriod(anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> Optional.ofNullable(saved));
        when(payrollRepository.save(any(MonthlyPayroll.class))).thenAnswer(invocation -> {
            saved = invocation.getArgument(0);
            return saved;
        });
    }

    private void stubOctoberShift(Attendance attendance, BigDecimal salary) {
        when(employeeRepository.getReferenceById(3)).thenReturn(staffEmployee);
        when(shiftAssignmentRepository.findPublishedAssignedForStoreBetween(
                eq(1), eq(LocalDate.of(2026, 10, 1)), eq(LocalDate.of(2026, 10, 31)), eq(AssignmentStatus.ASSIGNED)))
                .thenReturn(java.util.List.of(assignment(LocalDate.of(2026, 10, 6))));
        when(attendanceRepository.findForMonthlyReport(1, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)))
                .thenReturn(java.util.List.of(attendance));
        when(employmentContractRepository.findByStoreId(1)).thenReturn(java.util.List.of(
                contract(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31), salary)));
    }

    private Shift morning() {
        return Shift.builder().id(1).shiftName("Ca sáng").store(store)
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(16, 0)).build();
    }

    private ShiftAssignment assignment(LocalDate day) {
        return ShiftAssignment.builder()
                .id(11).employee(staffEmployee).shift(morning()).workDate(day)
                .status(AssignmentStatus.ASSIGNED).isPublished(true).build();
    }

    private Attendance worked(LocalDate day) {
        return Attendance.builder()
                .id(40).employee(staffEmployee).shift(morning()).store(store).workDate(day)
                .checkInTime(day.atTime(8, 0)).checkOutTime(day.atTime(16, 0))
                .lateMinutes(0).earlyLeaveMinutes(0).status(AttendanceStatus.ON_TIME).build();
    }

    private EmploymentContract contract(LocalDate start, LocalDate end, BigDecimal salary) {
        return EmploymentContract.builder()
                .id(7).employee(staffEmployee).startDate(start).endDate(end).salary(salary).build();
    }
}

package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.ApprovalStatus;
import com.example.coffee_hrm.common.enums.AssignmentStatus;
import com.example.coffee_hrm.common.enums.CertificationStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.EmploymentContract;
import com.example.coffee_hrm.entity.OvertimeRequest;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.Shift;
import com.example.coffee_hrm.entity.ShiftAssignment;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.User;
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
import com.example.coffee_hrm.service.support.ApprovedLeaveCoverage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OvertimeServiceImplTest {

    @Mock private OvertimeRequestRepository overtimeRequestRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private StoreRepository storeRepository;
    @Mock private ShiftRepository shiftRepository;
    @Mock private ShiftAssignmentRepository shiftAssignmentRepository;
    @Mock private WorkAvailabilityRepository workAvailabilityRepository;
    @Mock private EmploymentContractRepository employmentContractRepository;
    @Mock private ApprovedLeaveCoverage approvedLeaveCoverage;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private OvertimeServiceImpl service;

    private Store store;
    private Employee staffEmployee;
    private AuthenticatedUser staff;
    private AuthenticatedUser manager;
    private LocalDate day;
    private Shift morning;
    private Shift afternoon;
    private Shift evening;
    private Shift lateEvening;

    @BeforeEach
    void setUp() {
        day = VietnamTime.today().plusDays(3);
        Employee managerEmployee = Employee.builder().id(10).fullName("Quản lý").status(EmployeeStatus.ACTIVE).build();
        store = Store.builder().id(1).storeName("Cầu Giấy").manager(managerEmployee).build();
        managerEmployee.setStore(store);
        staffEmployee = Employee.builder().id(3).fullName("Lê Thị Thu").status(EmployeeStatus.ACTIVE)
                .certificationStatus(CertificationStatus.CERTIFIED).store(store).build();
        staff = AuthenticatedUser.from(User.builder().id(3).username("staff").passwordHash("x")
                .role(Role.builder().id(3).roleName(RoleName.STAFF).build())
                .employee(staffEmployee).isActive(true).build());
        manager = AuthenticatedUser.from(User.builder().id(2).username("manager").passwordHash("x")
                .role(Role.builder().id(2).roleName(RoleName.MANAGER).build())
                .employee(managerEmployee).isActive(true).build());
        morning = shift(1, "Ca sáng", 7, 12);
        afternoon = shift(2, "Ca chiều", 12, 17);
        evening = shift(3, "Ca tối", 17, 22);
        lateEvening = shift(4, "Ca khuya", 18, 23);
        lenient().when(employeeRepository.findByIdWithStore(3)).thenReturn(Optional.of(staffEmployee));
        lenient().when(employeeRepository.lockByIdForLeaveRequest(3)).thenReturn(Optional.of(staffEmployee));
        lenient().when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        lenient().when(employmentContractRepository.findByEmployeeId(3)).thenReturn(List.of(EmploymentContract.builder()
                .id(5).employee(staffEmployee).startDate(day.minusMonths(1)).endDate(day.plusMonths(6)).build()));
        lenient().when(overtimeRequestRepository.findActiveOnDate(eq(3), eq(day), any())).thenReturn(List.of());
        lenient().when(workAvailabilityRepository.findApprovedCovering(anyInt(), any(), any(), any())).thenReturn(List.of());
    }

    @Test
    void staffWithTwoShiftsCanRequestAThirdShiftAsOvertime() {
        when(shiftRepository.findByIdAndStore_Id(3, 1)).thenReturn(Optional.of(evening));
        when(shiftAssignmentRepository.findByEmployee_IdAndWorkDateAndStatus(3, day, AssignmentStatus.ASSIGNED))
                .thenReturn(List.of(assigned(morning), assigned(afternoon)));
        when(overtimeRequestRepository.save(any(OvertimeRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.submit(staff, 3, day, "Cửa hàng đông khách");

        ArgumentCaptor<OvertimeRequest> captor = ArgumentCaptor.forClass(OvertimeRequest.class);
        verify(overtimeRequestRepository).save(captor.capture());
        assertEquals(ApprovalStatus.PENDING, captor.getValue().getStatus());
        assertSame(evening, captor.getValue().getShift());
    }

    @Test
    void overtimeCannotExceedThreeShiftsPerDay() {
        when(shiftRepository.findByIdAndStore_Id(4, 1)).thenReturn(Optional.of(shift(4, "Ca đêm", 22, 23)));
        when(shiftAssignmentRepository.findByEmployee_IdAndWorkDateAndStatus(3, day, AssignmentStatus.ASSIGNED))
                .thenReturn(List.of(assigned(morning), assigned(afternoon), assigned(evening)));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.submit(staff, 4, day, "Làm thêm"));
        assertTrue(ex.getMessage().startsWith("Mỗi ngày chỉ được làm tối đa 3 ca khi tăng ca."), ex.getMessage());
        verify(overtimeRequestRepository, never()).save(any());
    }

    @Test
    void pendingOvertimeOfTheSameDayCountsTowardTheLimit() {
        when(shiftRepository.findByIdAndStore_Id(3, 1)).thenReturn(Optional.of(evening));
        when(shiftAssignmentRepository.findByEmployee_IdAndWorkDateAndStatus(3, day, AssignmentStatus.ASSIGNED))
                .thenReturn(List.of(assigned(morning), assigned(afternoon)));
        OvertimeRequest pending = OvertimeRequest.builder().id(8).shift(shift(5, "Ca đêm", 22, 23))
                .workDate(day).status(ApprovalStatus.PENDING).build();
        when(overtimeRequestRepository.findActiveOnDate(eq(3), eq(day), any())).thenReturn(List.of(pending));

        assertThrows(BusinessException.class, () -> service.submit(staff, 3, day, "Làm thêm"));
    }

    @Test
    void overlappingShiftIsRejected() {
        when(shiftRepository.findByIdAndStore_Id(4, 1)).thenReturn(Optional.of(lateEvening));
        when(shiftAssignmentRepository.findByEmployee_IdAndWorkDateAndStatus(3, day, AssignmentStatus.ASSIGNED))
                .thenReturn(List.of(assigned(evening)));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.submit(staff, 4, day, "Làm thêm"));
        assertTrue(ex.getMessage().startsWith("Ca tăng ca trùng giờ"), ex.getMessage());
    }

    @Test
    void approvingCreatesAPublishedAssignment() {
        OvertimeRequest request = OvertimeRequest.builder().id(9).employee(staffEmployee).store(store)
                .shift(evening).workDate(day).reason("x").status(ApprovalStatus.PENDING).build();
        when(overtimeRequestRepository.lockById(9)).thenReturn(Optional.of(request));
        when(shiftAssignmentRepository.findByEmployee_IdAndWorkDateAndStatus(3, day, AssignmentStatus.ASSIGNED))
                .thenReturn(List.of(assigned(morning), assigned(afternoon)));
        when(shiftAssignmentRepository.save(any(ShiftAssignment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.approve(manager, 9);

        assertEquals(ApprovalStatus.APPROVED, request.getStatus());
        assertEquals(AssignmentStatus.ASSIGNED, request.getAssignment().getStatus());
        assertTrue(request.getAssignment().getIsPublished());
        assertSame(evening, request.getAssignment().getShift());
    }

    @Test
    void rejectNeedsAReason() {
        assertThrows(BusinessException.class, () -> service.reject(manager, 9, "  "));
        verify(overtimeRequestRepository, never()).save(any());
    }

    private Shift shift(int id, String name, int from, int to) {
        return Shift.builder().id(id).shiftName(name).store(store)
                .startTime(LocalTime.of(from, 0)).endTime(LocalTime.of(to, 0)).build();
    }

    private ShiftAssignment assigned(Shift shift) {
        return ShiftAssignment.builder().id(100 + shift.getId()).employee(staffEmployee).shift(shift)
                .workDate(day).status(AssignmentStatus.ASSIGNED).isPublished(true).build();
    }
}

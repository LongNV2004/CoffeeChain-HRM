package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.ApprovalStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.request.AssignShiftRequest;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.Shift;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.entity.WorkAvailability;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.ShiftRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.repository.UserRepository;
import com.example.coffee_hrm.repository.WorkAvailabilityRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.NotificationService;
import com.example.coffee_hrm.service.ScheduleService;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffAvailabilityServiceImplTest {

    @Mock
    private WorkAvailabilityRepository workAvailabilityRepository;
    @Mock
    private ShiftRepository shiftRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private ScheduleService scheduleService;

    @InjectMocks
    private StaffAvailabilityServiceImpl service;

    private AuthenticatedUser managerUser;
    private Store store;
    private Employee staff;
    private Shift shift;
    private LocalDate workDate;

    @BeforeEach
    void setUp() {
        store = Store.builder().id(1).storeName("Store 1").build();
        Employee manager = Employee.builder().id(10).fullName("Manager A").store(store).status(EmployeeStatus.ACTIVE).build();
        staff = Employee.builder().id(20).fullName("Trần Văn Tuấn").store(store).status(EmployeeStatus.ACTIVE).build();
        managerUser = AuthenticatedUser.from(buildUser(2, "manager1", RoleName.MANAGER, manager));
        shift = Shift.builder()
                .id(1)
                .shiftName("Ca Sáng")
                .store(store)
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(12, 0))
                .build();
        workDate = LocalDate.of(2026, 10, 5);
    }

    @Test
    void approveCreatesAssignmentAndMarksApproved() {
        WorkAvailability availability = pendingAvailability();
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(workAvailabilityRepository.findByIdWithDetails(5)).thenReturn(Optional.of(availability));

        service.reviewAvailability(5, true, managerUser);

        ArgumentCaptor<AssignShiftRequest> captor = ArgumentCaptor.forClass(AssignShiftRequest.class);
        verify(scheduleService).assignShiftFromApprovedAvailability(captor.capture(), org.mockito.ArgumentMatchers.eq(managerUser));
        AssignShiftRequest request = captor.getValue();
        assertEquals(20, request.getEmployeeId());
        assertEquals(1, request.getShiftId());
        assertEquals(workDate, request.getWorkDate());
        assertEquals(ApprovalStatus.APPROVED, availability.getStatus());
        verify(workAvailabilityRepository).save(availability);
    }

    @Test
    void approveAgainDoesNotSaveStatusWhenAlreadyApproved() {
        WorkAvailability availability = pendingAvailability();
        availability.setStatus(ApprovalStatus.APPROVED);
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(workAvailabilityRepository.findByIdWithDetails(5)).thenReturn(Optional.of(availability));

        service.reviewAvailability(5, true, managerUser);

        verify(scheduleService).assignShiftFromApprovedAvailability(any(), org.mockito.ArgumentMatchers.eq(managerUser));
        verify(workAvailabilityRepository, never()).save(any());
        assertEquals(ApprovalStatus.APPROVED, availability.getStatus());
    }

    @Test
    void approveKeepsPendingWhenScheduleValidationFails() {
        WorkAvailability availability = pendingAvailability();
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(workAvailabilityRepository.findByIdWithDetails(5)).thenReturn(Optional.of(availability));
        doThrow(new BusinessException("Nhân viên đã có ca trùng giờ."))
                .when(scheduleService).assignShiftFromApprovedAvailability(any(), any());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.reviewAvailability(5, true, managerUser));

        assertEquals("Nhân viên đã có ca trùng giờ.", ex.getMessage());
        assertEquals(ApprovalStatus.PENDING, availability.getStatus());
        verify(workAvailabilityRepository, never()).save(any());
    }

    @Test
    void rejectDoesNotCreateAssignment() {
        WorkAvailability availability = pendingAvailability();
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(workAvailabilityRepository.findByIdWithDetails(5)).thenReturn(Optional.of(availability));

        service.reviewAvailability(5, false, managerUser);

        assertEquals(ApprovalStatus.REJECTED, availability.getStatus());
        verify(workAvailabilityRepository).save(availability);
        verify(scheduleService, never()).assignShiftFromApprovedAvailability(any(), any());
        verify(scheduleService, never()).assignShift(any(), any());
    }

    @Test
    void approveRejectedAvailabilityDoesNotCreateAssignment() {
        WorkAvailability availability = pendingAvailability();
        availability.setStatus(ApprovalStatus.REJECTED);
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(workAvailabilityRepository.findByIdWithDetails(5)).thenReturn(Optional.of(availability));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.reviewAvailability(5, true, managerUser));

        assertEquals(ApprovalStatus.REJECTED, availability.getStatus());
        verify(scheduleService, never()).assignShiftFromApprovedAvailability(any(), any());
        verify(workAvailabilityRepository, never()).save(any());
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("từ chối"));
    }

    @Test
    void reviewAllApprovesEveryPendingSlot() {
        WorkAvailability first = pendingAvailability();
        WorkAvailability second = pendingAvailability();
        second.setId(6);
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(workAvailabilityRepository.findByStoreAndDateRange(eq(1), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(first, second));
        when(workAvailabilityRepository.findByIdWithDetails(5)).thenReturn(Optional.of(first));
        when(workAvailabilityRepository.findByIdWithDetails(6)).thenReturn(Optional.of(second));

        String message = service.reviewAllNextWeekAvailabilities(true, managerUser);

        verify(scheduleService, times(2)).assignShiftFromApprovedAvailability(any(), eq(managerUser));
        assertEquals(ApprovalStatus.APPROVED, first.getStatus());
        assertEquals(ApprovalStatus.APPROVED, second.getStatus());
        assertTrue(message.contains("2"));
    }

    @Test
    void reviewAllRejectsOnlyPendingSlots() {
        WorkAvailability pending = pendingAvailability();
        WorkAvailability alreadyApproved = pendingAvailability();
        alreadyApproved.setId(6);
        alreadyApproved.setStatus(ApprovalStatus.APPROVED);
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(workAvailabilityRepository.findByStoreAndDateRange(eq(1), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(pending, alreadyApproved));

        String message = service.reviewAllNextWeekAvailabilities(false, managerUser);

        assertEquals(ApprovalStatus.REJECTED, pending.getStatus());
        assertEquals(ApprovalStatus.APPROVED, alreadyApproved.getStatus());
        assertTrue(message.contains("1"));
        verify(workAvailabilityRepository).saveAll(List.of(pending));
        verify(scheduleService, never()).assignShiftFromApprovedAvailability(any(), any());
    }

    @Test
    void reviewAllKeepsFailedSlotPending() {
        WorkAvailability first = pendingAvailability();
        WorkAvailability second = pendingAvailability();
        second.setId(6);
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(workAvailabilityRepository.findByStoreAndDateRange(eq(1), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(first, second));
        when(workAvailabilityRepository.findByIdWithDetails(5)).thenReturn(Optional.of(first));
        when(workAvailabilityRepository.findByIdWithDetails(6)).thenReturn(Optional.of(second));
        org.mockito.Mockito.doNothing()
                .doThrow(new BusinessException("Nhân viên đã có ca trùng giờ."))
                .when(scheduleService).assignShiftFromApprovedAvailability(any(), any());

        String message = service.reviewAllNextWeekAvailabilities(true, managerUser);

        assertEquals(ApprovalStatus.APPROVED, first.getStatus());
        assertEquals(ApprovalStatus.PENDING, second.getStatus());
        assertTrue(message.contains("trùng giờ"));
    }

    @Test
    void reviewAllThrowsWhenNothingPending() {
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(workAvailabilityRepository.findByStoreAndDateRange(eq(1), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.reviewAllNextWeekAvailabilities(true, managerUser));

        assertTrue(ex.getMessage().contains("chờ duyệt"));
        verify(scheduleService, never()).assignShiftFromApprovedAvailability(any(), any());
    }

    private WorkAvailability pendingAvailability() {
        return WorkAvailability.builder()
                .id(5)
                .employee(staff)
                .shift(shift)
                .workDate(workDate)
                .status(ApprovalStatus.PENDING)
                .build();
    }

    private User buildUser(Integer id, String username, RoleName roleName, Employee employee) {
        Role role = Role.builder().id(2).roleName(roleName).build();
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

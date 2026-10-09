package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.ApprovalStatus;
import com.example.coffee_hrm.common.enums.CertificationStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.NotificationType;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.request.AssignShiftRequest;
import com.example.coffee_hrm.dto.request.SubmitWorkAvailabilityRequest;
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
import com.example.coffee_hrm.service.support.AvailabilityCoverage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
        staff = Employee.builder()
                .id(20)
                .fullName("Trần Văn Tuấn")
                .store(store)
                .status(EmployeeStatus.ACTIVE)
                .certificationStatus(CertificationStatus.CERTIFIED)
                .build();
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
        when(workAvailabilityRepository.findVisibleByStore(eq(1), any(LocalDate.class), eq(ApprovalStatus.PENDING)))
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
        when(workAvailabilityRepository.findVisibleByStore(eq(1), any(LocalDate.class), eq(ApprovalStatus.PENDING)))
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
        when(workAvailabilityRepository.findVisibleByStore(eq(1), any(LocalDate.class), eq(ApprovalStatus.PENDING)))
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
    void approveRejectsStaffWithoutCertificate() {
        staff.setCertificationStatus(CertificationStatus.NOTCERTIFIED);
        WorkAvailability availability = pendingAvailability();
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(workAvailabilityRepository.findByIdWithDetails(5)).thenReturn(Optional.of(availability));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.reviewAvailability(5, true, managerUser));

        assertEquals(StaffAvailabilityServiceImpl.NOT_CERTIFIED_APPROVAL_MESSAGE, ex.getMessage());
        assertEquals(ApprovalStatus.PENDING, availability.getStatus());
        verify(scheduleService, never()).assignShiftFromApprovedAvailability(any(), any());
        verify(workAvailabilityRepository, never()).save(any());
    }

    @Test
    void submitRejectsStaffWithoutCertificate() {
        staff.setCertificationStatus(CertificationStatus.NOTCERTIFIED);
        AuthenticatedUser staffUser = AuthenticatedUser.from(buildUser(3, "staff1", RoleName.STAFF, staff));
        when(employeeRepository.findByIdWithStoreAndManager(20)).thenReturn(Optional.of(staff));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.submitNextWeekAvailability(nextWeekRequest(), staffUser));

        assertEquals(StaffAvailabilityServiceImpl.NOT_CERTIFIED_MESSAGE, ex.getMessage());
        verify(workAvailabilityRepository, never()).deleteByEmployeeAndDateRange(any(), any(), any());
        verify(workAvailabilityRepository, never()).saveAll(any());
    }

    @Test
    void submitRejectsWhenCertificationStatusIsMissing() {
        staff.setCertificationStatus(null);
        AuthenticatedUser staffUser = AuthenticatedUser.from(buildUser(3, "staff1", RoleName.STAFF, staff));
        when(employeeRepository.findByIdWithStoreAndManager(20)).thenReturn(Optional.of(staff));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.submitNextWeekAvailability(nextWeekRequest(), staffUser));

        assertEquals(StaffAvailabilityServiceImpl.NOT_CERTIFIED_MESSAGE, ex.getMessage());
        verify(workAvailabilityRepository, never()).saveAll(any());
    }

    @Test
    void submitAllowsCertifiedStaff() {
        AuthenticatedUser staffUser = AuthenticatedUser.from(buildUser(3, "staff1", RoleName.STAFF, staff));
        when(employeeRepository.findByIdWithStoreAndManager(20)).thenReturn(Optional.of(staff));
        when(shiftRepository.findByIdAndStore_Id(1, 1)).thenReturn(Optional.of(shift));

        int count = service.submitNextWeekAvailability(nextWeekRequest(), staffUser);

        assertEquals(1, count);
        verify(workAvailabilityRepository, never()).deleteByEmployeeAndDateRange(any(), any(), any());
        verify(workAvailabilityRepository, never()).deleteByIds(any());
        verify(workAvailabilityRepository).saveAll(any());
    }

    @Test
    void registrationBlockedMessageExplainsMissingCertificate() {
        staff.setCertificationStatus(CertificationStatus.NOTCERTIFIED);
        AuthenticatedUser staffUser = AuthenticatedUser.from(buildUser(3, "staff1", RoleName.STAFF, staff));
        when(employeeRepository.findByIdWithStore(20)).thenReturn(Optional.of(staff));

        assertEquals(StaffAvailabilityServiceImpl.NOT_CERTIFIED_MESSAGE, service.registrationBlockedMessage(staffUser));
    }

    @Test
    void registrationBlockedMessageIsNullForCertifiedStaff() {
        AuthenticatedUser staffUser = AuthenticatedUser.from(buildUser(3, "staff1", RoleName.STAFF, staff));
        when(employeeRepository.findByIdWithStore(20)).thenReturn(Optional.of(staff));

        assertNull(service.registrationBlockedMessage(staffUser));
    }

    @Test
    void submitRecurringPatternDoesNotCreateShift() {
        AuthenticatedUser staffUser = AuthenticatedUser.from(buildUser(3, "staff1", RoleName.STAFF, staff));
        when(employeeRepository.findByIdWithStoreAndManager(20)).thenReturn(Optional.of(staff));
        when(shiftRepository.findByIdAndStore_Id(1, 1)).thenReturn(Optional.of(shift));

        service.submitNextWeekAvailability(nextWeekRequest(), staffUser);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<WorkAvailability>> captor = ArgumentCaptor.forClass(List.class);
        verify(workAvailabilityRepository).saveAll(captor.capture());
        WorkAvailability saved = captor.getValue().getFirst();
        LocalDate nextMonday = VietnamTime.today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(1);
        assertEquals(1, saved.getDayOfWeek());
        assertEquals(nextMonday, saved.getValidFrom());
        assertEquals(nextMonday.plusDays(6), saved.getValidTo());
        assertEquals(ApprovalStatus.PENDING, saved.getStatus());
        assertEquals("W1", saved.getDurationCode());
        verify(scheduleService, never()).assignShiftFromApprovedAvailability(any(), any());
    }

    @Test
    void submitRejectsOverlapWithApprovedPattern() {
        AuthenticatedUser staffUser = AuthenticatedUser.from(buildUser(3, "staff1", RoleName.STAFF, staff));
        LocalDate nextMonday = VietnamTime.today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(1);
        WorkAvailability approved = pendingAvailability();
        approved.setDayOfWeek(1);
        approved.setWorkDate(null);
        approved.setValidFrom(nextMonday);
        approved.setValidTo(nextMonday.plusMonths(1).minusDays(1));
        approved.setStatus(ApprovalStatus.APPROVED);
        when(employeeRepository.findByIdWithStoreAndManager(20)).thenReturn(Optional.of(staff));
        when(shiftRepository.findByIdAndStore_Id(1, 1)).thenReturn(Optional.of(shift));
        when(workAvailabilityRepository.findVisibleByEmployee(eq(20), any(LocalDate.class), eq(ApprovalStatus.PENDING)))
                .thenReturn(List.of(approved));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.submitNextWeekAvailability(monthRequest(), staffUser));

        assertTrue(ex.getMessage().contains("đã được duyệt"));
        verify(workAvailabilityRepository, never()).saveAll(any());
        verify(workAvailabilityRepository, never()).deleteByIds(any());
        assertEquals(ApprovalStatus.APPROVED, approved.getStatus());
    }

    @Test
    void submitReplacesOverlappingPendingOnly() {
        AuthenticatedUser staffUser = AuthenticatedUser.from(buildUser(3, "staff1", RoleName.STAFF, staff));
        LocalDate nextMonday = VietnamTime.today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(1);
        WorkAvailability pending = pendingAvailability();
        pending.setDayOfWeek(1);
        pending.setWorkDate(null);
        pending.setValidFrom(nextMonday);
        pending.setValidTo(nextMonday.plusDays(6));
        when(employeeRepository.findByIdWithStoreAndManager(20)).thenReturn(Optional.of(staff));
        when(shiftRepository.findByIdAndStore_Id(1, 1)).thenReturn(Optional.of(shift));
        when(workAvailabilityRepository.findVisibleByEmployee(eq(20), any(LocalDate.class), eq(ApprovalStatus.PENDING)))
                .thenReturn(List.of(pending));

        int count = service.submitNextWeekAvailability(nextWeekRequest(), staffUser);

        assertEquals(1, count);
        verify(workAvailabilityRepository).deleteByIds(List.of(5));
        verify(workAvailabilityRepository).saveAll(any());
    }

    @Test
    void approveRecurringRegistrationCreatesFutureShiftsAndNotifiesStaff() {
        WorkAvailability availability = pendingAvailability();
        availability.setDayOfWeek(1);
        availability.setWorkDate(null);
        availability.setDurationCode("M1");
        availability.setRegistrationKey("batch-1");
        LocalDate validFrom = LocalDate.of(2026, 10, 12);
        LocalDate validTo = LocalDate.of(2026, 11, 11);
        availability.setValidFrom(validFrom);
        availability.setValidTo(validTo);
        User staffAccount = buildUser(3, "staff1", RoleName.STAFF, staff);
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(workAvailabilityRepository.findByIdWithDetails(5)).thenReturn(Optional.of(availability));
        when(workAvailabilityRepository.findByRegistrationKey("batch-1")).thenReturn(List.of(availability));
        when(userRepository.findByEmployee_Id(20)).thenReturn(Optional.of(staffAccount));

        service.reviewAvailability(5, true, managerUser);

        List<LocalDate> expectedDates = AvailabilityCoverage.occurrences(1, validFrom, validTo).stream()
                .filter(date -> !date.isBefore(VietnamTime.today()))
                .toList();
        assertEquals(ApprovalStatus.APPROVED, availability.getStatus());
        verify(scheduleService, times(expectedDates.size()))
                .assignShiftFromApprovedAvailability(any(), eq(managerUser));
        verify(workAvailabilityRepository).saveAll(List.of(availability));
        verify(notificationService).notifyUsers(
                eq(List.of(staffAccount)),
                eq("Lịch làm việc đã được duyệt"),
                anyString(),
                eq(NotificationType.WORK_AVAILABILITY_APPROVED),
                eq("WORK_AVAILABILITY"),
                eq(20));
    }

    @Test
    void rejectRegistrationNotifiesStaff() {
        WorkAvailability availability = pendingAvailability();
        availability.setDayOfWeek(1);
        availability.setWorkDate(null);
        availability.setDurationCode("W1");
        availability.setRegistrationKey("batch-1");
        availability.setValidFrom(LocalDate.of(2026, 10, 12));
        availability.setValidTo(LocalDate.of(2026, 10, 18));
        User staffAccount = buildUser(3, "staff1", RoleName.STAFF, staff);
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(workAvailabilityRepository.findByRegistrationKey("batch-1")).thenReturn(List.of(availability));
        when(userRepository.findByEmployee_Id(20)).thenReturn(Optional.of(staffAccount));

        service.reviewRegistration("batch-1", false, managerUser);

        assertEquals(ApprovalStatus.REJECTED, availability.getStatus());
        verify(scheduleService, never()).assignShiftFromApprovedAvailability(any(), any());
        verify(notificationService).notifyUsers(
                eq(List.of(staffAccount)),
                eq("Lịch làm việc bị từ chối"),
                anyString(),
                eq(NotificationType.WORK_AVAILABILITY_REJECTED),
                eq("WORK_AVAILABILITY"),
                eq(20));
    }

    @Test
    void reviewAllThrowsWhenNothingPending() {
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(workAvailabilityRepository.findVisibleByStore(eq(1), any(LocalDate.class), eq(ApprovalStatus.PENDING)))
                .thenReturn(List.of());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.reviewAllNextWeekAvailabilities(true, managerUser));

        assertTrue(ex.getMessage().contains("chờ duyệt"));
        verify(scheduleService, never()).assignShiftFromApprovedAvailability(any(), any());
    }

    private SubmitWorkAvailabilityRequest nextWeekRequest() {
        return SubmitWorkAvailabilityRequest.builder()
                .durationCode("W1")
                .selectedSlots(List.of("1:1"))
                .build();
    }

    private SubmitWorkAvailabilityRequest monthRequest() {
        return SubmitWorkAvailabilityRequest.builder()
                .durationCode("M1")
                .selectedSlots(List.of("1:1"))
                .build();
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

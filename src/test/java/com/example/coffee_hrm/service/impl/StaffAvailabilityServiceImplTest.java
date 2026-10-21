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
import com.example.coffee_hrm.dto.response.SlotLimitUpdateResult;
import com.example.coffee_hrm.dto.response.WeeklyAvailabilityView;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.Shift;
import com.example.coffee_hrm.entity.ShiftSlotLimit;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.entity.WorkAvailability;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.ShiftRepository;
import com.example.coffee_hrm.repository.ShiftSlotLimitRepository;
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
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffAvailabilityServiceImplTest {

    @Mock
    private WorkAvailabilityRepository workAvailabilityRepository;
    @Mock
    private ShiftSlotLimitRepository shiftSlotLimitRepository;
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
        verifyNoInteractions(shiftSlotLimitRepository);
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
    void submitNotifiesStoreManager() {
        Employee manager = Employee.builder().id(10).fullName("Manager A").store(store)
                .status(EmployeeStatus.ACTIVE).build();
        store.setManager(manager);
        User managerAccount = buildUser(2, "manager1", RoleName.MANAGER, manager);
        AuthenticatedUser staffUser = AuthenticatedUser.from(buildUser(3, "staff1", RoleName.STAFF, staff));
        when(employeeRepository.findByIdWithStoreAndManager(20)).thenReturn(Optional.of(staff));
        when(shiftRepository.findByIdAndStore_Id(1, 1)).thenReturn(Optional.of(shift));
        when(userRepository.findByEmployee_Id(10)).thenReturn(Optional.of(managerAccount));
        stubOpenLimit(4);

        service.submitNextWeekAvailability(nextWeekRequest(), staffUser);

        verify(notificationService).notifyUsers(
                eq(List.of(managerAccount)),
                eq("Nhân viên đăng ký ca làm"),
                anyString(),
                eq(NotificationType.WORK_AVAILABILITY_SUBMITTED),
                eq("WORK_AVAILABILITY"),
                eq(20));
    }

    @Test
    void submitAllowsCertifiedStaff() {
        AuthenticatedUser staffUser = AuthenticatedUser.from(buildUser(3, "staff1", RoleName.STAFF, staff));
        when(employeeRepository.findByIdWithStoreAndManager(20)).thenReturn(Optional.of(staff));
        when(shiftRepository.findByIdAndStore_Id(1, 1)).thenReturn(Optional.of(shift));
        stubOpenLimit(4);

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
        stubOpenLimit(4);

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
        stubOpenLimit(4);

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
                eq("Đăng ký ca làm đã được duyệt"),
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
        verify(workAvailabilityRepository, never()).findVisibleByStore(any(), any(), any());
        verify(scheduleService, never()).assignShiftFromApprovedAvailability(any(), any());
        verify(notificationService).notifyUsers(
                eq(List.of(staffAccount)),
                eq("Đăng ký ca làm bị từ chối"),
                anyString(),
                eq(NotificationType.WORK_AVAILABILITY_REJECTED),
                eq("WORK_AVAILABILITY"),
                eq(20));
    }

    @Test
    void reviewOneEmployeeLeavesAnotherPending() {
        WorkAvailability selected = pendingAvailability();
        selected.setDayOfWeek(1);
        selected.setWorkDate(null);
        selected.setDurationCode("W1");
        selected.setRegistrationKey("batch-tuan");
        selected.setValidFrom(LocalDate.of(2026, 10, 12));
        selected.setValidTo(LocalDate.of(2026, 10, 18));
        Employee other = Employee.builder()
                .id(21)
                .fullName("Nguyễn Thị Mai")
                .store(store)
                .status(EmployeeStatus.ACTIVE)
                .certificationStatus(CertificationStatus.CERTIFIED)
                .build();
        WorkAvailability untouched = WorkAvailability.builder()
                .id(6)
                .employee(other)
                .shift(shift)
                .dayOfWeek(2)
                .workDate(null)
                .durationCode("W1")
                .registrationKey("batch-mai")
                .validFrom(LocalDate.of(2026, 10, 12))
                .validTo(LocalDate.of(2026, 10, 18))
                .status(ApprovalStatus.PENDING)
                .build();
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(workAvailabilityRepository.findByRegistrationKey("batch-tuan")).thenReturn(List.of(selected));
        when(userRepository.findByEmployee_Id(20)).thenReturn(Optional.of(buildUser(3, "staff1", RoleName.STAFF, staff)));

        service.reviewRegistration("batch-tuan", true, managerUser);

        assertEquals(ApprovalStatus.APPROVED, selected.getStatus());
        assertEquals(ApprovalStatus.PENDING, untouched.getStatus());
        verify(workAvailabilityRepository).saveAll(List.of(selected));
        verify(workAvailabilityRepository, never()).findByRegistrationKey("batch-mai");
        verify(notificationService).notifyUsers(any(), anyString(), anyString(), any(), anyString(), eq(20));
        verify(notificationService, never()).notifyUsers(any(), anyString(), anyString(), any(), anyString(), eq(21));
    }

    @Test
    void approveAllPendingLeavesProcessedRegistrationsUntouched() {
        WorkAvailability selected = pendingPattern("batch-tuan", staff);
        Employee other = Employee.builder()
                .id(21)
                .fullName("Nguyễn Thị Mai")
                .store(store)
                .status(EmployeeStatus.ACTIVE)
                .certificationStatus(CertificationStatus.CERTIFIED)
                .build();
        WorkAvailability otherPending = pendingPattern("batch-mai", other);
        otherPending.setId(6);
        WorkAvailability alreadyApproved = pendingPattern("batch-done", staff);
        alreadyApproved.setId(7);
        alreadyApproved.setStatus(ApprovalStatus.APPROVED);
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(workAvailabilityRepository.findVisibleByStore(eq(1), any(LocalDate.class), eq(ApprovalStatus.PENDING)))
                .thenReturn(List.of(selected, otherPending, alreadyApproved));
        when(workAvailabilityRepository.findByRegistrationKey("batch-tuan")).thenReturn(List.of(selected));
        when(workAvailabilityRepository.findByRegistrationKey("batch-mai")).thenReturn(List.of(otherPending));
        when(userRepository.findByEmployee_Id(20)).thenReturn(Optional.of(buildUser(3, "staff1", RoleName.STAFF, staff)));
        when(userRepository.findByEmployee_Id(21)).thenReturn(Optional.of(buildUser(4, "staff2", RoleName.STAFF, other)));

        int reviewed = service.reviewAllPendingRegistrations(true, managerUser);

        assertEquals(2, reviewed);
        assertEquals(ApprovalStatus.APPROVED, selected.getStatus());
        assertEquals(ApprovalStatus.APPROVED, otherPending.getStatus());
        assertEquals(ApprovalStatus.APPROVED, alreadyApproved.getStatus());
        verify(workAvailabilityRepository, never()).findByRegistrationKey("batch-done");
        verify(notificationService).notifyUsers(any(), anyString(), anyString(), any(), anyString(), eq(20));
        verify(notificationService).notifyUsers(any(), anyString(), anyString(), any(), anyString(), eq(21));
    }

    @Test
    void rejectAllPendingKeepsApprovedRegistration() {
        WorkAvailability pending = pendingPattern("batch-tuan", staff);
        WorkAvailability approved = pendingPattern("batch-done", staff);
        approved.setId(7);
        approved.setStatus(ApprovalStatus.APPROVED);
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(workAvailabilityRepository.findVisibleByStore(eq(1), any(LocalDate.class), eq(ApprovalStatus.PENDING)))
                .thenReturn(List.of(pending, approved));
        when(workAvailabilityRepository.findByRegistrationKey("batch-tuan")).thenReturn(List.of(pending));
        when(userRepository.findByEmployee_Id(20)).thenReturn(Optional.of(buildUser(3, "staff1", RoleName.STAFF, staff)));

        int reviewed = service.reviewAllPendingRegistrations(false, managerUser);

        assertEquals(1, reviewed);
        assertEquals(ApprovalStatus.REJECTED, pending.getStatus());
        assertEquals(ApprovalStatus.APPROVED, approved.getStatus());
        verify(scheduleService, never()).assignShiftFromApprovedAvailability(any(), any());
        verify(workAvailabilityRepository, never()).findByRegistrationKey("batch-done");
    }

    @Test
    void reviewAllDoesNothingWhenNoRegistrationIsPending() {
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(workAvailabilityRepository.findVisibleByStore(eq(1), any(LocalDate.class), eq(ApprovalStatus.PENDING)))
                .thenReturn(List.of());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.reviewAllPendingRegistrations(true, managerUser));

        assertEquals("Không có đơn đang chờ duyệt.", ex.getMessage());
        verify(workAvailabilityRepository, never()).findByRegistrationKey(anyString());
        verify(scheduleService, never()).assignShiftFromApprovedAvailability(any(), any());
    }

    @Test
    void approveAllStopsWhenOneEmployeeCannotBeScheduled() {
        WorkAvailability selected = pendingPattern("batch-tuan", staff);
        Employee other = Employee.builder()
                .id(21)
                .fullName("Nguyễn Thị Mai")
                .store(store)
                .status(EmployeeStatus.ACTIVE)
                .certificationStatus(CertificationStatus.CERTIFIED)
                .build();
        WorkAvailability otherPending = pendingPattern("batch-mai", other);
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(workAvailabilityRepository.findVisibleByStore(eq(1), any(LocalDate.class), eq(ApprovalStatus.PENDING)))
                .thenReturn(List.of(selected, otherPending));
        when(workAvailabilityRepository.findByRegistrationKey("batch-tuan")).thenReturn(List.of(selected));
        doThrow(new BusinessException("Nhân viên đã có ca trùng giờ."))
                .when(scheduleService).assignShiftFromApprovedAvailability(any(), any());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.reviewAllPendingRegistrations(true, managerUser));

        assertTrue(ex.getMessage().contains("trùng giờ"));
        assertEquals(ApprovalStatus.PENDING, selected.getStatus());
        assertEquals(ApprovalStatus.PENDING, otherPending.getStatus());
        verify(workAvailabilityRepository, never()).findByRegistrationKey("batch-mai");
        verify(workAvailabilityRepository, never()).saveAll(any());
    }

    @Test
    void managerConfiguresIndependentLimitsForEachDayAndShift() {
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(shiftRepository.findByIdAndStore_Id(1, 1)).thenReturn(Optional.of(shift));
        when(shiftSlotLimitRepository.lockByShiftAndDay(1, 1)).thenReturn(Optional.empty());
        when(shiftSlotLimitRepository.lockByShiftAndDay(1, 2)).thenReturn(Optional.empty());
        stubSchedulableStaff(5);

        SlotLimitUpdateResult monday = service.updateSlotLimit(1, 1, "4", managerUser);
        SlotLimitUpdateResult tuesday = service.updateSlotLimit(1, 2, "5", managerUser);

        ArgumentCaptor<ShiftSlotLimit> captor = ArgumentCaptor.forClass(ShiftSlotLimit.class);
        verify(shiftSlotLimitRepository, times(2)).saveAndFlush(captor.capture());
        assertEquals(1, captor.getAllValues().get(0).getDayOfWeek());
        assertEquals(4, captor.getAllValues().get(0).getMaxEmployees());
        assertEquals(1, captor.getAllValues().get(0).getShift().getId());
        assertEquals(2, captor.getAllValues().get(1).getDayOfWeek());
        assertEquals(5, captor.getAllValues().get(1).getMaxEmployees());
        assertFalse(monday.isWarning());
        assertTrue(monday.getMessage().contains("tuần"));
        assertFalse(tuesday.isWarning());
        verify(workAvailabilityRepository, never()).save(any());
        verify(workAvailabilityRepository, never()).saveAll(any());
    }

    @Test
    void slotLimitIsReadBackByWeekdayWithoutACalendarWeek() {
        AuthenticatedUser staffUser = AuthenticatedUser.from(buildUser(3, "staff1", RoleName.STAFF, staff));
        when(employeeRepository.findByIdWithStore(20)).thenReturn(Optional.of(staff));
        when(shiftRepository.findByStore_IdOrderByStartTimeAsc(1)).thenReturn(List.of(shift));
        when(shiftSlotLimitRepository.findByStoreId(1)).thenReturn(List.of(slotLimit(1, 4), slotLimit(2, 5)));
        WorkAvailability pending = patternAvailability(21, 1, ApprovalStatus.PENDING);
        WorkAvailability approved = patternAvailability(22, 1, ApprovalStatus.APPROVED);
        when(workAvailabilityRepository.findCapacityCandidatesByStore(
                eq(1), any(LocalDate.class), eq(ApprovalStatus.PENDING), any()))
                .thenReturn(List.of(pending, approved));

        WeeklyAvailabilityView grid = service.getNextWeekAvailabilityGrid(staffUser);

        WeeklyAvailabilityView.DayCell monday = grid.getShiftRows().getFirst().getDayCells().get(0);
        WeeklyAvailabilityView.DayCell tuesday = grid.getShiftRows().getFirst().getDayCells().get(1);
        WeeklyAvailabilityView.DayCell sunday = grid.getShiftRows().getFirst().getDayCells().get(6);
        assertEquals("Thứ 2", monday.getDayOfWeekName());
        assertEquals(4, monday.getMaxEmployees());
        assertEquals(1, monday.getApprovedCount());
        assertEquals(1, monday.getPendingCount());
        assertEquals(2, monday.getRemaining());
        assertEquals("2", monday.getRemainingText());
        assertEquals("open", monday.getSlotStatusKey());
        assertFalse(monday.isRegistrationClosed());
        assertEquals(5, tuesday.getMaxEmployees());
        assertEquals(0, tuesday.getPendingCount());
        assertEquals("5", tuesday.getRemainingText());
        assertFalse(sunday.isConfigured());
        assertTrue(sunday.isRegistrationClosed());
        assertEquals("unconfigured", sunday.getSlotStatusKey());
        assertEquals("—", sunday.getRemainingText());
    }

    @Test
    void pendingRegistrationOccupiesASeatAndFullSlotRejectsSubmit() {
        AuthenticatedUser staffUser = AuthenticatedUser.from(buildUser(3, "staff1", RoleName.STAFF, staff));
        when(employeeRepository.findByIdWithStoreAndManager(20)).thenReturn(Optional.of(staff));
        when(shiftRepository.findByIdAndStore_Id(1, 1)).thenReturn(Optional.of(shift));
        stubOpenLimit(1);
        when(workAvailabilityRepository.findCapacityCandidatesByShift(
                eq(1), any(LocalDate.class), eq(ApprovalStatus.PENDING), any()))
                .thenReturn(List.of(patternAvailability(21, 1, ApprovalStatus.PENDING)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.submitNextWeekAvailability(nextWeekRequest(), staffUser));

        assertTrue(ex.getMessage().contains("đã đủ số lượng nhân viên cho ngày"));
        assertTrue(ex.getMessage().contains("chờ duyệt"));
        verify(workAvailabilityRepository, never()).saveAll(any());
    }

    @Test
    void rejectedOrExpiredRegistrationDoesNotOccupyASeat() {
        AuthenticatedUser staffUser = AuthenticatedUser.from(buildUser(3, "staff1", RoleName.STAFF, staff));
        when(employeeRepository.findByIdWithStoreAndManager(20)).thenReturn(Optional.of(staff));
        when(shiftRepository.findByIdAndStore_Id(1, 1)).thenReturn(Optional.of(shift));
        stubOpenLimit(1);
        WorkAvailability rejected = patternAvailability(21, 1, ApprovalStatus.REJECTED);
        WorkAvailability expired = patternAvailability(22, 1, ApprovalStatus.APPROVED);
        expired.setValidTo(VietnamTime.today().minusDays(1));
        when(workAvailabilityRepository.findCapacityCandidatesByShift(
                eq(1), any(LocalDate.class), eq(ApprovalStatus.PENDING), any()))
                .thenReturn(List.of(rejected, expired));

        int count = service.submitNextWeekAvailability(nextWeekRequest(), staffUser);

        assertEquals(1, count);
        verify(workAvailabilityRepository).saveAll(any());
    }

    @Test
    void approvedRegistrationInAnotherWeekDoesNotFillNextWeek() {
        AuthenticatedUser staffUser = AuthenticatedUser.from(buildUser(3, "staff1", RoleName.STAFF, staff));
        LocalDate later = VietnamTime.today().plusMonths(4);
        WorkAvailability approved = patternAvailability(20, 1, ApprovalStatus.APPROVED);
        approved.setEmployee(staff);
        approved.setValidFrom(later);
        approved.setValidTo(later.plusMonths(1));
        when(employeeRepository.findByIdWithStoreAndManager(20)).thenReturn(Optional.of(staff));
        when(shiftRepository.findByIdAndStore_Id(1, 1)).thenReturn(Optional.of(shift));
        when(workAvailabilityRepository.findVisibleByEmployee(eq(20), any(LocalDate.class), eq(ApprovalStatus.PENDING)))
                .thenReturn(List.of(approved));
        when(workAvailabilityRepository.findCapacityCandidatesByShift(
                eq(1), any(LocalDate.class), eq(ApprovalStatus.PENDING), any()))
                .thenReturn(List.of(approved));
        stubOpenLimit(1);

        int count = service.submitNextWeekAvailability(nextWeekRequest(), staffUser);

        assertEquals(1, count);
        assertEquals(ApprovalStatus.APPROVED, approved.getStatus());
        verify(workAvailabilityRepository).saveAll(any());
        verify(workAvailabilityRepository, never()).deleteByIds(any());
    }

    @Test
    void duplicateRegistrationOnTheSameDateIsRejected() {
        AuthenticatedUser staffUser = AuthenticatedUser.from(buildUser(3, "staff1", RoleName.STAFF, staff));
        LocalDate nextMonday = nextMonday();
        WorkAvailability approved = patternAvailability(20, 1, ApprovalStatus.APPROVED);
        approved.setEmployee(staff);
        approved.setValidFrom(nextMonday);
        approved.setValidTo(nextMonday.plusDays(6));
        when(employeeRepository.findByIdWithStoreAndManager(20)).thenReturn(Optional.of(staff));
        when(shiftRepository.findByIdAndStore_Id(1, 1)).thenReturn(Optional.of(shift));
        when(workAvailabilityRepository.findVisibleByEmployee(eq(20), any(LocalDate.class), eq(ApprovalStatus.PENDING)))
                .thenReturn(List.of());
        when(workAvailabilityRepository.findCapacityCandidatesByShift(
                eq(1), any(LocalDate.class), eq(ApprovalStatus.PENDING), any()))
                .thenReturn(List.of(approved));
        stubOpenLimit(4);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.submitNextWeekAvailability(nextWeekRequest(), staffUser));

        assertTrue(ex.getMessage().contains("trùng"));
        assertTrue(ex.getMessage().contains(vn(nextMonday)));
        assertEquals(ApprovalStatus.APPROVED, approved.getStatus());
        verify(workAvailabilityRepository, never()).saveAll(any());
    }

    @Test
    void replacingOwnPendingDoesNotNeedAnExtraSeat() {
        AuthenticatedUser staffUser = AuthenticatedUser.from(buildUser(3, "staff1", RoleName.STAFF, staff));
        LocalDate nextMonday = VietnamTime.today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(1);
        WorkAvailability pending = patternAvailability(20, 1, ApprovalStatus.PENDING);
        pending.setId(5);
        pending.setEmployee(staff);
        pending.setValidFrom(nextMonday);
        pending.setValidTo(nextMonday.plusDays(6));
        when(employeeRepository.findByIdWithStoreAndManager(20)).thenReturn(Optional.of(staff));
        when(shiftRepository.findByIdAndStore_Id(1, 1)).thenReturn(Optional.of(shift));
        when(workAvailabilityRepository.findVisibleByEmployee(eq(20), any(LocalDate.class), eq(ApprovalStatus.PENDING)))
                .thenReturn(List.of(pending));
        when(workAvailabilityRepository.findCapacityCandidatesByShift(
                eq(1), any(LocalDate.class), eq(ApprovalStatus.PENDING), any()))
                .thenReturn(List.of(pending));
        stubOpenLimit(1);

        int count = service.submitNextWeekAvailability(nextWeekRequest(), staffUser);

        assertEquals(1, count);
        verify(workAvailabilityRepository).deleteByIds(List.of(5));
        verify(workAvailabilityRepository).saveAll(any());
    }

    @Test
    void loweringTheLimitBelowOccupiedDatesIsRejectedAndDoesNotSave() {
        ShiftSlotLimit existing = slotLimit(1, 5);
        WorkAvailability approved = patternAvailability(21, 1, ApprovalStatus.APPROVED);
        WorkAvailability pending = patternAvailability(22, 1, ApprovalStatus.PENDING);
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(shiftRepository.findByIdAndStore_Id(1, 1)).thenReturn(Optional.of(shift));
        when(shiftSlotLimitRepository.lockByShiftAndDay(1, 1)).thenReturn(Optional.of(existing));
        when(workAvailabilityRepository.findCapacityCandidatesByShift(
                eq(1), any(LocalDate.class), eq(ApprovalStatus.PENDING), any()))
                .thenReturn(List.of(approved, pending));
        stubSchedulableStaff(10);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateSlotLimit(1, 1, "1", managerUser));

        assertTrue(ex.getMessage().contains("Không lưu"));
        assertTrue(ex.getMessage().contains("giữ nguyên"));
        assertEquals(5, existing.getMaxEmployees());
        assertEquals(ApprovalStatus.APPROVED, approved.getStatus());
        assertEquals(ApprovalStatus.PENDING, pending.getStatus());
        verify(shiftSlotLimitRepository, never()).saveAndFlush(any());
        verify(workAvailabilityRepository, never()).save(any());
        verify(workAvailabilityRepository, never()).saveAll(any());
        verify(workAvailabilityRepository, never()).deleteByIds(any());
    }

    @Test
    void raisingTheLimitUpdatesRemainingSeats() {
        ShiftSlotLimit existing = slotLimit(1, 1);
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(shiftRepository.findByIdAndStore_Id(1, 1)).thenReturn(Optional.of(shift));
        when(shiftSlotLimitRepository.lockByShiftAndDay(1, 1)).thenReturn(Optional.of(existing));
        when(workAvailabilityRepository.findCapacityCandidatesByShift(
                eq(1), any(LocalDate.class), eq(ApprovalStatus.PENDING), any()))
                .thenReturn(List.of(patternAvailability(21, 1, ApprovalStatus.PENDING)));
        stubSchedulableStaff(4);

        SlotLimitUpdateResult result = service.updateSlotLimit(1, 1, "4", managerUser);

        assertFalse(result.isWarning());
        assertEquals(4, existing.getMaxEmployees());
        assertTrue(result.getMessage().contains("còn 3"));
        assertTrue(result.getMessage().contains("chờ duyệt 1"));
    }

    @Test
    void managerCannotConfigureAnotherStoresShift() {
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(shiftRepository.findByIdAndStore_Id(8, 1)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateSlotLimit(8, 1, "3", managerUser));

        assertEquals(StaffAvailabilityServiceImpl.OTHER_STORE_SHIFT_MESSAGE, ex.getMessage());
        verify(shiftSlotLimitRepository, never()).saveAndFlush(any());
    }

    @Test
    void limitMustBeAPositiveInteger() {
        assertEquals(4, StaffAvailabilityServiceImpl.parsePositiveLimit(" 4 "));
        for (String raw : new String[]{"0", "-1", "1.5", "abc", " ", null}) {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.updateSlotLimit(1, 1, raw, managerUser));
            assertEquals(StaffAvailabilityServiceImpl.INVALID_LIMIT_MESSAGE, ex.getMessage());
        }
        verifyNoInteractions(shiftSlotLimitRepository);
    }

    @Test
    void slotCapacityAcceptsOneThroughCurrentStaffAndRejectsMore() {
        ShiftSlotLimit existing = slotLimit(1, 11);
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(shiftRepository.findByIdAndStore_Id(1, 1)).thenReturn(Optional.of(shift));
        when(shiftSlotLimitRepository.lockByShiftAndDay(1, 1)).thenReturn(Optional.of(existing));
        stubSchedulableStaff(10);

        BusinessException over = assertThrows(BusinessException.class,
                () -> service.updateSlotLimit(1, 1, "11", managerUser));
        assertEquals(11, existing.getMaxEmployees());
        verify(shiftSlotLimitRepository, never()).saveAndFlush(any());

        SlotLimitUpdateResult eight = service.updateSlotLimit(1, 1, "8", managerUser);
        SlotLimitUpdateResult ten = service.updateSlotLimit(1, 1, "10", managerUser);

        assertFalse(eight.isWarning());
        assertFalse(ten.isWarning());
        assertEquals(10, existing.getMaxEmployees());
        assertEquals("Số lượng nhân viên trong ca không được vượt quá số nhân viên Staff hiện có tại cửa hàng (10 nhân viên).",
                over.getMessage());
        verify(employeeRepository, times(3)).countSchedulableStaff(
                1, EmployeeStatus.ACTIVE, RoleName.STAFF, 10);
        verify(shiftSlotLimitRepository, times(2)).saveAndFlush(existing);
        verify(workAvailabilityRepository, never()).deleteByIds(any());
        verify(workAvailabilityRepository, never()).save(any());
        verify(workAvailabilityRepository, never()).saveAll(any());
    }

    @Test
    void storeWithoutActiveStaffCannotSetAPositiveCapacity() {
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(shiftRepository.findByIdAndStore_Id(1, 1)).thenReturn(Optional.of(shift));
        stubSchedulableStaff(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateSlotLimit(1, 1, "1", managerUser));

        assertEquals(StaffAvailabilityServiceImpl.SLOT_LIMIT_NO_STAFF_MESSAGE, ex.getMessage());
        assertEquals(0, service.countStaffForSlotLimit(managerUser));
        verify(shiftSlotLimitRepository, never()).saveAndFlush(any());
    }

    @Test
    void submitLocksTheSlotBeforeCountingAndSaving() {
        AuthenticatedUser staffUser = AuthenticatedUser.from(buildUser(3, "staff1", RoleName.STAFF, staff));
        when(employeeRepository.findByIdWithStoreAndManager(20)).thenReturn(Optional.of(staff));
        when(shiftRepository.findByIdAndStore_Id(1, 1)).thenReturn(Optional.of(shift));
        stubOpenLimit(2);

        service.submitNextWeekAvailability(nextWeekRequest(), staffUser);

        InOrder order = inOrder(shiftSlotLimitRepository, workAvailabilityRepository);
        order.verify(shiftSlotLimitRepository).lockByShiftAndDay(1, 1);
        order.verify(workAvailabilityRepository).findCapacityCandidatesByShift(
                eq(1), any(LocalDate.class), eq(ApprovalStatus.PENDING), any());
        order.verify(workAvailabilityRepository).saveAll(any());
    }

    @Test
    void secondRegistrationSeesTheFirstAndDoesNotExceedTheLimit() {
        AuthenticatedUser firstUser = AuthenticatedUser.from(buildUser(3, "staff1", RoleName.STAFF, staff));
        Employee second = Employee.builder()
                .id(30)
                .fullName("Lê Thị Thu")
                .store(store)
                .status(EmployeeStatus.ACTIVE)
                .certificationStatus(CertificationStatus.CERTIFIED)
                .build();
        AuthenticatedUser secondUser = AuthenticatedUser.from(buildUser(4, "staff2", RoleName.STAFF, second));
        when(employeeRepository.findByIdWithStoreAndManager(20)).thenReturn(Optional.of(staff));
        when(employeeRepository.findByIdWithStoreAndManager(30)).thenReturn(Optional.of(second));
        when(shiftRepository.findByIdAndStore_Id(1, 1)).thenReturn(Optional.of(shift));
        stubOpenLimit(1);

        service.submitNextWeekAvailability(nextWeekRequest(), firstUser);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<WorkAvailability>> captor = ArgumentCaptor.forClass(List.class);
        verify(workAvailabilityRepository).saveAll(captor.capture());
        WorkAvailability saved = captor.getValue().getFirst();
        saved.setId(100);
        when(workAvailabilityRepository.findCapacityCandidatesByShift(
                eq(1), any(LocalDate.class), eq(ApprovalStatus.PENDING), any()))
                .thenReturn(List.of(saved));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.submitNextWeekAvailability(nextWeekRequest(), secondUser));

        assertTrue(ex.getMessage().contains("đã đủ số lượng nhân viên cho ngày"));
        assertTrue(ex.getMessage().contains(vn(nextMonday())));
        verify(workAvailabilityRepository, times(1)).saveAll(any());
    }

    @Test
    void multipleSlotsAreLockedInStableOrder() {
        Shift afternoon = Shift.builder()
                .id(2)
                .shiftName("Ca Chiều")
                .store(store)
                .startTime(LocalTime.of(13, 0))
                .endTime(LocalTime.of(17, 0))
                .build();
        AuthenticatedUser staffUser = AuthenticatedUser.from(buildUser(3, "staff1", RoleName.STAFF, staff));
        when(employeeRepository.findByIdWithStoreAndManager(20)).thenReturn(Optional.of(staff));
        when(shiftRepository.findByIdAndStore_Id(1, 1)).thenReturn(Optional.of(shift));
        when(shiftRepository.findByIdAndStore_Id(2, 1)).thenReturn(Optional.of(afternoon));
        when(shiftSlotLimitRepository.lockByShiftAndDay(1, 1)).thenReturn(Optional.of(slotLimit(1, 3)));
        when(shiftSlotLimitRepository.lockByShiftAndDay(2, 3)).thenReturn(Optional.of(
                ShiftSlotLimit.builder().id(8).shift(afternoon).dayOfWeek(3).maxEmployees(3).build()));

        service.submitNextWeekAvailability(SubmitWorkAvailabilityRequest.builder()
                .durationCode("W1")
                .selectedSlots(List.of("2:3", "1:1"))
                .build(), staffUser);

        InOrder order = inOrder(shiftSlotLimitRepository);
        order.verify(shiftSlotLimitRepository).lockByShiftAndDay(1, 1);
        order.verify(shiftSlotLimitRepository).lockByShiftAndDay(2, 3);
    }

    @Test
    void managerGridWarnsWhenCurrentRegistrationsExceedTheNewLimit() {
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(shiftRepository.findByStore_IdOrderByStartTimeAsc(1)).thenReturn(List.of(shift));
        when(shiftSlotLimitRepository.findByStoreId(1)).thenReturn(List.of(slotLimit(1, 1)));
        when(workAvailabilityRepository.findCapacityCandidatesByStore(
                eq(1), any(LocalDate.class), eq(ApprovalStatus.PENDING), any()))
                .thenReturn(List.of(
                        patternAvailability(21, 1, ApprovalStatus.APPROVED),
                        patternAvailability(22, 1, ApprovalStatus.PENDING)));

        WeeklyAvailabilityView grid = service.getStoreNextWeekAvailabilityGrid(managerUser);
        WeeklyAvailabilityView.DayCell monday = grid.getShiftRows().getFirst().getDayCells().get(0);

        assertEquals(1, monday.getApprovedCount());
        assertEquals(1, monday.getPendingCount());
        assertEquals("0", monday.getRemainingText());
        assertTrue(monday.isOverCapacity());
        assertEquals("over", monday.getSlotStatusKey());
        assertTrue(monday.getRegistrationHint().contains("Tạm ngưng"));
    }

    @Test
    void ownApprovedRegistrationClosesTheSlotEvenWhenSeatsRemain() {
        AuthenticatedUser staffUser = AuthenticatedUser.from(buildUser(3, "staff1", RoleName.STAFF, staff));
        when(employeeRepository.findByIdWithStore(20)).thenReturn(Optional.of(staff));
        when(shiftRepository.findByStore_IdOrderByStartTimeAsc(1)).thenReturn(List.of(shift));
        when(shiftSlotLimitRepository.findByStoreId(1)).thenReturn(List.of(slotLimit(1, 4)));
        WorkAvailability approved = patternAvailability(20, 1, ApprovalStatus.APPROVED);
        approved.setEmployee(staff);
        when(workAvailabilityRepository.findCapacityCandidatesByStore(
                eq(1), any(LocalDate.class), eq(ApprovalStatus.PENDING), any()))
                .thenReturn(List.of(approved));

        WeeklyAvailabilityView grid = service.getNextWeekAvailabilityGrid(staffUser);
        WeeklyAvailabilityView.DayCell monday = grid.getShiftRows().getFirst().getDayCells().get(0);

        assertEquals(3, monday.getRemaining());
        assertTrue(monday.isRegistrationClosed());
        assertTrue(monday.getRegistrationHint().contains("đã đăng ký"));
    }

    @Test
    void capacityRulesMatchTheRemainingSeatFormula() {
        LocalDate today = VietnamTime.today();
        WorkAvailability rejected = patternAvailability(21, 1, ApprovalStatus.REJECTED);
        WorkAvailability expired = patternAvailability(22, 1, ApprovalStatus.APPROVED);
        expired.setValidTo(today.minusDays(1));
        WorkAvailability pending = patternAvailability(23, 1, ApprovalStatus.PENDING);
        pending.setValidTo(today.minusDays(1));
        WorkAvailability otherDay = patternAvailability(24, 2, ApprovalStatus.APPROVED);

        LocalDate nextMonday = nextMonday();
        WorkAvailability currentWeek = patternAvailability(25, 1, ApprovalStatus.APPROVED);
        LocalDate thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        currentWeek.setValidFrom(thisMonday);
        currentWeek.setValidTo(thisMonday.plusDays(6));
        WorkAvailability nextWeekPending = patternAvailability(26, 1, ApprovalStatus.PENDING);
        nextWeekPending.setValidFrom(nextMonday);
        nextWeekPending.setValidTo(nextMonday.plusDays(6));

        assertFalse(StaffAvailabilityServiceImpl.occupiesSlot(rejected, 1, nextMonday));
        assertFalse(StaffAvailabilityServiceImpl.occupiesSlot(expired, 1, today));
        assertFalse(StaffAvailabilityServiceImpl.occupiesSlot(pending, 1, today));
        assertFalse(StaffAvailabilityServiceImpl.occupiesSlot(otherDay, 1, nextMonday));
        assertFalse(StaffAvailabilityServiceImpl.occupiesSlot(currentWeek, 1, nextMonday));
        assertTrue(StaffAvailabilityServiceImpl.occupiesSlot(nextWeekPending, 1, nextMonday));
        assertTrue(StaffAvailabilityServiceImpl.occupiesSlot(currentWeek, 1, thisMonday));
        assertEquals(1, StaffAvailabilityServiceImpl.remainingSeats(4, 2, 1));
        assertEquals(-1, StaffAvailabilityServiceImpl.remainingSeats(2, 1, 2));
    }

    @Test
    void currentWeekOccupancyDoesNotBlockNextWeekRegistration() {
        AuthenticatedUser staffUser = AuthenticatedUser.from(buildUser(3, "staff1", RoleName.STAFF, staff));
        LocalDate thisMonday = VietnamTime.today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate thisSunday = thisMonday.plusDays(6);
        List<WorkAvailability> currentWeek = new java.util.ArrayList<>();
        for (int employeeId = 21; employeeId <= 23; employeeId++) {
            WorkAvailability approved = patternAvailability(employeeId, 1, ApprovalStatus.APPROVED);
            approved.setValidFrom(thisMonday);
            approved.setValidTo(thisSunday);
            currentWeek.add(approved);
        }
        when(employeeRepository.findByIdWithStoreAndManager(20)).thenReturn(Optional.of(staff));
        when(shiftRepository.findByIdAndStore_Id(1, 1)).thenReturn(Optional.of(shift));
        when(workAvailabilityRepository.findCapacityCandidatesByShift(
                eq(1), any(LocalDate.class), eq(ApprovalStatus.PENDING), any()))
                .thenReturn(currentWeek);
        stubOpenLimit(3);

        int count = service.submitNextWeekAvailability(nextWeekRequest(), staffUser);

        assertEquals(1, count);
        verify(workAvailabilityRepository).saveAll(any());
    }

    @Test
    void nextWeekGridCountsOnlyThatWeek() {
        AuthenticatedUser staffUser = AuthenticatedUser.from(buildUser(3, "staff1", RoleName.STAFF, staff));
        LocalDate thisMonday = VietnamTime.today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        WorkAvailability currentWeek = patternAvailability(21, 1, ApprovalStatus.APPROVED);
        currentWeek.setValidFrom(thisMonday);
        currentWeek.setValidTo(thisMonday.plusDays(6));
        WorkAvailability nextWeek = patternAvailability(22, 1, ApprovalStatus.PENDING);
        nextWeek.setValidFrom(nextMonday());
        nextWeek.setValidTo(nextMonday().plusDays(6));
        when(employeeRepository.findByIdWithStore(20)).thenReturn(Optional.of(staff));
        when(shiftRepository.findByStore_IdOrderByStartTimeAsc(1)).thenReturn(List.of(shift));
        when(shiftSlotLimitRepository.findByStoreId(1)).thenReturn(List.of(slotLimit(1, 3)));
        when(workAvailabilityRepository.findCapacityCandidatesByStore(
                eq(1), any(LocalDate.class), eq(ApprovalStatus.PENDING), any()))
                .thenReturn(List.of(currentWeek, nextWeek));

        WeeklyAvailabilityView grid = service.getNextWeekAvailabilityGrid(staffUser);
        WeeklyAvailabilityView.DayCell monday = grid.getShiftRows().getFirst().getDayCells().get(0);

        assertEquals(nextMonday(), grid.getWeekStartDate());
        assertEquals(vn(nextMonday()), grid.getDays().getFirst().getFormattedDate());
        assertEquals(0, monday.getApprovedCount());
        assertEquals(1, monday.getPendingCount());
        assertEquals(2, monday.getRemaining());
        assertEquals("open", monday.getSlotStatusKey());
        assertFalse(monday.isRegistrationClosed());
    }

    @Test
    void oneWeekRegistrationIgnoresAFullLaterWeek() {
        AuthenticatedUser staffUser = AuthenticatedUser.from(buildUser(3, "staff1", RoleName.STAFF, staff));
        LocalDate laterMonday = nextMonday().plusWeeks(3);
        WorkAvailability later = patternAvailability(21, 1, ApprovalStatus.APPROVED);
        later.setValidFrom(laterMonday);
        later.setValidTo(laterMonday.plusDays(6));
        when(employeeRepository.findByIdWithStoreAndManager(20)).thenReturn(Optional.of(staff));
        when(shiftRepository.findByIdAndStore_Id(1, 1)).thenReturn(Optional.of(shift));
        when(workAvailabilityRepository.findCapacityCandidatesByShift(
                eq(1), any(LocalDate.class), eq(ApprovalStatus.PENDING), any()))
                .thenReturn(List.of(later));
        stubOpenLimit(1);

        assertEquals(1, service.submitNextWeekAvailability(nextWeekRequest(), staffUser));
    }

    @Test
    void longerRegistrationIsRejectedOnTheFullLaterDate() {
        AuthenticatedUser staffUser = AuthenticatedUser.from(buildUser(3, "staff1", RoleName.STAFF, staff));
        LocalDate laterMonday = nextMonday().plusWeeks(3);
        WorkAvailability later = patternAvailability(21, 1, ApprovalStatus.APPROVED);
        later.setValidFrom(laterMonday);
        later.setValidTo(laterMonday.plusDays(6));
        when(employeeRepository.findByIdWithStoreAndManager(20)).thenReturn(Optional.of(staff));
        when(shiftRepository.findByIdAndStore_Id(1, 1)).thenReturn(Optional.of(shift));
        when(workAvailabilityRepository.findCapacityCandidatesByShift(
                eq(1), any(LocalDate.class), eq(ApprovalStatus.PENDING), any()))
                .thenReturn(List.of(later));
        stubOpenLimit(1);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.submitNextWeekAvailability(monthRequest(), staffUser));

        assertTrue(ex.getMessage().contains("đã đủ số lượng nhân viên cho ngày " + vn(laterMonday)));
        verify(workAvailabilityRepository, never()).saveAll(any());
    }

    @Test
    void sameEmployeeOccupiesOneSeatOnTheSameDate() {
        AuthenticatedUser staffUser = AuthenticatedUser.from(buildUser(3, "staff1", RoleName.STAFF, staff));
        LocalDate nextMonday = nextMonday();
        WorkAvailability approved = patternAvailability(21, 1, ApprovalStatus.APPROVED);
        approved.setValidFrom(nextMonday);
        approved.setValidTo(nextMonday.plusDays(6));
        WorkAvailability pending = patternAvailability(21, 1, ApprovalStatus.PENDING);
        pending.setId(2100);
        pending.setValidFrom(nextMonday);
        pending.setValidTo(nextMonday.plusDays(6));
        when(employeeRepository.findByIdWithStoreAndManager(20)).thenReturn(Optional.of(staff));
        when(shiftRepository.findByIdAndStore_Id(1, 1)).thenReturn(Optional.of(shift));
        when(workAvailabilityRepository.findCapacityCandidatesByShift(
                eq(1), any(LocalDate.class), eq(ApprovalStatus.PENDING), any()))
                .thenReturn(List.of(approved, pending));
        stubOpenLimit(2);

        assertEquals(1, service.submitNextWeekAvailability(nextWeekRequest(), staffUser));
    }

    @Test
    void pastWeekOccupancyDoesNotBlockALowerFutureLimit() {
        ShiftSlotLimit existing = slotLimit(1, 5);
        LocalDate thisMonday = VietnamTime.today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate lastMonday = thisMonday.minusWeeks(1);
        WorkAvailability past = patternAvailability(21, 1, ApprovalStatus.APPROVED);
        past.setValidFrom(lastMonday);
        past.setValidTo(lastMonday.plusDays(6));
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(shiftRepository.findByIdAndStore_Id(1, 1)).thenReturn(Optional.of(shift));
        when(shiftSlotLimitRepository.lockByShiftAndDay(1, 1)).thenReturn(Optional.of(existing));
        when(workAvailabilityRepository.findCapacityCandidatesByShift(
                eq(1), any(LocalDate.class), eq(ApprovalStatus.PENDING), any()))
                .thenReturn(List.of(past));
        stubSchedulableStaff(10);

        SlotLimitUpdateResult result = service.updateSlotLimit(1, 1, "1", managerUser);

        assertFalse(result.isWarning());
        assertEquals(1, existing.getMaxEmployees());
        assertTrue(result.getMessage().contains("còn 1"));
        verify(shiftSlotLimitRepository).saveAndFlush(existing);
        verify(workAvailabilityRepository, never()).deleteByIds(any());
    }

    @Test
    void unconfiguredSlotCannotBeSubmitted() {
        AuthenticatedUser staffUser = AuthenticatedUser.from(buildUser(3, "staff1", RoleName.STAFF, staff));
        when(employeeRepository.findByIdWithStoreAndManager(20)).thenReturn(Optional.of(staff));
        when(shiftRepository.findByIdAndStore_Id(1, 1)).thenReturn(Optional.of(shift));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.submitNextWeekAvailability(nextWeekRequest(), staffUser));

        assertTrue(ex.getMessage().contains("chưa có số lượng"));
        verify(workAvailabilityRepository, never()).saveAll(any());
    }

    private LocalDate nextMonday() {
        return VietnamTime.today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(1);
    }

    private String vn(LocalDate date) {
        return date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
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

    private WorkAvailability patternAvailability(int employeeId, int dayOfWeek, ApprovalStatus status) {
        Employee employee = employeeId == staff.getId()
                ? staff
                : Employee.builder().id(employeeId).fullName("Nhân viên " + employeeId).store(store).build();
        return WorkAvailability.builder()
                .id(employeeId)
                .employee(employee)
                .shift(shift)
                .dayOfWeek(dayOfWeek)
                .workDate(null)
                .validFrom(VietnamTime.today())
                .validTo(VietnamTime.today().plusMonths(1))
                .status(status)
                .build();
    }

    private ShiftSlotLimit slotLimit(int dayOfWeek, int maxEmployees) {
        return ShiftSlotLimit.builder()
                .id(90 + dayOfWeek)
                .shift(shift)
                .dayOfWeek(dayOfWeek)
                .maxEmployees(maxEmployees)
                .updatedAt(VietnamTime.now())
                .build();
    }

    private void stubOpenLimit(int maxEmployees) {
        when(shiftSlotLimitRepository.lockByShiftAndDay(1, 1))
                .thenReturn(Optional.of(slotLimit(1, maxEmployees)));
    }

    private WorkAvailability pendingPattern(String registrationKey, Employee employee) {
        LocalDate nextMonday = VietnamTime.today().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        return WorkAvailability.builder()
                .id(employee.getId())
                .employee(employee)
                .shift(shift)
                .dayOfWeek(1)
                .workDate(null)
                .durationCode("W1")
                .registrationKey(registrationKey)
                .validFrom(nextMonday)
                .validTo(nextMonday.plusDays(6))
                .status(ApprovalStatus.PENDING)
                .build();
    }

    private void stubSchedulableStaff(int staffCount) {
        when(employeeRepository.countSchedulableStaff(1, EmployeeStatus.ACTIVE, RoleName.STAFF, 10))
                .thenReturn((long) staffCount);
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

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

    @Test
    void managerConfiguresIndependentLimitsForEachDayAndShift() {
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(shiftRepository.findByIdAndStore_Id(1, 1)).thenReturn(Optional.of(shift));
        when(shiftSlotLimitRepository.lockByShiftAndDay(1, 1)).thenReturn(Optional.empty());
        when(shiftSlotLimitRepository.lockByShiftAndDay(1, 2)).thenReturn(Optional.empty());

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

        assertTrue(ex.getMessage().contains("đã đủ người"));
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
    void duplicateRegistrationForTheSameSlotIsRejected() {
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
        stubOpenLimit(4);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.submitNextWeekAvailability(nextWeekRequest(), staffUser));

        assertTrue(ex.getMessage().contains("trùng"));
        assertEquals(ApprovalStatus.APPROVED, approved.getStatus());
        verify(workAvailabilityRepository, never()).saveAll(any());
        verify(workAvailabilityRepository, never()).deleteByIds(any());
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
    void loweringTheLimitKeepsExistingRegistrationsAndWarns() {
        ShiftSlotLimit existing = slotLimit(1, 5);
        WorkAvailability approved = patternAvailability(21, 1, ApprovalStatus.APPROVED);
        WorkAvailability pending = patternAvailability(22, 1, ApprovalStatus.PENDING);
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(shiftRepository.findByIdAndStore_Id(1, 1)).thenReturn(Optional.of(shift));
        when(shiftSlotLimitRepository.lockByShiftAndDay(1, 1)).thenReturn(Optional.of(existing));
        when(workAvailabilityRepository.findCapacityCandidatesByShift(
                eq(1), any(LocalDate.class), eq(ApprovalStatus.PENDING), any()))
                .thenReturn(List.of(approved, pending));

        SlotLimitUpdateResult result = service.updateSlotLimit(1, 1, "1", managerUser);

        assertTrue(result.isWarning());
        assertTrue(result.getMessage().contains("giữ nguyên"));
        assertEquals(1, existing.getMaxEmployees());
        assertEquals(ApprovalStatus.APPROVED, approved.getStatus());
        assertEquals(ApprovalStatus.PENDING, pending.getStatus());
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

        assertTrue(ex.getMessage().contains("đã đủ người"));
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

        assertFalse(StaffAvailabilityServiceImpl.occupiesSlot(rejected, 1, today));
        assertFalse(StaffAvailabilityServiceImpl.occupiesSlot(expired, 1, today));
        assertTrue(StaffAvailabilityServiceImpl.occupiesSlot(pending, 1, today));
        assertFalse(StaffAvailabilityServiceImpl.occupiesSlot(otherDay, 1, today));
        assertEquals(1, StaffAvailabilityServiceImpl.remainingSeats(4, 2, 1));
        assertEquals(-1, StaffAvailabilityServiceImpl.remainingSeats(2, 1, 2));
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

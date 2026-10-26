package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.ApprovalStatus;
import com.example.coffee_hrm.common.enums.CertificationStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.enums.TrainingSkillStatus;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.request.SubmitWorkAvailabilityRequest;
import com.example.coffee_hrm.dto.response.WeeklyAvailabilityView;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.EmploymentContract;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.Shift;
import com.example.coffee_hrm.entity.ShiftSlotLimit;
import com.example.coffee_hrm.entity.ShiftSlotPositionNeed;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.TrainingSkill;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.entity.WorkAvailability;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.EmploymentContractRepository;
import com.example.coffee_hrm.repository.ShiftRepository;
import com.example.coffee_hrm.repository.ShiftSlotLimitRepository;
import com.example.coffee_hrm.repository.ShiftSlotPositionNeedRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.repository.TrainingClassEnrollmentRepository;
import com.example.coffee_hrm.repository.TrainingSkillRepository;
import com.example.coffee_hrm.repository.UserRepository;
import com.example.coffee_hrm.repository.WorkAvailabilityRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.NotificationService;
import com.example.coffee_hrm.service.ScheduleService;
import com.example.coffee_hrm.service.support.PositionStaffing;
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
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShiftPositionStaffingServiceTest {

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
    @Mock
    private ShiftSlotPositionNeedRepository shiftSlotPositionNeedRepository;
    @Mock
    private TrainingSkillRepository trainingSkillRepository;
    @Mock
    private TrainingClassEnrollmentRepository trainingClassEnrollmentRepository;
    @Mock
    private EmploymentContractRepository employmentContractRepository;

    @InjectMocks
    private StaffAvailabilityServiceImpl service;

    private Store store;
    private Employee manager;
    private Employee staff;
    private AuthenticatedUser managerUser;
    private AuthenticatedUser staffUser;
    private Shift shift;
    private TrainingSkill brew;
    private TrainingSkill cash;
    private TrainingSkill serve;

    @BeforeEach
    void setUp() {
        store = Store.builder().id(1).storeName("Cửa hàng 1").isActive(true).build();
        manager = Employee.builder().id(10).fullName("Quản lý").store(store).status(EmployeeStatus.ACTIVE).build();
        staff = Employee.builder()
                .id(20)
                .fullName("Trần Văn Tuấn")
                .store(store)
                .status(EmployeeStatus.ACTIVE)
                .certificationStatus(CertificationStatus.CERTIFIED)
                .build();
        store.setManager(manager);
        managerUser = AuthenticatedUser.from(user(2, RoleName.MANAGER, manager));
        staffUser = AuthenticatedUser.from(user(3, RoleName.STAFF, staff));
        shift = Shift.builder()
                .id(1)
                .store(store)
                .shiftName("Ca sáng")
                .startTime(LocalTime.of(7, 0))
                .endTime(LocalTime.of(11, 0))
                .build();
        brew = skill(1, "Pha chế");
        cash = skill(2, "Thu ngân");
        serve = skill(3, "Phục vụ");
        lenient().when(employmentContractRepository.findByEmployeeId(any()))
                .thenReturn(List.of(EmploymentContract.builder()
                        .startDate(LocalDate.of(2020, 1, 6))
                        .endDate(LocalDate.of(2035, 12, 31))
                        .weeklyShiftCount(1)
                        .build()));
    }

    @Test
    void savesTheShiftTotalAndClearsOldPositionNeeds() {
        stubOwnedShift();
        stubStaffCount(5);
        ShiftSlotLimit limit = openLimit(5);
        when(shiftSlotLimitRepository.lockByShiftAndDay(1, 1)).thenReturn(Optional.of(limit));
        when(workAvailabilityRepository.findCapacityCandidatesByShift(eq(1), any(), any(), any())).thenReturn(List.of());

        var result = service.updateSlotStaffing(1, 1, "5", counts(2, 1, 2), managerUser);

        assertTrue(result.getMessage().contains("tối đa 5"));
        assertFalse(result.getMessage().contains("Pha chế"));
        verify(shiftSlotPositionNeedRepository).deleteByLimitId(91);
        verify(shiftSlotPositionNeedRepository, never()).saveAll(any());
    }

    @Test
    void positionCountsNoLongerBlockTheShiftTotal() {
        stubOwnedShift();
        stubStaffCount(5);
        when(shiftSlotLimitRepository.lockByShiftAndDay(1, 1)).thenReturn(Optional.of(openLimit(5)));
        when(workAvailabilityRepository.findCapacityCandidatesByShift(eq(1), any(), any(), any())).thenReturn(List.of());

        var result = service.updateSlotStaffing(1, 1, "5", counts(2, 1, 1), managerUser);

        assertTrue(result.getMessage().contains("tối đa 5"));
        verify(shiftSlotLimitRepository).saveAndFlush(any());
    }

    @Test
    void negativePositionCountIsIgnored() {
        stubOwnedShift();
        stubStaffCount(5);
        when(shiftSlotLimitRepository.lockByShiftAndDay(1, 1)).thenReturn(Optional.of(openLimit(5)));
        when(workAvailabilityRepository.findCapacityCandidatesByShift(eq(1), any(), any(), any())).thenReturn(List.of());

        var result = service.updateSlotStaffing(1, 1, "5", Map.of(1, "2", 2, "-1", 3, "4"), managerUser);

        assertTrue(result.getMessage().contains("tối đa 5"));
    }

    @Test
    void rejectsTargetAboveSchedulableStaff() {
        stubOwnedShift();
        stubStaffCount(4);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateSlotStaffing(1, 1, "5", counts(2, 1, 2), managerUser));

        assertEquals(StaffAvailabilityServiceImpl.SLOT_LIMIT_HEADCOUNT_MESSAGE, ex.getMessage());
    }

    @Test
    void certifiedHeadcountDoesNotLimitTheShiftTotal() {
        stubOwnedShift();
        stubStaffCount(5);
        when(shiftSlotLimitRepository.lockByShiftAndDay(1, 1)).thenReturn(Optional.of(openLimit(5)));
        when(workAvailabilityRepository.findCapacityCandidatesByShift(eq(1), any(), any(), any())).thenReturn(List.of());

        var result = service.updateSlotStaffing(1, 1, "5", counts(2, 1, 2), managerUser);

        assertTrue(result.getMessage().contains("tối đa 5"));
        assertFalse(result.getMessage().contains("Pha chế"));
    }

    @Test
    void rejectsConfigurationForAnotherStore() {
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(shiftRepository.findByIdAndStore_Id(8, 1)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateSlotStaffing(8, 1, "5", counts(2, 1, 2), managerUser));

        assertEquals(StaffAvailabilityServiceImpl.OTHER_STORE_SHIFT_MESSAGE, ex.getMessage());
    }

    @Test
    void changingTheTargetClearsOldPositionNeeds() {
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(shiftRepository.findByIdAndStore_Id(1, 1)).thenReturn(Optional.of(shift));
        when(employeeRepository.countSchedulableStaff(1, EmployeeStatus.ACTIVE, RoleName.STAFF, 10)).thenReturn(5L);
        when(shiftSlotLimitRepository.lockByShiftAndDay(1, 1)).thenReturn(Optional.of(openLimit(5)));
        when(workAvailabilityRepository.findCapacityCandidatesByShift(eq(1), any(), any(), any())).thenReturn(List.of());

        var result = service.updateSlotLimit(1, 1, "4", managerUser);

        assertTrue(result.getMessage().contains("tối đa 4"));
        assertFalse(result.getMessage().contains("vị trí"));
        verify(shiftSlotPositionNeedRepository).deleteByLimitId(91);
    }

    @Test
    void existingShiftWithoutPositionNeedsStillUpdatesTheTotal() {
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(shiftRepository.findByIdAndStore_Id(1, 1)).thenReturn(Optional.of(shift));
        when(employeeRepository.countSchedulableStaff(1, EmployeeStatus.ACTIVE, RoleName.STAFF, 10)).thenReturn(5L);
        when(shiftSlotLimitRepository.lockByShiftAndDay(1, 1)).thenReturn(Optional.of(openLimit(3)));
        when(workAvailabilityRepository.findCapacityCandidatesByShift(eq(1), any(), any(), any())).thenReturn(List.of());

        var result = service.updateSlotLimit(1, 1, "4", managerUser);

        assertTrue(result.getMessage().contains("tối đa 4"));
        verify(shiftSlotPositionNeedRepository).deleteByLimitId(91);
    }

    @Test
    void gridShowsAFullPositionBesideAShortOneAndIgnoresPendingRejectedAndUnqualified() {
        Employee cashier = certified(12, "Thu ngân viên");
        Employee server = certified(13, "Phục vụ viên");
        Employee waiting = certified(14, "Người chờ");
        Employee rejected = certified(15, "Người từ chối");
        Employee unqualified = certified(16, "Chưa đủ chứng chỉ vị trí");
        WorkAvailability approvedBrew = registration(staff, brew, ApprovalStatus.APPROVED);
        WorkAvailability approvedCash = registration(cashier, cash, ApprovalStatus.APPROVED);
        WorkAvailability approvedServe = registration(server, serve, ApprovalStatus.APPROVED);
        WorkAvailability pendingBrew = registration(waiting, brew, ApprovalStatus.PENDING);
        WorkAvailability rejectedBrew = registration(rejected, brew, ApprovalStatus.REJECTED);
        WorkAvailability approvedWithoutSkill = registration(unqualified, brew, ApprovalStatus.APPROVED);
        approvedWithoutSkill.setSkill(null);

        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(shiftRepository.findByStore_IdOrderByStartTimeAsc(1)).thenReturn(List.of(shift));
        when(shiftSlotLimitRepository.findByStoreId(1)).thenReturn(List.of(openLimit(5)));
        when(workAvailabilityRepository.findVisibleByStore(eq(1), any(), eq(ApprovalStatus.PENDING)))
                .thenReturn(List.of());
        when(workAvailabilityRepository.findCapacityCandidatesByStore(eq(1), any(), any(), any()))
                .thenReturn(List.of(approvedBrew, approvedCash, approvedServe, pendingBrew, rejectedBrew, approvedWithoutSkill));

        WeeklyAvailabilityView.DayCell monday = service.getStoreNextWeekAvailabilityGrid(managerUser)
                .getShiftRows().getFirst().getDayCells().getFirst();

        assertEquals(5, monday.getMaxEmployees());
        assertEquals(5, monday.getRegisteredCount());
        assertEquals(1, monday.getPendingCount());
        assertEquals(4, monday.getApprovedCount());
        assertEquals(1, monday.getShortfall());
        assertEquals(0, monday.getPositionsFilled());
        assertEquals(0, monday.getPositionsTracked());
        assertTrue(monday.getPositionLines().isEmpty());
        assertEquals(PositionStaffing.SHORT, monday.getStaffingStatusKey());
        assertTrue(monday.isAwaitingApproval());
        assertTrue(monday.getShortageSummary() == null || !monday.getShortageSummary().contains("Pha chế"));
    }

    @Test
    void approvingThenRejectingChangesTheConfirmedCount() {
        WorkAvailability pending = registration(staff, brew, ApprovalStatus.PENDING);
        pending.setRegistrationKey("don-1");
        ShiftSlotLimit limit = openLimit(1);
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(workAvailabilityRepository.findByRegistrationKey("don-1")).thenReturn(List.of(pending));
        when(shiftSlotLimitRepository.lockByShiftAndDay(1, 1)).thenReturn(Optional.of(limit));
        when(workAvailabilityRepository.findCapacityCandidatesByShift(eq(1), any(), any(), any()))
                .thenReturn(List.of(pending));
        when(userRepository.findByEmployee_Id(20)).thenReturn(Optional.of(user(3, RoleName.STAFF, staff)));

        service.reviewRegistration("don-1", true, managerUser);

        assertEquals(ApprovalStatus.APPROVED, pending.getStatus());

        pending.setStatus(ApprovalStatus.PENDING);
        service.reviewRegistration("don-1", false, managerUser);
        assertEquals(ApprovalStatus.REJECTED, pending.getStatus());
    }

    @Test
    void secondConcurrentApprovalCannotPassThePositionQuota() {
        Employee other = certified(21, "Người đã duyệt");
        WorkAvailability incoming = registration(staff, brew, ApprovalStatus.PENDING);
        incoming.setRegistrationKey("don-moi");
        WorkAvailability already = registration(other, brew, ApprovalStatus.APPROVED);
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(workAvailabilityRepository.findByRegistrationKey("don-moi")).thenReturn(List.of(incoming));
        when(shiftSlotLimitRepository.lockByShiftAndDay(1, 1)).thenReturn(Optional.of(openLimit(1)));
        when(workAvailabilityRepository.findCapacityCandidatesByShift(eq(1), any(), any(), any()))
                .thenReturn(List.of(incoming, already));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.reviewRegistration("don-moi", true, managerUser));

        assertTrue(ex.getMessage().contains("vượt tổng chỉ tiêu"));
        assertFalse(ex.getMessage().contains("vị trí"));
        assertEquals(ApprovalStatus.PENDING, incoming.getStatus());
        verify(scheduleService, never()).assignShiftFromApprovedAvailability(any(), any());
    }

    @Test
    void staffWithoutTheEmployeeCertificateCannotRegister() {
        staff.setCertificationStatus(CertificationStatus.NOTCERTIFIED);
        when(employeeRepository.findByIdWithStoreAndManager(20)).thenReturn(Optional.of(staff));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.submitNextWeekAvailability(SubmitWorkAvailabilityRequest.builder()
                        .durationCode("W1")
                        .skillId(1)
                        .selectedSlots(List.of("1:1"))
                        .build(), staffUser));

        assertTrue(ex.getMessage().contains("Bạn chưa có chứng chỉ"));
        assertFalse(ex.getMessage().contains("Pha chế"));
    }

    @Test
    void registrationStopsWhenTheShiftIsAlreadyFull() {
        Employee other = certified(21, "Đã duyệt");
        WorkAvailability approved = registration(other, brew, ApprovalStatus.APPROVED);
        when(employeeRepository.findByIdWithStoreAndManager(20)).thenReturn(Optional.of(staff));
        when(shiftRepository.findByIdAndStore_Id(1, 1)).thenReturn(Optional.of(shift));
        when(workAvailabilityRepository.findVisibleByEmployee(eq(20), any(), any())).thenReturn(List.of());
        when(shiftSlotLimitRepository.lockByShiftAndDay(1, 1)).thenReturn(Optional.of(openLimit(1)));
        when(workAvailabilityRepository.findCapacityCandidatesByShift(eq(1), any(), any(), any()))
                .thenReturn(List.of(approved));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.submitNextWeekAvailability(SubmitWorkAvailabilityRequest.builder()
                        .durationCode("W1")
                        .skillId(1)
                        .selectedSlots(List.of("1:1"))
                        .build(), staffUser));

        assertTrue(ex.getMessage().contains("đã đủ số lượng nhân viên"));
        assertFalse(ex.getMessage().contains("vị trí"));
    }

    @Test
    void staffCannotRegisterAShiftOutsideTheirStore() {
        when(employeeRepository.findByIdWithStoreAndManager(20)).thenReturn(Optional.of(staff));
        when(shiftRepository.findByIdAndStore_Id(99, 1)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.submitNextWeekAvailability(SubmitWorkAvailabilityRequest.builder()
                        .durationCode("W1")
                        .selectedSlots(List.of("99:1"))
                        .build(), staffUser));

        assertTrue(ex.getMessage().contains("không thuộc cửa hàng"));
    }

    @Test
    void overlappingScheduleStillBlocksApproval() {
        WorkAvailability pending = registration(staff, brew, ApprovalStatus.PENDING);
        pending.setRegistrationKey("don-trung");
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(workAvailabilityRepository.findByRegistrationKey("don-trung")).thenReturn(List.of(pending));
        when(shiftSlotLimitRepository.lockByShiftAndDay(1, 1)).thenReturn(Optional.of(openLimit(2)));
        when(workAvailabilityRepository.findCapacityCandidatesByShift(eq(1), any(), any(), any()))
                .thenReturn(List.of(pending));
        org.mockito.Mockito.doThrow(new BusinessException("Nhân viên đã có ca trùng giờ."))
                .when(scheduleService).assignShiftFromApprovedAvailability(any(), any());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.reviewRegistration("don-trung", true, managerUser));

        assertTrue(ex.getMessage().contains("trùng giờ"));
        assertEquals(ApprovalStatus.PENDING, pending.getStatus());
    }

    private void stubOwnedShift() {
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(shiftRepository.findByIdAndStore_Id(1, 1)).thenReturn(Optional.of(shift));
    }

    private void stubStaffCount(int count) {
        when(employeeRepository.countSchedulableStaff(1, EmployeeStatus.ACTIVE, RoleName.STAFF, 10))
                .thenReturn((long) count);
    }

    private void stubActiveSkills() {
        when(trainingSkillRepository.findByStatusWithCreator(TrainingSkillStatus.ACTIVE))
                .thenReturn(List.of(brew, cash, serve));
    }

    private void stubCertifiedHolders(int brewers, int cashiers, int servers) {
        when(employeeRepository.findSchedulableStaffIds(
                1, EmployeeStatus.ACTIVE, RoleName.STAFF, CertificationStatus.CERTIFIED, 10))
                .thenReturn(List.of(31, 32, 33, 34, 35));
    }

    private Map<Integer, String> counts(int brewers, int cashiers, int servers) {
        return Map.of(1, Integer.toString(brewers), 2, Integer.toString(cashiers), 3, Integer.toString(servers));
    }

    private ShiftSlotLimit openLimit(int maxEmployees) {
        return ShiftSlotLimit.builder()
                .id(91)
                .shift(shift)
                .dayOfWeek(1)
                .maxEmployees(maxEmployees)
                .updatedAt(VietnamTime.now())
                .build();
    }

    private ShiftSlotPositionNeed need(TrainingSkill skill, int count) {
        return ShiftSlotPositionNeed.builder()
                .id(skill.getId())
                .slotLimit(openLimit(5))
                .skill(skill)
                .requiredCount(count)
                .build();
    }

    private TrainingSkill skill(int id, String name) {
        return TrainingSkill.builder()
                .id(id)
                .skillName(name)
                .status(TrainingSkillStatus.ACTIVE)
                .build();
    }

    private Employee certified(int id, String name) {
        return Employee.builder()
                .id(id)
                .fullName(name)
                .store(store)
                .status(EmployeeStatus.ACTIVE)
                .certificationStatus(CertificationStatus.CERTIFIED)
                .build();
    }

    private WorkAvailability registration(Employee employee, TrainingSkill skill, ApprovalStatus status) {
        LocalDate from = VietnamTime.today().minusDays(1);
        return WorkAvailability.builder()
                .id(employee.getId())
                .employee(employee)
                .shift(shift)
                .skill(skill)
                .dayOfWeek(1)
                .validFrom(from)
                .validTo(from.plusMonths(2))
                .status(status)
                .build();
    }

    private User user(int id, RoleName roleName, Employee employee) {
        return User.builder()
                .id(id)
                .username("user" + id)
                .isActive(true)
                .role(Role.builder().roleName(roleName).build())
                .employee(employee)
                .build();
    }
}

package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.ApprovalStatus;
import com.example.coffee_hrm.common.enums.CertificationStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.NotificationType;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.enums.TrainingClassStatus;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.response.StaffAllocationRoster;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.EmploymentContract;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.Shift;
import com.example.coffee_hrm.entity.ShiftSlotLimit;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.TrainingClass;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.entity.WorkAvailability;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.EmploymentContractRepository;
import com.example.coffee_hrm.repository.ShiftAssignmentRepository;
import com.example.coffee_hrm.repository.ShiftRepository;
import com.example.coffee_hrm.repository.ShiftSlotLimitRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.repository.TrainingClassEnrollmentRepository;
import com.example.coffee_hrm.repository.WorkAvailabilityRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.NotificationService;
import com.example.coffee_hrm.service.StaffAllocationService;
import com.example.coffee_hrm.service.support.ApprovedLeaveCoverage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StaffAllocationServiceImplTest {

    @Mock private StoreRepository storeRepository;
    @Mock private ShiftRepository shiftRepository;
    @Mock private ShiftSlotLimitRepository shiftSlotLimitRepository;
    @Mock private WorkAvailabilityRepository workAvailabilityRepository;
    @Mock private ShiftAssignmentRepository shiftAssignmentRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private EmploymentContractRepository employmentContractRepository;
    @Mock private TrainingClassEnrollmentRepository trainingClassEnrollmentRepository;
    @Mock private ApprovedLeaveCoverage approvedLeaveCoverage;
    @Mock private com.example.coffee_hrm.service.ScheduleService scheduleService;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private StaffAllocationServiceImpl service;

    private final LocalDate today = VietnamTime.today();
    private final LocalDate monday = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    private Store store;
    private Shift morning;
    private Shift evening;
    private Employee manager;
    private final List<Employee> employees = new ArrayList<>();
    private final List<ShiftSlotLimit> limits = new ArrayList<>();
    private final List<WorkAvailability> registrations = new ArrayList<>();
    private final List<EmploymentContract> contracts = new ArrayList<>();

    @BeforeEach
    void setUp() {
        manager = Employee.builder().id(5).fullName("Quản lý").status(EmployeeStatus.ACTIVE).build();
        store = Store.builder().id(1).storeName("Coffee Cầu Giấy").manager(manager).build();
        manager.setStore(store);
        account(manager, RoleName.MANAGER);
        employees.add(manager);
        morning = Shift.builder().id(1).store(store).shiftName("Ca sáng")
                .startTime(LocalTime.of(7, 0)).endTime(LocalTime.of(12, 0)).build();
        evening = Shift.builder().id(2).store(store).shiftName("Ca tối")
                .startTime(LocalTime.of(17, 0)).endTime(LocalTime.of(22, 0)).build();
        // Mọi ô ca trong tuần đều cần 3 người: đủ chỗ cho các đề xuất trong test.
        for (int day = 1; day <= 7; day++) {
            limits.add(limit(morning, day, 3));
            limits.add(limit(evening, day, 3));
        }

        when(storeRepository.findByManager_Id(5)).thenReturn(Optional.of(store));
        when(shiftRepository.findByStore_IdOrderByStartTimeAsc(1)).thenReturn(List.of(morning, evening));
        when(shiftSlotLimitRepository.findByStoreId(1)).thenAnswer(invocation -> List.copyOf(limits));
        when(workAvailabilityRepository.findCapacityCandidatesByStore(eq(1), any(), eq(ApprovalStatus.PENDING), anyList()))
                .thenAnswer(invocation -> List.copyOf(registrations));
        when(shiftAssignmentRepository.findWeeklyAssignmentsForStore(eq(1), any(), any(), any())).thenReturn(List.of());
        when(employeeRepository.findByStoreIdWithAccount(1)).thenAnswer(invocation -> List.copyOf(employees));
        when(employmentContractRepository.findByEmployeeIds(anyCollection())).thenAnswer(invocation -> List.copyOf(contracts));
        when(employeeRepository.lockById(anyInt())).thenAnswer(invocation -> {
            Integer id = invocation.getArgument(0);
            return employees.stream().filter(e -> e.getId().equals(id)).findFirst();
        });
        when(shiftSlotLimitRepository.lockByShiftAndDay(anyInt(), anyInt())).thenReturn(Optional.empty());
        when(trainingClassEnrollmentRepository.findClassesEndingOnOrAfter(anyInt(), any(), any())).thenReturn(List.of());
    }

    @Test
    void staffWithAValidContractAreEligibleWhetherUnregisteredOrShortOfShifts() {
        Employee an = staff(11, "Nguyễn Văn An");
        contract(an, 2, today.minusMonths(1), today.plusMonths(6));
        Employee binh = staff(12, "Trần Thị Bình");
        contract(binh, 3, today.minusMonths(1), today.plusMonths(6));
        registrations.add(registration(binh, morning, 1, ApprovalStatus.APPROVED));

        StaffAllocationRoster roster = service.roster(managerUser(), monday);

        assertTrue(roster.isAllowed());
        assertEquals(2, roster.getTotal());
        assertEquals(2, roster.getEligibleCount());
        StaffAllocationRoster.Row anRow = row(roster, 11);
        assertTrue(anRow.isEligible());
        assertEquals("none", anRow.getRegistrationKey());
        assertNull(anRow.getReason());
        StaffAllocationRoster.Row binhRow = row(roster, 12);
        assertTrue(binhRow.isEligible());
        assertEquals("short", binhRow.getRegistrationKey());
        assertEquals(2, binhRow.getMissingShifts());
        assertEquals("DANG_HIEU_LUC", binhRow.getContractStatusKey());
        assertEquals("Pha chế", binhRow.getPosition());
        // Manager không nằm trong danh sách.
        assertTrue(roster.getRows().stream().noneMatch(r -> r.getEmployeeId() == 5));
    }

    @Test
    void eachIneligibleCaseShowsItsOwnReason() {
        Employee noContract = staff(21, "A Chưa hợp đồng");
        noContract.setStatus(EmployeeStatus.PENDING_CONTRACT);

        Employee notStarted = staff(22, "B Chờ hiệu lực");
        contract(notStarted, 3, monday.plusDays(14), monday.plusMonths(6));

        Employee expired = staff(23, "C Hết hạn");
        contract(expired, 3, today.minusMonths(6), today.minusDays(3));

        Employee terminatedContract = staff(24, "D Chấm dứt");
        contract(terminatedContract, 3, today.minusMonths(6), today.plusMonths(6)).setTerminatedDate(today.minusDays(2));

        Employee notRenewed = staff(25, "E Không gia hạn");
        EmploymentContract declined = contract(notRenewed, 3, today.minusMonths(6), today.minusDays(1));
        declined.setRenewalDeclinedAt(LocalDateTime.now().minusDays(10));

        Employee resigned = staff(26, "F Nghỉ việc");
        resigned.setStatus(EmployeeStatus.TERMINATED);
        contract(resigned, 3, today.minusMonths(6), today.plusMonths(6));

        Employee uncertified = staff(27, "G Chưa chứng chỉ");
        uncertified.setCertificationStatus(CertificationStatus.NOTCERTIFIED);
        contract(uncertified, 3, today.minusMonths(1), today.plusMonths(6));

        Employee full = staff(28, "H Đủ ca");
        contract(full, 1, today.minusMonths(1), today.plusMonths(6));
        registrations.add(registration(full, morning, 1, ApprovalStatus.PENDING));

        Employee inTraining = staff(29, "I Lớp đào tạo");
        contract(inTraining, 3, today.minusMonths(1), today.plusMonths(6));
        // Học 06:00–23:00 cả tuần: không còn giờ trống cho ca nào.
        when(trainingClassEnrollmentRepository.findClassesEndingOnOrAfter(eq(29), eq(TrainingClassStatus.APPROVED), any()))
                .thenReturn(List.of(TrainingClass.builder().id(1).className("Pha chế nâng cao")
                        .startDate(monday).endDate(monday.plusDays(6))
                        .startTime(LocalTime.of(6, 0)).endTime(LocalTime.of(23, 0)).build()));

        StaffAllocationRoster roster = service.roster(managerUser(), monday);

        assertEquals(0, roster.getEligibleCount());
        assertEquals(9, roster.getIneligibleCount());
        assertReason(roster, 21, StaffAllocationServiceImpl.REASON_NO_CONTRACT, "warn");
        assertReason(roster, 22, StaffAllocationServiceImpl.REASON_NOT_STARTED, "warn");
        assertReason(roster, 23, StaffAllocationServiceImpl.REASON_EXPIRED, "bad");
        assertReason(roster, 24, StaffAllocationServiceImpl.REASON_TERMINATED_CONTRACT, "bad");
        assertReason(roster, 25, StaffAllocationServiceImpl.REASON_NOT_RENEWED, "bad");
        assertReason(roster, 26, StaffAllocationServiceImpl.REASON_RESIGNED, "bad");
        assertReason(roster, 27, StaffAllocationServiceImpl.REASON_CERTIFICATE, "bad");
        assertReason(roster, 28, StaffAllocationServiceImpl.REASON_LIMIT, "bad");
        assertTrue(row(roster, 29).getReason().startsWith(StaffAllocationServiceImpl.REASON_SCHEDULE));
        // Không có hợp đồng tách biệt với có hợp đồng nhưng chưa đăng ký.
        assertEquals("na", row(roster, 21).getRegistrationKey());
        assertEquals("NONE", row(roster, 21).getContractStatusKey());
        assertEquals("CHO_HIEU_LUC", row(roster, 22).getContractStatusKey());
        assertEquals("enough", row(roster, 28).getRegistrationKey());
    }

    @Test
    void allocatingSchedulesTheMissingShiftsDirectlyWithoutAnotherApproval() {
        Employee an = staff(11, "Nguyễn Văn An");
        contract(an, 2, today.minusMonths(1), today.plusMonths(6));
        Employee binh = staff(12, "Trần Thị Bình");
        contract(binh, 3, today.minusMonths(1), today.plusMonths(6));
        registrations.add(registration(binh, morning, 1, ApprovalStatus.APPROVED));

        StaffAllocationService.Outcome outcome = service.allocate(managerUser(), monday, List.of(11, 12));

        assertEquals(2, outcome.employees());
        assertEquals(4, outcome.shifts(), "An thiếu 2 ca, Bình thiếu 2 ca");
        assertEquals(monday, outcome.weekStart());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<WorkAvailability>> saved = ArgumentCaptor.forClass(List.class);
        verify(workAvailabilityRepository, times(2)).saveAll(saved.capture());
        for (List<WorkAvailability> group : saved.getAllValues()) {
            assertEquals(1, group.stream().map(WorkAvailability::getRegistrationKey).distinct().count());
            for (WorkAvailability row : group) {
                assertEquals(ApprovalStatus.APPROVED, row.getStatus());
                assertEquals("W1", row.getDurationCode());
                assertEquals(monday, row.getValidFrom());
                assertEquals(monday.plusDays(6), row.getValidTo());
                assertEquals(StaffAllocationServiceImpl.ALLOCATION_NOTE, row.getNote());
            }
        }
        // Mỗi ca được xếp thẳng vào lịch làm việc.
        verify(scheduleService, times(4)).assignShiftFromApprovedAvailability(any(), any());
        verify(notificationService, times(2)).notifyUsers(anyList(), eq("Bạn được phân bổ ca làm việc"), anyString(),
                eq(NotificationType.WORK_AVAILABILITY_APPROVED), eq("WORK_AVAILABILITY"), anyInt());
        // Không đổi trạng thái hợp đồng hay nhân viên.
        verify(employmentContractRepository, never()).saveAll(any());
        verify(employeeRepository, never()).save(any());
    }

    @Test
    void backendRejectsIneligibleOrChangedEmployeesAndSavesNothing() {
        Employee an = staff(11, "Nguyễn Văn An");
        contract(an, 2, today.minusMonths(1), today.plusMonths(6));
        Employee noContract = staff(21, "Chưa hợp đồng");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.allocate(managerUser(), monday, List.of(11, 21)));
        assertTrue(ex.getMessage().contains("Chưa hợp đồng (Chưa có hợp đồng lao động)"), ex.getMessage());
        verify(workAvailabilityRepository, never()).saveAll(any());

        // Giữa lúc mở danh sách và lúc gửi, An đã tự đăng ký đủ ca.
        registrations.add(registration(an, morning, 1, ApprovalStatus.PENDING));
        registrations.add(registration(an, morning, 2, ApprovalStatus.PENDING));
        BusinessException changed = assertThrows(BusinessException.class,
                () -> service.allocate(managerUser(), monday, List.of(11)));
        assertTrue(changed.getMessage().contains("không còn đủ điều kiện"));
        assertTrue(changed.getMessage().contains("Đã đạt giới hạn số ca đăng ký"));
        verify(workAvailabilityRepository, never()).saveAll(any());
    }

    @Test
    void rejectsEmptySelectionAndEmployeesOfAnotherStore() {
        assertEquals(StaffAllocationServiceImpl.NOTHING_SELECTED_MESSAGE, assertThrows(BusinessException.class,
                () -> service.allocate(managerUser(), monday, List.of())).getMessage());

        Employee outsider = Employee.builder().id(99).fullName("Người ngoài")
                .store(Store.builder().id(2).build()).status(EmployeeStatus.ACTIVE).build();
        when(employeeRepository.lockById(99)).thenReturn(Optional.of(outsider));
        assertEquals(StaffAllocationServiceImpl.OTHER_STORE_EMPLOYEE_MESSAGE, assertThrows(BusinessException.class,
                () -> service.allocate(managerUser(), monday, List.of(99))).getMessage());
        verify(workAvailabilityRepository, never()).saveAll(any());
    }

    @Test
    void rejectsWhenThereAreNotEnoughOpenShiftsForEveryoneSelected() {
        limits.clear();
        limits.add(limit(morning, 1, 1));
        limits.add(limit(evening, 1, 1));
        Employee an = staff(11, "Nguyễn Văn An");
        contract(an, 2, today.minusMonths(1), today.plusMonths(6));
        Employee binh = staff(12, "Trần Thị Bình");
        contract(binh, 2, today.minusMonths(1), today.plusMonths(6));

        // Mỗi người riêng lẻ vẫn xếp đủ 2 ca, nên cả hai đủ điều kiện trong danh sách.
        assertEquals(2, service.roster(managerUser(), monday).getEligibleCount());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.allocate(managerUser(), monday, List.of(11, 12)));
        assertTrue(ex.getMessage().contains("không đủ ca còn trống"), ex.getMessage());
        verify(scheduleService, never()).assignShiftFromApprovedAvailability(any(), any());
        verify(workAvailabilityRepository, never()).saveAll(any());
    }

    @Test
    void scheduleConflictStopsTheWholeAllocation() {
        Employee an = staff(11, "Nguyễn Văn An");
        contract(an, 2, today.minusMonths(1), today.plusMonths(6));
        org.mockito.Mockito.doThrow(new BusinessException("Nhân viên đã có ca trùng giờ."))
                .when(scheduleService).assignShiftFromApprovedAvailability(any(), any());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.allocate(managerUser(), monday, List.of(11)));

        assertTrue(ex.getMessage().contains("trùng giờ"));
        assertTrue(ex.getMessage().contains("Chưa phân bổ ai"));
        verify(notificationService, never()).notifyUsers(any(), any(), any(), any(), any(), any());
    }

    @Test
    void locksEmployeesBeforeShiftSlotsLikeTheReviewFlow() {
        Employee an = staff(11, "Nguyễn Văn An");
        contract(an, 1, today.minusMonths(1), today.plusMonths(6));
        Employee binh = staff(12, "Trần Thị Bình");
        contract(binh, 1, today.minusMonths(1), today.plusMonths(6));

        service.allocate(managerUser(), monday, List.of(12, 11));

        InOrder order = inOrder(employeeRepository, shiftSlotLimitRepository, workAvailabilityRepository);
        order.verify(employeeRepository).lockById(11);
        order.verify(employeeRepository).lockById(12);
        order.verify(shiftSlotLimitRepository).lockByShiftAndDay(1, 1);
        order.verify(workAvailabilityRepository).findCapacityCandidatesByStore(eq(1), any(), any(), anyList());
    }

    @Test
    void proposalWeekIsNeverAWeekThatHasStarted() {
        LocalDate thisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        assertEquals(monday, service.roster(managerUser(), thisWeek).getWeekStart());
        assertEquals(monday.plusWeeks(2), service.roster(managerUser(), monday.plusWeeks(2)).getWeekStart());
    }

    @Test
    void onlyManagersCanUseIt() {
        Employee an = staff(11, "Nguyễn Văn An");
        AuthenticatedUser staffUser = AuthenticatedUser.from(an.getUser());
        assertThrows(BusinessException.class, () -> service.roster(staffUser, monday));
        assertThrows(BusinessException.class, () -> service.allocate(staffUser, monday, List.of(11)));
    }

    @Test
    void storeWithoutConfiguredShiftsCannotSubmit() {
        limits.clear();
        Employee an = staff(11, "Nguyễn Văn An");
        contract(an, 2, today.minusMonths(1), today.plusMonths(6));

        StaffAllocationRoster roster = service.roster(managerUser(), monday);
        assertFalse(roster.isAllowed());
        assertEquals(StaffAllocationServiceImpl.NO_LIMIT_MESSAGE, roster.getBlockedMessage());
        assertThrows(BusinessException.class, () -> service.allocate(managerUser(), monday, List.of(11)));
    }

    // ---------------------------------------------------------------- dữ liệu mẫu

    private AuthenticatedUser managerUser() {
        return AuthenticatedUser.from(manager.getUser());
    }

    private Employee staff(int id, String name) {
        Employee employee = Employee.builder().id(id).fullName(name).store(store)
                .status(EmployeeStatus.ACTIVE).certificationStatus(CertificationStatus.CERTIFIED).build();
        account(employee, RoleName.STAFF);
        employees.add(employee);
        return employee;
    }

    private void account(Employee employee, RoleName role) {
        employee.setUser(User.builder().id(100 + employee.getId()).username("u" + employee.getId()).passwordHash("hash")
                .role(Role.builder().id(role == RoleName.MANAGER ? 2 : 3).roleName(role).build())
                .employee(employee).isActive(true).build());
    }

    private EmploymentContract contract(Employee employee, int weeklyShifts, LocalDate start, LocalDate end) {
        EmploymentContract contract = EmploymentContract.builder().id(employee.getId() * 10 + contracts.size())
                .contractCode("HD-" + employee.getId()).employee(employee).startDate(start).endDate(end)
                .weeklyShiftCount(weeklyShifts).jobTitle("Pha chế").salary(new BigDecimal("25000")).build();
        contracts.add(contract);
        return contract;
    }

    private WorkAvailability registration(Employee employee, Shift shift, int day, ApprovalStatus status) {
        return WorkAvailability.builder().id(employee.getId() * 100 + shift.getId() * 10 + day)
                .employee(employee).shift(shift).dayOfWeek(day)
                .validFrom(monday).validTo(monday.plusMonths(1)).durationCode("M1")
                .registrationKey("REG-" + employee.getId()).status(status).build();
    }

    private ShiftSlotLimit limit(Shift shift, int day, int max) {
        return ShiftSlotLimit.builder().id(shift.getId() * 10 + day).shift(shift).dayOfWeek(day).maxEmployees(max).build();
    }

    private static StaffAllocationRoster.Row row(StaffAllocationRoster roster, int id) {
        return roster.getRows().stream().filter(r -> r.getEmployeeId() == id).findFirst().orElseThrow();
    }

    private static void assertReason(StaffAllocationRoster roster, int id, String reason, String tone) {
        StaffAllocationRoster.Row row = row(roster, id);
        assertFalse(row.isEligible(), row.getFullName());
        assertEquals(reason, row.getReason(), row.getFullName());
        assertEquals(tone, row.getReasonTone(), row.getFullName());
    }
}

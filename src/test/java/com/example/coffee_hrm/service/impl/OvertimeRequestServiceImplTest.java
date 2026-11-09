package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.ApprovalStatus;
import com.example.coffee_hrm.common.enums.AssignmentStatus;
import com.example.coffee_hrm.common.enums.CertificationStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.NotificationType;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.request.AssignShiftRequest;
import com.example.coffee_hrm.dto.request.CreateOvertimeRequestDto;
import com.example.coffee_hrm.dto.response.OvertimePageView;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.EmploymentContract;
import com.example.coffee_hrm.entity.OvertimeRequest;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.Shift;
import com.example.coffee_hrm.entity.ShiftAssignment;
import com.example.coffee_hrm.entity.ShiftSlotLimit;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.entity.WorkAvailability;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.EmploymentContractRepository;
import com.example.coffee_hrm.repository.OvertimeRequestRepository;
import com.example.coffee_hrm.repository.ShiftAssignmentRepository;
import com.example.coffee_hrm.repository.ShiftRepository;
import com.example.coffee_hrm.repository.ShiftSlotLimitRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.repository.UserRepository;
import com.example.coffee_hrm.repository.WorkAvailabilityRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.NotificationService;
import com.example.coffee_hrm.service.ScheduleService;
import com.example.coffee_hrm.service.support.ApprovedLeaveCoverage;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OvertimeRequestServiceImplTest {

    @Mock
    private OvertimeRequestRepository overtimeRequestRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private ShiftRepository shiftRepository;
    @Mock
    private ShiftAssignmentRepository shiftAssignmentRepository;
    @Mock
    private WorkAvailabilityRepository workAvailabilityRepository;
    @Mock
    private ShiftSlotLimitRepository shiftSlotLimitRepository;
    @Mock
    private EmploymentContractRepository employmentContractRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ApprovedLeaveCoverage approvedLeaveCoverage;
    @Mock
    private NotificationService notificationService;
    @Mock
    private ScheduleService scheduleService;

    @InjectMocks
    private OvertimeRequestServiceImpl service;

    private Store store;
    private Employee managerEmployee;
    private Employee staffEmployee;
    private User managerUser;
    private User staffUser;
    private AuthenticatedUser staffAuth;
    private AuthenticatedUser managerAuth;
    private Shift morningShift;
    private Shift afternoonShift;
    private Shift eveningShift;
    private LocalDate nextMonday;

    @BeforeEach
    void setUp() {
        managerEmployee = Employee.builder()
                .id(10)
                .fullName("Quản lý Trần")
                .status(EmployeeStatus.ACTIVE)
                .build();
        store = Store.builder()
                .id(1)
                .storeName("Coffee Quận 1")
                .manager(managerEmployee)
                .build();
        managerEmployee.setStore(store);

        staffEmployee = Employee.builder()
                .id(20)
                .fullName("Nhân viên An")
                .phone("0901234567")
                .store(store)
                .status(EmployeeStatus.ACTIVE)
                .certificationStatus(CertificationStatus.CERTIFIED)
                .build();

        managerUser = User.builder()
                .id(2)
                .username("manager1")
                .isActive(true)
                .role(Role.builder().id(2).roleName(RoleName.MANAGER).build())
                .employee(managerEmployee)
                .build();
        staffUser = User.builder()
                .id(3)
                .username("staff1")
                .isActive(true)
                .role(Role.builder().id(3).roleName(RoleName.STAFF).build())
                .employee(staffEmployee)
                .build();

        managerAuth = AuthenticatedUser.from(managerUser);
        staffAuth = AuthenticatedUser.from(staffUser);

        morningShift = Shift.builder()
                .id(1)
                .shiftName("Ca sáng")
                .store(store)
                .startTime(LocalTime.of(7, 0))
                .endTime(LocalTime.of(12, 0))
                .build();
        afternoonShift = Shift.builder()
                .id(2)
                .shiftName("Ca chiều")
                .store(store)
                .startTime(LocalTime.of(12, 0))
                .endTime(LocalTime.of(17, 0))
                .build();
        eveningShift = Shift.builder()
                .id(3)
                .shiftName("Ca tối")
                .store(store)
                .startTime(LocalTime.of(17, 0))
                .endTime(LocalTime.of(22, 0))
                .build();

        nextMonday = VietnamTime.today()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .plusWeeks(1);
    }

    @Test
    void submitRejectsWhenRegularShiftsBelowContractWeeklyQuota() {
        when(employeeRepository.findByIdWithStoreAndManager(20)).thenReturn(Optional.of(staffEmployee));
        when(shiftRepository.findByIdAndStore_Id(2, 1)).thenReturn(Optional.of(afternoonShift));
        when(employmentContractRepository.findByEmployeeId(20)).thenReturn(List.of(contract(7)));
        when(workAvailabilityRepository.findVisibleByEmployee(eq(20), any(LocalDate.class), eq(ApprovalStatus.PENDING)))
                .thenReturn(regularAvailabilities(5));

        CreateOvertimeRequestDto dto = CreateOvertimeRequestDto.builder()
                .shiftId(2)
                .workDate(nextMonday)
                .reason("Xin làm thêm ca chiều")
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.submitOvertimeRequest(dto, staffAuth));

        assertTrue(ex.getMessage().contains("mới đăng ký 5/7 ca theo hợp đồng"));
        verify(overtimeRequestRepository, never()).save(any());
    }

    @Test
    void submitSucceedsWhenContractQuotaReachedAndNotifiesManager() {
        when(employeeRepository.findByIdWithStoreAndManager(20)).thenReturn(Optional.of(staffEmployee));
        when(shiftRepository.findByIdAndStore_Id(2, 1)).thenReturn(Optional.of(afternoonShift));
        when(employmentContractRepository.findByEmployeeId(20)).thenReturn(List.of(contract(7)));
        when(workAvailabilityRepository.findVisibleByEmployee(eq(20), any(LocalDate.class), eq(ApprovalStatus.PENDING)))
                .thenReturn(regularAvailabilities(7));
        when(overtimeRequestRepository.save(any(OvertimeRequest.class))).thenAnswer(inv -> {
            OvertimeRequest r = inv.getArgument(0);
            r.setId(99);
            return r;
        });
        when(userRepository.findByEmployee_Id(10)).thenReturn(Optional.of(managerUser));

        CreateOvertimeRequestDto dto = CreateOvertimeRequestDto.builder()
                .shiftId(2)
                .workDate(nextMonday)
                .reason("Xin tăng ca hỗ trợ quán cuối tuần")
                .build();

        service.submitOvertimeRequest(dto, staffAuth);

        ArgumentCaptor<OvertimeRequest> captor = ArgumentCaptor.forClass(OvertimeRequest.class);
        verify(overtimeRequestRepository).save(captor.capture());
        OvertimeRequest saved = captor.getValue();
        assertEquals(7, saved.getContractWeeklyQuota());
        assertEquals(ApprovalStatus.PENDING, saved.getStatus());
        assertEquals("Xin tăng ca hỗ trợ quán cuối tuần", saved.getReason());
        assertEquals(nextMonday, saved.getWorkDate());
        verify(notificationService).notifyUsers(
                eq(List.of(managerUser)),
                eq("Đơn xin tăng ca mới"),
                any(),
                eq(NotificationType.OVERTIME_REQUEST_SUBMITTED),
                eq("OVERTIME_REQUEST"),
                eq(99));
    }

    @Test
    void submitRejectsWhenSlotCapacityIsFull() {
        when(employeeRepository.findByIdWithStoreAndManager(20)).thenReturn(Optional.of(staffEmployee));
        when(shiftRepository.findByIdAndStore_Id(2, 1)).thenReturn(Optional.of(afternoonShift));
        when(employmentContractRepository.findByEmployeeId(20)).thenReturn(List.of(contract(7)));
        when(workAvailabilityRepository.findVisibleByEmployee(eq(20), any(LocalDate.class), eq(ApprovalStatus.PENDING)))
                .thenReturn(regularAvailabilities(7));
        when(shiftSlotLimitRepository.findByShift_IdAndDayOfWeek(2, 1))
                .thenReturn(Optional.of(ShiftSlotLimit.builder().shift(afternoonShift).dayOfWeek(1).maxEmployees(1).build()));
        Employee otherEmp = Employee.builder().id(21).fullName("Bình").store(store).build();
        when(shiftAssignmentRepository.findWeeklyAssignmentsForStore(1, nextMonday, nextMonday, AssignmentStatus.ASSIGNED))
                .thenReturn(List.of(ShiftAssignment.builder()
                        .id(500)
                        .employee(otherEmp)
                        .shift(afternoonShift)
                        .workDate(nextMonday)
                        .status(AssignmentStatus.ASSIGNED)
                        .build()));

        CreateOvertimeRequestDto dto = CreateOvertimeRequestDto.builder()
                .shiftId(2)
                .workDate(nextMonday)
                .reason("Xin tăng ca")
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.submitOvertimeRequest(dto, staffAuth));

        assertTrue(ex.getMessage().contains("đã đủ số lượng nhân viên tối đa"));
        verify(overtimeRequestRepository, never()).save(any());
    }

    @Test
    void submitRejectsThirdShiftOnSameDay() {
        when(employeeRepository.findByIdWithStoreAndManager(20)).thenReturn(Optional.of(staffEmployee));
        when(shiftRepository.findByIdAndStore_Id(3, 1)).thenReturn(Optional.of(eveningShift));
        when(employmentContractRepository.findByEmployeeId(20)).thenReturn(List.of(contract(7)));
        when(workAvailabilityRepository.findVisibleByEmployee(eq(20), any(LocalDate.class), eq(ApprovalStatus.PENDING)))
                .thenReturn(regularAvailabilities(7));
        OvertimeRequest existingOtOnMonday = OvertimeRequest.builder()
                .id(50)
                .employee(staffEmployee)
                .store(store)
                .shift(afternoonShift)
                .workDate(nextMonday)
                .status(ApprovalStatus.PENDING)
                .build();
        when(overtimeRequestRepository.findByEmployeeAndDateRangeAndStatuses(
                eq(20), eq(nextMonday), eq(nextMonday.plusDays(6)), any()))
                .thenReturn(List.of(existingOtOnMonday));

        CreateOvertimeRequestDto dto = CreateOvertimeRequestDto.builder()
                .shiftId(3)
                .workDate(nextMonday)
                .reason("Xin làm ca thứ 3 trong ngày")
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.submitOvertimeRequest(dto, staffAuth));

        assertTrue(ex.getMessage().contains("tối đa 2 ca"));
        verify(overtimeRequestRepository, never()).save(any());
    }

    @Test
    void managerApproveAssignsShiftAndNotifiesStaff() {
        OvertimeRequest pending = OvertimeRequest.builder()
                .id(77)
                .employee(staffEmployee)
                .store(store)
                .shift(afternoonShift)
                .workDate(nextMonday)
                .contractWeeklyQuota(7)
                .reason("Hỗ trợ cuối tuần")
                .status(ApprovalStatus.PENDING)
                .build();
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(overtimeRequestRepository.findByIdWithDetails(77)).thenReturn(Optional.of(pending));
        when(userRepository.findById(2)).thenReturn(Optional.of(managerUser));
        when(shiftAssignmentRepository.findByEmployee_IdAndWorkDateAndStatus(20, nextMonday, AssignmentStatus.ASSIGNED))
                .thenReturn(List.of(ShiftAssignment.builder()
                        .id(888)
                        .employee(staffEmployee)
                        .shift(afternoonShift)
                        .workDate(nextMonday)
                        .status(AssignmentStatus.ASSIGNED)
                        .build()));
        when(userRepository.findByEmployee_Id(20)).thenReturn(Optional.of(staffUser));

        service.reviewOvertimeRequest(77, true, "Đồng ý duyệt tăng ca", managerAuth);

        verify(scheduleService).assignShift(any(AssignShiftRequest.class), eq(managerAuth));
        assertEquals(ApprovalStatus.APPROVED, pending.getStatus());
        assertEquals(888, pending.getCreatedAssignmentId());
        assertEquals("Đồng ý duyệt tăng ca", pending.getReviewNote());
        assertNotNull(pending.getReviewedAt());
        verify(notificationService).notifyUsers(
                eq(List.of(staffUser)),
                eq("Đơn xin tăng ca đã được duyệt"),
                any(),
                eq(NotificationType.OVERTIME_REQUEST_APPROVED),
                eq("OVERTIME_REQUEST"),
                eq(77));
    }

    @Test
    void managerRejectMarksRejectedWithoutAssigningShift() {
        OvertimeRequest pending = OvertimeRequest.builder()
                .id(78)
                .employee(staffEmployee)
                .store(store)
                .shift(afternoonShift)
                .workDate(nextMonday)
                .contractWeeklyQuota(7)
                .reason("Hỗ trợ cuối tuần")
                .status(ApprovalStatus.PENDING)
                .build();
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(overtimeRequestRepository.findByIdWithDetails(78)).thenReturn(Optional.of(pending));
        when(userRepository.findById(2)).thenReturn(Optional.of(managerUser));
        when(userRepository.findByEmployee_Id(20)).thenReturn(Optional.of(staffUser));

        service.reviewOvertimeRequest(78, false, "Ca này đã đủ người", managerAuth);

        verify(scheduleService, never()).assignShift(any(), any());
        assertEquals(ApprovalStatus.REJECTED, pending.getStatus());
        assertEquals("Ca này đã đủ người", pending.getReviewNote());
        verify(notificationService).notifyUsers(
                eq(List.of(staffUser)),
                eq("Đơn xin tăng ca bị từ chối"),
                any(),
                eq(NotificationType.OVERTIME_REQUEST_REJECTED),
                eq("OVERTIME_REQUEST"),
                eq(78));
    }

    @Test
    void staffPageReflectsQuotaReachedAndSelectableOvertimeSlots() {
        when(employeeRepository.findByIdWithStore(20)).thenReturn(Optional.of(staffEmployee));
        when(employmentContractRepository.findByEmployeeId(20)).thenReturn(List.of(contract(7)));
        when(shiftRepository.findByStore_IdOrderByStartTimeAsc(1)).thenReturn(List.of(morningShift, afternoonShift));
        when(workAvailabilityRepository.findVisibleByEmployee(eq(20), any(LocalDate.class), eq(ApprovalStatus.PENDING)))
                .thenReturn(regularAvailabilities(7));
        when(overtimeRequestRepository.findAllByEmployeeId(20)).thenReturn(List.of());

        OvertimePageView view = service.getStaffOvertimePage(staffAuth, nextMonday);

        assertEquals(7, view.getContractWeeklyQuota());
        assertEquals(7, view.getRegularShiftsInWeek());
        assertTrue(view.isQuotaReached());
        assertTrue(view.isCanSubmitOvertimeInWeek());
        assertEquals(2, view.getShiftRows().size());
        assertFalse(view.getShiftRows().get(0).getCells().get(0).isSelectable());
        assertTrue(view.getShiftRows().get(1).getCells().get(0).isSelectable());
    }

    private EmploymentContract contract(int weeklyShiftCount) {
        return EmploymentContract.builder()
                .id(1)
                .contractCode("HD-001")
                .employee(staffEmployee)
                .startDate(nextMonday.minusMonths(2))
                .endDate(nextMonday.plusMonths(6))
                .weeklyShiftCount(weeklyShiftCount)
                .build();
    }

    private List<WorkAvailability> regularAvailabilities(int count) {
        List<WorkAvailability> list = new ArrayList<>();
        for (int day = 1; day <= count; day++) {
            list.add(WorkAvailability.builder()
                    .id(100 + day)
                    .employee(staffEmployee)
                    .shift(morningShift)
                    .dayOfWeek(day)
                    .validFrom(nextMonday)
                    .validTo(nextMonday.plusDays(6))
                    .status(ApprovalStatus.APPROVED)
                    .build());
        }
        return list;
    }
}


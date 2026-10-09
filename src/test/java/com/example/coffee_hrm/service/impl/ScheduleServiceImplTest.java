package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.ApprovalStatus;
import com.example.coffee_hrm.common.enums.AssignmentStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.request.AssignShiftRequest;
import com.example.coffee_hrm.dto.request.CreateShiftChangeRequestDto;
import com.example.coffee_hrm.dto.response.WeeklyScheduleView;
import com.example.coffee_hrm.entity.*;
import com.example.coffee_hrm.repository.*;
import com.example.coffee_hrm.security.AuthenticatedUser;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceImplTest {

    @Mock
    private StoreRepository storeRepository;
    @Mock
    private ShiftRepository shiftRepository;
    @Mock
    private ShiftAssignmentRepository shiftAssignmentRepository;
    @Mock
    private ShiftChangeRequestRepository shiftChangeRequestRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private WorkAvailabilityRepository workAvailabilityRepository;

    @InjectMocks
    private ScheduleServiceImpl scheduleService;

    private AuthenticatedUser managerUser;
    private AuthenticatedUser staffUser;
    private Store store;
    private Shift shiftMorning;
    private Employee empManager;
    private Employee empTuan;
    private Employee empThu;

    @BeforeEach
    void setUp() {
        store = Store.builder().id(1).storeName("Store 1").build();

        empManager = Employee.builder().id(10).fullName("Manager A").store(store).status(EmployeeStatus.ACTIVE).build();
        empTuan = Employee.builder().id(20).fullName("Trần Văn Tuấn").store(store).status(EmployeeStatus.ACTIVE).build();
        empThu = Employee.builder().id(30).fullName("Lê Thị Thu").store(store).status(EmployeeStatus.ACTIVE).build();

        managerUser = AuthenticatedUser.from(buildUser(2, "manager1", RoleName.MANAGER, empManager));
        staffUser = AuthenticatedUser.from(buildUser(3, "tuanth", RoleName.STAFF, empTuan));

        shiftMorning = Shift.builder()
                .id(1)
                .shiftName("Ca Sáng")
                .store(store)
                .startTime(LocalTime.of(7, 0))
                .endTime(LocalTime.of(12, 0))
                .build();
    }

    @Test
    void assignShiftSuccess() {
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(shiftRepository.findByIdAndStore_Id(1, 1)).thenReturn(Optional.of(shiftMorning));
        when(employeeRepository.findById(20)).thenReturn(Optional.of(empTuan));
        LocalDate workDate = VietnamTime.today().plusDays(1);
        when(shiftAssignmentRepository.existsByEmployee_IdAndShift_IdAndWorkDateAndStatus(
                20, 1, workDate, AssignmentStatus.ASSIGNED)).thenReturn(false);
        when(shiftAssignmentRepository.findByEmployee_IdAndWorkDateAndStatus(
                20, workDate, AssignmentStatus.ASSIGNED)).thenReturn(List.of());

        AssignShiftRequest request = AssignShiftRequest.builder()
                .shiftId(1)
                .employeeId(20)
                .workDate(workDate)
                .build();

        scheduleService.assignShift(request, managerUser);

        ArgumentCaptor<ShiftAssignment> captor = ArgumentCaptor.forClass(ShiftAssignment.class);
        verify(shiftAssignmentRepository).save(captor.capture());

        ShiftAssignment saved = captor.getValue();
        assertEquals(AssignmentStatus.ASSIGNED, saved.getStatus());
        assertTrue(saved.getIsPublished());
        assertNotNull(saved.getPublishedAt());
        assertEquals(empTuan, saved.getEmployee());
        assertEquals(shiftMorning, saved.getShift());
    }

    @Test
    void assignShiftRejectsPastStart() {
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(shiftRepository.findByIdAndStore_Id(1, 1)).thenReturn(Optional.of(shiftMorning));
        when(employeeRepository.findById(20)).thenReturn(Optional.of(empTuan));

        AssignShiftRequest request = AssignShiftRequest.builder()
                .shiftId(1)
                .employeeId(20)
                .workDate(VietnamTime.today().minusDays(1))
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> scheduleService.assignShift(request, managerUser));
        assertEquals("Không thể phân ca cho thời gian đã qua.", ex.getMessage());
        verify(shiftAssignmentRepository, never()).save(any());
    }

    @Test
    void assignShiftRejectsDuplicate() {
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(shiftRepository.findByIdAndStore_Id(1, 1)).thenReturn(Optional.of(shiftMorning));
        when(employeeRepository.findById(20)).thenReturn(Optional.of(empTuan));
        LocalDate workDate = VietnamTime.today().plusDays(1);
        when(shiftAssignmentRepository.existsByEmployee_IdAndShift_IdAndWorkDateAndStatus(
                20, 1, workDate, AssignmentStatus.ASSIGNED)).thenReturn(true);

        AssignShiftRequest request = AssignShiftRequest.builder()
                .shiftId(1)
                .employeeId(20)
                .workDate(workDate)
                .build();

        BusinessException ex = assertThrows(BusinessException.class, () -> scheduleService.assignShift(request, managerUser));
        assertTrue(ex.getMessage().contains("đã được phân công vào ca này"));
        verify(shiftAssignmentRepository, never()).save(any());
    }

    @Test
    void assignShiftFromApprovedAvailabilitySkipsDuplicate() {
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(shiftRepository.findByIdAndStore_Id(1, 1)).thenReturn(Optional.of(shiftMorning));
        when(employeeRepository.findById(20)).thenReturn(Optional.of(empTuan));
        LocalDate workDate = VietnamTime.today().plusDays(1);
        when(shiftAssignmentRepository.existsByEmployee_IdAndShift_IdAndWorkDateAndStatus(
                20, 1, workDate, AssignmentStatus.ASSIGNED)).thenReturn(true);

        AssignShiftRequest request = AssignShiftRequest.builder()
                .shiftId(1)
                .employeeId(20)
                .workDate(workDate)
                .build();

        scheduleService.assignShiftFromApprovedAvailability(request, managerUser);

        verify(shiftAssignmentRepository, never()).save(any());
    }

    @Test
    void assignShiftFromApprovedAvailabilityRejectsOverlap() {
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(shiftRepository.findByIdAndStore_Id(1, 1)).thenReturn(Optional.of(shiftMorning));
        when(employeeRepository.findById(20)).thenReturn(Optional.of(empTuan));
        LocalDate workDate = VietnamTime.today().plusDays(1);
        when(shiftAssignmentRepository.existsByEmployee_IdAndShift_IdAndWorkDateAndStatus(
                20, 1, workDate, AssignmentStatus.ASSIGNED)).thenReturn(false);

        Shift overlapping = Shift.builder()
                .id(2)
                .shiftName("Ca trưa")
                .store(store)
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(14, 0))
                .build();
        ShiftAssignment existing = ShiftAssignment.builder()
                .id(8)
                .shift(overlapping)
                .employee(empTuan)
                .workDate(workDate)
                .status(AssignmentStatus.ASSIGNED)
                .build();
        when(shiftAssignmentRepository.findByEmployee_IdAndWorkDateAndStatus(
                20, workDate, AssignmentStatus.ASSIGNED)).thenReturn(List.of(existing));

        AssignShiftRequest request = AssignShiftRequest.builder()
                .shiftId(1)
                .employeeId(20)
                .workDate(workDate)
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> scheduleService.assignShiftFromApprovedAvailability(request, managerUser));
        assertTrue(ex.getMessage().contains("trùng giờ"));
        verify(shiftAssignmentRepository, never()).save(any());
    }

    @Test
    void requestShiftChangeRejectsShiftThatAlreadyStarted() {
        when(employeeRepository.findByIdWithStore(20)).thenReturn(Optional.of(empTuan));

        ShiftAssignment pastAssignment = ShiftAssignment.builder()
                .id(100)
                .employee(empTuan)
                .shift(shiftMorning)
                .isPublished(true)
                .workDate(VietnamTime.today().minusDays(1))
                .build();
        when(shiftAssignmentRepository.findByIdWithDetails(100)).thenReturn(Optional.of(pastAssignment));

        CreateShiftChangeRequestDto dto = CreateShiftChangeRequestDto.builder()
                .assignmentId(100)
                .reason("Bận việc")
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> scheduleService.requestShiftChange(dto, staffUser));
        assertEquals("Không thể gửi yêu cầu đổi cho ca làm việc đã qua.", ex.getMessage());
        verify(shiftChangeRequestRepository, never()).save(any());
    }

    @Test
    void resolveShiftChangeSwapModeSwapsBothAssignments() {
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        User mgr = buildUser(2, "manager1", RoleName.MANAGER, empManager);
        when(userRepository.findById(2)).thenReturn(Optional.of(mgr));

        ShiftAssignment assignmentA = ShiftAssignment.builder()
                .id(100)
                .employee(empTuan)
                .build();

        ShiftAssignment assignmentB = ShiftAssignment.builder()
                .id(101)
                .employee(empThu)
                .build();

        ShiftChangeRequest pendingSwap = ShiftChangeRequest.builder()
                .id(5)
                .assignment(assignmentA)
                .targetAssignment(assignmentB)
                .employee(empTuan)
                .targetEmployee(empThu)
                .status(ApprovalStatus.PENDING)
                .reason("[Đổi chéo ca] Bận sáng thứ Ba")
                .build();

        when(shiftChangeRequestRepository.findByIdWithDetails(5)).thenReturn(Optional.of(pendingSwap));

        scheduleService.resolveShiftChange(5, true, null, managerUser);

        assertEquals(ApprovalStatus.APPROVED, pendingSwap.getStatus());
        assertEquals(mgr, pendingSwap.getResolvedBy());
        // Both assignments must be swapped!
        assertEquals(empThu, assignmentA.getEmployee());
        assertEquals(empTuan, assignmentB.getEmployee());
        verify(shiftAssignmentRepository).save(assignmentA);
        verify(shiftAssignmentRepository).save(assignmentB);
        verify(shiftChangeRequestRepository).save(pendingSwap);
    }

    @Test
    void resolveShiftChangeManagerAssignRequiresReplacementEmployee() {
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        User mgr = buildUser(2, "manager1", RoleName.MANAGER, empManager);
        when(userRepository.findById(2)).thenReturn(Optional.of(mgr));

        ShiftAssignment assignment = ShiftAssignment.builder()
                .id(100)
                .employee(empTuan)
                .shift(shiftMorning)
                .workDate(VietnamTime.today().plusDays(1))
                .build();

        ShiftChangeRequest pending = ShiftChangeRequest.builder()
                .id(6)
                .assignment(assignment)
                .employee(empTuan)
                .status(ApprovalStatus.PENDING)
                .reason("[Nhờ Quản lý sắp xếp người thay thế] Việc gấp")
                .build();

        when(shiftChangeRequestRepository.findByIdWithDetails(6)).thenReturn(Optional.of(pending));

        // When manager does not provide replacement employee
        BusinessException ex = assertThrows(BusinessException.class,
                () -> scheduleService.resolveShiftChange(6, true, null, managerUser));
        assertEquals("Vui lòng chọn nhân viên thay thế vào ca này trước khi duyệt.", ex.getMessage());

        // When manager provides replacement employee (empThu ID: 30)
        when(employeeRepository.findById(30)).thenReturn(Optional.of(empThu));
        scheduleService.resolveShiftChange(6, true, 30, managerUser);

        assertEquals(ApprovalStatus.APPROVED, pending.getStatus());
        assertEquals(empThu, assignment.getEmployee()); // Original employee replaced by Thu!
        verify(shiftAssignmentRepository).save(assignment);
        verify(shiftChangeRequestRepository).save(pending);
    }

    @Test
    void respondToIncomingShiftChangeAgreed() {
        AuthenticatedUser thuUser = AuthenticatedUser.from(buildUser(4, "staff2", RoleName.STAFF, empThu));
        when(employeeRepository.findByIdWithStore(30)).thenReturn(Optional.of(empThu));

        ShiftAssignment assignmentA = ShiftAssignment.builder().id(100).employee(empTuan).build();
        ShiftAssignment assignmentB = ShiftAssignment.builder().id(101).employee(empThu).build();

        ShiftChangeRequest pendingRequest = ShiftChangeRequest.builder()
                .id(7)
                .assignment(assignmentA)
                .targetAssignment(assignmentB)
                .employee(empTuan)
                .targetEmployee(empThu)
                .status(ApprovalStatus.PENDING)
                .isTargetAgreed(null)
                .reason("[Đổi chéo ca] Em bận việc")
                .build();

        when(shiftChangeRequestRepository.findByIdWithDetails(7)).thenReturn(Optional.of(pendingRequest));

        scheduleService.respondToIncomingShiftChange(7, true, thuUser);

        assertTrue(pendingRequest.getIsTargetAgreed());
        assertNotNull(pendingRequest.getTargetAgreedAt());
        assertEquals(ApprovalStatus.PENDING, pendingRequest.getStatus()); // Stays pending for Store Manager approval
        verify(shiftChangeRequestRepository).save(pendingRequest);
    }

    @Test
    void respondToIncomingShiftChangeDeclined() {
        AuthenticatedUser thuUser = AuthenticatedUser.from(buildUser(4, "staff2", RoleName.STAFF, empThu));
        when(employeeRepository.findByIdWithStore(30)).thenReturn(Optional.of(empThu));

        ShiftAssignment assignmentA = ShiftAssignment.builder().id(100).employee(empTuan).build();
        ShiftAssignment assignmentB = ShiftAssignment.builder().id(101).employee(empThu).build();

        ShiftChangeRequest pendingRequest = ShiftChangeRequest.builder()
                .id(8)
                .assignment(assignmentA)
                .targetAssignment(assignmentB)
                .employee(empTuan)
                .targetEmployee(empThu)
                .status(ApprovalStatus.PENDING)
                .isTargetAgreed(null)
                .reason("[Đổi chéo ca] Em bận việc")
                .build();

        when(shiftChangeRequestRepository.findByIdWithDetails(8)).thenReturn(Optional.of(pendingRequest));

        scheduleService.respondToIncomingShiftChange(8, false, thuUser);

        assertFalse(pendingRequest.getIsTargetAgreed());
        assertNotNull(pendingRequest.getTargetAgreedAt());
        assertEquals(ApprovalStatus.REJECTED, pendingRequest.getStatus()); // Automatically rejected
        assertTrue(pendingRequest.getReason().contains("Đồng nghiệp từ chối"));
        verify(shiftChangeRequestRepository).save(pendingRequest);
    }

    @Test
    void respondToIncomingShiftChangeUnauthorized() {
        Employee empOther = Employee.builder().id(40).fullName("Nguyễn Văn C").store(store).build();
        AuthenticatedUser unauthorizedUser = AuthenticatedUser.from(buildUser(5, "staff3", RoleName.STAFF, empOther));
        when(employeeRepository.findByIdWithStore(40)).thenReturn(Optional.of(empOther));

        ShiftChangeRequest pendingRequest = ShiftChangeRequest.builder()
                .id(9)
                .employee(empTuan)
                .targetEmployee(empThu) // Only empThu can respond
                .status(ApprovalStatus.PENDING)
                .isTargetAgreed(null)
                .build();

        when(shiftChangeRequestRepository.findByIdWithDetails(9)).thenReturn(Optional.of(pendingRequest));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> scheduleService.respondToIncomingShiftChange(9, true, unauthorizedUser));
        assertEquals("Bạn không phải là người nhận trong yêu cầu đổi ca này.", ex.getMessage());
        verify(shiftChangeRequestRepository, never()).save(any());
    }

    @Test
    void weeklyScheduleAppliesApprovedRegistrationAsShift() {
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(shiftRepository.findByStore_IdOrderByStartTimeAsc(1)).thenReturn(List.of(shiftMorning));
        LocalDate monday = LocalDate.of(2026, 10, 12);
        WorkAvailability covering = WorkAvailability.builder()
                .id(1)
                .employee(empTuan)
                .shift(shiftMorning)
                .dayOfWeek(1)
                .validFrom(monday)
                .validTo(LocalDate.of(2026, 11, 11))
                .status(ApprovalStatus.APPROVED)
                .build();
        WorkAvailability notYetStarted = WorkAvailability.builder()
                .id(2)
                .employee(empThu)
                .shift(shiftMorning)
                .dayOfWeek(1)
                .validFrom(LocalDate.of(2026, 11, 16))
                .validTo(LocalDate.of(2026, 12, 16))
                .status(ApprovalStatus.APPROVED)
                .build();
        when(workAvailabilityRepository.findApprovedCovering(eq(1), eq(monday), eq(monday.plusDays(6)), eq(ApprovalStatus.APPROVED)))
                .thenReturn(List.of(covering, notYetStarted));

        WeeklyScheduleView view = scheduleService.getWeeklyScheduleForManager(managerUser, LocalDate.of(2026, 10, 14));

        WeeklyScheduleView.AssignmentItem mondayShift = view.getShiftRows().getFirst().getDayCells().getFirst().getAssignments().getFirst();
        assertEquals("Trần Văn Tuấn", mondayShift.getEmployeeName());
        assertTrue(mondayShift.isAppliedFromRegistration());
        assertFalse(mondayShift.isCanCancel());
        assertNull(view.getShiftRows().getFirst().getDayCells().getFirst().getAvailableEmployeesLabel());
        assertTrue(view.getShiftRows().getFirst().getDayCells().get(1).getAssignments().isEmpty());
        verify(shiftAssignmentRepository, never()).save(any());
    }

    @Test
    void staffScheduleShowsApprovedRegistrationInFutureWeek() {
        when(employeeRepository.findByIdWithStore(20)).thenReturn(Optional.of(empTuan));
        when(shiftRepository.findByStore_IdOrderByStartTimeAsc(1)).thenReturn(List.of(shiftMorning));
        LocalDate monday = LocalDate.of(2026, 10, 12);
        WorkAvailability covering = WorkAvailability.builder()
                .id(1)
                .employee(empTuan)
                .shift(shiftMorning)
                .dayOfWeek(1)
                .validFrom(monday)
                .validTo(LocalDate.of(2026, 11, 11))
                .status(ApprovalStatus.APPROVED)
                .build();
        when(workAvailabilityRepository.findApprovedCovering(eq(1), eq(monday), eq(monday.plusDays(6)), eq(ApprovalStatus.APPROVED)))
                .thenReturn(List.of(covering));

        WeeklyScheduleView view = scheduleService.getWeeklyScheduleForStaff(staffUser, LocalDate.of(2026, 10, 14));

        WeeklyScheduleView.AssignmentItem mondayShift = view.getShiftRows().getFirst().getDayCells().getFirst().getAssignments().getFirst();
        assertEquals("Trần Văn Tuấn", mondayShift.getEmployeeName());
        assertTrue(mondayShift.isCurrentStaff());
        assertTrue(mondayShift.isAppliedFromRegistration());
        assertFalse(mondayShift.isCanRequestChange());
        assertTrue(view.getShiftRows().getFirst().getDayCells().get(1).getAssignments().isEmpty());
    }

    private User buildUser(Integer id, String username, RoleName roleName, Employee employee) {
        Role role = Role.builder().id(roleName == RoleName.ADMIN ? 1 : (roleName == RoleName.MANAGER ? 2 : 3)).roleName(roleName).build();
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

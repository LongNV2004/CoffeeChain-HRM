package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.ApprovalStatus;
import com.example.coffee_hrm.common.enums.AssignmentStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.request.AssignShiftRequest;
import com.example.coffee_hrm.dto.request.CreateShiftChangeRequestDto;
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
        when(shiftAssignmentRepository.existsByEmployee_IdAndShift_IdAndWorkDateAndStatus(
                20, 1, LocalDate.of(2026, 9, 21), AssignmentStatus.ASSIGNED)).thenReturn(false);
        when(shiftAssignmentRepository.findByEmployee_IdAndWorkDateAndStatus(
                20, LocalDate.of(2026, 9, 21), AssignmentStatus.ASSIGNED)).thenReturn(List.of());

        AssignShiftRequest request = AssignShiftRequest.builder()
                .shiftId(1)
                .employeeId(20)
                .workDate(LocalDate.of(2026, 9, 21))
                .build();

        scheduleService.assignShift(request, managerUser);

        ArgumentCaptor<ShiftAssignment> captor = ArgumentCaptor.forClass(ShiftAssignment.class);
        verify(shiftAssignmentRepository).save(captor.capture());

        ShiftAssignment saved = captor.getValue();
        assertEquals(AssignmentStatus.ASSIGNED, saved.getStatus());
        assertFalse(saved.getIsPublished());
        assertEquals(empTuan, saved.getEmployee());
        assertEquals(shiftMorning, saved.getShift());
    }

    @Test
    void assignShiftRejectsDuplicate() {
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));
        when(shiftRepository.findByIdAndStore_Id(1, 1)).thenReturn(Optional.of(shiftMorning));
        when(employeeRepository.findById(20)).thenReturn(Optional.of(empTuan));
        when(shiftAssignmentRepository.existsByEmployee_IdAndShift_IdAndWorkDateAndStatus(
                20, 1, LocalDate.of(2026, 9, 21), AssignmentStatus.ASSIGNED)).thenReturn(true);

        AssignShiftRequest request = AssignShiftRequest.builder()
                .shiftId(1)
                .employeeId(20)
                .workDate(LocalDate.of(2026, 9, 21))
                .build();

        BusinessException ex = assertThrows(BusinessException.class, () -> scheduleService.assignShift(request, managerUser));
        assertTrue(ex.getMessage().contains("đã được phân công vào ca này"));
        verify(shiftAssignmentRepository, never()).save(any());
    }

    @Test
    void publishWeeklyScheduleSuccess() {
        when(storeRepository.findByManager_Id(10)).thenReturn(Optional.of(store));

        ShiftAssignment sa1 = ShiftAssignment.builder().id(1).isPublished(false).build();
        ShiftAssignment sa2 = ShiftAssignment.builder().id(2).isPublished(false).build();
        when(shiftAssignmentRepository.findAssignmentsToPublish(eq(1), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(sa1, sa2));

        scheduleService.publishWeeklySchedule(LocalDate.of(2026, 9, 21), managerUser);

        assertTrue(sa1.getIsPublished());
        assertNotNull(sa1.getPublishedAt());
        assertTrue(sa2.getIsPublished());
        assertNotNull(sa2.getPublishedAt());
        verify(shiftAssignmentRepository).saveAll(List.of(sa1, sa2));
    }

    @Test
    void requestShiftChangeRejectsUnpublishedShift() {
        when(employeeRepository.findByIdWithStore(20)).thenReturn(Optional.of(empTuan));

        ShiftAssignment draftAssignment = ShiftAssignment.builder()
                .id(100)
                .employee(empTuan)
                .isPublished(false)
                .workDate(LocalDate.now().plusDays(1))
                .build();

        when(shiftAssignmentRepository.findByIdWithDetails(100)).thenReturn(Optional.of(draftAssignment));

        CreateShiftChangeRequestDto dto = CreateShiftChangeRequestDto.builder()
                .assignmentId(100)
                .reason("Bận việc")
                .build();

        BusinessException ex = assertThrows(BusinessException.class, () -> scheduleService.requestShiftChange(dto, staffUser));
        assertEquals("Ca làm việc này chưa được quản lý công bố, không thể yêu cầu đổi ca.", ex.getMessage());
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

package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.ApprovalStatus;
import com.example.coffee_hrm.common.enums.AssignmentStatus;
import com.example.coffee_hrm.common.enums.CertificationStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.ReliabilityActionType;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.response.ReliabilityLeaderboardView;
import com.example.coffee_hrm.entity.Attendance;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.ReliabilityScoreLog;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.Shift;
import com.example.coffee_hrm.entity.ShiftAssignment;
import com.example.coffee_hrm.entity.ShiftChangeRequest;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.repository.ReliabilityScoreLogRepository;
import com.example.coffee_hrm.repository.ShiftAssignmentRepository;
import com.example.coffee_hrm.repository.ShiftChangeRequestRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReliabilityServiceImplTest {

    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private ShiftChangeRequestRepository shiftChangeRequestRepository;
    @Mock
    private ShiftAssignmentRepository shiftAssignmentRepository;
    @Mock
    private ReliabilityScoreLogRepository reliabilityScoreLogRepository;

    @InjectMocks
    private ReliabilityServiceImpl reliabilityService;

    private Store store;
    private Employee empManager;
    private Employee empRequester;
    private Employee empRescuer;
    private AuthenticatedUser managerUser;
    private AuthenticatedUser staffUser;

    @BeforeEach
    void setUp() {
        empManager = Employee.builder()
                .id(1)
                .fullName("Quản lý Cửa hàng")
                .status(EmployeeStatus.ACTIVE)
                .certificationStatus(CertificationStatus.CERTIFIED)
                .reliabilityScore(100)
                .build();
        store = Store.builder()
                .id(10)
                .storeName("CoffeeHRM Cầu Giấy")
                .address("123 Cầu Giấy")
                .manager(empManager)
                .build();
        empManager.setStore(store);

        empRequester = Employee.builder()
                .id(20)
                .fullName("Nguyễn Văn A")
                .email("a@coffee.vn")
                .store(store)
                .status(EmployeeStatus.ACTIVE)
                .certificationStatus(CertificationStatus.CERTIFIED)
                .reliabilityScore(100)
                .build();

        empRescuer = Employee.builder()
                .id(30)
                .fullName("Trần Văn Tuấn")
                .email("tuan@coffee.vn")
                .store(store)
                .status(EmployeeStatus.ACTIVE)
                .certificationStatus(CertificationStatus.CERTIFIED)
                .reliabilityScore(100)
                .build();

        Role mgrRole = Role.builder().id(2).roleName(RoleName.MANAGER).build();
        Role staffRole = Role.builder().id(3).roleName(RoleName.STAFF).build();
        managerUser = AuthenticatedUser.from(User.builder()
                .id(100).username("manager").passwordHash("h").role(mgrRole).employee(empManager).isActive(true).build());
        staffUser = AuthenticatedUser.from(User.builder()
                .id(200).username("tuanth").passwordHash("h").role(staffRole).employee(empRescuer).isActive(true).build());
    }

    @Test
    void employeeStartsWithDefault100ReliabilityScore() {
        Employee fresh = Employee.builder().id(99).fullName("Nhân viên mới").build();
        assertEquals(100, fresh.getEffectiveReliabilityScore());
    }

    @Test
    void lateShiftChangeWithin24hDeducts10Points() {
        LocalDateTime urgentStart = VietnamTime.now().plusHours(6);
        Shift shift = Shift.builder()
                .id(1)
                .shiftName("Ca Tối")
                .store(store)
                .startTime(urgentStart.toLocalTime())
                .endTime(urgentStart.toLocalTime().plusHours(4))
                .build();
        ShiftAssignment assignment = ShiftAssignment.builder()
                .id(501)
                .employee(empRequester)
                .shift(shift)
                .workDate(urgentStart.toLocalDate())
                .status(AssignmentStatus.ASSIGNED)
                .build();

        reliabilityService.recordLateShiftChangeIfUrgent(empRequester, assignment, 88);

        assertEquals(90, empRequester.getReliabilityScore());
        ArgumentCaptor<ReliabilityScoreLog> logCaptor = ArgumentCaptor.forClass(ReliabilityScoreLog.class);
        verify(reliabilityScoreLogRepository).save(logCaptor.capture());
        assertEquals(ReliabilityActionType.LATE_SHIFT_CANCEL.getDbValue(), logCaptor.getValue().getActionType());
        assertEquals(-10, logCaptor.getValue().getPointsDelta());
        assertEquals(90, logCaptor.getValue().getScoreAfter());
    }

    @Test
    void rescueShiftWithin24hAdds5PointsOnApproval() {
        LocalDateTime urgentStart = VietnamTime.now().plusHours(8);
        Shift shift = Shift.builder()
                .id(2)
                .shiftName("Ca Chiều")
                .store(store)
                .startTime(urgentStart.toLocalTime())
                .endTime(urgentStart.toLocalTime().plusHours(4))
                .build();
        ShiftAssignment assignment = ShiftAssignment.builder()
                .id(502)
                .employee(empRequester)
                .shift(shift)
                .workDate(urgentStart.toLocalDate())
                .status(AssignmentStatus.ASSIGNED)
                .build();
        ShiftChangeRequest approvedReq = ShiftChangeRequest.builder()
                .id(99)
                .assignment(assignment)
                .employee(empRequester)
                .targetEmployee(empRescuer)
                .status(ApprovalStatus.APPROVED)
                .build();

        reliabilityService.recordRescueShiftOnApproval(approvedReq, empRescuer);

        assertEquals(105, empRescuer.getReliabilityScore());
        ArgumentCaptor<ReliabilityScoreLog> logCaptor = ArgumentCaptor.forClass(ReliabilityScoreLog.class);
        verify(reliabilityScoreLogRepository).save(logCaptor.capture());
        assertEquals(ReliabilityActionType.RESCUE_SHIFT_24H.getDbValue(), logCaptor.getValue().getActionType());
        assertEquals(5, logCaptor.getValue().getPointsDelta());
        assertEquals(105, logCaptor.getValue().getScoreAfter());
    }

    @Test
    void emergencyDeclineFirstTimeWarnsSecondTimeDeducts5Points() {
        LocalDateTime urgentStart = VietnamTime.now().plusHours(5);
        Shift shift = Shift.builder()
                .id(3)
                .shiftName("Ca Sáng")
                .store(store)
                .startTime(urgentStart.toLocalTime())
                .endTime(urgentStart.toLocalTime().plusHours(4))
                .build();
        ShiftAssignment assignment = ShiftAssignment.builder()
                .id(503)
                .employee(empRequester)
                .shift(shift)
                .workDate(urgentStart.toLocalDate())
                .status(AssignmentStatus.ASSIGNED)
                .build();
        ShiftChangeRequest req1 = ShiftChangeRequest.builder()
                .id(101)
                .assignment(assignment)
                .employee(empRequester)
                .targetEmployee(empRescuer)
                .build();

        // Lần 1 trong tháng: chỉ cảnh báo (0 điểm)
        when(reliabilityScoreLogRepository.countByEmployee_IdAndActionTypeInAndCreatedAtBetween(
                eq(30), anyCollection(), any(), any())).thenReturn(0L);
        reliabilityService.recordEmergencyDeclineIfApplicable(empRescuer, req1);
        assertEquals(100, empRescuer.getReliabilityScore());

        // Lần 2 trong tháng: trừ 5 điểm
        when(reliabilityScoreLogRepository.countByEmployee_IdAndActionTypeInAndCreatedAtBetween(
                eq(30), anyCollection(), any(), any())).thenReturn(1L);
        reliabilityService.recordEmergencyDeclineIfApplicable(empRescuer, req1);
        assertEquals(95, empRescuer.getReliabilityScore());
    }

    @Test
    void completedSwappedShiftAdds2Points() {
        when(storeRepository.findByManager_Id(1)).thenReturn(Optional.of(store));
        LocalDateTime shiftTime = VietnamTime.now().minusHours(2);
        Shift shift = Shift.builder()
                .id(4)
                .shiftName("Ca Sáng")
                .store(store)
                .startTime(shiftTime.toLocalTime())
                .endTime(shiftTime.toLocalTime().plusHours(4))
                .build();
        ShiftAssignment assignment = ShiftAssignment.builder()
                .id(504)
                .employee(empRescuer)
                .shift(shift)
                .workDate(shiftTime.toLocalDate())
                .status(AssignmentStatus.ASSIGNED)
                .build();
        ShiftChangeRequest approvedSwap = ShiftChangeRequest.builder()
                .id(104)
                .assignment(assignment)
                .employee(empRequester)
                .targetEmployee(empRescuer)
                .status(ApprovalStatus.APPROVED)
                .build();
        when(shiftChangeRequestRepository.findByIdWithDetails(104)).thenReturn(Optional.of(approvedSwap));
        when(reliabilityScoreLogRepository.existsByEmployee_IdAndActionTypeAndReferenceTypeAndReferenceId(
                30, ReliabilityActionType.COMPLETE_SWAPPED_SHIFT.getDbValue(), "SHIFT_CHANGE", 104))
                .thenReturn(false);

        reliabilityService.confirmSwappedShiftCompletion(104, managerUser);

        assertEquals(102, empRescuer.getReliabilityScore());
    }

    @Test
    void completedSwappedShiftOnCheckoutAutomaticallyAdds2Points() {
        LocalDateTime shiftTime = VietnamTime.now().minusHours(4);
        Shift shift = Shift.builder()
                .id(5)
                .shiftName("Ca Sáng")
                .store(store)
                .startTime(shiftTime.toLocalTime())
                .endTime(shiftTime.toLocalTime().plusHours(4))
                .build();
        ShiftAssignment assignment = ShiftAssignment.builder()
                .id(505)
                .employee(empRescuer)
                .shift(shift)
                .workDate(shiftTime.toLocalDate())
                .status(AssignmentStatus.ASSIGNED)
                .build();
        ShiftChangeRequest approvedSwap = ShiftChangeRequest.builder()
                .id(105)
                .assignment(assignment)
                .employee(empRequester)
                .targetEmployee(empRescuer)
                .status(ApprovalStatus.APPROVED)
                .build();
        Attendance attendance = Attendance.builder()
                .id(900)
                .employee(empRescuer)
                .store(store)
                .shift(shift)
                .workDate(shiftTime.toLocalDate())
                .lateMinutes(0)
                .earlyLeaveMinutes(0)
                .build();

        when(shiftAssignmentRepository.findByEmployee_IdAndWorkDateAndStatus(30, shiftTime.toLocalDate(), AssignmentStatus.ASSIGNED))
                .thenReturn(List.of(assignment));
        when(shiftChangeRequestRepository.findAllByAssignmentId(505))
                .thenReturn(List.of(approvedSwap));
        when(reliabilityScoreLogRepository.existsByEmployee_IdAndActionTypeAndReferenceTypeAndReferenceId(
                30, ReliabilityActionType.COMPLETE_SWAPPED_SHIFT.getDbValue(), "SHIFT_CHANGE", 105))
                .thenReturn(false);

        reliabilityService.recordCompletedSwappedShiftOnCheckout(attendance);

        assertEquals(102, empRescuer.getReliabilityScore());
    }

    @Test
    void leaderboardRanksTopPerformersAndGrantsKpiAndPrioritySchedule() {
        empRescuer.setReliabilityScore(115);
        empRequester.setReliabilityScore(90);

        when(employeeRepository.findByIdWithStore(30)).thenReturn(Optional.of(empRescuer));
        when(employeeRepository.findByStore_IdAndStatus(10, EmployeeStatus.ACTIVE))
                .thenReturn(List.of(empManager, empRequester, empRescuer));

        ReliabilityScoreLog logRescue = ReliabilityScoreLog.builder()
                .id(1)
                .employee(empRescuer)
                .store(store)
                .actionType(ReliabilityActionType.RESCUE_SHIFT_24H.getDbValue())
                .pointsDelta(5)
                .scoreAfter(115)
                .reason("Cứu bồ ca tối")
                .createdAt(VietnamTime.now())
                .build();
        when(reliabilityScoreLogRepository.findForStoreInRange(eq(10), any(), any()))
                .thenReturn(List.of(logRescue));
        when(reliabilityScoreLogRepository.findRecentForStore(eq(10), any()))
                .thenReturn(List.of(logRescue));
        when(reliabilityScoreLogRepository.findRecentForEmployee(eq(30), any()))
                .thenReturn(List.of(logRescue));
        when(shiftChangeRequestRepository.findOpenRescueRequestsForStore(10))
                .thenReturn(List.of());

        ReliabilityLeaderboardView view = reliabilityService.getLeaderboardForStaff(staffUser, YearMonth.now());

        assertNotNull(view);
        assertEquals(2, view.getLeaderboardEntries().size());
        ReliabilityLeaderboardView.RankEntry rank1 = view.getLeaderboardEntries().getFirst();
        assertEquals(1, rank1.getRank());
        assertEquals(30, rank1.getEmployeeId());
        assertEquals(115, rank1.getReliabilityScore());
        assertTrue(rank1.isKpiRewardEligible());
        assertTrue(rank1.isPriorityScheduleEligible());
        assertEquals(1, view.getTotalRescueShiftsMonth());
    }
}

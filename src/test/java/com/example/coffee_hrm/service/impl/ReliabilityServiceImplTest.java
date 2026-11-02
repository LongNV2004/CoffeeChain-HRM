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
import com.example.coffee_hrm.service.ReliabilityService;
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
    void punctualityPenaltyTiersByMinutes() {
        assertEquals(0, ReliabilityServiceImpl.punctualityPenalty(0));
        assertEquals(-2, ReliabilityServiceImpl.punctualityPenalty(20));
        assertEquals(-2, ReliabilityServiceImpl.punctualityPenalty(30));
        assertEquals(-4, ReliabilityServiceImpl.punctualityPenalty(31));
        assertEquals(-4, ReliabilityServiceImpl.punctualityPenalty(60));
        assertEquals(-6, ReliabilityServiceImpl.punctualityPenalty(61));
    }

    @Test
    void lateArrivalAndEarlyLeaveDeductOnCheckout() {
        Attendance attendance = punctualityAttendance(901, 45, 15);
        // Lần trừ thứ 2 thấy được khoản -4 vừa ghi trong tháng
        when(reliabilityScoreLogRepository.sumDeltaForEmployeeInRange(eq(30), any(), any())).thenReturn(0, -4);

        reliabilityService.recordPunctualityPenaltyOnCheckout(attendance);

        // Đi muộn 45p (-4) + về sớm 15p (-2)
        assertEquals(94, empRescuer.getReliabilityScore());
        ArgumentCaptor<ReliabilityScoreLog> logCaptor = ArgumentCaptor.forClass(ReliabilityScoreLog.class);
        verify(reliabilityScoreLogRepository, times(2)).save(logCaptor.capture());
        assertEquals(ReliabilityActionType.LATE_ARRIVAL.getDbValue(), logCaptor.getAllValues().get(0).getActionType());
        assertEquals(-4, logCaptor.getAllValues().get(0).getPointsDelta());
        assertEquals(ReliabilityActionType.EARLY_LEAVE.getDbValue(), logCaptor.getAllValues().get(1).getActionType());
        assertEquals(-2, logCaptor.getAllValues().get(1).getPointsDelta());
        assertEquals("ATTENDANCE", logCaptor.getAllValues().get(1).getReferenceType());
    }

    @Test
    void earlyLeaveWithinGraceIsNotPenalized() {
        Attendance attendance = punctualityAttendance(902, 0, ReliabilityService.EARLY_LEAVE_GRACE_MINUTES - 1);

        reliabilityService.recordPunctualityPenaltyOnCheckout(attendance);

        assertEquals(100, empRescuer.getReliabilityScore());
        verify(reliabilityScoreLogRepository, never()).save(any());
    }

    @Test
    void punctualityPenaltyIsNotAppliedTwiceForSameAttendance() {
        Attendance attendance = punctualityAttendance(903, 90, 0);
        when(reliabilityScoreLogRepository.existsByEmployee_IdAndActionTypeAndReferenceTypeAndReferenceId(
                30, ReliabilityActionType.LATE_ARRIVAL.getDbValue(), "ATTENDANCE", 903))
                .thenReturn(true);

        reliabilityService.recordPunctualityPenaltyOnCheckout(attendance);

        assertEquals(100, empRescuer.getReliabilityScore());
        verify(reliabilityScoreLogRepository, never()).save(any());
    }

    private Attendance punctualityAttendance(int id, int lateMinutes, int earlyMinutes) {
        LocalDateTime shiftTime = VietnamTime.now().minusHours(5);
        Shift shift = Shift.builder()
                .id(6)
                .shiftName("Ca Chiều")
                .store(store)
                .startTime(shiftTime.toLocalTime())
                .endTime(shiftTime.toLocalTime().plusHours(4))
                .build();
        return Attendance.builder()
                .id(id)
                .employee(empRescuer)
                .store(store)
                .shift(shift)
                .workDate(shiftTime.toLocalDate())
                .lateMinutes(lateMinutes)
                .earlyLeaveMinutes(earlyMinutes)
                .build();
    }

    @Test
    void leaderboardRanksTopPerformersAndGrantsKpiAndPrioritySchedule() {
        // Giá trị cộng dồn cũ trên Employee không còn được dùng để xếp hạng tháng.
        empRescuer.setReliabilityScore(999);
        empRequester.setReliabilityScore(999);

        when(employeeRepository.findByIdWithStore(30)).thenReturn(Optional.of(empRescuer));
        when(employeeRepository.findByStore_IdAndStatus(10, EmployeeStatus.ACTIVE))
                .thenReturn(List.of(empManager, empRequester, empRescuer));

        LocalDateTime now = VietnamTime.now();
        YearMonth thisMonth = YearMonth.from(now);
        LocalDateTime lastMonthTime = thisMonth.minusMonths(1).atDay(15).atTime(10, 0);
        // Danh sách trả về theo thứ tự mới nhất trước (giống query thật).
        when(reliabilityScoreLogRepository.findForStoreInRange(eq(10), any(), any()))
                .thenReturn(List.of(
                        scoreLog(4, empRescuer, ReliabilityActionType.RESCUE_SHIFT_24H, 5, now),
                        scoreLog(3, empRescuer, ReliabilityActionType.RESCUE_SHIFT_24H, 5, now.minusSeconds(1)),
                        scoreLog(2, empRescuer, ReliabilityActionType.MANAGER_BONUS, 5, now.minusSeconds(2)),
                        scoreLog(1, empRequester, ReliabilityActionType.LATE_SHIFT_CANCEL, -10, now.minusSeconds(3)),
                        scoreLog(0, empRescuer, ReliabilityActionType.MANAGER_PENALTY, -20, lastMonthTime)));
        when(shiftChangeRequestRepository.findOpenRescueRequestsForStore(10))
                .thenReturn(List.of());

        ReliabilityLeaderboardView view = reliabilityService.getLeaderboardForStaff(staffUser, thisMonth);

        assertNotNull(view);
        assertEquals(2, view.getLeaderboardEntries().size());
        ReliabilityLeaderboardView.RankEntry rank1 = view.getLeaderboardEntries().getFirst();
        assertEquals(1, rank1.getRank());
        assertEquals(30, rank1.getEmployeeId());
        // Tháng này: 100 + 5 + 5 + 5 = 115 (khoản -20 tháng trước không tính)
        assertEquals(115, rank1.getReliabilityScore());
        assertTrue(rank1.isKpiRewardEligible());
        assertTrue(rank1.isPriorityScheduleEligible());
        assertEquals(90, view.getLeaderboardEntries().get(1).getReliabilityScore());
        assertEquals(2, view.getTotalRescueShiftsMonth());
        assertNull(view.getNextMonthParam());
        assertTrue(view.isCurrentMonth());

        // Lịch sử điểm của tôi chỉ gồm biến động tháng đang xem, "điểm sau" tính lũy kế trong tháng
        assertEquals(3, view.getMyRecentLogs().size());
        assertEquals(115, view.getMyRecentLogs().getFirst().getScoreAfter());

        // Lịch sử các tháng: tháng này 115đ hạng 1, tháng trước 80đ
        assertEquals(6, view.getMonthlyHistory().size());
        assertEquals(115, view.getMonthlyHistory().get(0).getMyScore());
        assertEquals(1, view.getMonthlyHistory().get(0).getMyRank());
        assertEquals(80, view.getMonthlyHistory().get(1).getMyScore());
        assertFalse(view.getMonthlyHistory().get(2).isHasActivity());
    }

    @Test
    void futureMonthIsClampedToCurrentMonth() {
        when(employeeRepository.findByIdWithStore(30)).thenReturn(Optional.of(empRescuer));
        when(employeeRepository.findByStore_IdAndStatus(10, EmployeeStatus.ACTIVE))
                .thenReturn(List.of(empRescuer));
        when(reliabilityScoreLogRepository.findForStoreInRange(eq(10), any(), any())).thenReturn(List.of());
        when(shiftChangeRequestRepository.findOpenRescueRequestsForStore(10)).thenReturn(List.of());

        YearMonth thisMonth = YearMonth.from(VietnamTime.today());
        ReliabilityLeaderboardView view = reliabilityService.getLeaderboardForStaff(staffUser, thisMonth.plusMonths(3));

        assertEquals(thisMonth.toString(), view.getSelectedMonth());
        assertEquals(100, view.getMySummary().getReliabilityScore());
    }

    @Test
    void scoreAfterResetsEachMonth() {
        // Employee còn giá trị cộng dồn cũ nhưng tháng này chưa có biến động nào -> mốc là 100
        empRequester.setReliabilityScore(140);
        when(reliabilityScoreLogRepository.sumDeltaForEmployeeInRange(eq(20), any(), any())).thenReturn(0);
        LocalDateTime urgentStart = VietnamTime.now().plusHours(6);
        Shift shift = Shift.builder().id(7).shiftName("Ca Tối").store(store)
                .startTime(urgentStart.toLocalTime()).endTime(urgentStart.toLocalTime().plusHours(4)).build();
        ShiftAssignment assignment = ShiftAssignment.builder().id(507).employee(empRequester).shift(shift)
                .workDate(urgentStart.toLocalDate()).status(AssignmentStatus.ASSIGNED).build();

        reliabilityService.recordLateShiftChangeIfUrgent(empRequester, assignment, 77);

        assertEquals(90, empRequester.getReliabilityScore());
    }

    private ReliabilityScoreLog scoreLog(int id, Employee emp, ReliabilityActionType type, int delta, LocalDateTime at) {
        return ReliabilityScoreLog.builder()
                .id(id)
                .employee(emp)
                .store(store)
                .actionType(type.getDbValue())
                .pointsDelta(delta)
                .scoreAfter(999)
                .reason(type.getLabel())
                .createdAt(at)
                .build();
    }
}

package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.dto.response.ReliabilityLeaderboardView;
import com.example.coffee_hrm.dto.response.WeeklyScheduleView;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.security.DatabaseUserDetailsService;
import com.example.coffee_hrm.security.RoleBasedRedirector;
import com.example.coffee_hrm.security.SecurityConfig;
import com.example.coffee_hrm.service.NotificationService;
import com.example.coffee_hrm.service.RecruitmentRequestService;
import com.example.coffee_hrm.service.ReliabilityService;
import com.example.coffee_hrm.service.ShiftCoverInvitationService;
import com.example.coffee_hrm.service.ResignationProposalService;
import com.example.coffee_hrm.service.ScheduleService;
import com.example.coffee_hrm.service.TrainingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ScheduleController.class)
@Import({SecurityConfig.class, RoleBasedRedirector.class})
class ScheduleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScheduleService scheduleService;
    @MockitoBean
    private ReliabilityService reliabilityService;
    @MockitoBean
    private ShiftCoverInvitationService shiftCoverInvitationService;
    @MockitoBean
    private DatabaseUserDetailsService userDetailsService;
    @MockitoBean
    private NotificationService notificationService;
    @MockitoBean
    private RecruitmentRequestService recruitmentRequestService;
    @MockitoBean
    private ResignationProposalService resignationProposalService;
    @MockitoBean
    private TrainingService trainingService;

    @Test
    void managerLeaderboardRendersPodiumAndTable() throws Exception {
        when(reliabilityService.getLeaderboardForManager(any(), any())).thenReturn(sampleLeaderboard());

        mockMvc.perform(get("/schedule/manager/leaderboard").with(user(manager())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Bảng xếp hạng Nhân viên xuất sắc")))
                .andExpect(content().string(containsString("Trần Văn Tuấn")))
                .andExpect(content().string(containsString("115 đ")))
                .andExpect(content().string(containsString("Nhân viên xuất sắc tháng + Ưu tiên lịch tuần sau")));
    }

    @Test
    void staffLeaderboardRendersPersonalKarmaAndRescueShifts() throws Exception {
        when(reliabilityService.getLeaderboardForStaff(any(), any())).thenReturn(sampleLeaderboard());

        mockMvc.perform(get("/schedule/staff/leaderboard").with(user(staff())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nhân viên xuất sắc")))
                .andExpect(content().string(containsString("Trần Văn Tuấn")))
                .andExpect(content().string(containsString("Nhận hỗ trợ ca (+")));
    }

    @Test
    void staffVolunteerRescueShiftRedirectsToLeaderboard() throws Exception {
        mockMvc.perform(post("/schedule/staff/requests/77/rescue")
                        .with(user(staff()))
                        .with(csrf())
                        .param("origin", "leaderboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/schedule/staff/leaderboard"));

        verify(scheduleService).volunteerRescueShift(any(), any());
    }

    @Test
    void managerScheduleRendersCoverInvitationPanel() throws Exception {
        when(scheduleService.getWeeklyScheduleForManager(any(), any())).thenReturn(sampleWeek(pendingOpenRequest()));
        when(shiftCoverInvitationService.getManagerPanels(any())).thenReturn(java.util.Map.of(77,
                com.example.coffee_hrm.dto.response.CoverInvitationView.ManagerPanel.builder()
                        .requestId(77).urgent(true).canInvite(true).pendingCount(1)
                        .invitations(java.util.List.of(com.example.coffee_hrm.dto.response.CoverInvitationView.InvitationItem.builder()
                                .invitationId(1).employeeName("Phạm Minh Đức").statusCode("DECLINED").statusLabel("Từ chối")
                                .declineReasonLabel("Lịch học / thi").timeLabel("14:10 02/11").build()))
                        .candidates(java.util.List.of(com.example.coffee_hrm.dto.response.CoverInvitationView.Candidate.builder()
                                .employeeId(30).fullName("Lê Thị Thu").monthScore(108).build()))
                        .build()));

        mockMvc.perform(get("/schedule/manager").with(user(manager())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mời hỗ trợ")))
                .andExpect(content().string(containsString("Lê Thị Thu")))
                .andExpect(content().string(containsString("108đ")))
                .andExpect(content().string(containsString("Lịch học / thi")))
                .andExpect(content().string(containsString("cover-declined")));
    }

    @Test
    void staffScheduleRendersCoverInvitationCard() throws Exception {
        when(scheduleService.getWeeklyScheduleForStaff(any(), any())).thenReturn(sampleWeek(null));
        when(shiftCoverInvitationService.getPendingForStaff(any())).thenReturn(java.util.List.of(
                com.example.coffee_hrm.dto.response.CoverInvitationView.StaffCard.builder()
                        .invitationId(900).requestId(77).requesterName("Trần Văn Tuấn").shiftName("Ca Chiều")
                        .timeRange("12:00 – 17:00").workDate(java.time.LocalDate.now()).urgent(true)
                        .expiresAtLabel("16:00 02/11").minutesLeft(110).declinesUsed(2).freeDeclines(2)
                        .nextDeclinePenalized(true).build()));

        mockMvc.perform(get("/schedule/staff").with(user(staff())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Lời mời hỗ trợ ca từ Quản lý")))
                .andExpect(content().string(containsString("Thay Trần Văn Tuấn")))
                .andExpect(content().string(containsString("từ chối lần này sẽ bị -3đ")))
                .andExpect(content().string(containsString("/schedule/staff/invitations/900/decline")))
                .andExpect(content().string(containsString("Lịch làm việc chung tại cửa hàng")))
                .andExpect(content().string(containsString("Lịch sử yêu cầu đổi ca của tôi")))
                .andExpect(content().string(containsString("⇄ Đổi ca")));
    }

    @Test
    void staffDeclineInvitationShowsQuotaMessage() throws Exception {
        when(shiftCoverInvitationService.decline(eq(900), eq("STUDY"), any(), any())).thenReturn(1);

        mockMvc.perform(post("/schedule/staff/invitations/900/decline")
                        .with(user(staff())).with(csrf())
                        .param("reason", "STUDY"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage", containsString("Lần 1/2 được miễn trừ")));
    }

    private WeeklyScheduleView sampleWeek(WeeklyScheduleView.ShiftChangeItem pending) {
        java.time.LocalDate monday = java.time.LocalDate.of(2026, 11, 16);
        String[] dayNames = {"Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7", "Chủ nhật"};
        java.util.List<WeeklyScheduleView.DayHeader> days = new java.util.ArrayList<>();
        for (int i = 0; i < 7; i++) {
            java.time.LocalDate d = monday.plusDays(i);
            days.add(WeeklyScheduleView.DayHeader.builder()
                    .date(d).dayOfWeekName(dayNames[i])
                    .formattedDate(String.format("%02d/%02d", d.getDayOfMonth(), d.getMonthValue()))
                    .isToday(i == 1).build());
        }
        WeeklyScheduleView.AssignmentItem me = WeeklyScheduleView.AssignmentItem.builder()
                .assignmentId(101).employeeId(30).employeeName("Lê Thị Thu")
                .isCurrentStaff(true).canRequestChange(true).status("ASSIGNED").build();
        WeeklyScheduleView.AssignmentItem tuan = WeeklyScheduleView.AssignmentItem.builder()
                .assignmentId(102).employeeId(31).employeeName("Trần Văn Tuấn")
                .isCurrentStaff(false).canRequestChange(false).status("ASSIGNED").build();
        WeeklyScheduleView.AssignmentItem duc = WeeklyScheduleView.AssignmentItem.builder()
                .assignmentId(103).employeeId(32).employeeName("Phạm Minh Đức")
                .isCurrentStaff(false).canRequestChange(false).status("ASSIGNED").build();

        java.util.List<WeeklyScheduleView.DayCell> morningCells = new java.util.ArrayList<>();
        java.util.List<WeeklyScheduleView.DayCell> afternoonCells = new java.util.ArrayList<>();
        java.util.List<WeeklyScheduleView.DayCell> eveningCells = new java.util.ArrayList<>();
        for (int i = 0; i < 7; i++) {
            java.time.LocalDate d = monday.plusDays(i);
            morningCells.add(WeeklyScheduleView.DayCell.builder().date(d).shiftId(1)
                    .assignments(i == 0 ? java.util.List.of(duc, tuan) : (i < 6 ? java.util.List.of(me, tuan) : java.util.List.of()))
                    .build());
            afternoonCells.add(WeeklyScheduleView.DayCell.builder().date(d).shiftId(2)
                    .assignments(i < 5 ? java.util.List.of(tuan, me, duc) : (i == 5 ? java.util.List.of(me) : java.util.List.of()))
                    .build());
            eveningCells.add(WeeklyScheduleView.DayCell.builder().date(d).shiftId(3)
                    .assignments(i == 0 ? java.util.List.of(me) : (i < 4 ? java.util.List.of(duc) : java.util.List.of()))
                    .build());
        }
        java.util.List<WeeklyScheduleView.ShiftRow> rows = java.util.List.of(
                WeeklyScheduleView.ShiftRow.builder().shiftId(1).shiftName("Ca Sáng").formattedTime("07:00 – 12:00").dayCells(morningCells).build(),
                WeeklyScheduleView.ShiftRow.builder().shiftId(2).shiftName("Ca Chiều").formattedTime("12:00 – 17:00").dayCells(afternoonCells).build(),
                WeeklyScheduleView.ShiftRow.builder().shiftId(3).shiftName("Ca Tối").formattedTime("17:00 – 22:00").dayCells(eveningCells).build()
        );
        return WeeklyScheduleView.builder()
                .storeId(10).storeName("The Coffee House - Cầu Giấy").storeAddress("122 Cầu Giấy, Hà Nội")
                .weekStartDate(monday).weekEndDate(monday.plusDays(6))
                .prevWeekDate(monday.minusWeeks(1)).nextWeekDate(monday.plusWeeks(1))
                .days(days).shiftRows(rows)
                .storeEmployees(java.util.List.of()).colleagueShifts(java.util.List.of())
                .pendingShiftChanges(pending == null ? java.util.List.of() : java.util.List.of(pending))
                .incomingRequests(java.util.List.of()).openRescueRequests(java.util.List.of())
                .currentStaffReliabilityScore(97).currentStaffTierLabel("Ổn định")
                .build();
    }

    private WeeklyScheduleView.ShiftChangeItem pendingOpenRequest() {
        return WeeklyScheduleView.ShiftChangeItem.builder()
                .requestId(77).employeeName("Trần Văn Tuấn").shiftName("Ca Chiều")
                .workDate(java.time.LocalDate.of(2026, 11, 2)).timeRange("12:00 – 17:00")
                .reason("Việc gấp").requestDate("02/11/2026 09:00")
                .changeType("MANAGER_ASSIGN").changeTypeLabel("Nhờ Quản lý tìm người")
                .proposalSummary("Chờ Quản lý phân công").urgentWithin24h(true)
                .build();
    }

    @Test
    void managerLeaderboardRendersCloseMonthFormAndCategories() throws Exception {
        ReliabilityLeaderboardView view = sampleLeaderboard();
        view.setCurrentMonth(false);
        view.setCanCloseMonth(true);
        view.setMinShiftsForAward(8);
        view.setMinScoreForAward(105);
        view.setManualAdjustCap(15);
        view.getLeaderboardEntries().forEach(e -> { e.setAwardEligible(true); e.setShiftsWorked(9); });
        view.setAwardProposals(view.getLeaderboardEntries());
        view.setAwards(java.util.List.of());
        when(reliabilityService.getLeaderboardForManager(any(), any())).thenReturn(view);

        mockMvc.perform(get("/schedule/manager/leaderboard").param("month", "2026-10").with(user(manager())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Chốt kết quả tháng")))
                .andExpect(content().string(containsString("(đủ điều kiện)")))
                .andExpect(content().string(containsString("Được khách hàng khen")))
                .andExpect(content().string(containsString("Chưa chốt")));
    }

    @Test
    void managerLeaderboardRendersClosedAwards() throws Exception {
        ReliabilityLeaderboardView view = sampleLeaderboard();
        view.setCurrentMonth(false);
        view.setMonthClosed(true);
        view.setAwards(java.util.List.of(ReliabilityLeaderboardView.AwardItem.builder()
                .rank(1).title("Nhân viên xuất sắc tháng").employeeId(30).employeeName("Trần Văn Tuấn")
                .monthScore(115).decidedAt("01/11/2026 09:00").overrideReason("Được khách khen nhiều lần").build()));
        when(reliabilityService.getLeaderboardForManager(any(), any())).thenReturn(view);

        mockMvc.perform(get("/schedule/manager/leaderboard").param("month", "2026-10").with(user(manager())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Đã chốt")))
                .andExpect(content().string(containsString("Lý do chọn khác đề xuất: Được khách khen nhiều lần")));
    }

    private ReliabilityLeaderboardView sampleLeaderboard() {
        ReliabilityLeaderboardView.RankEntry top1 = ReliabilityLeaderboardView.RankEntry.builder()
                .rank(1)
                .employeeId(30)
                .fullName("Trần Văn Tuấn")
                .email("tuan@coffee.vn")
                .certified(true)
                .currentStaff(true)
                .reliabilityScore(115)
                .monthlyNetDelta(15)
                .rescueShiftsCount(3)
                .completedSwapsCount(2)
                .lateCancelsCount(0)
                .emergencyDeclinesCount(0)
                .tierCode("HERO")
                .tierLabel("Xuất sắc")
                .tierBadgeClass("tier-hero")
                .kpiRewardEligible(true)
                .priorityScheduleEligible(true)
                .rewardBadgeLabel("Nhân viên xuất sắc tháng + Ưu tiên lịch tuần sau")
                .build();

        ReliabilityLeaderboardView.RescueShiftItem rescueItem = ReliabilityLeaderboardView.RescueShiftItem.builder()
                .requestId(77)
                .assignmentId(500)
                .requesterId(20)
                .requesterName("Nguyễn Văn A")
                .shiftName("Ca Tối")
                .workDate(LocalDate.of(2026, 10, 27))
                .formattedDate("27/10/2026")
                .timeRange("17:00 – 22:00")
                .reason("Bận việc gia đình")
                .urgentWithin24h(true)
                .hoursUntilStart(6)
                .rescueBonusPoints(5)
                .canVolunteer(true)
                .build();

        return ReliabilityLeaderboardView.builder()
                .storeId(10)
                .storeName("CoffeeHRM Cầu Giấy")
                .storeAddress("123 Cầu Giấy")
                .selectedMonth("2026-10")
                .periodLabel("Tháng 10/2026")
                .prevMonthParam("2026-09")
                .nextMonthParam("2026-11")
                .totalStaffCount(1)
                .averageReliabilityScore("115.0")
                .totalRescueShiftsMonth(3)
                .totalCompletedSwapsMonth(2)
                .totalLateCancelsMonth(0)
                .openRescueShiftsCount(1)
                .podiumEntries(List.of(top1))
                .leaderboardEntries(List.of(top1))
                .mySummary(top1)
                .nextTierLabel("Đã đạt cấp cao nhất: Xuất sắc")
                .pointsToNextTier(0)
                .progressPercent(100)
                .myRecentLogs(List.of())
                .openRescueShifts(List.of(rescueItem))
                .completedSwapCandidates(List.of())
                .recentLogs(List.of())
                .build();
    }

    private AuthenticatedUser manager() {
        User account = User.builder()
                .id(10)
                .username("manager1")
                .passwordHash("hash")
                .role(Role.builder().id(2).roleName(RoleName.MANAGER).build())
                .isActive(true)
                .build();
        return AuthenticatedUser.from(account);
    }

    private AuthenticatedUser staff() {
        User account = User.builder()
                .id(40)
                .username("tuanth")
                .passwordHash("hash")
                .role(Role.builder().id(3).roleName(RoleName.STAFF).build())
                .isActive(true)
                .build();
        return AuthenticatedUser.from(account);
    }
}

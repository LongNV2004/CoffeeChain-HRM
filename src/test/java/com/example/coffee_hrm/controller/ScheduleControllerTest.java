package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.dto.response.ReliabilityLeaderboardView;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.security.DatabaseUserDetailsService;
import com.example.coffee_hrm.security.RoleBasedRedirector;
import com.example.coffee_hrm.security.SecurityConfig;
import com.example.coffee_hrm.service.NotificationService;
import com.example.coffee_hrm.service.RecruitmentRequestService;
import com.example.coffee_hrm.service.ReliabilityService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
                .andExpect(content().string(containsString("Bảng xếp hạng Chiến thần cứu ca")))
                .andExpect(content().string(containsString("Trần Văn Tuấn")))
                .andExpect(content().string(containsString("115 đ")))
                .andExpect(content().string(containsString("Quán quân KPI Tháng + Ưu tiên lịch tuần sau")));
    }

    @Test
    void staffLeaderboardRendersPersonalKarmaAndRescueShifts() throws Exception {
        when(reliabilityService.getLeaderboardForStaff(any(), any())).thenReturn(sampleLeaderboard());

        mockMvc.perform(get("/schedule/staff/leaderboard").with(user(staff())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Chiến thần cứu ca")))
                .andExpect(content().string(containsString("Trần Văn Tuấn")))
                .andExpect(content().string(containsString("Nhận ca cứu bồ (+")));
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
                .tierLabel("Chiến thần cứu ca")
                .tierBadgeClass("tier-hero")
                .kpiRewardEligible(true)
                .priorityScheduleEligible(true)
                .rewardBadgeLabel("👑 Quán quân KPI Tháng + Ưu tiên lịch tuần sau")
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
                .nextTierLabel("Đã đạt cấp cao nhất: Chiến thần cứu ca")
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

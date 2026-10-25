package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.dto.response.AdminDashboardView;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.security.DatabaseUserDetailsService;
import com.example.coffee_hrm.security.RoleBasedRedirector;
import com.example.coffee_hrm.security.SecurityConfig;
import com.example.coffee_hrm.service.DashboardService;
import com.example.coffee_hrm.service.NotificationService;
import com.example.coffee_hrm.service.RecruitmentRequestService;
import com.example.coffee_hrm.service.ResignationProposalService;
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
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardController.class)
@Import({SecurityConfig.class, RoleBasedRedirector.class})
class AdminDashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;
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
    void adminDashboardRendersLiveSectionsAndRoutes() throws Exception {
        when(dashboardService.buildAdminDashboard(any())).thenReturn(populated());
        when(recruitmentRequestService.countPendingRequests()).thenReturn(4);
        when(resignationProposalService.countPendingProposals()).thenReturn(2);

        mockMvc.perform(get("/dashboard/admin").with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tổng quan hệ thống")))
                .andExpect(content().string(containsString("Theo dõi tình hình hoạt động của toàn bộ chuỗi cửa hàng")))
                .andExpect(content().string(containsString("Công việc cần xử lý")))
                .andExpect(content().string(containsString("Cơ cấu nhân sự")))
                .andExpect(content().string(containsString("Biến động nhân sự")))
                .andExpect(content().string(containsString("Chưa có tài khoản")))
                .andExpect(content().string(containsString("href=\"/admin/stores\"")))
                .andExpect(content().string(containsString("href=\"/admin/employees\"")))
                .andExpect(content().string(containsString("Báo cáo toàn chuỗi")))
                .andExpect(content().string(containsString("Đang chờ duyệt trong tháng này")))
                .andExpect(content().string(containsString("href=\"/attendance/admin/reports\"")))
                .andExpect(content().string(containsString("href=\"/admin/training\"")))
                .andExpect(content().string(not(containsString("Lớp đào tạo chờ duyệt"))))
                .andExpect(content().string(not(containsString("Đề xuất đào tạo"))))
                .andExpect(content().string(not(containsString("Chờ Admin phê duyệt"))))
                .andExpect(content().string(containsString("href=\"/admin/recruitment\"")))
                .andExpect(content().string(containsString("href=\"/admin/resignation\"")))
                .andExpect(content().string(containsString("data-labels=\"05/2026,06/2026\"")))
                .andExpect(content().string(containsString("id=\"adminRoleChart\"")))
                .andExpect(content().string(not(containsString("Xin chào Admin"))))
                .andExpect(content().string(not(containsString("Hoạt động gần đây"))))
                .andExpect(content().string(not(containsString("Vai trò khác"))));
    }

    @Test
    void emptyWorkforceShowsEmptyStateInsteadOfARoleChart() throws Exception {
        when(dashboardService.buildAdminDashboard(any())).thenReturn(emptyWorkforce());

        mockMvc.perform(get("/dashboard/admin").with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Chưa có nhân sự đang làm việc.")))
                .andExpect(content().string(containsString("Không có tuyển mới hoặc nghỉ việc trong 6 tháng gần nhất.")))
                .andExpect(content().string(containsString("id=\"adminTrendChart\"")))
                .andExpect(content().string(not(containsString("id=\"adminRoleChart\""))));
    }

    private AdminDashboardView populated() {
        return AdminDashboardView.builder()
                .username("admin")
                .displayName("Admin")
                .today(LocalDate.of(2026, 10, 23))
                .todayLabel("thứ Sáu, 23/10/2026")
                .totalStores(11)
                .activeStores(7)
                .totalEmployees(12)
                .waitingChainReportCount(5)
                .managerCount(3)
                .staffCount(8)
                .unassignedRoleCount(1)
                .otherRoleCount(0)
                .trend(List.of(
                        AdminDashboardView.TrendPoint.builder().label("05/2026").hired(1).resigned(0).build(),
                        AdminDashboardView.TrendPoint.builder().label("06/2026").hired(0).resigned(2).build()))
                .build();
    }

    private AdminDashboardView emptyWorkforce() {
        return AdminDashboardView.builder()
                .username("admin")
                .displayName("Admin")
                .today(LocalDate.of(2026, 10, 23))
                .todayLabel("23/10/2026")
                .totalStores(0)
                .activeStores(0)
                .totalEmployees(0)
                .managerCount(0)
                .staffCount(0)
                .unassignedRoleCount(0)
                .otherRoleCount(0)
                .trend(List.of(
                        AdminDashboardView.TrendPoint.builder().label("05/2026").hired(0).resigned(0).build(),
                        AdminDashboardView.TrendPoint.builder().label("06/2026").hired(0).resigned(0).build()))
                .build();
    }

    private AuthenticatedUser admin() {
        User user = User.builder()
                .id(1)
                .username("admin")
                .passwordHash("hash")
                .role(Role.builder().id(1).roleName(RoleName.ADMIN).build())
                .isActive(true)
                .build();
        return AuthenticatedUser.from(user);
    }
}

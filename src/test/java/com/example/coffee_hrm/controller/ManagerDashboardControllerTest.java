package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.dto.response.ManagerDashboardView;
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
class ManagerDashboardControllerTest {

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
    void managerDashboardRendersStoreOverviewFromAssignedStore() throws Exception {
        when(dashboardService.buildManagerDashboard(any())).thenReturn(populated());

        mockMvc.perform(get("/dashboard/manager").with(user(manager())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tổng quan cửa hàng")))
                .andExpect(content().string(containsString("Theo dõi tình hình hoạt động và nhân sự tại cửa hàng")))
                .andExpect(content().string(containsString("Cà phê Nguyễn Huệ")))
                .andExpect(content().string(containsString("Hoạt động")))
                .andExpect(content().string(containsString("thứ Sáu, 23/10/2026")))
                .andExpect(content().string(containsString("Nhân viên đang làm việc")))
                .andExpect(content().string(containsString("Ca làm hôm nay")))
                .andExpect(content().string(containsString("8/12")))
                .andExpect(content().string(containsString("Công việc cần xử lý")))
                .andExpect(content().string(containsString("Đơn xin nghỉ phép")))
                .andExpect(content().string(containsString("Đăng ký ca làm")))
                .andExpect(content().string(containsString("Yêu cầu đổi ca")))
                .andExpect(content().string(containsString("Tình hình chấm công")))
                .andExpect(content().string(containsString("Lịch làm việc hôm nay")))
                .andExpect(content().string(containsString("Ca sáng")))
                .andExpect(content().string(containsString("07:00 – 12:00")))
                .andExpect(content().string(containsString("href=\"/manager/employees\"")))
                .andExpect(content().string(containsString("href=\"/schedule/manager\"")))
                .andExpect(content().string(containsString("href=\"/attendance/manager/day\"")))
                .andExpect(content().string(containsString("href=\"/manager/leaves?status=PENDING\"")))
                .andExpect(content().string(containsString("href=\"/availability/manager\"")))
                .andExpect(content().string(containsString("href=\"/schedule/manager#shift-change-requests\"")))
                .andExpect(content().string(containsString("id=\"managerAttendanceChart\"")))
                .andExpect(content().string(containsString("data-completed=\"8\"")))
                .andExpect(content().string(containsString("data-remaining=\"4\"")))
                .andExpect(content().string(containsString("data-rate=\"67\"")))
                .andExpect(content().string(not(containsString("Hoạt động gần đây"))))
                .andExpect(content().string(not(containsString("Đề xuất tuyển dụng"))))
                .andExpect(content().string(not(containsString("Xin chào Manager"))));
    }

    @Test
    void emptyStoreDayShowsEmptyStatesInsteadOfAChart() throws Exception {
        when(dashboardService.buildManagerDashboard(any())).thenReturn(quietStore());

        mockMvc.perform(get("/dashboard/manager").with(user(manager())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Không có yêu cầu cần xử lý.")))
                .andExpect(content().string(containsString("Chưa có ca làm việc được phân công trong ngày.")))
                .andExpect(content().string(containsString("Chưa có lịch làm việc trong ngày.")))
                .andExpect(content().string(containsString("href=\"/schedule/manager\"")))
                .andExpect(content().string(not(containsString("id=\"managerAttendanceChart\""))))
                .andExpect(content().string(not(containsString("Đơn xin nghỉ phép"))));
    }

    @Test
    void managerWithoutAStoreDoesNotRenderAnotherStoreName() throws Exception {
        when(dashboardService.buildManagerDashboard(any())).thenReturn(unassigned());

        mockMvc.perform(get("/dashboard/manager").with(user(manager())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("chưa được phân công cửa hàng")))
                .andExpect(content().string(not(containsString("Cà phê Nguyễn Huệ"))))
                .andExpect(content().string(not(containsString("id=\"managerAttendanceChart\""))));
    }

    private ManagerDashboardView populated() {
        return ManagerDashboardView.builder()
                .username("lan")
                .displayName("Lan")
                .storeId(4)
                .storeName("Cà phê Nguyễn Huệ")
                .storeStatusLabel("Hoạt động")
                .storeStatusKey("is-active")
                .today(LocalDate.of(2026, 10, 23))
                .todayLabel("thứ Sáu, 23/10/2026")
                .workingEmployeeCount(6)
                .todaySlotCount(12)
                .todayEmployeeCount(9)
                .completedSlotCount(8)
                .completionRate(67)
                .violationSlotCount(2)
                .pendingRequestCount(4)
                .pendingLeaveCount(2)
                .pendingRegistrationCount(1)
                .pendingShiftChangeCount(1)
                .todayShifts(List.of(ManagerDashboardView.ShiftSummary.builder()
                        .shiftId(1)
                        .shiftName("Ca sáng")
                        .timeLabel("07:00 – 12:00")
                        .slotCount(8)
                        .employeeCount(8)
                        .build()))
                .build();
    }

    private ManagerDashboardView quietStore() {
        return ManagerDashboardView.builder()
                .storeId(4)
                .storeName("Cà phê Nguyễn Huệ")
                .storeStatusLabel("Hoạt động")
                .storeStatusKey("is-active")
                .today(LocalDate.of(2026, 10, 23))
                .todayLabel("thứ Sáu, 23/10/2026")
                .completionRate(null)
                .build();
    }

    private ManagerDashboardView unassigned() {
        return ManagerDashboardView.builder()
                .today(LocalDate.of(2026, 10, 23))
                .todayLabel("thứ Sáu, 23/10/2026")
                .build();
    }

    private AuthenticatedUser manager() {
        User account = User.builder()
                .id(20)
                .username("lan")
                .passwordHash("hash")
                .role(Role.builder().id(2).roleName(RoleName.MANAGER).build())
                .isActive(true)
                .build();
        return AuthenticatedUser.from(account);
    }
}

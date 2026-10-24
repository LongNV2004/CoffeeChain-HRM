package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.dto.response.StaffDashboardView;
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
class StaffDashboardControllerTest {

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
    void staffDashboardRendersPersonalOverview() throws Exception {
        when(dashboardService.buildStaffDashboard(any())).thenReturn(populated());

        mockMvc.perform(get("/dashboard/staff").with(user(staff())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tổng quan cá nhân")))
                .andExpect(content().string(containsString("Xin chào, Mai Trần.")))
                .andExpect(content().string(containsString("Theo dõi lịch làm việc, chấm công và thông tin cá nhân của bạn.")))
                .andExpect(content().string(containsString("Cà phê Nguyễn Huệ")))
                .andExpect(content().string(containsString("Ca làm hôm nay")))
                .andExpect(content().string(containsString("Ca làm sắp tới")))
                .andExpect(content().string(containsString("Đăng ký ca chờ duyệt")))
                .andExpect(content().string(containsString("Yêu cầu cá nhân chờ xử lý")))
                .andExpect(content().string(containsString("Lịch làm việc sắp tới")))
                .andExpect(content().string(containsString("Ca sáng")))
                .andExpect(content().string(containsString("07:00 – 12:00")))
                .andExpect(content().string(containsString("Chấm công hôm nay")))
                .andExpect(content().string(containsString("Chưa đến giờ")))
                .andExpect(content().string(containsString("Đăng ký ca làm")))
                .andExpect(content().string(containsString("Đào tạo và chứng chỉ")))
                .andExpect(content().string(containsString("CERTIFIED - Pha chế")))
                .andExpect(content().string(containsString("href=\"/schedule/staff\"")))
                .andExpect(content().string(containsString("href=\"/availability\"")))
                .andExpect(content().string(containsString("href=\"/attendance/staff/history\"")))
                .andExpect(content().string(containsString("href=\"/training/staff\"")))
                .andExpect(content().string(containsString("href=\"/staff/leave\"")))
                .andExpect(content().string(not(containsString("Hoạt động gần đây"))))
                .andExpect(content().string(not(containsString("Nguyễn Văn B"))));
    }

    @Test
    void staffDashboardShowsEmptyScheduleAndRegistrationBlock() throws Exception {
        when(dashboardService.buildStaffDashboard(any())).thenReturn(quiet());

        mockMvc.perform(get("/dashboard/staff").with(user(staff())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Bạn chưa có lịch làm việc sắp tới.")))
                .andExpect(content().string(containsString("Bạn không có ca làm việc cần chấm công hôm nay.")))
                .andExpect(content().string(containsString("Bạn chưa có chứng chỉ nên không thể đăng ký ca làm việc.")))
                .andExpect(content().string(containsString("Bạn chưa có lớp đào tạo sắp diễn ra.")))
                .andExpect(content().string(not(containsString(">Đăng ký ca làm</a>"))));
    }

    private StaffDashboardView populated() {
        return StaffDashboardView.builder()
                .greeting("Xin chào, Mai Trần.")
                .storeName("Cà phê Nguyễn Huệ")
                .today(LocalDate.of(2026, 10, 24))
                .todayLabel("thứ Bảy, 24/10/2026")
                .todaySlotCount(1)
                .upcomingSlotCount(3)
                .upcomingWindowLabel("7 ngày tiếp theo, từ 25/10/2026 đến 31/10/2026, không gồm hôm nay")
                .scheduleWindowLabel("Ca đã xác nhận và chưa bắt đầu, đến 31/10/2026")
                .pendingRegistrationCount(1)
                .submittedRegistrationCount(2)
                .approvedRegistrationCount(1)
                .pendingPersonalCount(1)
                .pendingLeaveCount(1)
                .canRegister(true)
                .certificationKnown(true)
                .certified(true)
                .certificationLabel("Đã chứng nhận")
                .upcomingShifts(List.of(StaffDashboardView.ScheduleItem.builder()
                        .workDate(LocalDate.of(2026, 10, 26))
                        .dateLabel("thứ Hai, 26/10")
                        .shiftName("Ca sáng")
                        .timeLabel("07:00 – 12:00")
                        .storeName("Cà phê Nguyễn Huệ")
                        .statusLabel("Đã phân công")
                        .build()))
                .todayAttendance(List.of(StaffDashboardView.AttendanceSlot.builder()
                        .shiftName("Ca sáng")
                        .scheduleLabel("07:00 – 12:00")
                        .statusKey("upcoming")
                        .statusLabel("Chưa đến giờ")
                        .checkInLabel("—")
                        .checkOutLabel("—")
                        .build()))
                .skills(List.of(StaffDashboardView.SkillItem.builder()
                        .skillName("Pha chế")
                        .certificationLabel("CERTIFIED - Pha chế")
                        .build()))
                .upcomingClasses(List.of(StaffDashboardView.TrainingItem.builder()
                        .className("Lớp pha chế")
                        .dateLabel("27/10/2026")
                        .statusLabel("Sắp diễn ra")
                        .build()))
                .build();
    }

    private StaffDashboardView quiet() {
        return StaffDashboardView.builder()
                .greeting("Xin chào, Mai Trần.")
                .today(LocalDate.of(2026, 10, 24))
                .todayLabel("thứ Bảy, 24/10/2026")
                .upcomingWindowLabel("7 ngày tiếp theo")
                .scheduleWindowLabel("Ca đã xác nhận và chưa bắt đầu")
                .attendanceMessage("Bạn không có ca làm việc cần chấm công hôm nay.")
                .canRegister(false)
                .registerBlockedMessage("Bạn chưa có chứng chỉ nên không thể đăng ký ca làm việc.")
                .certificationKnown(true)
                .certificationLabel("Chưa chứng nhận")
                .build();
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

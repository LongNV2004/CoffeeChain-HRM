package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.dto.response.OvertimePageView;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.security.DatabaseUserDetailsService;
import com.example.coffee_hrm.security.RoleBasedRedirector;
import com.example.coffee_hrm.security.SecurityConfig;
import com.example.coffee_hrm.service.NotificationService;
import com.example.coffee_hrm.service.OvertimeRequestService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OvertimePageController.class)
@Import({SecurityConfig.class, RoleBasedRedirector.class})
class OvertimePageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OvertimeRequestService overtimeRequestService;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private DatabaseUserDetailsService userDetailsService;

    @MockitoBean
    private RecruitmentRequestService recruitmentRequestService;

    @MockitoBean
    private ResignationProposalService resignationProposalService;

    @MockitoBean
    private TrainingService trainingService;

    @Test
    void staffOvertimePageRendersQuotaAndForm() throws Exception {
        AuthenticatedUser staff = buildPrincipal(3, "staff1", RoleName.STAFF, 20, "Nhân viên An");
        LocalDate monday = LocalDate.of(2026, 11, 16);
        OvertimePageView view = OvertimePageView.builder()
                .storeName("Coffee Quận 1")
                .employeeName("Nhân viên An")
                .weekStart(monday)
                .weekEnd(monday.plusDays(6))
                .previousWeek(monday.minusWeeks(1))
                .nextWeek(monday.plusWeeks(1))
                .currentWeek(monday.minusWeeks(1))
                .formattedWeekRange("16/11 – 22/11/2026")
                .contractWeeklyQuota(7)
                .regularShiftsInWeek(7)
                .approvedOvertimeInWeek(0)
                .pendingOvertimeInWeek(0)
                .quotaReached(true)
                .canSubmitOvertimeInWeek(true)
                .quotaStatusMessage("Bạn đã đăng ký đủ 7/7 ca tối đa theo hợp đồng trong tuần này.")
                .days(List.of())
                .shiftRows(List.of())
                .requests(List.of())
                .totals(new OvertimePageView.SummaryTotals(0, 0, 0, 0))
                .build();

        when(overtimeRequestService.getStaffOvertimePage(any(), any())).thenReturn(view);

        mockMvc.perform(get("/schedule/staff/overtime").with(user(staff)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Đơn xin tăng ca")))
                .andExpect(content().string(containsString("7 ca/tuần")))
                .andExpect(content().string(containsString("7/7 ca")))
                .andExpect(content().string(containsString("Gửi đơn xin tăng ca")));
    }

    @Test
    void managerOvertimeReviewRedirectsAndCallsService() throws Exception {
        AuthenticatedUser manager = buildPrincipal(2, "manager1", RoleName.MANAGER, 10, "Quản lý Trần");

        mockMvc.perform(post("/schedule/manager/overtime/55/review")
                        .with(user(manager))
                        .with(csrf())
                        .param("approved", "true")
                        .param("reviewNote", "Duyệt hỗ trợ cuối tuần"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/schedule/manager/overtime"));

        verify(overtimeRequestService).reviewOvertimeRequest(
                eq(55), eq(true), eq("Duyệt hỗ trợ cuối tuần"), any());
    }

    private static AuthenticatedUser buildPrincipal(Integer userId,
                                                    String username,
                                                    RoleName roleName,
                                                    Integer employeeId,
                                                    String fullName) {
        Store store = Store.builder().id(1).storeName("Coffee Quận 1").build();
        Employee employee = Employee.builder()
                .id(employeeId)
                .fullName(fullName)
                .store(store)
                .managementCertified(true)
                .build();
        User user = User.builder()
                .id(userId)
                .username(username)
                .passwordHash("hash")
                .isActive(true)
                .role(Role.builder().id(1).roleName(roleName).build())
                .employee(employee)
                .build();
        return AuthenticatedUser.from(user);
    }
}

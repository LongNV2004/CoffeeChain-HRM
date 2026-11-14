package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.dto.response.AdminLeaveReport;
import com.example.coffee_hrm.dto.response.TrainingClassResponse;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.EmploymentContractRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.security.DatabaseUserDetailsService;
import com.example.coffee_hrm.security.RoleBasedRedirector;
import com.example.coffee_hrm.security.SecurityConfig;
import com.example.coffee_hrm.service.LeaveRequestService;
import com.example.coffee_hrm.service.NotificationService;
import com.example.coffee_hrm.service.RecruitmentRequestService;
import com.example.coffee_hrm.service.ResignationProposalService;
import com.example.coffee_hrm.service.TrainingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phân trang phía server dùng chung fragment Numbered Pagination (fragments/pager.html). */
@WebMvcTest({TrainingController.class, AdminLeaveController.class})
@Import({SecurityConfig.class, RoleBasedRedirector.class})
class NumberedPaginationPageTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TrainingService trainingService;
    @MockitoBean
    private LeaveRequestService leaveRequestService;
    @MockitoBean
    private EmploymentContractRepository employmentContractRepository;
    @MockitoBean
    private DatabaseUserDetailsService userDetailsService;
    @MockitoBean
    private NotificationService notificationService;
    @MockitoBean
    private RecruitmentRequestService recruitmentRequestService;
    @MockitoBean
    private ResignationProposalService resignationProposalService;

    @Test
    void trainingClassListUsesNumberedPaginationWithEllipsis() throws Exception {
        List<TrainingClassResponse> rows = new ArrayList<>();
        // Trang 5/10 (6 lớp mỗi trang, 60 lớp).
        when(trainingService.listClassesForManager(any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(new PageImpl<>(rows, PageRequest.of(4, 6), 60));
        when(trainingService.listEndedClassesForManager(any())).thenReturn(List.of());

        mockMvc.perform(get("/training").param("page", "5").param("keyword", "pha chế").with(user(manager())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"coffee-pager coffee-pager--server\"")))
                .andExpect(content().string(containsString("Trang 5/10 · 60 lớp")))
                // Trước 3 4 [5] 6 7 Sau: tối đa 5 số trang, giữ nguyên bộ lọc trên liên kết
                .andExpect(content().string(containsString("aria-current=\"page\" class=\"is-on\">5</a>")))
                .andExpect(content().string(containsString("href=\"/training?page=3&amp;")))
                .andExpect(content().string(containsString("href=\"/training?page=7&amp;")))
                .andExpect(content().string(containsString("keyword=pha")))
                .andExpect(content().string(not(containsString("coffee-pager-gap"))))
                .andExpect(content().string(not(containsString("href=\"/training?page=2&amp;"))))
                .andExpect(content().string(not(containsString("href=\"/training?page=8&amp;"))))
                .andExpect(content().string(not(containsString("Số dòng mỗi trang"))))
                .andExpect(content().string(not(containsString("Trang tiếp theo"))))
                .andExpect(content().string(not(containsString("__PAGE__"))));
    }

    @Test
    void zeroBasedLeaveReportLinksStillPointToTheRightPage() throws Exception {
        when(leaveRequestService.adminReport(eq(0), any(), any(), any()))
                .thenReturn(new AdminLeaveReport(0, 0, 0, 0, List.of(), 0, 3, 45));

        mockMvc.perform(get("/admin/leaves").with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Trang 1/3 · 45 đơn")))
                // Trang hiển thị "2" dẫn tới page=1 vì API tính từ 0.
                .andExpect(content().string(containsString("href=\"/admin/leaves?page=1&amp;status=&amp;fromDate=&amp;toDate=\">2</a>")))
                .andExpect(content().string(containsString("href=\"/admin/leaves?page=2&amp;status=&amp;fromDate=&amp;toDate=\">3</a>")))
                .andExpect(content().string(containsString("<span class=\"is-disabled\" aria-disabled=\"true\">Trước</span>")))
                .andExpect(content().string(containsString("href=\"/admin/leaves?page=1&amp;status=&amp;fromDate=&amp;toDate=\">Sau</a>")))
                .andExpect(content().string(not(containsString("__PAGE__"))));
    }

    private AuthenticatedUser manager() {
        Employee employee = Employee.builder().id(5).fullName("Quản lý").managementCertified(true).build();
        User account = User.builder().id(20).username("lan").passwordHash("hash")
                .role(Role.builder().id(2).roleName(RoleName.MANAGER).build())
                .employee(employee).isActive(true).build();
        return AuthenticatedUser.from(account);
    }

    private AuthenticatedUser admin() {
        User account = User.builder().id(1).username("admin").passwordHash("hash")
                .role(Role.builder().id(1).roleName(RoleName.ADMIN).build())
                .isActive(true).build();
        return AuthenticatedUser.from(account);
    }
}

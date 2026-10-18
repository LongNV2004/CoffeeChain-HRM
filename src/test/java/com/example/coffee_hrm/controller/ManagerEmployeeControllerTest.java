package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.response.EmployeeResponse;
import com.example.coffee_hrm.dto.response.ManagedEmployeeDetail;
import com.example.coffee_hrm.dto.response.ManagedEmployeeDirectory;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.security.DatabaseUserDetailsService;
import com.example.coffee_hrm.security.RoleBasedRedirector;
import com.example.coffee_hrm.security.SecurityConfig;
import com.example.coffee_hrm.service.EmployeeService;
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

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ManagerEmployeeController.class)
@Import({SecurityConfig.class, RoleBasedRedirector.class})
class ManagerEmployeeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmployeeService employeeService;
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
    void staffAndAdminCannotOpenManagerDirectory() throws Exception {
        mockMvc.perform(get("/manager/employees").with(user(actor(RoleName.STAFF))))
                .andExpect(redirectedUrl("/dashboard/staff"));
        mockMvc.perform(get("/manager/employees/5").with(user(actor(RoleName.STAFF))))
                .andExpect(redirectedUrl("/dashboard/staff"));
        mockMvc.perform(get("/manager/employees").with(user(actor(RoleName.ADMIN))))
                .andExpect(redirectedUrl("/dashboard/admin"));
        verify(employeeService, never()).getManagedDirectory(any());
        verify(employeeService, never()).getManagedEmployee(any(), any());
    }

    @Test
    void anonymousIsSentToLogin() throws Exception {
        mockMvc.perform(get("/manager/employees"))
                .andExpect(redirectedUrl("/login"));
        verify(employeeService, never()).getManagedDirectory(any());
    }

    @Test
    void managerListIgnoresStoreIdFromRequest() throws Exception {
        when(employeeService.getManagedDirectory(any())).thenReturn(directory());

        mockMvc.perform(get("/manager/employees").param("storeId", "99").with(user(actor(RoleName.MANAGER))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Danh sách nhân sự")))
                .andExpect(content().string(containsString("Cửa hàng A")))
                .andExpect(content().string(containsString("Nguyễn An")))
                .andExpect(content().string(containsString("/manager/employees/5")));

        verify(employeeService).getManagedDirectory(any());
        verify(employeeService, never()).getStoreEmployees(any(), eq(99));
    }

    @Test
    void employeeOutsideManagedStoreReturnsToList() throws Exception {
        when(employeeService.getManagedEmployee(any(), eq(9)))
                .thenThrow(new BusinessException("Nhân viên không thuộc cửa hàng này"));

        mockMvc.perform(get("/manager/employees/9").with(user(actor(RoleName.MANAGER))))
                .andExpect(redirectedUrl("/manager/employees"))
                .andExpect(flash().attribute("errorMessage", "Nhân viên không thuộc cửa hàng này"));
    }

    @Test
    void managerCanOpenEmployeeDetail() throws Exception {
        EmployeeResponse employee = EmployeeResponse.builder()
                .id(5)
                .fullName("Nguyễn An")
                .email("an@store.vn")
                .phone("0901000000")
                .storeName("Cửa hàng A")
                .status(EmployeeStatus.ACTIVE)
                .roleName(RoleName.STAFF)
                .accountActive(true)
                .hasCertificate(false)
                .build();
        when(employeeService.getManagedEmployee(any(), eq(5))).thenReturn(
                new ManagedEmployeeDetail(employee, "Chưa chứng nhận", List.of(), List.of(), List.of(), List.of()));

        mockMvc.perform(get("/manager/employees/5").with(user(actor(RoleName.MANAGER))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Thông tin cá nhân")))
                .andExpect(content().string(containsString("Nguyễn An")))
                .andExpect(content().string(containsString("Chưa có lịch làm việc sắp tới.")))
                .andExpect(content().string(containsString("Chưa có lịch sử chấm công.")));
    }

    private ManagedEmployeeDirectory directory() {
        EmployeeResponse employee = EmployeeResponse.builder()
                .id(5)
                .fullName("Nguyễn An")
                .email("an@store.vn")
                .phone("0901000000")
                .status(EmployeeStatus.ACTIVE)
                .roleName(RoleName.STAFF)
                .accountActive(true)
                .build();
        return new ManagedEmployeeDirectory(
                1, "Cửa hàng A", "123 Lê Lợi",
                1, 1, 0, 0, 0, 1, 0, 0,
                List.of(employee));
    }

    private AuthenticatedUser actor(RoleName roleName) {
        Role role = Role.builder().id(1).roleName(roleName).build();
        return AuthenticatedUser.from(User.builder()
                .id(1)
                .username("user")
                .passwordHash("x")
                .role(role)
                .isActive(true)
                .build());
    }
}

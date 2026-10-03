package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.response.EmployeeResponse;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.security.DatabaseUserDetailsService;
import com.example.coffee_hrm.security.RoleBasedRedirector;
import com.example.coffee_hrm.security.SecurityConfig;
import com.example.coffee_hrm.service.EmployeeService;
import com.example.coffee_hrm.service.NotificationService;
import com.example.coffee_hrm.service.RecruitmentRequestService;
import com.example.coffee_hrm.service.StoreService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

@WebMvcTest(AdminEmployeeController.class)
@Import({SecurityConfig.class, RoleBasedRedirector.class})
class AdminEmployeeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StoreService storeService;
    @MockitoBean
    private EmployeeService employeeService;
    @MockitoBean
    private DatabaseUserDetailsService userDetailsService;
    @MockitoBean
    private NotificationService notificationService;
    @MockitoBean
    private RecruitmentRequestService recruitmentRequestService;

    @Test
    void managerAndStaffCannotViewStoreEmployees() throws Exception {
        mockMvc.perform(get("/admin/stores/1/employees").with(user(actor(RoleName.MANAGER))))
                .andExpect(redirectedUrl("/dashboard/manager"));
        mockMvc.perform(get("/admin/stores/1/employees").with(user(actor(RoleName.STAFF))))
                .andExpect(redirectedUrl("/dashboard/staff"));
        verify(employeeService, never()).getStoreEmployees(any(), any());
    }

    @Test
    void managerAndStaffCannotChangeRole() throws Exception {
        for (RoleName role : new RoleName[]{RoleName.MANAGER, RoleName.STAFF}) {
            mockMvc.perform(post("/admin/stores/1/employees/2/role").with(user(actor(role))).with(csrf())
                    .param("role", "MANAGER"));
        }
        verify(employeeService, never()).changeEmployeeRole(any(), anyInt(), anyInt(), anyString());
    }

    @Test
    void anonymousIsSentToLogin() throws Exception {
        mockMvc.perform(post("/admin/stores/1/employees/2/role").with(csrf()).param("role", "MANAGER"))
                .andExpect(redirectedUrl("/login"));
        verify(employeeService, never()).changeEmployeeRole(any(), anyInt(), anyInt(), anyString());
    }

    @Test
    void changeRoleRequiresCsrfToken() throws Exception {
        mockMvc.perform(post("/admin/stores/1/employees/2/role").with(user(actor(RoleName.ADMIN)))
                .param("role", "MANAGER"));
        verify(employeeService, never()).changeEmployeeRole(any(), anyInt(), anyInt(), anyString());
    }

    @Test
    void adminChangesRoleAndReturnsToStorePage() throws Exception {
        when(employeeService.changeEmployeeRole(any(), eq(1), eq(2), eq("MANAGER"))).thenReturn(
                EmployeeResponse.builder().id(2).fullName("Trần Văn Tuấn").storeId(1)
                        .status(EmployeeStatus.ACTIVE).roleName(RoleName.MANAGER).storeManager(true).build());

        mockMvc.perform(post("/admin/stores/1/employees/2/role").with(user(actor(RoleName.ADMIN))).with(csrf())
                        .param("role", "MANAGER"))
                .andExpect(redirectedUrl("/admin/stores/1/employees"))
                .andExpect(flash().attribute("successMessage",
                        "Đã cập nhật role của Trần Văn Tuấn thành MANAGER."));
    }

    @Test
    void businessRuleViolationIsShownAsFlashError() throws Exception {
        when(employeeService.changeEmployeeRole(any(), eq(1), eq(2), eq("MANAGER")))
                .thenThrow(new BusinessException("Cửa hàng đã có Manager"));

        mockMvc.perform(post("/admin/stores/1/employees/2/role").with(user(actor(RoleName.ADMIN))).with(csrf())
                        .param("role", "MANAGER"))
                .andExpect(redirectedUrl("/admin/stores/1/employees"))
                .andExpect(flash().attribute("errorMessage", "Cửa hàng đã có Manager"));
    }

    @Test
    void unknownStoreRedirectsToStorePicker() throws Exception {
        when(storeService.getStore(any(), eq(99))).thenThrow(new BusinessException("Store not found"));

        mockMvc.perform(get("/admin/stores/99/employees").with(user(actor(RoleName.ADMIN))))
                .andExpect(redirectedUrl("/admin/employees"))
                .andExpect(flash().attribute("errorMessage", "Store not found"));
    }

    @Test
    void storePickerRedirectsToSelectedStore() throws Exception {
        mockMvc.perform(get("/admin/employees").param("storeId", "3").with(user(actor(RoleName.ADMIN))))
                .andExpect(redirectedUrl("/admin/stores/3/employees"));
    }

    private AuthenticatedUser actor(RoleName roleName) {
        Role role = Role.builder().id(1).roleName(roleName).build();
        return AuthenticatedUser.from(User.builder().id(1).username("user").passwordHash("x").role(role).isActive(true).build());
    }
}

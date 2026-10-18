package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.enums.ResignationProposalStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.dto.response.ResignationProposalResponse;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.security.DatabaseUserDetailsService;
import com.example.coffee_hrm.security.RoleBasedRedirector;
import com.example.coffee_hrm.security.SecurityConfig;
import com.example.coffee_hrm.service.NotificationService;
import com.example.coffee_hrm.service.RecruitmentRequestService;
import com.example.coffee_hrm.service.ResignationProposalService;
import com.example.coffee_hrm.service.StoreService;
import com.example.coffee_hrm.service.TrainingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({ManagerResignationController.class, AdminResignationController.class})
@Import({SecurityConfig.class, RoleBasedRedirector.class})
class ResignationControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ResignationProposalService resignationProposalService;
    @MockitoBean
    private StoreService storeService;
    @MockitoBean
    private DatabaseUserDetailsService userDetailsService;
    @MockitoBean
    private NotificationService notificationService;
    @MockitoBean
    private RecruitmentRequestService recruitmentRequestService;
    @MockitoBean
    private TrainingService trainingService;

    @Test
    void managerAndStaffCannotApprove() throws Exception {
        mockMvc.perform(post("/admin/resignation/7/items/101/approve").with(user(actor(RoleName.MANAGER))).with(csrf()))
                .andExpect(redirectedUrl("/dashboard/manager"));
        mockMvc.perform(post("/admin/resignation/7/items/101/reject").with(user(actor(RoleName.STAFF))).with(csrf()))
                .andExpect(redirectedUrl("/dashboard/staff"));
        mockMvc.perform(post("/admin/resignation/7/approve-pending").with(user(actor(RoleName.MANAGER))).with(csrf()))
                .andExpect(redirectedUrl("/dashboard/manager"));

        verify(resignationProposalService, never()).approveItem(any(), any(), any());
        verify(resignationProposalService, never()).rejectItem(any(), any(), any(), any());
        verify(resignationProposalService, never()).approvePending(any(), any());
    }

    @Test
    void staffCannotOpenManagerResignationPages() throws Exception {
        mockMvc.perform(get("/manager/resignation").with(user(actor(RoleName.STAFF))))
                .andExpect(redirectedUrl("/dashboard/staff"));
        mockMvc.perform(post("/manager/resignation/create").with(user(actor(RoleName.STAFF))).with(csrf()))
                .andExpect(redirectedUrl("/dashboard/staff"));
        verify(resignationProposalService, never()).create(any(), any());
    }

    @Test
    void adminCanOpenProposalsFromEveryStore() throws Exception {
        when(resignationProposalService.listForAdmin(any(), any(), any(), any())).thenReturn(List.of(
                ResignationProposalResponse.builder()
                        .id(7).title("Store A").storeName("Store A").managerName("Lan")
                        .status(ResignationProposalStatus.PENDING).statusLabel("Chờ duyệt").build(),
                ResignationProposalResponse.builder()
                        .id(8).title("Store B").storeName("Store B").managerName("Minh")
                        .status(ResignationProposalStatus.COMPLETED).statusLabel("Đã xử lý").build()));
        when(storeService.getStores(any())).thenReturn(List.of());

        mockMvc.perform(get("/admin/resignation").with(user(actor(RoleName.ADMIN))))
                .andExpect(status().isOk());
    }

    private AuthenticatedUser actor(RoleName roleName) {
        Role role = Role.builder().id(1).roleName(roleName).build();
        return AuthenticatedUser.from(User.builder().id(1).username("user").passwordHash("x").role(role).isActive(true).build());
    }
}

package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.enums.StoreStatus;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.response.ManagerProvisionResult;
import com.example.coffee_hrm.dto.response.StoreResponse;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.security.DatabaseUserDetailsService;
import com.example.coffee_hrm.security.RoleBasedRedirector;
import com.example.coffee_hrm.security.SecurityConfig;
import com.example.coffee_hrm.service.NotificationService;
import com.example.coffee_hrm.service.RecruitmentRequestService;
import com.example.coffee_hrm.service.ResignationProposalService;
import com.example.coffee_hrm.service.StoreManagerAccountService;
import com.example.coffee_hrm.service.StoreService;
import com.example.coffee_hrm.service.TrainingService;
import com.example.coffee_hrm.service.impl.StoreManagerAccountServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(StoreManagerController.class)
@Import({SecurityConfig.class, RoleBasedRedirector.class})
class StoreManagerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StoreManagerAccountService storeManagerAccountService;
    @MockitoBean
    private StoreService storeService;
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
    void adminSeesAssignedStoreWithoutPasswordField() throws Exception {
        when(storeService.getStore(any(), eq(5))).thenReturn(openStore());

        mockMvc.perform(get("/admin/stores/5/managers/new").with(user(actor(RoleName.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/store-manager-create"))
                .andExpect(content().string(containsString("Coffee Nguyễn Trãi")))
                .andExpect(content().string(containsString("Cửa hàng được phân công")))
                .andExpect(content().string(containsString("Thêm Manager")))
                .andExpect(content().string(not(containsString("name=\"password\""))))
                .andExpect(content().string(not(containsString("name=\"temporaryPassword\""))));
    }

    @Test
    void adminViewsAssignedManager() throws Exception {
        when(storeService.getStore(any(), eq(5))).thenReturn(StoreResponse.builder()
                .id(5)
                .storeName("Coffee Nguyễn Trãi")
                .address("12 Nguyễn Trãi")
                .status(StoreStatus.ACTIVE)
                .statusLabel("Hoạt động")
                .managerName("Lan Nguyen")
                .managerEmail("lan@coffee.vn")
                .managerPhone("0901234567")
                .managerStatusLabel("Đang làm việc")
                .managerAssignmentLabel("Đã phân công")
                .build());

        mockMvc.perform(get("/admin/stores/5/managers").with(user(actor(RoleName.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/store-manager-view"))
                .andExpect(content().string(containsString("Xem Manager")))
                .andExpect(content().string(containsString("Lan Nguyen")))
                .andExpect(content().string(containsString("lan@coffee.vn")))
                .andExpect(content().string(containsString("0901234567")))
                .andExpect(content().string(not(containsString("Gửi lại email"))));
    }

    @Test
    void viewWithoutManagerReturnsToTheList() throws Exception {
        when(storeService.getStore(any(), eq(5))).thenReturn(openStore());

        mockMvc.perform(get("/admin/stores/5/managers").with(user(actor(RoleName.ADMIN))))
                .andExpect(redirectedUrl("/admin/stores"))
                .andExpect(flash().attribute("errorMessage", "Cửa hàng chưa có Manager."));
    }

    @Test
    void managerCannotOpenTheForm() throws Exception {
        mockMvc.perform(get("/admin/stores/5/managers/new").with(user(actor(RoleName.MANAGER))))
                .andExpect(redirectedUrl("/dashboard/manager"));
        verify(storeService, never()).getStore(any(), any());
    }

    @Test
    void successfulCreateShowsSuccessToastData() throws Exception {
        when(storeService.getStore(any(), eq(5))).thenReturn(openStore());
        when(storeManagerAccountService.createManager(any(), eq(5), any())).thenReturn(ManagerProvisionResult.builder()
                .managerName("Lan Nguyen").storeName("Coffee Nguyễn Trãi").emailSent(true).build());

        mockMvc.perform(post("/admin/stores/5/managers").with(user(actor(RoleName.ADMIN))).with(csrf())
                        .param("fullName", "Lan Nguyen")
                        .param("email", "lan@coffee.vn")
                        .param("phone", "0901234567"))
                .andExpect(redirectedUrl("/admin/stores"))
                .andExpect(flash().attribute("successMessage",
                        containsString("gửi email thông tin đăng nhập")));
    }

    @Test
    void emailFailureIsNotReportedAsSuccess() throws Exception {
        when(storeService.getStore(any(), eq(5))).thenReturn(openStore());
        when(storeManagerAccountService.createManager(any(), eq(5), any())).thenReturn(ManagerProvisionResult.builder()
                .managerName("Lan Nguyen").storeName("Coffee Nguyễn Trãi").emailSent(false).build());

        mockMvc.perform(post("/admin/stores/5/managers").with(user(actor(RoleName.ADMIN))).with(csrf())
                        .param("fullName", "Lan Nguyen")
                        .param("email", "lan@coffee.vn")
                        .param("phone", "0901234567"))
                .andExpect(redirectedUrl("/admin/stores"))
                .andExpect(flash().attribute("warningMessage", containsString("gửi email thất bại")))
                .andExpect(flash().attribute("warningMessage", containsString("không bị xóa")));
    }

    @Test
    void duplicateEmailStaysOnTheForm() throws Exception {
        when(storeService.getStore(any(), eq(5))).thenReturn(openStore());
        when(storeManagerAccountService.createManager(any(), eq(5), any()))
                .thenThrow(new BusinessException(StoreManagerAccountServiceImpl.EMAIL_EXISTS));

        mockMvc.perform(post("/admin/stores/5/managers").with(user(actor(RoleName.ADMIN))).with(csrf())
                        .param("fullName", "Lan Nguyen")
                        .param("email", "lan@coffee.vn")
                        .param("phone", "0901234567"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/store-manager-create"))
                .andExpect(content().string(containsString("Email đã được sử dụng.")));
    }

    private StoreResponse openStore() {
        return StoreResponse.builder()
                .id(5)
                .storeName("Coffee Nguyễn Trãi")
                .address("12 Nguyễn Trãi")
                .status(StoreStatus.ACTIVE)
                .statusLabel("Hoạt động")
                .stateKey("active")
                .isActive(true)
                .build();
    }

    private AuthenticatedUser actor(RoleName roleName) {
        Role role = Role.builder().id(1).roleName(roleName).build();
        return AuthenticatedUser.from(User.builder().id(1).username("user").passwordHash("x").role(role)
                .isActive(true).build());
    }
}

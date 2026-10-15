package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.enums.StoreStatus;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.request.CreateStoreRequest;
import com.example.coffee_hrm.dto.response.StoreResponse;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.security.DatabaseUserDetailsService;
import com.example.coffee_hrm.security.RoleBasedRedirector;
import com.example.coffee_hrm.security.SecurityConfig;
import com.example.coffee_hrm.service.NotificationService;
import com.example.coffee_hrm.service.RecruitmentRequestService;
import com.example.coffee_hrm.service.StoreService;
import com.example.coffee_hrm.service.TrainingService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(StoreController.class)
@Import({SecurityConfig.class, RoleBasedRedirector.class})
class StoreControllerCreateTest {

    @Autowired
    private MockMvc mockMvc;

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
    void adminOpensCreateFormWithDefaults() throws Exception {
        mockMvc.perform(get("/admin/stores/create").with(user(actor(RoleName.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/store-create"))
                .andExpect(model().attribute("storeForm", allOf(
                        hasProperty("totalLeaveDays", is(12)),
                        hasProperty("openingDate", nullValue()))))
                .andExpect(content().string(containsString("Tạo cửa hàng mới")))
                .andExpect(content().string(containsString("Ngày khai trương")))
                .andExpect(content().string(containsString("Chưa chỉ định")));
    }

    @Test
    void managerAndStaffCannotOpenCreateForm() throws Exception {
        mockMvc.perform(get("/admin/stores/create").with(user(actor(RoleName.MANAGER))))
                .andExpect(redirectedUrl("/dashboard/manager"));
        mockMvc.perform(get("/admin/stores/create").with(user(actor(RoleName.STAFF))))
                .andExpect(redirectedUrl("/dashboard/staff"));
    }

    @Test
    void anonymousIsSentToLogin() throws Exception {
        mockMvc.perform(get("/admin/stores/create"))
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void managerAndStaffCannotCreateStore() throws Exception {
        for (RoleName role : new RoleName[]{RoleName.MANAGER, RoleName.STAFF}) {
            mockMvc.perform(post("/admin/stores/create").with(user(actor(role))).with(csrf())
                            .param("storeName", "Store X")
                            .param("address", "X")
                            .param("totalLeaveDays", "12")
                            .param("openingDate", "20/10/2026"))
                    .andExpect(status().is3xxRedirection());
        }
        verify(storeService, never()).createStore(any(), any());
    }

    @Test
    void createRequiresCsrfToken() throws Exception {
        mockMvc.perform(post("/admin/stores/create").with(user(actor(RoleName.ADMIN)))
                        .param("storeName", "Store X")
                        .param("address", "X")
                        .param("totalLeaveDays", "12")
                        .param("openingDate", "20/10/2026"))
                .andExpect(status().is3xxRedirection());
        verify(storeService, never()).createStore(any(), any());
    }

    @Test
    void adminCreatesStoreWithTrimmedInput() throws Exception {
        when(storeService.createStore(any(), any())).thenReturn(StoreResponse.builder()
                .id(5).storeName("Coffee Nguyễn Trãi").address("12 Nguyễn Trãi").totalLeaveDays(12).isActive(true)
                .employeeCount(0).build());

        mockMvc.perform(post("/admin/stores/create").with(user(actor(RoleName.ADMIN))).with(csrf())
                        .param("storeName", "   Coffee Nguyễn Trãi   ")
                        .param("address", "  12 Nguyễn Trãi ")
                        .param("totalLeaveDays", "12")
                        .param("openingDate", "20/10/2026")
                        .param("latitude", " 21.028511 ")
                        .param("longitude", " 105.854167 "))
                .andExpect(redirectedUrl("/admin/stores"))
                .andExpect(flash().attribute("successMessage", containsString("Coffee Nguyễn Trãi")));

        ArgumentCaptor<CreateStoreRequest> captor = ArgumentCaptor.forClass(CreateStoreRequest.class);
        verify(storeService).createStore(any(AuthenticatedUser.class), captor.capture());
        assertEquals("Coffee Nguyễn Trãi", captor.getValue().getStoreName());
        assertEquals("12 Nguyễn Trãi", captor.getValue().getAddress());
        assertEquals("21.028511", captor.getValue().getLatitude());
        assertEquals("105.854167", captor.getValue().getLongitude());
        assertEquals(java.time.LocalDate.of(2026, 10, 20), captor.getValue().getOpeningDate());
    }

    @Test
    void blankNameAndAddressAreRejectedBeforeService() throws Exception {
        mockMvc.perform(post("/admin/stores/create").with(user(actor(RoleName.ADMIN))).with(csrf())
                        .param("storeName", "   ")
                        .param("address", "")
                        .param("totalLeaveDays", "12")
                        .param("openingDate", "20/10/2026"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/store-create"))
                .andExpect(model().attributeHasFieldErrors("storeForm", "storeName", "address"))
                .andExpect(content().string(containsString("Vui lòng nhập tên cửa hàng")));
        verify(storeService, never()).createStore(any(), any());
    }

    @Test
    void tooLongNameAndNegativeLeaveDaysAreRejected() throws Exception {
        mockMvc.perform(post("/admin/stores/create").with(user(actor(RoleName.ADMIN))).with(csrf())
                        .param("storeName", "a".repeat(101))
                        .param("address", "X")
                        .param("totalLeaveDays", "-1")
                        .param("openingDate", "20/10/2026"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("storeForm", "storeName", "totalLeaveDays"));
        verify(storeService, never()).createStore(any(), any());
    }

    @Test
    void duplicateNameIsShownOnForm() throws Exception {
        when(storeService.createStore(any(), any())).thenThrow(new BusinessException("Tên cửa hàng đã tồn tại"));

        mockMvc.perform(post("/admin/stores/create").with(user(actor(RoleName.ADMIN))).with(csrf())
                        .param("storeName", "Store A")
                        .param("address", "X")
                        .param("totalLeaveDays", "12")
                        .param("openingDate", "20/10/2026")
                        .param("latitude", "21.028511")
                        .param("longitude", "105.854167"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/store-create"))
                .andExpect(model().attribute("errorMessage", "Tên cửa hàng đã tồn tại"));
        verify(storeService).createStore(any(), any());
    }

    @Test
    void missingOpeningDateIsRejectedOnTheField() throws Exception {
        mockMvc.perform(post("/admin/stores/create").with(user(actor(RoleName.ADMIN))).with(csrf())
                        .param("storeName", "Store A")
                        .param("address", "12 Nguyễn Trãi")
                        .param("totalLeaveDays", "12")
                        .param("latitude", "21.028511")
                        .param("longitude", "105.854167"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("storeForm", "openingDate"))
                .andExpect(content().string(containsString("Vui lòng chọn ngày khai trương")));
        verify(storeService, never()).createStore(any(), any());
    }

    @Test
    void adminDeactivatesStoreThroughStatusEndpoint() throws Exception {
        when(storeService.changeStatus(any(), eq(5), eq("deactivate"))).thenReturn(StoreResponse.builder()
                .id(5).storeName("Coffee Nguyễn Trãi").status(StoreStatus.INACTIVE)
                .statusLabel("Ngừng hoạt động").isActive(false).build());

        mockMvc.perform(post("/admin/stores/5/status").with(user(actor(RoleName.ADMIN))).with(csrf())
                        .param("action", "deactivate"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ngừng hoạt động")))
                .andExpect(content().string(containsString("Coffee Nguyễn Trãi")));
    }

    @Test
    void managerCannotChangeStoreStatus() throws Exception {
        mockMvc.perform(post("/admin/stores/5/status").with(user(actor(RoleName.MANAGER))).with(csrf())
                        .param("action", "deactivate"))
                .andExpect(redirectedUrl("/dashboard/manager"));
        verify(storeService, never()).changeStatus(any(), any(), any());
    }

    private AuthenticatedUser actor(RoleName roleName) {
        Role role = Role.builder().id(1).roleName(roleName).build();
        return AuthenticatedUser.from(User.builder().id(1).username("user").passwordHash("x").role(role).isActive(true).build());
    }
}

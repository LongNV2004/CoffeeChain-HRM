package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.RecruitmentStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.dto.response.EmployeeResponse;
import com.example.coffee_hrm.dto.response.StoreResponse;
import com.example.coffee_hrm.entity.RecruitmentRequest;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.Store;
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

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({StoreController.class, AdminEmployeeController.class, RecruitmentRequestController.class})
@Import({SecurityConfig.class, RoleBasedRedirector.class})
class AdminPagesRenderTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StoreService storeService;
    @MockitoBean
    private EmployeeService employeeService;
    @MockitoBean
    private RecruitmentRequestService recruitmentRequestService;
    @MockitoBean
    private DatabaseUserDetailsService userDetailsService;
    @MockitoBean
    private NotificationService notificationService;

    @Test
    void storeListShowsCreateButtonAndUnassignedManager() throws Exception {
        when(storeService.getStores(any())).thenReturn(List.of(storeResponse()));

        mockMvc.perform(get("/admin/stores").with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/admin/stores/create")))
                .andExpect(content().string(containsString("Chưa chỉ định")))
                .andExpect(content().string(containsString("Coffee Nguyễn Trãi")));
    }

    @Test
    void storeEditPageRenders() throws Exception {
        when(storeService.getStore(any(), eq(5))).thenReturn(storeResponse());

        mockMvc.perform(get("/admin/stores/5/edit").with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cập nhật thông tin cửa hàng")))
                .andExpect(content().string(containsString("Chưa chỉ định")));
    }

    @Test
    void employeesHomeListsStoresToChoose() throws Exception {
        when(storeService.getStores(any())).thenReturn(List.of(storeResponse()));

        mockMvc.perform(get("/admin/employees").with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("— Chọn cửa hàng —")))
                .andExpect(content().string(containsString("/admin/stores/5/employees")));
    }

    @Test
    void storeEmployeesPageShowsRoleCertificateAndActions() throws Exception {
        StoreResponse store = StoreResponse.builder().id(5).storeName("Coffee Nguyễn Trãi").address("12 Nguyễn Trãi")
                .totalLeaveDays(12).isActive(true).employeeCount(2).managerName("Nguyễn Văn A").build();
        when(storeService.getStore(any(), eq(5))).thenReturn(store);
        when(storeService.getStores(any())).thenReturn(List.of(store));
        when(employeeService.getStoreEmployees(any(), eq(5))).thenReturn(List.of(
                EmployeeResponse.builder().id(1).fullName("Nguyễn Văn A").email("a@x.vn").phone("0901")
                        .storeId(5).status(EmployeeStatus.ACTIVE).roleName(RoleName.MANAGER)
                        .storeManager(true).hasCertificate(true).build(),
                EmployeeResponse.builder().id(2).fullName("Trần Thị B").email("b@x.vn").phone("0902")
                        .storeId(5).status(EmployeeStatus.ON_LEAVE).roleName(RoleName.STAFF).build(),
                EmployeeResponse.builder().id(3).fullName("Lê Văn C").email("c@x.vn")
                        .storeId(5).status(EmployeeStatus.ACTIVE).build()));

        mockMvc.perform(get("/admin/stores/5/employees").with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Có chứng chỉ")))
                .andExpect(content().string(containsString("Không có chứng chỉ")))
                .andExpect(content().string(containsString("Đang nghỉ phép")))
                .andExpect(content().string(containsString("Chuyển về Staff")))
                .andExpect(content().string(containsString("Chỉ định Manager")))
                .andExpect(content().string(containsString("Chưa có tài khoản")))
                .andExpect(content().string(containsString("/admin/stores/5/employees/1/role")))
                .andExpect(content().string(containsString("Cửa hàng đã có Manager")));
    }

    @Test
    void recruitmentListRenders() throws Exception {
        Store store = Store.builder().id(1).storeName("Store A").address("A").build();
        when(recruitmentRequestService.getAllRequests()).thenReturn(List.of(
                RecruitmentRequest.builder().id(3).store(store).requestedNumber(2).reason("Thiếu người")
                        .status(RecruitmentStatus.APPROVED).build()));

        mockMvc.perform(get("/admin/recruitment").with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("badge-ok")))
                .andExpect(content().string(containsString("/admin/recruitment/3/approve")));
    }

    private StoreResponse storeResponse() {
        return StoreResponse.builder().id(5).storeName("Coffee Nguyễn Trãi").address("12 Nguyễn Trãi")
                .totalLeaveDays(12).isActive(true).employeeCount(0).build();
    }

    private AuthenticatedUser admin() {
        Role role = Role.builder().id(1).roleName(RoleName.ADMIN).build();
        return AuthenticatedUser.from(User.builder().id(1).username("admin").passwordHash("x").role(role).isActive(true).build());
    }
}

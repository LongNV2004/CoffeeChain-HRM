package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.RecruitmentProposalStatus;
import com.example.coffee_hrm.common.enums.RecruitmentStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.enums.StoreStatus;
import com.example.coffee_hrm.dto.response.EmployeeResponse;
import com.example.coffee_hrm.dto.response.RecruitmentCandidateResponse;
import com.example.coffee_hrm.dto.response.RecruitmentRequestResponse;
import com.example.coffee_hrm.dto.response.StoreResponse;
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
import com.example.coffee_hrm.service.StoreService;
import com.example.coffee_hrm.service.TrainingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({StoreController.class, AdminEmployeeController.class, AdminRecruitmentController.class})
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
    private ResignationProposalService resignationProposalService;
    @MockitoBean
    private DatabaseUserDetailsService userDetailsService;
    @MockitoBean
    private NotificationService notificationService;
    @MockitoBean
    private TrainingService trainingService;

    @Test
    void storeListShowsCreateButtonAndUnassignedManager() throws Exception {
        when(storeService.getStores(any())).thenReturn(List.of(storeResponse()));

        mockMvc.perform(get("/admin/stores").with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/admin/stores/create")))
                .andExpect(content().string(containsString("Chưa phân công")))
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
        when(storeService.getStores(any())).thenReturn(List.of(StoreResponse.builder()
                .id(5)
                .storeName("Coffee Nguyễn Trãi")
                .address("12 Nguyễn Trãi")
                .employeeCount(4)
                .status(StoreStatus.ACTIVE)
                .statusLabel("Hoạt động")
                .stateKey("active")
                .build()));

        String html = mockMvc.perform(get("/admin/employees").with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Danh sách cửa hàng")))
                .andExpect(content().string(containsString("Coffee Nguyễn Trãi")))
                .andExpect(content().string(containsString("12 Nguyễn Trãi")))
                .andExpect(content().string(containsString("Mã cửa hàng")))
                .andExpect(content().string(containsString("Hoạt động")))
                .andExpect(content().string(containsString("Xem nhân viên")))
                .andExpect(content().string(containsString("/admin/stores/5/employees")))
                .andExpect(content().string(not(containsString("Chỉnh sửa nhân viên"))))
                .andReturn()
                .getResponse()
                .getContentAsString();

        int storeName = html.indexOf("Coffee Nguyễn Trãi");
        int viewEmployees = html.indexOf("Xem nhân viên");
        org.junit.jupiter.api.Assertions.assertTrue(storeName >= 0 && viewEmployees > storeName);
        verify(employeeService, never()).getAllEmployees(any());
    }

    @Test
    void storeEmployeesPageShowsReadOnlyRosterWithActualStatus() throws Exception {
        StoreResponse store = StoreResponse.builder().id(5).storeName("Coffee Nguyễn Trãi").address("12 Nguyễn Trãi")
                .totalLeaveDays(12).isActive(true).employeeCount(2).managerName("Nguyễn Văn A").build();
        when(storeService.getStore(any(), eq(5))).thenReturn(store);
        when(employeeService.getStoreEmployees(any(), eq(5))).thenReturn(List.of(
                EmployeeResponse.builder().id(1).fullName("Nguyễn Văn A").email("a@x.vn").phone("0901")
                        .storeId(5).status(EmployeeStatus.ACTIVE).roleName(RoleName.MANAGER)
                        .storeManager(true).hasCertificate(true).build(),
                EmployeeResponse.builder().id(2).fullName("Trần Thị B").email("b@x.vn").phone("0902")
                        .storeId(5).status(EmployeeStatus.ON_LEAVE).roleName(RoleName.STAFF).build(),
                EmployeeResponse.builder().id(3).fullName("Lê Văn C")
                        .storeId(5).status(EmployeeStatus.TERMINATED).build()));

        String html = mockMvc.perform(get("/admin/stores/5/employees").with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Coffee Nguyễn Trãi")))
                .andExpect(content().string(containsString("12 Nguyễn Trãi")))
                .andExpect(content().string(containsString("Đang làm việc")))
                .andExpect(content().string(containsString("Đang nghỉ phép")))
                .andExpect(content().string(containsString("Đã nghỉ việc")))
                .andExpect(content().string(containsString("Chưa có tài khoản")))
                .andExpect(content().string(containsString("Xem chi tiết")))
                .andExpect(content().string(containsString("/admin/stores/5/employees/1")))
                .andExpect(content().string(containsString("Chưa cập nhật")))
                .andExpect(content().string(not(containsString("Chỉnh sửa nhân viên"))))
                .andExpect(content().string(not(containsString("Lưu thay đổi"))))
                .andExpect(content().string(not(containsString("name=\"status\""))))
                .andExpect(content().string(not(containsString("password"))))
                .andReturn()
                .getResponse()
                .getContentAsString();

        org.junit.jupiter.api.Assertions.assertTrue(html.indexOf("Nguyễn Văn A") < html.indexOf("Trần Thị B"));
        org.junit.jupiter.api.Assertions.assertTrue(html.indexOf("Trần Thị B") < html.indexOf("Lê Văn C"));
    }

    @Test
    void employeeDetailIsReadOnlyAndShowsStoredStatus() throws Exception {
        when(storeService.getStore(any(), eq(5))).thenReturn(StoreResponse.builder()
                .id(5).storeName("Coffee Nguyễn Trãi").address("12 Nguyễn Trãi").employeeCount(2).build());
        when(employeeService.getStoreEmployee(any(), eq(5), eq(2))).thenReturn(EmployeeResponse.builder()
                .id(2)
                .fullName("Trần Thị B")
                .storeId(5)
                .storeName("Coffee Nguyễn Trãi")
                .status(EmployeeStatus.ON_LEAVE)
                .roleName(RoleName.STAFF)
                .build());

        mockMvc.perform(get("/admin/stores/5/employees/2").with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Trần Thị B")))
                .andExpect(content().string(containsString("Đang nghỉ phép")))
                .andExpect(content().string(containsString("Chưa cập nhật")))
                .andExpect(content().string(containsString("/admin/stores/5/employees")))
                .andExpect(content().string(containsString("Quay lại danh sách nhân viên")))
                .andExpect(content().string(not(containsString("<select"))))
                .andExpect(content().string(not(containsString("name=\"status\""))))
                .andExpect(content().string(not(containsString("Lưu thay đổi"))))
                .andExpect(content().string(not(containsString("employeeEditForm"))))
                .andExpect(content().string(not(containsString("passwordHash"))));
    }

    @Test
    void recruitmentListRendersCandidateWithoutPassword() throws Exception {
        when(storeService.getStores(any())).thenReturn(List.of());
        when(recruitmentRequestService.listManagerOptions()).thenReturn(List.of());
        when(recruitmentRequestService.listForAdmin(any(), nullable(Integer.class), nullable(Integer.class),
                nullable(RecruitmentProposalStatus.class), nullable(java.time.LocalDate.class), nullable(java.time.LocalDate.class)))
                .thenReturn(List.of(RecruitmentRequestResponse.builder()
                        .id(3)
                        .title("Tuyển barista")
                        .storeName("Store A")
                        .managerName("Manager A")
                        .status(RecruitmentProposalStatus.PENDING)
                        .statusLabel("Chờ duyệt")
                        .totalCandidates(1)
                        .pendingCount(1)
                        .createdAt(LocalDateTime.of(2026, 10, 4, 8, 0))
                        .build()));

        mockMvc.perform(get("/admin/recruitment").with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tuyển barista")))
                .andExpect(content().string(containsString("Store A")))
                .andExpect(content().string(containsString("Manager A")))
                .andExpect(content().string(containsString("Chờ 1")))
                .andExpect(content().string(containsString("badge-wait")))
                .andExpect(content().string(containsString("/admin/recruitment/3")))
                .andExpect(content().string(not(containsString("name=\"password\""))))
                .andExpect(content().string(not(containsString("/approve"))));
    }

    @Test
    void recruitmentDetailOffersApproveAndRejectWithoutPassword() throws Exception {
        when(recruitmentRequestService.getForAdmin(any(), eq(3))).thenReturn(RecruitmentRequestResponse.builder()
                .id(3)
                .title("Tuyển barista")
                .storeName("Store A")
                .managerName("Manager A")
                .status(RecruitmentProposalStatus.PARTIALLY_PROCESSED)
                .statusLabel("Đang xử lý")
                .totalCandidates(2)
                .pendingCount(1)
                .approvedCount(1)
                .createdAt(LocalDateTime.of(2026, 10, 4, 8, 0))
                .candidates(List.of(
                        RecruitmentCandidateResponse.builder()
                                .id(9)
                                .fullName("Nguyễn Văn B")
                                .email("b@store.vn")
                                .phone("0901234567")
                                .status(RecruitmentStatus.PENDING)
                                .statusLabel("Chờ duyệt")
                                .build(),
                        RecruitmentCandidateResponse.builder()
                                .id(10)
                                .fullName("Đã duyệt rồi")
                                .email("done@store.vn")
                                .phone("0900000000")
                                .status(RecruitmentStatus.APPROVED)
                                .statusLabel("Đã duyệt")
                                .build()))
                .build());

        mockMvc.perform(get("/admin/recruitment/3").with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nguyễn Văn B")))
                .andExpect(content().string(containsString("b@store.vn")))
                .andExpect(content().string(containsString("/admin/recruitment/3/candidates/9/approve")))
                .andExpect(content().string(containsString("/admin/recruitment/3/candidates/9/reject")))
                .andExpect(content().string(not(containsString("/admin/recruitment/3/candidates/10/approve"))))
                .andExpect(content().string(not(containsString("/admin/recruitment/3/candidates/10/reject"))))
                .andExpect(content().string(containsString(">Duyệt</button>")))
                .andExpect(content().string(containsString(">Từ chối</button>")))
                .andExpect(content().string(containsString("data-coffee-reason=\"rejectReason\"")))
                .andExpect(content().string(not(containsString("name=\"rejectReason\""))))
                .andExpect(content().string(not(containsString("<textarea"))))
                .andExpect(content().string(not(containsString("name=\"password\""))));
    }

    @Test
    void managerCannotApproveRecruitment() throws Exception {
        mockMvc.perform(post("/admin/recruitment/3/candidates/9/approve").with(user(manager())).with(csrf()))
                .andExpect(redirectedUrl("/dashboard/manager"));
        verify(recruitmentRequestService, never()).approveCandidate(any(), any(), any());
    }

    private StoreResponse storeResponse() {
        return StoreResponse.builder().id(5).storeName("Coffee Nguyễn Trãi").address("12 Nguyễn Trãi")
                .totalLeaveDays(12).isActive(true).employeeCount(0).build();
    }

    private AuthenticatedUser manager() {
        Role role = Role.builder().id(2).roleName(RoleName.MANAGER).build();
        return AuthenticatedUser.from(User.builder().id(2).username("manager").passwordHash("x").role(role).isActive(true).build());
    }

    private AuthenticatedUser admin() {
        Role role = Role.builder().id(1).roleName(RoleName.ADMIN).build();
        return AuthenticatedUser.from(User.builder().id(1).username("admin").passwordHash("x").role(role).isActive(true).build());
    }
}

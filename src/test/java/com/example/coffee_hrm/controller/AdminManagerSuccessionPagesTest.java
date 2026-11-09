package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.enums.ContractValidityStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.dto.response.EmployeeResponse;
import com.example.coffee_hrm.dto.response.EmploymentContractResponse;
import com.example.coffee_hrm.dto.response.ManagerSuccession;
import com.example.coffee_hrm.dto.response.StoreResponse;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.security.DatabaseUserDetailsService;
import com.example.coffee_hrm.security.RoleBasedRedirector;
import com.example.coffee_hrm.security.SecurityConfig;
import com.example.coffee_hrm.service.EmployeeService;
import com.example.coffee_hrm.service.EmploymentContractService;
import com.example.coffee_hrm.service.NotificationService;
import com.example.coffee_hrm.service.RecruitmentRequestService;
import com.example.coffee_hrm.service.ResignationProposalService;
import com.example.coffee_hrm.service.StoreService;
import com.example.coffee_hrm.service.TrainingService;
import org.junit.jupiter.api.BeforeEach;
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

/** Giao diện Admin: thay đổi chức vụ, chấm dứt hợp đồng Manager và bổ nhiệm Manager thay thế. */
@WebMvcTest(AdminEmployeeController.class)
@Import({SecurityConfig.class, RoleBasedRedirector.class})
class AdminManagerSuccessionPagesTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StoreService storeService;
    @MockitoBean
    private EmployeeService employeeService;
    @MockitoBean
    private EmploymentContractService contractService;
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

    private final ManagerSuccession.Candidate eligible = new ManagerSuccession.Candidate(
            4, "Nghiêm Tiến Quý", "quy@shop.vn", "HD-4", LocalDate.of(2026, 11, 3), LocalDate.of(2026, 12, 3),
            "Đang hiệu lực", true, true, false, List.of());
    private final ManagerSuccession.Candidate blocked = new ManagerSuccession.Candidate(
            2, "Trần Hữu Tuấn", "tuan@shop.vn", null, null, null, null, false, true, false,
            List.of("Chưa có hợp đồng lao động"));

    @BeforeEach
    void setUp() {
        when(storeService.getStore(any(), eq(1))).thenReturn(StoreResponse.builder()
                .id(1).storeName("Coffee Cầu Giấy").address("122 Cầu Giấy").employeeCount(4).build());
    }

    @Test
    void managerPageShowsTerminationCardWithEligibleReplacements() throws Exception {
        when(employeeService.getStoreEmployee(any(), eq(1), eq(5))).thenReturn(employee(5, "Nguyễn Văn Long",
                RoleName.MANAGER, EmployeeStatus.ACTIVE, true));
        when(contractService.listForEmployee(any(), eq(5))).thenReturn(List.of(EmploymentContractResponse.builder()
                .id(8).contractCode("HD-8").employeeId(5)
                .startDate(LocalDate.of(2026, 1, 1)).endDate(LocalDate.of(2027, 1, 1))
                .validityStatus(ContractValidityStatus.DANG_HIEU_LUC).validityStatusLabel("Đang hiệu lực")
                .build()));
        when(employeeService.getManagerSuccession(any(), eq(1)))
                .thenReturn(new ManagerSuccession(true, "Nguyễn Văn Long", List.of(eligible, blocked)));

        mockMvc.perform(get("/admin/stores/1/employees/5").with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Chấm dứt hợp đồng")))
                .andExpect(content().string(containsString("HD-8")))
                .andExpect(content().string(containsString("01/01/2026 – 01/01/2027")))
                .andExpect(content().string(containsString("chuyển về Staff")))
                .andExpect(content().string(containsString("name=\"replacementEmployeeId\"")))
                .andExpect(content().string(containsString("value=\"4\"")))
                .andExpect(content().string(containsString("Nghiêm Tiến Quý · HD-4")))
                .andExpect(content().string(not(containsString("value=\"2\">Trần Hữu Tuấn"))))
                .andExpect(content().string(containsString("Chuyển về Staff")))
                // Cửa hàng còn Manager: chưa hiện bảng bổ nhiệm thay thế.
                .andExpect(content().string(not(containsString("bổ nhiệm người thay thế</h2>"))));
    }

    @Test
    void staffPageDisablesAppointmentUntilContractConditionIsMet() throws Exception {
        when(employeeService.getStoreEmployee(any(), eq(1), eq(2))).thenReturn(employee(2, "Trần Hữu Tuấn",
                RoleName.STAFF, EmployeeStatus.PENDING_CONTRACT, true));
        when(employeeService.getManagerSuccession(any(), eq(1)))
                .thenReturn(new ManagerSuccession(false, null, List.of(eligible, blocked)));

        mockMvc.perform(get("/admin/stores/1/employees/2").with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Vị trí quản lý đang trống.")))
                .andExpect(content().string(containsString("Chưa có hợp đồng lao động.")))
                .andExpect(content().string(containsString("disabled=\"disabled\">Bổ nhiệm làm Manager")))
                .andExpect(content().string(not(containsString("name=\"replacementEmployeeId\""))));
    }

    @Test
    void eligibleStaffPageCanBeAppointed() throws Exception {
        when(employeeService.getStoreEmployee(any(), eq(1), eq(4))).thenReturn(employee(4, "Nghiêm Tiến Quý",
                RoleName.STAFF, EmployeeStatus.ACTIVE, true));
        when(employeeService.getManagerSuccession(any(), eq(1)))
                .thenReturn(new ManagerSuccession(false, null, List.of(eligible, blocked)));

        mockMvc.perform(get("/admin/stores/1/employees/4").with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("HD-4 · Đang hiệu lực · đến 03/12/2026")))
                .andExpect(content().string(containsString(">Bổ nhiệm làm Manager</button>")))
                .andExpect(content().string(not(containsString("disabled=\"disabled\">Bổ nhiệm làm Manager"))));
    }

    @Test
    void storeListShowsReplacementPanelWhenStoreHasNoManager() throws Exception {
        when(employeeService.getStoreEmployees(any(), eq(1))).thenReturn(List.of());
        when(employeeService.getManagerSuccession(any(), eq(1)))
                .thenReturn(new ManagerSuccession(false, null, List.of(eligible, blocked)));

        mockMvc.perform(get("/admin/stores/1/employees").with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cửa hàng chưa có Manager — bổ nhiệm người thay thế")))
                .andExpect(content().string(containsString("/admin/stores/1/employees/4/role")))
                .andExpect(content().string(not(containsString("/admin/stores/1/employees/2/role"))))
                .andExpect(content().string(containsString("name=\"returnTo\" value=\"list\"")))
                .andExpect(content().string(containsString("Chưa có hợp đồng lao động")));
    }

    @Test
    void terminationWithReplacementAppointsTheNewManager() throws Exception {
        when(employeeService.terminateManagerContract(any(), eq(1), eq(5), eq("Nghỉ việc")))
                .thenReturn(employee(5, "Nguyễn Văn Long", RoleName.STAFF, EmployeeStatus.TERMINATED, false));
        when(employeeService.changeEmployeeRole(any(), eq(1), eq(4), eq("MANAGER")))
                .thenReturn(employee(4, "Nghiêm Tiến Quý", RoleName.MANAGER, EmployeeStatus.ACTIVE, true));

        mockMvc.perform(post("/admin/stores/1/employees/5/terminate-contract").with(user(admin())).with(csrf())
                        .param("reason", "Nghỉ việc").param("replacementEmployeeId", "4"))
                .andExpect(redirectedUrl("/admin/stores/1/employees/5"))
                .andExpect(flash().attribute("successMessage", containsString("Nghiêm Tiến Quý đã được bổ nhiệm làm Manager thay thế")));
    }

    @Test
    void failedTerminationDoesNotAppointAReplacement() throws Exception {
        when(employeeService.terminateManagerContract(any(), eq(1), eq(5), any()))
                .thenThrow(new com.example.coffee_hrm.common.exception.BusinessException("Quản lý đã nghỉ việc."));

        mockMvc.perform(post("/admin/stores/1/employees/5/terminate-contract").with(user(admin())).with(csrf())
                        .param("reason", "Nghỉ việc").param("replacementEmployeeId", "4"))
                .andExpect(redirectedUrl("/admin/stores/1/employees/5"))
                .andExpect(flash().attribute("errorMessage", "Quản lý đã nghỉ việc."));
        verify(employeeService, never()).changeEmployeeRole(any(), any(), any(), any());
    }

    @Test
    void appointmentFromListReturnsToTheList() throws Exception {
        when(employeeService.changeEmployeeRole(any(), eq(1), eq(4), eq("MANAGER")))
                .thenReturn(employee(4, "Nghiêm Tiến Quý", RoleName.MANAGER, EmployeeStatus.ACTIVE, true));

        mockMvc.perform(post("/admin/stores/1/employees/4/role").with(user(admin())).with(csrf())
                        .param("role", "MANAGER").param("returnTo", "list"))
                .andExpect(redirectedUrl("/admin/stores/1/employees"));
        mockMvc.perform(post("/admin/stores/1/employees/4/role").with(user(admin())).with(csrf())
                        .param("role", "MANAGER").param("returnEmployeeId", "5"))
                .andExpect(redirectedUrl("/admin/stores/1/employees/5"));
    }

    @Test
    void employeeDetailShowsAndEditsDateOfBirth() throws Exception {
        EmployeeResponse withBirthDate = EmployeeResponse.builder()
                .id(4).fullName("Nghiêm Tiến Quý").storeId(1).storeName("Coffee Cầu Giấy")
                .status(EmployeeStatus.ACTIVE).roleName(RoleName.STAFF).accountActive(true)
                .dateOfBirth(LocalDate.of(2000, 5, 2)).build();
        when(employeeService.getStoreEmployee(any(), eq(1), eq(4))).thenReturn(withBirthDate);

        mockMvc.perform(get("/admin/stores/1/employees/4").with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<dt>Ngày sinh</dt>")))
                .andExpect(content().string(containsString("02/05/2000")))
                .andExpect(content().string(containsString("/admin/stores/1/employees/4/date-of-birth")))
                .andExpect(content().string(containsString("value=\"2000-05-02\"")));

        mockMvc.perform(post("/admin/stores/1/employees/4/date-of-birth").with(user(admin())).with(csrf())
                        .param("dateOfBirth", "1999-12-31"))
                .andExpect(redirectedUrl("/admin/stores/1/employees/4"))
                .andExpect(flash().attribute("successMessage", "Đã cập nhật ngày sinh."));
        verify(employeeService).updateDateOfBirth(any(), eq(1), eq(4), eq(LocalDate.of(1999, 12, 31)));

        mockMvc.perform(post("/admin/stores/1/employees/4/date-of-birth").with(user(admin())).with(csrf())
                        .param("dateOfBirth", "31/12/1999"))
                .andExpect(flash().attribute("errorMessage", "Ngày sinh không hợp lệ."));
    }

    private EmployeeResponse employee(int id, String name, RoleName role, EmployeeStatus status, boolean active) {
        return EmployeeResponse.builder()
                .id(id).fullName(name).storeId(1).storeName("Coffee Cầu Giấy")
                .status(status).roleName(role).accountActive(active)
                .storeManager(role == RoleName.MANAGER && status != EmployeeStatus.TERMINATED)
                .build();
    }

    private AuthenticatedUser admin() {
        Role role = Role.builder().id(1).roleName(RoleName.ADMIN).build();
        return AuthenticatedUser.from(User.builder().id(1).username("admin").passwordHash("x").role(role).isActive(true).build());
    }
}

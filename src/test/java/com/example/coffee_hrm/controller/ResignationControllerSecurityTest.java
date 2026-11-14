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
import com.example.coffee_hrm.service.ResignationSettlementService;
import com.example.coffee_hrm.service.StoreService;
import com.example.coffee_hrm.service.TrainingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.coffee_hrm.common.enums.ResignationItemStatus;
import com.example.coffee_hrm.dto.response.ResignationItemResponse;
import com.example.coffee_hrm.dto.response.ResignationSettlementView;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private ResignationSettlementService settlementService;
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

    @Test
    void managerAndStaffCannotPaySalaryFromTheProposal() throws Exception {
        mockMvc.perform(post("/admin/resignation/7/items/101/pay").param("reportId", "4")
                        .with(user(actor(RoleName.MANAGER))).with(csrf()))
                .andExpect(redirectedUrl("/dashboard/manager"));
        mockMvc.perform(post("/admin/resignation/7/items/101/pay").param("reportId", "4")
                        .with(user(actor(RoleName.STAFF))).with(csrf()))
                .andExpect(redirectedUrl("/dashboard/staff"));
        verify(settlementService, never()).pay(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(), any());
    }

    @Test
    void adminDetailShowsSettlementAndDisablesApprovalWhileSalaryIsOwed() throws Exception {
        ResignationItemResponse item = ResignationItemResponse.builder()
                .id(101).employeeId(11).employeeName("An Nguyễn").positionLabel("Nhân viên").storeId(4)
                .storeName("Store A").currentStatusLabel("Đang làm việc").reason("Hết nhu cầu")
                .status(ResignationItemStatus.PENDING).statusLabel("Chờ duyệt").build();
        when(resignationProposalService.getForAdmin(any(), eq(7))).thenReturn(ResignationProposalResponse.builder()
                .id(7).title("Giảm biên").storeName("Store A").managerName("Lan")
                .status(ResignationProposalStatus.PENDING).statusLabel("Chờ duyệt")
                .totalCount(1).pendingCount(1).items(List.of(item)).build());
        ResignationSettlementView.Period period = ResignationSettlementView.Period.builder()
                .reportId(4).storeName("Store A").periodLabel("Tháng 10/2026").payrollStatusLabel("Đã chốt")
                .reportStatusLabel("Đã duyệt").finalized(true).totalLabel("225.000 đ").paidLabel("100.000 đ")
                .remainingLabel("125.000 đ").remainingValue("125000").payable(true).statusKey("PARTIAL")
                .statusLabel("Đã trả một phần").statusCss("badge-partial").build();
        when(settlementService.settlement(any(), eq(11))).thenReturn(ResignationSettlementView.builder()
                .employeeId(11)
                .contract(ResignationSettlementView.ContractInfo.builder().contractCode("HD-11").termLabel("Xác định thời hạn")
                        .startDate(LocalDate.of(2026, 1, 1)).endDate(LocalDate.of(2026, 12, 31)).rateLabel("25.000 đ/giờ").build())
                .total(new BigDecimal("225000")).paid(new BigDecimal("100000")).remaining(new BigDecimal("125000"))
                .totalLabel("225.000 đ").paidLabel("100.000 đ").remainingLabel("125.000 đ")
                .periodCount(1).owedCount(1).eligible(false)
                .statusKey(ResignationSettlementView.OWED).statusLabel("Còn lương chưa thanh toán").statusCss("badge-off")
                .reasons(List.of("Còn 125.000 đ lương đã xác định chưa thanh toán (1 kỳ)."))
                .periods(List.of(period))
                .payments(List.of(ResignationSettlementView.Payment.builder().paymentId(1).reportId(4)
                        .periodLabel("Tháng 10/2026").storeName("Store A").amountLabel("100.000 đ")
                        .paidDate(LocalDate.of(2026, 11, 2)).methodLabel("Chuyển khoản").transactionCode("FT1")
                        .paidByName("Admin").note("").build()))
                .requestKey("key").today(LocalDate.of(2026, 11, 11))
                .build());

        String html = mockMvc.perform(get("/admin/resignation/7").with(user(actor(RoleName.ADMIN))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertTrue(html.contains("Đối soát lương trước khi chấm dứt"));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("Còn 125.000 đ lương đã xác định chưa thanh toán"));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("/admin/resignation/7/items/101/pay"));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("HD-11"));
        org.junit.jupiter.api.Assertions.assertTrue(
                html.matches("(?s).*<button class=\"primary-btn btn-sm\" type=\"submit\"[^>]*disabled[^>]*>Duyệt chấm dứt hợp đồng</button>.*"));
    }

    private AuthenticatedUser actor(RoleName roleName) {
        Role role = Role.builder().id(1).roleName(roleName).build();
        return AuthenticatedUser.from(User.builder().id(1).username("user").passwordHash("x").role(role).isActive(true).build());
    }
}

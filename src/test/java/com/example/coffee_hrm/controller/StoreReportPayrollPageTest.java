package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.response.AdminAttendanceReportDetail;
import com.example.coffee_hrm.dto.response.AdminAttendanceReportPage;
import com.example.coffee_hrm.dto.response.ManagerAttendanceReportPage;
import com.example.coffee_hrm.dto.response.PayrollBoardView;
import com.example.coffee_hrm.dto.response.PayrollPaymentViews;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.EmploymentContract;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.EmploymentContractRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.security.DatabaseUserDetailsService;
import com.example.coffee_hrm.security.RoleBasedRedirector;
import com.example.coffee_hrm.security.SecurityConfig;
import com.example.coffee_hrm.service.AttendanceReportService;
import com.example.coffee_hrm.service.NotificationService;
import com.example.coffee_hrm.service.PayrollPaymentService;
import com.example.coffee_hrm.service.PayrollService;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** "Báo cáo cửa hàng": Manager gửi báo cáo chấm công và lương, Admin duyệt và thanh toán. */
@WebMvcTest({AttendanceReportController.class, SalaryPageController.class})
@Import({SecurityConfig.class, RoleBasedRedirector.class})
class StoreReportPayrollPageTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AttendanceReportService attendanceReportService;
    @MockitoBean
    private PayrollService payrollService;
    @MockitoBean
    private PayrollPaymentService payrollPaymentService;
    @MockitoBean
    private StoreService storeService;
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
    @MockitoBean
    private TrainingService trainingService;

    @BeforeEach
    void setUp() {
        when(employmentContractRepository.findByEmployeeId(anyInt())).thenReturn(List.of(EmploymentContract.builder()
                .id(1).contractCode("HD-1").startDate(VietnamTime.today().minusMonths(1))
                .endDate(VietnamTime.today().plusMonths(6)).salary(new BigDecimal("30000")).build()));
    }

    @Test
    void adminListShowsMoneyStatusesAndFilters() throws Exception {
        AdminAttendanceReportPage page = AdminAttendanceReportPage.builder()
                .year(2026).month(10).periodLabel("10/2026").filterStatus("all").stores(List.of()).rows(List.of())
                .build();
        when(attendanceReportService.adminPage(any(), eq(2026), eq(10), any(), any())).thenReturn(page);
        when(payrollPaymentService.adminReports(any(), eq(page), eq("CHUA_THANH_TOAN"), eq("cau")))
                .thenReturn(PayrollPaymentViews.ReportList.builder()
                        .year(2026).month(10).periodLabel("10/2026").filterPaymentStatus("CHUA_THANH_TOAN").query("cau")
                        .waitingReports(1).approvedReports(1).totalStores(2).unsentStores(0)
                        .totalPayrollLabel("325.000 đ").totalPaidLabel("25.000 đ").totalRemainingLabel("300.000 đ")
                        .rows(List.of(PayrollPaymentViews.ReportRow.builder()
                                .reportId(4).storeId(1).storeName("Cầu Giấy").periodLabel("10/2026").senderName("Quý")
                                .submittedAt(LocalDateTime.of(2026, 11, 1, 9, 0)).submitted(true).hasPayroll(true)
                                .employeeCount(2).totalLabel("325.000 đ").paidLabel("25.000 đ").remainingLabel("300.000 đ")
                                .statusCode("APPROVED").statusLabel("✓ Đã duyệt").statusCss("status-approved")
                                .paymentStatusKey("CHUA_THANH_TOAN").paymentStatusLabel("Chưa thanh toán").build()))
                        .build());

        mockMvc.perform(get("/attendance/admin/reports").param("year", "2026").param("month", "10")
                        .param("paymentStatus", "CHUA_THANH_TOAN").param("q", "cau").with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h1>Báo cáo cửa hàng</h1>")))
                .andExpect(content().string(containsString("Cầu Giấy")))
                .andExpect(content().string(containsString("01/11/2026 09:00")))
                .andExpect(content().string(containsString("300.000 đ")))
                .andExpect(content().string(containsString("Chưa thanh toán")))
                .andExpect(content().string(containsString("name=\"paymentStatus\"")))
                .andExpect(content().string(containsString("name=\"q\" value=\"cau\"")))
                .andExpect(content().string(containsString("href=\"/attendance/admin/reports/4\"")))
                // Menu Admin: "Báo cáo cửa hàng", không còn "Quản lý lương" hay "Báo cáo toàn chuỗi".
                .andExpect(content().string(not(containsString("Quản lý lương"))))
                .andExpect(content().string(not(containsString("Báo cáo toàn chuỗi"))));
    }

    @Test
    void adminDetailOffersApprovalWhileWaiting() throws Exception {
        when(attendanceReportService.adminDetail(any(), eq(4), any(), any(), any())).thenReturn(detail("WAITING"));
        when(payrollPaymentService.reportPayroll(any(), eq(4))).thenReturn(payroll(false));

        mockMvc.perform(get("/attendance/admin/reports/4").with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("action=\"/attendance/admin/reports/4/approve\"")))
                .andExpect(content().string(containsString("Trả lại báo cáo")))
                .andExpect(content().string(containsString("B. Bảng lương và thanh toán")))
                .andExpect(content().string(containsString("Lương cơ bản")))
                .andExpect(content().string(containsString("250.000 đ")))
                .andExpect(content().string(containsString("Chỉ thanh toán được báo cáo đã được duyệt.")))
                .andExpect(content().string(not(containsString("/pay-all"))));
    }

    @Test
    void adminDetailOffersPaymentOnceApproved() throws Exception {
        when(attendanceReportService.adminDetail(any(), eq(4), any(), any(), any())).thenReturn(detail("APPROVED"));
        when(payrollPaymentService.reportPayroll(any(), eq(4))).thenReturn(payroll(true));

        mockMvc.perform(get("/attendance/admin/reports/4").with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/approve\""))))
                .andExpect(content().string(containsString("action=\"/attendance/admin/reports/4/pay-all\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("name=\"proof\""))))
                .andExpect(content().string(containsString("name=\"requestKey\" value=\"rk-1\"")))
                .andExpect(content().string(containsString("name=\"expectedCount\" value=\"2\"")))
                .andExpect(content().string(containsString("Thanh toán toàn bộ (325.000 đ cho 2 nhân viên)")))
                .andExpect(content().string(containsString("Chi tiết / Thanh toán")));
    }

    @Test
    void adminApprovesAndPays() throws Exception {
        mockMvc.perform(post("/attendance/admin/reports/4/approve").with(user(admin())).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/attendance/admin/reports/4"))
                .andExpect(flash().attribute("success", containsString("Đã duyệt báo cáo")));
        verify(attendanceReportService).approve(any(), eq(4));

        when(payrollPaymentService.payAll(any(), eq(4), any()))
                .thenReturn(new PayrollPaymentService.PayAllResult(2, "325.000 đ"));
        mockMvc.perform(multipart("/attendance/admin/reports/4/pay-all")
                        .param("method", "CHUYEN_KHOAN").param("paidDate", "2026-11-10")
                        .param("requestKey", "rk-1").param("expectedCount", "2")
                        .with(user(admin())).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/attendance/admin/reports/4#bang-luong"))
                .andExpect(flash().attribute("success", containsString("325.000 đ cho 2 nhân viên")));

        when(payrollPaymentService.payEmployee(any(), eq(4), eq(11), any()))
                .thenThrow(new BusinessException("Số tiền thanh toán vượt quá số còn phải trả (225.000 đ)."));
        mockMvc.perform(multipart("/attendance/admin/reports/4/payroll/11/pay")
                        .param("amount", "999999").param("method", "TIEN_MAT").param("requestKey", "rk-2")
                        .with(user(admin())).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/attendance/admin/reports/4/payroll/11"))
                .andExpect(flash().attribute("pageError", containsString("vượt quá")));
    }

    @Test
    void managerCannotApproveOrPay() throws Exception {
        mockMvc.perform(post("/attendance/admin/reports/4/approve").with(user(manager())).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(multipart("/attendance/admin/reports/4/pay-all").param("method", "TIEN_MAT")
                        .with(user(manager())).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(multipart("/attendance/admin/reports/4/payroll/5/pay").param("amount", "1")
                        .with(user(manager())).with(csrf()))
                .andExpect(status().is3xxRedirection());
        verify(attendanceReportService, never()).approve(any(), anyInt());
        verify(payrollPaymentService, never()).payAll(any(), anyInt(), any());
        verify(payrollPaymentService, never()).payEmployee(any(), anyInt(), anyInt(), any());
    }

    @Test
    void managerReportPageShowsPayrollAndBlocksDuplicateSubmit() throws Exception {
        when(attendanceReportService.managerPage(any(), any(), any(), any(), any())).thenReturn(managerPage("WAITING"));
        when(payrollService.board(any(), eq(2026), eq(10), any(), any(), any(), any()))
                .thenReturn(board("DA_CHOT", true));
        when(payrollPaymentService.managerReport(any(), eq(2026), eq(10))).thenReturn(payroll(false));

        mockMvc.perform(get("/attendance/manager/reports").param("year", "2026").param("month", "10")
                        .with(user(manager())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h1>Báo cáo cửa hàng</h1>")))
                .andExpect(content().string(containsString("Bảng lương kỳ báo cáo")))
                .andExpect(content().string(containsString("1.450.000 đ")))
                .andExpect(content().string(containsString("Tình trạng thanh toán")))
                .andExpect(content().string(containsString("Đang chờ Admin duyệt")))
                .andExpect(content().string(not(containsString("action=\"/attendance/manager/reports/submit\""))))
                .andExpect(content().string(containsString("Báo cáo cửa hàng</span>")))
                .andExpect(content().string(not(containsString("Báo cáo Store"))));
    }

    @Test
    void managerMustCalculateBeforeSubmitting() throws Exception {
        when(attendanceReportService.managerPage(any(), any(), any(), any(), any())).thenReturn(managerPage("UNSENT"));
        when(payrollService.board(any(), eq(2026), eq(10), any(), any(), any(), any()))
                .thenReturn(board("CHUA_TINH", false));

        mockMvc.perform(get("/attendance/manager/reports").param("year", "2026").param("month", "10")
                        .with(user(manager())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("action=\"/attendance/manager/reports/submit\"")))
                .andExpect(content().string(containsString("disabled=\"disabled\"")))
                .andExpect(content().string(containsString("Hãy tính lương")));
    }

    @Test
    void managerSubmitReportsTheWaitingStatusOrTheReason() throws Exception {
        mockMvc.perform(post("/attendance/manager/reports/submit").param("year", "2026").param("month", "10")
                        .with(user(manager())).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("success", containsString("chờ Admin duyệt")));
        verify(attendanceReportService).submit(any(), eq(2026), eq(10));
    }

    @Test
    void oldAdminSalaryAddressOpensStoreReports() throws Exception {
        mockMvc.perform(get("/admin/salary").param("period", "2026-10").with(user(admin())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/attendance/admin/reports?year=2026&month=10"));
        mockMvc.perform(get("/admin/salary").with(user(admin())))
                .andExpect(redirectedUrl("/attendance/admin/reports"));
    }

    @Test
    void staffSalaryPageShowsApprovedPeriodsAndPaymentStatus() throws Exception {
        when(payrollService.board(any(), any(), any(), any(), any(), any(), any())).thenReturn(board("DA_CHOT", false));
        when(payrollPaymentService.myPeriods(any())).thenReturn(List.of(PayrollPaymentViews.StaffPeriod.builder()
                .year(2026).month(10).periodLabel("Tháng 10/2026").storeName("Cầu Giấy")
                .assignedShifts(2).workedShifts(2).baseLabel("250.000 đ").allowanceLabel("0 đ").deductionLabel("25.000 đ")
                .netLabel("225.000 đ").receivedLabel("100.000 đ").remainingLabel("125.000 đ")
                .paymentStatusKey("CHUA_THANH_TOAN").paymentStatusLabel("Chưa thanh toán")
                .lastPaidDate(LocalDate.of(2026, 11, 5))
                .payments(List.of(PayrollPaymentViews.PaymentRow.builder().paidDate(LocalDate.of(2026, 11, 5))
                        .amountLabel("100.000 đ").methodLabel("Tiền mặt").build()))
                .build()));

        mockMvc.perform(get("/staff/salary").with(user(staff())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Lương đã duyệt và thanh toán")))
                .andExpect(content().string(containsString("225.000 đ")))
                .andExpect(content().string(containsString("125.000 đ")))
                .andExpect(content().string(containsString("Chưa thanh toán")))
                .andExpect(content().string(containsString("05/11/2026 · 100.000 đ · Tiền mặt")))
                .andExpect(content().string(not(containsString("/pay"))));
    }

    private PayrollPaymentViews.ReportPayroll payroll(boolean approved) {
        return PayrollPaymentViews.ReportPayroll.builder()
                .reportId(4).storeId(1).storeName("Cầu Giấy").year(2026).month(10).periodLabel("Tháng 10/2026")
                .statusCode(approved ? "APPROVED" : "SUBMITTED").approved(approved).hasPayroll(true)
                .payrollStatusLabel("Đã chốt").employeeCount(2)
                .total(new BigDecimal("325000")).paid(BigDecimal.ZERO).remaining(new BigDecimal("325000"))
                .totalLabel("325.000 đ").paidLabel("0 đ").remainingLabel("325.000 đ")
                .paymentStatusKey("CHUA_THANH_TOAN").paymentStatusLabel("Chưa thanh toán")
                .unpaidCount(2).canPay(approved)
                .payBlockedReason(approved ? null : "Chỉ thanh toán được báo cáo đã được duyệt.")
                .requestKey("rk-1").today(LocalDate.of(2026, 11, 11))
                .employees(List.of(PayrollPaymentViews.EmployeeRow.builder()
                        .employeeId(11).employeeCode("NV011").employeeName("Nguyễn An").position("Nhân viên")
                        .assignedShifts(2).attendedShifts(2).validShifts(2)
                        .baseLabel("250.000 đ").allowanceLabel("0 đ").deductionLabel("25.000 đ").netLabel("225.000 đ")
                        .paidLabel("0 đ").remainingLabel("225.000 đ").remainingValue("225000").owed(true)
                        .paymentStatusKey("CHUA_THANH_TOAN").paymentStatusLabel("Chưa thanh toán").build()))
                .build();
    }

    private AdminAttendanceReportDetail detail(String status) {
        return AdminAttendanceReportDetail.builder()
                .reportId(4).storeId(1).storeName("Cầu Giấy").managerName("Quý").year(2026).month(10)
                .periodLabel("10/2026").submitted(true).statusCode(status)
                .statusLabel("APPROVED".equals(status) ? "✓ Đã duyệt" : "Chờ Admin duyệt")
                .statusCss("status-waiting").senderName("Quý").authorName("Quý")
                .submittedAt(LocalDateTime.of(2026, 11, 1, 9, 0)).versionNumber(1)
                .periodStart(LocalDate.of(2026, 10, 1)).periodEnd(LocalDate.of(2026, 10, 31))
                .live(ManagerAttendanceReportPage.AttendanceReportPreview.builder().build())
                .employees(List.of()).historyKind("all").days(List.of()).trainingDays(List.of())
                .versions(List.of()).versionPage(1).versionPageCount(0)
                .build();
    }

    private ManagerAttendanceReportPage managerPage(String status) {
        return ManagerAttendanceReportPage.builder()
                .storeId(1).storeName("Cầu Giấy").authorName("Quý").year(2026).month(10).periodLabel("10/2026")
                .statusCode(status).statusLabel("WAITING".equals(status) ? "Chờ Admin duyệt" : "Chưa gửi")
                .statusCss("status-waiting").submitted("WAITING".equals(status))
                .preview(ManagerAttendanceReportPage.AttendanceReportPreview.builder().build())
                .employees(List.of()).historyKind("all").days(List.of()).trainingDays(List.of())
                .build();
    }

    private PayrollBoardView board(String status, boolean reportLocked) {
        return PayrollBoardView.builder()
                .year(2026).month(10).monthValue("2026-10").periodLabel("Tháng 10/2026")
                .homePath("/staff/salary").navRole("staff")
                .statusKey(status).statusLabel("CHUA_TINH".equals(status) ? "Chưa tính" : "Đã chốt")
                .employeeCount(2).payableHoursLabel("58 giờ").payrollFundLabel("1.450.000 đ")
                .reportLocked(reportLocked).reportLockMessage(reportLocked ? "Báo cáo cửa hàng kỳ này đã gửi Admin." : null)
                .build();
    }

    private AuthenticatedUser admin() {
        return account(1, "admin", RoleName.ADMIN, null);
    }

    private AuthenticatedUser manager() {
        return account(20, "lan", RoleName.MANAGER, Employee.builder().id(5).fullName("Quản lý").build());
    }

    private AuthenticatedUser staff() {
        return account(30, "an", RoleName.STAFF, Employee.builder().id(11).fullName("Nguyễn An").build());
    }

    private AuthenticatedUser account(int id, String username, RoleName role, Employee employee) {
        return AuthenticatedUser.from(User.builder().id(id).username(username).passwordHash("hash")
                .role(Role.builder().id(id).roleName(role).build())
                .employee(employee).isActive(true).build());
    }
}

package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.response.ManagerMonthAttendanceView;
import com.example.coffee_hrm.dto.response.PayrollBoardView;
import com.example.coffee_hrm.dto.response.PayrollDetailView;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.EmploymentContract;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.EmploymentContractRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.security.DatabaseUserDetailsService;
import com.example.coffee_hrm.security.RoleBasedRedirector;
import com.example.coffee_hrm.security.SecurityConfig;
import com.example.coffee_hrm.service.AttendanceHistoryService;
import com.example.coffee_hrm.service.ManagerDayAttendanceService;
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
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
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

/** Manager xem chấm công, bảng lương và tính lương trên một màn hình "Danh sách chấm công và lương". */
@WebMvcTest({AttendanceHistoryController.class, SalaryPageController.class})
@Import({SecurityConfig.class, RoleBasedRedirector.class})
class ManagerAttendancePayrollPageTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AttendanceHistoryService attendanceHistoryService;
    @MockitoBean
    private ManagerDayAttendanceService managerDayAttendanceService;
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
    void monthPageShowsAttendanceAndPayrollTogether() throws Exception {
        when(managerDayAttendanceService.getMonth(any(), eq("2026-10"))).thenReturn(monthBoard());
        when(payrollService.board(any(), isNull(), isNull(), eq("2026-10"), isNull(), isNull(), isNull()))
                .thenReturn(payrollBoard(true, false));

        mockMvc.perform(get("/attendance/manager/month").param("month", "2026-10").with(user(manager())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h1>Danh sách chấm công và lương</h1>")))
                .andExpect(content().string(containsString("Chấm công và lương")))
                // Chấm công
                .andExpect(content().string(containsString("Ca thiếu chấm công")))
                .andExpect(content().string(containsString("Xem chấm công")))
                // Lương của từng nhân viên
                .andExpect(content().string(containsString("<th>Lương dự kiến</th>")))
                .andExpect(content().string(containsString("1.250.000 đ")))
                .andExpect(content().string(containsString("25.000 đ/giờ")))
                .andExpect(content().string(containsString("Tổng quỹ lương dự kiến")))
                .andExpect(content().string(containsString("href=\"/manager/salary/detail?year=2026&amp;month=10&amp;employeeId=11\"")))
                // Nhân viên đã nghỉ nhưng có ca trong tháng vẫn có dòng lương
                .andExpect(content().string(containsString("Trần Cũ")))
                .andExpect(content().string(containsString("employeeId=12")))
                // Tính lương ngay tại màn hình này; không còn xuất Excel
                .andExpect(content().string(containsString("action=\"/manager/salary/calculate\"")))
                .andExpect(content().string(containsString(">Tính lương</button>")))
                .andExpect(content().string(not(containsString("action=\"/manager/salary/lock\""))))
                .andExpect(content().string(not(containsString("/export"))))
                .andExpect(content().string(not(containsString("Xuất Excel"))))
                // Menu không còn mục Lương riêng
                .andExpect(content().string(not(containsString("href=\"/manager/salary\""))))
                .andExpect(content().string(not(containsString("Quản lý lương"))))
                .andExpect(content().string(not(containsString("Theo dõi chấm công"))));
    }

    @Test
    void lockButtonAppearsOnceThePayrollIsCalculated() throws Exception {
        when(managerDayAttendanceService.getMonth(any(), any())).thenReturn(monthBoard());
        when(payrollService.board(any(), any(), any(), any(), any(), any(), any())).thenReturn(payrollBoard(true, true));

        mockMvc.perform(get("/attendance/manager/month").param("month", "2026-10").with(user(manager())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("action=\"/manager/salary/lock\"")))
                .andExpect(content().string(containsString(">Chốt lương</button>")));
    }

    @Test
    void attendanceStillShowsWhenPayrollCannotBeLoaded() throws Exception {
        when(managerDayAttendanceService.getMonth(any(), any())).thenReturn(monthBoard());
        when(payrollService.board(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new BusinessException("Tháng không hợp lệ."));

        mockMvc.perform(get("/attendance/manager/month").param("month", "2026-10").with(user(manager())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nguyễn An")))
                .andExpect(content().string(containsString("Tháng không hợp lệ.")))
                .andExpect(content().string(not(containsString(">Tính lương</button>"))));
    }

    @Test
    void oldSalaryAddressOpensTheCombinedPage() throws Exception {
        mockMvc.perform(get("/manager/salary").param("year", "2026").param("month", "10").with(user(manager())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/attendance/manager/month?month=2026-10"));
        mockMvc.perform(get("/manager/salary").param("period", "2026-09").with(user(manager())))
                .andExpect(redirectedUrl("/attendance/manager/month?month=2026-09"));
        mockMvc.perform(get("/manager/salary").with(user(manager())))
                .andExpect(redirectedUrl("/attendance/manager/month"));
    }

    @Test
    void calculatingReturnsToTheCombinedPage() throws Exception {
        mockMvc.perform(post("/manager/salary/calculate").with(user(manager())).with(csrf())
                        .param("year", "2026").param("month", "10"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/attendance/manager/month?month=2026-10"))
                .andExpect(flash().attribute("successMessage", "Đã tính lương tháng 10/2026."));
        verify(payrollService).calculate(any(), eq(2026), eq(10));
    }

    @Test
    void lockErrorsAreShownOnTheCombinedPage() throws Exception {
        doThrow(new BusinessException("Còn 2 ca thiếu dữ liệu, chưa thể chốt lương."))
                .when(payrollService).lock(any(), eq(2026), eq(10));

        mockMvc.perform(post("/manager/salary/lock").with(user(manager())).with(csrf())
                        .param("year", "2026").param("month", "10"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/attendance/manager/month?month=2026-10"))
                .andExpect(flash().attribute("errorMessage", "Còn 2 ca thiếu dữ liệu, chưa thể chốt lương."));
    }

    @Test
    void salaryDetailLeadsBackToTheCombinedPage() throws Exception {
        when(payrollService.detail(any(), eq(2026), eq(10), eq(11))).thenReturn(PayrollDetailView.builder()
                .year(2026).month(10).periodLabel("Tháng 10/2026").homePath("/manager/salary").navRole("manager")
                .employeeCode("NV011").employeeName("Nguyễn An").storeName("Cầu Giấy")
                .statusKey("DA_TINH").statusLabel("Đã tính").hourlyRateLabel("25.000 đ/giờ")
                .expectedSalaryLabel("1.250.000 đ").build());

        mockMvc.perform(get("/manager/salary/detail").param("year", "2026").param("month", "10")
                        .param("employeeId", "11").with(user(manager())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/attendance/manager/month?month=2026-10\"")))
                .andExpect(content().string(containsString("Danh sách chấm công và lương")))
                .andExpect(content().string(not(containsString("Quản lý lương"))));
    }

    private ManagerMonthAttendanceView monthBoard() {
        return ManagerMonthAttendanceView.builder()
                .year(2026).month(10).monthValue("2026-10").monthLabel("Tháng 10/2026").storeName("Cầu Giấy")
                .employeeCount(1).scheduledSlots(10).completedSlots(9).missingSlots(1)
                .employees(List.of(ManagerMonthAttendanceView.EmployeeMonth.builder()
                        .employeeId(11).employeeCode("NV011").employeeName("Nguyễn An")
                        .scheduledSlots(10).completedSlots(9).missingSlots(1).paidHoursLabel("50 giờ").build()))
                .build();
    }

    private PayrollBoardView payrollBoard(boolean canCalculate, boolean canLock) {
        return PayrollBoardView.builder()
                .year(2026).month(10).monthValue("2026-10").periodLabel("Tháng 10/2026")
                .homePath("/manager/salary").navRole("manager")
                .canCalculate(canCalculate).canLock(canLock)
                .statusKey(canLock ? "DA_TINH" : "CHUA_TINH").statusLabel(canLock ? "Đã tính" : "Chưa tính")
                .employeeCount(2).payableHours(58).payableHoursLabel("58 giờ").payrollFundLabel("1.450.000 đ")
                .rows(List.of(
                        payRow(11, "Nguyễn An", 50, "1.250.000 đ"),
                        payRow(12, "Trần Cũ", 8, "200.000 đ")))
                .build();
    }

    private PayrollBoardView.Row payRow(int id, String name, int hours, String salary) {
        return PayrollBoardView.Row.builder()
                .employeeId(id).storeId(1).employeeCode(String.format("NV%03d", id)).employeeName(name)
                .storeName("Cầu Giấy").assignedShifts(hours / 5).payableHours(hours)
                .hourlyRateLabel("25.000 đ/giờ").expectedSalaryLabel(salary)
                .statusKey("CHUA_TINH").statusLabel("Chưa tính").build();
    }

    private AuthenticatedUser manager() {
        Employee employee = Employee.builder().id(5).fullName("Quản lý").build();
        User account = User.builder().id(20).username("lan").passwordHash("hash")
                .role(Role.builder().id(2).roleName(RoleName.MANAGER).build())
                .employee(employee).isActive(true).build();
        return AuthenticatedUser.from(account);
    }
}

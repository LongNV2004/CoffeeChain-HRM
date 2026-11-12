package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.response.ManagerDayAttendanceView;
import com.example.coffee_hrm.dto.response.ManagerMonthAttendanceView;
import com.example.coffee_hrm.dto.response.PayrollBoardView;
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
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** "Theo dõi chấm công" của nhân viên: cùng mẫu với Manager nhưng chỉ dữ liệu của chính mình. */
@WebMvcTest({AttendanceHistoryController.class, SalaryPageController.class})
@Import({SecurityConfig.class, RoleBasedRedirector.class})
class StaffAttendanceTrackPageTest {

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
    void dayPageUsesTheManagerLayoutWithOnlyMyRows() throws Exception {
        LocalDate day = LocalDate.of(2026, 10, 21);
        when(managerDayAttendanceService.getOwnDayBoard(any(), eq(day))).thenReturn(ManagerDayAttendanceView.builder()
                .workDate(day).dateLabel("Thứ Tư, 21/10/2026").storeName("Cầu Giấy")
                .assignedEmployees(1).checkedInCount(1).checkedOutCount(1).lateCount(1)
                .slots(List.of(ManagerDayAttendanceView.Slot.builder()
                        .employeeId(11).employeeCode("NV011").employeeName("Nguyễn An")
                        .shiftName("Ca sáng").startLabel("07:00").endLabel("12:00")
                        .checkInLabel("07:10").checkOutLabel("12:00").lateLabel("10 phút").earlyLabel("0 phút")
                        .late(true).statusKey("late").statusLabel("Đi muộn").paidHoursLabel("4 giờ 50 phút")
                        .note("Đi muộn 10 phút").build()))
                .build());

        mockMvc.perform(get("/attendance/staff/history").param("date", "2026-10-21").with(user(staff())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h1>Theo dõi chấm công</h1>")))
                .andExpect(content().string(containsString("Theo dõi theo ngày")))
                .andExpect(content().string(containsString("href=\"/attendance/staff/history/month\"")))
                .andExpect(content().string(containsString("<th>Thời gian có mặt</th>")))
                .andExpect(content().string(containsString("NV011 — Nguyễn An")))
                .andExpect(content().string(containsString("Đi muộn 10 phút")))
                // Không có bộ lọc chọn nhân viên khác, không gọi dữ liệu cửa hàng của Manager
                .andExpect(content().string(not(containsString("id=\"dayEmployee\""))))
                .andExpect(content().string(not(containsString("Tổng số nhân viên được phân ca"))))
                .andExpect(content().string(not(containsString("/attendance/manager/"))));
    }

    @Test
    void monthPageShowsOnlyMyAttendanceAndSalary() throws Exception {
        when(managerDayAttendanceService.getOwnMonth(any(), eq("2026-10"))).thenReturn(ManagerMonthAttendanceView.builder()
                .year(2026).month(10).monthValue("2026-10").monthLabel("Tháng 10/2026").storeName("Cầu Giấy")
                .employeeCount(1).scheduledSlots(10).completedSlots(9).missingSlots(1)
                .employees(List.of(ManagerMonthAttendanceView.EmployeeMonth.builder()
                        .employeeId(11).employeeCode("NV011").employeeName("Nguyễn An")
                        .scheduledSlots(10).completedSlots(9).missingSlots(1).paidHoursLabel("50 giờ").build()))
                .build());
        // Giả lập service trả nhầm cả dòng của người khác: màn hình vẫn không được hiển thị.
        when(payrollService.board(any(), any(), any(), eq("2026-10"), any(), any(), any())).thenReturn(PayrollBoardView.builder()
                .year(2026).month(10).monthValue("2026-10").periodLabel("Tháng 10/2026")
                .homePath("/staff/salary").navRole("staff")
                .statusKey("DA_TINH").statusLabel("Đã tính")
                .employeeCount(2).payableHours(58).payableHoursLabel("58 giờ").payrollFundLabel("9.999.000 đ")
                .rows(List.of(payRow(11, "Nguyễn An", "1.250.000 đ"), payRow(12, "Trần Khác", "8.749.000 đ")))
                .build());

        mockMvc.perform(get("/attendance/staff/history/month").param("month", "2026-10").with(user(staff())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h1>Theo dõi chấm công</h1>")))
                .andExpect(content().string(containsString("Chấm công và lương theo tháng")))
                .andExpect(content().string(containsString("<th>Lương dự kiến</th>")))
                .andExpect(content().string(containsString("1.250.000 đ")))
                .andExpect(content().string(containsString("href=\"/staff/salary/detail?year=2026&amp;month=10&amp;employeeId=11\"")))
                // Không lộ dữ liệu hoặc thao tác vượt quyền
                .andExpect(content().string(not(containsString("Trần Khác"))))
                .andExpect(content().string(not(containsString("8.749.000 đ"))))
                .andExpect(content().string(not(containsString("9.999.000 đ"))))
                .andExpect(content().string(not(containsString("Tổng quỹ lương"))))
                .andExpect(content().string(not(containsString("/manager/salary"))))
                .andExpect(content().string(not(containsString(">Tính lương</button>"))))
                .andExpect(content().string(not(containsString(">Chốt lương</button>"))));
    }

    @Test
    void staffCannotOpenTheManagerBoards() throws Exception {
        // Hệ thống chuyển hướng khi bị từ chối quyền; dữ liệu cửa hàng không bao giờ được nạp.
        mockMvc.perform(get("/attendance/manager/day").with(user(staff())))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/attendance/manager/month").with(user(staff())))
                .andExpect(status().is3xxRedirection());
        verify(managerDayAttendanceService, never()).getDay(any(), any());
        verify(managerDayAttendanceService, never()).getMonth(any(), any());
    }

    private PayrollBoardView.Row payRow(int id, String name, String salary) {
        return PayrollBoardView.Row.builder()
                .employeeId(id).storeId(1).employeeCode(String.format("NV%03d", id)).employeeName(name)
                .storeName("Cầu Giấy").assignedShifts(10).payableHours(50)
                .hourlyRateLabel("25.000 đ/giờ").expectedSalaryLabel(salary)
                .statusKey("DA_TINH").statusLabel("Đã tính").build();
    }

    private AuthenticatedUser staff() {
        Employee employee = Employee.builder().id(11).fullName("Nguyễn An").build();
        User account = User.builder().id(21).username("an").passwordHash("hash")
                .role(Role.builder().id(3).roleName(RoleName.STAFF).build())
                .employee(employee).isActive(true).build();
        return AuthenticatedUser.from(account);
    }
}

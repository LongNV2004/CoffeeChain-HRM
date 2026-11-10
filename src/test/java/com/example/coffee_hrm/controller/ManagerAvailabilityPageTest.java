package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.response.StaffAllocationRoster;
import com.example.coffee_hrm.dto.response.WeeklyAvailabilityView;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.EmploymentContract;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.EmploymentContractRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.security.DatabaseUserDetailsService;
import com.example.coffee_hrm.security.RoleBasedRedirector;
import com.example.coffee_hrm.security.SecurityConfig;
import com.example.coffee_hrm.service.NotificationService;
import com.example.coffee_hrm.service.RecruitmentRequestService;
import com.example.coffee_hrm.service.ResignationProposalService;
import com.example.coffee_hrm.service.StaffAllocationService;
import com.example.coffee_hrm.service.StaffAvailabilityService;
import com.example.coffee_hrm.service.TrainingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
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

/** Trang Duyệt đăng ký lịch làm việc hiển thị dạng bảng tuần (ca × ngày) giống trang Lịch làm việc. */
@WebMvcTest(ManagerAvailabilityController.class)
@Import({SecurityConfig.class, RoleBasedRedirector.class})
class ManagerAvailabilityPageTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StaffAvailabilityService staffAvailabilityService;
    @MockitoBean
    private StaffAllocationService staffAllocationService;
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

    private final LocalDate monday = VietnamTime.today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

    @BeforeEach
    void setUp() {
        // Manager có hợp đồng đang hiệu lực để qua bộ lọc truy cập chức năng quản lý.
        when(employmentContractRepository.findByEmployeeId(anyInt())).thenReturn(List.of(EmploymentContract.builder()
                .id(1).contractCode("HD-1").startDate(VietnamTime.today().minusMonths(1))
                .endDate(VietnamTime.today().plusMonths(6)).salary(new BigDecimal("30000")).build()));
        when(staffAvailabilityService.getStoreWeekStaffingGrid(any(), any())).thenReturn(grid());
    }

    @Test
    void rendersShiftByDayGridWithEmployeesInEachCell() throws Exception {
        MvcResult result = mockMvc.perform(get("/availability/manager").param("week", monday.toString())
                        .with(user(manager())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Lịch đăng ký theo tuần")))
                .andExpect(content().string(containsString("class=\"rv-week-grid\"")))
                // Thứ 2 Ca sáng: 1/3 đã duyệt, 1 chờ duyệt.
                .andExpect(content().string(containsString("data-slot=\";1|Ca sáng;\"")))
                .andExpect(content().string(containsString("1 chờ duyệt")))
                .andExpect(content().string(containsString("Thiếu 3")))
                .andExpect(content().string(containsString("Đủ người")))
                .andExpect(content().string(containsString("Chưa cấu hình")))
                .andExpect(content().string(containsString("rv-person is-approved")))
                .andExpect(content().string(containsString("rv-person is-pending")))
                .andExpect(content().string(containsString("rv-person is-rejected")))
                .andExpect(content().string(containsString("data-reg-id=\"k:REG-AN\"")))
                .andExpect(content().string(containsString("is-today")))
                .andExpect(content().string(not(containsString("rv-slot-row"))))
                // Nút Đề xuất phân bổ nhân sự nằm ngay trên màn hình duyệt, mở modal tại chỗ.
                .andExpect(content().string(containsString("Phân bổ nhân sự")))
                .andExpect(content().string(not(containsString("Đề xuất phân bổ"))))
                .andExpect(content().string(containsString("id=\"saModal\"")))
                .andExpect(content().string(containsString("data-url=\"/availability/manager/allocation?week=" + monday + "\"")))
                .andReturn();
        String html = result.getResponse().getContentAsString();
        // Mỗi ca có đúng 7 ô ngày.
        assertEquals(3 * 7, html.split("class=\"rv-slot ", -1).length - 1);
        // Chip của An (đăng ký Thứ 2 + Thứ 3 ca sáng) xuất hiện ở đúng hai ô.
        assertEquals(2, html.split("data-reg-id=\"k:REG-AN\"", -1).length - 2);
        String dump = System.getProperty("availability.dump");
        if (dump != null) {
            java.nio.file.Files.writeString(java.nio.file.Path.of(dump), html);
        }
    }

    @Test
    void allocationPanelShowsOnlyEligibleStaffWithCodeAndName() throws Exception {
        LocalDate next = VietnamTime.today().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        when(staffAllocationService.roster(any(), eq(next))).thenReturn(StaffAllocationRoster.builder()
                .weekStart(next).weekEnd(next.plusDays(6))
                .rows(List.of(
                        row(11, "Nguyễn Văn An", true, "none", "Chưa đăng ký (0/2 ca)", "DANG_HIEU_LUC", "Đang hiệu lực", "ok", null, null),
                        row(14, "Phạm Dũng", false, "na", "Không áp dụng", "NONE", "Chưa có hợp đồng", "warn",
                                "Chưa có hợp đồng lao động.", "warn")))
                .build());

        MvcResult result = mockMvc.perform(get("/availability/manager/allocation").param("week", next.toString())
                        .with(user(manager())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"sa-panel\"")))
                .andExpect(content().string(containsString("<th scope=\"col\">Mã nhân viên</th>")))
                .andExpect(content().string(containsString("<th scope=\"col\">Tên nhân viên</th>")))
                .andExpect(content().string(containsString("NV011")))
                .andExpect(content().string(containsString("Nguyễn Văn An")))
                .andExpect(content().string(containsString("name=\"employeeIds\"")))
                .andExpect(content().string(containsString(">Phân bổ</button>")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                // Không hiển thị nhân viên không thể phân bổ và các cột khác.
                .andExpect(content().string(not(containsString("Phạm Dũng"))))
                .andExpect(content().string(not(containsString("Chưa có hợp đồng lao động."))))
                .andExpect(content().string(not(containsString("Điều kiện phân bổ"))))
                .andExpect(content().string(not(containsString("Trạng thái hợp đồng"))))
                .andExpect(content().string(not(containsString("Đăng ký ca"))))
                .andExpect(content().string(not(containsString("Ca sáng"))))
                .andExpect(content().string(not(containsString("<html"))))
                .andReturn();
        String dump = System.getProperty("allocation.dump");
        if (dump != null) {
            java.nio.file.Files.writeString(java.nio.file.Path.of(dump), result.getResponse().getContentAsString());
        }
    }

    @Test
    void allocationPanelSaysSoWhenNobodyIsEligible() throws Exception {
        LocalDate next = VietnamTime.today().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        when(staffAllocationService.roster(any(), eq(next))).thenReturn(StaffAllocationRoster.builder()
                .weekStart(next).weekEnd(next.plusDays(6))
                .rows(List.of(row(14, "Phạm Dũng", false, "na", "Không áp dụng", "NONE", "Chưa có hợp đồng", "warn",
                        "Chưa có hợp đồng lao động.", "warn")))
                .build());

        mockMvc.perform(get("/availability/manager/allocation").param("week", next.toString()).with(user(manager())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Hiện không có nhân viên nào đủ điều kiện phân bổ")))
                .andExpect(content().string(not(containsString("Phạm Dũng"))))
                .andExpect(content().string(not(containsString(">Phân bổ</button>"))));
    }

    @Test
    void allocatingSelectedStaffSchedulesThemAndOpensTheAllocatedWeek() throws Exception {
        LocalDate next = VietnamTime.today().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        when(staffAllocationService.allocate(any(), eq(next), anyList()))
                .thenReturn(new StaffAllocationService.Outcome(2, 5, next));

        mockMvc.perform(post("/availability/manager/allocation").with(user(manager())).with(csrf())
                        .param("week", next.toString())
                        .param("employeeIds", "11", "12"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/availability/manager?week=" + next))
                .andExpect(flash().attribute("successMessage", containsString("Đã phân bổ 5 ca cho 2 nhân viên")))
                .andExpect(flash().attribute("successMessage", containsString("xếp vào lịch làm việc")));

        verify(staffAllocationService).allocate(any(), eq(next), eq(List.of(11, 12)));
    }

    @Test
    void reviewAllOnlyReviewsTheWeekOnScreenAndReportsSkippedRegistrations() throws Exception {
        LocalDate next = VietnamTime.today().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        when(staffAvailabilityService.reviewAllPendingRegistrations(eq(true), any(), eq(next)))
                .thenReturn(new StaffAvailabilityService.ReviewAllResult(2,
                        List.of("Vân Anh Công Chúa: Bạn chưa có hợp đồng hiệu lực cho tuần đăng ký.")));

        mockMvc.perform(post("/availability/manager/review-all").with(user(manager())).with(csrf())
                        .param("approved", "true").param("week", next.toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/availability/manager?week=" + next))
                .andExpect(flash().attribute("warningMessage", containsString("Đã duyệt 2 đơn")))
                .andExpect(flash().attribute("warningMessage", containsString("1 đơn chưa duyệt được: Vân Anh Công Chúa")));
    }

    @Test
    void submissionErrorsAreShownOnTheApprovalPage() throws Exception {
        LocalDate next = VietnamTime.today().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        when(staffAllocationService.allocate(any(), any(), anyList()))
                .thenThrow(new BusinessException("Chưa phân bổ ai vì có nhân viên không còn đủ điều kiện: An."));

        mockMvc.perform(post("/availability/manager/allocation").with(user(manager())).with(csrf())
                        .param("week", next.toString()).param("employeeIds", "11"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage", containsString("không còn đủ điều kiện")));
    }

    @Test
    void staffCannotOpenOrSubmitAllocation() throws Exception {
        Employee employee = Employee.builder().id(11).fullName("Nhân viên").build();
        User account = User.builder().id(30).username("an").passwordHash("hash")
                .role(Role.builder().id(3).roleName(RoleName.STAFF).build())
                .employee(employee).isActive(true).build();
        AuthenticatedUser staff = AuthenticatedUser.from(account);

        mockMvc.perform(get("/availability/manager/allocation").with(user(staff)))
                .andExpect(redirectedUrl("/dashboard/staff"));
        mockMvc.perform(post("/availability/manager/allocation").with(user(staff)).with(csrf())
                        .param("employeeIds", "11"))
                .andExpect(redirectedUrl("/dashboard/staff"));
        verify(staffAllocationService, never()).roster(any(), any());
        verify(staffAllocationService, never()).allocate(any(), any(), any());
    }

    private static StaffAllocationRoster.Row row(int id, String name, boolean eligible, String regKey, String regLabel,
                                                 String contractKey, String contractLabel, String contractTone,
                                                 String reason, String reasonTone) {
        return StaffAllocationRoster.Row.builder().employeeId(id).employeeCode(String.format("NV%03d", id))
                .fullName(name).initials("NV").position("Pha chế")
                .contractStatusKey(contractKey).contractStatusLabel(contractLabel).contractTone(contractTone)
                .registrationKey(regKey).registrationLabel(regLabel)
                .eligible(eligible).reason(reason).reasonTone(reasonTone).build();
    }

    private WeeklyAvailabilityView grid() {
        String[] names = {"Ca sáng", "Ca chiều", "Ca tối"};
        LocalTime[] starts = {LocalTime.of(7, 0), LocalTime.of(12, 0), LocalTime.of(17, 0)};
        String[] days = {"Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7", "Chủ nhật"};
        List<WeeklyAvailabilityView.DayHeader> headers = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            headers.add(WeeklyAvailabilityView.DayHeader.builder().date(monday.plusDays(i)).dayOfWeekName(days[i])
                    .formattedDate(monday.plusDays(i).toString()).build());
        }
        List<WeeklyAvailabilityView.ShiftRow> rows = new ArrayList<>();
        for (int s = 0; s < 3; s++) {
            List<WeeklyAvailabilityView.DayCell> cells = new ArrayList<>();
            for (int d = 1; d <= 7; d++) {
                int approved = (s == 0 && d <= 2) ? 1 : (s == 2 && d == 1 ? 3 : 0);
                int pending = (s == 0 && d == 1) ? 1 : 0;
                boolean configured = !(s == 1 && d == 7);
                int demand = configured ? 3 : 0;
                String key = !configured ? "unconfigured" : approved >= demand ? "enough" : "short";
                cells.add(WeeklyAvailabilityView.DayCell.builder()
                        .date(monday.plusDays(d - 1)).shiftId(s + 1).dayOfWeek(d).dayOfWeekName(days[d - 1])
                        .configured(configured).maxEmployees(configured ? demand : null)
                        .approvedCount(approved).pendingCount(pending).registeredCount(approved + pending)
                        .shortfall(configured ? Math.max(0, demand - approved) : 0)
                        .staffingStatusKey(key).build());
            }
            rows.add(WeeklyAvailabilityView.ShiftRow.builder().shiftId(s + 1).shiftName(names[s])
                    .startTime(starts[s]).endTime(starts[s].plusHours(5))
                    .formattedTime(starts[s] + " – " + starts[s].plusHours(5)).dayCells(cells).build());
        }
        List<WeeklyAvailabilityView.RegistrationGroup> registrations = List.of(
                registration("REG-AN", 11, "Nguyễn Văn An", "approved", "Đã duyệt", ";1|Ca sáng;2|Ca sáng;",
                        "Thứ 2: Ca sáng; Thứ 3: Ca sáng"),
                registration("REG-BINH", 12, "Trần Thị Bình", "pending", "Chờ duyệt", ";1|Ca sáng;",
                        "Thứ 2: Ca sáng"),
                registration("REG-CUONG", 13, "Lê Văn Cường", "rejected", "Từ chối", ";3|Ca chiều;",
                        "Thứ 4: Ca chiều"));
        return WeeklyAvailabilityView.builder().storeId(1).storeName("Coffee Cầu Giấy")
                .weekStartDate(monday).weekEndDate(monday.plusDays(6))
                .days(headers).shiftRows(rows).registrations(registrations).build();
    }

    private WeeklyAvailabilityView.RegistrationGroup registration(String key, int employeeId, String name, String status,
                                                                  String label, String slotKeys, String slotsByDay) {
        return WeeklyAvailabilityView.RegistrationGroup.builder()
                .registrationKey(key).availabilityId(employeeId * 10).employeeId(employeeId).employeeName(name)
                .statusKey(status).statusLabel(label).pending("pending".equals(status))
                .slotKeys(slotKeys).slotsByDay(slotsByDay).durationLabel("1 tháng")
                .formattedPeriod("01/11/2026 – 30/11/2026").activeInWeek(true)
                .submittedAt(LocalDateTime.of(2026, 10, 28, 9, 30)).build();
    }

    private AuthenticatedUser manager() {
        Employee employee = Employee.builder().id(5).fullName("Quản lý").build();
        User account = User.builder().id(20).username("lan").passwordHash("hash")
                .role(Role.builder().id(2).roleName(RoleName.MANAGER).build())
                .employee(employee).isActive(true).build();
        return AuthenticatedUser.from(account);
    }
}

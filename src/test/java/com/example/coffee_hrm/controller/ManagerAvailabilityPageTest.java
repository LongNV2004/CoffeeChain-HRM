package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.common.enums.RoleName;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

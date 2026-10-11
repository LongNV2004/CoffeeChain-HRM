package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.response.AdminAttendanceReportPage;
import com.example.coffee_hrm.dto.response.ManagerAttendanceReportPage;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.AttendanceReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/attendance")
public class AttendanceReportController {

    private final AttendanceReportService attendanceReportService;

    @GetMapping("/manager/reports")
    @PreAuthorize("hasRole('MANAGER')")
    public String managerPage(@RequestParam(value = "year", required = false) Integer year,
                              @RequestParam(value = "month", required = false) Integer month,
                              @AuthenticationPrincipal AuthenticatedUser user,
                              Model model) {
        return managerView(year, month, null, user, model, "manager/ManagerAttendanceReport");
    }

    @GetMapping("/manager/reports/detail")
    @PreAuthorize("hasRole('MANAGER')")
    public String managerDetail(@RequestParam(value = "year", required = false) Integer year,
                                @RequestParam(value = "month", required = false) Integer month,
                                @RequestParam(value = "employeeId", required = false) Integer employeeId,
                                @AuthenticationPrincipal AuthenticatedUser user,
                                Model model) {
        return managerView(year, month, employeeId, user, model, "manager/ManagerAttendanceReportDetail");
    }

    @PostMapping("/manager/reports/submit")
    @PreAuthorize("hasRole('MANAGER')")
    public String submit(@RequestParam("year") int year,
                         @RequestParam("month") int month,
                         @AuthenticationPrincipal AuthenticatedUser user,
                         RedirectAttributes redirect) {
        try {
            attendanceReportService.submit(user, year, month);
            redirect.addFlashAttribute("success", "Đã gửi báo cáo cho Admin.");
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("pageError", ex.getMessage());
        } catch (DataAccessException ex) {
            redirect.addFlashAttribute("pageError", SCHEMA_MESSAGE);
        }
        return redirectManager(year, month);
    }

    @GetMapping("/admin/reports")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminPage(@RequestParam(value = "year", required = false) Integer year,
                            @RequestParam(value = "month", required = false) Integer month,
                            @RequestParam(value = "storeId", required = false) Integer storeId,
                            @RequestParam(value = "status", required = false) String status,
                            @AuthenticationPrincipal AuthenticatedUser user,
                            Model model) {
        try {
            AdminAttendanceReportPage page = attendanceReportService.adminPage(user, year, month, storeId, status);
            model.addAttribute("report", page);
            model.addAttribute("years", years(page.getYear()));
            model.addAttribute("pageError", null);
        } catch (BusinessException ex) {
            int fallbackYear = year == null ? LocalDate.now(VietnamTime.ZONE).getYear() : year;
            int fallbackMonth = month == null ? LocalDate.now(VietnamTime.ZONE).getMonthValue() : month;
            model.addAttribute("report", emptyAdmin(fallbackYear, fallbackMonth));
            model.addAttribute("years", years(fallbackYear));
            model.addAttribute("pageError", ex.getMessage());
        } catch (DataAccessException ex) {
            int fallbackYear = year == null ? LocalDate.now(VietnamTime.ZONE).getYear() : year;
            int fallbackMonth = month == null ? LocalDate.now(VietnamTime.ZONE).getMonthValue() : month;
            model.addAttribute("report", emptyAdmin(fallbackYear, fallbackMonth));
            model.addAttribute("years", years(fallbackYear));
            model.addAttribute("pageError", SCHEMA_MESSAGE);
        }
        return "admin/AdminAttendanceReport";
    }

    @GetMapping("/admin/reports/{reportId}")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminDetail(@PathVariable("reportId") int reportId,
                              @RequestParam(value = "employeeId", required = false) Integer employeeId,
                              @AuthenticationPrincipal AuthenticatedUser user,
                              Model model) {
        try {
            model.addAttribute("detail", attendanceReportService.adminDetail(user, reportId, employeeId));
            model.addAttribute("pageError", null);
        } catch (BusinessException ex) {
            model.addAttribute("detail", null);
            model.addAttribute("pageError", ex.getMessage());
        } catch (DataAccessException ex) {
            model.addAttribute("detail", null);
            model.addAttribute("pageError", SCHEMA_MESSAGE);
        }
        return "admin/AdminAttendanceReportDetail";
    }

    private static final String SCHEMA_MESSAGE =
            "Chưa đọc được dữ liệu báo cáo. Hãy chạy script src/main/resources/db/monthly-attendance-report.sql. "
                    + "Nếu chưa có bảng chấm công đào tạo, hãy chạy thêm training-attendance.sql.";

    private String managerView(Integer year,
                               Integer month,
                               Integer employeeId,
                               AuthenticatedUser user,
                               Model model,
                               String view) {
        try {
            ManagerAttendanceReportPage page = attendanceReportService.managerPage(user, year, month, employeeId);
            model.addAttribute("report", page);
            model.addAttribute("years", years(page.getYear()));
            model.addAttribute("pageError", null);
        } catch (BusinessException ex) {
            int fallbackYear = year == null ? LocalDate.now(VietnamTime.ZONE).getYear() : year;
            int fallbackMonth = month == null ? LocalDate.now(VietnamTime.ZONE).getMonthValue() : month;
            model.addAttribute("report", emptyManager(user, fallbackYear, fallbackMonth));
            model.addAttribute("years", years(fallbackYear));
            model.addAttribute("pageError", ex.getMessage());
        } catch (DataAccessException ex) {
            int fallbackYear = year == null ? LocalDate.now(VietnamTime.ZONE).getYear() : year;
            int fallbackMonth = month == null ? LocalDate.now(VietnamTime.ZONE).getMonthValue() : month;
            model.addAttribute("report", emptyManager(user, fallbackYear, fallbackMonth));
            model.addAttribute("years", years(fallbackYear));
            model.addAttribute("pageError", SCHEMA_MESSAGE);
        }
        return view;
    }

    private String redirectManager(int year, int month) {
        return "redirect:/attendance/manager/reports?year=" + year + "&month=" + month;
    }

    private List<Integer> years(int selected) {
        int current = LocalDate.now(VietnamTime.ZONE).getYear();
        int start = Math.min(selected, current - 3);
        int end = Math.max(selected, current);
        if (start < 2000) {
            start = 2000;
        }
        List<Integer> values = new ArrayList<>();
        for (int year = end; year >= start; year--) {
            values.add(year);
        }
        return values;
    }

    private ManagerAttendanceReportPage emptyManager(AuthenticatedUser user, int year, int month) {
        return ManagerAttendanceReportPage.builder()
                .storeId(user.getStoreId() == null ? 0 : user.getStoreId())
                .storeName(user.getStoreName())
                .authorName(user.getDisplayName())
                .year(year)
                .month(month)
                .periodLabel(String.format("%02d/%04d", month, year))
                .statusCode("NOT_CREATED")
                .statusLabel("Chưa gửi")
                .submitted(false)
                .preview(ManagerAttendanceReportPage.AttendanceReportPreview.builder().build())
                .employees(List.of())
                .days(List.of())
                .build();
    }

    private AdminAttendanceReportPage emptyAdmin(int year, int month) {
        return AdminAttendanceReportPage.builder()
                .year(year)
                .month(month)
                .periodLabel(String.format("%02d/%04d", month, year))
                .filterStatus("all")
                .stores(List.of())
                .rows(List.of())
                .build();
    }
}

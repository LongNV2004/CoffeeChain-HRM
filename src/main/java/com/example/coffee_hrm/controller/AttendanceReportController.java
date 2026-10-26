package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.response.AdminAttendanceReportPage;
import com.example.coffee_hrm.dto.response.ManagerAttendanceReportPage;
import com.example.coffee_hrm.report.ReportExcelFile;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.AttendanceReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
        return managerView(year, month, null, null, user, model, "manager/ManagerAttendanceReport");
    }

    @GetMapping("/manager/reports/detail")
    @PreAuthorize("hasRole('MANAGER')")
    public String managerDetail(@RequestParam(value = "year", required = false) Integer year,
                                @RequestParam(value = "month", required = false) Integer month,
                                @RequestParam(value = "employeeId", required = false) Integer employeeId,
                                @RequestParam(value = "kind", required = false) String kind,
                                @AuthenticationPrincipal AuthenticatedUser user,
                                Model model) {
        return managerView(year, month, employeeId, kind, user, model, "manager/ManagerAttendanceReportDetail");
    }

    @PostMapping("/manager/reports/submit")
    @PreAuthorize("hasRole('MANAGER')")
    public String submit(@RequestParam("year") int year,
                         @RequestParam("month") int month,
                         @AuthenticationPrincipal AuthenticatedUser user,
                         RedirectAttributes redirect) {
        try {
            attendanceReportService.submit(user, year, month);
            redirect.addFlashAttribute("success", "Đã gửi báo cáo và file Excel cho Admin.");
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("pageError", ex.getMessage());
        } catch (DataAccessException ex) {
            redirect.addFlashAttribute("pageError", SCHEMA_MESSAGE);
        }
        return redirectManager(year, month);
    }

    @GetMapping("/manager/reports/excel")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<byte[]> managerExcel(@RequestParam("year") int year,
                                               @RequestParam("month") int month,
                                               @AuthenticationPrincipal AuthenticatedUser user) {
        return excelResponse(attendanceReportService.managerReportExcel(user, year, month));
    }

    @GetMapping("/admin/reports/{reportId}/excel")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> adminExcel(@PathVariable("reportId") int reportId,
                                             @AuthenticationPrincipal AuthenticatedUser user) {
        return excelResponse(attendanceReportService.adminReportExcel(user, reportId));
    }

    @GetMapping("/admin/reports/{reportId}/versions/{version}/excel")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> adminVersionExcel(@PathVariable("reportId") int reportId,
                                                    @PathVariable("version") int version,
                                                    @AuthenticationPrincipal AuthenticatedUser user) {
        return excelResponse(attendanceReportService.adminVersionExcel(user, reportId, version));
    }

    private ResponseEntity<byte[]> excelResponse(ReportExcelFile file) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.fileName())
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType("application/vnd.ms-excel"))
                .body(file.body());
    }

    @PostMapping("/admin/reports/{reportId}/revision")
    @PreAuthorize("hasRole('ADMIN')")
    public String requestRevision(@PathVariable("reportId") int reportId,
                                  @RequestParam("year") int year,
                                  @RequestParam("month") int month,
                                  @RequestParam(value = "note", required = false) String note,
                                  @RequestParam(value = "back", required = false) String back,
                                  @AuthenticationPrincipal AuthenticatedUser user,
                                  RedirectAttributes redirect) {
        try {
            attendanceReportService.requestRevision(user, reportId, note);
            redirect.addFlashAttribute("success", "Đã yêu cầu gửi lại báo cáo.");
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("pageError", ex.getMessage());
        } catch (DataAccessException ex) {
            redirect.addFlashAttribute("pageError", SCHEMA_MESSAGE);
        }
        return redirectAdmin(reportId, year, month, back);
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
                              @RequestParam(value = "kind", required = false) String kind,
                              @RequestParam(value = "versionPage", required = false) Integer versionPage,
                              @AuthenticationPrincipal AuthenticatedUser user,
                              Model model) {
        try {
            model.addAttribute("detail", attendanceReportService.adminDetail(user, reportId, employeeId, kind, versionPage));
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

    @PostMapping("/admin/reports/{reportId}/versions/{version}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String deleteVersion(@PathVariable("reportId") int reportId,
                                @PathVariable("version") int version,
                                @RequestParam(value = "versionPage", required = false) Integer versionPage,
                                @AuthenticationPrincipal AuthenticatedUser user,
                                RedirectAttributes redirect) {
        try {
            attendanceReportService.deleteVersion(user, reportId, version);
            redirect.addFlashAttribute("success", "Đã xoá lần gửi v" + version + ".");
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("pageError", ex.getMessage());
        } catch (DataAccessException ex) {
            redirect.addFlashAttribute("pageError", SCHEMA_MESSAGE);
        }
        String page = versionPage != null && versionPage > 1 ? "?versionPage=" + versionPage : "";
        return "redirect:/attendance/admin/reports/" + reportId + page + "#cac-lan-gui";
    }

    @GetMapping("/admin/reports/{reportId}/versions/{version}")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminVersion(@PathVariable("reportId") int reportId,
                               @PathVariable("version") int version,
                               @AuthenticationPrincipal AuthenticatedUser user,
                               Model model) {
        try {
            model.addAttribute("version", attendanceReportService.adminVersion(user, reportId, version));
            model.addAttribute("pageError", null);
        } catch (BusinessException ex) {
            model.addAttribute("version", null);
            model.addAttribute("pageError", ex.getMessage());
        } catch (DataAccessException ex) {
            model.addAttribute("version", null);
            model.addAttribute("pageError", SCHEMA_MESSAGE);
        }
        return "admin/AdminAttendanceReportVersion";
    }

    private static final String SCHEMA_MESSAGE =
            "Chưa đọc được dữ liệu báo cáo. Hãy chạy script src/main/resources/db/monthly-attendance-report.sql. "
                    + "Nếu chưa có bảng chấm công đào tạo, hãy chạy thêm training-attendance.sql.";

    private String managerView(Integer year,
                               Integer month,
                               Integer employeeId,
                               String historyKind,
                               AuthenticatedUser user,
                               Model model,
                               String view) {
        try {
            ManagerAttendanceReportPage page = attendanceReportService.managerPage(
                    user, year, month, employeeId, historyKind);
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

    private String redirectAdmin(int reportId, int year, int month, String back) {
        if ("detail".equals(back)) {
            return "redirect:/attendance/admin/reports/" + reportId;
        }
        return "redirect:/attendance/admin/reports?year=" + year + "&month=" + month;
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
                .statusCode("UNSENT")
                .statusLabel("Chưa gửi")
                .statusCss("status-unknown")
                .submitted(false)
                .preview(ManagerAttendanceReportPage.AttendanceReportPreview.builder().build())
                .employees(List.of())
                .historyKind("all")
                .days(List.of())
                .trainingDays(List.of())
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

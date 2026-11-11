package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.request.PayrollPaymentForm;
import com.example.coffee_hrm.dto.response.AdminAttendanceReportPage;
import com.example.coffee_hrm.dto.response.ManagerAttendanceReportPage;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.AttendanceReportService;
import com.example.coffee_hrm.service.PayrollPaymentService;
import com.example.coffee_hrm.service.PayrollService;
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
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/attendance")
public class AttendanceReportController {


    private final AttendanceReportService attendanceReportService;
    private final PayrollService payrollService;
    private final PayrollPaymentService payrollPaymentService;

    @GetMapping("/manager/reports")
    @PreAuthorize("hasRole('MANAGER')")
    public String managerPage(@RequestParam(value = "year", required = false) Integer year,
                              @RequestParam(value = "month", required = false) Integer month,
                              @AuthenticationPrincipal AuthenticatedUser user,
                              Model model) {
        String view = managerView(year, month, null, null, user, model, "manager/ManagerAttendanceReport");
        addManagerPayroll(user, (ManagerAttendanceReportPage) model.getAttribute("report"), model);
        return view;
    }

    /**
     * Báo cáo cửa hàng gồm chấm công và bảng lương. Manager xem bảng lương của kỳ, tình trạng duyệt và thanh toán;
     * không duyệt hay thanh toán được.
     */
    private void addManagerPayroll(AuthenticatedUser user, ManagerAttendanceReportPage report, Model model) {
        if (report == null) {
            return;
        }
        try {
            model.addAttribute("payroll", payrollService.board(user, report.getYear(), report.getMonth(),
                    null, null, null, null));
            model.addAttribute("payment", payrollPaymentService.managerReport(user, report.getYear(), report.getMonth()));
        } catch (BusinessException ex) {
            model.addAttribute("payroll", null);
            model.addAttribute("payment", null);
            model.addAttribute("payrollError", ex.getMessage());
        } catch (DataAccessException ex) {
            model.addAttribute("payroll", null);
            model.addAttribute("payment", null);
            model.addAttribute("payrollError", PAYMENT_SCHEMA_MESSAGE);
        }
    }

    private static final String PAYMENT_SCHEMA_MESSAGE =
            "Chưa đọc được dữ liệu thanh toán lương. Hãy chạy script src/main/resources/db/payroll-payment.sql.";

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
            redirect.addFlashAttribute("success",
                    "Đã gửi báo cáo chấm công và lương cho Admin. Báo cáo đang chờ Admin duyệt.");
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("pageError", ex.getMessage());
        } catch (DataAccessException ex) {
            redirect.addFlashAttribute("pageError", SCHEMA_MESSAGE);
        }
        return redirectManager(year, month);
    }

    @PostMapping("/admin/reports/{reportId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public String approve(@PathVariable("reportId") int reportId,
                          @AuthenticationPrincipal AuthenticatedUser user,
                          RedirectAttributes redirect) {
        try {
            attendanceReportService.approve(user, reportId);
            redirect.addFlashAttribute("success", "Đã duyệt báo cáo. Bây giờ có thể thanh toán lương cho nhân viên.");
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("pageError", ex.getMessage());
        } catch (DataAccessException ex) {
            redirect.addFlashAttribute("pageError", SCHEMA_MESSAGE);
        }
        return "redirect:/attendance/admin/reports/" + reportId;
    }

    /** Thanh toán toàn bộ nhân viên còn nợ trong báo cáo. Tiền đã được trả ngoài hệ thống; ở đây chỉ ghi nhận. */
    @PostMapping("/admin/reports/{reportId}/pay-all")
    @PreAuthorize("hasRole('ADMIN')")
    public String payAll(@PathVariable("reportId") int reportId,
                         @ModelAttribute PayrollPaymentForm form,
                         @RequestParam(value = "proof", required = false) MultipartFile proof,
                         @AuthenticationPrincipal AuthenticatedUser user,
                         RedirectAttributes redirect) {
        try {
            PayrollPaymentService.PayAllResult result = payrollPaymentService.payAll(user, reportId, form, proof);
            redirect.addFlashAttribute("success", "Đã ghi nhận thanh toán " + result.totalLabel() + " cho "
                    + result.employees() + " nhân viên. Nhân viên đã nhận được thông báo.");
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("pageError", ex.getMessage());
        } catch (DataAccessException ex) {
            redirect.addFlashAttribute("pageError", PAYMENT_SCHEMA_MESSAGE);
        }
        return "redirect:/attendance/admin/reports/" + reportId + "#bang-luong";
    }

    @GetMapping("/admin/reports/{reportId}/payroll/{employeeId}")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminPayrollEmployee(@PathVariable("reportId") int reportId,
                                       @PathVariable("employeeId") int employeeId,
                                       @AuthenticationPrincipal AuthenticatedUser user,
                                       Model model) {
        try {
            model.addAttribute("detail", payrollPaymentService.employeePayroll(user, reportId, employeeId));
        } catch (BusinessException ex) {
            model.addAttribute("detail", null);
            model.addAttribute("pageError", ex.getMessage());
        } catch (DataAccessException ex) {
            model.addAttribute("detail", null);
            model.addAttribute("pageError", PAYMENT_SCHEMA_MESSAGE);
        }
        model.addAttribute("reportId", reportId);
        return "admin/AdminPayrollEmployee";
    }

    @PostMapping("/admin/reports/{reportId}/payroll/{employeeId}/pay")
    @PreAuthorize("hasRole('ADMIN')")
    public String payEmployee(@PathVariable("reportId") int reportId,
                              @PathVariable("employeeId") int employeeId,
                              @ModelAttribute PayrollPaymentForm form,
                              @RequestParam(value = "proof", required = false) MultipartFile proof,
                              @AuthenticationPrincipal AuthenticatedUser user,
                              RedirectAttributes redirect) {
        try {
            String amount = payrollPaymentService.payEmployee(user, reportId, employeeId, form, proof);
            redirect.addFlashAttribute("success", "Đã ghi nhận thanh toán " + amount + ". Nhân viên đã nhận được thông báo.");
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("pageError", ex.getMessage());
        } catch (DataAccessException ex) {
            redirect.addFlashAttribute("pageError", PAYMENT_SCHEMA_MESSAGE);
        }
        return "redirect:/attendance/admin/reports/" + reportId + "/payroll/" + employeeId;
    }

    @GetMapping("/admin/reports/{reportId}/payments/{paymentId}/proof")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> paymentProof(@PathVariable("reportId") int reportId,
                                               @PathVariable("paymentId") int paymentId,
                                               @AuthenticationPrincipal AuthenticatedUser user) {
        PayrollPaymentService.ProofFile file = payrollPaymentService.proof(user, reportId, paymentId);
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(file.fileName(), java.nio.charset.StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(file.contentType()))
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
            redirect.addFlashAttribute("success", "Đã trả lại báo cáo cho Manager. Manager có thể tính lại lương và gửi lại.");
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
                            @RequestParam(value = "paymentStatus", required = false) String paymentStatus,
                            @RequestParam(value = "q", required = false) String query,
                            @AuthenticationPrincipal AuthenticatedUser user,
                            Model model) {
        try {
            AdminAttendanceReportPage page = attendanceReportService.adminPage(user, year, month, storeId, status);
            model.addAttribute("report", page);
            model.addAttribute("years", years(page.getYear()));
            model.addAttribute("list", payrollPaymentService.adminReports(user, page, paymentStatus, query));
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
            try {
                model.addAttribute("payroll", payrollPaymentService.reportPayroll(user, reportId));
            } catch (BusinessException ex) {
                model.addAttribute("payroll", null);
                model.addAttribute("payrollError", ex.getMessage());
            } catch (DataAccessException ex) {
                model.addAttribute("payroll", null);
                model.addAttribute("payrollError", PAYMENT_SCHEMA_MESSAGE);
            }
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

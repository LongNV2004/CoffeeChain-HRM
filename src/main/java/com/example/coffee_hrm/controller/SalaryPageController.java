package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.response.AdminStoreCard;
import com.example.coffee_hrm.dto.response.PayrollBoardView;
import com.example.coffee_hrm.dto.response.PayrollDetailView;
import com.example.coffee_hrm.dto.response.StoreResponse;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.PayrollPaymentService;
import com.example.coffee_hrm.service.PayrollService;
import com.example.coffee_hrm.service.StoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class SalaryPageController {

    private final PayrollService payrollService;
    private final StoreService storeService;
    private final PayrollPaymentService payrollPaymentService;

    /**
     * Admin không còn màn hình "Quản lý lương" riêng: bảng lương, duyệt và thanh toán nằm trong "Báo cáo cửa hàng".
     */
    @GetMapping("/admin/salary")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminSalary(@RequestParam(value = "year", required = false) Integer year,
                              @RequestParam(value = "month", required = false) Integer month,
                              @RequestParam(value = "period", required = false) String period) {
        return redirectAdminReports(year, month, period);
    }

    /** Manager không còn màn hình Lương riêng: bảng lương nằm trong "Danh sách chấm công và lương". */
    @GetMapping("/manager/salary")
    @PreAuthorize("hasRole('MANAGER')")
    public String managerSalary(@RequestParam(value = "year", required = false) Integer year,
                                @RequestParam(value = "month", required = false) Integer month,
                                @RequestParam(value = "period", required = false) String period) {
        return redirectManagerBoard(year, month, period);
    }

    @GetMapping("/staff/salary")
    @PreAuthorize("hasRole('STAFF')")
    public String staffSalary(@AuthenticationPrincipal AuthenticatedUser user,
                              @RequestParam(value = "year", required = false) Integer year,
                              @RequestParam(value = "month", required = false) Integer month,
                              @RequestParam(value = "period", required = false) String period,
                              @RequestParam(value = "storeId", required = false) Integer storeId,
                              @RequestParam(value = "employeeId", required = false) Integer employeeId,
                              @RequestParam(value = "status", required = false) String status,
                              Model model) {
        return render(user, "Cá nhân — Nhân viên", year, month, period, storeId, employeeId, status, model);
    }

    @GetMapping("/admin/salary/detail")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminDetail(@RequestParam(value = "year", required = false) Integer year,
                              @RequestParam(value = "month", required = false) Integer month) {
        return redirectAdminReports(year, month, null);
    }

    @GetMapping("/manager/salary/detail")
    @PreAuthorize("hasRole('MANAGER')")
    public String managerDetail(@AuthenticationPrincipal AuthenticatedUser user,
                                @RequestParam int year,
                                @RequestParam int month,
                                @RequestParam Integer employeeId,
                                Model model) {
        return detail(user, "Cửa hàng — Quản lý", year, month, employeeId, model);
    }

    @GetMapping("/staff/salary/detail")
    @PreAuthorize("hasRole('STAFF')")
    public String staffDetail(@AuthenticationPrincipal AuthenticatedUser user,
                              @RequestParam int year,
                              @RequestParam int month,
                              @RequestParam Integer employeeId,
                              Model model) {
        return detail(user, "Cá nhân — Nhân viên", year, month, employeeId, model);
    }

    @PostMapping("/manager/salary/calculate")
    @PreAuthorize("hasRole('MANAGER')")
    public String calculate(@AuthenticationPrincipal AuthenticatedUser user,
                            @RequestParam int year,
                            @RequestParam int month,
                            RedirectAttributes redirectAttributes) {
        payrollService.calculate(user, year, month);
        redirectAttributes.addFlashAttribute("successMessage", "Đã tính lương tháng " + month + "/" + year + ".");
        return redirectManagerBoard(year, month, null);
    }

    @PostMapping("/manager/salary/lock")
    @PreAuthorize("hasRole('MANAGER')")
    public String lock(@AuthenticationPrincipal AuthenticatedUser user,
                       @RequestParam int year,
                       @RequestParam int month,
                       RedirectAttributes redirectAttributes) {
        payrollService.lock(user, year, month);
        redirectAttributes.addFlashAttribute("successMessage", "Đã chốt lương tháng " + month + "/" + year + ".");
        return redirectManagerBoard(year, month, null);
    }

    @ExceptionHandler(BusinessException.class)
    public String handleBusiness(BusinessException ex,
                                 HttpServletRequest request,
                                 RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        String uri = request.getRequestURI() == null ? "" : request.getRequestURI();
        String year = request.getParameter("year");
        String month = request.getParameter("month");
        if (uri.contains("/manager/salary")) {
            Integer y = digits(year);
            Integer m = digits(month);
            return y != null && m != null ? redirectManagerBoard(y, m, null) : redirectManagerBoard(null, null, null);
        }
        if (uri.contains("/admin/salary")) {
            return redirectAdminReports(digits(year), digits(month), null);
        }
        String home = "/staff/salary";
        if (year != null && month != null && year.chars().allMatch(Character::isDigit)
                && month.chars().allMatch(Character::isDigit)) {
            return "redirect:" + home + "?year=" + year + "&month=" + month;
        }
        return "redirect:" + home;
    }

    /** Màn hình "Danh sách chấm công và lương" của Manager, mở đúng tháng đang xem. */
    static final String MANAGER_BOARD = "/attendance/manager/month";

    private String redirectManagerBoard(Integer year, Integer month, String period) {
        String value = null;
        if (period != null && period.matches("\\d{4}-\\d{2}")) {
            value = period;
        } else if (year != null && month != null && month >= 1 && month <= 12 && year >= 2000 && year <= 2100) {
            value = String.format("%04d-%02d", year, month);
        }
        return "redirect:" + MANAGER_BOARD + (value == null ? "" : "?month=" + value);
    }

    private String redirectAdminReports(Integer year, Integer month, String period) {
        Integer y = year;
        Integer m = month;
        if (period != null && period.matches("\\d{4}-\\d{2}")) {
            y = Integer.valueOf(period.substring(0, 4));
            m = Integer.valueOf(period.substring(5, 7));
        }
        if (y != null && m != null && m >= 1 && m <= 12 && y >= 2000 && y <= 2100) {
            return "redirect:/attendance/admin/reports?year=" + y + "&month=" + m;
        }
        return "redirect:/attendance/admin/reports";
    }

    private static Integer digits(String value) {
        if (value == null || value.isEmpty() || value.length() > 4 || !value.chars().allMatch(Character::isDigit)) {
            return null;
        }
        return Integer.valueOf(value);
    }

    private String render(AuthenticatedUser user,
                          String eyebrow,
                          Integer year,
                          Integer month,
                          String period,
                          Integer storeId,
                          Integer employeeId,
                          String status,
                          Model model) {
        model.addAttribute("navRole", payrollNav(user));
        model.addAttribute("eyebrow", eyebrow);
        PayrollBoardView board;
        try {
            board = payrollService.board(user, year, month, period, storeId, employeeId, status);
            model.addAttribute("board", board);
            model.addAttribute("pageError", null);
        } catch (BusinessException ex) {
            board = payrollService.empty(user);
            model.addAttribute("board", board);
            model.addAttribute("pageError", ex.getMessage());
        }
        if ("admin".equals(payrollNav(user)) && storeId == null) {
            model.addAttribute("storeCards", salaryCards(user, board));
        }
        if ("staff".equals(payrollNav(user))) {
            // Các kỳ lương đã được Admin duyệt và tình trạng nhận lương của chính nhân viên.
            try {
                model.addAttribute("periods", payrollPaymentService.myPeriods(user));
            } catch (BusinessException | org.springframework.dao.DataAccessException ex) {
                model.addAttribute("periods", List.of());
            }
        }
        return "salary/SalaryPage";
    }

    private List<AdminStoreCard> salaryCards(AuthenticatedUser user, PayrollBoardView board) {
        Map<Integer, List<PayrollBoardView.Row>> rowsByStore = new HashMap<>();
        for (PayrollBoardView.Row row : board.getRows()) {
            if (row.getStoreId() == null) {
                continue;
            }
            rowsByStore.computeIfAbsent(row.getStoreId(), ignored -> new ArrayList<>()).add(row);
        }
        String period = board.getMonthValue() == null ? "" : board.getMonthValue();
        List<AdminStoreCard> cards = new ArrayList<>();
        for (StoreResponse store : storeService.getStores(user)) {
            List<PayrollBoardView.Row> rows = rowsByStore.getOrDefault(store.getId(), List.of());
            int hours = rows.stream().mapToInt(PayrollBoardView.Row::getPayableHours).sum();
            cards.add(AdminStoreCard.of(
                    store,
                    rows.size() + " nhân viên",
                    hours + " giờ được tính lương · " + board.getPeriodLabel(),
                    "/admin/salary?storeId=" + store.getId() + "&period=" + period,
                    "Xem lương"));
        }
        return cards;
    }

    private String detail(AuthenticatedUser user,
                          String eyebrow,
                          int year,
                          int month,
                          Integer employeeId,
                          Model model) {
        PayrollDetailView detail = payrollService.detail(user, year, month, employeeId);
        model.addAttribute("navRole", payrollNav(user));
        model.addAttribute("eyebrow", eyebrow);
        model.addAttribute("detail", detail);
        return "salary/SalaryDetail";
    }

    private String payrollNav(AuthenticatedUser user) {
        return switch (user.getRoleName()) {
            case ADMIN -> "admin";
            case MANAGER -> "manager";
            case STAFF -> "staff";
        };
    }
}

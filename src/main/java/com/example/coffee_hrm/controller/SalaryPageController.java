package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.response.AdminStoreCard;
import com.example.coffee_hrm.dto.response.PayrollBoardView;
import com.example.coffee_hrm.dto.response.PayrollDetailView;
import com.example.coffee_hrm.dto.response.StoreResponse;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.PayrollService;
import com.example.coffee_hrm.service.StoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    @GetMapping("/admin/salary")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminSalary(@AuthenticationPrincipal AuthenticatedUser user,
                              @RequestParam(value = "year", required = false) Integer year,
                              @RequestParam(value = "month", required = false) Integer month,
                              @RequestParam(value = "period", required = false) String period,
                              @RequestParam(value = "storeId", required = false) Integer storeId,
                              @RequestParam(value = "employeeId", required = false) Integer employeeId,
                              @RequestParam(value = "status", required = false) String status,
                              Model model) {
        return render(user, "Trụ sở — Admin", year, month, period, storeId, employeeId, status, model);
    }

    @GetMapping("/manager/salary")
    @PreAuthorize("hasRole('MANAGER')")
    public String managerSalary(@AuthenticationPrincipal AuthenticatedUser user,
                                @RequestParam(value = "year", required = false) Integer year,
                                @RequestParam(value = "month", required = false) Integer month,
                                @RequestParam(value = "period", required = false) String period,
                                @RequestParam(value = "storeId", required = false) Integer storeId,
                                @RequestParam(value = "employeeId", required = false) Integer employeeId,
                                @RequestParam(value = "status", required = false) String status,
                                Model model) {
        return render(user, "Cửa hàng — Quản lý", year, month, period, storeId, employeeId, status, model);
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
    public String adminDetail(@AuthenticationPrincipal AuthenticatedUser user,
                              @RequestParam int year,
                              @RequestParam int month,
                              @RequestParam Integer employeeId,
                              Model model) {
        return detail(user, "Trụ sở — Admin", year, month, employeeId, model);
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
        redirectAttributes.addAttribute("year", year);
        redirectAttributes.addAttribute("month", month);
        return "redirect:/manager/salary";
    }

    @PostMapping("/manager/salary/lock")
    @PreAuthorize("hasRole('MANAGER')")
    public String lock(@AuthenticationPrincipal AuthenticatedUser user,
                       @RequestParam int year,
                       @RequestParam int month,
                       RedirectAttributes redirectAttributes) {
        payrollService.lock(user, year, month);
        redirectAttributes.addFlashAttribute("successMessage", "Đã chốt lương tháng " + month + "/" + year + ".");
        redirectAttributes.addAttribute("year", year);
        redirectAttributes.addAttribute("month", month);
        return "redirect:/manager/salary";
    }

    @GetMapping("/admin/salary/export")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> adminExport(@AuthenticationPrincipal AuthenticatedUser user,
                                              @RequestParam(value = "year", required = false) Integer year,
                                              @RequestParam(value = "month", required = false) Integer month,
                                              @RequestParam(value = "period", required = false) String period,
                                              @RequestParam(value = "storeId", required = false) Integer storeId,
                                              @RequestParam(value = "employeeId", required = false) Integer employeeId,
                                              @RequestParam(value = "status", required = false) String status) {
        return excel(user, year, month, period, storeId, employeeId, status);
    }

    @GetMapping("/manager/salary/export")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<byte[]> managerExport(@AuthenticationPrincipal AuthenticatedUser user,
                                                @RequestParam(value = "year", required = false) Integer year,
                                                @RequestParam(value = "month", required = false) Integer month,
                                                @RequestParam(value = "period", required = false) String period,
                                                @RequestParam(value = "storeId", required = false) Integer storeId,
                                                @RequestParam(value = "employeeId", required = false) Integer employeeId,
                                                @RequestParam(value = "status", required = false) String status) {
        return excel(user, year, month, period, storeId, employeeId, status);
    }

    @GetMapping("/staff/salary/export")
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<byte[]> staffExport(@AuthenticationPrincipal AuthenticatedUser user,
                                              @RequestParam(value = "year", required = false) Integer year,
                                              @RequestParam(value = "month", required = false) Integer month,
                                              @RequestParam(value = "period", required = false) String period,
                                              @RequestParam(value = "employeeId", required = false) Integer employeeId,
                                              @RequestParam(value = "status", required = false) String status) {
        return excel(user, year, month, period, null, employeeId, status);
    }

    @ExceptionHandler(BusinessException.class)
    public String handleBusiness(BusinessException ex,
                                 HttpServletRequest request,
                                 RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        String uri = request.getRequestURI() == null ? "" : request.getRequestURI();
        String home = uri.contains("/admin/salary")
                ? "/admin/salary"
                : uri.contains("/staff/salary") ? "/staff/salary" : "/manager/salary";
        String year = request.getParameter("year");
        String month = request.getParameter("month");
        if (year != null && month != null && year.chars().allMatch(Character::isDigit)
                && month.chars().allMatch(Character::isDigit)) {
            return "redirect:" + home + "?year=" + year + "&month=" + month;
        }
        return "redirect:" + home;
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

    private ResponseEntity<byte[]> excel(AuthenticatedUser user,
                                         Integer year,
                                         Integer month,
                                         String period,
                                         Integer storeId,
                                         Integer employeeId,
                                         String status) {
        byte[] body = payrollService.export(user, year, month, period, storeId, employeeId, status);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(payrollService.exportFileName(year, month, period))
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType("application/vnd.ms-excel"))
                .body(body);
    }

    private String payrollNav(AuthenticatedUser user) {
        return switch (user.getRoleName()) {
            case ADMIN -> "admin";
            case MANAGER -> "manager";
            case STAFF -> "staff";
        };
    }
}

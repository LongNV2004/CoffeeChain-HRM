package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.request.CreateOvertimeRequestDto;
import com.example.coffee_hrm.dto.response.OvertimePageView;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.OvertimeRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;

@Controller
@RequiredArgsConstructor
public class OvertimePageController {

    private final OvertimeRequestService overtimeRequestService;

    @GetMapping("/schedule/manager/overtime")
    @PreAuthorize("hasRole('MANAGER')")
    public String managerOvertime(@RequestParam(required = false) String week,
                                  @RequestParam(required = false) String status,
                                  @AuthenticationPrincipal AuthenticatedUser user,
                                  Model model) {
        model.addAttribute("navRole", "manager");
        model.addAttribute("eyebrow", "Cửa hàng — Quản lý");
        String normalizedStatus = normalizeStatus(status);
        model.addAttribute("statusFilter", normalizedStatus);
        try {
            LocalDate parsedWeek = parseWeek(week);
            OvertimePageView view = overtimeRequestService.getManagerOvertimePage(user, parsedWeek, normalizedStatus);
            model.addAttribute("overtimeView", view);
            model.addAttribute("pageError", null);
        } catch (BusinessException ex) {
            model.addAttribute("overtimeView", null);
            model.addAttribute("pageError", ex.getMessage());
        } catch (DateTimeParseException ex) {
            model.addAttribute("overtimeView", null);
            model.addAttribute("pageError", "Tuần đã chọn không hợp lệ.");
        } catch (org.springframework.dao.DataAccessException ex) {
            model.addAttribute("overtimeView", null);
            model.addAttribute("pageError", "Chưa đồng bộ bảng OvertimeRequests. Vui lòng khởi động lại ứng dụng để tự động cập nhật bảng dữ liệu.");
        }
        return "overtime/OvertimePage";
    }

    @PostMapping("/schedule/manager/overtime/{id}/review")
    @PreAuthorize("hasRole('MANAGER')")
    public String reviewOvertime(@PathVariable Integer id,
                                 @RequestParam boolean approved,
                                 @RequestParam(required = false) String reviewNote,
                                 @RequestParam(required = false) String week,
                                 @RequestParam(required = false) String status,
                                 @AuthenticationPrincipal AuthenticatedUser user,
                                 RedirectAttributes redirectAttributes) {
        try {
            overtimeRequestService.reviewOvertimeRequest(id, approved, reviewNote, user);
            redirectAttributes.addFlashAttribute("successMessage",
                    approved
                            ? "Đã duyệt đơn xin tăng ca. Ca làm việc đã được xếp vào Lịch làm việc và nhân viên đã được thông báo."
                            : "Đã từ chối đơn xin tăng ca. Nhân viên đã được thông báo.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        keepParams(redirectAttributes, week, status);
        return "redirect:/schedule/manager/overtime";
    }

    @PostMapping("/schedule/manager/overtime/review-all")
    @PreAuthorize("hasRole('MANAGER')")
    public String reviewAllOvertime(@RequestParam boolean approved,
                                    @RequestParam(required = false) String week,
                                    @RequestParam(required = false) String status,
                                    @AuthenticationPrincipal AuthenticatedUser user,
                                    RedirectAttributes redirectAttributes) {
        try {
            int count = overtimeRequestService.reviewAllPending(approved, null, user);
            redirectAttributes.addFlashAttribute("successMessage",
                    approved
                            ? "Đã duyệt tất cả " + count + " đơn xin tăng ca đang chờ. Các ca đã được thêm vào Lịch làm việc."
                            : "Đã từ chối tất cả " + count + " đơn xin tăng ca đang chờ.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        keepParams(redirectAttributes, week, status);
        return "redirect:/schedule/manager/overtime";
    }

    @GetMapping("/schedule/staff/overtime")
    @PreAuthorize("hasRole('STAFF')")
    public String staffOvertime(@RequestParam(required = false) String week,
                                @AuthenticationPrincipal AuthenticatedUser user,
                                Model model) {
        populateStaffPage(user, week, model);
        if (!model.containsAttribute("overtimeForm")) {
            model.addAttribute("overtimeForm", new CreateOvertimeRequestDto());
        }
        return "overtime/OvertimePage";
    }

    @PostMapping("/schedule/staff/overtime")
    @PreAuthorize("hasRole('STAFF')")
    public String submitStaffOvertime(@ModelAttribute("overtimeForm") CreateOvertimeRequestDto overtimeForm,
                                      @RequestParam(required = false) String week,
                                      @AuthenticationPrincipal AuthenticatedUser user,
                                      Model model,
                                      RedirectAttributes redirectAttributes) {
        try {
            overtimeRequestService.submitOvertimeRequest(overtimeForm, user);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã gửi đơn xin tăng ca thành công. Quản lý cửa hàng đã nhận được thông báo và sẽ xét duyệt đơn của bạn.");
            if (overtimeForm != null && overtimeForm.getWorkDate() != null) {
                LocalDate targetMonday = overtimeForm.getWorkDate()
                        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                redirectAttributes.addAttribute("week", targetMonday.toString());
            } else if (week != null && !week.isBlank()) {
                redirectAttributes.addAttribute("week", week);
            }
            return "redirect:/schedule/staff/overtime";
        } catch (BusinessException ex) {
            String targetWeek = overtimeForm != null && overtimeForm.getWorkDate() != null
                    ? overtimeForm.getWorkDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()
                    : week;
            populateStaffPage(user, targetWeek, model);
            model.addAttribute("overtimeForm", overtimeForm);
            model.addAttribute("errorMessage", ex.getMessage());
            return "overtime/OvertimePage";
        }
    }

    @PostMapping("/schedule/staff/overtime/{id}/cancel")
    @PreAuthorize("hasRole('STAFF')")
    public String cancelStaffOvertime(@PathVariable Integer id,
                                      @RequestParam(required = false) String week,
                                      @AuthenticationPrincipal AuthenticatedUser user,
                                      RedirectAttributes redirectAttributes) {
        try {
            overtimeRequestService.cancelOvertimeRequest(id, user);
            redirectAttributes.addFlashAttribute("successMessage", "Đã hủy đơn xin tăng ca.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        keepParams(redirectAttributes, week, null);
        return "redirect:/schedule/staff/overtime";
    }

    private void populateStaffPage(AuthenticatedUser user, String week, Model model) {
        model.addAttribute("navRole", "staff");
        model.addAttribute("eyebrow", "Cá nhân — Nhân viên");
        try {
            LocalDate parsedWeek = parseWeek(week);
            OvertimePageView view = overtimeRequestService.getStaffOvertimePage(user, parsedWeek);
            model.addAttribute("overtimeView", view);
            model.addAttribute("pageError", null);
        } catch (BusinessException ex) {
            model.addAttribute("overtimeView", null);
            model.addAttribute("pageError", ex.getMessage());
        } catch (DateTimeParseException ex) {
            model.addAttribute("overtimeView", null);
            model.addAttribute("pageError", "Tuần đã chọn không hợp lệ.");
        } catch (org.springframework.dao.DataAccessException ex) {
            model.addAttribute("overtimeView", null);
            model.addAttribute("pageError", "Chưa đồng bộ bảng OvertimeRequests. Vui lòng khởi động lại ứng dụng để tự động cập nhật bảng dữ liệu.");
        }
    }

    private static LocalDate parseWeek(String week) {
        if (week == null || week.isBlank()) {
            return null;
        }
        return LocalDate.parse(week.trim());
    }

    private static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "all";
        }
        String lower = status.trim().toLowerCase();
        return switch (lower) {
            case "pending", "approved", "rejected" -> lower;
            default -> "all";
        };
    }

    private static void keepParams(RedirectAttributes redirectAttributes, String week, String status) {
        if (week != null && !week.isBlank()) {
            redirectAttributes.addAttribute("week", week);
        }
        if (status != null && !status.isBlank() && !"all".equalsIgnoreCase(status)) {
            redirectAttributes.addAttribute("status", status);
        }
    }
}

package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.OvertimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

/** Đăng kí tăng ca: nhân viên gửi đơn làm ca thứ 3 trong ngày, quản lý duyệt hoặc từ chối. */
@Controller
@RequiredArgsConstructor
public class OvertimePageController {

    static final String STAFF_PATH = "/schedule/staff/overtime";
    static final String MANAGER_PATH = "/schedule/manager/overtime";
    static final String SCHEMA_MESSAGE =
            "Chưa đọc được dữ liệu đơn tăng ca. Hãy chạy script src/main/resources/db/overtime-request.sql.";

    private final OvertimeService overtimeService;

    @GetMapping(MANAGER_PATH)
    @PreAuthorize("hasRole('MANAGER')")
    public String managerOvertime(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        model.addAttribute("navRole", "manager");
        model.addAttribute("eyebrow", "Cửa hàng — Quản lý");
        try {
            model.addAttribute("page", overtimeService.managerPage(user));
        } catch (BusinessException ex) {
            model.addAttribute("pageError", ex.getMessage());
        } catch (DataAccessException ex) {
            model.addAttribute("pageError", SCHEMA_MESSAGE);
        }
        return "overtime/OvertimePage";
    }

    @GetMapping(STAFF_PATH)
    @PreAuthorize("hasRole('STAFF')")
    public String staffOvertime(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        model.addAttribute("navRole", "staff");
        model.addAttribute("eyebrow", "Cá nhân — Nhân viên");
        try {
            model.addAttribute("page", overtimeService.staffPage(user));
        } catch (BusinessException ex) {
            model.addAttribute("pageError", ex.getMessage());
        } catch (DataAccessException ex) {
            model.addAttribute("pageError", SCHEMA_MESSAGE);
        }
        return "overtime/OvertimePage";
    }

    @PostMapping(STAFF_PATH)
    @PreAuthorize("hasRole('STAFF')")
    public String submit(@AuthenticationPrincipal AuthenticatedUser user,
                         @RequestParam(value = "shiftId", required = false) Integer shiftId,
                         @RequestParam(value = "workDate", required = false)
                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate workDate,
                         @RequestParam(value = "reason", required = false) String reason,
                         RedirectAttributes redirect) {
        return run(redirect, STAFF_PATH, "Đã gửi đơn tăng ca. Vui lòng chờ quản lý duyệt.",
                () -> overtimeService.submit(user, shiftId, workDate, reason));
    }

    @PostMapping(STAFF_PATH + "/{id}/cancel")
    @PreAuthorize("hasRole('STAFF')")
    public String cancel(@AuthenticationPrincipal AuthenticatedUser user,
                         @PathVariable("id") Integer id,
                         RedirectAttributes redirect) {
        return run(redirect, STAFF_PATH, "Đã hủy đơn tăng ca.", () -> overtimeService.cancel(user, id));
    }

    @PostMapping(MANAGER_PATH + "/{id}/approve")
    @PreAuthorize("hasRole('MANAGER')")
    public String approve(@AuthenticationPrincipal AuthenticatedUser user,
                          @PathVariable("id") Integer id,
                          RedirectAttributes redirect) {
        return run(redirect, MANAGER_PATH, "Đã duyệt đơn tăng ca và thêm ca vào lịch làm việc.",
                () -> overtimeService.approve(user, id));
    }

    @PostMapping(MANAGER_PATH + "/{id}/reject")
    @PreAuthorize("hasRole('MANAGER')")
    public String reject(@AuthenticationPrincipal AuthenticatedUser user,
                         @PathVariable("id") Integer id,
                         @RequestParam(value = "reason", required = false) String reason,
                         RedirectAttributes redirect) {
        return run(redirect, MANAGER_PATH, "Đã từ chối đơn tăng ca.", () -> overtimeService.reject(user, id, reason));
    }

    private String run(RedirectAttributes redirect, String path, String success, Runnable action) {
        try {
            action.run();
            redirect.addFlashAttribute("successMessage", success);
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        } catch (DataAccessException ex) {
            redirect.addFlashAttribute("errorMessage", SCHEMA_MESSAGE);
        }
        return "redirect:" + path;
    }
}

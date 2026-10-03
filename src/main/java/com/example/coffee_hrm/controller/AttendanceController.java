package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.web.ClientIpResolver;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.AttendanceService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@RequestMapping("/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;

    @GetMapping("/staff")
    @PreAuthorize("hasRole('STAFF')")
    public String staffClock(@AuthenticationPrincipal AuthenticatedUser user,
                             HttpServletRequest request,
                             Model model) {
        return clock(user, request, model, "staff/StaffCheckIn");
    }

    @PostMapping("/staff/check-in")
    @PreAuthorize("hasRole('STAFF')")
    public String staffCheckIn(@AuthenticationPrincipal AuthenticatedUser user,
                               HttpServletRequest request,
                               RedirectAttributes redirectAttributes) {
        return submitCheckIn(user, request, redirectAttributes, "redirect:/attendance/staff");
    }

    @PostMapping("/staff/check-out")
    @PreAuthorize("hasRole('STAFF')")
    public String staffCheckOut(@AuthenticationPrincipal AuthenticatedUser user,
                                HttpServletRequest request,
                                RedirectAttributes redirectAttributes) {
        return submitCheckOut(user, request, redirectAttributes, "redirect:/attendance/staff");
    }

    @GetMapping("/manager")
    @PreAuthorize("hasRole('MANAGER')")
    public String managerClock(@AuthenticationPrincipal AuthenticatedUser user,
                               HttpServletRequest request,
                               Model model) {
        return clock(user, request, model, "manager/ManagerCheckIn");
    }

    @PostMapping("/manager/check-in")
    @PreAuthorize("hasRole('MANAGER')")
    public String managerCheckIn(@AuthenticationPrincipal AuthenticatedUser user,
                                 HttpServletRequest request,
                                 RedirectAttributes redirectAttributes) {
        return submitCheckIn(user, request, redirectAttributes, "redirect:/attendance/manager");
    }

    @PostMapping("/manager/check-out")
    @PreAuthorize("hasRole('MANAGER')")
    public String managerCheckOut(@AuthenticationPrincipal AuthenticatedUser user,
                                  HttpServletRequest request,
                                  RedirectAttributes redirectAttributes) {
        return submitCheckOut(user, request, redirectAttributes, "redirect:/attendance/manager");
    }

    @PostMapping("/staff/ip")
    @PreAuthorize("hasRole('STAFF')")
    public String staffUpdateStoreIp(@AuthenticationPrincipal AuthenticatedUser user,
                                     HttpServletRequest request,
                                     RedirectAttributes redirectAttributes) {
        return updateStoreIp(user, request, redirectAttributes, "redirect:/attendance/staff");
    }

    @PostMapping("/manager/ip")
    @PreAuthorize("hasRole('MANAGER')")
    public String managerUpdateStoreIp(@AuthenticationPrincipal AuthenticatedUser user,
                                       HttpServletRequest request,
                                       RedirectAttributes redirectAttributes) {
        return updateStoreIp(user, request, redirectAttributes, "redirect:/attendance/manager");
    }

    private String updateStoreIp(AuthenticatedUser user,
                                 HttpServletRequest request,
                                 RedirectAttributes redirectAttributes,
                                 String redirect) {
        try {
            attendanceService.updateStoreIp(user, ClientIpResolver.resolve(request));
            redirectAttributes.addFlashAttribute("pageMessage", "Đã cập nhật IP mạng cửa hàng theo máy đang kết nối.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("pageError", ex.getMessage());
        }
        return redirect;
    }

    private String clock(AuthenticatedUser user, HttpServletRequest request, Model model, String view) {
        try {
            model.addAttribute("clock", attendanceService.getClock(user, ClientIpResolver.resolve(request)));
            if (!model.containsAttribute("pageError")) {
                model.addAttribute("pageError", null);
            }
        } catch (BusinessException ex) {
            model.addAttribute("clock", null);
            model.addAttribute("pageError", ex.getMessage());
        }
        return view;
    }

    private String submitCheckIn(AuthenticatedUser user,
                                 HttpServletRequest request,
                                 RedirectAttributes redirectAttributes,
                                 String redirect) {
        try {
            attendanceService.checkIn(user, ClientIpResolver.resolve(request));
            redirectAttributes.addFlashAttribute("pageMessage", "Check-in thành công.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("pageError", ex.getMessage());
        }
        return redirect;
    }

    private String submitCheckOut(AuthenticatedUser user,
                                  HttpServletRequest request,
                                  RedirectAttributes redirectAttributes,
                                  String redirect) {
        try {
            attendanceService.checkOut(user, ClientIpResolver.resolve(request));
            redirectAttributes.addFlashAttribute("pageMessage", "Check-out thành công.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("pageError", ex.getMessage());
        }
        return redirect;
    }
}

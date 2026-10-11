package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.request.AttendanceLocation;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@RequestMapping("/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;

    @GetMapping("/staff")
    @PreAuthorize("hasRole('STAFF')")
    public String staffClock(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        return clock(user, model, "staff/StaffCheckIn");
    }

    @PostMapping("/staff/check-in")

    @PreAuthorize("hasRole('STAFF')")
    public String staffCheckIn(@AuthenticationPrincipal AuthenticatedUser user,
                               @RequestParam(required = false) String latitude,
                               @RequestParam(required = false) String longitude,
                               @RequestParam(required = false) String accuracy,
                               RedirectAttributes redirectAttributes) {
        return submitCheckIn(user, latitude, longitude, accuracy, redirectAttributes, "redirect:/attendance/staff");
    }

    @PostMapping("/staff/check-out")
    @PreAuthorize("hasRole('STAFF')")
    public String staffCheckOut(@AuthenticationPrincipal AuthenticatedUser user,
                                @RequestParam(required = false) String latitude,
                                @RequestParam(required = false) String longitude,
                                @RequestParam(required = false) String accuracy,
                                RedirectAttributes redirectAttributes) {
        return submitCheckOut(user, latitude, longitude, accuracy, redirectAttributes, "redirect:/attendance/staff");
    }

    @GetMapping("/manager")
    @PreAuthorize("hasRole('MANAGER')")
    public String managerClock(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        return clock(user, model, "manager/ManagerCheckIn");
    }

    @PostMapping("/manager/check-in")
    @PreAuthorize("hasRole('MANAGER')")
    public String managerCheckIn(@AuthenticationPrincipal AuthenticatedUser user,
                                 @RequestParam(required = false) String latitude,
                                 @RequestParam(required = false) String longitude,
                                 @RequestParam(required = false) String accuracy,
                                 RedirectAttributes redirectAttributes) {
        return submitCheckIn(user, latitude, longitude, accuracy, redirectAttributes, "redirect:/attendance/manager");
    }

    @PostMapping("/manager/check-out")
    @PreAuthorize("hasRole('MANAGER')")
    public String managerCheckOut(@AuthenticationPrincipal AuthenticatedUser user,
                                  @RequestParam(required = false) String latitude,
                                  @RequestParam(required = false) String longitude,
                                  @RequestParam(required = false) String accuracy,
                                  RedirectAttributes redirectAttributes) {
        return submitCheckOut(user, latitude, longitude, accuracy, redirectAttributes, "redirect:/attendance/manager");
    }

    private String clock(AuthenticatedUser user, Model model, String view) {
        try {
            model.addAttribute("clock", attendanceService.getClock(user));
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
                                 String latitude,
                                 String longitude,
                                 String accuracy,
                                 RedirectAttributes redirectAttributes,
                                 String redirect) {
        try {
            attendanceService.checkIn(user, AttendanceLocation.parse(latitude, longitude, accuracy));
            redirectAttributes.addFlashAttribute("pageMessage", "Check-in thành công.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("pageError", ex.getMessage());
        }
        return redirect;
    }

    private String submitCheckOut(AuthenticatedUser user,
                                  String latitude,
                                  String longitude,
                                  String accuracy,
                                  RedirectAttributes redirectAttributes,
                                  String redirect) {
        try {
            attendanceService.checkOut(user, AttendanceLocation.parse(latitude, longitude, accuracy));
            redirectAttributes.addFlashAttribute("pageMessage", "Check-out thành công.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("pageError", ex.getMessage());
        }
        return redirect;
    }
}

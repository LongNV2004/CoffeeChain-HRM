package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.request.AttendanceLocation;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.TrainingAttendanceService;
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
public class TrainingAttendanceController {

    private final TrainingAttendanceService trainingAttendanceService;

    @GetMapping("/staff/choose")
    @PreAuthorize("hasRole('STAFF')")
    public String staffChoice(Model model) {
        model.addAttribute("navbarRole", "staff");
        model.addAttribute("shiftUrl", "/attendance/staff");
        model.addAttribute("trainingUrl", "/attendance/staff/training");
        model.addAttribute("historyUrl", "/attendance/staff/history");
        model.addAttribute("eyebrow", "Cá nhân — Staff");
        return "attendance/AttendanceChoice";
    }

    @GetMapping("/manager/choose")
    @PreAuthorize("hasRole('MANAGER')")
    public String managerChoice(Model model) {
        model.addAttribute("navbarRole", "manager");
        model.addAttribute("shiftUrl", "/attendance/manager");
        model.addAttribute("trainingUrl", "/attendance/manager/training");
        model.addAttribute("historyUrl", "/attendance/manager/history");
        model.addAttribute("eyebrow", "Cửa hàng — Manager");
        return "attendance/AttendanceChoice";
    }

    @GetMapping("/staff/training")
    @PreAuthorize("hasRole('STAFF')")
    public String staffClock(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        return clock(user, model, "/attendance/staff/training", "/attendance/staff/history", "staff");
    }

    @PostMapping("/staff/training/check-in")
    @PreAuthorize("hasRole('STAFF')")
    public String staffCheckIn(@AuthenticationPrincipal AuthenticatedUser user,
                               @RequestParam(required = false) Integer classId,
                               @RequestParam(required = false) String latitude,
                               @RequestParam(required = false) String longitude,
                               @RequestParam(required = false) String accuracy,
                               RedirectAttributes redirectAttributes) {
        return submitCheckIn(user, classId, latitude, longitude, accuracy, redirectAttributes,
                "redirect:/attendance/staff/training");
    }

    @PostMapping("/staff/training/check-out")
    @PreAuthorize("hasRole('STAFF')")
    public String staffCheckOut(@AuthenticationPrincipal AuthenticatedUser user,
                                @RequestParam(required = false) Integer classId,
                                @RequestParam(required = false) String latitude,
                                @RequestParam(required = false) String longitude,
                                @RequestParam(required = false) String accuracy,
                                RedirectAttributes redirectAttributes) {
        return submitCheckOut(user, classId, latitude, longitude, accuracy, redirectAttributes,
                "redirect:/attendance/staff/training");
    }

    @GetMapping("/manager/training")
    @PreAuthorize("hasRole('MANAGER')")
    public String managerClock(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        return clock(user, model, "/attendance/manager/training", "/attendance/manager/history", "manager");
    }

    @PostMapping("/manager/training/check-in")
    @PreAuthorize("hasRole('MANAGER')")
    public String managerCheckIn(@AuthenticationPrincipal AuthenticatedUser user,
                                 @RequestParam(required = false) Integer classId,
                                 @RequestParam(required = false) String latitude,
                                 @RequestParam(required = false) String longitude,
                                 @RequestParam(required = false) String accuracy,
                                 RedirectAttributes redirectAttributes) {
        return submitCheckIn(user, classId, latitude, longitude, accuracy, redirectAttributes,
                "redirect:/attendance/manager/training");
    }

    @PostMapping("/manager/training/check-out")
    @PreAuthorize("hasRole('MANAGER')")
    public String managerCheckOut(@AuthenticationPrincipal AuthenticatedUser user,
                                  @RequestParam(required = false) Integer classId,
                                  @RequestParam(required = false) String latitude,
                                  @RequestParam(required = false) String longitude,
                                  @RequestParam(required = false) String accuracy,
                                  RedirectAttributes redirectAttributes) {
        return submitCheckOut(user, classId, latitude, longitude, accuracy, redirectAttributes,
                "redirect:/attendance/manager/training");
    }

    private String clock(AuthenticatedUser user,
                         Model model,
                         String actionBase,
                         String historyUrl,
                         String role) {
        model.addAttribute("actionBase", actionBase);
        model.addAttribute("historyUrl", historyUrl);
        model.addAttribute("chooseUrl", "manager".equals(role) ? "/attendance/manager/choose" : "/attendance/staff/choose");
        model.addAttribute("navbarRole", role);
        model.addAttribute("eyebrow", "manager".equals(role) ? "Cửa hàng — Manager" : "Cá nhân — Staff");
        try {
            model.addAttribute("clock", trainingAttendanceService.getClock(user));
            if (!model.containsAttribute("pageError")) {
                model.addAttribute("pageError", null);
            }
        } catch (BusinessException ex) {
            model.addAttribute("clock", null);
            model.addAttribute("pageError", ex.getMessage());
        }
        return "attendance/TrainingCheckIn";
    }

    private String submitCheckIn(AuthenticatedUser user,
                                 Integer classId,
                                 String latitude,
                                 String longitude,
                                 String accuracy,
                                 RedirectAttributes redirectAttributes,
                                 String redirect) {
        try {
            trainingAttendanceService.checkIn(user, classId, AttendanceLocation.parse(latitude, longitude, accuracy));
            redirectAttributes.addFlashAttribute("pageMessage", "Check-in thành công.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("pageError", ex.getMessage());
        }
        return redirect;
    }

    private String submitCheckOut(AuthenticatedUser user,
                                  Integer classId,
                                  String latitude,
                                  String longitude,
                                  String accuracy,
                                  RedirectAttributes redirectAttributes,
                                  String redirect) {
        try {
            trainingAttendanceService.checkOut(user, classId, AttendanceLocation.parse(latitude, longitude, accuracy));
            redirectAttributes.addFlashAttribute("pageMessage", "Check-out thành công.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("pageError", ex.getMessage());
        }
        return redirect;
    }
}

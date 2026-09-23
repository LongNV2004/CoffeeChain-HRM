package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequiredArgsConstructor
@RequestMapping("/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminDashboard(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        model.addAttribute("dashboard", dashboardService.buildAdminDashboard(user));
        return "AdminDashboard";
    }

    @GetMapping("/manager")
    @PreAuthorize("hasRole('MANAGER')")
    public String managerDashboard(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        model.addAttribute("dashboard", dashboardService.buildManagerDashboard(user));
        return "ManagerDashboard";
    }

    @GetMapping("/staff")
    @PreAuthorize("hasRole('STAFF')")
    public String staffDashboard(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        model.addAttribute("dashboard", dashboardService.buildStaffDashboard(user));
        return "StaffDashboard";
    }
}

package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.StaffAvailabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequiredArgsConstructor
@RequestMapping("/availability/manager")
@PreAuthorize("hasRole('MANAGER')")
public class ManagerAvailabilityController {

    private final StaffAvailabilityService staffAvailabilityService;

    @GetMapping
    public String managerAvailabilityPage(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        model.addAttribute("displayName", user.getDisplayName());
        try {
            model.addAttribute("availabilityGrid", staffAvailabilityService.getStoreNextWeekAvailabilityGrid(user));
            model.addAttribute("availabilities", staffAvailabilityService.listStoreNextWeekAvailabilities(user));
            model.addAttribute("pageError", null);
        } catch (BusinessException ex) {
            model.addAttribute("availabilityGrid", null);
            model.addAttribute("availabilities", java.util.List.of());
            model.addAttribute("pageError", ex.getMessage());
        }
        return "ManagerAvailability";
    }
}

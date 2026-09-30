package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.response.WorkAvailabilityResponse;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.StaffAvailabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

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
            List<WorkAvailabilityResponse> availabilities = staffAvailabilityService.listStoreNextWeekAvailabilities(user);
            model.addAttribute("availabilities", availabilities);
            model.addAttribute("pendingCount", availabilities.stream().filter(WorkAvailabilityResponse::isPending).count());
            model.addAttribute("pageError", null);
        } catch (BusinessException ex) {
            model.addAttribute("availabilityGrid", null);
            model.addAttribute("availabilities", List.of());
            model.addAttribute("pendingCount", 0L);
            model.addAttribute("pageError", ex.getMessage());
        }
        return "manager/ManagerAvailability";
    }

    @PostMapping("/approve-all")
    public String approveAll(@AuthenticationPrincipal AuthenticatedUser user,
                             RedirectAttributes redirectAttributes) {
        return reviewAll(true, user, redirectAttributes);
    }

    @PostMapping("/reject-all")
    public String rejectAll(@AuthenticationPrincipal AuthenticatedUser user,
                            RedirectAttributes redirectAttributes) {
        return reviewAll(false, user, redirectAttributes);
    }

    private String reviewAll(boolean approved,
                             AuthenticatedUser user,
                             RedirectAttributes redirectAttributes) {
        try {
            String message = staffAvailabilityService.reviewAllNextWeekAvailabilities(approved, user);
            redirectAttributes.addFlashAttribute("successMessage", message);
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/availability/manager";
    }
}

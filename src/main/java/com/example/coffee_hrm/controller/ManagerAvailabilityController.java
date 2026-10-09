package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.response.WeeklyAvailabilityView;
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
import org.springframework.web.bind.annotation.RequestParam;
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
            WeeklyAvailabilityView grid = staffAvailabilityService.getStoreNextWeekAvailabilityGrid(user);
            model.addAttribute("availabilityGrid", grid);
            List<WeeklyAvailabilityView.RegistrationGroup> registrations =
                    grid.getRegistrations() != null ? grid.getRegistrations() : List.of();
            model.addAttribute("registrations", registrations);
            model.addAttribute("pendingCount", registrations.stream().filter(WeeklyAvailabilityView.RegistrationGroup::isPending).count());
            model.addAttribute("pageError", null);
        } catch (BusinessException ex) {
            model.addAttribute("availabilityGrid", null);
            model.addAttribute("registrations", List.of());
            model.addAttribute("pendingCount", 0L);
            model.addAttribute("pageError", ex.getMessage());
        }
        return "manager/ManagerAvailability";
    }

    @PostMapping("/review")
    public String review(@RequestParam(required = false) String registrationKey,
                         @RequestParam(required = false) Integer availabilityId,
                         @RequestParam boolean approved,
                         @AuthenticationPrincipal AuthenticatedUser user,
                         RedirectAttributes redirectAttributes) {
        try {
            if (registrationKey != null && !registrationKey.isBlank()) {
                staffAvailabilityService.reviewRegistration(registrationKey, approved, user);
            } else if (availabilityId != null) {
                staffAvailabilityService.reviewAvailability(availabilityId, approved, user);
            } else {
                throw new BusinessException("Không tìm thấy đăng ký cần duyệt.");
            }
            redirectAttributes.addFlashAttribute("successMessage",
                    approved
                            ? "Đã duyệt đăng ký. Các ca đã được xếp vào lịch làm việc trong thời hạn đăng ký. Nhân viên đã được thông báo."
                            : "Đã từ chối đăng ký. Nhân viên đã được thông báo và các slot này không được xếp vào lịch.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/availability/manager";
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

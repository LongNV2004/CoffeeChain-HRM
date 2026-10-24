package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.response.SlotLimitUpdateResult;
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
            model.addAttribute("pageError", null);
        } catch (BusinessException ex) {
            model.addAttribute("availabilityGrid", null);
            model.addAttribute("registrations", List.of());
            model.addAttribute("pageError", ex.getMessage());
        }
        return "manager/ManagerAvailability";
    }

    @GetMapping("/limits")
    public String slotLimitsPage(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        model.addAttribute("displayName", user.getDisplayName());
        try {
            model.addAttribute("availabilityGrid", staffAvailabilityService.getStoreNextWeekAvailabilityGrid(user));
            model.addAttribute("staffSlotCapacity", staffAvailabilityService.countStaffForSlotLimit(user));
            model.addAttribute("pageError", null);
        } catch (BusinessException ex) {
            model.addAttribute("availabilityGrid", null);
            model.addAttribute("staffSlotCapacity", null);
            model.addAttribute("pageError", ex.getMessage());
        }
        return "manager/ManagerSlotLimits";
    }

    @PostMapping("/limits")
    public String updateLimit(@RequestParam Integer shiftId,
                              @RequestParam Integer dayOfWeek,
                              @RequestParam(required = false) String maxEmployees,
                              @AuthenticationPrincipal AuthenticatedUser user,
                              RedirectAttributes redirectAttributes) {
        try {
            SlotLimitUpdateResult result = staffAvailabilityService.updateSlotLimit(
                    shiftId, dayOfWeek, maxEmployees, user);
            if (result.isWarning()) {
                redirectAttributes.addFlashAttribute("warningMessage", result.getMessage());
            } else {
                redirectAttributes.addFlashAttribute("successMessage", result.getMessage());
            }
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/availability/manager/limits";
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
                            ? "Đã duyệt đơn đăng ký ca của nhân viên này. Các ca trong đơn đã được xếp vào lịch làm việc. Nhân viên đã được thông báo."
                            : "Đã từ chối đơn đăng ký ca của nhân viên này. Nhân viên đã được thông báo và các ca trong đơn không được xếp vào lịch.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/availability/manager";
    }

    @PostMapping("/review-all")
    public String reviewAll(@RequestParam boolean approved,
                            @AuthenticationPrincipal AuthenticatedUser user,
                            RedirectAttributes redirectAttributes) {
        try {
            int reviewed = staffAvailabilityService.reviewAllPendingRegistrations(approved, user);
            redirectAttributes.addFlashAttribute("successMessage",
                    approved
                            ? "Đã duyệt tất cả " + reviewed
                            + " đơn đang chờ. Mỗi nhân viên được duyệt theo đơn của chính họ; các ca đã được xếp vào lịch làm việc và nhân viên đã được thông báo."
                            : "Đã từ chối tất cả " + reviewed
                            + " đơn đang chờ. Đơn đã duyệt được giữ nguyên. Nhân viên của các đơn vừa từ chối đã được thông báo.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/availability/manager";
    }
}

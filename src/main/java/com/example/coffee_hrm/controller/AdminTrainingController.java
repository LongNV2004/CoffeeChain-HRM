package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.TrainingService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/training")
@PreAuthorize("hasRole('ADMIN')")
public class AdminTrainingController {

    private final TrainingService trainingService;

    @GetMapping
    public String trainingPage(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        model.addAttribute("displayName", user.getDisplayName());
        model.addAttribute("pendingClasses", trainingService.listPendingClasses());
        model.addAttribute("allClasses", trainingService.listAllClassesForAdmin());
        return "AdminTraining";
    }

    @PostMapping("/classes/{id}/approve")
    public String approve(@PathVariable Integer id,
                          @AuthenticationPrincipal AuthenticatedUser user,
                          RedirectAttributes redirectAttributes) {
        try {
            trainingService.approveClass(id, user);
            redirectAttributes.addFlashAttribute("successMessage", "Đã phê duyệt lớp đào tạo.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/training";
    }

    @PostMapping("/classes/{id}/reject")
    public String reject(@PathVariable Integer id,
                         @AuthenticationPrincipal AuthenticatedUser user,
                         RedirectAttributes redirectAttributes) {
        try {
            trainingService.rejectClass(id, user);
            redirectAttributes.addFlashAttribute("successMessage", "Đã từ chối lớp đào tạo.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/training";
    }
}

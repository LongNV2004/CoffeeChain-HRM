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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/training/staff")
@PreAuthorize("hasRole('STAFF')")
public class StaffTrainingController {

    private final TrainingService trainingService;

    @GetMapping
    public String classListPage(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        model.addAttribute("displayName", user.getDisplayName());
        model.addAttribute("storeName", user.getStoreName());
        try {
            model.addAttribute("classes", trainingService.listClassesForEmployee(user));
        } catch (BusinessException ex) {
            model.addAttribute("classes", List.of());
            model.addAttribute("errorMessage", ex.getMessage());
        }
        return "staff/StaffTraining";
    }

    @GetMapping("/classes/{id}")
    public String classDetailPage(@PathVariable Integer id,
                                  @AuthenticationPrincipal AuthenticatedUser user,
                                  Model model,
                                  RedirectAttributes redirectAttributes) {
        try {
            model.addAttribute("displayName", user.getDisplayName());
            model.addAttribute("storeName", user.getStoreName());
            model.addAttribute("classDetail", trainingService.getClassForEmployee(id, user));
            return "staff/StaffTrainingDetail";
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/training/staff";
        }
    }
}

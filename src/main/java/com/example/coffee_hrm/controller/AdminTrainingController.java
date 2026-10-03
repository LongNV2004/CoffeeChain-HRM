package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.request.CreateCentralizedTrainingRequest;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.TrainingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
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
        return "admin/AdminTraining";
    }

    @GetMapping("/create")
    public String createPage(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        populateCreatePage(user, model);
        if (!model.containsAttribute("classForm")) {
            model.addAttribute("classForm", new CreateCentralizedTrainingRequest());
        }
        return "admin/AdminTrainingCreate";
    }

    @PostMapping("/classes")
    public String createClass(@Valid @ModelAttribute("classForm") CreateCentralizedTrainingRequest classForm,
                              BindingResult bindingResult,
                              @AuthenticationPrincipal AuthenticatedUser user,
                              RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("classForm", classForm);
            redirectAttributes.addFlashAttribute("classError", firstError(bindingResult));
            return "redirect:/admin/training/create";
        }
        try {
            trainingService.createCentralizedClass(classForm, user);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã tạo lớp đào tạo tập trung.");
            return "redirect:/admin/training";
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("classForm", classForm);
            redirectAttributes.addFlashAttribute("classError", ex.getMessage());
            return "redirect:/admin/training/create";
        }
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

    private void populateCreatePage(AuthenticatedUser user, Model model) {
        model.addAttribute("displayName", user.getDisplayName());
        model.addAttribute("stores", trainingService.listActiveStores(user));
        model.addAttribute("trainers", trainingService.listTrainerCandidates(user));
        model.addAttribute("employees", trainingService.listActiveEmployees(user));
        model.addAttribute("activeSkills", trainingService.listActiveSkills());
    }

    private String firstError(BindingResult bindingResult) {
        if (!bindingResult.getAllErrors().isEmpty()
                && bindingResult.getAllErrors().getFirst().getDefaultMessage() != null) {
            return bindingResult.getAllErrors().getFirst().getDefaultMessage();
        }
        return "Không thể tạo lớp đào tạo tập trung.";
    }
}

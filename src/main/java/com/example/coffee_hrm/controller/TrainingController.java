package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.request.CreateTrainingClassRequest;
import com.example.coffee_hrm.dto.request.CreateTrainingSkillRequest;
import com.example.coffee_hrm.dto.request.UpdateTrainingSkillRequest;
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
@RequestMapping("/training")
@PreAuthorize("hasRole('MANAGER')")
public class TrainingController {

    private final TrainingService trainingService;

    @GetMapping
    public String trainingPage(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        populatePage(user, model);
        if (!model.containsAttribute("skillForm")) {
            model.addAttribute("skillForm", new CreateTrainingSkillRequest());
        }
        if (!model.containsAttribute("updateSkillForm")) {
            model.addAttribute("updateSkillForm", new UpdateTrainingSkillRequest());
        }
        if (!model.containsAttribute("classForm")) {
            model.addAttribute("classForm", new CreateTrainingClassRequest());
        }
        return "Training";
    }

    @PostMapping("/skills")
    public String createSkill(@Valid @ModelAttribute("skillForm") CreateTrainingSkillRequest skillForm,
                              BindingResult bindingResult,
                              @AuthenticationPrincipal AuthenticatedUser user,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            populatePage(user, model);
            model.addAttribute("updateSkillForm", new UpdateTrainingSkillRequest());
            model.addAttribute("classForm", new CreateTrainingClassRequest());
            model.addAttribute("skillError", firstError(bindingResult, "Không thể tạo kỹ năng."));
            return "Training";
        }
        try {
            trainingService.createSkill(skillForm, user);
            redirectAttributes.addFlashAttribute("successMessage", "Đã thêm kỹ năng đào tạo.");
            return "redirect:/training";
        } catch (BusinessException ex) {
            populatePage(user, model);
            model.addAttribute("updateSkillForm", new UpdateTrainingSkillRequest());
            model.addAttribute("classForm", new CreateTrainingClassRequest());
            model.addAttribute("skillError", ex.getMessage());
            return "Training";
        }
    }

    @PostMapping("/skills/{id}")
    public String updateSkill(@PathVariable Integer id,
                              @Valid @ModelAttribute("updateSkillForm") UpdateTrainingSkillRequest updateSkillForm,
                              BindingResult bindingResult,
                              @AuthenticationPrincipal AuthenticatedUser user,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            populatePage(user, model);
            model.addAttribute("skillForm", new CreateTrainingSkillRequest());
            model.addAttribute("classForm", new CreateTrainingClassRequest());
            model.addAttribute("skillUpdateError", firstError(bindingResult, "Không thể cập nhật kỹ năng."));
            model.addAttribute("editingSkillId", id);
            return "Training";
        }
        try {
            trainingService.updateSkill(id, updateSkillForm, user);
            redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật kỹ năng đào tạo.");
            return "redirect:/training";
        } catch (BusinessException ex) {
            populatePage(user, model);
            model.addAttribute("skillForm", new CreateTrainingSkillRequest());
            model.addAttribute("classForm", new CreateTrainingClassRequest());
            model.addAttribute("skillUpdateError", ex.getMessage());
            model.addAttribute("editingSkillId", id);
            return "Training";
        }
    }

    @PostMapping("/skills/{id}/deactivate")
    public String deactivateSkill(@PathVariable Integer id,
                                  @AuthenticationPrincipal AuthenticatedUser user,
                                  RedirectAttributes redirectAttributes) {
        try {
            trainingService.deactivateSkill(id, user);
            redirectAttributes.addFlashAttribute("successMessage", "Đã ngừng sử dụng kỹ năng đào tạo.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/training";
    }

    @PostMapping("/classes")
    public String createClass(@Valid @ModelAttribute("classForm") CreateTrainingClassRequest classForm,
                              BindingResult bindingResult,
                              @AuthenticationPrincipal AuthenticatedUser user,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            populatePage(user, model);
            model.addAttribute("skillForm", new CreateTrainingSkillRequest());
            model.addAttribute("updateSkillForm", new UpdateTrainingSkillRequest());
            model.addAttribute("classError", firstError(bindingResult, "Không thể tạo lớp đào tạo."));
            return "Training";
        }
        try {
            trainingService.createClass(classForm, user);
            redirectAttributes.addFlashAttribute("successMessage", "Đã gửi lớp đào tạo cho Admin duyệt.");
            return "redirect:/training";
        } catch (BusinessException ex) {
            populatePage(user, model);
            model.addAttribute("skillForm", new CreateTrainingSkillRequest());
            model.addAttribute("updateSkillForm", new UpdateTrainingSkillRequest());
            model.addAttribute("classError", ex.getMessage());
            return "Training";
        }
    }

    private void populatePage(AuthenticatedUser user, Model model) {
        model.addAttribute("displayName", user.getDisplayName());
        model.addAttribute("storeName", user.getStoreName());
        model.addAttribute("skills", trainingService.listSkills());
        model.addAttribute("activeSkills", trainingService.listActiveSkills());
        model.addAttribute("classes", trainingService.listClassesForManager(user));
        model.addAttribute("hasStore", trainingService.managerHasAssignedStore(user));
    }

    private String firstError(BindingResult bindingResult, String fallback) {
        if (!bindingResult.getAllErrors().isEmpty() && bindingResult.getAllErrors().getFirst().getDefaultMessage() != null) {
            return bindingResult.getAllErrors().getFirst().getDefaultMessage();
        }
        return fallback;
    }
}

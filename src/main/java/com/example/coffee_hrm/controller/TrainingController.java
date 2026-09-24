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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

@Controller
@RequiredArgsConstructor
@RequestMapping("/training")
@PreAuthorize("hasRole('MANAGER')")
public class TrainingController {

    private final TrainingService trainingService;

    @GetMapping
    public String classListPage(@AuthenticationPrincipal AuthenticatedUser user,
                                @RequestParam(required = false) Integer skillId,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                @RequestParam(required = false) String keyword,
                                @RequestParam(required = false, defaultValue = "asc") String sortDir,
                                @RequestParam(required = false, defaultValue = "1") int page,
                                Model model) {
        populateListPage(user, model, skillId, date, keyword, sortDir, page);
        return "manager/TrainingClasses";
    }

    @GetMapping("/classes/{id}")
    public String classDetailPage(@PathVariable Integer id,
                                  @AuthenticationPrincipal AuthenticatedUser user,
                                  Model model,
                                  RedirectAttributes redirectAttributes) {
        try {
            model.addAttribute("displayName", user.getDisplayName());
            model.addAttribute("storeName", user.getStoreName());
            model.addAttribute("classDetail", trainingService.getApprovedClassDetailForManager(id, user));
            return "manager/TrainingClassDetail";
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/training";
        }
    }

    @GetMapping("/create")
    public String createClassPage(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        populateCreatePage(user, model);
        if (!model.containsAttribute("skillForm")) {
            model.addAttribute("skillForm", new CreateTrainingSkillRequest());
        }
        if (!model.containsAttribute("updateSkillForm")) {
            model.addAttribute("updateSkillForm", new UpdateTrainingSkillRequest());
        }
        if (!model.containsAttribute("classForm")) {
            model.addAttribute("classForm", new CreateTrainingClassRequest());
        }
        return "manager/Training";
    }

    @PostMapping("/skills")
    public String createSkill(@Valid @ModelAttribute("skillForm") CreateTrainingSkillRequest skillForm,
                              BindingResult bindingResult,
                              @AuthenticationPrincipal AuthenticatedUser user,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            populateCreatePage(user, model);
            model.addAttribute("updateSkillForm", new UpdateTrainingSkillRequest());
            model.addAttribute("classForm", new CreateTrainingClassRequest());
            model.addAttribute("skillError", firstError(bindingResult, "Không thể tạo kỹ năng."));
            return "manager/Training";
        }
        try {
            trainingService.createSkill(skillForm, user);
            redirectAttributes.addFlashAttribute("successMessage", "Đã thêm kỹ năng đào tạo.");
            return "redirect:/training/create";
        } catch (BusinessException ex) {
            populateCreatePage(user, model);
            model.addAttribute("updateSkillForm", new UpdateTrainingSkillRequest());
            model.addAttribute("classForm", new CreateTrainingClassRequest());
            model.addAttribute("skillError", ex.getMessage());
            return "manager/Training";
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
            populateCreatePage(user, model);
            model.addAttribute("skillForm", new CreateTrainingSkillRequest());
            model.addAttribute("classForm", new CreateTrainingClassRequest());
            model.addAttribute("skillUpdateError", firstError(bindingResult, "Không thể cập nhật kỹ năng."));
            model.addAttribute("editingSkillId", id);
            return "manager/Training";
        }
        try {
            trainingService.updateSkill(id, updateSkillForm, user);
            redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật kỹ năng đào tạo.");
            return "redirect:/training/create";
        } catch (BusinessException ex) {
            populateCreatePage(user, model);
            model.addAttribute("skillForm", new CreateTrainingSkillRequest());
            model.addAttribute("classForm", new CreateTrainingClassRequest());
            model.addAttribute("skillUpdateError", ex.getMessage());
            model.addAttribute("editingSkillId", id);
            return "manager/Training";
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
        return "redirect:/training/create";
    }

    @PostMapping("/skills/{id}/activate")
    public String activateSkill(@PathVariable Integer id,
                                @AuthenticationPrincipal AuthenticatedUser user,
                                RedirectAttributes redirectAttributes) {
        try {
            trainingService.activateSkill(id, user);
            redirectAttributes.addFlashAttribute("successMessage", "Đã kích hoạt lại kỹ năng đào tạo.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/training/create";
    }

    @PostMapping("/classes/{id}/delete")
    public String deleteClass(@PathVariable Integer id,
                              @AuthenticationPrincipal AuthenticatedUser user,
                              RedirectAttributes redirectAttributes) {
        try {
            trainingService.deleteClassForManager(id, user);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa lớp đào tạo.");
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
            populateCreatePage(user, model);
            model.addAttribute("skillForm", new CreateTrainingSkillRequest());
            model.addAttribute("updateSkillForm", new UpdateTrainingSkillRequest());
            model.addAttribute("classError", firstError(bindingResult, "Không thể tạo lớp đào tạo."));
            return "manager/Training";
        }
        try {
            trainingService.createClass(classForm, user);
            redirectAttributes.addFlashAttribute("successMessage", "Đã gửi lớp đào tạo cho Admin duyệt.");
            return "redirect:/training";
        } catch (BusinessException ex) {
            populateCreatePage(user, model);
            model.addAttribute("skillForm", new CreateTrainingSkillRequest());
            model.addAttribute("updateSkillForm", new UpdateTrainingSkillRequest());
            model.addAttribute("classError", ex.getMessage());
            return "manager/Training";
        }
    }

    private void populateListPage(AuthenticatedUser user,
                                  Model model,
                                  Integer skillId,
                                  LocalDate date,
                                  String keyword,
                                  String sortDir,
                                  int page) {
        var classPage = trainingService.listClassesForManager(user, skillId, date, keyword, sortDir, page);
        String normalizedSortDir = "desc".equalsIgnoreCase(sortDir) ? "desc" : "asc";
        model.addAttribute("displayName", user.getDisplayName());
        model.addAttribute("storeName", user.getStoreName());
        model.addAttribute("classes", classPage.getContent());
        model.addAttribute("classPage", classPage);
        model.addAttribute("hasStore", trainingService.managerHasAssignedStore(user));
        model.addAttribute("filterSkills", trainingService.listActiveSkills());
        model.addAttribute("skillId", skillId);
        model.addAttribute("date", date);
        model.addAttribute("keyword", keyword);
        model.addAttribute("sortDir", normalizedSortDir);
        model.addAttribute("currentPage", classPage.getNumber() + 1);
        model.addAttribute("totalPages", classPage.getTotalPages());
    }

    private void populateCreatePage(AuthenticatedUser user, Model model) {
        model.addAttribute("displayName", user.getDisplayName());
        model.addAttribute("storeName", user.getStoreName());
        model.addAttribute("skills", trainingService.listSkills());
        model.addAttribute("activeSkills", trainingService.listActiveSkills());
        model.addAttribute("hasStore", trainingService.managerHasAssignedStore(user));
    }

    private String firstError(BindingResult bindingResult, String fallback) {
        if (!bindingResult.getAllErrors().isEmpty() && bindingResult.getAllErrors().getFirst().getDefaultMessage() != null) {
            return bindingResult.getAllErrors().getFirst().getDefaultMessage();
        }
        return fallback;
    }
}

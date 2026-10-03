package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.request.AddTrainingClassStudentsRequest;
import com.example.coffee_hrm.dto.request.CreateTrainingClassRequest;
import com.example.coffee_hrm.dto.request.CreateTrainingSkillRequest;
import com.example.coffee_hrm.dto.request.EvaluateTrainingStudentRequest;
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

    @GetMapping("/classes")
    public String classListShortcut() {
        return "redirect:/training";
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
            model.addAttribute("availableEmployees", trainingService.listAvailableEmployeesForClass(id, user));
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
                              RedirectAttributes redirectAttributes,
                              @AuthenticationPrincipal AuthenticatedUser user) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("skillForm", skillForm);
            redirectAttributes.addFlashAttribute("skillError", firstError(bindingResult, "Không thể tạo kỹ năng."));
            return "redirect:/training/create";
        }
        try {
            trainingService.createSkill(skillForm, user);
            redirectAttributes.addFlashAttribute("successMessage", "Đã thêm kỹ năng đào tạo.");
            return "redirect:/training/create";
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("skillForm", skillForm);
            redirectAttributes.addFlashAttribute("skillError", ex.getMessage());
            return "redirect:/training/create";
        }
    }

    @PostMapping("/skills/{id}")
    public String updateSkill(@PathVariable Integer id,
                              @Valid @ModelAttribute("updateSkillForm") UpdateTrainingSkillRequest updateSkillForm,
                              BindingResult bindingResult,
                              RedirectAttributes redirectAttributes,
                              @AuthenticationPrincipal AuthenticatedUser user) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("updateSkillForm", updateSkillForm);
            redirectAttributes.addFlashAttribute("skillUpdateError", firstError(bindingResult, "Không thể cập nhật kỹ năng."));
            redirectAttributes.addFlashAttribute("editingSkillId", id);
            return "redirect:/training/create";
        }
        try {
            trainingService.updateSkill(id, updateSkillForm, user);
            redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật kỹ năng đào tạo.");
            return "redirect:/training/create";
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("updateSkillForm", updateSkillForm);
            redirectAttributes.addFlashAttribute("skillUpdateError", ex.getMessage());
            redirectAttributes.addFlashAttribute("editingSkillId", id);
            return "redirect:/training/create";
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

    @PostMapping("/classes/{id}/students")
    public String addStudents(@PathVariable Integer id,
                              @Valid @ModelAttribute("addStudentsForm") AddTrainingClassStudentsRequest addStudentsForm,
                              BindingResult bindingResult,
                              @AuthenticationPrincipal AuthenticatedUser user,
                              RedirectAttributes redirectAttributes) {
        String redirect = "redirect:/training/classes/" + id;
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    validationMessage(bindingResult, "Vui lòng chọn ít nhất một nhân viên."));
            return redirect;
        }
        try {
            int added = trainingService.addStudentsToClass(id, addStudentsForm, user);
            redirectAttributes.addFlashAttribute("successMessage",
                    added == 1 ? "Đã thêm nhân viên vào lớp đào tạo." : "Đã thêm " + added + " nhân viên vào lớp đào tạo.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return redirect;
    }

    @PostMapping("/classes/{id}/students/{employeeId}/evaluation")
    public String evaluateStudent(@PathVariable Integer id,
                                  @PathVariable Integer employeeId,
                                  @Valid @ModelAttribute("evaluationForm") EvaluateTrainingStudentRequest evaluationForm,
                                  BindingResult bindingResult,
                                  @AuthenticationPrincipal AuthenticatedUser user,
                                  RedirectAttributes redirectAttributes) {
        String redirect = "redirect:/training/classes/" + id;
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    validationMessage(bindingResult, "Không thể lưu kết quả đánh giá."));
            return redirect;
        }
        try {
            trainingService.evaluateStudent(id, employeeId, evaluationForm, user);
            redirectAttributes.addFlashAttribute("successMessage",
                    evaluationForm.isUpdating() ? "Đã cập nhật kết quả đánh giá." : "Đã lưu kết quả đánh giá.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return redirect;
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
                              RedirectAttributes redirectAttributes,
                              @AuthenticationPrincipal AuthenticatedUser user) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("classForm", classForm);
            redirectAttributes.addFlashAttribute("classError", firstError(bindingResult, "Không thể tạo lớp đào tạo."));
            return "redirect:/training/create";
        }
        try {
            trainingService.createClass(classForm, user);
            redirectAttributes.addFlashAttribute("successMessage", "Đã gửi lớp đào tạo cho Admin duyệt.");
            return "redirect:/training";
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("classForm", classForm);
            redirectAttributes.addFlashAttribute("classError", ex.getMessage());
            return "redirect:/training/create";
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
        model.addAttribute("submittedClasses", trainingService.listSubmittedClassesForManager(user));
        model.addAttribute("endedClasses", trainingService.listEndedClassesForManager(user));
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
        model.addAttribute("storeEmployees", trainingService.listEmployeesForNewStoreClass(user));
    }

    private String validationMessage(BindingResult bindingResult, String fallback) {
        if (bindingResult.hasFieldErrors("employeeIds")) {
            return "Vui lòng chọn ít nhất một nhân viên.";
        }
        if (bindingResult.hasFieldErrors("result")) {
            return "Vui lòng chọn kết quả đánh giá.";
        }
        if (bindingResult.hasFieldErrors("note")) {
            return "Ghi chú tối đa 500 ký tự.";
        }
        return firstError(bindingResult, fallback);
    }

    private String firstError(BindingResult bindingResult, String fallback) {
        if (!bindingResult.getAllErrors().isEmpty() && bindingResult.getAllErrors().getFirst().getDefaultMessage() != null) {
            return bindingResult.getAllErrors().getFirst().getDefaultMessage();
        }
        return fallback;
    }
}

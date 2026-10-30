package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.enums.CertificateKind;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.request.CreateCentralizedTrainingRequest;
import com.example.coffee_hrm.dto.request.CreateTrainingSkillRequest;
import com.example.coffee_hrm.dto.request.SkillFilePayload;
import com.example.coffee_hrm.dto.request.UpdateTrainingSkillRequest;
import com.example.coffee_hrm.dto.response.SkillDocumentDownload;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.TrainingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/training")
@PreAuthorize("hasRole('ADMIN')")
public class AdminTrainingController {

    private final TrainingService trainingService;

    @GetMapping
    public String trainingPage(@AuthenticationPrincipal AuthenticatedUser user,
                               @RequestParam(required = false) Integer skillId,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                               @RequestParam(required = false) String keyword,
                               @RequestParam(required = false, defaultValue = "asc") String sortDir,
                               @RequestParam(required = false, defaultValue = "1") int page,
                               Model model) {
        var classPage = trainingService.listActiveClassesForAdmin(user, skillId, date, keyword, sortDir, page);
        String normalizedSortDir = "desc".equalsIgnoreCase(sortDir) ? "desc" : "asc";
        model.addAttribute("displayName", user.getDisplayName());
        model.addAttribute("currentUserId", user.getUserId());
        model.addAttribute("endedClasses", trainingService.listEndedClassesForAdmin(user));
        model.addAttribute("classes", classPage.getContent());
        model.addAttribute("classPage", classPage);
        model.addAttribute("filterSkills", trainingService.listActiveSkills());
        model.addAttribute("skillId", skillId);
        model.addAttribute("date", date);
        model.addAttribute("keyword", keyword);
        model.addAttribute("sortDir", normalizedSortDir);
        model.addAttribute("currentPage", classPage.getNumber() + 1);
        model.addAttribute("totalPages", classPage.getTotalPages());
        return "admin/AdminTraining";
    }

    @GetMapping("/classes/{id}")
    public String classDetailPage(@PathVariable Integer id,
                                  @AuthenticationPrincipal AuthenticatedUser user,
                                  Model model,
                                  RedirectAttributes redirectAttributes) {
        try {
            model.addAttribute("displayName", user.getDisplayName());
            model.addAttribute("viewerEmployeeId", user.getEmployeeId());
            model.addAttribute("classDetail", trainingService.getApprovedClassDetailForAdmin(id, user));
            return "admin/AdminTrainingClassDetail";
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/admin/training";
        }
    }

    @GetMapping("/skills")
    public String skillPage(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        model.addAttribute("displayName", user.getDisplayName());
        model.addAttribute("skills", trainingService.listSkills(CertificateKind.STAFF));
        if (!model.containsAttribute("skillForm")) {
            model.addAttribute("skillForm", new CreateTrainingSkillRequest());
        }
        if (!model.containsAttribute("updateSkillForm")) {
            model.addAttribute("updateSkillForm", new UpdateTrainingSkillRequest());
        }
        return "admin/AdminTrainingSkill";
    }

    @PostMapping("/skills")
    public String createSkill(@Valid @ModelAttribute("skillForm") CreateTrainingSkillRequest skillForm,
                              BindingResult bindingResult,
                              @RequestParam(value = "documents", required = false) List<MultipartFile> documents,
                              @AuthenticationPrincipal AuthenticatedUser user,
                              RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("skillForm", skillForm);
            redirectAttributes.addFlashAttribute("skillError", firstError(bindingResult, "Không thể tạo kỹ năng."));
            return skillRedirect(skillForm.getCertificateKind());
        }
        try {
            trainingService.createSkill(skillForm, user, filesOf(documents));
            redirectAttributes.addFlashAttribute("successMessage", "Đã thêm kỹ năng đào tạo.");
            return skillRedirect(skillForm.getCertificateKind());
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("skillForm", skillForm);
            redirectAttributes.addFlashAttribute("skillError", ex.getMessage());
            return skillRedirect(skillForm.getCertificateKind());
        } catch (IOException ex) {
            redirectAttributes.addFlashAttribute("skillForm", skillForm);
            redirectAttributes.addFlashAttribute("skillError", "Không đọc được tài liệu đính kèm.");
            return skillRedirect(skillForm.getCertificateKind());
        }
    }

    @PostMapping("/skills/{id}")
    public String updateSkill(@PathVariable Integer id,
                              @Valid @ModelAttribute("updateSkillForm") UpdateTrainingSkillRequest updateSkillForm,
                              BindingResult bindingResult,
                              @RequestParam(required = false) CertificateKind certificateKind,
                              @RequestParam(value = "documents", required = false) List<MultipartFile> documents,
                              @AuthenticationPrincipal AuthenticatedUser user,
                              RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("updateSkillForm", updateSkillForm);
            redirectAttributes.addFlashAttribute("skillUpdateError", firstError(bindingResult, "Không thể cập nhật kỹ năng."));
            redirectAttributes.addFlashAttribute("editingSkillId", id);
            return skillRedirect(certificateKind);
        }
        try {
            var updated = trainingService.updateSkill(id, updateSkillForm, user, filesOf(documents));
            redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật kỹ năng đào tạo.");
            return skillRedirect(updated.getCertificateKind());
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("updateSkillForm", updateSkillForm);
            redirectAttributes.addFlashAttribute("skillUpdateError", ex.getMessage());
            redirectAttributes.addFlashAttribute("editingSkillId", id);
            return skillRedirect(certificateKind);
        } catch (IOException ex) {
            redirectAttributes.addFlashAttribute("updateSkillForm", updateSkillForm);
            redirectAttributes.addFlashAttribute("skillUpdateError", "Không đọc được tài liệu đính kèm.");
            redirectAttributes.addFlashAttribute("editingSkillId", id);
            return skillRedirect(certificateKind);
        }
    }

    @GetMapping("/skills/documents/{id}")
    public ResponseEntity<byte[]> downloadSkillDocument(@PathVariable Integer id,
                                                        @AuthenticationPrincipal AuthenticatedUser user) {
        return attachment(trainingService.downloadSkillDocument(id, user));
    }

    @PostMapping("/skills/documents/{id}/delete")
    public String deleteSkillDocument(@PathVariable Integer id,
                                      @AuthenticationPrincipal AuthenticatedUser user,
                                      RedirectAttributes redirectAttributes) {
        try {
            Integer skillId = trainingService.skillIdOfDocument(id);
            CertificateKind kind = trainingService.removeSkillDocument(id, user);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa tài liệu kỹ năng.");
            redirectAttributes.addFlashAttribute("editingSkillId", skillId);
            return skillRedirect(kind);
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/admin/training/skills";
        }
    }

    @GetMapping("/classes/{id}/documents/{documentId}")
    public ResponseEntity<byte[]> downloadClassMaterial(@PathVariable Integer id,
                                                        @PathVariable Integer documentId,
                                                        @AuthenticationPrincipal AuthenticatedUser user) {
        return attachment(trainingService.downloadClassMaterialForAdmin(id, documentId, user));
    }

    @PostMapping("/skills/{id}/deactivate")
    public String deactivateSkill(@PathVariable Integer id,
                                  @AuthenticationPrincipal AuthenticatedUser user,
                                  RedirectAttributes redirectAttributes) {
        try {
            var updated = trainingService.deactivateSkill(id, user);
            redirectAttributes.addFlashAttribute("successMessage", "Đã ngừng sử dụng kỹ năng đào tạo.");
            return skillRedirect(updated.getCertificateKind());
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/admin/training/skills";
        }
    }

    @PostMapping("/skills/{id}/activate")
    public String activateSkill(@PathVariable Integer id,
                                @AuthenticationPrincipal AuthenticatedUser user,
                                RedirectAttributes redirectAttributes) {
        try {
            var updated = trainingService.activateSkill(id, user);
            redirectAttributes.addFlashAttribute("successMessage", "Đã kích hoạt lại kỹ năng đào tạo.");
            return skillRedirect(updated.getCertificateKind());
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/admin/training/skills";
        }
    }

    @GetMapping("/my")
    public String ownClasses() {
        return "redirect:/admin/training";
    }

    @GetMapping("/my/classes/{id}")
    public String ownClassDetail(@PathVariable Integer id) {
        return "redirect:/admin/training/classes/" + id;
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
                    "Đã tạo lớp đào tạo.");
            return "redirect:/admin/training";
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("classForm", classForm);
            redirectAttributes.addFlashAttribute("classError", ex.getMessage());
            return "redirect:/admin/training/create";
        }
    }

    @PostMapping("/classes/{id}/evaluate")
    public String evaluateManagementClass(@PathVariable Integer id,
                                          @RequestParam Integer employeeId,
                                          @Valid @ModelAttribute("evaluationForm") com.example.coffee_hrm.dto.request.EvaluateTrainingStudentRequest evaluationForm,
                                          BindingResult bindingResult,
                                          @AuthenticationPrincipal AuthenticatedUser user,
                                          RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Vui lòng chọn kết quả đánh giá.");
            return "redirect:/admin/training/classes/" + id;
        }
        try {
            trainingService.evaluateManagementStudent(id, employeeId, evaluationForm, user);
            redirectAttributes.addFlashAttribute("successMessage",
                    evaluationForm.isUpdating() ? "Đã cập nhật kết quả đánh giá." : "Đã lưu kết quả đánh giá.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/training/classes/" + id;
    }

    private List<SkillFilePayload> filesOf(List<MultipartFile> documents) throws IOException {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        List<SkillFilePayload> payloads = new ArrayList<>();
        for (MultipartFile file : documents) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            payloads.add(new SkillFilePayload(file.getOriginalFilename(), file.getBytes()));
        }
        return payloads;
    }

    private ResponseEntity<byte[]> attachment(SkillDocumentDownload file) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.fileName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.content());
    }

    private String skillRedirect(CertificateKind certificateKind) {
        return certificateKind == CertificateKind.MANAGEMENT
                ? "redirect:/admin/training/management"
                : "redirect:/admin/training/skills";
    }

    private void populateCreatePage(AuthenticatedUser user, Model model) {
        model.addAttribute("displayName", user.getDisplayName());
        model.addAttribute("stores", trainingService.listActiveStores(user));
        model.addAttribute("trainers", trainingService.listTrainerCandidates(user));
        model.addAttribute("employees", trainingService.listClassCandidates(user));
    }

    private String firstError(BindingResult bindingResult) {
        return firstError(bindingResult, "Không thể tạo lớp đào tạo.");
    }

    private String firstError(BindingResult bindingResult, String fallback) {
        if (!bindingResult.getAllErrors().isEmpty()
                && bindingResult.getAllErrors().getFirst().getDefaultMessage() != null) {
            return bindingResult.getAllErrors().getFirst().getDefaultMessage();
        }
        return fallback;
    }
}

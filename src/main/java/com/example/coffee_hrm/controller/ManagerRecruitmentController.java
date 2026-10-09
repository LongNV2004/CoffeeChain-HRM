package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.enums.RecruitmentProposalStatus;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.dto.request.CreateRecruitmentRequest;
import com.example.coffee_hrm.dto.request.RecruitmentCandidateRequest;
import com.example.coffee_hrm.dto.response.RecruitmentRequestResponse;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.RecruitmentRequestService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@RequestMapping("/manager/recruitment")
@PreAuthorize("hasRole('MANAGER')")
public class ManagerRecruitmentController {

    private final RecruitmentRequestService recruitmentRequestService;

    @GetMapping
    public String list(@AuthenticationPrincipal AuthenticatedUser user,
                       @RequestParam(required = false) RecruitmentProposalStatus status,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        try {
            List<RecruitmentRequestResponse> all = recruitmentRequestService.listMine(user);
            model.addAttribute("requests", status == null
                    ? all
                    : all.stream().filter(item -> item.getStatus() == status).toList());
            model.addAttribute("statuses", RecruitmentProposalStatus.values());
            model.addAttribute("selectedStatus", status);
            model.addAttribute("pendingCount", countStatus(all, RecruitmentProposalStatus.PENDING));
            model.addAttribute("partialCount", countStatus(all, RecruitmentProposalStatus.PARTIALLY_PROCESSED));
            model.addAttribute("completedCount", countStatus(all, RecruitmentProposalStatus.COMPLETED));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/dashboard/manager";
        }
        return "manager/recruitment-list";
    }

    @GetMapping("/create")
    public String createPage(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new CreateRecruitmentRequest());
        }
        prepareCreatePage(user, model);
        return "manager/recruitment-create";
    }

    @PostMapping("/create")
    public String create(@Valid @ModelAttribute("form") CreateRecruitmentRequest form,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal AuthenticatedUser user,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            prepareCreatePage(user, model);
            return "manager/recruitment-create";
        }
        try {
            recruitmentRequestService.create(user, form);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã gửi đề xuất cùng danh sách nhân viên. Admin sẽ xem xét từng người.");
            return "redirect:/manager/recruitment";
        } catch (BusinessException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            prepareCreatePage(user, model);
            return "manager/recruitment-create";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Integer id,
                         @AuthenticationPrincipal AuthenticatedUser user,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        try {
            model.addAttribute("request", recruitmentRequestService.getMine(user, id));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/manager/recruitment";
        }
        return "manager/recruitment-detail";
    }

    private long countStatus(List<RecruitmentRequestResponse> requests, RecruitmentProposalStatus status) {
        return requests.stream().filter(item -> item.getStatus() == status).count();
    }

    private void prepareCreatePage(AuthenticatedUser user, Model model) {
        model.addAttribute("maxBirthDate", VietnamTime.today().minusDays(1));
        model.addAttribute("maxBirthDateIso", VietnamTime.today().minusDays(1).toString());
        Object form = model.getAttribute("form");
        List<RecruitmentCandidateRequest> candidates = form instanceof CreateRecruitmentRequest request
                && request.getCandidates() != null
                ? request.getCandidates()
                : List.of();
        model.addAttribute("candidatePayload", candidates.stream().map(this::toPayload).toList());
        try {
            model.addAttribute("storeName", recruitmentRequestService.managedStoreLabel(user));
        } catch (BusinessException ex) {
            model.addAttribute("storeName", null);
            if (!model.containsAttribute("errorMessage")) {
                model.addAttribute("errorMessage", ex.getMessage());
            }
        }
    }

    private Map<String, String> toPayload(RecruitmentCandidateRequest candidate) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("fullName", candidate.getFullName() == null ? "" : candidate.getFullName());
        row.put("dateOfBirth", candidate.getDateOfBirth() == null ? "" : candidate.getDateOfBirth().toString());
        row.put("gender", candidate.getGender() == null ? "" : candidate.getGender().name());
        row.put("phone", candidate.getPhone() == null ? "" : candidate.getPhone());
        row.put("email", candidate.getEmail() == null ? "" : candidate.getEmail());
        row.put("address", candidate.getAddress() == null ? "" : candidate.getAddress());
        return row;
    }
}

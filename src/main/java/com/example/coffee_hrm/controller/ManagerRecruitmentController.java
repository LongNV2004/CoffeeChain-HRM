package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.request.CreateRecruitmentRequest;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@RequestMapping("/manager/recruitment")
@PreAuthorize("hasRole('MANAGER')")
public class ManagerRecruitmentController {

    private final RecruitmentRequestService recruitmentRequestService;

    @GetMapping
    public String list(@AuthenticationPrincipal AuthenticatedUser user,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        try {
            model.addAttribute("requests", recruitmentRequestService.listMine(user));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/dashboard/manager";
        }
        return "manager/recruitment-list";
    }

    @GetMapping("/create")
    public String createPage(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        prepareCreatePage(user, model);
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new CreateRecruitmentRequest());
        }
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
                    "Đã gửi đề xuất tuyển nhân sự. Admin sẽ xem xét yêu cầu.");
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

    private void prepareCreatePage(AuthenticatedUser user, Model model) {
        try {
            model.addAttribute("storeName", recruitmentRequestService.managedStoreLabel(user));
        } catch (BusinessException ex) {
            model.addAttribute("storeName", null);
            if (!model.containsAttribute("errorMessage")) {
                model.addAttribute("errorMessage", ex.getMessage());
            }
        }
    }
}

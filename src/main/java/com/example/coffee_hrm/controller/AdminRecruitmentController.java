package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.enums.RecruitmentStatus;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.RecruitmentRequestService;
import com.example.coffee_hrm.service.StoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/recruitment")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRecruitmentController {

    private final RecruitmentRequestService recruitmentRequestService;
    private final StoreService storeService;

    @GetMapping
    public String list(@RequestParam(required = false) Integer storeId,
                       @RequestParam(required = false) Integer managerId,
                       @RequestParam(required = false) RecruitmentStatus status,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
                       @AuthenticationPrincipal AuthenticatedUser user,
                       Model model) {
        try {
            model.addAttribute("requests", recruitmentRequestService.listForAdmin(
                    user, storeId, managerId, status, createdFrom, createdTo));
        } catch (BusinessException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("requests", List.of());
        }
        model.addAttribute("stores", storeService.getStores(user));
        model.addAttribute("managers", recruitmentRequestService.listManagerOptions());
        model.addAttribute("statuses", RecruitmentStatus.values());
        model.addAttribute("storeId", storeId);
        model.addAttribute("managerId", managerId);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("createdFrom", createdFrom);
        model.addAttribute("createdTo", createdTo);
        return "admin/recruitment-list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Integer id,
                         @AuthenticationPrincipal AuthenticatedUser user,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        try {
            model.addAttribute("request", recruitmentRequestService.getForAdmin(user, id));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/admin/recruitment";
        }
        return "admin/recruitment-detail";
    }

    @PostMapping("/{id}/approve")
    public String approve(@PathVariable Integer id,
                          @AuthenticationPrincipal AuthenticatedUser user,
                          RedirectAttributes redirectAttributes) {
        try {
            recruitmentRequestService.approve(user, id);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã duyệt đề xuất, tạo tài khoản Staff và gửi mật khẩu tạm thời qua email.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/recruitment/" + id;
    }

    @PostMapping("/{id}/reject")
    public String reject(@PathVariable Integer id,
                         @RequestParam(required = false) String rejectReason,
                         @AuthenticationPrincipal AuthenticatedUser user,
                         RedirectAttributes redirectAttributes) {
        try {
            recruitmentRequestService.reject(user, id, rejectReason);
            redirectAttributes.addFlashAttribute("successMessage", "Đã từ chối đề xuất tuyển nhân sự.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/recruitment/" + id;
    }
}

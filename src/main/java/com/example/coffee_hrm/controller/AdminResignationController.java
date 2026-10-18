package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.enums.ResignationProposalStatus;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.response.ResignationDecisionResult;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.ResignationProposalService;
import com.example.coffee_hrm.service.StoreService;
import lombok.RequiredArgsConstructor;
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

import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/resignation")
@PreAuthorize("hasRole('ADMIN')")
public class AdminResignationController {

    private final ResignationProposalService resignationProposalService;
    private final StoreService storeService;

    @GetMapping
    public String list(@RequestParam(required = false) Integer storeId,
                       @RequestParam(required = false) ResignationProposalStatus status,
                       @RequestParam(required = false) String keyword,
                       @AuthenticationPrincipal AuthenticatedUser user,
                       Model model) {
        try {
            model.addAttribute("requests", resignationProposalService.listForAdmin(user, storeId, status, keyword));
        } catch (BusinessException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("requests", List.of());
        }
        model.addAttribute("stores", storeService.getStores(user));
        model.addAttribute("statuses", ResignationProposalStatus.values());
        model.addAttribute("storeId", storeId);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("keyword", keyword);
        return "admin/resignation-list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Integer id,
                         @AuthenticationPrincipal AuthenticatedUser user,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        try {
            model.addAttribute("request", resignationProposalService.getForAdmin(user, id));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/admin/resignation";
        }
        return "admin/resignation-detail";
    }

    @PostMapping("/{id}/items/{itemId}/approve")
    public String approve(@PathVariable Integer id,
                          @PathVariable Integer itemId,
                          @AuthenticationPrincipal AuthenticatedUser user,
                          RedirectAttributes redirectAttributes) {
        try {
            ResignationDecisionResult result = resignationProposalService.approveItem(user, id, itemId);
            redirectAttributes.addFlashAttribute("successMessage", approveMessage(result));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/resignation/" + id;
    }

    @PostMapping("/{id}/items/{itemId}/reject")
    public String reject(@PathVariable Integer id,
                         @PathVariable Integer itemId,
                         @RequestParam(required = false) String rejectReason,
                         @AuthenticationPrincipal AuthenticatedUser user,
                         RedirectAttributes redirectAttributes) {
        try {
            ResignationDecisionResult result = resignationProposalService.rejectItem(user, id, itemId, rejectReason);
            redirectAttributes.addFlashAttribute("successMessage", rejectMessage(result));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/resignation/" + id;
    }

    @PostMapping("/{id}/approve-pending")
    public String approvePending(@PathVariable Integer id,
                                 @AuthenticationPrincipal AuthenticatedUser user,
                                 RedirectAttributes redirectAttributes) {
        try {
            ResignationDecisionResult result = resignationProposalService.approvePending(user, id);
            redirectAttributes.addFlashAttribute("successMessage", bulkApproveMessage(result));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/resignation/" + id;
    }

    @PostMapping("/{id}/reject-pending")
    public String rejectPending(@PathVariable Integer id,
                                @RequestParam(required = false) String rejectReason,
                                @AuthenticationPrincipal AuthenticatedUser user,
                                RedirectAttributes redirectAttributes) {
        try {
            ResignationDecisionResult result = resignationProposalService.rejectPending(user, id, rejectReason);
            redirectAttributes.addFlashAttribute("successMessage", bulkRejectMessage(result));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/resignation/" + id;
    }

    @PostMapping("/{id}/items/{itemId}/resend-notice")
    public String resendNotice(@PathVariable Integer id,
                               @PathVariable Integer itemId,
                               @AuthenticationPrincipal AuthenticatedUser user,
                               RedirectAttributes redirectAttributes) {
        try {
            ResignationDecisionResult result = resignationProposalService.resendDecisionNotice(user, id, itemId);
            if (result.isEmailSent()) {
                redirectAttributes.addFlashAttribute("successMessage",
                        "Đã gửi lại email thông báo nghỉ việc cho " + result.getEmployeeName() + ".");
            } else {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Chưa gửi được email thông báo nghỉ việc cho " + result.getEmployeeName()
                                + ". Trạng thái nghỉ việc vẫn được giữ nguyên.");
            }
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/resignation/" + id;
    }

    private String approveMessage(ResignationDecisionResult result) {
        String email = result.isEmailSent()
                ? " Đã gửi email thông báo nghỉ việc cho nhân viên."
                : " Trạng thái nghỉ việc đã được lưu, nhưng chưa gửi được email. Có thể gửi lại ở dòng nhân sự này.";
        return "Đã duyệt " + result.getEmployeeName()
                + ". Nhân sự đã nghỉ việc và tài khoản đăng nhập đã bị vô hiệu hóa." + email;
    }

    private String rejectMessage(ResignationDecisionResult result) {
        return "Đã từ chối " + result.getEmployeeName() + ". Nhân viên vẫn làm việc và tài khoản vẫn hoạt động.";
    }

    private String bulkApproveMessage(ResignationDecisionResult result) {
        String email = result.isEmailSent()
                ? " Đã gửi email thông báo nghỉ việc cho các nhân viên được duyệt."
                : " Trạng thái nghỉ việc đã được lưu, nhưng chưa gửi đủ email. Có thể gửi lại ở từng dòng chưa nhận email.";
        return "Đã duyệt " + result.getProcessedCount()
                + " nhân sự đang chờ. Những người này đã nghỉ việc và tài khoản đăng nhập đã bị vô hiệu hóa." + email;
    }

    private String bulkRejectMessage(ResignationDecisionResult result) {
        return "Đã từ chối " + result.getProcessedCount()
                + " nhân sự đang chờ. Họ vẫn làm việc và tài khoản vẫn hoạt động.";
    }
}

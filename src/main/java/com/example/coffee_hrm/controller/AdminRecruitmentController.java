package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.enums.RecruitmentProposalStatus;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.response.AdminStoreCard;
import com.example.coffee_hrm.dto.response.CandidateApprovalResult;
import com.example.coffee_hrm.dto.response.CandidateRejectionResult;
import com.example.coffee_hrm.dto.response.RecruitmentRequestResponse;
import com.example.coffee_hrm.dto.response.StoreResponse;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
                       @RequestParam(required = false) RecruitmentProposalStatus status,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
                       @AuthenticationPrincipal AuthenticatedUser user,
                       Model model) {
        List<StoreResponse> stores = storeService.getStores(user);
        if (storeId == null) {
            model.addAttribute("storeCards", recruitmentCards(user, stores));
            model.addAttribute("stores", stores);
            return "admin/recruitment-list";
        }
        try {
            model.addAttribute("requests", recruitmentRequestService.listForAdmin(
                    user, storeId, managerId, status, createdFrom, createdTo));
        } catch (BusinessException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("requests", List.of());
        }
        model.addAttribute("stores", stores);
        model.addAttribute("managers", recruitmentRequestService.listManagerOptions());
        model.addAttribute("statuses", RecruitmentProposalStatus.values());
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

    @PostMapping("/{id}/candidates/{candidateId}/approve")
    public String approve(@PathVariable Integer id,
                          @PathVariable Integer candidateId,
                          @AuthenticationPrincipal AuthenticatedUser user,
                          RedirectAttributes redirectAttributes) {
        try {
            CandidateApprovalResult result = recruitmentRequestService.approveCandidate(user, id, candidateId);
            if (result.reactivatedExistingAccount()) {
                redirectAttributes.addFlashAttribute("successMessage",
                        "Đã duyệt tuyển dụng lại " + result.fullName()
                                + ". Hồ sơ và tài khoản cũ được kích hoạt, mật khẩu mới đã gửi qua email.");
            } else {
                redirectAttributes.addFlashAttribute("successMessage",
                        "Đã duyệt " + result.fullName() + ", tạo tài khoản Staff và gửi mật khẩu tạm thời qua email.");
            }
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/recruitment/" + id;
    }

    @PostMapping("/{id}/candidates/{candidateId}/reject")
    public String reject(@PathVariable Integer id,
                         @PathVariable Integer candidateId,
                         @RequestParam(required = false) String rejectReason,
                         @AuthenticationPrincipal AuthenticatedUser user,
                         RedirectAttributes redirectAttributes) {
        try {
            CandidateRejectionResult result = recruitmentRequestService.rejectCandidate(user, id, candidateId, rejectReason);
            if (result.noticeEmailSent()) {
                redirectAttributes.addFlashAttribute("successMessage",
                        "Đã từ chối nhân viên " + result.fullName() + " và gửi email thông báo không trúng tuyển.");
            } else {
                redirectAttributes.addFlashAttribute("successMessage",
                        "Đã từ chối nhân viên " + result.fullName()
                                + ". Trạng thái từ chối đã được lưu, nhưng chưa gửi được email thông báo không trúng tuyển.");
            }
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/recruitment/" + id;
    }

    private List<AdminStoreCard> recruitmentCards(AuthenticatedUser user, List<StoreResponse> stores) {
        Map<Integer, int[]> counts = new HashMap<>();
        try {
            for (RecruitmentRequestResponse request : recruitmentRequestService.listForAdmin(
                    user, null, null, null, null, null)) {
                if (request.getStoreId() == null) {
                    continue;
                }
                int[] count = counts.computeIfAbsent(request.getStoreId(), ignored -> new int[2]);
                count[0]++;
                count[1] += request.getPendingCount();
            }
        } catch (BusinessException ex) {
            counts.clear();
        }
        return stores.stream()
                .map(store -> {
                    int[] count = counts.getOrDefault(store.getId(), new int[2]);
                    return AdminStoreCard.of(
                            store,
                            count[0] + " đề xuất",
                            "Chờ duyệt " + count[1] + " nhân viên",
                            "/admin/recruitment?storeId=" + store.getId(),
                            "Xem đề xuất");
                })
                .toList();
    }
}

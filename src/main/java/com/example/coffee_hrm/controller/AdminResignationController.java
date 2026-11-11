package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.enums.ResignationItemStatus;
import com.example.coffee_hrm.common.enums.ResignationProposalStatus;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.request.PayrollPaymentForm;
import com.example.coffee_hrm.dto.response.ResignationDecisionResult;
import com.example.coffee_hrm.dto.response.ResignationItemResponse;
import com.example.coffee_hrm.dto.response.ResignationProposalResponse;
import com.example.coffee_hrm.dto.response.ResignationSettlementView;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.ResignationProposalService;
import com.example.coffee_hrm.service.ResignationSettlementService;
import com.example.coffee_hrm.service.StoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/resignation")
@PreAuthorize("hasRole('ADMIN')")
public class AdminResignationController {

    private static final String SETTLEMENT_UNAVAILABLE =
            "Chưa đọc được dữ liệu lương/thanh toán. Kiểm tra bảng MonthlyPayrolls và PayrollPayments trong database.";

    private final ResignationProposalService resignationProposalService;
    private final StoreService storeService;
    private final ResignationSettlementService settlementService;

    @GetMapping
    public String list(@RequestParam(required = false) Integer storeId,
                       @RequestParam(required = false) ResignationProposalStatus status,
                       @RequestParam(required = false) String keyword,
                       @AuthenticationPrincipal AuthenticatedUser user,
                       Model model) {
        try {
            List<ResignationProposalResponse> requests =
                    resignationProposalService.listForAdmin(user, storeId, status, keyword);
            model.addAttribute("requests", requests);
            model.addAttribute("settlementBadges", settlementBadges(user, requests));
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
            ResignationProposalResponse request = resignationProposalService.getForAdmin(user, id);
            model.addAttribute("request", request);
            loadSettlements(user, request, model);
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

    @PostMapping("/{id}/items/{itemId}/pay")
    public String pay(@PathVariable Integer id,
                      @PathVariable Integer itemId,
                      @RequestParam("reportId") int reportId,
                      @ModelAttribute PayrollPaymentForm form,
                      @RequestParam(value = "proof", required = false) MultipartFile proof,
                      @AuthenticationPrincipal AuthenticatedUser user,
                      RedirectAttributes redirectAttributes) {
        try {
            String amount = settlementService.pay(user, id, itemId, reportId, form, proof);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã ghi nhận thanh toán lương " + amount + ". Nhân viên đã nhận được thông báo.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Thanh toán chưa được ghi nhận: " + ex.getMessage());
        } catch (DataAccessException ex) {
            log.error("Không ghi nhận được thanh toán lương cho nhân sự {} trong đề xuất {}", itemId, id, ex);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Thanh toán chưa được ghi nhận do lỗi lưu dữ liệu. Không có khoản nào được đánh dấu đã trả.");
        }
        return "redirect:/admin/resignation/" + id + "#settle-" + itemId;
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

    /** Đối soát lương cho từng nhân sự của đề xuất; lỗi một dòng không làm hỏng cả trang. */
    private void loadSettlements(AuthenticatedUser user, ResignationProposalResponse request, Model model) {
        Map<Integer, ResignationSettlementView> settlements = new LinkedHashMap<>();
        Map<Integer, String> settlementErrors = new HashMap<>();
        if (request.getItems() != null) {
            for (ResignationItemResponse item : request.getItems()) {
                try {
                    settlements.put(item.getId(), settlementService.settlement(user, item.getEmployeeId()));
                } catch (BusinessException ex) {
                    settlementErrors.put(item.getId(), ex.getMessage());
                } catch (DataAccessException ex) {
                    log.error("Không đối soát được lương của nhân sự {} trong đề xuất {}", item.getId(), request.getId(), ex);
                    settlementErrors.put(item.getId(), SETTLEMENT_UNAVAILABLE);
                }
            }
        }
        boolean allPendingEligible = request.getItems() != null && request.getItems().stream()
                .filter(item -> item.getStatus() == ResignationItemStatus.PENDING)
                .allMatch(item -> settlements.containsKey(item.getId()) && settlements.get(item.getId()).isEligible());
        model.addAttribute("settlements", settlements);
        model.addAttribute("settlementErrors", settlementErrors);
        model.addAttribute("allPendingEligible", allPendingEligible);
    }

    /**
     * Tình trạng lương của các nhân sự đang chờ duyệt trong mỗi đề xuất, để nhận biết nhanh trên danh sách.
     * Lấy trạng thái nặng nhất: còn lương chưa trả, rồi tới chưa hoàn tất đối soát, rồi đủ điều kiện.
     */
    private Map<Integer, ResignationSettlementView> settlementBadges(AuthenticatedUser user,
                                                                    List<ResignationProposalResponse> requests) {
        Map<Integer, ResignationSettlementView> badges = new HashMap<>();
        for (ResignationProposalResponse request : requests) {
            if (request.getPendingCount() <= 0 || request.getItems() == null) {
                continue;
            }
            ResignationSettlementView worst = null;
            try {
                for (ResignationItemResponse item : request.getItems()) {
                    if (item.getStatus() != ResignationItemStatus.PENDING) {
                        continue;
                    }
                    ResignationSettlementView view = settlementService.settlement(user, item.getEmployeeId());
                    if (worst == null || severity(view) > severity(worst)) {
                        worst = view;
                    }
                }
            } catch (BusinessException | DataAccessException ex) {
                log.warn("Không đối soát được lương cho đề xuất {}: {}", request.getId(), ex.getMessage());
                continue;
            }
            if (worst != null) {
                badges.put(request.getId(), worst);
            }
        }
        return badges;
    }

    private static int severity(ResignationSettlementView view) {
        return switch (view.getStatusKey()) {
            case ResignationSettlementView.OWED -> 2;
            case ResignationSettlementView.PENDING -> 1;
            default -> 0;
        };
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

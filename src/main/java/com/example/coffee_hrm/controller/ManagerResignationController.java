package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.enums.ResignationProposalStatus;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.request.CreateResignationProposalRequest;
import com.example.coffee_hrm.dto.request.ResignationItemRequest;
import com.example.coffee_hrm.dto.response.ResignationDecisionResult;
import com.example.coffee_hrm.dto.response.ResignationProposalResponse;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.ResignationProposalService;
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
@RequestMapping("/manager/resignation")
@PreAuthorize("hasRole('MANAGER')")
public class ManagerResignationController {

    private final ResignationProposalService resignationProposalService;

    @GetMapping
    public String list(@AuthenticationPrincipal AuthenticatedUser user,
                       @RequestParam(required = false) ResignationProposalStatus status,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        try {
            List<ResignationProposalResponse> all = resignationProposalService.listMine(user);
            model.addAttribute("requests", status == null
                    ? all
                    : all.stream().filter(item -> item.getStatus() == status).toList());
            model.addAttribute("statuses", ResignationProposalStatus.values());
            model.addAttribute("selectedStatus", status);
            model.addAttribute("pendingCount", countStatus(all, ResignationProposalStatus.PENDING));
            model.addAttribute("partialCount", countStatus(all, ResignationProposalStatus.PARTIALLY_PROCESSED));
            model.addAttribute("completedCount", countStatus(all, ResignationProposalStatus.COMPLETED));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/dashboard/manager";
        }
        return "manager/resignation-list";
    }

    @GetMapping("/create")
    public String createPage(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new CreateResignationProposalRequest());
        }
        prepareCreatePage(user, model);
        return "manager/resignation-create";
    }

    @PostMapping("/create")
    public String create(@Valid @ModelAttribute("form") CreateResignationProposalRequest form,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal AuthenticatedUser user,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            prepareCreatePage(user, model);
            return "manager/resignation-create";
        }
        try {
            ResignationDecisionResult result = resignationProposalService.create(user, form);
            redirectAttributes.addFlashAttribute("successMessage", submitMessage(result));
            return "redirect:/manager/resignation";
        } catch (BusinessException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            prepareCreatePage(user, model);
            return "manager/resignation-create";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Integer id,
                         @AuthenticationPrincipal AuthenticatedUser user,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        try {
            model.addAttribute("request", resignationProposalService.getMine(user, id));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/manager/resignation";
        }
        return "manager/resignation-detail";
    }

    private String submitMessage(ResignationDecisionResult result) {
        String base = "Đã gửi đề xuất nghỉ việc gồm " + result.getProcessedCount()
                + " nhân sự. Admin sẽ xem xét từng người.";
        if (result.isEmailSent()) {
            return base;
        }
        return base + " Đề xuất đã được lưu và thông báo trong hệ thống, nhưng chưa gửi được email cho Admin.";
    }

    private long countStatus(List<ResignationProposalResponse> requests, ResignationProposalStatus status) {
        return requests.stream().filter(item -> item.getStatus() == status).count();
    }

    private void prepareCreatePage(AuthenticatedUser user, Model model) {
        Object form = model.getAttribute("form");
        List<ResignationItemRequest> items = form instanceof CreateResignationProposalRequest request
                && request.getItems() != null
                ? request.getItems()
                : List.of();
        model.addAttribute("itemPayload", items.stream().map(this::toPayload).toList());
        try {
            model.addAttribute("storeName", resignationProposalService.managedStoreLabel(user));
            model.addAttribute("employees", resignationProposalService.listSelectableEmployees(user));
        } catch (BusinessException ex) {
            model.addAttribute("storeName", null);
            model.addAttribute("employees", List.of());
            if (!model.containsAttribute("errorMessage")) {
                model.addAttribute("errorMessage", ex.getMessage());
            }
        }
    }

    private Map<String, String> toPayload(ResignationItemRequest item) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("employeeId", item.getEmployeeId() == null ? "" : item.getEmployeeId().toString());
        row.put("reason", item.getReason() == null ? "" : item.getReason());
        row.put("note", item.getNote() == null ? "" : item.getNote());
        return row;
    }
}

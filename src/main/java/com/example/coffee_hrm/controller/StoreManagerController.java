package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.enums.StoreStatus;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.request.CreateStoreManagerRequest;
import com.example.coffee_hrm.dto.response.ManagerProvisionResult;
import com.example.coffee_hrm.dto.response.StoreResponse;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.StoreManagerAccountService;
import com.example.coffee_hrm.service.StoreService;
import com.example.coffee_hrm.service.impl.StoreManagerAccountServiceImpl;
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
@RequestMapping("/admin/stores/{storeId}/managers")
@PreAuthorize("hasRole('ADMIN')")
public class StoreManagerController {

    private static final String FORM_VIEW = "admin/store-manager-create";
    private static final String DETAIL_VIEW = "admin/store-manager-view";
    private static final String LIST_REDIRECT = "redirect:/admin/stores";
    private static final String MANAGER_NOT_ASSIGNED = "Cửa hàng chưa có Manager.";

    private final StoreService storeService;
    private final StoreManagerAccountService storeManagerAccountService;

    @GetMapping
    public String view(@PathVariable Integer storeId,
                       @AuthenticationPrincipal AuthenticatedUser user,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        StoreResponse store;
        try {
            store = storeService.getStore(user, storeId);
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return LIST_REDIRECT;
        }
        if (store.getManagerName() == null || store.getManagerName().isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", MANAGER_NOT_ASSIGNED);
            return LIST_REDIRECT;
        }
        model.addAttribute("store", store);
        return DETAIL_VIEW;
    }

    @GetMapping("/new")
    public String createForm(@PathVariable Integer storeId,
                             @AuthenticationPrincipal AuthenticatedUser user,
                             Model model,
                             RedirectAttributes redirectAttributes) {
        StoreResponse store;
        try {
            store = requireOpenStore(user, storeId);
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return LIST_REDIRECT;
        }
        if (!model.containsAttribute("managerForm")) {
            model.addAttribute("managerForm", new CreateStoreManagerRequest());
        }
        model.addAttribute("store", store);
        return FORM_VIEW;
    }

    @PostMapping
    public String create(@PathVariable Integer storeId,
                         @Valid @ModelAttribute("managerForm") CreateStoreManagerRequest managerForm,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal AuthenticatedUser user,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        StoreResponse store;
        try {
            store = requireOpenStore(user, storeId);
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return LIST_REDIRECT;
        }
        model.addAttribute("store", store);
        if (bindingResult.hasErrors()) {
            return FORM_VIEW;
        }
        try {
            ManagerProvisionResult result = storeManagerAccountService.createManager(user, storeId, managerForm);
            publishOutcome(redirectAttributes, result);
            return LIST_REDIRECT;
        } catch (BusinessException ex) {
            if (isFieldError(ex)) {
                exposeFieldError(bindingResult, ex);
                model.addAttribute("errorMessage", ex.getMessage());
                return FORM_VIEW;
            }
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return LIST_REDIRECT;
        }
    }

    private StoreResponse requireOpenStore(AuthenticatedUser user, Integer storeId) {
        StoreResponse store = storeService.getStore(user, storeId);
        if (store.getManagerName() != null && !store.getManagerName().isBlank()) {
            throw new BusinessException(StoreManagerAccountServiceImpl.STORE_ALREADY_HAS_MANAGER);
        }
        if (store.getStatus() == StoreStatus.INACTIVE || Boolean.FALSE.equals(store.getIsActive())) {
            throw new BusinessException(StoreManagerAccountServiceImpl.STORE_NOT_ELIGIBLE);
        }
        return store;
    }

    private void publishOutcome(RedirectAttributes redirectAttributes, ManagerProvisionResult result) {
        String message = result.isEmailSent()
                ? "Đã tạo tài khoản Manager " + result.getManagerName()
                + " cho cửa hàng " + result.getStoreName() + " và gửi email thông tin đăng nhập."
                : "Đã tạo tài khoản Manager " + result.getManagerName()
                + " cho cửa hàng " + result.getStoreName()
                + ", nhưng gửi email thất bại. Tài khoản không bị xóa.";
        if (result.isEmailSent()) {
            redirectAttributes.addFlashAttribute("successMessage", message);
        } else {
            redirectAttributes.addFlashAttribute("warningMessage", message);
        }
    }

    private boolean isFieldError(BusinessException ex) {
        String message = ex.getMessage();
        return StoreManagerAccountServiceImpl.EMAIL_EXISTS.equals(message)
                || StoreManagerAccountServiceImpl.EMAIL_INVALID.equals(message)
                || StoreManagerAccountServiceImpl.EMAIL_REQUIRED.equals(message)
                || StoreManagerAccountServiceImpl.PHONE_EXISTS.equals(message)
                || StoreManagerAccountServiceImpl.PHONE_INVALID.equals(message)
                || StoreManagerAccountServiceImpl.PHONE_REQUIRED.equals(message)
                || StoreManagerAccountServiceImpl.NAME_REQUIRED.equals(message);
    }

    private void exposeFieldError(BindingResult bindingResult, BusinessException ex) {
        String message = ex.getMessage();
        if (StoreManagerAccountServiceImpl.NAME_REQUIRED.equals(message)) {
            bindingResult.rejectValue("fullName", "business", message);
        } else if (StoreManagerAccountServiceImpl.EMAIL_EXISTS.equals(message)
                || StoreManagerAccountServiceImpl.EMAIL_INVALID.equals(message)
                || StoreManagerAccountServiceImpl.EMAIL_REQUIRED.equals(message)) {
            bindingResult.rejectValue("email", "business", message);
        } else {
            bindingResult.rejectValue("phone", "business", message);
        }
    }
}

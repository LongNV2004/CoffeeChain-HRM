package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.request.CreateStoreRequest;
import com.example.coffee_hrm.dto.request.UpdateStoreRequest;
import com.example.coffee_hrm.dto.response.StoreResponse;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.StoreService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/stores")
@PreAuthorize("hasRole('ADMIN')")
public class StoreController {

    private static final String STORE_LIST_VIEW = "admin/stores";
    private static final String STORE_CREATE_VIEW = "admin/store-create";
    private static final String STORE_EDIT_VIEW = "admin/store-edit";
    private static final String STORE_LIST_REDIRECT = "redirect:/admin/stores";
    private static final String SYSTEM_ERROR_MESSAGE = "Unable to process the request. Please try again later.";

    private final StoreService storeService;

    @InitBinder
    public void trimStrings(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    // UC 1.1 - Admin View Cafe Locations
    @GetMapping
    public String viewStores(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        model.addAttribute("stores", storeService.getStores(user));
        return STORE_LIST_VIEW;
    }

    // UC 1.3 - Admin Create Cafe Location
    @GetMapping("/create")
    public String createStoreForm(Model model) {
        Store defaults = Store.builder().build();
        model.addAttribute("storeForm", CreateStoreRequest.builder()
                .totalLeaveDays(defaults.getTotalLeaveDays())
                .isActive(defaults.getIsActive())
                .build());
        return STORE_CREATE_VIEW;
    }

    @PostMapping("/create")
    public String createStore(@Valid @ModelAttribute("storeForm") CreateStoreRequest storeForm,
                              BindingResult bindingResult,
                              @AuthenticationPrincipal AuthenticatedUser user,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return STORE_CREATE_VIEW;
        }
        try {
            StoreResponse created = storeService.createStore(user, storeForm);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã tạo cửa hàng " + created.getStoreName() + " (Store #" + created.getId()
                            + "). Manager: Chưa chỉ định.");
            return STORE_LIST_REDIRECT;
        } catch (BusinessException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            return STORE_CREATE_VIEW;
        }
    }

    // UC 1.2 - Admin Update Cafe Location
    @GetMapping("/{storeId}/edit")
    public String editStore(@PathVariable Integer storeId,
                            @AuthenticationPrincipal AuthenticatedUser user,
                            Model model,
                            RedirectAttributes redirectAttributes) {
        try {
            StoreResponse store = storeService.getStore(user, storeId);
            model.addAttribute("store", store);
            model.addAttribute("storeForm", UpdateStoreRequest.builder()
                    .storeName(store.getStoreName())
                    .address(store.getAddress())
                    .totalLeaveDays(store.getTotalLeaveDays())
                    .isActive(store.getIsActive())
                    .build());
            return STORE_EDIT_VIEW;
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return STORE_LIST_REDIRECT;
        }
    }

    @PostMapping("/{storeId}")
    public String updateStore(@PathVariable Integer storeId,
                              @Valid @ModelAttribute("storeForm") UpdateStoreRequest storeForm,
                              BindingResult bindingResult,
                              @AuthenticationPrincipal AuthenticatedUser user,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        StoreResponse current;
        try {
            current = storeService.getStore(user, storeId);
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return STORE_LIST_REDIRECT;
        }
        model.addAttribute("store", current);
        if (bindingResult.hasErrors()) {
            return STORE_EDIT_VIEW;
        }
        try {
            StoreResponse updated = storeService.updateStore(user, storeId, storeForm);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã cập nhật thông tin cửa hàng " + updated.getStoreName() + ".");
            return STORE_LIST_REDIRECT;
        } catch (BusinessException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            return STORE_EDIT_VIEW;
        }
    }

    @ExceptionHandler(DataAccessException.class)
    public ModelAndView handleDataAccessException() {
        ModelAndView view = new ModelAndView(STORE_LIST_VIEW);
        view.addObject("stores", List.of());
        view.addObject("errorMessage", SYSTEM_ERROR_MESSAGE);
        return view;
    }
}

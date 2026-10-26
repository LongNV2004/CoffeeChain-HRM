package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.request.CreateStoreRequest;
import com.example.coffee_hrm.dto.request.UpdateStoreRequest;
import com.example.coffee_hrm.dto.response.StoreResponse;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.StoreService;
import com.example.coffee_hrm.service.impl.StoreServiceImpl;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

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
        model.addAttribute("storeForm", CreateStoreRequest.builder().build());
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
                            + "). Trạng thái: " + created.getStatusLabel()
                            + ". Manager: Chưa chỉ định."
                            + locationSuffix(created));
            return STORE_LIST_REDIRECT;
        } catch (BusinessException ex) {
            exposeBusinessError(bindingResult, ex);
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
                    .totalLeaveSlots(store.getTotalLeaveSlots())
                    .totalLeaveDays(store.getTotalLeaveDays())
                    .openingDate(store.getOpeningDate())
                    .latitude(plainCoordinate(store.getLatitude()))
                    .longitude(plainCoordinate(store.getLongitude()))
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
            redirectAttributes.addFlashAttribute("successMessage", storeUpdatedMessage(updated));
            return STORE_LIST_REDIRECT;
        } catch (BusinessException ex) {
            exposeBusinessError(bindingResult, ex);
            model.addAttribute("errorMessage", ex.getMessage());
            return STORE_EDIT_VIEW;
        }
    }

    @PostMapping("/{storeId}/status")
    @ResponseBody
    public ResponseEntity<Map<String, String>> changeStatus(@PathVariable Integer storeId,
                                                            @RequestParam String action,
                                                            @AuthenticationPrincipal AuthenticatedUser user) {
        try {
            StoreResponse updated = storeService.changeStatus(user, storeId, action);
            String message = "deactivate".equals(action)
                    ? "Đã chuyển cửa hàng " + updated.getStoreName()
                    + " sang Ngừng hoạt động. Dữ liệu nhân sự và nghiệp vụ được giữ nguyên."
                    : "Đã kích hoạt lại cửa hàng " + updated.getStoreName()
                    + ". Trạng thái hiện tại: " + updated.getStatusLabel() + ".";
            return ResponseEntity.ok(Map.of(
                    "message", message,
                    "status", updated.getStatus().name(),
                    "statusLabel", updated.getStatusLabel()));
        } catch (BusinessException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    private String storeUpdatedMessage(StoreResponse updated) {
        String message = "Đã cập nhật thông tin cửa hàng " + updated.getStoreName() + ".";
        if (updated.getStatusLabel() != null) {
            message = message + " Trạng thái: " + updated.getStatusLabel() + ".";
        }
        return message + locationSuffix(updated);
    }

    private void exposeBusinessError(BindingResult bindingResult, BusinessException ex) {
        String message = ex.getMessage();
        if (StoreServiceImpl.STORE_NAME_EXISTS.equals(message)
                || StoreServiceImpl.STORE_NAME_REQUIRED.equals(message)) {
            bindingResult.rejectValue("storeName", "business", message);
        } else if (StoreServiceImpl.ADDRESS_REQUIRED.equals(message)) {
            bindingResult.rejectValue("address", "business", message);
        } else if (StoreServiceImpl.OPENING_DATE_REQUIRED.equals(message)) {
            bindingResult.rejectValue("openingDate", "business", message);
        } else if (StoreServiceImpl.LOCATION_REQUIRED.equals(message)
                || StoreServiceImpl.INVALID_COORDINATES.equals(message)) {
            bindingResult.rejectValue("latitude", "business", message);
            bindingResult.rejectValue("longitude", "business", message);
        }
    }

    private String locationSuffix(StoreResponse store) {
        if (store.getLatitude() == null || store.getLongitude() == null) {
            return "";
        }
        return " Vị trí chấm công: "
                + store.getLatitude().stripTrailingZeros().toPlainString()
                + ", "
                + store.getLongitude().stripTrailingZeros().toPlainString()
                + ".";
    }

    private String plainCoordinate(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    @ExceptionHandler(DataAccessException.class)
    public ModelAndView handleDataAccessException() {
        ModelAndView view = new ModelAndView(STORE_LIST_VIEW);
        view.addObject("stores", List.of());
        view.addObject("errorMessage", SYSTEM_ERROR_MESSAGE);
        return view;
    }
}

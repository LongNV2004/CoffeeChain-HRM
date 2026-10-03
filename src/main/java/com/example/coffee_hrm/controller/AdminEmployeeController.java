package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.response.EmployeeResponse;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.EmployeeService;
import com.example.coffee_hrm.service.StoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminEmployeeController {

    private static final String STORE_EMPLOYEES_VIEW = "admin/store-employees";
    private static final String EMPLOYEES_HOME_REDIRECT = "redirect:/admin/employees";
    private static final String STORE_EMPLOYEES_REDIRECT = "redirect:/admin/stores/{storeId}/employees";
    private static final String SYSTEM_ERROR_MESSAGE = "Unable to process the request. Please try again later.";

    private final StoreService storeService;
    private final EmployeeService employeeService;

    // UC 6 - Admin quản lý nhân sự theo cửa hàng: chọn cửa hàng
    @GetMapping("/employees")
    public String chooseStore(@RequestParam(required = false) Integer storeId,
                              @AuthenticationPrincipal AuthenticatedUser user,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        if (storeId != null) {
            redirectAttributes.addAttribute("storeId", storeId);
            return STORE_EMPLOYEES_REDIRECT;
        }
        model.addAttribute("stores", storeService.getStores(user));
        return STORE_EMPLOYEES_VIEW;
    }

    // UC 6 - Admin xem nhân sự của một cửa hàng
    @GetMapping("/stores/{storeId}/employees")
    public String viewStoreEmployees(@PathVariable Integer storeId,
                                     @AuthenticationPrincipal AuthenticatedUser user,
                                     Model model,
                                     RedirectAttributes redirectAttributes) {
        try {
            model.addAttribute("selectedStore", storeService.getStore(user, storeId));
            model.addAttribute("employees", employeeService.getStoreEmployees(user, storeId));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return EMPLOYEES_HOME_REDIRECT;
        }
        model.addAttribute("stores", storeService.getStores(user));
        return STORE_EMPLOYEES_VIEW;
    }

    // UC 6.1 - Admin phân quyền MANAGER / STAFF cho nhân viên của cửa hàng
    @PostMapping("/stores/{storeId}/employees/{employeeId}/role")
    public String changeEmployeeRole(@PathVariable Integer storeId,
                                     @PathVariable Integer employeeId,
                                     @RequestParam(required = false) String role,
                                     @AuthenticationPrincipal AuthenticatedUser user,
                                     RedirectAttributes redirectAttributes) {
        try {
            EmployeeResponse updated = employeeService.changeEmployeeRole(user, storeId, employeeId, role);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã cập nhật role của " + updated.getFullName() + " thành " + updated.getRoleName().name() + ".");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return STORE_EMPLOYEES_REDIRECT;
    }

    @ExceptionHandler(DataAccessException.class)
    public ModelAndView handleDataAccessException() {
        ModelAndView view = new ModelAndView(STORE_EMPLOYEES_VIEW);
        view.addObject("stores", List.of());
        view.addObject("errorMessage", SYSTEM_ERROR_MESSAGE);
        return view;
    }
}

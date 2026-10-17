package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/manager/employees")
@PreAuthorize("hasRole('MANAGER')")
public class ManagerEmployeeController {

    private static final String LIST_VIEW = "manager/employees";
    private static final String DETAIL_VIEW = "manager/employee-detail";
    private static final String LIST_REDIRECT = "redirect:/manager/employees";
    private static final String SYSTEM_ERROR_MESSAGE =
            "Không thể tải danh sách nhân sự. Vui lòng thử lại sau.";

    private final EmployeeService employeeService;

    @GetMapping
    public String list(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        try {
            var directory = employeeService.getManagedDirectory(user);
            model.addAttribute("directory", directory);
            model.addAttribute("employees", directory.employees());
        } catch (BusinessException ex) {
            model.addAttribute("employees", List.of());
            model.addAttribute("errorMessage", ex.getMessage());
        }
        return LIST_VIEW;
    }

    @GetMapping("/{employeeId}")
    public String detail(@PathVariable Integer employeeId,
                         @AuthenticationPrincipal AuthenticatedUser user,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        try {
            model.addAttribute("detail", employeeService.getManagedEmployee(user, employeeId));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return LIST_REDIRECT;
        }
        return DETAIL_VIEW;
    }

    @ExceptionHandler(DataAccessException.class)
    public String handleDataAccess(Model model) {
        model.addAttribute("employees", List.of());
        model.addAttribute("errorMessage", SYSTEM_ERROR_MESSAGE);
        return LIST_VIEW;
    }
}

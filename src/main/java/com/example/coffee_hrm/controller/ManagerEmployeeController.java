package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.response.EmployeeResponse;
import com.example.coffee_hrm.dto.response.EmploymentContractResponse;
import com.example.coffee_hrm.dto.response.ManagedEmployeeDetail;
import com.example.coffee_hrm.dto.response.ManagedEmployeeDirectory;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.EmployeeService;
import com.example.coffee_hrm.service.EmploymentContractService;
import com.example.coffee_hrm.service.support.EmployeeRecordSupport;
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
import org.springframework.web.bind.annotation.RequestParam;
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
    private final EmploymentContractService contractService;

    @GetMapping
    public String list(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        try {
            ManagedEmployeeDirectory directory = employeeService.getManagedDirectory(user);
            model.addAttribute("directory", directory);
            model.addAttribute("employees", directory.employees());
            attachContractStatus(user, directory, model);
        } catch (BusinessException ex) {
            model.addAttribute("employees", List.of());
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("effectiveContractCount", 0L);
            model.addAttribute("expiringContractCount", 0L);
        }
        return LIST_VIEW;
    }

    @GetMapping("/{employeeId}")
    public String detail(@PathVariable Integer employeeId,
                         @RequestParam(name = "tab", defaultValue = "profile") String tab,
                         @AuthenticationPrincipal AuthenticatedUser user,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        ManagedEmployeeDetail detail;
        try {
            detail = employeeService.getManagedEmployee(user, employeeId);
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return LIST_REDIRECT;
        }
        List<EmploymentContractResponse> contracts = contractsOf(user, employeeId, model);
        model.addAttribute("detail", detail);
        model.addAttribute("contracts", contracts);
        model.addAttribute("history", EmployeeRecordSupport.history(detail.employee(), detail.trainings(), contracts));
        model.addAttribute("tab", normalizeTab(tab));
        model.addAttribute("canCreateContract", canCreate(user, detail.employee()));
        return DETAIL_VIEW;
    }

    @ExceptionHandler(DataAccessException.class)
    public String handleDataAccess(Model model) {
        model.addAttribute("employees", List.of());
        model.addAttribute("errorMessage", SYSTEM_ERROR_MESSAGE);
        model.addAttribute("effectiveContractCount", 0L);
        model.addAttribute("expiringContractCount", 0L);
        return LIST_VIEW;
    }

    private void attachContractStatus(AuthenticatedUser actor, ManagedEmployeeDirectory directory, Model model) {
        List<EmploymentContractResponse> contracts = List.of();
        try {
            List<EmploymentContractResponse> loaded = contractService.list(actor);
            if (loaded != null) {
                contracts = loaded;
            }
        } catch (BusinessException ex) {
            model.addAttribute("contractError", ex.getMessage());
        }
        EmployeeRecordSupport.ContractTotals totals = EmployeeRecordSupport.applyCurrentStatus(
                directory.employees(), contracts);
        model.addAttribute("effectiveContractCount", totals.effectiveCount());
        model.addAttribute("expiringContractCount", totals.expiringCount());
    }

    private List<EmploymentContractResponse> contractsOf(AuthenticatedUser actor, Integer employeeId, Model model) {
        try {
            List<EmploymentContractResponse> loaded = contractService.listForEmployee(actor, employeeId);
            return loaded == null ? List.of() : loaded;
        } catch (BusinessException ex) {
            model.addAttribute("contractError", ex.getMessage());
            return List.of();
        }
    }

    private static boolean canCreate(AuthenticatedUser actor, EmployeeResponse employee) {
        if (employee == null || employee.getStatus() == EmployeeStatus.TERMINATED) {
            return false;
        }
        return actor == null || actor.getEmployeeId() == null || !actor.getEmployeeId().equals(employee.getId());
    }

    private static String normalizeTab(String tab) {
        if ("contract".equals(tab) || "history".equals(tab)) {
            return tab;
        }
        return "profile";
    }
}

package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.common.enums.ContractValidityStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.dto.response.EmployeeResponse;
import com.example.coffee_hrm.dto.response.ManagerSuccession;
import com.example.coffee_hrm.dto.response.EmploymentContractResponse;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.EmployeeService;
import com.example.coffee_hrm.service.EmploymentContractService;
import com.example.coffee_hrm.service.StoreService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.servlet.http.HttpServletRequest;
import com.example.coffee_hrm.dto.request.CreateEmployeeRequest;
import com.example.coffee_hrm.dto.request.UpdateEmployeeRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import java.util.Map;

import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminEmployeeController {

    private static final String CHAIN_STORES_VIEW = "admin/chain-employees";
    private static final String STORE_EMPLOYEES_VIEW = "admin/store-employees";
    private static final String EMPLOYEE_DETAIL_VIEW = "admin/employee-detail";
    private static final String EMPLOYEES_HOME_REDIRECT = "redirect:/admin/employees";
    private static final String STORE_EMPLOYEES_REDIRECT = "redirect:/admin/stores/{storeId}/employees";
    private static final String SYSTEM_ERROR_MESSAGE = "Unable to process the request. Please try again later.";


    private final StoreService storeService;
    private final EmployeeService employeeService;
    private final EmploymentContractService contractService;

    @GetMapping("/employees")
    public String viewChainStores(
            @RequestParam(required = false) Integer storeId,
            @AuthenticationPrincipal AuthenticatedUser user,
            Model model
    ) {
        if (storeId != null) {
            return "redirect:/admin/stores/" + storeId + "/employees";
        }
        model.addAttribute("stores", storeService.getStores(user));
        return CHAIN_STORES_VIEW;
    }

    @GetMapping("/stores/{storeId}/employees")
    public String viewStoreEmployees(@PathVariable Integer storeId,
                                     @AuthenticationPrincipal AuthenticatedUser user,
                                     Model model,
                                     RedirectAttributes redirectAttributes) {
        List<EmployeeResponse> employees;
        try {
            model.addAttribute("selectedStore", storeService.getStore(user, storeId));
            employees = employeeService.getStoreEmployees(user, storeId);
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return EMPLOYEES_HOME_REDIRECT;
        }
        if (employees == null) {
            employees = List.of();
        }
        model.addAttribute("employees", employees);
        EmployeeRecordSupport.ContractTotals totals = EmployeeRecordSupport.applyCurrentStatus(
                employees, contractsOfStore(user));
        model.addAttribute("effectiveContractCount", totals.effectiveCount());
        model.addAttribute("expiringContractCount", totals.expiringCount());
        model.addAttribute("succession", successionOf(user, storeId));
        return STORE_EMPLOYEES_VIEW;
    }

    @GetMapping("/stores/{storeId}/employees/{employeeId}")
    public String viewEmployeeDetail(@PathVariable Integer storeId,
                                     @PathVariable Integer employeeId,
                                     @RequestParam(name = "tab", defaultValue = "profile") String tab,
                                     @AuthenticationPrincipal AuthenticatedUser user,
                                     Model model,
                                     RedirectAttributes redirectAttributes) {
        try {
            model.addAttribute("store", storeService.getStore(user, storeId));
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return EMPLOYEES_HOME_REDIRECT;
        }
        EmployeeResponse employee;
        try {
            employee = employeeService.getStoreEmployee(user, storeId, employeeId);
            model.addAttribute("employee", employee);
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return STORE_EMPLOYEES_REDIRECT;
        }
        List<EmploymentContractResponse> contracts = contractsOf(user, employeeId);
        model.addAttribute("contracts", contracts);
        model.addAttribute("terminableContract", terminableContract(employee, contracts));
        model.addAttribute("succession", successionOf(user, storeId));
        model.addAttribute("maxBirthDate", com.example.coffee_hrm.common.time.VietnamTime.today().minusDays(1));
        model.addAttribute("history", EmployeeRecordSupport.history(employee, List.of(), contracts));
        model.addAttribute("tab", "contract".equals(tab) || "history".equals(tab) ? tab : "profile");
        return EMPLOYEE_DETAIL_VIEW;
    }

    private List<EmploymentContractResponse> contractsOfStore(AuthenticatedUser user) {
        try {
            List<EmploymentContractResponse> loaded = contractService.list(user);
            return loaded == null ? List.of() : loaded;
        } catch (BusinessException ex) {
            return List.of();
        }
    }

    private List<EmploymentContractResponse> contractsOf(AuthenticatedUser user, Integer employeeId) {
        try {
            List<EmploymentContractResponse> loaded = contractService.listForEmployee(user, employeeId);
            return loaded == null ? List.of() : loaded;
        } catch (BusinessException ex) {
            return List.of();
        }
    }

    @PostMapping("/stores/{storeId}/employees/{employeeId}/role")
    public String changeEmployeeRole(@PathVariable Integer storeId,
                                     @PathVariable Integer employeeId,
                                     @RequestParam(required = false) String role,
                                     @RequestParam(required = false) String returnTo,
                                     @RequestParam(required = false) Integer returnEmployeeId,
                                     @AuthenticationPrincipal AuthenticatedUser user,
                                     RedirectAttributes redirectAttributes) {
        try {
            EmployeeResponse updated = employeeService.changeEmployeeRole(user, storeId, employeeId, role);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã thay đổi chức vụ của " + updated.getFullName() + " thành "
                            + (updated.getRoleName() == RoleName.MANAGER ? "Manager" : "Staff") + ".");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        // Quay lại đúng trang Admin vừa thao tác (danh sách cửa hàng hoặc hồ sơ Manager cũ); chỉ nhận giá trị cố định.
        if ("list".equals(returnTo)) {
            return "redirect:/admin/stores/" + storeId + "/employees";
        }
        Integer target = returnEmployeeId != null ? returnEmployeeId : employeeId;
        return "redirect:/admin/stores/" + storeId + "/employees/" + target;
    }

    @PostMapping("/stores/{storeId}/employees/{employeeId}/terminate-contract")
    public String terminateManagerContract(@PathVariable Integer storeId,
                                           @PathVariable Integer employeeId,
                                           @RequestParam(required = false) String reason,
                                           @RequestParam(required = false) Integer replacementEmployeeId,
                                           @AuthenticationPrincipal AuthenticatedUser user,
                                           RedirectAttributes redirectAttributes) {
        EmployeeResponse updated;
        try {
            updated = employeeService.terminateManagerContract(user, storeId, employeeId, reason);
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/admin/stores/" + storeId + "/employees/" + employeeId;
        }
        String terminated = "Đã chấm dứt hợp đồng của " + updated.getFullName()
                + ". Tài khoản chuyển về Staff, hồ sơ chuyển sang đã nghỉ việc và không đăng nhập được nữa.";
        if (replacementEmployeeId == null) {
            redirectAttributes.addFlashAttribute("successMessage",
                    terminated + " Cửa hàng đang chưa có Manager — hãy bổ nhiệm người thay thế.");
            return "redirect:/admin/stores/" + storeId + "/employees/" + employeeId;
        }
        // Chấm dứt đã hoàn tất; bổ nhiệm người thay thế là bước riêng để lỗi bổ nhiệm không hoàn tác việc chấm dứt.
        try {
            EmployeeResponse replacement = employeeService.changeEmployeeRole(
                    user, storeId, replacementEmployeeId, RoleName.MANAGER.name());
            redirectAttributes.addFlashAttribute("successMessage",
                    terminated + " " + replacement.getFullName() + " đã được bổ nhiệm làm Manager thay thế.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("successMessage", terminated);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Chưa bổ nhiệm được Manager thay thế: " + ex.getMessage());
        }
        return "redirect:/admin/stores/" + storeId + "/employees/" + employeeId;
    }

    private ManagerSuccession successionOf(AuthenticatedUser user, Integer storeId) {
        try {
            return employeeService.getManagerSuccession(user, storeId);
        } catch (BusinessException ex) {
            return new ManagerSuccession(true, null, List.of());
        }
    }

    private static EmploymentContractResponse terminableContract(EmployeeResponse employee,
                                                                 List<EmploymentContractResponse> contracts) {
        if (employee == null || employee.getRoleName() != RoleName.MANAGER
                || employee.getStatus() == EmployeeStatus.TERMINATED || contracts == null) {
            return null;
        }
        EmploymentContractResponse working = null;
        EmploymentContractResponse waiting = null;
        for (EmploymentContractResponse contract : contracts) {
            ContractValidityStatus status = contract.getValidityStatus();
            if (status == null) {
                continue;
            }
            if (status.allowsWork()) {
                working = laterContract(working, contract);
            } else if (status == ContractValidityStatus.CHO_HIEU_LUC) {
                waiting = laterContract(waiting, contract);
            }
        }
        return working != null ? working : waiting;
    }

    private static EmploymentContractResponse laterContract(EmploymentContractResponse current,
                                                            EmploymentContractResponse candidate) {
        if (current == null || candidate.getStartDate() == null) {
            return current == null ? candidate : current;
        }
        if (current.getStartDate() == null || candidate.getStartDate().isAfter(current.getStartDate())) {
            return candidate;
        }
        return current;
    }

    @PostMapping("/stores/{storeId}/employees/create")
    @ResponseBody
    public EmployeeResponse createEmployee(
            @PathVariable Integer storeId,
            @Valid @RequestBody CreateEmployeeRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        if (!storeId.equals(request.getStoreId())) {
            throw new BusinessException("Chi nhánh không hợp lệ");
        }

        return employeeService.createEmployee(user, request);
    }

    @GetMapping("/stores/{storeId}/employees/{employeeId}/edit-data")
    @ResponseBody
    public EmployeeResponse getEmployeeForEdit(
            @PathVariable Integer storeId,
            @PathVariable Integer employeeId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return employeeService.getStoreEmployee(user, storeId, employeeId);
    }

    @PostMapping("/stores/{storeId}/employees/{employeeId}/citizen-id")
    public String updateCitizenId(
            @PathVariable Integer storeId,
            @PathVariable Integer employeeId,
            @RequestParam(required = false) String citizenId,
            @AuthenticationPrincipal AuthenticatedUser user,
            RedirectAttributes redirectAttributes
    ) {
        try {
            employeeService.updateCitizenId(user, storeId, employeeId, citizenId);
            redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật số căn cước công dân.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/stores/" + storeId + "/employees/" + employeeId;
    }

    @PostMapping("/stores/{storeId}/employees/{employeeId}/date-of-birth")
    public String updateDateOfBirth(
            @PathVariable Integer storeId,
            @PathVariable Integer employeeId,
            @RequestParam(required = false) String dateOfBirth,
            @AuthenticationPrincipal AuthenticatedUser user,
            RedirectAttributes redirectAttributes
    ) {
        try {
            java.time.LocalDate parsed = dateOfBirth == null || dateOfBirth.isBlank()
                    ? null : java.time.LocalDate.parse(dateOfBirth.trim());
            employeeService.updateDateOfBirth(user, storeId, employeeId, parsed);
            redirectAttributes.addFlashAttribute("successMessage",
                    parsed == null ? "Đã xóa ngày sinh trong hồ sơ." : "Đã cập nhật ngày sinh.");
        } catch (java.time.format.DateTimeParseException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Ngày sinh không hợp lệ.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/stores/" + storeId + "/employees/" + employeeId;
    }

    @PostMapping("/stores/{storeId}/employees/{employeeId}/edit")
    @ResponseBody
    public EmployeeResponse updateEmployee(
            @PathVariable Integer storeId,
            @PathVariable Integer employeeId,
            @Valid @RequestBody UpdateEmployeeRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return employeeService.updateEmployee(
                user, storeId, employeeId, request
        );
    }

    @ExceptionHandler(BusinessException.class)
    @ResponseBody
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleEmployeeBusinessException(
            BusinessException ex
    ) {
        return Map.of("message", ex.getMessage());
    }
    @ExceptionHandler(DataAccessException.class)
    public ModelAndView handleDataAccessException(HttpServletRequest request) {
        String uri = request.getRequestURI();
        boolean chainDirectory = uri != null && uri.endsWith("/admin/employees");
        ModelAndView view = new ModelAndView(chainDirectory ? CHAIN_STORES_VIEW : STORE_EMPLOYEES_VIEW);
        view.addObject("stores", List.of());
        view.addObject("employees", List.of());
        view.addObject("errorMessage", SYSTEM_ERROR_MESSAGE);
        return view;
    }
}

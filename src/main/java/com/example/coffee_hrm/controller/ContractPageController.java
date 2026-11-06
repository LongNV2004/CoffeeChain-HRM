package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.request.ContractFilePayload;
import com.example.coffee_hrm.dto.request.CreateEmploymentContractRequest;
import com.example.coffee_hrm.dto.response.ContractDownload;
import com.example.coffee_hrm.dto.response.ContractEmployeeOption;
import com.example.coffee_hrm.dto.response.ContractOcrView;
import com.example.coffee_hrm.dto.response.EmploymentContractResponse;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.ContractRenewalService;
import com.example.coffee_hrm.service.EmploymentContractService;
import com.example.coffee_hrm.service.impl.ContractValidityRules;
import com.example.coffee_hrm.service.ocr.ContractOcrService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class ContractPageController {

    private final EmploymentContractService contractService;
    private final ContractRenewalService contractRenewalService;
    private final ContractOcrService contractOcrService;

    @GetMapping("/admin/contract")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminContract() {
        return "redirect:/admin/employees";
    }

    @GetMapping("/manager/contract")
    @PreAuthorize("hasRole('MANAGER')")
    public String managerContract() {
        return "redirect:/manager/employees";
    }

    @GetMapping("/staff/contract")
    @PreAuthorize("hasRole('STAFF')")
    public String staffContract() {
        return "redirect:/profile?tab=contract";
    }

    @GetMapping("/admin/contract/{id:\\d+}")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminDetail(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Integer id,
                              RedirectAttributes redirectAttributes) {
        return redirectToEmployee(actor, "admin", id, redirectAttributes);
    }

    @GetMapping("/manager/contract/{id:\\d+}")
    @PreAuthorize("hasRole('MANAGER')")
    public String managerDetail(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Integer id,
                                RedirectAttributes redirectAttributes) {
        return redirectToEmployee(actor, "manager", id, redirectAttributes);
    }

    @GetMapping("/staff/contract/{id:\\d+}")
    @PreAuthorize("hasRole('STAFF')")
    public String staffDetail(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Integer id,
                              RedirectAttributes redirectAttributes) {
        return redirectToEmployee(actor, "staff", id, redirectAttributes);
    }

    @GetMapping("/admin/contract/{id:\\d+}/file")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> adminFile(@AuthenticationPrincipal AuthenticatedUser actor,
                                            @PathVariable Integer id,
                                            @RequestParam(defaultValue = "false") boolean inline) {
        return file(actor, id, inline);
    }

    @GetMapping("/manager/contract/{id:\\d+}/file")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<byte[]> managerFile(@AuthenticationPrincipal AuthenticatedUser actor,
                                              @PathVariable Integer id,
                                              @RequestParam(defaultValue = "false") boolean inline) {
        return file(actor, id, inline);
    }

    @GetMapping("/staff/contract/{id:\\d+}/file")
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<byte[]> staffFile(@AuthenticationPrincipal AuthenticatedUser actor,
                                            @PathVariable Integer id,
                                            @RequestParam(defaultValue = "false") boolean inline) {
        return file(actor, id, inline);
    }

    @GetMapping("/manager/contract/create")
    @PreAuthorize("hasRole('MANAGER')")
    public String createForm(@AuthenticationPrincipal AuthenticatedUser actor,
                             @RequestParam(required = false) Integer employeeId,
                             Model model) {
        prepareCreateForm(employeeId, model);
        return renderCreate(actor, model);
    }

    @GetMapping("/admin/contract/create")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminCreateForm(@AuthenticationPrincipal AuthenticatedUser actor,
                                  @RequestParam(required = false) Integer employeeId,
                                  Model model) {
        prepareCreateForm(employeeId, model);
        return renderCreate(actor, model);
    }

    @PostMapping(value = "/admin/contract/ocr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public ContractOcrView adminOcr(@AuthenticationPrincipal AuthenticatedUser actor,
                                    @RequestParam(value = "contractFile", required = false) MultipartFile contractFile) {
        return readOcr(actor, contractFile);
    }

    @PostMapping(value = "/manager/contract/ocr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('MANAGER')")
    @ResponseBody
    public ContractOcrView ocr(@AuthenticationPrincipal AuthenticatedUser actor,
                               @RequestParam(value = "contractFile", required = false) MultipartFile contractFile) {
        return readOcr(actor, contractFile);
    }

    @PostMapping("/admin/contract/stop-renewal")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminStopRenewal(@AuthenticationPrincipal AuthenticatedUser actor,
                                   @RequestParam(value = "contractId", required = false) Integer contractId,
                                   @RequestParam(value = "employeeId", required = false) Integer employeeId,
                                   @RequestParam(value = "reason", required = false) String reason,
                                   @RequestParam(value = "note", required = false) String note,
                                   RedirectAttributes redirectAttributes) {
        return stopRenewal(actor, contractId, employeeId, reason, note, redirectAttributes);
    }

    @PostMapping("/manager/contract/stop-renewal")
    @PreAuthorize("hasRole('MANAGER')")
    public String stopRenewal(@AuthenticationPrincipal AuthenticatedUser actor,
                              @RequestParam(value = "contractId", required = false) Integer contractId,
                              @RequestParam(value = "employeeId", required = false) Integer employeeId,
                              @RequestParam(value = "reason", required = false) String reason,
                              @RequestParam(value = "note", required = false) String note,
                              RedirectAttributes redirectAttributes) {
        boolean admin = actor != null && actor.getRoleName() == com.example.coffee_hrm.common.enums.RoleName.ADMIN;
        String back = admin
                ? "redirect:/admin/employees"
                : employeeId == null
                ? "redirect:/manager/employees"
                : "redirect:/manager/employees/" + employeeId + "?tab=contract";
        try {
            EmploymentContractResponse updated = contractRenewalService.stopRenewal(actor, contractId, reason, note);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã xác nhận không gia hạn hợp đồng " + updated.getContractCode() + ".");
            if (!admin && updated.getEmployeeId() != null) {
                return "redirect:/manager/employees/" + updated.getEmployeeId() + "?tab=contract";
            }
            return back;
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return back;
        }
    }

    @PostMapping("/admin/contract/create")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminCreate(@AuthenticationPrincipal AuthenticatedUser actor,
                              @Valid @ModelAttribute("form") CreateEmploymentContractRequest form,
                              BindingResult binding,
                              @RequestParam(value = "contractFile", required = false) MultipartFile contractFile,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        return saveContract(actor, form, binding, contractFile, null, null, model, redirectAttributes);
    }

    @PostMapping("/manager/contract/create")
    @PreAuthorize("hasRole('MANAGER')")
    public String create(@AuthenticationPrincipal AuthenticatedUser actor,
                         @Valid @ModelAttribute("form") CreateEmploymentContractRequest form,
                         BindingResult binding,
                         @RequestParam(value = "contractFile", required = false) MultipartFile contractFile,
                         @RequestParam(value = "renewal", required = false) String renewal,
                         @RequestParam(value = "contractId", required = false) Integer contractId,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        return saveContract(actor, form, binding, contractFile, renewal, contractId, model, redirectAttributes);
    }

    private String saveContract(AuthenticatedUser actor,
                                 CreateEmploymentContractRequest form,
                                 BindingResult binding,
                                 MultipartFile contractFile,
                                 String renewal,
                                 Integer contractId,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        boolean renewing = renewal != null && actor != null && actor.getRoleName() == com.example.coffee_hrm.common.enums.RoleName.MANAGER;
        rejectCrossField(form, binding);
        if (contractFile == null || contractFile.isEmpty()) {
            binding.reject("contractFile", "Vui lòng tải lên file hợp đồng đã ký.");
        }
        if (binding.hasErrors()) {
            model.addAttribute("errorMessage", "Chưa tạo được hợp đồng. Hãy kiểm tra các trường được đánh dấu.");
            return renderCreate(actor, model);
        }
        try {
            ContractFilePayload payload = new ContractFilePayload(contractFile.getOriginalFilename(), contractFile.getBytes());
            EmploymentContractResponse created = renewing
                    ? contractRenewalService.renew(actor, contractId, form, payload)
                    : contractService.create(actor, form, payload);
            redirectAttributes.addFlashAttribute("successMessage",
                    (renewing ? "Đã gia hạn hợp đồng " : "Đã tạo hợp đồng ") + created.getContractCode() + ".");
            return "redirect:" + createdContractPath(actor, created);
        } catch (BusinessException ex) {
            attachFieldError(ex, binding, model);
            return renderCreate(actor, model);
        } catch (IOException ex) {
            model.addAttribute("errorMessage", "Không đọc được file hợp đồng. Hãy chọn lại file.");
            return renderCreate(actor, model);
        }
    }

    private ContractOcrView readOcr(AuthenticatedUser actor, MultipartFile contractFile) {
        try {
            if (contractFile == null || contractFile.isEmpty()) {
                return failedOcr("Vui lòng chọn file hợp đồng.");
            }
            return contractOcrService.inspect(actor, contractFile.getOriginalFilename(), contractFile.getBytes());
        } catch (BusinessException ex) {
            return failedOcr(ex.getMessage());
        } catch (IOException ex) {
            return failedOcr("Không đọc được file hợp đồng.");
        }
    }

    private static void prepareCreateForm(Integer employeeId, Model model) {
        if (!model.containsAttribute("form")) {
            CreateEmploymentContractRequest form = new CreateEmploymentContractRequest();
            form.setEmployeeId(employeeId);
            model.addAttribute("form", form);
        }
    }

    private String redirectToEmployee(AuthenticatedUser actor, String role, Integer id,
                                      RedirectAttributes redirectAttributes) {
        try {
            EmploymentContractResponse contract = contractService.detail(actor, id);
            String path = contractListPath(role, contract);
            if ("admin".equals(role) && path.contains("/employees/") && !path.contains("tab=")) {
                path = path + "?tab=contract";
            }
            if ("staff".equals(role)) {
                path = "/profile?tab=contract";
            }
            return "redirect:" + path;
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return contractHome(role);
        }
    }

    private ResponseEntity<byte[]> file(AuthenticatedUser actor, Integer id, boolean inline) {
        ContractDownload download = contractService.download(actor, id);
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        try {
            mediaType = MediaType.parseMediaType(download.contentType());
        } catch (Exception ignored) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        String fileName = download.fileName() == null ? "" : download.fileName().toLowerCase();
        boolean viewInline = inline && (mediaType.includes(MediaType.APPLICATION_PDF) || fileName.endsWith(".pdf"));
        ContentDisposition disposition = (viewInline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(download.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(mediaType)
                .body(download.content());
    }

    private String renderCreate(AuthenticatedUser actor, Model model) {
        boolean admin = actor != null && actor.getRoleName() == com.example.coffee_hrm.common.enums.RoleName.ADMIN;
        model.addAttribute("navRole", admin ? "admin" : "manager");
        model.addAttribute("allowRenewal", !admin);
        model.addAttribute("contractAction", admin ? "/admin/contract/create" : "/manager/contract/create");
        List<ContractEmployeeOption> employees = List.of();
        try {
            employees = contractService.listEmployees(actor);
            model.addAttribute("formReady", true);
        } catch (BusinessException ex) {
            model.addAttribute("formReady", false);
            if (!model.containsAttribute("errorMessage")) {
                model.addAttribute("errorMessage", ex.getMessage());
            }
        }
        model.addAttribute("employees", employees);
        model.addAttribute("listPath", createCancelPath(actor, model, employees));
        return "contract/ContractCreate";
    }

    private static ContractOcrView failedOcr(String message) {
        ContractOcrView.Signature unknown = ContractOcrView.Signature.builder()
                .status("CHUA_XAC_DINH")
                .label("Chưa xác định")
                .location("Chưa xác định vị trí")
                .reason(message)
                .build();
        return ContractOcrView.builder()
                .status("FAILED")
                .message(message)
                .fields(List.of())
                .warnings(List.of())
                .managerSignature(unknown)
                .employeeSignature(unknown)
                .overall("CAN_KIEM_TRA")
                .allowsCreate(false)
                .build();
    }

    private static void rejectCrossField(CreateEmploymentContractRequest form, BindingResult binding) {
        if (form.getEndDate() != null && form.getStartDate() != null && form.getEndDate().isBefore(form.getStartDate())) {
            binding.rejectValue("endDate", "endDate", "Ngày kết thúc không được trước ngày bắt đầu hiệu lực.");
        }
        if (form.getEndDate() != null && form.getStartDate() != null
                && form.getEndDate().isAfter(form.getStartDate().plusMonths(ContractValidityRules.FIXED_TERM_MAX_MONTHS))) {
            binding.rejectValue("endDate", "endDate", "Hợp đồng xác định thời hạn không được dài quá 36 tháng.");
        }
        if (form.getSalary() != null && form.getSalary().compareTo(BigDecimal.ZERO) <= 0) {
            binding.rejectValue("salary", "salary", "Mức lương phải lớn hơn 0.");
        }
    }

    @ExceptionHandler(BusinessException.class)
    public String handleBusiness(BusinessException ex, HttpServletRequest request, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorMessage",
                ex.getMessage() == null ? "Không thể thực hiện thao tác với hợp đồng." : ex.getMessage());
        String uri = request.getRequestURI() == null ? "" : request.getRequestURI();
        if (uri.contains("/admin/contract")) {
            return "redirect:/admin/employees";
        }
        if (uri.contains("/staff/contract")) {
            return "redirect:/profile?tab=contract";
        }
        return "redirect:/manager/employees";
    }

    private static String contractHome(String role) {
        if ("admin".equals(role)) {
            return "redirect:/admin/employees";
        }
        if ("staff".equals(role)) {
            return "redirect:/profile?tab=contract";
        }
        return "redirect:/manager/employees";
    }

    private static String contractListPath(String role, EmploymentContractResponse contract) {
        if ("staff".equals(role)) {
            return "/profile?tab=contract";
        }
        if ("manager".equals(role) && contract.getEmployeeId() != null) {
            return "/manager/employees/" + contract.getEmployeeId() + "?tab=contract";
        }
        if ("admin".equals(role) && contract.getStoreId() != null && contract.getEmployeeId() != null) {
            return "/admin/stores/" + contract.getStoreId() + "/employees/" + contract.getEmployeeId();
        }
        return "/admin/employees";
    }

    private static String createdContractPath(AuthenticatedUser actor, EmploymentContractResponse created) {
        if (created == null || created.getEmployeeId() == null) {
            return actor != null && actor.getRoleName() == com.example.coffee_hrm.common.enums.RoleName.ADMIN
                    ? "/admin/employees"
                    : "/manager/employees";
        }
        if (actor != null && actor.getRoleName() == com.example.coffee_hrm.common.enums.RoleName.ADMIN) {
            if (created.getStoreId() != null) {
                return "/admin/stores/" + created.getStoreId() + "/employees/" + created.getEmployeeId() + "?tab=contract";
            }
            return "/admin/employees";
        }
        return "/manager/employees/" + created.getEmployeeId() + "?tab=contract";
    }

    private static String createCancelPath(AuthenticatedUser actor,
                                           Model model,
                                           List<ContractEmployeeOption> employees) {
        Object form = model.getAttribute("form");
        Integer employeeId = form instanceof CreateEmploymentContractRequest request ? request.getEmployeeId() : null;
        boolean admin = actor != null && actor.getRoleName() == com.example.coffee_hrm.common.enums.RoleName.ADMIN;
        if (!admin) {
            return employeeId == null ? "/manager/employees" : "/manager/employees/" + employeeId + "?tab=contract";
        }
        if (employeeId == null || employees == null) {
            return "/admin/employees";
        }
        Integer storeId = employees.stream()
                .filter(employee -> employeeId.equals(employee.getId()))
                .map(ContractEmployeeOption::getStoreId)
                .findFirst()
                .orElse(null);
        if (storeId == null) {
            return "/admin/employees";
        }
        return "/admin/stores/" + storeId + "/employees/" + employeeId + "?tab=contract";
    }

    private static void attachFieldError(BusinessException ex, BindingResult binding, Model model) {
        String message = ex.getMessage() == null ? "Không thể tạo hợp đồng." : ex.getMessage();
        if (message.contains("file") || message.contains("File") || message.contains("PDF") || message.contains("DOCX")) {
            binding.reject("contractFile", message);
        } else if (message.contains("chính bạn") || message.contains("nhân viên") || message.contains("Nhân viên")) {
            binding.rejectValue("employeeId", "employeeId", message);
        } else if (message.contains("kết thúc")) {
            binding.rejectValue("endDate", "endDate", message);
        } else if (message.contains("Ghi chú") || message.contains("ghi chú")) {
            binding.rejectValue("internalNote", "internalNote", message);
        } else if (message.contains("ca làm việc") || message.contains("ca/tuần")) {
            binding.rejectValue("weeklyShiftCount", "weeklyShiftCount", message);
        } else if (message.contains("bắt đầu")) {
            binding.rejectValue("startDate", "startDate", message);
        } else if (message.contains("lương") || message.contains("Lương")) {
            binding.rejectValue("salary", "salary", message);
        } else if (message.contains("file") || message.contains("File")) {
            binding.reject("contractFile", message);
        } else {
            model.addAttribute("errorMessage", message);
        }
    }
}

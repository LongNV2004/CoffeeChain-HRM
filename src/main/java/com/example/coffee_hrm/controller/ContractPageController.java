package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.request.ContractFilePayload;
import com.example.coffee_hrm.dto.request.CreateEmploymentContractRequest;
import com.example.coffee_hrm.dto.response.ContractDownload;
import com.example.coffee_hrm.dto.response.EmploymentContractResponse;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.EmploymentContractService;
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
        return "redirect:/profile";
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
        if (!model.containsAttribute("form")) {
            CreateEmploymentContractRequest form = new CreateEmploymentContractRequest();
            form.setEmployeeId(employeeId);
            model.addAttribute("form", form);
        }
        return renderCreate(actor, model);
    }

    @PostMapping("/manager/contract/create")
    @PreAuthorize("hasRole('MANAGER')")
    public String create(@AuthenticationPrincipal AuthenticatedUser actor,
                         @Valid @ModelAttribute("form") CreateEmploymentContractRequest form,
                         BindingResult binding,
                         @RequestParam(value = "contractFile", required = false) MultipartFile contractFile,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        rejectCrossField(form, binding);
        if (contractFile == null || contractFile.isEmpty()) {
            binding.reject("contractFile", "Vui lòng tải lên file hợp đồng đã ký.");
        }
        if (binding.hasErrors()) {
            model.addAttribute("errorMessage", "Chưa tạo được hợp đồng. Hãy kiểm tra các trường được đánh dấu.");
            return renderCreate(actor, model);
        }
        try {
            EmploymentContractResponse created = contractService.create(
                    actor, form, new ContractFilePayload(contractFile.getOriginalFilename(), contractFile.getBytes()));
            redirectAttributes.addFlashAttribute("successMessage", "Đã tạo hợp đồng " + created.getContractCode() + ".");
            Integer target = created.getEmployeeId();
            if (target == null) {
                return "redirect:/manager/employees";
            }
            return "redirect:/manager/employees/" + target + "?tab=contract";
        } catch (BusinessException ex) {
            attachFieldError(ex, binding, model);
            return renderCreate(actor, model);
        } catch (IOException ex) {
            model.addAttribute("errorMessage", "Không đọc được file hợp đồng. Hãy chọn lại file.");
            return renderCreate(actor, model);
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
                path = "/profile#contracts";
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
        model.addAttribute("navRole", "manager");
        model.addAttribute("eyebrow", "Cửa hàng — Quản lý");
        model.addAttribute("listPath", createCancelPath(model));
        try {
            model.addAttribute("employees", contractService.listEmployees(actor));
            model.addAttribute("formReady", true);
        } catch (BusinessException ex) {
            model.addAttribute("employees", List.of());
            model.addAttribute("formReady", false);
            if (!model.containsAttribute("errorMessage")) {
                model.addAttribute("errorMessage", ex.getMessage());
            }
        }
        return "contract/ContractCreate";
    }

    private static void rejectCrossField(CreateEmploymentContractRequest form, BindingResult binding) {
        if (form.getEndDate() != null && form.getStartDate() != null && form.getEndDate().isBefore(form.getStartDate())) {
            binding.rejectValue("endDate", "endDate", "Ngày kết thúc không được trước ngày bắt đầu hiệu lực.");
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
            return "redirect:/profile";
        }
        return "redirect:/manager/employees";
    }

    private static String contractHome(String role) {
        if ("admin".equals(role)) {
            return "redirect:/admin/employees";
        }
        if ("staff".equals(role)) {
            return "redirect:/profile";
        }
        return "redirect:/manager/employees";
    }

    private static String contractListPath(String role, EmploymentContractResponse contract) {
        if ("staff".equals(role)) {
            return "/profile";
        }
        if ("manager".equals(role) && contract.getEmployeeId() != null) {
            return "/manager/employees/" + contract.getEmployeeId() + "?tab=contract";
        }
        if ("admin".equals(role) && contract.getStoreId() != null && contract.getEmployeeId() != null) {
            return "/admin/stores/" + contract.getStoreId() + "/employees/" + contract.getEmployeeId();
        }
        return "/admin/employees";
    }

    private static String createCancelPath(Model model) {
        Object form = model.getAttribute("form");
        if (form instanceof CreateEmploymentContractRequest request && request.getEmployeeId() != null) {
            return "/manager/employees/" + request.getEmployeeId() + "?tab=contract";
        }
        return "/manager/employees";
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

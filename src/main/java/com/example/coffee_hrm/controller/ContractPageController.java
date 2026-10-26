package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.request.ContractFilePayload;
import com.example.coffee_hrm.dto.request.CreateEmploymentContractRequest;
import com.example.coffee_hrm.dto.response.ContractDownload;
import com.example.coffee_hrm.dto.response.EmploymentContractListView;
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
import java.time.LocalDate;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class ContractPageController {

    private final EmploymentContractService contractService;

    @GetMapping("/admin/contract")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminContract(@AuthenticationPrincipal AuthenticatedUser actor,
                                @RequestParam(required = false) String keyword,
                                @RequestParam(required = false) Integer storeId,
                                @RequestParam(required = false) String validity,
                                @RequestParam(required = false) LocalDate fromDate,
                                @RequestParam(required = false) LocalDate toDate,
                                @RequestParam(defaultValue = "1") int page,
                                Model model) {
        return renderList(actor, "admin", "Trụ sở — Admin", keyword, storeId, validity, fromDate, toDate, page, model);
    }

    @GetMapping("/manager/contract")
    @PreAuthorize("hasRole('MANAGER')")
    public String managerContract(@AuthenticationPrincipal AuthenticatedUser actor,
                                  @RequestParam(required = false) String keyword,
                                  @RequestParam(required = false) Integer storeId,
                                  @RequestParam(required = false) String validity,
                                  @RequestParam(required = false) LocalDate fromDate,
                                  @RequestParam(required = false) LocalDate toDate,
                                  @RequestParam(defaultValue = "1") int page,
                                  Model model) {
        return renderList(actor, "manager", "Cửa hàng — Quản lý", keyword, storeId, validity, fromDate, toDate, page, model);
    }

    @GetMapping("/staff/contract")
    @PreAuthorize("hasRole('STAFF')")
    public String staffContract(@AuthenticationPrincipal AuthenticatedUser actor,
                                @RequestParam(required = false) String keyword,
                                @RequestParam(required = false) String validity,
                                @RequestParam(required = false) LocalDate fromDate,
                                @RequestParam(required = false) LocalDate toDate,
                                @RequestParam(defaultValue = "1") int page,
                                Model model) {
        return renderList(actor, "staff", "Cá nhân — Nhân viên", keyword, null, validity, fromDate, toDate, page, model);
    }

    @GetMapping("/admin/contract/{id:\\d+}")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminDetail(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Integer id,
                              Model model, RedirectAttributes redirectAttributes) {
        return renderDetail(actor, "admin", "Trụ sở — Admin", id, model, redirectAttributes);
    }

    @GetMapping("/manager/contract/{id:\\d+}")
    @PreAuthorize("hasRole('MANAGER')")
    public String managerDetail(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Integer id,
                                Model model, RedirectAttributes redirectAttributes) {
        return renderDetail(actor, "manager", "Cửa hàng — Quản lý", id, model, redirectAttributes);
    }

    @GetMapping("/staff/contract/{id:\\d+}")
    @PreAuthorize("hasRole('STAFF')")
    public String staffDetail(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Integer id,
                              Model model, RedirectAttributes redirectAttributes) {
        return renderDetail(actor, "staff", "Cá nhân — Nhân viên", id, model, redirectAttributes);
    }

    @GetMapping("/admin/contract/{id:\\d+}/file")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> adminFile(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Integer id) {
        return file(actor, id);
    }

    @GetMapping("/manager/contract/{id:\\d+}/file")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<byte[]> managerFile(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Integer id) {
        return file(actor, id);
    }

    @GetMapping("/staff/contract/{id:\\d+}/file")
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<byte[]> staffFile(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Integer id) {
        return file(actor, id);
    }

    @GetMapping("/manager/contract/create")
    @PreAuthorize("hasRole('MANAGER')")
    public String createForm(@AuthenticationPrincipal AuthenticatedUser actor, Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new CreateEmploymentContractRequest());
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
            return "redirect:/manager/contract";
        } catch (BusinessException ex) {
            attachFieldError(ex, binding, model);
            return renderCreate(actor, model);
        } catch (IOException ex) {
            model.addAttribute("errorMessage", "Không đọc được file hợp đồng. Hãy chọn lại file.");
            return renderCreate(actor, model);
        }
    }

    private String renderList(AuthenticatedUser actor, String role, String eyebrow, String keyword, Integer storeId,
                              String validity, LocalDate fromDate, LocalDate toDate, int page, Model model) {
        model.addAttribute("navRole", role);
        model.addAttribute("eyebrow", eyebrow);
        model.addAttribute("listPath", "/" + role + "/contract");
        if (fromDate != null && toDate != null && toDate.isBefore(fromDate)) {
            model.addAttribute("errorMessage", "Khoảng ngày không hợp lệ.");
            fromDate = null;
            toDate = null;
        }
        try {
            model.addAttribute("view", contractService.search(actor, keyword, storeId, validity, fromDate, toDate, page));
        } catch (BusinessException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("view", EmploymentContractListView.empty("admin".equals(role), "manager".equals(role)));
        }
        return "contract/ContractPage";
    }

    private String renderDetail(AuthenticatedUser actor, String role, String eyebrow, Integer id,
                                Model model, RedirectAttributes redirectAttributes) {
        try {
            model.addAttribute("navRole", role);
            model.addAttribute("eyebrow", eyebrow);
            model.addAttribute("listPath", "/" + role + "/contract");
            model.addAttribute("contract", contractService.detail(actor, id));
            return "contract/ContractDetail";
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/" + role + "/contract";
        }
    }

    private ResponseEntity<byte[]> file(AuthenticatedUser actor, Integer id) {
        ContractDownload download = contractService.download(actor, id);
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        try {
            mediaType = MediaType.parseMediaType(download.contentType());
        } catch (Exception ignored) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        ContentDisposition disposition = ContentDisposition.attachment()
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
        model.addAttribute("listPath", "/manager/contract");
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
            binding.rejectValue("salary", "salary", "Mức lương cơ bản phải lớn hơn 0.");
        }
        if (form.getAllowance() != null && form.getAllowance().compareTo(BigDecimal.ZERO) < 0) {
            binding.rejectValue("allowance", "allowance", "Phụ cấp không được âm.");
        }
    }

    @ExceptionHandler(BusinessException.class)
    public String handleBusiness(BusinessException ex, HttpServletRequest request, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorMessage",
                ex.getMessage() == null ? "Không thể thực hiện thao tác với hợp đồng." : ex.getMessage());
        String uri = request.getRequestURI() == null ? "" : request.getRequestURI();
        if (uri.contains("/admin/contract")) {
            return "redirect:/admin/contract";
        }
        if (uri.contains("/staff/contract")) {
            return "redirect:/staff/contract";
        }
        return "redirect:/manager/contract";
    }

    private static void attachFieldError(BusinessException ex, BindingResult binding, Model model) {
        String message = ex.getMessage() == null ? "Không thể tạo hợp đồng." : ex.getMessage();
        if (message.contains("file") || message.contains("File") || message.contains("PDF") || message.contains("DOCX")) {
            binding.reject("contractFile", message);
        } else if (message.contains("chính bạn") || message.contains("nhân viên") || message.contains("Nhân viên")) {
            binding.rejectValue("employeeId", "employeeId", message);
        } else if (message.contains("kết thúc")) {
            binding.rejectValue("endDate", "endDate", message);
        } else if (message.contains("bắt đầu")) {
            binding.rejectValue("startDate", "startDate", message);
        } else if (message.contains("Chức danh") || message.contains("chức danh")) {
            binding.rejectValue("jobTitle", "jobTitle", message);
        } else if (message.contains("ca mỗi tuần")) {
            binding.rejectValue("weeklyShiftCount", "weeklyShiftCount", message);
        } else if (message.contains("Phụ cấp") || message.contains("phụ cấp")) {
            binding.rejectValue("allowance", "allowance", message);
        } else if (message.contains("lương") || message.contains("Lương")) {
            binding.rejectValue("salary", "salary", message);
        } else if (message.contains("Hình thức") || message.contains("hình thức")) {
            binding.rejectValue("paymentMethod", "paymentMethod", message);
        } else if (message.contains("file") || message.contains("File")) {
            binding.reject("contractFile", message);
        } else {
            model.addAttribute("errorMessage", message);
        }
    }
}

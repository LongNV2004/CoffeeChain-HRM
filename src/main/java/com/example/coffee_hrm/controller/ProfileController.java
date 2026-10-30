package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.request.ChangePasswordRequest;
import com.example.coffee_hrm.dto.request.UpdateProfileRequest;
import com.example.coffee_hrm.dto.response.EmploymentContractResponse;
import com.example.coffee_hrm.dto.response.MyProfileView;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.EmploymentContractService;
import com.example.coffee_hrm.service.ProfileService;
import com.example.coffee_hrm.service.support.EmployeeRecordSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/profile")
@PreAuthorize("hasAnyRole('MANAGER','STAFF')")
public class ProfileController {

    private final ProfileService profileService;
    private final EmploymentContractService contractService;

    @GetMapping
    public String profile(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        if (user == null) {
            return "redirect:/login";
        }
        return render(user, model);
    }

    @PostMapping
    public String updateProfile(@Valid @ModelAttribute("profileForm") UpdateProfileRequest profileForm,
                                BindingResult bindingResult,
                                @AuthenticationPrincipal AuthenticatedUser user,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        if (user == null) {
            return "redirect:/login";
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("errorMessage", firstError(bindingResult));
            model.addAttribute("profileFormOpen", true);
            return render(user, model);
        }
        try {
            profileService.updateProfile(user, profileForm);
            redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật hồ sơ cá nhân.");
            return "redirect:/profile";
        } catch (BusinessException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("profileFormOpen", true);
            return render(user, model);
        }
    }

    @PostMapping("/password")
    public String changePassword(@Valid @ModelAttribute("passwordForm") ChangePasswordRequest passwordForm,
                                 BindingResult bindingResult,
                                 @AuthenticationPrincipal AuthenticatedUser user,
                                 HttpServletRequest request,
                                 HttpServletResponse response,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        if (user == null) {
            return "redirect:/login";
        }
        if (!bindingResult.hasErrors()
                && passwordForm.getNewPassword() != null
                && !passwordForm.getNewPassword().equals(passwordForm.getConfirmPassword())) {
            bindingResult.rejectValue(
                    "confirmPassword",
                    "mismatch",
                    "Mật khẩu mới và xác nhận mật khẩu không trùng nhau.");
        }
        if (bindingResult.hasErrors()) {
            clearSecrets(passwordForm);
            model.addAttribute("errorMessage", firstError(bindingResult));
            model.addAttribute("passwordFormOpen", true);
            return render(user, model);
        }
        try {
            profileService.changePassword(user, passwordForm, request, response);
            redirectAttributes.addFlashAttribute("successMessage", "Đã đổi mật khẩu.");
            return "redirect:/profile";
        } catch (BusinessException ex) {
            clearSecrets(passwordForm);
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("passwordFormOpen", true);
            return render(user, model);
        }
    }

    private String render(AuthenticatedUser user, Model model) {
        if (!model.containsAttribute("passwordForm")) {
            model.addAttribute("passwordForm", new ChangePasswordRequest());
        }
        model.addAttribute("sidebarRole", sidebarRole(user));
        MyProfileView profile = null;
        try {
            profile = profileService.getProfile(user);
            model.addAttribute("profile", profile);
            if (profile.getSidebarRole() != null) {
                model.addAttribute("sidebarRole", profile.getSidebarRole());
            }
        } catch (BusinessException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
        }
        if (!model.containsAttribute("profileForm")) {
            model.addAttribute("profileForm", formFrom(profile));
        }
        List<EmploymentContractResponse> contracts = contractsOf(user, profile);
        model.addAttribute("contracts", contracts);
        model.addAttribute("contractRole", user.getRoleName() == RoleName.MANAGER ? "manager" : "staff");
        model.addAttribute("history", EmployeeRecordSupport.history(
                null,
                profile == null ? null : profile.getHireDate(),
                profile == null ? null : profile.getTerminationDate(),
                profile == null ? null : profile.getStoreName(),
                null,
                false,
                List.of(),
                contracts));
        return "profile/MyProfile";
    }

    private List<EmploymentContractResponse> contractsOf(AuthenticatedUser user, MyProfileView profile) {
        if (profile == null || profile.getEmployeeId() == null) {
            return List.of();
        }
        try {
            List<EmploymentContractResponse> loaded = contractService.listForEmployee(user, profile.getEmployeeId());
            return loaded == null ? List.of() : loaded;
        } catch (BusinessException ex) {
            return List.of();
        }
    }

    private UpdateProfileRequest formFrom(MyProfileView profile) {
        if (profile == null) {
            return new UpdateProfileRequest();
        }
        return UpdateProfileRequest.builder()
                .fullName(profile.getFullName())
                .phone(profile.getPhone())
                .email(profile.getEmail())
                .address(profile.getAddress())
                .build();
    }

    private void clearSecrets(ChangePasswordRequest passwordForm) {
        if (passwordForm == null) {
            return;
        }
        passwordForm.setCurrentPassword(null);
        passwordForm.setNewPassword(null);
        passwordForm.setConfirmPassword(null);
    }

    private String firstError(BindingResult bindingResult) {
        FieldError fieldError = bindingResult.getFieldError();
        if (fieldError != null && fieldError.getDefaultMessage() != null && !fieldError.getDefaultMessage().isBlank()) {
            return fieldError.getDefaultMessage();
        }
        return "Thông tin đổi mật khẩu chưa hợp lệ.";
    }

    private String sidebarRole(AuthenticatedUser user) {
        return user.getRoleName() == RoleName.MANAGER ? "manager" : "staff";
    }
}

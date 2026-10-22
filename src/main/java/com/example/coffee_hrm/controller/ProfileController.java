package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.request.ChangePasswordRequest;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.ProfileService;
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

@Controller
@RequiredArgsConstructor
@RequestMapping("/profile")
@PreAuthorize("hasAnyRole('MANAGER','STAFF')")
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping
    public String profile(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        if (user == null) {
            return "redirect:/login";
        }
        return render(user, model);
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
        try {
            var profile = profileService.getProfile(user);
            model.addAttribute("profile", profile);
            if (profile.getSidebarRole() != null) {
                model.addAttribute("sidebarRole", profile.getSidebarRole());
            }
        } catch (BusinessException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
        }
        return "profile/MyProfile";
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

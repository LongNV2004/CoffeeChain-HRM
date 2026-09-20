package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.dto.request.LoginRequest;
import com.example.coffee_hrm.dto.response.LoginResponse;
import com.example.coffee_hrm.security.RoleBasedRedirector;
import com.example.coffee_hrm.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RoleBasedRedirector roleBasedRedirector;

    @GetMapping("/login")
    public String loginPage(@RequestParam(name = "loginError", required = false) String loginError,
                            @RequestParam(name = "errorCode", required = false) String errorCode,
                            @RequestParam(name = "logoutSuccess", required = false) String logoutSuccess,
                            Authentication authentication,
                            Model model) {
        try {
            String dashboard = authenticatedDashboard(authentication);
            if (dashboard != null) {
                return "redirect:" + dashboard;
            }
        } catch (Exception ex) {
            // Bỏ qua lỗi session/remember-me cũ để người dùng vẫn vào được trang login
        }
        if (!model.containsAttribute("loginRequest")) {
            model.addAttribute("loginRequest", new LoginRequest());
        }
        model.addAttribute("loginError", loginError != null);
        model.addAttribute("logoutSuccess", logoutSuccess != null);
        model.addAttribute("errorCode", errorCode);
        return "LandingPage";
    }

    @PostMapping("/login")
    public String login(@Valid @ModelAttribute("loginRequest") LoginRequest loginRequest,
                        BindingResult bindingResult,
                        HttpServletRequest request,
                        HttpServletResponse response,
                        Model model) {
        if (bindingResult.hasErrors()) {
            loginRequest.setPassword(null);
            applyFieldErrors(bindingResult, model);
            model.addAttribute("loginValidationMessage", firstErrorMessage(bindingResult));
            return "LandingPage";
        }

        try {
            LoginResponse loggedIn = authService.authenticate(loginRequest, request, response);
            String dashboard = roleBasedRedirector.resolve(loggedIn.getRoleName());
            if (dashboard == null) {
                model.addAttribute("loginError", true);
                model.addAttribute("errorCode", "unauthorized");
                return "LandingPage";
            }
            return "redirect:" + dashboard;
        } catch (DisabledException ex) {
            loginRequest.setPassword(null);
            model.addAttribute("loginError", true);
            model.addAttribute("errorCode", "inactive");
            return "LandingPage";
        } catch (BadCredentialsException ex) {
            loginRequest.setPassword(null);
            model.addAttribute("loginError", true);
            return "LandingPage";
        } catch (Exception ex) {
            loginRequest.setPassword(null);
            model.addAttribute("loginError", true);
            model.addAttribute("loginValidationMessage", "Lỗi CSDL / Hệ thống: " + ex.getMessage());
            return "LandingPage";
        }
    }

    @PostMapping("/logout")
    public String logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(request, response);
        return "redirect:/login?logoutSuccess=true";
    }

    private String authenticatedDashboard(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return roleBasedRedirector.resolve(authentication);
    }

    private void applyFieldErrors(BindingResult bindingResult, Model model) {
        FieldError usernameError = bindingResult.getFieldError("username");
        if (usernameError != null) {
            model.addAttribute("usernameError", usernameError.getDefaultMessage());
        }
        FieldError passwordError = bindingResult.getFieldError("password");
        if (passwordError != null) {
            model.addAttribute("passwordError", passwordError.getDefaultMessage());
        }
    }

    private String firstErrorMessage(BindingResult bindingResult) {
        if (!bindingResult.getAllErrors().isEmpty()) {
            return bindingResult.getAllErrors().getFirst().getDefaultMessage();
        }
        return "Vui lòng kiểm tra lại thông tin đăng nhập.";
    }
}

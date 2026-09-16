package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.dto.request.LoginRequest;
import com.example.coffee_hrm.security.RoleBasedRedirector;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class LandingPageController {

    private final RoleBasedRedirector roleBasedRedirector;

    @GetMapping({"/", "/coffee"})
    public String landingPage(@RequestParam(name = "loginError", required = false) String loginError,
                              @RequestParam(name = "errorCode", required = false) String errorCode,
                              @RequestParam(name = "logoutSuccess", required = false) String logoutSuccess,
                              Authentication authentication,
                              Model model) {
        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            String dashboard = roleBasedRedirector.resolve(authentication);
            if (dashboard != null) {
                return "redirect:" + dashboard;
            }
        }

        if (!model.containsAttribute("loginRequest")) {
            model.addAttribute("loginRequest", new LoginRequest());
        }
        model.addAttribute("loginError", loginError != null || Boolean.TRUE.equals(model.getAttribute("loginError")));
        model.addAttribute("logoutSuccess", logoutSuccess != null);
        model.addAttribute("errorCode", errorCode);
        return "LandingPage";
    }
}

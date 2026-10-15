package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.store.StoreAccessMessages;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.security.RoleBasedRedirector;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class InactiveStoreController {

    private final RoleBasedRedirector roleBasedRedirector;

    @GetMapping("/store-inactive")
    public String inactiveStore(@AuthenticationPrincipal AuthenticatedUser user,
                                Authentication authentication,
                                Model model) {
        if (user == null) {
            return "redirect:/login";
        }
        String home = roleBasedRedirector.resolve(authentication);
        model.addAttribute("message", StoreAccessMessages.INACTIVE);
        model.addAttribute("homeUrl", home != null ? home : "/login");
        return "store-inactive";
    }
}

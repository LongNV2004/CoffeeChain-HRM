package com.example.coffee_hrm.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;

public class CoffeeRememberMeServices extends TokenBasedRememberMeServices {

    public static final String ISSUE_COOKIE_ATTRIBUTE = "ISSUE_REMEMBER_ME";

    public CoffeeRememberMeServices(String key, UserDetailsService userDetailsService) {
        super(key, userDetailsService);
    }

    @Override
    protected boolean rememberMeRequested(HttpServletRequest request, String parameter) {
        if (Boolean.TRUE.equals(request.getAttribute(ISSUE_COOKIE_ATTRIBUTE))) {
            return true;
        }
        return super.rememberMeRequested(request, parameter);
    }
}

package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
@RequiredArgsConstructor
public class NotificationModelAdvice {

    private final NotificationService notificationService;

    @ModelAttribute("unreadNotificationCount")
    public long unreadNotificationCount(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return 0L;
        }
        return notificationService.countUnread(user);
    }
}

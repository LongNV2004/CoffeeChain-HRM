package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.NotificationService;
import com.example.coffee_hrm.service.RecruitmentRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
@RequiredArgsConstructor
public class NotificationModelAdvice {

    private final NotificationService notificationService;
    private final RecruitmentRequestService recruitmentRequestService;

    @ModelAttribute("unreadNotificationCount")
    public long unreadNotificationCount(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return 0L;
        }
        return notificationService.countUnread(user);
    }

    @ModelAttribute("pendingRecruitments")
    public int pendingRecruitments(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return 0;
        }
        if (user.getRoleName() != RoleName.ADMIN) {
            return 0;
        }
        Integer count = recruitmentRequestService.countPendingRequests();
        return count == null ? 0 : count;
    }
}

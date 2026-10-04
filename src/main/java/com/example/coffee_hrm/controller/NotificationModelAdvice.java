package com.example.coffee_hrm.controller;

import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.NotificationService;
import com.example.coffee_hrm.service.RecruitmentRequestService;
import com.example.coffee_hrm.service.TrainingService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
@RequiredArgsConstructor
public class NotificationModelAdvice {

    private final NotificationService notificationService;
    private final RecruitmentRequestService recruitmentRequestService;
    private final TrainingService trainingService;

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
        return recruitmentRequestService.countPendingRequests();
    }

    @ModelAttribute("activeTrainingCount")
    public long activeTrainingCount(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return 0L;
        }
        return trainingService.countOngoingClassesForEmployee(user);
    }
}

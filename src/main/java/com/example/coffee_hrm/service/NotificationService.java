package com.example.coffee_hrm.service;

import com.example.coffee_hrm.common.enums.NotificationType;
import com.example.coffee_hrm.dto.response.NotificationResponse;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.security.AuthenticatedUser;

import java.util.List;

public interface NotificationService {

    void notifyUsers(List<User> recipients,
                     String title,
                     String message,
                     NotificationType type,
                     String referenceType,
                     Integer referenceId);

    List<NotificationResponse> listMine(AuthenticatedUser user);

    long countUnread(AuthenticatedUser user);

    void markAsRead(Integer notificationId, AuthenticatedUser user);

    void markAllAsRead(AuthenticatedUser user);
}

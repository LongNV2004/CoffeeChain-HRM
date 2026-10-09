package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.NotificationType;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.dto.response.NotificationResponse;
import com.example.coffee_hrm.entity.Notification;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.NotificationRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    @Override
    @Transactional
    public void notifyUsers(List<User> recipients,
                            String title,
                            String message,
                            NotificationType type,
                            String referenceType,
                            Integer referenceId) {
        if (recipients == null || recipients.isEmpty()) {
            return;
        }
        List<Notification> notifications = recipients.stream()
                .filter(user -> user != null && user.getId() != null)
                .map(user -> Notification.builder()
                        .recipient(user)
                        .title(title)
                        .message(message)
                        .type(type)
                        .isRead(false)
                        .referenceType(referenceType)
                        .referenceId(referenceId)
                        .build())
                .toList();
        if (!notifications.isEmpty()) {
            notificationRepository.saveAll(notifications);
        }
    }

    @Override
    public List<NotificationResponse> listMine(AuthenticatedUser user) {
        requireUser(user);
        return notificationRepository.findByRecipient_IdOrderByCreatedAtDesc(user.getUserId()).stream()
                .map(notification -> toResponse(notification, user))
                .toList();
    }

    @Override
    public long countUnread(AuthenticatedUser user) {
        if (user == null || user.getUserId() == null) {
            return 0;
        }
        return notificationRepository.countByRecipient_IdAndIsReadFalse(user.getUserId());
    }

    @Override
    @Transactional
    public void markAsRead(Integer notificationId, AuthenticatedUser user) {
        requireUser(user);
        Notification notification = notificationRepository.findByIdAndRecipient_Id(notificationId, user.getUserId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy thông báo."));
        notification.setIsRead(true);
    }

    @Override
    @Transactional
    public void markAllAsRead(AuthenticatedUser user) {
        requireUser(user);
        notificationRepository.markAllReadByRecipientId(user.getUserId());
    }

    private void requireUser(AuthenticatedUser user) {
        if (user == null || user.getUserId() == null) {
            throw new BusinessException("Vui lòng đăng nhập để xem thông báo.");
        }
    }

    private NotificationResponse toResponse(Notification notification, AuthenticatedUser user) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .type(notification.getType())
                .read(Boolean.TRUE.equals(notification.getIsRead()))
                .referenceType(notification.getReferenceType())
                .referenceId(notification.getReferenceId())
                .actionUrl(resolveActionUrl(notification, user))
                .createdAt(notification.getCreatedAt())
                .build();
    }

    private String resolveActionUrl(Notification notification, AuthenticatedUser user) {
        if (notification.getReferenceType() == null || user.getRoleName() == null) {
            return null;
        }
        return switch (notification.getReferenceType()) {
            case "TRAINING_CLASS" -> switch (user.getRoleName()) {
                case ADMIN -> "/admin/training";
                case MANAGER -> "/training";
                case STAFF -> notification.getReferenceId() == null
                        ? "/training/staff"
                        : "/training/staff/classes/" + notification.getReferenceId();
            };
            case "WORK_AVAILABILITY" -> switch (user.getRoleName()) {
                case MANAGER -> "/availability/manager";
                case STAFF -> notification.getType() == NotificationType.WORK_AVAILABILITY_REJECTED
                        ? "/availability"
                        : "/schedule/staff";
                case ADMIN -> null;
            };
            case "RECRUITMENT_REQUEST" -> switch (user.getRoleName()) {
                case ADMIN -> notification.getReferenceId() == null
                        ? "/admin/recruitment"
                        : "/admin/recruitment/" + notification.getReferenceId();
                case MANAGER -> notification.getReferenceId() == null
                        ? "/manager/recruitment"
                        : "/manager/recruitment/" + notification.getReferenceId();
                case STAFF -> null;
            };
            default -> null;
        };
    }
}

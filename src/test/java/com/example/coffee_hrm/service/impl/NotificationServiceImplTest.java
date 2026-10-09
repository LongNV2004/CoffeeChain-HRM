package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.NotificationType;
import com.example.coffee_hrm.common.enums.RoleName;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.entity.Notification;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.User;
import com.example.coffee_hrm.repository.NotificationRepository;
import com.example.coffee_hrm.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    private AuthenticatedUser owner;
    private User ownerEntity;

    @BeforeEach
    void setUp() {
        ownerEntity = User.builder()
                .id(2)
                .username("manager")
                .passwordHash("hash")
                .role(Role.builder().id(2).roleName(com.example.coffee_hrm.common.enums.RoleName.MANAGER).build())
                .isActive(true)
                .build();
        owner = AuthenticatedUser.from(ownerEntity);
    }

    @Test
    void listMineReturnsOnlyPersistedItemsForCurrentUser() {
        when(notificationRepository.findByRecipient_IdOrderByCreatedAtDesc(2)).thenReturn(List.of(
                Notification.builder()
                        .id(9)
                        .recipient(ownerEntity)
                        .title("Lớp đào tạo đã được duyệt")
                        .message("Lớp \"Ca sáng\" đã được Admin duyệt.")
                        .type(NotificationType.TRAINING_CLASS_APPROVED)
                        .isRead(false)
                        .createdAt(LocalDateTime.now())
                        .build()));

        var items = notificationService.listMine(owner);

        assertEquals(1, items.size());
        assertEquals(9, items.getFirst().getId());
        assertEquals("Lớp đào tạo đã được duyệt", items.getFirst().getTitle());
        assertNull(items.getFirst().getActionUrl());
    }

    @Test
    void listMineMapsKnownReferenceToExistingPage() {
        when(notificationRepository.findByRecipient_IdOrderByCreatedAtDesc(2)).thenReturn(List.of(
                Notification.builder()
                        .id(11)
                        .recipient(ownerEntity)
                        .title("STAFF đăng ký lịch rảnh")
                        .message("Xem đề xuất tại Lịch rảnh nhân viên.")
                        .type(NotificationType.WORK_AVAILABILITY_SUBMITTED)
                        .isRead(false)
                        .referenceType("WORK_AVAILABILITY")
                        .referenceId(4)
                        .createdAt(LocalDateTime.now())
                        .build()));

        var items = notificationService.listMine(owner);

        assertEquals("/availability/manager", items.getFirst().getActionUrl());
    }

    @Test
    void approvedAvailabilityNotificationOpensStaffSchedule() {
        User staff = User.builder()
                .id(8)
                .username("staff")
                .passwordHash("hash")
                .role(Role.builder().id(3).roleName(RoleName.STAFF).build())
                .isActive(true)
                .build();
        when(notificationRepository.findByRecipient_IdOrderByCreatedAtDesc(8)).thenReturn(List.of(
                Notification.builder()
                        .id(16)
                        .recipient(staff)
                        .title("Lịch làm việc đã được duyệt")
                        .message("Quản lý đã duyệt lịch làm việc.")
                        .type(NotificationType.WORK_AVAILABILITY_APPROVED)
                        .isRead(false)
                        .referenceType("WORK_AVAILABILITY")
                        .referenceId(20)
                        .createdAt(LocalDateTime.now())
                        .build()));

        var items = notificationService.listMine(AuthenticatedUser.from(staff));

        assertEquals("/schedule/staff", items.getFirst().getActionUrl());
    }

    @Test
    void rejectedAvailabilityNotificationOpensStaffRegistration() {
        User staff = User.builder()
                .id(8)
                .username("staff")
                .passwordHash("hash")
                .role(Role.builder().id(3).roleName(RoleName.STAFF).build())
                .isActive(true)
                .build();
        when(notificationRepository.findByRecipient_IdOrderByCreatedAtDesc(8)).thenReturn(List.of(
                Notification.builder()
                        .id(17)
                        .recipient(staff)
                        .title("Lịch làm việc bị từ chối")
                        .message("Quản lý đã từ chối đăng ký.")
                        .type(NotificationType.WORK_AVAILABILITY_REJECTED)
                        .isRead(false)
                        .referenceType("WORK_AVAILABILITY")
                        .referenceId(20)
                        .createdAt(LocalDateTime.now())
                        .build()));

        var items = notificationService.listMine(AuthenticatedUser.from(staff));

        assertEquals("/availability", items.getFirst().getActionUrl());
    }

    @Test
    void trainingEnrollmentNotificationOpensStaffClassDetail() {
        User staff = User.builder()
                .id(8)
                .username("staff")
                .passwordHash("hash")
                .role(Role.builder().id(3).roleName(RoleName.STAFF).build())
                .isActive(true)
                .build();
        when(notificationRepository.findByRecipient_IdOrderByCreatedAtDesc(8)).thenReturn(List.of(
                Notification.builder()
                        .id(15)
                        .recipient(staff)
                        .title("Bạn được thêm vào lớp đào tạo")
                        .message("Bạn đã được thêm vào lớp đào tạo Pha Chế 2.")
                        .type(NotificationType.TRAINING_CLASS_ENROLLED)
                        .isRead(false)
                        .referenceType("TRAINING_CLASS")
                        .referenceId(22)
                        .createdAt(LocalDateTime.now())
                        .build()));

        var items = notificationService.listMine(AuthenticatedUser.from(staff));

        assertEquals("/training/staff/classes/22", items.getFirst().getActionUrl());
        assertEquals("Bạn đã được thêm vào lớp đào tạo Pha Chế 2.", items.getFirst().getMessage());
    }

    @Test
    void markAsReadRejectsNotificationOfAnotherUser() {
        when(notificationRepository.findByIdAndRecipient_Id(5, 2)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> notificationService.markAsRead(5, owner));
        assertEquals("Không tìm thấy thông báo.", ex.getMessage());
    }

    @Test
    void notifyUsersPersistsRecipientAndContent() {
        notificationService.notifyUsers(
                List.of(ownerEntity),
                "Lớp đào tạo chờ duyệt",
                "Manager đã tạo lớp.",
                NotificationType.TRAINING_CLASS_CREATED,
                "TRAINING_CLASS",
                22);

        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationRepository).saveAll(captor.capture());
        Notification saved = captor.getValue().getFirst();
        assertEquals(2, saved.getRecipient().getId());
        assertEquals("Lớp đào tạo chờ duyệt", saved.getTitle());
        assertEquals(Boolean.FALSE, saved.getIsRead());
        assertEquals(22, saved.getReferenceId());
    }
}

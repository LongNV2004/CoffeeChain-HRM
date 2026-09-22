package com.example.coffee_hrm.repository;

import com.example.coffee_hrm.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Integer> {

    List<Notification> findByRecipient_IdOrderByCreatedAtDesc(Integer userId);

    long countByRecipient_IdAndIsReadFalse(Integer userId);

    Optional<Notification> findByIdAndRecipient_Id(Integer id, Integer userId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.recipient.id = :userId AND n.isRead = false")
    int markAllReadByRecipientId(@Param("userId") Integer userId);
}

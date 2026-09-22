package com.example.coffee_hrm.dto.response;

import com.example.coffee_hrm.common.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponse {

    private Integer id;
    private String title;
    private String message;
    private NotificationType type;
    private boolean read;
    private String referenceType;
    private Integer referenceId;
    private LocalDateTime createdAt;
}

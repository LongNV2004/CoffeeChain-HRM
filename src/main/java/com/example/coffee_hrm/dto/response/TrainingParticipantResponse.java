package com.example.coffee_hrm.dto.response;

import com.example.coffee_hrm.common.enums.TrainingParticipantStatus;
import com.example.coffee_hrm.common.enums.TrainingResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrainingParticipantResponse {

    private Integer id;
    private Integer sessionId;
    private Integer courseId;
    private String courseName;
    private LocalDate sessionDate;
    private Integer employeeId;
    private String employeeName;
    private TrainingParticipantStatus status;
    private BigDecimal score;
    private TrainingResult result;
    private LocalDateTime createdAt;
}

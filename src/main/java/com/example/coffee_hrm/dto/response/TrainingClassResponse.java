package com.example.coffee_hrm.dto.response;

import com.example.coffee_hrm.common.enums.TrainingClassStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrainingClassResponse {

    private Integer id;
    private Integer skillId;
    private String skillName;
    private Integer storeId;
    private String storeName;
    private String className;
    private String trainer;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String location;
    private Integer maxParticipants;
    private String notes;
    private TrainingClassStatus status;
    private String createdByName;
    private List<TrainingClassStudentResponse> students;
    private String approvedByName;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
}

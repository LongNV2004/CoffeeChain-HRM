package com.example.coffee_hrm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrainingSessionResponse {

    private Integer id;
    private Integer courseId;
    private String courseName;
    private Integer storeId;
    private LocalDate sessionDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String trainer;
    private LocalDateTime createdAt;
}

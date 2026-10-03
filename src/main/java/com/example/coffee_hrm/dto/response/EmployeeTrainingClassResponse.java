package com.example.coffee_hrm.dto.response;

import com.example.coffee_hrm.common.enums.TrainingResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeTrainingClassResponse {

    private Integer id;
    private String className;
    private String skillName;
    private String storeName;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String trainer;
    private String supervisorName;
    private String location;
    private String notes;
    private String participationStatus;
    private boolean ended;
    private TrainingResult result;
    private String resultLabel;
    private String evaluationNote;
}

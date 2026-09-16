package com.example.coffee_hrm.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class UpdateTrainingSessionRequest {

    @NotNull
    private LocalDate sessionDate;

    private LocalTime startTime;

    private LocalTime endTime;

    @Size(max = 100)
    private String trainer;
}

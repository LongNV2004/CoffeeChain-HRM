package com.example.coffee_hrm.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateTrainingParticipantRequest {

    @NotNull
    private Integer sessionId;

    @NotNull
    private Integer employeeId;
}

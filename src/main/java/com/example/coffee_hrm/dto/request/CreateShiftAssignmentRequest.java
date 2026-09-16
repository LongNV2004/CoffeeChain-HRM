package com.example.coffee_hrm.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateShiftAssignmentRequest {

    @NotNull
    private Integer shiftId;

    @NotNull
    private Integer employeeId;

    @NotNull
    private LocalDate workDate;
}

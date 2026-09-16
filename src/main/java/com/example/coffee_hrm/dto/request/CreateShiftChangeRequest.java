package com.example.coffee_hrm.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateShiftChangeRequest {

    @NotNull
    private Integer assignmentId;

    @Size(max = 255)
    private String reason;
}

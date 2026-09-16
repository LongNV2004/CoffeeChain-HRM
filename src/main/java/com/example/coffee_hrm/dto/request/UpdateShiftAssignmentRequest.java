package com.example.coffee_hrm.dto.request;

import com.example.coffee_hrm.common.enums.AssignmentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateShiftAssignmentRequest {

    @NotNull
    private AssignmentStatus status;
}

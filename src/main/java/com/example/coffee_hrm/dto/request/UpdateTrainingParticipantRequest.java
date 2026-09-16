package com.example.coffee_hrm.dto.request;

import com.example.coffee_hrm.common.enums.TrainingParticipantStatus;
import com.example.coffee_hrm.common.enums.TrainingResult;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateTrainingParticipantRequest {

    private TrainingParticipantStatus status;

    @DecimalMin("0")
    @DecimalMax("100")
    private BigDecimal score;

    private TrainingResult result;
}

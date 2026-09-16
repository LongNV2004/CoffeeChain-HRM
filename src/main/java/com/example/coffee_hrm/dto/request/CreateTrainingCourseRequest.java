package com.example.coffee_hrm.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateTrainingCourseRequest {

    @NotNull
    private Integer storeId;

    @NotBlank
    @Size(max = 100)
    private String courseName;

    @Size(max = 500)
    private String description;

    @DecimalMin("0")
    @DecimalMax("100")
    private BigDecimal passScore;
}

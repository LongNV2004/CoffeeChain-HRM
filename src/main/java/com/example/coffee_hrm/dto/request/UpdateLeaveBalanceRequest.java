package com.example.coffee_hrm.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateLeaveBalanceRequest {

    @NotNull
    @Min(0)
    private Integer totalDays;

    @NotNull
    @Min(0)
    private Integer usedDays;
}

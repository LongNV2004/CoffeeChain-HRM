package com.example.coffee_hrm.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoreOperatingHourRequest {

    /** 0 = Sunday, 1 = Monday, ..., 6 = Saturday. */
    @NotNull
    @Min(0)
    @Max(6)
    private Short dayOfWeek;

    private LocalTime openTime;

    private LocalTime closeTime;

    @NotNull
    private Boolean isOpen;
}

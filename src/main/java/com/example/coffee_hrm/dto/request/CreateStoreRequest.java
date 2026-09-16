package com.example.coffee_hrm.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateStoreRequest {

    @NotBlank
    @Size(max = 100)
    private String storeName;

    @NotBlank
    @Size(max = 255)
    private String address;

    private Integer managerId;

    @Min(0)
    private Integer totalLeaveDays;

    @Valid
    private List<StoreOperatingHourRequest> operatingHours;
}

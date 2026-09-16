package com.example.coffee_hrm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoreResponse {

    private Integer id;
    private String storeName;
    private String address;
    private Integer managerId;
    private String managerName;
    private Integer totalLeaveDays;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private List<StoreOperatingHourResponse> operatingHours;
}

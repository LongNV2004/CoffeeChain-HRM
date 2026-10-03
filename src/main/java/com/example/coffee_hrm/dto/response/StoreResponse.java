package com.example.coffee_hrm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoreResponse {

    private Integer id;
    private String storeName;
    private String address;
    private Integer totalLeaveDays;
    private Boolean isActive;
    private long employeeCount;
    private String managerName;
}

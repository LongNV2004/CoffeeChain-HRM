package com.example.coffee_hrm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
    private BigDecimal latitude;
    private BigDecimal longitude;
    private LocalDateTime locationUpdatedAt;
}

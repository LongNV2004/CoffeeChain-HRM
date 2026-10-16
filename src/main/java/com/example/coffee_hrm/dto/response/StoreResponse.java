package com.example.coffee_hrm.dto.response;

import com.example.coffee_hrm.common.enums.StoreStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
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
    private StoreStatus status;
    private String statusLabel;
    private String stateKey;
    private LocalDate openingDate;
    private long employeeCount;
    private String managerName;
    private String managerEmail;
    private String managerPhone;
    private String managerStatusLabel;
    private String managerAssignmentLabel;
    private boolean managerCredentialsPending;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private LocalDateTime locationUpdatedAt;
}

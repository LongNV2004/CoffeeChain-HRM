package com.example.coffee_hrm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveBalanceResponse {

    private Integer employeeId;
    private String employeeName;
    private Short year;
    private Integer totalDays;
    private Integer usedDays;
    private Integer remainingDays;
}

package com.example.coffee_hrm.dto.response;

import com.example.coffee_hrm.common.enums.ApprovalStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveRequestResponse {

    private Integer id;
    private Integer employeeId;
    private String employeeName;
    private LocalDate startDate;
    private LocalDate endDate;
    private String reason;
    private ApprovalStatus status;
    private Integer approvedById;
    private String approvedByName;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedDate;
}

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
public class ShiftChangeRequestResponse {

    private Integer id;
    private Integer assignmentId;
    private Integer employeeId;
    private String employeeName;
    private LocalDate workDate;
    private String shiftName;
    private String reason;
    private ApprovalStatus status;
    private LocalDateTime requestDate;
    private Integer resolvedById;
    private String resolvedByName;
    private LocalDateTime resolvedDate;
}

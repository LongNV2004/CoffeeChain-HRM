package com.example.coffee_hrm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResignationDecisionResult {

    private String employeeName;
    private LocalDate approvedTerminationDate;
    private boolean emailSent;
    private boolean terminationApplied;
    private int processedCount;
}

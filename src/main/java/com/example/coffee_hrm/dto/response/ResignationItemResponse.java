package com.example.coffee_hrm.dto.response;

import com.example.coffee_hrm.common.enums.ResignationItemStatus;
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
public class ResignationItemResponse {

    private Integer id;
    private Integer employeeId;
    private String employeeName;
    private String citizenId;
    private String positionLabel;
    private Integer storeId;
    private String storeName;
    private String hireDate;
    private String currentStatusLabel;
    private LocalDate proposedTerminationDate;
    private LocalDate approvedTerminationDate;
    private String reason;
    private String note;
    private ResignationItemStatus status;
    private String statusLabel;
    private String rejectReason;
    private LocalDateTime reviewedAt;
    private String reviewedByName;
    private boolean applied;
    private boolean decisionNoticeSent;
}

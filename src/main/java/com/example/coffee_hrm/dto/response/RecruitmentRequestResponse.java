package com.example.coffee_hrm.dto.response;

import com.example.coffee_hrm.common.enums.RecruitmentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecruitmentRequestResponse {

    private Integer id;
    private Integer storeId;
    private String storeName;
    private Integer managerId;
    private String managerName;
    private String fullName;
    private String email;
    private String phone;
    private String address;
    private RecruitmentStatus status;
    private String statusLabel;
    private String rejectReason;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
    private Integer createdEmployeeId;
}

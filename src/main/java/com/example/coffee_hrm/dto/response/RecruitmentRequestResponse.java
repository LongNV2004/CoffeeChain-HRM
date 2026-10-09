package com.example.coffee_hrm.dto.response;

import com.example.coffee_hrm.common.enums.RecruitmentProposalStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecruitmentRequestResponse {

    private Integer id;
    private String title;
    private String note;
    private Integer storeId;
    private String storeName;
    private Integer managerId;
    private String managerName;
    private RecruitmentProposalStatus status;
    private String statusLabel;
    private LocalDateTime createdAt;
    private int totalCandidates;
    private int pendingCount;
    private int approvedCount;
    private int rejectedCount;
    @Builder.Default
    private List<RecruitmentCandidateResponse> candidates = new ArrayList<>();
}

package com.example.coffee_hrm.dto.response;

import com.example.coffee_hrm.common.enums.ResignationProposalStatus;
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
public class ResignationProposalResponse {

    private Integer id;
    private String title;
    private String note;
    private Integer storeId;
    private String storeName;
    private Integer managerId;
    private String managerName;
    private ResignationProposalStatus status;
    private String statusLabel;
    private String outcomeLabel;
    private LocalDateTime createdAt;
    private int totalCount;
    private int pendingCount;
    private int approvedCount;
    private int rejectedCount;
    @Builder.Default
    private List<ResignationItemResponse> items = new ArrayList<>();
}

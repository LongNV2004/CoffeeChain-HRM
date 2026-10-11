package com.example.coffee_hrm.dto.response;

import com.example.coffee_hrm.common.enums.ApprovalStatus;

public record ManagerLeaveReviewResult(Integer requestId, ApprovalStatus status,
                                       int chargedWorkDays, int releasedShifts) {
}

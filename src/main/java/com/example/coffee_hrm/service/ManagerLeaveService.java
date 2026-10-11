package com.example.coffee_hrm.service;

import com.example.coffee_hrm.common.enums.ApprovalStatus;
import com.example.coffee_hrm.dto.request.ReviewLeaveRequest;
import com.example.coffee_hrm.dto.response.ManagerLeaveDetail;
import com.example.coffee_hrm.dto.response.ManagerLeavePage;
import com.example.coffee_hrm.dto.response.ManagerLeaveReviewResult;
import com.example.coffee_hrm.security.AuthenticatedUser;

public interface ManagerLeaveService {
    ManagerLeavePage list(int page, ApprovalStatus status, AuthenticatedUser manager);
    ManagerLeaveDetail detail(Integer id, AuthenticatedUser manager);
    ManagerLeaveReviewResult review(Integer id, ReviewLeaveRequest input,
                                    AuthenticatedUser manager);
}

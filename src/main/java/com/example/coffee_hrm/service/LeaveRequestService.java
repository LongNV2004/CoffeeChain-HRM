package com.example.coffee_hrm.service;

import com.example.coffee_hrm.common.enums.ApprovalStatus;
import com.example.coffee_hrm.common.enums.LeaveType;
import com.example.coffee_hrm.dto.request.CreateLeaveRequest;
import com.example.coffee_hrm.dto.response.AdminLeaveReport;
import com.example.coffee_hrm.dto.response.LeaveFormContext;
import com.example.coffee_hrm.dto.response.LeaveHistoryPage;
import com.example.coffee_hrm.dto.response.LeavePreviewResponse;
import com.example.coffee_hrm.dto.response.LeaveRequestResponse;
import com.example.coffee_hrm.dto.response.LeaveSlotPage;
import com.example.coffee_hrm.security.AuthenticatedUser;

import java.time.LocalDate;
import java.util.List;

public interface LeaveRequestService {
    LeaveFormContext formContext(AuthenticatedUser user);

    LeaveSlotPage listSlots(LocalDate fromDate, LocalDate toDate, AuthenticatedUser user);

    LeavePreviewResponse preview(List<Integer> assignmentIds, LeaveType leaveType, AuthenticatedUser user);

    LeaveRequestResponse create(CreateLeaveRequest request, AuthenticatedUser user);

    LeaveHistoryPage history(int page, ApprovalStatus status, LocalDate fromDate, LocalDate toDate,
                             AuthenticatedUser user);

    LeaveRequestResponse detail(Integer id, AuthenticatedUser user);

    LeaveRequestResponse cancel(Integer id, AuthenticatedUser user);

    AdminLeaveReport adminReport(int page, ApprovalStatus status, LocalDate fromDate, LocalDate toDate);
}

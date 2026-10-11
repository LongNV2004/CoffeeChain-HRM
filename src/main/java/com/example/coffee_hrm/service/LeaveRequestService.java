package com.example.coffee_hrm.service;

import com.example.coffee_hrm.dto.request.CreateLeaveRequest;
import com.example.coffee_hrm.dto.response.LeaveHistoryPage;
import com.example.coffee_hrm.dto.response.LeavePreviewResponse;
import com.example.coffee_hrm.dto.response.LeaveRequestResponse;
import com.example.coffee_hrm.security.AuthenticatedUser;
import java.time.LocalDate;

public interface LeaveRequestService {
    LeavePreviewResponse preview(LocalDate startDate, LocalDate endDate, AuthenticatedUser user);
    LeaveRequestResponse create(CreateLeaveRequest request, AuthenticatedUser user);
    LeaveHistoryPage history(int page, AuthenticatedUser user);
}

package com.example.coffee_hrm.service;

import com.example.coffee_hrm.dto.request.CreateOvertimeRequestDto;
import com.example.coffee_hrm.dto.response.OvertimePageView;
import com.example.coffee_hrm.security.AuthenticatedUser;

import java.time.LocalDate;

public interface OvertimeRequestService {

    OvertimePageView getStaffOvertimePage(AuthenticatedUser staff, LocalDate weekStart);

    void submitOvertimeRequest(CreateOvertimeRequestDto request, AuthenticatedUser staff);

    void cancelOvertimeRequest(Integer requestId, AuthenticatedUser staff);

    OvertimePageView getManagerOvertimePage(AuthenticatedUser manager, LocalDate weekStart, String statusFilter);

    void reviewOvertimeRequest(Integer requestId, boolean approved, String reviewNote, AuthenticatedUser manager);

    int reviewAllPending(boolean approved, LocalDate weekStart, AuthenticatedUser manager);
}


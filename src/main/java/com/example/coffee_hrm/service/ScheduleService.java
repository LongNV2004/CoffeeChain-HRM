package com.example.coffee_hrm.service;

import com.example.coffee_hrm.dto.request.AssignShiftRequest;
import com.example.coffee_hrm.dto.request.CreateShiftChangeRequestDto;
import com.example.coffee_hrm.dto.response.WeeklyScheduleView;
import com.example.coffee_hrm.security.AuthenticatedUser;

import java.time.LocalDate;

public interface ScheduleService {

    WeeklyScheduleView getWeeklyScheduleForManager(AuthenticatedUser user, LocalDate dateInWeek);

    WeeklyScheduleView getWeeklyScheduleForStaff(AuthenticatedUser user, LocalDate dateInWeek);

    void assignShift(AssignShiftRequest request, AuthenticatedUser user);

    void cancelAssignment(Integer assignmentId, AuthenticatedUser user);

    void publishWeeklySchedule(LocalDate weekStartDate, AuthenticatedUser user);

    void requestShiftChange(CreateShiftChangeRequestDto request, AuthenticatedUser user);

    void resolveShiftChange(Integer requestId, boolean approved, Integer replacementEmployeeId, AuthenticatedUser user);

    void respondToIncomingShiftChange(Integer requestId, boolean agreed, AuthenticatedUser user);
}

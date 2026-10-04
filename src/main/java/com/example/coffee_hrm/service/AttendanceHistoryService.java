package com.example.coffee_hrm.service;

import com.example.coffee_hrm.dto.request.AttendanceHistoryQuery;
import com.example.coffee_hrm.dto.response.AttendanceHistoryView;
import com.example.coffee_hrm.security.AuthenticatedUser;

import java.time.LocalDate;

public interface AttendanceHistoryService {

    AttendanceHistoryView getStaffHistory(AuthenticatedUser user, LocalDate fromDate, LocalDate toDate);

    AttendanceHistoryView getStaffHistory(AuthenticatedUser user, AttendanceHistoryQuery query);

    AttendanceHistoryView getManagerHistory(AuthenticatedUser user,
                                            Integer employeeId,
                                            LocalDate fromDate,
                                            LocalDate toDate);

    AttendanceHistoryView getManagerHistory(AuthenticatedUser user, AttendanceHistoryQuery query);

    AttendanceHistoryView getAdminHistory(AuthenticatedUser user, AttendanceHistoryQuery query);
}

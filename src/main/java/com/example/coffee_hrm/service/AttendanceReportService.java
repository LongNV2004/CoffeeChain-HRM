package com.example.coffee_hrm.service;

import com.example.coffee_hrm.dto.response.AdminAttendanceReportDetail;
import com.example.coffee_hrm.dto.response.AdminAttendanceReportPage;
import com.example.coffee_hrm.dto.response.ManagerAttendanceReportPage;
import com.example.coffee_hrm.security.AuthenticatedUser;

public interface AttendanceReportService {

    ManagerAttendanceReportPage managerPage(AuthenticatedUser user, Integer year, Integer month, Integer employeeId);

    void submit(AuthenticatedUser user, int year, int month);

    AdminAttendanceReportPage adminPage(AuthenticatedUser user, Integer year, Integer month, Integer storeId, String status);

    AdminAttendanceReportDetail adminDetail(AuthenticatedUser user, int reportId, Integer employeeId);
}

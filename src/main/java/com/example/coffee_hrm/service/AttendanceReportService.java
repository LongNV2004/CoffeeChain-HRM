package com.example.coffee_hrm.service;

import com.example.coffee_hrm.dto.response.AdminAttendanceReportDetail;
import com.example.coffee_hrm.dto.response.AdminAttendanceReportPage;
import com.example.coffee_hrm.dto.response.AttendanceReportVersionView;
import com.example.coffee_hrm.dto.response.ManagerAttendanceReportPage;
import com.example.coffee_hrm.report.ReportExcelFile;
import com.example.coffee_hrm.security.AuthenticatedUser;

public interface AttendanceReportService {

    ManagerAttendanceReportPage managerPage(AuthenticatedUser user, Integer year, Integer month, Integer employeeId, String historyKind);

    void submit(AuthenticatedUser user, int year, int month);

    void approve(AuthenticatedUser user, int reportId);

    void requestRevision(AuthenticatedUser user, int reportId, String note);

    AdminAttendanceReportPage adminPage(AuthenticatedUser user, Integer year, Integer month, Integer storeId, String status);

    default AdminAttendanceReportDetail adminDetail(AuthenticatedUser user, int reportId, Integer employeeId, String historyKind) {
        return adminDetail(user, reportId, employeeId, historyKind, null);
    }

    AdminAttendanceReportDetail adminDetail(AuthenticatedUser user, int reportId, Integer employeeId, String historyKind, Integer versionPage);

    AttendanceReportVersionView adminVersion(AuthenticatedUser user, int reportId, int version);

    void deleteVersion(AuthenticatedUser user, int reportId, int version);

    ReportExcelFile managerReportExcel(AuthenticatedUser user, int year, int month);

    ReportExcelFile adminReportExcel(AuthenticatedUser user, int reportId);

    ReportExcelFile adminVersionExcel(AuthenticatedUser user, int reportId, int version);
}

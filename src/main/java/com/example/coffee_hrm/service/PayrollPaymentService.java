package com.example.coffee_hrm.service;

import com.example.coffee_hrm.dto.request.PayrollPaymentForm;
import com.example.coffee_hrm.dto.response.AdminAttendanceReportPage;
import com.example.coffee_hrm.dto.response.PayrollPaymentViews;
import com.example.coffee_hrm.security.AuthenticatedUser;

import java.util.List;

/**
 * Báo cáo lương cửa hàng và thanh toán lương. Admin duyệt rồi ghi nhận giao dịch đã trả ngoài hệ thống;
 * Manager chỉ xem tình trạng của cửa hàng mình; Staff chỉ xem lương của bản thân.
 */
public interface PayrollPaymentService {

    /** Danh sách báo cáo của mọi cửa hàng trong kỳ, kèm tổng lương, đã trả và còn phải trả (Admin). */
    PayrollPaymentViews.ReportList adminReports(AuthenticatedUser admin,
                                                AdminAttendanceReportPage page,
                                                String paymentStatus,
                                                String query);

    /** Bảng lương đã chốt của một báo cáo cùng lịch sử thanh toán (Admin). */
    PayrollPaymentViews.ReportPayroll reportPayroll(AuthenticatedUser admin, int reportId);

    /** Chi tiết chấm công, cách tính lương và thanh toán của một nhân viên trong báo cáo (Admin). */
    PayrollPaymentViews.EmployeePayroll employeePayroll(AuthenticatedUser admin, int reportId, int employeeId);

    /** Tình trạng thanh toán báo cáo của cửa hàng Manager quản lý; null khi kỳ này chưa gửi báo cáo. */
    PayrollPaymentViews.ReportPayroll managerReport(AuthenticatedUser manager, int year, int month);

    /** Thanh toán một nhân viên. Trả về số tiền đã ghi nhận (định dạng hiển thị). */
    String payEmployee(AuthenticatedUser admin, int reportId, int employeeId, PayrollPaymentForm form);

    /** Thanh toán toàn bộ nhân viên còn nợ trong báo cáo. */
    PayAllResult payAll(AuthenticatedUser admin, int reportId, PayrollPaymentForm form);

    /** Các kỳ lương đã được duyệt của chính nhân viên đang đăng nhập (Staff). */
    List<PayrollPaymentViews.StaffPeriod> myPeriods(AuthenticatedUser staff);

    record PayAllResult(int employees, String totalLabel) {
    }
}

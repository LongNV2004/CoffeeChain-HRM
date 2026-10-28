package com.example.coffee_hrm.report;

import com.example.coffee_hrm.dto.response.PayrollBoardView;
import com.example.coffee_hrm.dto.response.PayrollDetailView;

import java.util.ArrayList;
import java.util.List;

public final class PayrollExcel {

    private PayrollExcel() {
    }

    public static byte[] write(PayrollBoardView board, List<PayrollDetailView> details) {
        List<List<String>> summary = new ArrayList<>();
        summary.add(List.of(
                "Tháng", "Mã nhân viên", "Họ tên", "Cửa hàng", "Tổng số ca",
                "Số giờ được tính lương", "Mức lương/giờ", "Tổng lương dự kiến", "Trạng thái"));
        for (PayrollBoardView.Row row : board.getRows()) {
            summary.add(List.of(
                    text(board.getPeriodLabel()),
                    text(row.getEmployeeCode()),
                    text(row.getEmployeeName()),
                    text(row.getStoreName()),
                    String.valueOf(row.getAssignedShifts()),
                    String.valueOf(row.getPayableHours()),
                    text(row.getHourlyRateLabel()),
                    text(row.getExpectedSalaryLabel()),
                    text(row.getStatusLabel())));
        }
        List<List<String>> shifts = new ArrayList<>();
        shifts.add(List.of(
                "Mã nhân viên", "Họ tên", "Cửa hàng", "Ngày", "Ca", "Giờ theo lịch",
                "Check-in", "Check-out", "Trạng thái chấm công", "Giờ được tính lương",
                "Mức lương/giờ", "Lương ca", "Lý do"));
        if (details != null) {
            for (PayrollDetailView detail : details) {
                for (PayrollDetailView.ShiftRow shift : detail.getShifts()) {
                    shifts.add(List.of(
                            text(detail.getEmployeeCode()),
                            text(detail.getEmployeeName()),
                            text(detail.getStoreName()),
                            text(shift.getDateLabel()),
                            text(shift.getShiftName()),
                            text(shift.getScheduleLabel()),
                            text(shift.getCheckInLabel()),
                            text(shift.getCheckOutLabel()),
                            text(shift.getAttendanceStatus()),
                            String.valueOf(shift.getPayableHours()),
                            text(shift.getHourlyRateLabel()),
                            text(shift.getShiftSalaryLabel()),
                            text(shift.getUnpaidReason())));
                }
            }
        }
        return ExcelSpreadsheet.write(List.of(
                new ExcelSpreadsheet.Sheet("Bang luong", summary),
                new ExcelSpreadsheet.Sheet("Chi tiet ca", shifts)));
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}

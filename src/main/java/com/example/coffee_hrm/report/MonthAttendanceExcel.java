package com.example.coffee_hrm.report;

import com.example.coffee_hrm.dto.response.ManagerMonthAttendanceView;

import java.util.ArrayList;
import java.util.List;

public final class MonthAttendanceExcel {

    private MonthAttendanceExcel() {
    }

    public static byte[] write(ManagerMonthAttendanceView board) {
        List<List<String>> summary = new ArrayList<>();
        summary.add(List.of(
                "Cửa hàng", "Tháng", "Mã nhân viên", "Tên nhân viên", "Vai trò",
                "Ca theo lịch", "Ca đã làm", "Ca đi muộn", "Tổng thời gian đi muộn",
                "Ca về sớm", "Tổng thời gian về sớm", "Ca vắng"));
        for (ManagerMonthAttendanceView.EmployeeMonth card : board.getEmployees()) {
            summary.add(List.of(
                    text(board.getStoreName()),
                    text(board.getMonthLabel()),
                    text(card.getEmployeeCode()),
                    text(card.getEmployeeName()),
                    text(card.getRoleLabel()),
                    String.valueOf(card.getScheduledSlots()),
                    String.valueOf(card.getWorkedSlots()),
                    String.valueOf(card.getLateSlots()),
                    text(card.getLateDurationLabel()),
                    String.valueOf(card.getEarlySlots()),
                    text(card.getEarlyDurationLabel()),
                    String.valueOf(card.getAbsentSlots())));
        }

        List<List<String>> details = new ArrayList<>();
        details.add(List.of(
                "Mã nhân viên", "Tên nhân viên", "Ngày", "Ca", "Giờ theo lịch",
                "Check-in", "Check-out", "Đi muộn", "Về sớm", "Giờ công", "Trạng thái"));
        for (ManagerMonthAttendanceView.EmployeeMonth card : board.getEmployees()) {
            for (ManagerMonthAttendanceView.DayLine line : card.getDays()) {
                details.add(List.of(
                        text(card.getEmployeeCode()),
                        text(card.getEmployeeName()),
                        text(line.getDateLabel()),
                        text(line.getShiftName()),
                        text(line.getScheduleLabel()),
                        text(line.getCheckInLabel()),
                        text(line.getCheckOutLabel()),
                        text(line.getLateLabel()),
                        text(line.getEarlyLabel()),
                        text(line.getWorkedLabel()),
                        text(line.getStatusLabel())));
            }
        }
        return ExcelSpreadsheet.write(List.of(
                new ExcelSpreadsheet.Sheet("Tong hop", summary),
                new ExcelSpreadsheet.Sheet("Chi tiet", details)));
    }

    public static String fileName(ManagerMonthAttendanceView board) {
        String month = board.getMonthValue() == null ? "thang" : board.getMonthValue();
        return "cham-cong-thang-" + month + ".xls";
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}

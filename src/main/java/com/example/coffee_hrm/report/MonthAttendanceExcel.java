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
                "Cửa hàng", "Tháng", "Mã nhân viên", "Tên nhân viên",
                "Ca được phân công", "Ca hoàn thành", "Ca đi muộn", "Ca về sớm",
                "Ca thiếu chấm công", "Thời gian có mặt"));
        for (ManagerMonthAttendanceView.EmployeeMonth card : board.getEmployees()) {
            summary.add(List.of(
                    text(board.getStoreName()),
                    text(board.getMonthLabel()),
                    text(card.getEmployeeCode()),
                    text(card.getEmployeeName()),
                    String.valueOf(card.getScheduledSlots()),
                    String.valueOf(card.getCompletedSlots()),
                    String.valueOf(card.getLateSlots()),
                    String.valueOf(card.getEarlySlots()),
                    String.valueOf(card.getMissingSlots()),
                    text(card.getPaidHoursLabel())));
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

package com.example.coffee_hrm.report;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class AttendanceReportExcel {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private AttendanceReportExcel() {
    }

    public static byte[] write(AttendanceReportWorkbook workbook) {
        return ExcelSpreadsheet.write(List.of(
                new ExcelSpreadsheet.Sheet("Tong hop", summary(workbook)),
                new ExcelSpreadsheet.Sheet("Ca lam", shifts(workbook)),
                new ExcelSpreadsheet.Sheet("Dao tao", training(workbook))));
    }

    public static String fileName(int storeId, int year, int month, int version) {
        return "bao-cao-cham-cong-" + storeId + "-" + year + "-" + String.format("%02d", month) + "-v" + version + ".xls";
    }

    private static List<List<String>> summary(AttendanceReportWorkbook workbook) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("Cửa hàng", text(workbook.getStoreName())));
        rows.add(List.of("Kỳ", workbook.periodLabel()));
        rows.add(List.of("Người lập", text(workbook.getAuthorName())));
        rows.add(List.of());
        rows.add(List.of(
                "STT", "Mã nhân viên", "Tên nhân viên", "Vai trò",
                "Ngày công", "Ngày có mặt", "Lượt đi muộn", "Phút đi muộn",
                "Lượt về sớm", "Phút về sớm", "Ngày vắng", "Lượt vắng", "Giờ công", "Ghi chú"));
        for (AttendanceReportWorkbook.SummaryRow row : workbook.getEmployees()) {
            rows.add(List.of(
                    String.valueOf(row.getOrder()),
                    "NV" + String.format("%03d", row.getEmployeeId()),
                    text(row.getFullName()),
                    text(row.getRoleLabel()),
                    String.valueOf(row.getWorkDays()),
                    String.valueOf(row.getAttendedDays()),
                    String.valueOf(row.getLateRecords()),
                    String.valueOf(row.getLateMinutes()),
                    String.valueOf(row.getEarlyRecords()),
                    String.valueOf(row.getEarlyMinutes()),
                    String.valueOf(row.getAbsentDays()),
                    String.valueOf(row.getAbsentRecords()),
                    AttendanceReportWorkbook.formatHours(row.getWorkedMinutes()),
                    text(row.getNote())));
        }
        return rows;
    }

    private static List<List<String>> shifts(AttendanceReportWorkbook workbook) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of(
                "STT", "Ngày", "Mã nhân viên", "Tên nhân viên", "Ca",
                "Giờ vào lịch", "Giờ ra lịch", "Check-in", "Check-out",
                "Đi muộn (phút)", "Về sớm (phút)", "Giờ công", "Trạng thái", "Ghi chú"));
        for (AttendanceReportWorkbook.ShiftRow row : workbook.getShifts()) {
            rows.add(List.of(
                    String.valueOf(row.getOrder()),
                    row.getWorkDate() == null ? "" : row.getWorkDate().format(DATE),
                    "NV" + String.format("%03d", row.getEmployeeId()),
                    text(row.getFullName()),
                    text(row.getShiftLabel()),
                    row.getScheduledStart() == null ? "" : row.getScheduledStart().format(TIME),
                    row.getScheduledEnd() == null ? "" : row.getScheduledEnd().format(TIME),
                    row.getCheckIn() == null ? "" : row.getCheckIn().format(CLOCK),
                    row.getCheckOut() == null ? "" : row.getCheckOut().format(CLOCK),
                    String.valueOf(row.getLateMinutes()),
                    String.valueOf(row.getEarlyMinutes()),
                    row.getWorkedMinutes() == null ? "" : AttendanceReportWorkbook.formatHours(row.getWorkedMinutes()),
                    text(row.getStatusLabel()),
                    text(row.getNote())));
        }
        return rows;
    }

    private static List<List<String>> training(AttendanceReportWorkbook workbook) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of(
                "STT", "Ngày", "Mã nhân viên", "Tên nhân viên", "Lớp",
                "Giờ vào lịch", "Giờ ra lịch", "Check-in", "Check-out",
                "Đi muộn (phút)", "Về sớm (phút)", "Trạng thái", "Ghi chú"));
        for (AttendanceReportWorkbook.TrainingRow row : workbook.getTrainings()) {
            rows.add(List.of(
                    String.valueOf(row.getOrder()),
                    row.getWorkDate() == null ? "" : row.getWorkDate().format(DATE),
                    "NV" + String.format("%03d", row.getEmployeeId()),
                    text(row.getFullName()),
                    text(row.getClassName()),
                    row.getScheduledStart() == null ? "" : row.getScheduledStart().format(TIME),
                    row.getScheduledEnd() == null ? "" : row.getScheduledEnd().format(TIME),
                    row.getCheckIn() == null ? "" : row.getCheckIn().format(CLOCK),
                    row.getCheckOut() == null ? "" : row.getCheckOut().format(CLOCK),
                    String.valueOf(row.getLateMinutes()),
                    String.valueOf(row.getEarlyMinutes()),
                    text(row.getStatusLabel()),
                    text(row.getNote())));
        }
        return rows;
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}

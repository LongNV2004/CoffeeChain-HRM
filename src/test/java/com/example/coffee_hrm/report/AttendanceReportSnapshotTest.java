package com.example.coffee_hrm.report;

import com.example.coffee_hrm.dto.response.AttendanceReportVersionView;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttendanceReportSnapshotTest {

    @Test
    void readsSavedSheetsIntoAttendanceCards() {
        byte[] body = ExcelSpreadsheet.write(List.of(
                new ExcelSpreadsheet.Sheet("Tong hop", List.of(
                        List.of("Cửa hàng", "Cầu Giấy"),
                        List.of(),
                        List.of("STT", "Mã nhân viên", "Tên nhân viên", "Vai trò",
                                "Ngày công", "Ngày có mặt", "Lượt đi muộn", "Phút đi muộn",
                                "Lượt về sớm", "Phút về sớm", "Ngày vắng", "Lượt vắng", "Giờ công", "Ghi chú"),
                        List.of("1", "NV003", "Lê Thị Thu", "Staff", "3", "1", "0", "0", "1", "95", "0", "0", "4 giờ 25 phút", ""))),
                new ExcelSpreadsheet.Sheet("Ca lam", List.of(
                        List.of("STT", "Ngày", "Mã nhân viên", "Tên nhân viên", "Ca",
                                "Giờ vào lịch", "Giờ ra lịch", "Check-in", "Check-out",
                                "Đi muộn (phút)", "Về sớm (phút)", "Giờ công", "Trạng thái", "Ghi chú"),
                        List.of("1", "21/10/2026", "NV003", "Lê Thị Thu", "Ca Chiều",
                                "12:00", "17:00", "21/10/2026 11:00", "21/10/2026 15:25",
                                "0", "95", "4 giờ 25 phút", "Về sớm", ""))),
                new ExcelSpreadsheet.Sheet("Dao tao", List.of(
                        List.of("STT", "Ngày", "Mã nhân viên", "Tên nhân viên", "Lớp",
                                "Giờ vào lịch", "Giờ ra lịch", "Check-in", "Check-out",
                                "Đi muộn (phút)", "Về sớm (phút)", "Trạng thái", "Ghi chú"),
                        List.of("1", "21/10/2026", "NV003", "Lê Thị Thu", "Phục vụ",
                                "09:05", "09:10", "21/10/2026 09:05", "",
                                "0", "0", "Có mặt", "")))));

        List<AttendanceReportVersionView.EmployeeCard> cards = AttendanceReportSnapshot.employees(ExcelSpreadsheetReader.read(body));

        assertEquals(1, cards.size());
        AttendanceReportVersionView.EmployeeCard card = cards.getFirst();
        assertEquals(3, card.getEmployeeId());
        assertEquals("Nhân viên", card.getRoleText());
        assertEquals("staff", card.getRoleKey());
        assertEquals("4 giờ 25 phút", card.getHours());
        assertEquals(1, card.getShifts().size());
        assertEquals("11:00", card.getShifts().getFirst().getCheckIn());
        assertEquals("15:25", card.getShifts().getFirst().getCheckOut());
        assertTrue(card.getShifts().getFirst().isEarly());
        assertEquals(1, card.getTrainings().size());
        assertTrue(card.getTrainings().getFirst().isTraining());
    }
}

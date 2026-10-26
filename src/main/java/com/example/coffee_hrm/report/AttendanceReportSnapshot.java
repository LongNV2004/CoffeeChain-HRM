package com.example.coffee_hrm.report;

import com.example.coffee_hrm.dto.response.AttendanceReportVersionView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Đọc bản Excel đã gửi thành thẻ nhân viên, cùng cách xem của theo dõi chấm công. */
public final class AttendanceReportSnapshot {

    private AttendanceReportSnapshot() {
    }

    public static List<AttendanceReportVersionView.EmployeeCard> employees(List<ExcelSpreadsheet.Sheet> sheets) {
        Map<String, Draft> people = new LinkedHashMap<>();
        if (sheets == null) {
            return List.of();
        }
        for (ExcelSpreadsheet.Sheet sheet : sheets) {
            if ("Tong hop".equals(sheet.name())) {
                readSummary(people, sheet.rows());
            } else if ("Ca lam".equals(sheet.name())) {
                readDays(people, sheet.rows(), true);
            } else if ("Dao tao".equals(sheet.name())) {
                readDays(people, sheet.rows(), false);
            }
        }
        List<AttendanceReportVersionView.EmployeeCard> cards = new ArrayList<>();
        for (Draft draft : people.values()) {
            cards.add(draft.toCard());
        }
        return cards;
    }

    private static void readSummary(Map<String, Draft> people, List<List<String>> rows) {
        for (List<String> row : rows) {
            if (row.isEmpty() || "STT".equals(cell(row, 0)) || cell(row, 1).isBlank()) {
                continue;
            }
            if (row.size() <= 2) {
                continue;
            }
            Draft draft = person(people, cell(row, 1), cell(row, 2));
            draft.roleRaw = cell(row, 3);
            draft.attendedDays = blank(cell(row, 5), "0");
            draft.lateRecords = blank(cell(row, 6), "0");
            draft.lateMinutes = blank(cell(row, 7), "0");
            draft.earlyRecords = blank(cell(row, 8), "0");
            draft.earlyMinutes = blank(cell(row, 9), "0");
            draft.absentRecords = blank(cell(row, 11), "0");
            draft.hours = blank(cell(row, 12), "—");
        }
    }

    private static void readDays(Map<String, Draft> people, List<List<String>> rows, boolean shift) {
        for (List<String> row : rows) {
            if (row.isEmpty() || "STT".equals(cell(row, 0)) || cell(row, 2).isBlank()) {
                continue;
            }
            Draft draft = person(people, cell(row, 2), cell(row, 3));
            String titleName = blank(cell(row, 4), shift ? "Ca làm" : "Đào tạo");
            String date = blank(cell(row, 1), "—");
            int late = whole(shift ? cell(row, 9) : cell(row, 9));
            int early = whole(shift ? cell(row, 10) : cell(row, 10));
            String status = shift ? cell(row, 12) : cell(row, 11);
            String note = shift ? cell(row, 13) : cell(row, 12);
            boolean absent = status.contains("Vắng") || status.contains("Không tham gia");
            String badge = absent ? "absent" : (late > 0 || early > 0 ? "late" : "on-time");
            AttendanceReportVersionView.DayLine line = AttendanceReportVersionView.DayLine.builder()
                    .title(date + " · " + titleName)
                    .kindLabel(shift ? "Ca làm" : "Đào tạo")
                    .schedule(blank(cell(row, 5), "—") + " – " + blank(cell(row, 6), "—"))
                    .checkIn(clock(cell(row, 7)))
                    .checkOut(clock(cell(row, 8)))
                    .lateText(late + " phút")
                    .earlyText(early + " phút")
                    .hours(shift ? blank(cell(row, 11), "—") : "")
                    .status(blank(status, "—"))
                    .badgeClass(badge)
                    .note(note)
                    .shift(shift)
                    .training(!shift)
                    .late(late > 0)
                    .early(early > 0)
                    .absent(absent)
                    .build();
            if (shift) {
                draft.shifts.add(line);
            } else {
                draft.trainings.add(line);
            }
        }
    }

    private static Draft person(Map<String, Draft> people, String code, String name) {
        return people.computeIfAbsent(code, key -> new Draft(key, name));
    }

    private static String cell(List<String> row, int index) {
        if (index >= row.size() || row.get(index) == null) {
            return "";
        }
        return row.get(index).trim();
    }

    private static String blank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String clock(String value) {
        if (value == null || value.isBlank()) {
            return "—";
        }
        int space = value.lastIndexOf(' ');
        return space >= 0 ? value.substring(space + 1) : value;
    }

    private static int whole(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static int employeeId(String code) {
        String digits = code.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static final class Draft {
        private final String code;
        private final String fullName;
        private String roleRaw = "";
        private String attendedDays = "0";
        private String hours = "—";
        private String lateRecords = "0";
        private String lateMinutes = "0";
        private String earlyRecords = "0";
        private String earlyMinutes = "0";
        private String absentRecords = "0";
        private final List<AttendanceReportVersionView.DayLine> shifts = new ArrayList<>();
        private final List<AttendanceReportVersionView.DayLine> trainings = new ArrayList<>();

        private Draft(String code, String fullName) {
            this.code = code;
            this.fullName = fullName == null || fullName.isBlank() ? code : fullName;
        }

        private AttendanceReportVersionView.EmployeeCard toCard() {
            String roleKey = roleKey(roleRaw);
            return AttendanceReportVersionView.EmployeeCard.builder()
                    .employeeId(employeeId(code))
                    .code(code)
                    .fullName(fullName)
                    .roleKey(roleKey)
                    .roleText(roleText(roleKey, roleRaw))
                    .attendedDays(attendedDays)
                    .hours(hours)
                    .lateRecords(lateRecords)
                    .lateMinutes(lateMinutes)
                    .earlyRecords(earlyRecords)
                    .earlyMinutes(earlyMinutes)
                    .absentRecords(absentRecords)
                    .shifts(List.copyOf(shifts))
                    .trainings(List.copyOf(trainings))
                    .build();
        }

        private static String roleKey(String label) {
            String value = label == null ? "" : label.toLowerCase();
            if (value.contains("manager") || value.contains("quản lý") || value.contains("quan ly")) {
                return "manager";
            }
            if (value.contains("staff") || value.contains("nhân viên") || value.contains("nhan vien")) {
                return "staff";
            }
            return "";
        }

        private static String roleText(String roleKey, String raw) {
            if ("manager".equals(roleKey)) {
                return "Quản lý";
            }
            if ("staff".equals(roleKey)) {
                return "Nhân viên";
            }
            return raw == null ? "" : raw;
        }
    }
}

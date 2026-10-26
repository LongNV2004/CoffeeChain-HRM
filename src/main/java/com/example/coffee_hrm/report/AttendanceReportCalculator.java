package com.example.coffee_hrm.report;

import com.example.coffee_hrm.common.enums.AttendanceStatus;
import com.example.coffee_hrm.entity.Attendance;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.Role;
import com.example.coffee_hrm.entity.Shift;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.entity.TrainingAttendance;
import com.example.coffee_hrm.entity.TrainingClass;
import com.example.coffee_hrm.entity.User;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class AttendanceReportCalculator {

    private AttendanceReportCalculator() {
    }

    public static AttendanceReportWorkbook build(Store store,
                                                 List<Employee> employees,
                                                 List<Attendance> shiftRows,
                                                 List<TrainingAttendance> trainingRows,
                                                 String authorName,
                                                 LocalDate createdDate) {
        int year = createdDate.getYear();
        int month = createdDate.getMonthValue();
        List<Employee> roster = employees.stream()
                .filter(employee -> belongsToStore(employee, store.getId()))
                .sorted(Comparator.comparing(Employee::getFullName, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(Employee::getId, Comparator.nullsLast(Integer::compareTo)))
                .toList();

        List<Attendance> shifts = shiftRows.stream()
                .filter(row -> includeShift(row, store.getId()))
                .sorted(shiftOrder())
                .toList();
        List<TrainingAttendance> trainings = trainingRows.stream()
                .filter(row -> includeTraining(row, store.getId()))
                .sorted(trainingOrder())
                .toList();
        Map<Integer, SummaryAccumulator> byEmployee = new LinkedHashMap<>();
        for (Employee employee : roster) {
            byEmployee.put(employee.getId(), new SummaryAccumulator(employee));
        }

        List<AttendanceReportWorkbook.ShiftRow> shiftDetails = new ArrayList<>();
        int order = 1;
        int shiftLate = 0;
        int absentRecords = 0;
        int shiftOpen = 0;
        int shiftReview = 0;
        for (Attendance attendance : shifts) {
            ShiftFacts facts = shiftFacts(attendance);
            if (facts.late) {
                shiftLate++;
            }
            if (attendance.getStatus() == AttendanceStatus.ABSENT) {
                absentRecords++;
            }
            if (facts.openCheckout) {
                shiftOpen++;
            }
            if (facts.needsReview) {
                shiftReview++;
            }
            SummaryAccumulator accumulator = byEmployee.get(attendance.getEmployee().getId());
            if (accumulator != null) {
                accumulator.add(attendance, facts);
            }
            shiftDetails.add(AttendanceReportWorkbook.ShiftRow.builder()
                    .order(order++)
                    .workDate(attendance.getWorkDate())
                    .employeeId(attendance.getEmployee().getId())
                    .fullName(text(attendance.getEmployee().getFullName()))
                    .roleLabel(roleLabel(attendance.getEmployee()))
                    .shiftLabel(shiftLabel(attendance))
                    .scheduledStart(scheduledStart(attendance))
                    .scheduledEnd(scheduledEnd(attendance))
                    .checkIn(attendance.getCheckInTime())
                    .checkOut(attendance.getCheckOutTime())
                    .lateMinutes(zero(attendance.getLateMinutes()))
                    .earlyMinutes(zero(attendance.getEarlyLeaveMinutes()))
                    .workedMinutes(facts.workedMinutes)
                    .statusLabel(statusLabel(attendance.getStatus()))
                    .note(facts.note)
                    .needsReview(facts.needsReview)
                    .attendanceId(attendance.getId())
                    .hasCheckInPhoto(hasText(attendance.getCheckInPhotoName()))
                    .hasCheckOutPhoto(hasText(attendance.getCheckOutPhotoName()))
                    .build());
        }

        List<AttendanceReportWorkbook.TrainingRow> trainingDetails = new ArrayList<>();
        int trainingOrder = 1;
        int trainingLate = 0;
        int trainingOpen = 0;
        int trainingReview = 0;
        for (TrainingAttendance training : trainings) {
            boolean late = isLate(training.getStatus());
            boolean open = training.getCheckInTime() != null && training.getCheckOutTime() == null;
            String note = trainingNote(training);
            boolean review = !note.isBlank();
            if (late) {
                trainingLate++;
            }
            if (open) {
                trainingOpen++;
            }
            if (review) {
                trainingReview++;
            }
            trainingDetails.add(AttendanceReportWorkbook.TrainingRow.builder()
                    .order(trainingOrder++)
                    .workDate(training.getWorkDate())
                    .employeeId(training.getEmployee().getId())
                    .fullName(text(training.getEmployee().getFullName()))
                    .roleLabel(roleLabel(training.getEmployee()))
                    .classCode(classCode(training))
                    .className(className(training))
                    .trainerName(trainerName(training))
                    .trainingType(trainingType(training))
                    .scheduledStart(training.getScheduledStartTime() != null
                            ? training.getScheduledStartTime()
                            : classStart(training))
                    .scheduledEnd(training.getScheduledEndTime() != null
                            ? training.getScheduledEndTime()
                            : classEnd(training))
                    .checkIn(training.getCheckInTime())
                    .checkOut(training.getCheckOutTime())
                    .lateMinutes(zero(training.getLateMinutes()))
                    .earlyMinutes(zero(training.getEarlyLeaveMinutes()))
                    .statusLabel(trainingStatusLabel(training.getStatus()))
                    .note(note)
                    .needsReview(review)
                    .attendanceId(training.getId())
                    .hasCheckInPhoto(hasText(training.getCheckInPhotoName()))
                    .hasCheckOutPhoto(hasText(training.getCheckOutPhotoName()))
                    .build());
        }

        List<AttendanceReportWorkbook.SummaryRow> summaries = new ArrayList<>();
        int summaryOrder = 1;
        for (SummaryAccumulator accumulator : byEmployee.values()) {
            summaries.add(accumulator.toRow(summaryOrder++));
        }

        int workDayCount = 0;
        int violationCount = 0;
        int missingCount = 0;
        for (AttendanceReportWorkbook.SummaryRow summary : summaries) {
            workDayCount += summary.getAttendedDays();
            violationCount += summary.getLateRecords() + summary.getEarlyRecords();
            missingCount += summary.getAbsentRecords();
        }

        AttendanceReportWorkbook.Preview preview = AttendanceReportWorkbook.Preview.builder()
                .employeeCount(summaries.size())
                .shiftRecordCount(shiftDetails.size())
                .trainingRecordCount(trainingDetails.size())
                .lateCount(shiftLate + trainingLate)
                .shiftLateCount(shiftLate)
                .trainingLateCount(trainingLate)
                .absentCount(absentRecords)
                .openCheckoutCount(shiftOpen + trainingOpen)
                .reviewCount(shiftReview + trainingReview)
                .workDayCount(workDayCount)
                .violationCount(violationCount)
                .missingCount(missingCount)
                .build();

        return AttendanceReportWorkbook.builder()
                .storeId(store.getId())
                .storeName(text(store.getStoreName()))
                .year(year)
                .month(month)
                .createdDate(createdDate)
                .authorName(text(authorName))
                .employees(List.copyOf(summaries))
                .shifts(List.copyOf(shiftDetails))
                .trainings(List.copyOf(trainingDetails))
                .preview(preview)
                .build();
    }

    public static AttendanceReportWorkbook buildForPeriod(Store store,
                                                          int year,
                                                          int month,
                                                          List<Employee> employees,
                                                          List<Attendance> shiftRows,
                                                          List<TrainingAttendance> trainingRows,
                                                          String authorName,
                                                          LocalDate createdDate) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        List<Attendance> inPeriod = shiftRows.stream()
                .filter(row -> row.getWorkDate() != null && !row.getWorkDate().isBefore(start) && !row.getWorkDate().isAfter(end))
                .toList();
        List<TrainingAttendance> trainingInPeriod = trainingRows.stream()
                .filter(row -> row.getWorkDate() != null && !row.getWorkDate().isBefore(start) && !row.getWorkDate().isAfter(end))
                .toList();
        AttendanceReportWorkbook workbook = build(store, employees, inPeriod, trainingInPeriod, authorName, createdDate);
        return AttendanceReportWorkbook.builder()
                .storeId(workbook.getStoreId())
                .storeName(workbook.getStoreName())
                .year(year)
                .month(month)
                .createdDate(workbook.getCreatedDate())
                .authorName(workbook.getAuthorName())
                .employees(workbook.getEmployees())
                .shifts(workbook.getShifts())
                .trainings(workbook.getTrainings())
                .preview(workbook.getPreview())
                .build();
    }

    private static boolean includeShift(Attendance attendance, Integer storeId) {
        return attendance.getEmployee() != null
                && attendance.getWorkDate() != null
                && attendance.getStore() != null
                && Objects.equals(attendance.getStore().getId(), storeId)
                && belongsToStore(attendance.getEmployee(), storeId);
    }

    private static boolean includeTraining(TrainingAttendance training, Integer storeId) {
        return training.getEmployee() != null
                && training.getWorkDate() != null
                && training.getStore() != null
                && Objects.equals(training.getStore().getId(), storeId)
                && belongsToStore(training.getEmployee(), storeId);
    }

    private static boolean belongsToStore(Employee employee, Integer storeId) {
        return employee != null
                && employee.getStore() != null
                && Objects.equals(employee.getStore().getId(), storeId);
    }

    private static Comparator<Attendance> shiftOrder() {
        return Comparator.comparing(Attendance::getWorkDate, Comparator.nullsLast(LocalDate::compareTo))
                .thenComparing(row -> row.getEmployee().getFullName(), Comparator.nullsLast(String::compareToIgnoreCase))
                .thenComparing(Attendance::getId, Comparator.nullsLast(Integer::compareTo));
    }

    private static Comparator<TrainingAttendance> trainingOrder() {
        return Comparator.comparing(TrainingAttendance::getWorkDate, Comparator.nullsLast(LocalDate::compareTo))
                .thenComparing(row -> row.getEmployee().getFullName(), Comparator.nullsLast(String::compareToIgnoreCase))
                .thenComparing(TrainingAttendance::getId, Comparator.nullsLast(Integer::compareTo));
    }

    private static ShiftFacts shiftFacts(Attendance attendance) {
        List<String> notes = new ArrayList<>();
        boolean open = attendance.getCheckInTime() != null && attendance.getCheckOutTime() == null;
        boolean absent = attendance.getStatus() == AttendanceStatus.ABSENT;
        Integer worked = null;
        if (open) {
            notes.add("Chưa Check-out, cần kiểm tra");
        }
        if (attendance.getCheckOutTime() != null && attendance.getCheckInTime() == null) {
            notes.add("Có Check-out nhưng chưa Check-in, cần kiểm tra");
        }
        if (attendance.getCheckInTime() != null
                && attendance.getCheckOutTime() != null
                && attendance.getCheckOutTime().isBefore(attendance.getCheckInTime())) {
            notes.add("Giờ Check-out trước giờ Check-in, cần kiểm tra");
        }
        if (absent && (attendance.getCheckInTime() != null || attendance.getCheckOutTime() != null)) {
            notes.add("Bản ghi vắng nhưng có giờ chấm công, cần kiểm tra");
        }
        boolean validTimes = attendance.getCheckInTime() != null
                && attendance.getCheckOutTime() != null
                && !attendance.getCheckOutTime().isBefore(attendance.getCheckInTime());
        if (validTimes && !absent) {
            if (attendance.getWorkingMinutes() != null && attendance.getWorkingMinutes() >= 0) {
                worked = attendance.getWorkingMinutes();
            } else if (attendance.getWorkingMinutes() != null) {
                notes.add("Số phút làm việc không hợp lệ, cần kiểm tra");
            } else {
                worked = (int) Duration.between(attendance.getCheckInTime(), attendance.getCheckOutTime()).toMinutes();
                notes.add("Chưa có số phút làm việc đã lưu, cần kiểm tra");
            }
        }
        if (differentDay(attendance.getWorkDate(), attendance.getCheckInTime())
                || differentDay(attendance.getWorkDate(), attendance.getCheckOutTime())) {
            notes.add("Giờ thực tế khác ngày làm việc, cần kiểm tra");
        }
        boolean early = isEarly(attendance.getStatus()) || zero(attendance.getEarlyLeaveMinutes()) > 0;
        return new ShiftFacts(
                isLate(attendance.getStatus()),
                early,
                open,
                worked,
                !notes.isEmpty(),
                String.join("; ", notes));
    }

    private static String trainingNote(TrainingAttendance training) {
        List<String> notes = new ArrayList<>();
        if (training.getCheckInTime() != null && training.getCheckOutTime() == null) {
            notes.add("Chưa Check-out, cần kiểm tra");
        }
        if (training.getCheckOutTime() != null && training.getCheckInTime() == null) {
            notes.add("Có Check-out nhưng chưa Check-in, cần kiểm tra");
        }
        if (training.getCheckInTime() != null
                && training.getCheckOutTime() != null
                && training.getCheckOutTime().isBefore(training.getCheckInTime())) {
            notes.add("Giờ Check-out trước giờ Check-in, cần kiểm tra");
        }
        if (training.getStatus() == AttendanceStatus.ABSENT) {
            notes.add("Đào tạo không có nghiệp vụ tạo vắng; trạng thái này cần kiểm tra");
        }
        if (differentDay(training.getWorkDate(), training.getCheckInTime())
                || differentDay(training.getWorkDate(), training.getCheckOutTime())) {
            notes.add("Giờ thực tế khác ngày đào tạo, cần kiểm tra");
        }
        return String.join("; ", notes);
    }

    private static boolean differentDay(LocalDate workDate, LocalDateTime time) {
        return workDate != null && time != null && !workDate.equals(time.toLocalDate());
    }

    static boolean isLate(AttendanceStatus status) {
        return status == AttendanceStatus.LATE || status == AttendanceStatus.LATE_AND_EARLY;
    }

    private static boolean isEarly(AttendanceStatus status) {
        return status == AttendanceStatus.EARLY_LEAVE || status == AttendanceStatus.LATE_AND_EARLY;
    }

    private static String trainingStatusLabel(AttendanceStatus status) {
        if (status == AttendanceStatus.LATE) {
            return "Check-in muộn";
        }
        if (status == AttendanceStatus.LATE_AND_EARLY) {
            return "Check-in muộn và về sớm";
        }
        return statusLabel(status);
    }

    static String statusLabel(AttendanceStatus status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case PRESENT -> "Có mặt";
            case ON_TIME -> "Đúng giờ";
            case LATE -> "Đi muộn";
            case EARLY_LEAVE -> "Về sớm";
            case LATE_AND_EARLY -> "Đi muộn và về sớm";
            case LATE_CHECKOUT -> "Check-out muộn";
            case LATE_AND_LATE_CHECKOUT -> "Đi muộn và check-out muộn";
            case NOT_PARTICIPATED -> "Không tham gia";
            case ABSENT -> "Vắng";
        };
    }

    static String roleLabel(Employee employee) {
        if (employee == null || employee.getUser() == null || employee.getUser().getRole() == null) {
            return "";
        }
        Role role = employee.getUser().getRole();
        return role.getRoleName() == null ? "" : role.getRoleName().getDbValue();
    }

    private static String shiftLabel(Attendance attendance) {
        if (attendance.getScheduledShiftName() != null && !attendance.getScheduledShiftName().isBlank()) {
            return attendance.getScheduledShiftName();
        }
        Shift shift = attendance.getShift();
        if (shift == null) {
            return "";
        }
        if (shift.getShiftName() != null && !shift.getShiftName().isBlank()) {
            return shift.getShiftName();
        }
        return shift.getId() == null ? "" : String.valueOf(shift.getId());
    }

    private static LocalTime scheduledStart(Attendance attendance) {
        if (attendance.getScheduledStartTime() != null) {
            return attendance.getScheduledStartTime();
        }
        return attendance.getShift() == null ? null : attendance.getShift().getStartTime();
    }

    private static LocalTime scheduledEnd(Attendance attendance) {
        if (attendance.getScheduledEndTime() != null) {
            return attendance.getScheduledEndTime();
        }
        return attendance.getShift() == null ? null : attendance.getShift().getEndTime();
    }

    private static String classCode(TrainingAttendance training) {
        TrainingClass trainingClass = training.getTrainingClass();
        if (trainingClass != null && trainingClass.getId() != null) {
            return String.valueOf(trainingClass.getId());
        }
        return "";
    }

    private static String className(TrainingAttendance training) {
        TrainingClass trainingClass = training.getTrainingClass();
        if (trainingClass != null && trainingClass.getClassName() != null && !trainingClass.getClassName().isBlank()) {
            return trainingClass.getClassName();
        }
        return text(training.getClassName());
    }

    private static String trainerName(TrainingAttendance training) {
        TrainingClass trainingClass = training.getTrainingClass();
        if (trainingClass == null || trainingClass.getTrainer() == null) {
            return "";
        }
        User trainer = trainingClass.getTrainer();
        if (trainer.getEmployee() != null && trainer.getEmployee().getFullName() != null) {
            return trainer.getEmployee().getFullName();
        }
        return text(trainer.getUsername());
    }

    private static String trainingType(TrainingAttendance training) {
        TrainingClass trainingClass = training.getTrainingClass();
        if (trainingClass == null || trainingClass.getTrainingType() == null) {
            return "";
        }
        return trainingClass.getTrainingType().getLabel();
    }

    private static LocalTime classStart(TrainingAttendance training) {
        return training.getTrainingClass() == null ? null : training.getTrainingClass().getStartTime();
    }

    private static LocalTime classEnd(TrainingAttendance training) {
        return training.getTrainingClass() == null ? null : training.getTrainingClass().getEndTime();
    }

    private static int zero(Integer value) {
        return value == null ? 0 : value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static final class SummaryAccumulator {
        private final Employee employee;
        private int workSlots;
        private int workedSlots;
        private int absentSlots;
        private int lateRecords;
        private int lateMinutes;
        private int earlyRecords;
        private int earlyMinutes;
        private int workedMinutes;
        private int openCheckouts;
        private final List<String> notes = new ArrayList<>();

        private SummaryAccumulator(Employee employee) {
            this.employee = employee;
        }

        private void add(Attendance attendance, ShiftFacts facts) {
            workSlots++;
            if (attendance.getStatus() == AttendanceStatus.ABSENT) {
                absentSlots++;
            } else if (attendance.getCheckInTime() != null) {
                workedSlots++;
            }
            if (facts.late) {
                lateRecords++;
            }
            lateMinutes += zero(attendance.getLateMinutes());
            if (facts.early) {
                earlyRecords++;
            }
            earlyMinutes += zero(attendance.getEarlyLeaveMinutes());
            if (facts.workedMinutes != null) {
                workedMinutes += facts.workedMinutes;
            }
            if (facts.openCheckout) {
                openCheckouts++;
            }
            if (facts.needsReview && !notes.contains(facts.note)) {
                notes.add(facts.note);
            }
        }

        private AttendanceReportWorkbook.SummaryRow toRow(int order) {
            return AttendanceReportWorkbook.SummaryRow.builder()
                    .order(order)
                    .employeeId(employee.getId())
                    .fullName(text(employee.getFullName()))
                    .roleLabel(roleLabel(employee))
                    .workDays(workSlots)
                    .attendedDays(workedSlots)
                    .lateRecords(lateRecords)
                    .lateMinutes(lateMinutes)
                    .earlyRecords(earlyRecords)
                    .earlyMinutes(earlyMinutes)
                    .absentDays(absentSlots)
                    .absentRecords(absentSlots)
                    .workedMinutes(workedMinutes)
                    .openCheckouts(openCheckouts)
                    .note(String.join("; ", notes))
                    .build();
        }
    }

    private record ShiftFacts(boolean late,
                              boolean early,
                              boolean openCheckout,
                              Integer workedMinutes,
                              boolean needsReview,
                              String note) {
    }
}

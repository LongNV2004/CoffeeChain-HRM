package com.example.coffee_hrm.service.support;

import com.example.coffee_hrm.common.enums.AttendanceStatus;
import com.example.coffee_hrm.entity.TrainingAttendance;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Điểm danh từng học viên của một lớp đào tạo (trang Thông tin lớp).
 *
 * <ul>
 *   <li>Mỗi ngày trong khoảng ngày của lớp là một buổi, cùng giờ bắt đầu.</li>
 *   <li>Buổi được tính là đã diễn ra khi đã qua giờ bắt đầu + ngưỡng "không tham gia" (sau mốc đó Check-in
 *       cũng bị ghi Không tham gia), hoặc khi học viên đã Check-in buổi đó.</li>
 *   <li>Có mặt: đã Check-in buổi đó và không bị ghi Không tham gia. Còn lại của buổi đã diễn ra: Vắng mặt.</li>
 * </ul>
 * Trạng thái hiển thị là của buổi gần nhất đã diễn ra; kèm số buổi có mặt / số buổi đã diễn ra.
 */
public final class TrainingRollCall {

    public static final String PRESENT = "present";
    public static final String ABSENT = "absent";
    public static final String PENDING = "pending";

    public record Summary(String key, String label, int attendedSessions, int elapsedSessions) {

        static final Summary NOT_STARTED = new Summary(PENDING, "Chưa đến buổi học", 0, 0);
    }

    private TrainingRollCall() {
    }

    public static Map<Integer, Summary> summarize(Collection<Integer> employeeIds,
                                                  Collection<TrainingAttendance> records,
                                                  LocalDate startDate,
                                                  LocalDate endDate,
                                                  LocalTime startTime,
                                                  LocalDateTime now,
                                                  int absentThresholdMinutes) {
        Map<Integer, Summary> result = new HashMap<>();
        if (employeeIds == null || employeeIds.isEmpty()) {
            return result;
        }
        Map<Integer, Map<LocalDate, TrainingAttendance>> byEmployee = new HashMap<>();
        if (records != null) {
            for (TrainingAttendance record : records) {
                if (record.getEmployee() == null || record.getEmployee().getId() == null || record.getWorkDate() == null) {
                    continue;
                }
                byEmployee.computeIfAbsent(record.getEmployee().getId(), ignored -> new HashMap<>())
                        .put(record.getWorkDate(), record);
            }
        }
        List<LocalDate> elapsedDays = elapsedDays(startDate, endDate, startTime, now, absentThresholdMinutes);
        for (Integer employeeId : employeeIds) {
            Map<LocalDate, TrainingAttendance> own = byEmployee.getOrDefault(employeeId, Map.of());
            // Buổi đã diễn ra với học viên này: buổi đã qua ngưỡng, hoặc buổi học viên đã Check-in (kể cả hôm nay).
            Set<LocalDate> sessions = new HashSet<>(elapsedDays);
            for (Map.Entry<LocalDate, TrainingAttendance> entry : own.entrySet()) {
                if (withinClass(entry.getKey(), startDate, endDate) && present(entry.getValue())) {
                    sessions.add(entry.getKey());
                }
            }
            if (sessions.isEmpty()) {
                result.put(employeeId, Summary.NOT_STARTED);
                continue;
            }
            LocalDate latest = sessions.stream().max(LocalDate::compareTo).orElseThrow();
            int attended = (int) sessions.stream().filter(day -> present(own.get(day))).count();
            boolean presentLatest = present(own.get(latest));
            result.put(employeeId, new Summary(
                    presentLatest ? PRESENT : ABSENT,
                    presentLatest ? "Có mặt" : "Vắng mặt",
                    attended,
                    sessions.size()));
        }
        return result;
    }

    private static List<LocalDate> elapsedDays(LocalDate startDate, LocalDate endDate, LocalTime startTime,
                                               LocalDateTime now, int absentThresholdMinutes) {
        List<LocalDate> days = new ArrayList<>();
        if (startDate == null || endDate == null || startTime == null || now == null || endDate.isBefore(startDate)) {
            return days;
        }
        int threshold = Math.max(absentThresholdMinutes, 0);
        for (LocalDate day = startDate; !day.isAfter(endDate); day = day.plusDays(1)) {
            if (now.isBefore(LocalDateTime.of(day, startTime).plusMinutes(threshold))) {
                break;
            }
            days.add(day);
        }
        return days;
    }

    private static boolean withinClass(LocalDate day, LocalDate startDate, LocalDate endDate) {
        return startDate != null && endDate != null && !day.isBefore(startDate) && !day.isAfter(endDate);
    }

    private static boolean present(TrainingAttendance record) {
        return record != null
                && record.getCheckInTime() != null
                && record.getStatus() != AttendanceStatus.NOT_PARTICIPATED
                && record.getStatus() != AttendanceStatus.ABSENT;
    }
}

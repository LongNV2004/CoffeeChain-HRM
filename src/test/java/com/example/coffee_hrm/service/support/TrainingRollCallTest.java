package com.example.coffee_hrm.service.support;

import com.example.coffee_hrm.common.enums.AttendanceStatus;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.TrainingAttendance;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrainingRollCallTest {

    private static final LocalDate DAY1 = LocalDate.of(2026, 11, 10);
    private static final LocalDate DAY3 = LocalDate.of(2026, 11, 12);
    private static final LocalTime START = LocalTime.of(8, 0);
    private static final int THRESHOLD = 20;

    @Test
    void beforeTheFirstSessionNobodyIsMarked() {
        Map<Integer, TrainingRollCall.Summary> result = TrainingRollCall.summarize(
                List.of(1, 2), List.of(), DAY1, DAY3, START, DAY1.atTime(8, 19), THRESHOLD);

        assertEquals(TrainingRollCall.PENDING, result.get(1).key());
        assertEquals("Chưa đến buổi học", result.get(2).label());
    }

    @Test
    void checkedInLearnerIsPresentImmediatelyAndOthersAreAbsentAfterTheThreshold() {
        List<TrainingAttendance> records = List.of(record(1, DAY1, AttendanceStatus.PRESENT));

        Map<Integer, TrainingRollCall.Summary> during = TrainingRollCall.summarize(
                List.of(1, 2), records, DAY1, DAY3, START, DAY1.atTime(8, 5), THRESHOLD);
        assertEquals("Có mặt", during.get(1).label());
        assertEquals(TrainingRollCall.PENDING, during.get(2).key());

        Map<Integer, TrainingRollCall.Summary> after = TrainingRollCall.summarize(
                List.of(1, 2), records, DAY1, DAY3, START, DAY1.atTime(8, 20), THRESHOLD);
        assertEquals("Có mặt", after.get(1).label());
        assertEquals("Vắng mặt", after.get(2).label());
    }

    @Test
    void checkInBeyondTheThresholdCountsAsAbsent() {
        Map<Integer, TrainingRollCall.Summary> result = TrainingRollCall.summarize(
                List.of(1), List.of(record(1, DAY1, AttendanceStatus.NOT_PARTICIPATED)),
                DAY1, DAY1, START, DAY1.atTime(12, 0), THRESHOLD);

        assertEquals("Vắng mặt", result.get(1).label());
        assertEquals(0, result.get(1).attendedSessions());
    }

    @Test
    void multiDayClassShowsTheLatestSessionAndTheCount() {
        List<TrainingAttendance> records = List.of(
                record(1, DAY1, AttendanceStatus.PRESENT),
                record(1, DAY1.plusDays(1), AttendanceStatus.LATE));

        Map<Integer, TrainingRollCall.Summary> result = TrainingRollCall.summarize(
                List.of(1), records, DAY1, DAY3, START, DAY3.atTime(9, 0), THRESHOLD);

        // Buổi gần nhất (ngày 3) chưa Check-in: Vắng mặt; có mặt 2/3 buổi.
        assertEquals("Vắng mặt", result.get(1).label());
        assertEquals(2, result.get(1).attendedSessions());
        assertEquals(3, result.get(1).elapsedSessions());
    }

    private static TrainingAttendance record(int employeeId, LocalDate day, AttendanceStatus status) {
        return TrainingAttendance.builder()
                .employee(Employee.builder().id(employeeId).build())
                .workDate(day)
                .checkInTime(day.atTime(8, 1))
                .status(status)
                .build();
    }
}

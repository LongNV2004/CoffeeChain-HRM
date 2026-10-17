package com.example.coffee_hrm.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Hồ sơ xem chi tiết. Không chứa mật khẩu, hash, token hay dữ liệu xác thực.
 */
public record ManagedEmployeeDetail(
        EmployeeResponse employee,
        String certificationLabel,
        List<ShiftLine> shifts,
        List<AttendanceLine> attendances,
        List<TrainingLine> trainings,
        List<SkillLine> skills) {

    public record ShiftLine(LocalDate workDate, String shiftName, String timeRange) {
    }

    public record AttendanceLine(
            LocalDate workDate,
            String shiftName,
            String statusLabel,
            LocalDateTime checkInTime,
            LocalDateTime checkOutTime) {
    }

    public record TrainingLine(
            String className,
            LocalDate startDate,
            LocalDate endDate,
            String statusLabel,
            String resultLabel) {
    }

    public record SkillLine(String skillName, String statusLabel) {
    }
}

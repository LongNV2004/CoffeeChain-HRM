package com.example.coffee_hrm.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Getter
@Builder
public class ManagementTrainingView {

    private final Integer id;
    private final String className;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final LocalTime startTime;
    private final LocalTime endTime;
    private final String notes;
    private final boolean ended;
    private final boolean enrolled;
    private final String resultLabel;
    private final List<EnrollmentLine> enrollments;

    @Getter
    @Builder
    public static class EnrollmentLine {
        private final Integer employeeId;
        private final String fullName;
        private final String storeName;
        private final String resultLabel;
        private final LocalDateTime evaluatedAt;
        private final String evaluatedBy;
    }

    @Getter
    @Builder
    public static class Candidate {
        private final Integer employeeId;
        private final String fullName;
        private final String storeName;
        private final boolean managementCertified;
    }
}

package com.example.coffee_hrm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

@Entity
@Table(name = "MonthlyAttendanceReportVersions",
        uniqueConstraints = @UniqueConstraint(
                name = "UQ_MonthlyAttendanceReportVersions",
                columnNames = {"ReportId", "VersionNumber"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"report", "submittedBy"})
public class MonthlyAttendanceReportVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "VersionId")
    @EqualsAndHashCode.Include
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ReportId", nullable = false)
    private MonthlyAttendanceReport report;

    @Column(name = "VersionNumber", nullable = false)
    private Integer versionNumber;

    @Column(name = "SubmittedAt", nullable = false)
    private LocalDateTime submittedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SubmittedByUserId")
    private User submittedBy;

    @Column(name = "SubmittedByName", length = 100)
    private String submittedByName;

    @Column(name = "EmployeeCount", nullable = false)
    private Integer employeeCount;

    @Column(name = "ShiftRecordCount", nullable = false)
    private Integer shiftRecordCount;

    @Column(name = "TrainingRecordCount", nullable = false)
    private Integer trainingRecordCount;

    @Column(name = "LateCount", nullable = false)
    private Integer lateCount;

    @Column(name = "AbsentCount", nullable = false)
    private Integer absentCount;

    @Column(name = "OpenCheckoutCount", nullable = false)
    private Integer openCheckoutCount;

    @Column(name = "ReviewCount", nullable = false)
    private Integer reviewCount;
}

package com.example.coffee_hrm.entity;

import com.example.coffee_hrm.common.enums.AttendanceReportStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "MonthlyAttendanceReports",
        uniqueConstraints = @UniqueConstraint(
                name = "UQ_MonthlyAttendanceReports_StorePeriod",
                columnNames = {"StoreId", "ReportYear", "ReportMonth"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"store", "submittedBy"})
public class MonthlyAttendanceReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ReportId")
    @EqualsAndHashCode.Include
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "StoreId", nullable = false)
    private Store store;

    @Column(name = "ReportYear", nullable = false)
    private Integer reportYear;

    @Column(name = "ReportMonth", nullable = false)
    private Integer reportMonth;

    @Enumerated(EnumType.STRING)
    @Column(name = "Status", nullable = false, length = 20)
    private AttendanceReportStatus status;

    @Column(name = "VersionNumber", nullable = false)
    @Builder.Default
    private Integer versionNumber = 0;

    @Column(name = "DraftGeneratedAt")
    private LocalDateTime draftGeneratedAt;

    @Column(name = "SubmittedAt")
    private LocalDateTime submittedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SubmittedByUserId")
    private User submittedBy;

    @Column(name = "SubmittedByName", length = 100)
    private String submittedByName;

    @Column(name = "PreparedByName", length = 100)
    private String preparedByName;

    @Column(name = "EmployeeCount", nullable = false)
    @Builder.Default
    private Integer employeeCount = 0;

    @Column(name = "ShiftRecordCount", nullable = false)
    @Builder.Default
    private Integer shiftRecordCount = 0;

    @Column(name = "TrainingRecordCount", nullable = false)
    @Builder.Default
    private Integer trainingRecordCount = 0;

    @Column(name = "LateCount", nullable = false)
    @Builder.Default
    private Integer lateCount = 0;

    @Column(name = "AbsentCount", nullable = false)
    @Builder.Default
    private Integer absentCount = 0;

    @Column(name = "OpenCheckoutCount", nullable = false)
    @Builder.Default
    private Integer openCheckoutCount = 0;

    @Column(name = "ReviewCount", nullable = false)
    @Builder.Default
    private Integer reviewCount = 0;

    @CreationTimestamp
    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "UpdatedAt")
    private LocalDateTime updatedAt;
}

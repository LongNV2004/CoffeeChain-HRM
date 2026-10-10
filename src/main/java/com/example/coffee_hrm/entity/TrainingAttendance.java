package com.example.coffee_hrm.entity;

import com.example.coffee_hrm.common.enums.AttendanceStatus;
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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "TrainingAttendances",
        uniqueConstraints = @UniqueConstraint(
                name = "UQ_TrainingAttendances_EmpClassDate",
                columnNames = {"EmployeeId", "ClassId", "WorkDate"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"employee", "trainingClass", "store"})
public class TrainingAttendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "TrainingAttendanceId")
    @EqualsAndHashCode.Include
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "EmployeeId", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ClassId", nullable = false)
    private TrainingClass trainingClass;

    @Column(name = "WorkDate", nullable = false)
    private LocalDate workDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "StoreId")
    private Store store;

    @Column(name = "ClassName", length = 150)
    private String className;

    @Column(name = "ScheduledStartTime")
    private LocalTime scheduledStartTime;

    @Column(name = "ScheduledEndTime")
    private LocalTime scheduledEndTime;

    @Column(name = "CheckInTime")
    private LocalDateTime checkInTime;

    @Column(name = "CheckOutTime")
    private LocalDateTime checkOutTime;

    @Column(name = "LateMinutes", nullable = false)
    @Builder.Default
    private Integer lateMinutes = 0;

    @Column(name = "EarlyLeaveMinutes", nullable = false)
    @Builder.Default
    private Integer earlyLeaveMinutes = 0;

    @Column(name = "WorkingMinutes")
    private Integer workingMinutes;

    @Column(name = "Status", nullable = false, length = 20)
    @Builder.Default
    private AttendanceStatus status = AttendanceStatus.PRESENT;

    @Column(name = "CheckInLatitude", precision = 10, scale = 7)
    private BigDecimal checkInLatitude;

    @Column(name = "CheckInLongitude", precision = 10, scale = 7)
    private BigDecimal checkInLongitude;

    @Column(name = "CheckOutLatitude", precision = 10, scale = 7)
    private BigDecimal checkOutLatitude;

    @Column(name = "CheckOutLongitude", precision = 10, scale = 7)
    private BigDecimal checkOutLongitude;

    @CreationTimestamp
    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "UpdatedAt")
    private LocalDateTime updatedAt;
}

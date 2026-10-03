package com.example.coffee_hrm.entity;

import com.example.coffee_hrm.common.enums.AttendanceStatus;
import jakarta.persistence.*;
import lombok.*;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "Attendances",
        uniqueConstraints = @UniqueConstraint(columnNames = {"EmployeeId", "ShiftId", "WorkDate"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"employee", "store", "shift"})
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "AttendanceId")
    @EqualsAndHashCode.Include
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "EmployeeId", nullable = false)
    private Employee employee;

    @Column(name = "WorkDate", nullable = false)
    private LocalDate workDate;

    @Column(name = "CheckInTime")
    private LocalDateTime checkInTime;

    @Column(name = "CheckOutTime")
    private LocalDateTime checkOutTime;

    @Column(name = "TotalHours", insertable = false, updatable = false, precision = 5, scale = 2)
    private BigDecimal totalHours;

    @Column(name = "Status", nullable = false, length = 20)
    @Builder.Default
    private AttendanceStatus status = AttendanceStatus.ABSENT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "StoreId")
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ShiftId")
    private Shift shift;

    @Column(name = "ScheduledShiftName", length = 50)
    private String scheduledShiftName;

    @Column(name = "ScheduledStartTime")
    private LocalTime scheduledStartTime;

    @Column(name = "ScheduledEndTime")
    private LocalTime scheduledEndTime;

    @Column(name = "CheckInIp", length = 45)
    private String checkInIp;

    @Column(name = "CheckOutIp", length = 45)
    private String checkOutIp;

    @Column(name = "WorkingMinutes")
    private Integer workingMinutes;

    @Column(name = "LateMinutes", nullable = false)
    @Builder.Default
    private Integer lateMinutes = 0;

    @Column(name = "EarlyLeaveMinutes", nullable = false)
    @Builder.Default
    private Integer earlyLeaveMinutes = 0;

    @CreationTimestamp
    @Column(name = "CreatedAt", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "UpdatedAt")
    private LocalDateTime updatedAt;
}


package com.example.coffee_hrm.entity;

import com.example.coffee_hrm.common.enums.AttendanceStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "Attendances",
        uniqueConstraints = @UniqueConstraint(columnNames = {"EmployeeId", "WorkDate"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = "employee")
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
}


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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "PayrollShiftLines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "employeeLine")
public class PayrollShiftLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ShiftLineId")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "EmployeeLineId", nullable = false)
    private PayrollEmployeeLine employeeLine;

    @Column(name = "AssignmentId")
    private Integer assignmentId;

    @Column(name = "AttendanceId")
    private Integer attendanceId;

    @Column(name = "WorkDate", nullable = false)
    private LocalDate workDate;

    @Column(name = "ShiftName", nullable = false, length = 50)
    private String shiftName;

    @Column(name = "ScheduledStartTime")
    private LocalTime scheduledStartTime;

    @Column(name = "ScheduledEndTime")
    private LocalTime scheduledEndTime;

    @Column(name = "CheckInTime")
    private LocalDateTime checkInTime;

    @Column(name = "CheckOutTime")
    private LocalDateTime checkOutTime;

    @Column(name = "AttendanceStatusLabel", length = 50)
    private String attendanceStatusLabel;

    @Column(name = "ScheduledHours", nullable = false)
    private Integer scheduledHours;

    @Column(name = "LateDeducted", nullable = false)
    private Boolean lateDeducted;

    @Column(name = "EarlyDeducted", nullable = false)
    private Boolean earlyDeducted;

    @Column(name = "PayableHours", nullable = false)
    private Integer payableHours;

    @Column(name = "HourlyRate", precision = 18, scale = 2)
    private BigDecimal hourlyRate;

    @Column(name = "ShiftSalary", nullable = false, precision = 18, scale = 2)
    private BigDecimal shiftSalary;

    @Column(name = "Payable", nullable = false)
    private Boolean payable;

    @Column(name = "UnpaidReason", length = 200)
    private String unpaidReason;
}

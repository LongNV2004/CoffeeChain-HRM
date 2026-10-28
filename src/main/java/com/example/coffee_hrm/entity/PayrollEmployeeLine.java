package com.example.coffee_hrm.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "PayrollEmployeeLines", uniqueConstraints =
        @UniqueConstraint(columnNames = {"PayrollId", "EmployeeId"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"payroll", "employee", "shiftLines"})
public class PayrollEmployeeLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "EmployeeLineId")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "PayrollId", nullable = false)
    private MonthlyPayroll payroll;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "EmployeeId", nullable = false)
    private Employee employee;

    @Column(name = "AssignedShiftCount", nullable = false)
    private Integer assignedShiftCount;

    @Column(name = "ValidShiftCount", nullable = false)
    private Integer validShiftCount;

    @Column(name = "ReviewShiftCount", nullable = false)
    private Integer reviewShiftCount;

    @Column(name = "PayableHours", nullable = false)
    private Integer payableHours;

    @Column(name = "HourlyRate", precision = 18, scale = 2)
    private BigDecimal hourlyRate;

    @Column(name = "ExpectedSalary", nullable = false, precision = 18, scale = 2)
    private BigDecimal expectedSalary;

    @Builder.Default
    @BatchSize(size = 32)
    @OneToMany(mappedBy = "employeeLine", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PayrollShiftLine> shiftLines = new ArrayList<>();

    public void addShift(PayrollShiftLine line) {
        if (shiftLines == null) {
            shiftLines = new ArrayList<>();
        }
        line.setEmployeeLine(this);
        shiftLines.add(line);
    }
}

package com.example.coffee_hrm.entity;

import com.example.coffee_hrm.common.enums.PayrollStatus;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "MonthlyPayrolls", uniqueConstraints =
        @UniqueConstraint(columnNames = {"StoreId", "PayrollYear", "PayrollMonth"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"store", "calculatedBy", "lockedBy", "employeeLines"})
public class MonthlyPayroll {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PayrollId")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "StoreId", nullable = false)
    private Store store;

    @Column(name = "PayrollYear", nullable = false)
    private Integer payrollYear;

    @Column(name = "PayrollMonth", nullable = false)
    private Integer payrollMonth;

    @Enumerated(EnumType.STRING)
    @Column(name = "Status", nullable = false, length = 20)
    private PayrollStatus status;

    @Column(name = "CalculatedAt", nullable = false)
    private LocalDateTime calculatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CalculatedByUserId")
    private User calculatedBy;

    @Column(name = "LockedAt")
    private LocalDateTime lockedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "LockedByUserId")
    private User lockedBy;

    @Builder.Default
    @OneToMany(mappedBy = "payroll", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PayrollEmployeeLine> employeeLines = new ArrayList<>();

    public void replaceEmployees(List<PayrollEmployeeLine> lines) {
        if (employeeLines == null) {
            employeeLines = new ArrayList<>();
        }
        for (PayrollEmployeeLine line : employeeLines) {
            if (line.getShiftLines() != null) {
                line.getShiftLines().size();
            }
        }
        employeeLines.clear();
        if (lines == null) {
            return;
        }
        for (PayrollEmployeeLine line : lines) {
            line.setPayroll(this);
            employeeLines.add(line);
        }
    }
}

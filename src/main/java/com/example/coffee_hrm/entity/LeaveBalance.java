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

@Entity
@Table(name = "LeaveBalances", uniqueConstraints = @UniqueConstraint(columnNames = {"EmployeeId", "Year"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = "employee")
public class LeaveBalance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "BalanceId")
    @EqualsAndHashCode.Include
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "EmployeeId", nullable = false)
    private Employee employee;

    @Column(name = "Year", nullable = false)
    private Integer year;

    @Column(name = "TotalDays", nullable = false)
    private Integer totalDays;

    @Column(name = "UsedDays", nullable = false)
    @Builder.Default
    private Integer usedDays = 0;

    @Column(name = "RemainingDays", nullable = false)
    private Integer remainingDays;

    /** Separate from the day columns. Null until an admin slot allowance is applied. */
    @Column(name = "TotalSlots")
    private Integer totalSlots;

    @Column(name = "UsedSlots", nullable = false)
    @Builder.Default
    private Integer usedSlots = 0;

    @Column(name = "RemainingSlots")
    private Integer remainingSlots;
}

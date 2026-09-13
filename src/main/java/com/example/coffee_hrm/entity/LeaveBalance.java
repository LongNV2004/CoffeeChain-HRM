package com.example.coffee_hrm.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "LeaveBalances")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = "employee")
public class LeaveBalance {

    @EqualsAndHashCode.Include
    @EmbeddedId
    private LeaveBalanceId id;

    @MapsId("employeeId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "EmployeeId", nullable = false)
    private Employee employee;

    @Column(name = "TotalDays", nullable = false)
    private Integer totalDays;

    @Column(name = "UsedDays", nullable = false)
    @Builder.Default
    private Integer usedDays = 0;

    @Transient
    public int getRemainingDays() {
        return totalDays - usedDays;
    }
}


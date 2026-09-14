package com.example.coffee_hrm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class LeaveBalanceId implements Serializable {

    @Column(name = "EmployeeId")
    private Integer employeeId;

    @Column(name = "Year")
    private Short year;
}

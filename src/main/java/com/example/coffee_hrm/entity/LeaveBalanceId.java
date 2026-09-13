package com.example.coffee_hrm.entity;

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

    private Integer employeeId;

    private Short year;
}
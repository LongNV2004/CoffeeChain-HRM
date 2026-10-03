package com.example.coffee_hrm.repository;

import lombok.Getter;

/**
 * Một kỹ năng nhân viên đã đạt trong lớp đào tạo đã được duyệt.
 */
@Getter
public class EmployeePassedSkill {

    private final Integer employeeId;
    private final Integer skillId;

    public EmployeePassedSkill(Integer employeeId, Integer skillId) {
        this.employeeId = employeeId;
        this.skillId = skillId;
    }
}

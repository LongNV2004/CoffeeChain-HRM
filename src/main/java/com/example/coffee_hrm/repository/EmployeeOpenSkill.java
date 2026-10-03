package com.example.coffee_hrm.repository;

import lombok.Getter;

/**
 * Kỹ năng nhân viên đang học ở lớp còn hiệu lực, chưa đánh giá và chưa bị từ chối.
 */
@Getter
public class EmployeeOpenSkill {

    private final Integer employeeId;
    private final Integer skillId;
    private final String skillName;
    private final String className;

    public EmployeeOpenSkill(Integer employeeId, Integer skillId, String skillName, String className) {
        this.employeeId = employeeId;
        this.skillId = skillId;
        this.skillName = skillName;
        this.className = className;
    }
}

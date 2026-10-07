package com.example.coffee_hrm.repository;

import com.example.coffee_hrm.common.enums.TrainingClassStatus;
import com.example.coffee_hrm.common.enums.TrainingResult;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Một lần nhân viên học một kỹ năng trong lớp chưa bị từ chối.
 * Chứng chỉ và lần đào tạo gần nhất được tính riêng theo từng dòng này.
 */
@Getter
public class EmployeeSkillTrainingFact {

    private final Integer employeeId;
    private final Integer skillId;
    private final String skillName;
    private final TrainingResult result;
    private final LocalDateTime evaluatedAt;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final LocalTime endTime;
    private final TrainingClassStatus classStatus;

    public EmployeeSkillTrainingFact(Integer employeeId,
                                     Integer skillId,
                                     String skillName,
                                     TrainingResult result,
                                     LocalDateTime evaluatedAt,
                                     LocalDate startDate,
                                     LocalDate endDate,
                                     LocalTime endTime,
                                     TrainingClassStatus classStatus) {
        this.employeeId = employeeId;
        this.skillId = skillId;
        this.skillName = skillName;
        this.result = result;
        this.evaluatedAt = evaluatedAt;
        this.startDate = startDate;
        this.endDate = endDate;
        this.endTime = endTime;
        this.classStatus = classStatus;
    }
}

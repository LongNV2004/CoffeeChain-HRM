package com.example.coffee_hrm.dto.response;

import com.example.coffee_hrm.common.enums.TrainingSkillStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrainingSkillResponse {

    private Integer id;
    private String skillName;
    private String description;
    private String requirements;
    private TrainingSkillStatus status;
    private String createdByName;
    private LocalDateTime createdAt;
}

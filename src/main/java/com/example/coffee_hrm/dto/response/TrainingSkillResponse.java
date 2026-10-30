package com.example.coffee_hrm.dto.response;

import com.example.coffee_hrm.common.enums.CertificateKind;
import com.example.coffee_hrm.common.enums.TrainingSkillStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

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
    private CertificateKind certificateKind;
    private String createdByName;
    private LocalDateTime createdAt;
    @Builder.Default
    private List<SkillDocumentResponse> documents = List.of();
}

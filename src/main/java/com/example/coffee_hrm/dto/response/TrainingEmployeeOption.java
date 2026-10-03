package com.example.coffee_hrm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrainingEmployeeOption {

    private Integer employeeId;
    private String fullName;
    private String email;
    private Integer storeId;
    private String storeName;

    @Builder.Default
    private List<Integer> certifiedSkillIds = List.of();

    private Boolean hasCertificate;

    /** Kỹ năng nhân viên đang học ở lớp còn hiệu lực, chưa đánh giá. */
    @Builder.Default
    private List<Integer> studyingSkillIds = List.of();

    public String getCertifiedSkillIdsCsv() {
        return toCsv(certifiedSkillIds);
    }

    public String getStudyingSkillIdsCsv() {
        return toCsv(studyingSkillIds);
    }

    private static String toCsv(List<Integer> skillIds) {
        if (skillIds == null || skillIds.isEmpty()) {
            return "";
        }
        return skillIds.stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }
}

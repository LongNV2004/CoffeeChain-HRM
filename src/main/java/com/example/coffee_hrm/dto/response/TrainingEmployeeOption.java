package com.example.coffee_hrm.dto.response;

import com.example.coffee_hrm.common.enums.CertificationStatus;
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

    /** Lịch sử chứng chỉ theo từng kỹ năng nhân viên đã từng học. */
    @Builder.Default
    private List<EmployeeSkillStatusLine> skillStatuses = List.of();

    public String getSkillStatusesJson() {
        if (skillStatuses == null || skillStatuses.isEmpty()) {
            return "[]";
        }
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < skillStatuses.size(); i++) {
            EmployeeSkillStatusLine line = skillStatuses.get(i);
            if (i > 0) {
                json.append(',');
            }
            boolean certified = line.getCertificationStatus() == CertificationStatus.CERTIFIED;
            json.append('{')
                    .append("\"id\":").append(line.getSkillId() == null ? "null" : line.getSkillId())
                    .append(",\"name\":").append(jsonQuote(line.getSkillName()))
                    .append(",\"certified\":").append(certified)
                    .append(",\"certifiedDate\":").append(jsonQuote(line.getCertifiedDateLabel()))
                    .append(",\"latest\":").append(jsonQuote(line.getLatestResultCode()))
                    .append(",\"latestDate\":").append(jsonQuote(line.getLatestDateLabel()))
                    .append('}');
        }
        json.append(']');
        return json.toString();
    }

    private static String jsonQuote(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                + "\"";
    }

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

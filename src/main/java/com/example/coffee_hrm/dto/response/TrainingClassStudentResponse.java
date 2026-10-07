package com.example.coffee_hrm.dto.response;

import com.example.coffee_hrm.common.enums.CertificationStatus;
import com.example.coffee_hrm.common.enums.TrainingResult;
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
public class TrainingClassStudentResponse {

    private Integer employeeId;
    private Integer storeId;
    private String storeName;
    private String fullName;
    private String email;
    private String phone;
    private TrainingResult result;
    private String resultLabel;
    private CertificationStatus certificationStatus;
    private String certificationLabel;
    private String evaluationNote;

    /** Chứng chỉ và lần đào tạo gần nhất của từng kỹ năng đang xét. */
    @Builder.Default
    private List<EmployeeSkillStatusLine> skillStatuses = List.of();

    /**
     * Có thể đăng ký / Có thể học lại / Đang đào tạo.
     * Null khi chưa chọn kỹ năng của lớp.
     */
    private String eligibilityLabel;

    /** Kỹ năng nhân viên đã đạt. Dùng khi kỹ năng lớp được chọn sau trên giao diện. */
    @Builder.Default
    private List<Integer> certifiedSkillIds = List.of();

    /**
     * true khi nhân viên đã có chứng chỉ cho mọi kỹ năng đang xét.
     * null khi chưa chọn kỹ năng nên chưa xác định được.
     */
    private Boolean hasCertificate;

    /** Kỹ năng nhân viên đang học ở lớp còn hiệu lực, chưa đánh giá. */
    @Builder.Default
    private List<Integer> studyingSkillIds = List.of();

    /**
     * true khi lớp đang xét trùng một kỹ năng nhân viên đang học ở lớp khác còn hiệu lực.
     * null khi chưa chọn kỹ năng nên chưa xác định được.
     */
    private Boolean studyingSameSkill;

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

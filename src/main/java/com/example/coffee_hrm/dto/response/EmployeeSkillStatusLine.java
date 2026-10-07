package com.example.coffee_hrm.dto.response;

import com.example.coffee_hrm.common.enums.CertificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Chứng chỉ và lần đào tạo gần nhất của một kỹ năng trên một nhân viên.
 * Hai thông tin này độc lập: NOT PASS lần học lại không xóa chứng chỉ đang có.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeSkillStatusLine {

    private Integer skillId;
    private String skillName;
    private CertificationStatus certificationStatus;
    /** Ví dụ: {@code CERTIFIED - Pha chế}. */
    private String certificationLabel;
    /** {@code dd/MM/yyyy} của lần PASS gần nhất. Null khi chưa có chứng chỉ. */
    private String certifiedDateLabel;
    /** {@code PASS}, {@code NOT_PASS} hoặc {@code IN_TRAINING}. */
    private String latestResultCode;
    /** {@code PASS}, {@code NOT PASS} hoặc {@code ĐANG ĐÀO TẠO}. */
    private String latestResultLabel;
    private String latestDateLabel;

    public String getLatestSummary() {
        String name = skillName == null || skillName.isBlank() ? "Kỹ năng" : skillName;
        if (latestResultLabel == null || latestResultLabel.isBlank()) {
            return name + ": —";
        }
        if (latestDateLabel == null || latestDateLabel.isBlank()) {
            return name + ": " + latestResultLabel;
        }
        return name + ": " + latestResultLabel + " · " + latestDateLabel;
    }
}

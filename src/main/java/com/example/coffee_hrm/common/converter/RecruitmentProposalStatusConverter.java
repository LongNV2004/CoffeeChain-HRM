package com.example.coffee_hrm.common.converter;

import com.example.coffee_hrm.common.enums.RecruitmentProposalStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class RecruitmentProposalStatusConverter implements AttributeConverter<RecruitmentProposalStatus, String> {

    @Override
    public String convertToDatabaseColumn(RecruitmentProposalStatus status) {
        return status == null ? null : status.getDbValue();
    }

    @Override
    public RecruitmentProposalStatus convertToEntityAttribute(String value) {
        if (value == null) {
            return null;
        }
        for (RecruitmentProposalStatus status : RecruitmentProposalStatus.values()) {
            if (status.getDbValue().equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown RecruitmentProposalStatus: " + value);
    }
}

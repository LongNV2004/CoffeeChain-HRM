package com.example.coffee_hrm.common.converter;

import com.example.coffee_hrm.common.enums.ApprovalStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ApprovalStatusConverter implements AttributeConverter<ApprovalStatus, String> {

    @Override
    public String convertToDatabaseColumn(ApprovalStatus attribute) {
        return attribute == null ? null : attribute.getDbValue();
    }

    @Override
    public ApprovalStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ApprovalStatus.fromDbValue(dbData);
    }
}

package com.example.coffee_hrm.common.converter;

import com.example.coffee_hrm.common.enums.AssignmentStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class AssignmentStatusConverter implements AttributeConverter<AssignmentStatus, String> {

    @Override
    public String convertToDatabaseColumn(AssignmentStatus attribute) {
        return attribute == null ? null : attribute.getDbValue();
    }

    @Override
    public AssignmentStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : AssignmentStatus.fromDbValue(dbData);
    }
}

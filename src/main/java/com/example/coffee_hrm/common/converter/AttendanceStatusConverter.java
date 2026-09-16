package com.example.coffee_hrm.common.converter;

import com.example.coffee_hrm.common.enums.AttendanceStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class AttendanceStatusConverter implements AttributeConverter<AttendanceStatus, String> {

    @Override
    public String convertToDatabaseColumn(AttendanceStatus attribute) {
        return attribute == null ? null : attribute.getDbValue();
    }

    @Override
    public AttendanceStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : AttendanceStatus.fromDbValue(dbData);
    }
}

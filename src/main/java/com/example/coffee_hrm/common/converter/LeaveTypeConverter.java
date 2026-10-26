package com.example.coffee_hrm.common.converter;

import com.example.coffee_hrm.common.enums.LeaveType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class LeaveTypeConverter implements AttributeConverter<LeaveType, String> {

    @Override
    public String convertToDatabaseColumn(LeaveType attribute) {
        return attribute == null ? null : attribute.getDbValue();
    }

    @Override
    public LeaveType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : LeaveType.fromDbValue(dbData);
    }
}

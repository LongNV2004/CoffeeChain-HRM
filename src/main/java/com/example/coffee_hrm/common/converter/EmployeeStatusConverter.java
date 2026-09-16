package com.example.coffee_hrm.common.converter;

import com.example.coffee_hrm.common.enums.EmployeeStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class EmployeeStatusConverter implements AttributeConverter<EmployeeStatus, String> {

    @Override
    public String convertToDatabaseColumn(EmployeeStatus attribute) {
        return attribute == null ? null : attribute.getDbValue();
    }

    @Override
    public EmployeeStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : EmployeeStatus.fromDbValue(dbData);
    }
}

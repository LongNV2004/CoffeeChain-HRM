package com.example.coffee_hrm.common.converter;

import com.example.coffee_hrm.common.enums.TrainingType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class TrainingTypeConverter implements AttributeConverter<TrainingType, String> {

    @Override
    public String convertToDatabaseColumn(TrainingType attribute) {
        return attribute == null ? null : attribute.getDbValue();
    }

    @Override
    public TrainingType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : TrainingType.fromDbValue(dbData);
    }
}

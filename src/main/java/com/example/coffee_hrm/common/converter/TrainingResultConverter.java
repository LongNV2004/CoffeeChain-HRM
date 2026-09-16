package com.example.coffee_hrm.common.converter;

import com.example.coffee_hrm.common.enums.TrainingResult;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class TrainingResultConverter implements AttributeConverter<TrainingResult, String> {

    @Override
    public String convertToDatabaseColumn(TrainingResult attribute) {
        return attribute == null ? null : attribute.getDbValue();
    }

    @Override
    public TrainingResult convertToEntityAttribute(String dbData) {
        return dbData == null ? null : TrainingResult.fromDbValue(dbData);
    }
}

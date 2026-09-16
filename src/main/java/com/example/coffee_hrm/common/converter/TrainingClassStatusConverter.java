package com.example.coffee_hrm.common.converter;

import com.example.coffee_hrm.common.enums.TrainingClassStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class TrainingClassStatusConverter implements AttributeConverter<TrainingClassStatus, String> {

    @Override
    public String convertToDatabaseColumn(TrainingClassStatus attribute) {
        return attribute == null ? null : attribute.getDbValue();
    }

    @Override
    public TrainingClassStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : TrainingClassStatus.fromDbValue(dbData);
    }
}

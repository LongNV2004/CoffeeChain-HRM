package com.example.coffee_hrm.common.converter;

import com.example.coffee_hrm.common.enums.TrainingSkillStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class TrainingSkillStatusConverter implements AttributeConverter<TrainingSkillStatus, String> {

    @Override
    public String convertToDatabaseColumn(TrainingSkillStatus attribute) {
        return attribute == null ? null : attribute.getDbValue();
    }

    @Override
    public TrainingSkillStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : TrainingSkillStatus.fromDbValue(dbData);
    }
}

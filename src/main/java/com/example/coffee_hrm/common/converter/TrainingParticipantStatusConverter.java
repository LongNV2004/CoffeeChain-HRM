package com.example.coffee_hrm.common.converter;

import com.example.coffee_hrm.common.enums.TrainingParticipantStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class TrainingParticipantStatusConverter implements AttributeConverter<TrainingParticipantStatus, String> {

    @Override
    public String convertToDatabaseColumn(TrainingParticipantStatus attribute) {
        return attribute == null ? null : attribute.getDbValue();
    }

    @Override
    public TrainingParticipantStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : TrainingParticipantStatus.fromDbValue(dbData);
    }
}

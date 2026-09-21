package com.example.coffee_hrm.common.converter;

import com.example.coffee_hrm.common.enums.NotificationType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class NotificationTypeConverter implements AttributeConverter<NotificationType, String> {

    @Override
    public String convertToDatabaseColumn(NotificationType attribute) {
        return attribute == null ? null : attribute.getDbValue();
    }

    @Override
    public NotificationType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : NotificationType.fromDbValue(dbData);
    }
}

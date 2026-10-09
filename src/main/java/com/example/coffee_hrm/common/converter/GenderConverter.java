package com.example.coffee_hrm.common.converter;

import com.example.coffee_hrm.common.enums.Gender;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class GenderConverter implements AttributeConverter<Gender, String> {

    @Override
    public String convertToDatabaseColumn(Gender gender) {
        return gender == null ? null : gender.getDbValue();
    }

    @Override
    public Gender convertToEntityAttribute(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (Gender gender : Gender.values()) {
            if (gender.getDbValue().equals(value)) {
                return gender;
            }
        }
        throw new IllegalArgumentException("Unknown Gender: " + value);
    }
}

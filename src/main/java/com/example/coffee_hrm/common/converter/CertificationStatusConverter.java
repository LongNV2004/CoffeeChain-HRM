package com.example.coffee_hrm.common.converter;

import com.example.coffee_hrm.common.enums.CertificationStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CertificationStatusConverter implements AttributeConverter<CertificationStatus, String> {

    @Override
    public String convertToDatabaseColumn(CertificationStatus attribute) {
        return attribute == null ? null : attribute.getDbValue();
    }

    @Override
    public CertificationStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : CertificationStatus.fromDbValue(dbData);
    }
}

package com.example.coffee_hrm.common.converter;

import com.example.coffee_hrm.common.enums.CertificateKind;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CertificateKindConverter implements AttributeConverter<CertificateKind, String> {

    @Override
    public String convertToDatabaseColumn(CertificateKind attribute) {
        return attribute == null ? null : attribute.getDbValue();
    }

    @Override
    public CertificateKind convertToEntityAttribute(String dbData) {
        return dbData == null ? null : CertificateKind.fromDbValue(dbData);
    }
}

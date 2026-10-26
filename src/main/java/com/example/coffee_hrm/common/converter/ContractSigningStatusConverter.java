package com.example.coffee_hrm.common.converter;

import com.example.coffee_hrm.common.enums.ContractSigningStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ContractSigningStatusConverter implements AttributeConverter<ContractSigningStatus, String> {

    @Override
    public String convertToDatabaseColumn(ContractSigningStatus status) {
        return status == null ? null : status.getDbValue();
    }

    @Override
    public ContractSigningStatus convertToEntityAttribute(String value) {
        if (value == null) {
            return null;
        }
        for (ContractSigningStatus status : ContractSigningStatus.values()) {
            if (status.getDbValue().equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown ContractSigningStatus: " + value);
    }
}

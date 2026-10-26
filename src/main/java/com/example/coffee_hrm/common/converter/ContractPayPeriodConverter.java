package com.example.coffee_hrm.common.converter;

import com.example.coffee_hrm.common.enums.ContractPayPeriod;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ContractPayPeriodConverter implements AttributeConverter<ContractPayPeriod, String> {

    @Override
    public String convertToDatabaseColumn(ContractPayPeriod value) {
        return value == null ? null : value.getDbValue();
    }

    @Override
    public ContractPayPeriod convertToEntityAttribute(String value) {
        if (value == null) {
            return null;
        }
        for (ContractPayPeriod period : ContractPayPeriod.values()) {
            if (period.getDbValue().equals(value)) {
                return period;
            }
        }
        throw new IllegalArgumentException("Unknown ContractPayPeriod: " + value);
    }
}

package com.example.coffee_hrm.common.converter;

import com.example.coffee_hrm.common.enums.ContractPaymentMethod;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ContractPaymentMethodConverter implements AttributeConverter<ContractPaymentMethod, String> {

    @Override
    public String convertToDatabaseColumn(ContractPaymentMethod value) {
        return value == null ? null : value.getDbValue();
    }

    @Override
    public ContractPaymentMethod convertToEntityAttribute(String value) {
        if (value == null) {
            return null;
        }
        for (ContractPaymentMethod method : ContractPaymentMethod.values()) {
            if (method.getDbValue().equals(value)) {
                return method;
            }
        }
        throw new IllegalArgumentException("Unknown ContractPaymentMethod: " + value);
    }
}

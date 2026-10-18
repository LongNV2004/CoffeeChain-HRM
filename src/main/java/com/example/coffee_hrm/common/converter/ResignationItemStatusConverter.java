package com.example.coffee_hrm.common.converter;

import com.example.coffee_hrm.common.enums.ResignationItemStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ResignationItemStatusConverter implements AttributeConverter<ResignationItemStatus, String> {

    @Override
    public String convertToDatabaseColumn(ResignationItemStatus status) {
        return status == null ? null : status.getDbValue();
    }

    @Override
    public ResignationItemStatus convertToEntityAttribute(String value) {
        if (value == null) {
            return null;
        }
        for (ResignationItemStatus status : ResignationItemStatus.values()) {
            if (status.getDbValue().equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown ResignationItemStatus: " + value);
    }
}

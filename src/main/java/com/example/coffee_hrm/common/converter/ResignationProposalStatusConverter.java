package com.example.coffee_hrm.common.converter;

import com.example.coffee_hrm.common.enums.ResignationProposalStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ResignationProposalStatusConverter implements AttributeConverter<ResignationProposalStatus, String> {

    @Override
    public String convertToDatabaseColumn(ResignationProposalStatus status) {
        return status == null ? null : status.getDbValue();
    }

    @Override
    public ResignationProposalStatus convertToEntityAttribute(String value) {
        if (value == null) {
            return null;
        }
        for (ResignationProposalStatus status : ResignationProposalStatus.values()) {
            if (status.getDbValue().equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown ResignationProposalStatus: " + value);
    }
}

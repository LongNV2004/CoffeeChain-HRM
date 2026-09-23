package com.example.coffee_hrm.common.converter;
import com.example.coffee_hrm.common.enums.RecruitmentStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
@Converter(autoApply = true)
public class RecruitmentStatusConverter
        implements AttributeConverter<RecruitmentStatus, String> {
    @Override
    public String convertToDatabaseColumn(RecruitmentStatus status) {
        return status == null ? null : status.getDbValue();
    }
    @Override
    public RecruitmentStatus convertToEntityAttribute(String value) {
        if(value == null){
            return null;
        }
        for(RecruitmentStatus status : RecruitmentStatus.values()){
            if(status.getDbValue().equals(value)){
                return status;
            }
        }
        throw new IllegalArgumentException(
                "Unknown RecruitmentStatus: " + value
        );
    }
}
package com.example.coffee_hrm.dto.response;

import com.example.coffee_hrm.common.enums.CertificationStatus;
import com.example.coffee_hrm.common.enums.TrainingResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrainingClassStudentResponse {

    private Integer employeeId;
    private Integer storeId;
    private String storeName;
    private String fullName;
    private String email;
    private String phone;
    private TrainingResult result;
    private String resultLabel;
    private CertificationStatus certificationStatus;
    private String certificationLabel;
    private String evaluationNote;
}

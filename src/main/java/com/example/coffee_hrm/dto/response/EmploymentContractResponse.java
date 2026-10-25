package com.example.coffee_hrm.dto.response;

import com.example.coffee_hrm.common.enums.ContractSigningStatus;
import com.example.coffee_hrm.common.enums.ContractValidityStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class EmploymentContractResponse {

    private final Integer id;
    private final String contractCode;
    private final Integer employeeId;
    private final String employeeName;
    private final Integer storeId;
    private final String storeName;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final BigDecimal salary;
    private final ContractSigningStatus signingStatus;
    private final String signingStatusLabel;
    private final LocalDate signedDate;
    private final String signedFilePath;
    private final ContractValidityStatus validityStatus;
    private final String validityStatusLabel;
    private final LocalDate terminatedDate;
}

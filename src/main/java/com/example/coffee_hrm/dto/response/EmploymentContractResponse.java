package com.example.coffee_hrm.dto.response;

import com.example.coffee_hrm.common.enums.ContractPayPeriod;
import com.example.coffee_hrm.common.enums.ContractPaymentMethod;
import com.example.coffee_hrm.common.enums.ContractSigningStatus;
import com.example.coffee_hrm.common.enums.ContractValidityStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class EmploymentContractResponse {

    private final Integer id;
    private final String contractCode;
    private final Integer employeeId;
    private final String employeeName;
    private final String positionLabel;
    private final LocalDate hireDate;
    private final Integer storeId;
    private final String storeName;
    private final LocalDate signedDate;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final String jobTitle;
    private final String workLocation;
    private final Integer weeklyShiftCount;
    private final String internalNote;
    private final BigDecimal salary;
    private final BigDecimal allowance;
    private final ContractPaymentMethod paymentMethod;
    private final String paymentMethodLabel;
    private final ContractPayPeriod payPeriod;
    private final String payPeriodLabel;
    private final String benefitNote;
    private final String originalFileName;
    private final Long fileSizeBytes;
    private final boolean hasFile;
    private final String createdByName;
    private final LocalDateTime createdAt;
    private final ContractSigningStatus signingStatus;
    private final String signingStatusLabel;
    private final ContractValidityStatus validityStatus;
    private final String validityStatusLabel;
    private final LocalDate terminatedDate;
}

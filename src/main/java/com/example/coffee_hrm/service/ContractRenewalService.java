package com.example.coffee_hrm.service;

import com.example.coffee_hrm.dto.request.ContractFilePayload;
import com.example.coffee_hrm.dto.request.CreateEmploymentContractRequest;
import com.example.coffee_hrm.dto.response.EmploymentContractResponse;
import com.example.coffee_hrm.security.AuthenticatedUser;

import java.time.LocalDate;

public interface ContractRenewalService {

    EmploymentContractResponse renew(AuthenticatedUser actor,
                                     Integer sourceContractId,
                                     CreateEmploymentContractRequest request,
                                     ContractFilePayload file);

    int activateDue(LocalDate today);

    EmploymentContractResponse stopRenewal(AuthenticatedUser actor, Integer contractId, String reason, String note);

    int recordExpiredRenewals(LocalDate today);

    int lockDeclinedAccounts(LocalDate today);
}

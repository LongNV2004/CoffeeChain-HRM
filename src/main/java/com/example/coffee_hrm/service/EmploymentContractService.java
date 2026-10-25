package com.example.coffee_hrm.service;

import com.example.coffee_hrm.dto.request.CreateEmploymentContractRequest;
import com.example.coffee_hrm.dto.response.EmploymentContractResponse;
import com.example.coffee_hrm.security.AuthenticatedUser;

import java.util.List;

public interface EmploymentContractService {

    List<EmploymentContractResponse> list(AuthenticatedUser actor);

    EmploymentContractResponse detail(AuthenticatedUser actor, Integer contractId);

    EmploymentContractResponse create(AuthenticatedUser actor, CreateEmploymentContractRequest request);
}

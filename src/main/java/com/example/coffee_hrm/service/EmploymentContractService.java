package com.example.coffee_hrm.service;

import com.example.coffee_hrm.dto.request.ContractFilePayload;
import com.example.coffee_hrm.dto.request.CreateEmploymentContractRequest;
import com.example.coffee_hrm.dto.response.ContractDownload;
import com.example.coffee_hrm.dto.response.ContractEmployeeOption;
import com.example.coffee_hrm.dto.response.EmploymentContractListView;
import com.example.coffee_hrm.dto.response.EmploymentContractResponse;
import com.example.coffee_hrm.security.AuthenticatedUser;

import java.time.LocalDate;
import java.util.List;

public interface EmploymentContractService {

    List<EmploymentContractResponse> list(AuthenticatedUser actor);

    List<EmploymentContractResponse> listForEmployee(AuthenticatedUser actor, Integer employeeId);

    EmploymentContractListView search(AuthenticatedUser actor, String keyword, Integer storeId, String validity,
                                       LocalDate fromDate, LocalDate toDate, int page);

    EmploymentContractResponse detail(AuthenticatedUser actor, Integer contractId);

    ContractDownload download(AuthenticatedUser actor, Integer contractId);

    List<ContractEmployeeOption> listEmployees(AuthenticatedUser actor);

    EmploymentContractResponse create(AuthenticatedUser actor, CreateEmploymentContractRequest request,
                                       ContractFilePayload file);
}

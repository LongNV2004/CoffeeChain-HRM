package com.example.coffee_hrm.service;

import com.example.coffee_hrm.dto.response.EmployeeResponse;
import com.example.coffee_hrm.security.AuthenticatedUser;
import com.example.coffee_hrm.dto.request.CreateEmployeeRequest;
import com.example.coffee_hrm.dto.request.UpdateEmployeeRequest;
import java.util.List;

public interface EmployeeService {
    EmployeeResponse createEmployee(
            AuthenticatedUser actor,
            CreateEmployeeRequest request
    );
    List<EmployeeResponse> getStoreEmployees(AuthenticatedUser actor, Integer storeId);
    EmployeeResponse updateEmployee(
            AuthenticatedUser actor,
            Integer storeId,
            Integer employeeId,
            UpdateEmployeeRequest request
    );
    EmployeeResponse changeEmployeeRole(AuthenticatedUser actor, Integer storeId, Integer employeeId, String role);
}

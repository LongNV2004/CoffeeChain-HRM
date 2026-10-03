package com.example.coffee_hrm.service;

import com.example.coffee_hrm.dto.response.EmployeeResponse;
import com.example.coffee_hrm.security.AuthenticatedUser;

import java.util.List;

public interface EmployeeService {

    List<EmployeeResponse> getStoreEmployees(AuthenticatedUser actor, Integer storeId);

    EmployeeResponse changeEmployeeRole(AuthenticatedUser actor, Integer storeId, Integer employeeId, String role);
}

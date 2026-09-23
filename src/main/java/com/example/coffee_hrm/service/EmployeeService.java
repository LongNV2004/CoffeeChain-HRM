package com.example.coffee_hrm.service;

import com.example.coffee_hrm.entity.Employee;

import java.util.List;

public interface EmployeeService {

    List<Employee> getEmployeesByStore(Integer storeId);

    List<Employee> getAllEmployees();

}
package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.repository.EmployeeRepository;
import com.example.coffee_hrm.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
@Service
@RequiredArgsConstructor
public class EmployeeServiceImpl implements EmployeeService {
    private final EmployeeRepository employeeRepository;
    @Override
    public List<Employee> getEmployeesByStore(Integer storeId) {
        return employeeRepository.findByStore_IdAndStatus(
                storeId,
                com.example.coffee_hrm.common.enums.EmployeeStatus.ACTIVE
        );
    }
    @Override
    public List<Employee> getAllEmployees() {
        return employeeRepository.findAll();
    }
}
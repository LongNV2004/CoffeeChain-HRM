package com.example.coffee_hrm.repository;

import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Integer> {

    long countByStore_IdAndStatusNot(Integer storeId, EmployeeStatus status);

    List<Employee> findByStore_IdAndStatus(Integer storeId, EmployeeStatus status);

    @Query("SELECT e FROM Employee e LEFT JOIN FETCH e.store WHERE e.id = :id")
    Optional<Employee> findByIdWithStore(@Param("id") Integer id);
}

package com.example.coffee_hrm.repository;

import com.example.coffee_hrm.entity.MonthlyPayroll;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MonthlyPayrollRepository extends JpaRepository<MonthlyPayroll, Integer> {

    @Query("""
            SELECT p
            FROM MonthlyPayroll p
            JOIN FETCH p.store
            LEFT JOIN FETCH p.employeeLines l
            LEFT JOIN FETCH l.employee
            WHERE p.store.id = :storeId
              AND p.payrollYear = :year
              AND p.payrollMonth = :month
            """)
    Optional<MonthlyPayroll> findPeriod(@Param("storeId") Integer storeId,
                                        @Param("year") int year,
                                        @Param("month") int month);
}

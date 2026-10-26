package com.example.coffee_hrm.repository;

import com.example.coffee_hrm.entity.EmploymentContract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EmploymentContractRepository extends JpaRepository<EmploymentContract, Integer> {

    @Query("""
            SELECT COUNT(c)
            FROM EmploymentContract c
            WHERE LOWER(c.contractCode) = LOWER(:contractCode)
            """)
    long countByContractCodeIgnoreCase(@Param("contractCode") String contractCode);

    @Query("""
            SELECT c
            FROM EmploymentContract c
            JOIN FETCH c.employee e
            JOIN FETCH e.store
            ORDER BY c.startDate DESC, c.id DESC
            """)
    List<EmploymentContract> findAllWithEmployee();

    @Query("""
            SELECT c
            FROM EmploymentContract c
            JOIN FETCH c.employee e
            JOIN FETCH e.store s
            WHERE s.id = :storeId
            ORDER BY c.startDate DESC, c.id DESC
            """)
    List<EmploymentContract> findByStoreId(@Param("storeId") Integer storeId);

    @Query("""
            SELECT c
            FROM EmploymentContract c
            JOIN FETCH c.employee e
            JOIN FETCH e.store
            WHERE c.id = :id
            """)
    Optional<EmploymentContract> findDetailById(@Param("id") Integer id);

    @Query("""
            SELECT c
            FROM EmploymentContract c
            JOIN FETCH c.employee e
            JOIN FETCH e.store
            WHERE e.id = :employeeId
            ORDER BY c.startDate DESC, c.id DESC
            """)
    List<EmploymentContract> findByEmployeeId(@Param("employeeId") Integer employeeId);

    @Query("""
            SELECT c.contractCode
            FROM EmploymentContract c
            WHERE c.contractCode LIKE CONCAT(:prefix, '%')
            """)
    List<String> findCodesByPrefix(@Param("prefix") String prefix);
}

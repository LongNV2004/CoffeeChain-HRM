package com.example.coffee_hrm.repository;

import com.example.coffee_hrm.entity.EmploymentContract;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
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
            SELECT c
            FROM EmploymentContract c
            JOIN FETCH c.employee e
            WHERE e.id IN :employeeIds
            """)
    List<EmploymentContract> findByEmployeeIds(@Param("employeeIds") Collection<Integer> employeeIds);

    @Query("""
            SELECT c.contractCode
            FROM EmploymentContract c
            WHERE c.contractCode LIKE CONCAT(:prefix, '%')
            """)
    List<String> findCodesByPrefix(@Param("prefix") String prefix);

    @Query("""
            SELECT c.id
            FROM EmploymentContract c
            WHERE c.endDate IS NOT NULL
              AND c.startDate <= :today
              AND c.endDate >= :today
              AND c.endDate <= :windowEnd
              AND (c.terminatedDate IS NULL OR c.terminatedDate > :today)
            """)
    List<Integer> findRenewalWindowIds(@Param("today") LocalDate today, @Param("windowEnd") LocalDate windowEnd);

    @Query("""
            SELECT c
            FROM EmploymentContract c
            JOIN FETCH c.employee e
            JOIN FETCH e.store s
            LEFT JOIN FETCH s.manager
            WHERE c.id = :id
            """)
    Optional<EmploymentContract> findReminderTarget(@Param("id") Integer id);

    boolean existsByPreviousContract_Id(Integer previousContractId);

    @Query("""
            SELECT c.previousContract.id
            FROM EmploymentContract c
            WHERE c.previousContract IS NOT NULL
            """)
    List<Integer> findRenewedSourceIds();

    @Query("""
            SELECT c.id
            FROM EmploymentContract c
            WHERE c.supersededDate IS NULL
              AND c.endDate < :today
              AND EXISTS (
                  SELECT 1 FROM EmploymentContract renewal
                  WHERE renewal.previousContract = c
              )
            """)
    List<Integer> findRenewedSourcesPastEnd(@Param("today") LocalDate today);

    @Query("""
            SELECT c.id
            FROM EmploymentContract c
            JOIN c.employee e
            WHERE c.renewalDeclinedAt IS NOT NULL
              AND c.endDate < :today
              AND e.status = com.example.coffee_hrm.common.enums.EmployeeStatus.ACTIVE
            """)
    List<Integer> findDeclinedIdsPastEnd(@Param("today") LocalDate today);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM EmploymentContract c WHERE c.id = :id")
    Optional<EmploymentContract> lockById(@Param("id") Integer id);

    @Query("""
            SELECT c.id
            FROM EmploymentContract c
            WHERE c.previousContract IS NOT NULL
              AND c.activatedAt IS NULL
              AND c.startDate <= :today
            ORDER BY c.startDate ASC, c.id ASC
            """)
    List<Integer> findDueActivationIds(@Param("today") LocalDate today);
}

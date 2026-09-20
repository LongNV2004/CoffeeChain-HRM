package com.example.coffee_hrm.repository;

import com.example.coffee_hrm.common.enums.ApprovalStatus;
import com.example.coffee_hrm.entity.ShiftChangeRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ShiftChangeRequestRepository extends JpaRepository<ShiftChangeRequest, Integer> {

    long countByEmployee_Store_IdAndStatus(Integer storeId, ApprovalStatus status);

    List<ShiftChangeRequest> findByEmployee_IdOrderByRequestDateDesc(Integer employeeId, Pageable pageable);

    @Query("""
            SELECT scr FROM ShiftChangeRequest scr
            JOIN FETCH scr.assignment sa
            JOIN FETCH sa.shift s
            JOIN FETCH scr.employee e
            LEFT JOIN FETCH scr.resolvedBy rb
            LEFT JOIN FETCH scr.targetEmployee te
            LEFT JOIN FETCH scr.targetAssignment tsa
            LEFT JOIN FETCH tsa.shift tsas
            WHERE e.store.id = :storeId
            ORDER BY scr.requestDate DESC
            """)
    List<ShiftChangeRequest> findAllForStore(@Param("storeId") Integer storeId);

    @Query("""
            SELECT scr FROM ShiftChangeRequest scr
            JOIN FETCH scr.assignment sa
            JOIN FETCH sa.shift s
            JOIN FETCH scr.employee e
            LEFT JOIN FETCH scr.resolvedBy rb
            LEFT JOIN FETCH scr.targetEmployee te
            LEFT JOIN FETCH scr.targetAssignment tsa
            LEFT JOIN FETCH tsa.shift tsas
            WHERE e.store.id = :storeId 
              AND scr.status = :status
              AND (scr.targetEmployee IS NULL OR scr.isTargetAgreed = TRUE)
            ORDER BY scr.requestDate DESC
            """)
    List<ShiftChangeRequest> findByStoreAndStatus(@Param("storeId") Integer storeId, @Param("status") ApprovalStatus status);

    @Query("""
            SELECT COUNT(scr) FROM ShiftChangeRequest scr
            JOIN scr.employee e
            WHERE e.store.id = :storeId
              AND scr.status = com.example.coffee_hrm.common.enums.ApprovalStatus.PENDING
              AND (scr.targetEmployee IS NULL OR scr.isTargetAgreed = TRUE)
            """)
    long countPendingForManager(@Param("storeId") Integer storeId);

    @Query("""
            SELECT scr FROM ShiftChangeRequest scr
            JOIN FETCH scr.assignment sa
            JOIN FETCH sa.shift s
            JOIN FETCH scr.employee e
            LEFT JOIN FETCH scr.resolvedBy rb
            LEFT JOIN FETCH scr.targetEmployee te
            LEFT JOIN FETCH scr.targetAssignment tsa
            LEFT JOIN FETCH tsa.shift tsas
            WHERE scr.targetEmployee.id = :employeeId
              AND scr.status = com.example.coffee_hrm.common.enums.ApprovalStatus.PENDING
              AND scr.isTargetAgreed IS NULL
            ORDER BY scr.requestDate DESC
            """)
    List<ShiftChangeRequest> findIncomingRequestsForEmployee(@Param("employeeId") Integer employeeId);

    @Query("""
            SELECT scr FROM ShiftChangeRequest scr
            JOIN FETCH scr.assignment sa
            JOIN FETCH sa.shift s
            JOIN FETCH scr.employee e
            LEFT JOIN FETCH scr.resolvedBy rb
            LEFT JOIN FETCH scr.targetEmployee te
            LEFT JOIN FETCH scr.targetAssignment tsa
            LEFT JOIN FETCH tsa.shift tsas
            WHERE scr.employee.id = :employeeId
            ORDER BY scr.requestDate DESC
            """)
    List<ShiftChangeRequest> findAllByEmployee(@Param("employeeId") Integer employeeId);

    @Query("""
            SELECT scr FROM ShiftChangeRequest scr
            JOIN FETCH scr.assignment sa
            JOIN FETCH sa.shift s
            JOIN FETCH scr.employee e
            LEFT JOIN FETCH scr.resolvedBy rb
            LEFT JOIN FETCH scr.targetEmployee te
            LEFT JOIN FETCH scr.targetAssignment tsa
            LEFT JOIN FETCH tsa.shift tsas
            WHERE scr.id = :id
            """)
    Optional<ShiftChangeRequest> findByIdWithDetails(@Param("id") Integer id);

    boolean existsByAssignment_IdAndStatus(Integer assignmentId, ApprovalStatus status);
}

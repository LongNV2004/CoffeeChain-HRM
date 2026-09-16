package com.example.coffee_hrm.repository;

import com.example.coffee_hrm.common.enums.AssignmentStatus;
import com.example.coffee_hrm.entity.ShiftAssignment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ShiftAssignmentRepository extends JpaRepository<ShiftAssignment, Integer> {

    long countByEmployee_Store_IdAndWorkDateAndStatus(Integer storeId, LocalDate workDate, AssignmentStatus status);

    @Query("""
            SELECT sa FROM ShiftAssignment sa
            JOIN FETCH sa.shift s
            WHERE sa.employee.id = :employeeId
              AND sa.workDate >= :fromDate
              AND sa.status = :status
            ORDER BY sa.workDate ASC
            """)
    List<ShiftAssignment> findUpcomingByEmployee(
            @Param("employeeId") Integer employeeId,
            @Param("fromDate") LocalDate fromDate,
            @Param("status") AssignmentStatus status,
            Pageable pageable);
}

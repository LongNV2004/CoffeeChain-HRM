package com.example.coffee_hrm.repository;

import com.example.coffee_hrm.common.enums.AssignmentStatus;
import com.example.coffee_hrm.entity.ShiftAssignment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

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

    @Query("""
            SELECT sa FROM ShiftAssignment sa
            JOIN FETCH sa.shift s
            JOIN FETCH sa.employee e
            WHERE s.store.id = :storeId
              AND sa.workDate >= :startDate
              AND sa.workDate <= :endDate
              AND sa.status = :status
            ORDER BY sa.workDate ASC, s.startTime ASC
            """)
    List<ShiftAssignment> findWeeklyAssignmentsForStore(
            @Param("storeId") Integer storeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("status") AssignmentStatus status);

    @Query("""
            SELECT sa FROM ShiftAssignment sa
            JOIN FETCH sa.shift s
            JOIN FETCH sa.employee e
            WHERE sa.employee.id = :employeeId
              AND sa.workDate >= :startDate
              AND sa.workDate <= :endDate
              AND sa.status = :status
            ORDER BY sa.workDate ASC, s.startTime ASC
            """)
    List<ShiftAssignment> findWeeklyAssignmentsForEmployee(
            @Param("employeeId") Integer employeeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("status") AssignmentStatus status);

    @Query("""
            SELECT sa FROM ShiftAssignment sa
            JOIN FETCH sa.shift s
            JOIN FETCH s.store st
            JOIN FETCH sa.employee e
            WHERE sa.id = :id
            """)
    Optional<ShiftAssignment> findByIdWithDetails(@Param("id") Integer id);

    boolean existsByEmployee_IdAndShift_IdAndWorkDateAndStatus(
            Integer employeeId, Integer shiftId, LocalDate workDate, AssignmentStatus status);

    List<ShiftAssignment> findByEmployee_IdAndWorkDateAndStatus(
            Integer employeeId, LocalDate workDate, AssignmentStatus status);

    @Query("""
            SELECT sa FROM ShiftAssignment sa
            JOIN sa.shift s
            WHERE s.store.id = :storeId
              AND sa.workDate >= :startDate
              AND sa.workDate <= :endDate
              AND sa.status = com.example.coffee_hrm.common.enums.AssignmentStatus.ASSIGNED
            """)
    List<ShiftAssignment> findAssignmentsToPublish(
            @Param("storeId") Integer storeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}

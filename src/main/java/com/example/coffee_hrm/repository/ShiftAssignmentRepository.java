package com.example.coffee_hrm.repository;

import com.example.coffee_hrm.common.enums.AssignmentStatus;
import com.example.coffee_hrm.entity.ShiftAssignment;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShiftAssignmentRepository extends JpaRepository<ShiftAssignment, Integer> {

    long countByEmployee_Store_IdAndWorkDateAndStatus(
            Integer storeId, LocalDate workDate, AssignmentStatus status);

    @Query(
            """
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

    @Query(
            """
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

    @Query(
            """
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

    @Query(
            """
            SELECT sa FROM ShiftAssignment sa
            JOIN FETCH sa.shift s
            JOIN FETCH s.store st
            JOIN FETCH sa.employee e
            WHERE sa.id = :id
            """)
    Optional<ShiftAssignment> findByIdWithDetails(@Param("id") Integer id);

    boolean existsByEmployee_IdAndShift_IdAndWorkDate(
            Integer employeeId, Integer shiftId, LocalDate workDate);

    boolean existsByReplacesAssignment_Id(Integer originalAssignmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT sa FROM ShiftAssignment sa WHERE sa.id IN :ids")
    List<ShiftAssignment> lockByIds(@Param("ids") Collection<Integer> ids);

    @Query("""
            SELECT sa FROM ShiftAssignment sa
            JOIN FETCH sa.shift s
            JOIN FETCH s.store
            JOIN FETCH sa.employee e
            JOIN FETCH e.store
            WHERE sa.id IN :ids
            """)
    List<ShiftAssignment> findForLeaveSelection(@Param("ids") Collection<Integer> ids);

    @Query("""
            SELECT sa FROM ShiftAssignment sa
            JOIN FETCH sa.shift s
            JOIN FETCH s.store
            JOIN FETCH sa.employee e
            JOIN FETCH e.store
            WHERE sa.employee.id = :employeeId
              AND sa.workDate >= :fromDate
              AND sa.workDate <= :toDate
            ORDER BY sa.workDate ASC, s.startTime ASC, sa.id ASC
            """)
    List<ShiftAssignment> findScheduleForLeavePicker(@Param("employeeId") Integer employeeId,
                                                     @Param("fromDate") LocalDate fromDate,
                                                     @Param("toDate") LocalDate toDate);

    Optional<ShiftAssignment> findByReplacesAssignment_Id(Integer originalAssignmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT sa FROM ShiftAssignment sa WHERE sa.id = :id")
    Optional<ShiftAssignment> lockForReplacement(@Param("id") Integer id);

    @Query("""
            SELECT sa FROM ShiftAssignment sa
            JOIN FETCH sa.shift s JOIN FETCH sa.employee e
            JOIN FETCH sa.cancelledByLeaveRequest lr
            WHERE s.store.id = :storeId
              AND (:leaveRequestId IS NULL OR lr.id = :leaveRequestId)
            ORDER BY sa.workDate, s.startTime, sa.id
            """)
    List<ShiftAssignment> findLeaveCancelledForStore(
            @Param("storeId") Integer storeId,
            @Param("leaveRequestId") Integer leaveRequestId);

    @Query("""
            SELECT sa FROM ShiftAssignment sa JOIN FETCH sa.employee e
            WHERE sa.replacesAssignment.id IN :ids
            """)
    List<ShiftAssignment> findReplacementsForOriginalIds(@Param("ids") List<Integer> originalIds);

    boolean existsByEmployee_IdAndShift_IdAndWorkDateAndStatus(
            Integer employeeId, Integer shiftId, LocalDate workDate, AssignmentStatus status);

    List<ShiftAssignment> findByEmployee_IdAndWorkDateAndStatus(
            Integer employeeId, LocalDate workDate, AssignmentStatus status);

    @Query(
            """
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

    @Query(
            """
            SELECT sa FROM ShiftAssignment sa
            JOIN FETCH sa.shift s
            JOIN FETCH s.store
            JOIN FETCH sa.employee e
            WHERE e.id = :employeeId
              AND sa.workDate >= :fromDate
              AND sa.workDate <= :toDate
              AND sa.status = :status
            ORDER BY sa.workDate ASC, s.startTime ASC
            """)
    List<ShiftAssignment> findAssignmentsForAttendance(
            @Param("employeeId") Integer employeeId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("status") AssignmentStatus status);

    @Query(
            """
            SELECT sa FROM ShiftAssignment sa
            JOIN FETCH sa.shift s
            JOIN FETCH s.store
            JOIN FETCH sa.employee e
            WHERE sa.workDate = :workDate
              AND sa.status = :status
              AND sa.isPublished = true
            """)
    List<ShiftAssignment> findPublishedAssignedOnDate(
            @Param("workDate") LocalDate workDate, @Param("status") AssignmentStatus status);

    @Query(
            """
            SELECT DISTINCT sa.workDate FROM ShiftAssignment sa
            JOIN sa.shift s
            WHERE sa.employee.id = :employeeId
              AND s.store.id = :storeId
              AND sa.status = :status
              AND sa.workDate BETWEEN :startDate AND :endDate
            ORDER BY sa.workDate
            """)
    List<LocalDate> findAssignedWorkDatesForLeave(
            @Param("employeeId") Integer employeeId,
            @Param("storeId") Integer storeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("status") AssignmentStatus status);

    @Query(
            """
            SELECT DISTINCT sa.workDate FROM ShiftAssignment sa
            JOIN sa.shift s
            WHERE sa.employee.id = :employeeId
              AND s.store.id = :storeId
              AND sa.status = :assignmentStatus
              AND sa.workDate BETWEEN :startDate AND :endDate
              AND EXISTS (
                SELECT lr.id FROM LeaveRequest lr
                WHERE lr.employee.id = :employeeId
                  AND lr.status = :pendingStatus
                  AND (lr.leaveType IS NULL OR lr.leaveType = :annualType)
                  AND sa.workDate BETWEEN lr.startDate AND lr.endDate
              )
            """)
    List<LocalDate> findPendingLeaveWorkDates(
            @Param("employeeId") Integer employeeId,
            @Param("storeId") Integer storeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("assignmentStatus") AssignmentStatus assignmentStatus,
            @Param("pendingStatus")
            com.example.coffee_hrm.common.enums.ApprovalStatus pendingStatus,
            @Param("annualType") com.example.coffee_hrm.common.enums.LeaveType annualType);

    @Query(
            """
            SELECT sa FROM ShiftAssignment sa
            JOIN FETCH sa.shift s
            WHERE sa.employee.id = :employeeId
              AND s.store.id = :storeId
              AND sa.status = :status
              AND sa.workDate BETWEEN :startDate AND :endDate
            ORDER BY sa.workDate ASC, s.startTime ASC, sa.id ASC
            """)
    List<ShiftAssignment> findActiveAssignmentsForLeave(
            @Param("employeeId") Integer employeeId,
            @Param("storeId") Integer storeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("status") AssignmentStatus status);

    @Query(
            """
            SELECT DISTINCT sa.workDate FROM ShiftAssignment sa
            JOIN sa.shift s
            WHERE sa.employee.id = :employeeId
              AND s.store.id = :storeId
              AND sa.status = :assignmentStatus
              AND sa.workDate BETWEEN :startDate AND :endDate
              AND EXISTS (
                SELECT lr.id FROM LeaveRequest lr
                WHERE lr.employee.id = :employeeId
                  AND lr.id <> :excludingRequestId
                  AND lr.status = :pendingStatus
                  AND (lr.leaveType IS NULL OR lr.leaveType = :annualType)
                  AND sa.workDate BETWEEN lr.startDate AND lr.endDate
              )
            """)
    List<LocalDate> findPendingLeaveWorkDatesExcluding(
            @Param("employeeId") Integer employeeId,
            @Param("storeId") Integer storeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("assignmentStatus") AssignmentStatus assignmentStatus,
            @Param("pendingStatus") com.example.coffee_hrm.common.enums.ApprovalStatus pendingStatus,
            @Param("excludingRequestId") Integer excludingRequestId,
            @Param("annualType") com.example.coffee_hrm.common.enums.LeaveType annualType);

    @Query("""
            SELECT sa FROM ShiftAssignment sa
            JOIN FETCH sa.shift s
            JOIN FETCH sa.employee e
            WHERE s.store.id = :storeId
              AND sa.workDate = :workDate
              AND sa.status = :status
              AND sa.isPublished = true
            ORDER BY s.startTime ASC, e.fullName ASC, sa.id ASC
            """)
    List<ShiftAssignment> findPublishedAssignedForStoreOnDate(
            @Param("storeId") Integer storeId,
            @Param("workDate") LocalDate workDate,
            @Param("status") AssignmentStatus status);

    @Query("""
            SELECT sa FROM ShiftAssignment sa
            JOIN FETCH sa.shift s
            JOIN FETCH sa.employee e
            WHERE s.store.id = :storeId
              AND sa.workDate >= :startDate
              AND sa.workDate <= :endDate
              AND sa.status = :status
              AND sa.isPublished = true
            """)
    List<ShiftAssignment> findPublishedAssignedForStoreBetween(
            @Param("storeId") Integer storeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("status") AssignmentStatus status);
}
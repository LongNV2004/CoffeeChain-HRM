package com.example.coffee_hrm.repository;

import com.example.coffee_hrm.common.enums.ApprovalStatus;
import com.example.coffee_hrm.entity.OvertimeRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OvertimeRequestRepository extends JpaRepository<OvertimeRequest, Integer> {

    /** Id các ca phân công được tạo từ đơn tăng ca có trạng thái cho trước (vd. đã duyệt). */
    @Query("""
            SELECT o.createdAssignmentId FROM OvertimeRequest o
            WHERE o.createdAssignmentId IN :assignmentIds AND o.status = :status
            """)
    List<Integer> findAssignmentIdsByStatus(@Param("assignmentIds") Collection<Integer> assignmentIds,
                                            @Param("status") ApprovalStatus status);

    @Query("""
            SELECT r FROM OvertimeRequest r
            JOIN FETCH r.employee e
            JOIN FETCH r.store st
            JOIN FETCH r.shift s
            LEFT JOIN FETCH r.reviewedBy rb
            WHERE r.id = :id
            """)
    Optional<OvertimeRequest> findByIdWithDetails(@Param("id") Integer id);

    @Query("""
            SELECT r FROM OvertimeRequest r
            JOIN FETCH r.employee e
            JOIN FETCH r.store st
            JOIN FETCH r.shift s
            LEFT JOIN FETCH r.reviewedBy rb
            WHERE e.id = :employeeId
            ORDER BY r.workDate DESC, s.startTime ASC, r.createdAt DESC
            """)
    List<OvertimeRequest> findAllByEmployeeId(@Param("employeeId") Integer employeeId);

    @Query("""
            SELECT r FROM OvertimeRequest r
            JOIN FETCH r.employee e
            JOIN FETCH r.store st
            JOIN FETCH r.shift s
            LEFT JOIN FETCH r.reviewedBy rb
            WHERE st.id = :storeId
            ORDER BY r.workDate DESC, s.startTime ASC, r.createdAt DESC
            """)
    List<OvertimeRequest> findAllByStoreId(@Param("storeId") Integer storeId);

    @Query("""
            SELECT r FROM OvertimeRequest r
            JOIN FETCH r.employee e
            JOIN FETCH r.shift s
            WHERE e.id = :employeeId
              AND r.workDate BETWEEN :fromDate AND :toDate
              AND r.status IN :statuses
            ORDER BY r.workDate ASC, s.startTime ASC
            """)
    List<OvertimeRequest> findByEmployeeAndDateRangeAndStatuses(
            @Param("employeeId") Integer employeeId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("statuses") Collection<ApprovalStatus> statuses);

    @Query("""
            SELECT r FROM OvertimeRequest r
            JOIN FETCH r.employee e
            JOIN FETCH r.shift s
            WHERE r.store.id = :storeId
              AND r.workDate BETWEEN :fromDate AND :toDate
              AND r.status IN :statuses
            ORDER BY r.workDate ASC, s.startTime ASC
            """)
    List<OvertimeRequest> findByStoreAndDateRangeAndStatuses(
            @Param("storeId") Integer storeId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("statuses") Collection<ApprovalStatus> statuses);

    boolean existsByEmployee_IdAndShift_IdAndWorkDateAndStatusIn(
            Integer employeeId,
            Integer shiftId,
            LocalDate workDate,
            Collection<ApprovalStatus> statuses);
}


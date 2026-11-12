package com.example.coffee_hrm.repository;

import com.example.coffee_hrm.common.enums.ApprovalStatus;
import com.example.coffee_hrm.entity.OvertimeRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OvertimeRequestRepository extends JpaRepository<OvertimeRequest, Integer> {

    /** Id các ca phân công được tạo từ đơn tăng ca có trạng thái cho trước (vd. đã duyệt). */
    @Query("""
            SELECT o.assignment.id FROM OvertimeRequest o
            WHERE o.assignment.id IN :assignmentIds AND o.status = :status
            """)
    List<Integer> findAssignmentIdsByStatus(@Param("assignmentIds") Collection<Integer> assignmentIds,
                                            @Param("status") ApprovalStatus status);

    @Query("""
            SELECT o FROM OvertimeRequest o
            JOIN FETCH o.employee e
            JOIN FETCH o.shift s
            LEFT JOIN FETCH o.resolvedBy rb
            LEFT JOIN FETCH rb.employee
            WHERE o.employee.id = :employeeId
            ORDER BY o.workDate DESC, s.startTime DESC, o.id DESC
            """)
    List<OvertimeRequest> findByEmployee(@Param("employeeId") Integer employeeId);

    @Query("""
            SELECT o FROM OvertimeRequest o
            JOIN FETCH o.employee e
            JOIN FETCH o.shift s
            LEFT JOIN FETCH o.resolvedBy rb
            LEFT JOIN FETCH rb.employee
            WHERE o.store.id = :storeId
            ORDER BY o.createdAt DESC, o.id DESC
            """)
    List<OvertimeRequest> findByStore(@Param("storeId") Integer storeId);

    @Query("""
            SELECT o FROM OvertimeRequest o
            JOIN FETCH o.shift s
            WHERE o.employee.id = :employeeId
              AND o.workDate = :workDate
              AND o.status IN :statuses
            """)
    List<OvertimeRequest> findActiveOnDate(@Param("employeeId") Integer employeeId,
                                           @Param("workDate") LocalDate workDate,
                                           @Param("statuses") Collection<ApprovalStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OvertimeRequest o WHERE o.id = :id")
    Optional<OvertimeRequest> lockById(@Param("id") Integer id);
}

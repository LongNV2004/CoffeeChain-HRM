package com.example.coffee_hrm.repository;
import com.example.coffee_hrm.common.enums.ApprovalStatus;
import com.example.coffee_hrm.common.enums.AssignmentStatus;
import com.example.coffee_hrm.entity.ShiftAssignment;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaveReplacementAssignmentRepository extends JpaRepository<ShiftAssignment, Integer> {
    @Query("""
            SELECT sa FROM ShiftAssignment sa
            JOIN FETCH sa.shift s JOIN FETCH sa.employee e
            WHERE e.id = :employeeId AND s.store.id = :storeId
              AND sa.status = :cancelled AND sa.workDate BETWEEN :startDate AND :endDate
            ORDER BY sa.workDate ASC, s.startTime ASC, sa.id ASC
            """)
    List<ShiftAssignment> findCancelledForLeave(@Param("employeeId") Integer employeeId,
                                                @Param("storeId") Integer storeId,
                                                @Param("startDate") LocalDate startDate,
                                                @Param("endDate") LocalDate endDate,
                                                @Param("cancelled") AssignmentStatus cancelled);

    @Query("""
            SELECT sa FROM ShiftAssignment sa
            JOIN FETCH sa.shift s JOIN FETCH sa.employee e
            WHERE s.store.id = :storeId AND sa.status = :cancelled
              AND sa.workDate >= :startDate
              AND EXISTS (SELECT lr.id FROM LeaveRequest lr
                          WHERE lr.employee.id = e.id AND lr.status = :approved
                            AND sa.workDate BETWEEN lr.startDate AND lr.endDate)
            ORDER BY sa.workDate ASC, s.startTime ASC, sa.id ASC
            """)
    List<ShiftAssignment> findStoreLeaveCancelledAssignments(
            @Param("storeId") Integer storeId,
            @Param("startDate") LocalDate startDate,
            @Param("cancelled") AssignmentStatus cancelled,
            @Param("approved") ApprovalStatus approved);
}

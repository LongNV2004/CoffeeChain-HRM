package com.example.coffee_hrm.repository;

import com.example.coffee_hrm.common.enums.ApprovalStatus;
import com.example.coffee_hrm.common.enums.LeaveType;
import com.example.coffee_hrm.entity.LeaveRequestSlot;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaveRequestSlotRepository extends JpaRepository<LeaveRequestSlot, Integer> {

    @Query("""
            SELECT s FROM LeaveRequestSlot s
            JOIN FETCH s.assignment a
            JOIN FETCH a.shift sh
            LEFT JOIN FETCH sh.store
            WHERE s.leaveRequest.id IN :requestIds
            ORDER BY a.workDate ASC, sh.startTime ASC, a.id ASC
            """)
    List<LeaveRequestSlot> findDetailedByRequestIds(@Param("requestIds") Collection<Integer> requestIds);

    @Query("""
            SELECT s.assignment.id FROM LeaveRequestSlot s
            WHERE s.assignment.id IN :assignmentIds
              AND s.leaveRequest.status IN :statuses
            """)
    List<Integer> findBlockedAssignmentIds(@Param("assignmentIds") Collection<Integer> assignmentIds,
                                           @Param("statuses") Collection<ApprovalStatus> statuses);

    @Query("""
            SELECT s.assignment.id AS assignmentId, s.leaveRequest.status AS status
            FROM LeaveRequestSlot s
            WHERE s.assignment.id IN :assignmentIds
              AND s.leaveRequest.status IN :statuses
            """)
    List<BlockingSlot> findBlockingSlots(@Param("assignmentIds") Collection<Integer> assignmentIds,
                                         @Param("statuses") Collection<ApprovalStatus> statuses);

    interface BlockingSlot {
        Integer getAssignmentId();
        ApprovalStatus getStatus();
    }

    @Query("""
            SELECT COUNT(s) FROM LeaveRequestSlot s
            WHERE s.leaveRequest.employee.id = :employeeId
              AND s.leaveRequest.status = :status
              AND (s.leaveRequest.leaveType IS NULL OR s.leaveRequest.leaveType = :annualType)
              AND s.assignment.workDate >= :fromDate
              AND s.assignment.workDate <= :toDate
              AND (:excludeRequestId IS NULL OR s.leaveRequest.id <> :excludeRequestId)
            """)
    long countReservedSlots(@Param("employeeId") Integer employeeId,
                            @Param("status") ApprovalStatus status,
                            @Param("annualType") LeaveType annualType,
                            @Param("fromDate") LocalDate fromDate,
                            @Param("toDate") LocalDate toDate,
                            @Param("excludeRequestId") Integer excludeRequestId);

    @Query("""
            SELECT COUNT(s) > 0 FROM LeaveRequestSlot s
            WHERE s.assignment.employee.id = :employeeId
              AND s.assignment.shift.id = :shiftId
              AND s.assignment.workDate = :workDate
              AND s.leaveRequest.status = :status
            """)
    boolean existsForShift(@Param("employeeId") Integer employeeId,
                           @Param("shiftId") Integer shiftId,
                           @Param("workDate") LocalDate workDate,
                           @Param("status") ApprovalStatus status);

    @Query("""
            SELECT s FROM LeaveRequestSlot s
            JOIN FETCH s.assignment a
            JOIN FETCH a.shift sh
            JOIN FETCH s.leaveRequest lr
            WHERE sh.store.id = :storeId
              AND lr.status = :status
              AND a.workDate >= :fromDate
              AND a.workDate <= :toDate
            """)
    List<LeaveRequestSlot> findApprovedInStore(@Param("storeId") Integer storeId,
                                               @Param("status") ApprovalStatus status,
                                               @Param("fromDate") LocalDate fromDate,
                                               @Param("toDate") LocalDate toDate);

    @Query("""
            SELECT s FROM LeaveRequestSlot s
            JOIN FETCH s.assignment a
            JOIN FETCH a.shift sh
            WHERE s.leaveRequest.id IN :requestIds
            """)
    List<LeaveRequestSlot> findByLeaveRequest_IdIn(@Param("requestIds") Collection<Integer> requestIds);
}

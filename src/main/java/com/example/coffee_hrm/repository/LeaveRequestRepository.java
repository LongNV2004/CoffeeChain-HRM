package com.example.coffee_hrm.repository;

import com.example.coffee_hrm.common.enums.ApprovalStatus;
import com.example.coffee_hrm.entity.LeaveRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Integer> {
    // Original queries (also used by Stage 1 and Dashboard).
    long countByEmployee_Store_IdAndStatus(Integer storeId, ApprovalStatus status);
    List<LeaveRequest> findByEmployee_IdOrderByCreatedAtDesc(Integer employeeId, Pageable pageable);

    // Stage 1: create, prevent overlapping requests, show personal history.
    boolean existsByEmployee_IdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Integer employeeId, Collection<ApprovalStatus> statuses,
            LocalDate requestedEndDate, LocalDate requestedStartDate);

    List<LeaveRequest> findByEmployee_IdOrderByCreatedAtDescIdDesc(
            Integer employeeId, Pageable pageable);
    long countByEmployee_Id(Integer employeeId);

    // Stage 2: Manager sees only requests from their own store.
    Page<LeaveRequest> findByEmployee_Store_IdOrderByCreatedAtDescIdDesc(
            Integer storeId, Pageable pageable);
    Page<LeaveRequest> findByEmployee_Store_IdAndStatusOrderByCreatedAtDescIdDesc(
            Integer storeId, ApprovalStatus status, Pageable pageable);

    // Scalar pre-check avoids caching a stale request before acquiring the employee lock.
    @Query("""
            SELECT lr.employee.id FROM LeaveRequest lr
            WHERE lr.id = :id AND lr.employee.store.id = :storeId
            """)
    Optional<Integer> findEmployeeIdForStore(@Param("id") Integer id,
                                             @Param("storeId") Integer storeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.id = :id")
    Optional<LeaveRequest> lockById(@Param("id") Integer id);

    @Query("""
            SELECT lr FROM LeaveRequest lr
            JOIN lr.employee e
            WHERE e.store.id = :storeId AND lr.status = :status
              AND lr.startDate <= :endDate AND lr.endDate >= :startDate
            """)
    List<LeaveRequest> findByStoreAndStatusOverlapping(
            @Param("storeId") Integer storeId,
            @Param("status") ApprovalStatus status,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    boolean existsByEmployee_IdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Integer employeeId, ApprovalStatus status, LocalDate workDateEnd, LocalDate workDateStart);

    @Query("""
            SELECT COUNT(lr) > 0 FROM LeaveRequest lr
            WHERE lr.employee.id = :employeeId
              AND lr.status = :status
              AND lr.startDate <= :workDate
              AND lr.endDate >= :workDate
              AND NOT EXISTS (
                  SELECT 1 FROM LeaveRequestSlot s WHERE s.leaveRequest = lr
              )
            """)
    boolean existsApprovedWithoutSlots(@Param("employeeId") Integer employeeId,
                                       @Param("status") ApprovalStatus status,
                                       @Param("workDate") LocalDate workDate);

    @EntityGraph(attributePaths = {"employee", "approvedBy", "approvedBy.employee"})
    @Query("""
            SELECT lr FROM LeaveRequest lr
            WHERE lr.employee.id = :employeeId
              AND (:status IS NULL OR lr.status = :status)
              AND (:fromDate IS NULL OR lr.endDate >= :fromDate)
              AND (:toDate IS NULL OR lr.startDate <= :toDate)
            """)
    Page<LeaveRequest> searchByEmployee(@Param("employeeId") Integer employeeId,
                                        @Param("status") ApprovalStatus status,
                                        @Param("fromDate") LocalDate fromDate,
                                        @Param("toDate") LocalDate toDate,
                                        Pageable pageable);

    long countByStatus(ApprovalStatus status);

    @EntityGraph(attributePaths = {"employee", "employee.store"})
    @Query("""
            SELECT lr FROM LeaveRequest lr
            WHERE (:status IS NULL OR lr.status = :status)
              AND (:fromDate IS NULL OR lr.endDate >= :fromDate)
              AND (:toDate IS NULL OR lr.startDate <= :toDate)
            """)
    Page<LeaveRequest> searchForAdmin(@Param("status") ApprovalStatus status,
                                      @Param("fromDate") LocalDate fromDate,
                                      @Param("toDate") LocalDate toDate,
                                      Pageable pageable);
}

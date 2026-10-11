package com.example.coffee_hrm.repository;
import com.example.coffee_hrm.common.enums.ApprovalStatus;
import com.example.coffee_hrm.entity.LeaveRequest;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaveWorkspaceRepository extends JpaRepository<LeaveRequest, Integer> {
    @Query("""
            SELECT lr FROM LeaveRequest lr JOIN lr.employee e
            WHERE e.store.id = :storeId
              AND (:status IS NULL OR lr.status = :status)
              AND (:keyword = '' OR LOWER(e.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<LeaveRequest> findInStore(@Param("storeId") Integer storeId,
                                   @Param("status") ApprovalStatus status,
                                   @Param("keyword") String keyword, Pageable pageable);

    long countByEmployee_Store_IdAndStatus(Integer storeId, ApprovalStatus status);

    @Query("""
            SELECT lr FROM LeaveRequest lr
            WHERE lr.employee.store.id = :storeId AND lr.status = :approved
              AND lr.endDate >= :fromDate
            """)
    List<LeaveRequest> findApprovedSince(@Param("storeId") Integer storeId,
                                        @Param("approved") ApprovalStatus approved,
                                        @Param("fromDate") LocalDate fromDate);

    @Query("""
            SELECT lr FROM LeaveRequest lr
            WHERE lr.employee.store.id = :storeId AND lr.status = :approved
              AND lr.startDate <= :date AND lr.endDate >= :date
            """)
    List<LeaveRequest> findApprovedOnDate(@Param("storeId") Integer storeId,
                                         @Param("approved") ApprovalStatus approved,
                                         @Param("date") LocalDate date);
}

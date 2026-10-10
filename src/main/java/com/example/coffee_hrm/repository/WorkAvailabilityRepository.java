package com.example.coffee_hrm.repository;

import com.example.coffee_hrm.entity.WorkAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.coffee_hrm.common.enums.ApprovalStatus;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WorkAvailabilityRepository extends JpaRepository<WorkAvailability, Integer> {

    boolean existsByEmployee_IdAndShift_IdAndWorkDate(Integer employeeId, Integer shiftId, LocalDate workDate);

    @Query("""
            SELECT wa FROM WorkAvailability wa
            JOIN FETCH wa.shift s
            JOIN FETCH wa.employee e
            JOIN FETCH e.store
            WHERE wa.id = :id
            """)
    Optional<WorkAvailability> findByIdWithDetails(@Param("id") Integer id);

    @Query("""
            SELECT wa FROM WorkAvailability wa
            JOIN FETCH wa.shift s
            WHERE wa.employee.id = :employeeId
              AND wa.workDate BETWEEN :fromDate AND :toDate
            ORDER BY wa.workDate ASC, s.startTime ASC
            """)
    List<WorkAvailability> findByEmployeeAndDateRange(
            @Param("employeeId") Integer employeeId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);

    @Query("""
            SELECT wa FROM WorkAvailability wa
            JOIN FETCH wa.shift s
            JOIN FETCH wa.employee e
            WHERE e.store.id = :storeId
              AND wa.workDate BETWEEN :fromDate AND :toDate
            ORDER BY e.fullName ASC, wa.workDate ASC, s.startTime ASC
            """)
    List<WorkAvailability> findByStoreAndDateRange(
            @Param("storeId") Integer storeId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM WorkAvailability wa
            WHERE wa.employee.id = :employeeId
              AND wa.workDate BETWEEN :fromDate AND :toDate
            """)
    int deleteByEmployeeAndDateRange(
            @Param("employeeId") Integer employeeId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);

    @Query("""
            SELECT wa FROM WorkAvailability wa
            JOIN FETCH wa.shift s
            JOIN FETCH wa.employee e
            JOIN FETCH e.store
            WHERE wa.registrationKey = :registrationKey
            ORDER BY wa.dayOfWeek ASC, s.startTime ASC
            """)
    List<WorkAvailability> findByRegistrationKey(@Param("registrationKey") String registrationKey);

    @Query("""
            SELECT wa FROM WorkAvailability wa
            JOIN FETCH wa.shift s
            WHERE wa.employee.id = :employeeId
              AND (
                    wa.status = :pending
                    OR (wa.validTo IS NOT NULL AND wa.validTo >= :today)
                    OR (wa.dayOfWeek IS NULL AND wa.workDate IS NOT NULL AND wa.workDate >= :today)
                  )
            ORDER BY wa.validFrom ASC, wa.dayOfWeek ASC, s.startTime ASC
            """)
    List<WorkAvailability> findVisibleByEmployee(
            @Param("employeeId") Integer employeeId,
            @Param("today") LocalDate today,
            @Param("pending") ApprovalStatus pending);

    @Query("""
            SELECT wa FROM WorkAvailability wa
            JOIN FETCH wa.shift s
            JOIN FETCH wa.employee e
            WHERE e.store.id = :storeId
              AND (
                    wa.status = :pending
                    OR (wa.validTo IS NOT NULL AND wa.validTo >= :today)
                    OR (wa.dayOfWeek IS NULL AND wa.workDate IS NOT NULL AND wa.workDate >= :today)
                  )
            ORDER BY e.fullName ASC, wa.dayOfWeek ASC, s.startTime ASC
            """)
    List<WorkAvailability> findVisibleByStore(
            @Param("storeId") Integer storeId,
            @Param("today") LocalDate today,
            @Param("pending") ApprovalStatus pending);

    @Query("""
            SELECT wa FROM WorkAvailability wa
            JOIN FETCH wa.shift s
            JOIN FETCH wa.employee e
            WHERE e.store.id = :storeId
              AND wa.status = :approved
              AND (
                    (wa.dayOfWeek IS NOT NULL AND wa.validFrom <= :weekEnd AND wa.validTo >= :weekStart)
                    OR (wa.dayOfWeek IS NULL AND wa.workDate BETWEEN :weekStart AND :weekEnd)
                  )
            ORDER BY e.fullName ASC, s.startTime ASC
            """)
    List<WorkAvailability> findApprovedCovering(
            @Param("storeId") Integer storeId,
            @Param("weekStart") LocalDate weekStart,
            @Param("weekEnd") LocalDate weekEnd,
            @Param("approved") ApprovalStatus approved);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM WorkAvailability wa
            WHERE wa.id IN :ids
            """)
    int deleteByIds(@Param("ids") Collection<Integer> ids);

    /**
     * Đăng ký còn chiếm chỗ của một ca: chờ duyệt, hoặc đã duyệt và còn hiệu lực.
     * Bản từ chối không nằm trong {@code statuses}.
     */
    @Query("""
            SELECT wa FROM WorkAvailability wa
            JOIN FETCH wa.employee e
            JOIN FETCH wa.shift s
            WHERE s.id = :shiftId
              AND wa.status IN :statuses
              AND (
                    wa.status = :pending
                    OR (wa.dayOfWeek IS NOT NULL AND (wa.validTo IS NULL OR wa.validTo >= :today))
                    OR (wa.dayOfWeek IS NULL AND wa.workDate IS NOT NULL AND wa.workDate >= :today)
                  )
            """)
    List<WorkAvailability> findCapacityCandidatesByShift(
            @Param("shiftId") Integer shiftId,
            @Param("today") LocalDate today,
            @Param("pending") ApprovalStatus pending,
            @Param("statuses") Collection<ApprovalStatus> statuses);

    @Query("""
            SELECT wa FROM WorkAvailability wa
            JOIN FETCH wa.employee e
            JOIN FETCH wa.shift s
            WHERE e.store.id = :storeId
              AND wa.status IN :statuses
              AND (
                    wa.status = :pending
                    OR (wa.dayOfWeek IS NOT NULL AND (wa.validTo IS NULL OR wa.validTo >= :today))
                    OR (wa.dayOfWeek IS NULL AND wa.workDate IS NOT NULL AND wa.workDate >= :today)
                  )
            """)
    List<WorkAvailability> findCapacityCandidatesByStore(
            @Param("storeId") Integer storeId,
            @Param("today") LocalDate today,
            @Param("pending") ApprovalStatus pending,
            @Param("statuses") Collection<ApprovalStatus> statuses);
}

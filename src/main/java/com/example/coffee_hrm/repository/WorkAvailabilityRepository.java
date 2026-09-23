package com.example.coffee_hrm.repository;

import com.example.coffee_hrm.entity.WorkAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface WorkAvailabilityRepository extends JpaRepository<WorkAvailability, Integer> {

    boolean existsByEmployee_IdAndShift_IdAndWorkDate(Integer employeeId, Integer shiftId, LocalDate workDate);

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
}

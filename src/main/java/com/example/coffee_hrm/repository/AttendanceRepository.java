package com.example.coffee_hrm.repository;

import com.example.coffee_hrm.entity.Attendance;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Integer> {

    Optional<Attendance> findByEmployee_IdAndWorkDate(Integer employeeId, LocalDate workDate);

    List<Attendance> findByEmployee_IdAndWorkDateOrderByIdDesc(Integer employeeId, LocalDate workDate);

    Optional<Attendance> findByEmployee_IdAndShift_IdAndWorkDate(Integer employeeId, Integer shiftId, LocalDate workDate);

    @Query("""
            SELECT a FROM Attendance a
            LEFT JOIN FETCH a.store
            LEFT JOIN FETCH a.shift
            WHERE a.employee.id = :employeeId
              AND a.checkInTime IS NOT NULL
              AND a.checkOutTime IS NULL
            ORDER BY a.checkInTime DESC, a.id DESC
            """)
    List<Attendance> findOpenByEmployeeId(@Param("employeeId") Integer employeeId);

    @Query("""
            SELECT a FROM Attendance a
            WHERE a.employee.id = :employeeId
            ORDER BY a.workDate DESC, a.id DESC
            """)
    List<Attendance> findRecentByEmployee(@Param("employeeId") Integer employeeId, Pageable pageable);

    @Query("""
            SELECT a FROM Attendance a
            JOIN FETCH a.employee e
            JOIN FETCH e.store
            LEFT JOIN FETCH a.store
            LEFT JOIN FETCH a.shift
            WHERE a.workDate >= :fromDate
              AND a.workDate <= :toDate
            ORDER BY a.workDate DESC, e.fullName ASC, a.id DESC
            """)
    List<Attendance> findHistoryBetween(@Param("fromDate") LocalDate fromDate,
                                        @Param("toDate") LocalDate toDate);

    @Query("""
            SELECT a FROM Attendance a
            JOIN FETCH a.employee e
            WHERE e.id = :employeeId
              AND a.workDate >= :fromDate
              AND a.workDate <= :toDate
            ORDER BY a.workDate DESC, a.id DESC
            """)
    List<Attendance> findHistoryByEmployee(@Param("employeeId") Integer employeeId,
                                           @Param("fromDate") LocalDate fromDate,
                                           @Param("toDate") LocalDate toDate);

    @Query("""
            SELECT a FROM Attendance a
            JOIN FETCH a.employee e
            WHERE e.store.id = :storeId
              AND a.workDate >= :fromDate
              AND a.workDate <= :toDate
            ORDER BY a.workDate DESC, e.fullName ASC, a.id DESC
            """)
    List<Attendance> findHistoryByStore(@Param("storeId") Integer storeId,
                                        @Param("fromDate") LocalDate fromDate,
                                        @Param("toDate") LocalDate toDate);

    @Query("""
            SELECT a FROM Attendance a
            JOIN FETCH a.employee e
            WHERE e.store.id = :storeId
              AND e.id = :employeeId
              AND a.workDate >= :fromDate
              AND a.workDate <= :toDate
            ORDER BY a.workDate DESC, a.id DESC
            """)
    List<Attendance> findHistoryByStoreAndEmployee(@Param("storeId") Integer storeId,
                                                   @Param("employeeId") Integer employeeId,
                                                   @Param("fromDate") LocalDate fromDate,
                                                   @Param("toDate") LocalDate toDate);
}

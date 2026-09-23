package com.example.coffee_hrm.repository;

import com.example.coffee_hrm.entity.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Integer> {

    Optional<Attendance> findByEmployee_IdAndWorkDate(Integer employeeId, LocalDate workDate);

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

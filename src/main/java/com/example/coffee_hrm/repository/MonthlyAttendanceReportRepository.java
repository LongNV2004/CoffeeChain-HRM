package com.example.coffee_hrm.repository;

import com.example.coffee_hrm.common.enums.AttendanceReportStatus;
import com.example.coffee_hrm.entity.MonthlyAttendanceReport;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MonthlyAttendanceReportRepository extends JpaRepository<MonthlyAttendanceReport, Integer> {

    @Query("""
            SELECT r FROM MonthlyAttendanceReport r
            JOIN FETCH r.store s
            LEFT JOIN FETCH s.manager
            LEFT JOIN FETCH r.submittedBy u
            LEFT JOIN FETCH u.employee
            WHERE r.store.id = :storeId
              AND r.reportYear = :year
              AND r.reportMonth = :month
            """)
    Optional<MonthlyAttendanceReport> findCurrent(@Param("storeId") Integer storeId,
                                                  @Param("year") int year,
                                                  @Param("month") int month);

    @Query("""
            SELECT r FROM MonthlyAttendanceReport r
            JOIN FETCH r.store s
            LEFT JOIN FETCH s.manager
            LEFT JOIN FETCH r.submittedBy u
            LEFT JOIN FETCH u.employee
            WHERE r.reportYear = :year
              AND r.reportMonth = :month
            """)
    List<MonthlyAttendanceReport> findByPeriod(@Param("year") int year, @Param("month") int month);

    @Query("""
            SELECT r FROM MonthlyAttendanceReport r
            JOIN FETCH r.store s
            LEFT JOIN FETCH s.manager
            LEFT JOIN FETCH r.submittedBy u
            LEFT JOIN FETCH u.employee
            WHERE r.id = :id
            """)
    Optional<MonthlyAttendanceReport> findByIdDetailed(@Param("id") Integer id);

    /**
     * Khoá báo cáo khi duyệt, trả lại hoặc thanh toán để hai thao tác đồng thời không cùng ghi
     * (ví dụ bấm "Thanh toán" hai lần, hoặc trả lại báo cáo đúng lúc đang thanh toán).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM MonthlyAttendanceReport r WHERE r.id = :id")
    Optional<MonthlyAttendanceReport> findByIdForUpdate(@Param("id") Integer id);

    /** Khoá báo cáo của một kỳ khi kiểm tra đối soát lương để duyệt chấm dứt hợp đồng. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT r FROM MonthlyAttendanceReport r
            WHERE r.store.id = :storeId
              AND r.reportYear = :year
              AND r.reportMonth = :month
            """)
    Optional<MonthlyAttendanceReport> lockCurrent(@Param("storeId") Integer storeId,
                                                  @Param("year") int year,
                                                  @Param("month") int month);

    long countByReportYearAndReportMonthAndSubmittedAtIsNotNullAndStatusNotIn(
            int reportYear,
            int reportMonth,
            Collection<AttendanceReportStatus> statuses);
}

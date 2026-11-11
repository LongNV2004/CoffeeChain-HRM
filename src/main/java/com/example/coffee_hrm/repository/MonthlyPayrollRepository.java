package com.example.coffee_hrm.repository;

import com.example.coffee_hrm.entity.MonthlyPayroll;
import com.example.coffee_hrm.entity.PayrollEmployeeLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MonthlyPayrollRepository extends JpaRepository<MonthlyPayroll, Integer> {

    @Query("""
            SELECT p
            FROM MonthlyPayroll p
            JOIN FETCH p.store
            LEFT JOIN FETCH p.employeeLines l
            LEFT JOIN FETCH l.employee
            WHERE p.store.id = :storeId
              AND p.payrollYear = :year
              AND p.payrollMonth = :month
            """)
    Optional<MonthlyPayroll> findPeriod(@Param("storeId") Integer storeId,
                                        @Param("year") int year,
                                        @Param("month") int month);

    /** Bảng lương của mọi cửa hàng trong một kỳ (danh sách báo cáo của Admin). */
    @Query("""
            SELECT DISTINCT p
            FROM MonthlyPayroll p
            JOIN FETCH p.store
            LEFT JOIN FETCH p.employeeLines l
            LEFT JOIN FETCH l.employee
            WHERE p.payrollYear = :year
              AND p.payrollMonth = :month
            """)
    List<MonthlyPayroll> findByPeriod(@Param("year") int year, @Param("month") int month);

    /** Các dòng lương đã lưu của một nhân viên ở mọi kỳ, mọi cửa hàng (màn hình lương cá nhân). */
    @Query("""
            SELECT l
            FROM PayrollEmployeeLine l
            JOIN FETCH l.payroll p
            JOIN FETCH p.store
            WHERE l.employee.id = :employeeId
            ORDER BY p.payrollYear DESC, p.payrollMonth DESC
            """)
    List<PayrollEmployeeLine> findLinesByEmployeeId(@Param("employeeId") Integer employeeId);
}

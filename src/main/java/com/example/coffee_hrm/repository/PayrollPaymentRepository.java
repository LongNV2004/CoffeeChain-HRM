package com.example.coffee_hrm.repository;

import com.example.coffee_hrm.entity.PayrollPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PayrollPaymentRepository extends JpaRepository<PayrollPayment, Integer> {

    @Query("""
            SELECT p FROM PayrollPayment p
            JOIN FETCH p.employee
            WHERE p.report.id = :reportId
            ORDER BY p.createdAt DESC, p.id DESC
            """)
    List<PayrollPayment> findByReportId(@Param("reportId") Integer reportId);

    @Query("""
            SELECT p FROM PayrollPayment p
            JOIN FETCH p.employee
            WHERE p.report.id IN :reportIds
            """)
    List<PayrollPayment> findByReportIds(@Param("reportIds") Collection<Integer> reportIds);

    @Query("""
            SELECT p FROM PayrollPayment p
            JOIN FETCH p.report r
            JOIN FETCH r.store
            WHERE p.employee.id = :employeeId
            ORDER BY p.paidDate DESC, p.id DESC
            """)
    List<PayrollPayment> findByEmployeeId(@Param("employeeId") Integer employeeId);

    boolean existsByReport_Id(Integer reportId);

    boolean existsByRequestKey(String requestKey);
}

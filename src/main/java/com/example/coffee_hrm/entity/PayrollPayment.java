package com.example.coffee_hrm.entity;

import com.example.coffee_hrm.common.enums.PaymentMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Một lần Admin xác nhận đã trả lương cho một nhân viên. Chỉ thêm mới, không sửa hay xoá. */
@Entity
@Table(name = "PayrollPayments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"report", "payroll", "employee", "paidBy"})
public class PayrollPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PaymentId")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ReportId", nullable = false)
    private MonthlyAttendanceReport report;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "PayrollId", nullable = false)
    private MonthlyPayroll payroll;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "EmployeeId", nullable = false)
    private Employee employee;

    @Column(name = "PayrollYear", nullable = false)
    private Integer payrollYear;

    @Column(name = "PayrollMonth", nullable = false)
    private Integer payrollMonth;

    @Column(name = "Amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "PaidDate", nullable = false)
    private LocalDate paidDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "Method", nullable = false, length = 20)
    private PaymentMethod method;

    @Column(name = "TransactionCode", length = 100)
    private String transactionCode;

    @Column(name = "Note", length = 500)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PaidByUserId")
    private User paidBy;

    @Column(name = "PaidByName", length = 100)
    private String paidByName;

    @Column(name = "RequestKey", nullable = false, length = 80)
    private String requestKey;

    @Column(name = "CreatedAt", nullable = false)
    private LocalDateTime createdAt;
}

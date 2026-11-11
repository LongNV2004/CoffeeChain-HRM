package com.example.coffee_hrm.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Dữ liệu hiển thị cho quy trình báo cáo lương và thanh toán: danh sách báo cáo của Admin, bảng lương của một báo cáo,
 * chi tiết một nhân viên và lương cá nhân của Staff. Số tiền lấy từ bảng lương đã chốt khi Manager gửi báo cáo.
 */
public final class PayrollPaymentViews {

    private PayrollPaymentViews() {
    }

    /** Danh sách báo cáo cửa hàng của Admin trong một kỳ. */
    @Getter
    @Builder
    public static class ReportList {
        private final int year;
        private final int month;
        private final String periodLabel;
        private final Integer filterStoreId;
        private final String filterStatus;
        private final String filterPaymentStatus;
        private final String query;
        private final int totalStores;
        private final int waitingReports;
        private final int approvedReports;
        private final int unsentStores;
        private final String totalPayrollLabel;
        private final String totalPaidLabel;
        private final String totalRemainingLabel;
        @Builder.Default
        private final List<StoreOption> stores = List.of();
        @Builder.Default
        private final List<ReportRow> rows = List.of();
    }

    @Getter
    @Builder
    public static class StoreOption {
        private final Integer storeId;
        private final String storeName;
    }

    @Getter
    @Builder
    public static class ReportRow {
        private final Integer reportId;
        private final Integer storeId;
        private final String storeName;
        private final String periodLabel;
        private final String senderName;
        private final LocalDateTime submittedAt;
        private final boolean submitted;
        private final boolean stale;
        private final boolean hasPayroll;
        private final int employeeCount;
        private final String totalLabel;
        private final String paidLabel;
        private final String remainingLabel;
        private final String statusCode;
        private final String statusLabel;
        private final String statusCss;
        /** Null khi cửa hàng chưa gửi báo cáo. */
        private final String paymentStatusKey;
        private final String paymentStatusLabel;
    }

    /** Bảng lương của một báo cáo: dùng ở trang chi tiết báo cáo (Admin) và trang Báo cáo cửa hàng (Manager). */
    @Getter
    @Builder
    public static class ReportPayroll {
        private final Integer reportId;
        private final Integer storeId;
        private final String storeName;
        private final int year;
        private final int month;
        private final String periodLabel;
        private final String statusCode;
        private final boolean approved;
        private final boolean hasPayroll;
        private final String payrollStatusLabel;
        private final int employeeCount;
        private final BigDecimal total;
        private final BigDecimal paid;
        private final BigDecimal remaining;
        private final String totalLabel;
        private final String paidLabel;
        private final String remainingLabel;
        private final String paymentStatusKey;
        private final String paymentStatusLabel;
        /** Số nhân viên còn phải trả (dùng cho "Thanh toán toàn bộ"). */
        private final int unpaidCount;
        private final boolean canPay;
        private final String payBlockedReason;
        /** Khoá chống gửi trùng cho form thanh toán; mỗi lần mở trang một khoá mới. */
        private final String requestKey;
        private final LocalDate today;
        @Builder.Default
        private final List<EmployeeRow> employees = List.of();
        @Builder.Default
        private final List<PaymentRow> payments = List.of();
    }

    @Getter
    @Builder
    public static class EmployeeRow {
        private final Integer employeeId;
        private final String employeeCode;
        private final String employeeName;
        private final String position;
        private final int assignedShifts;
        private final int attendedShifts;
        private final int validShifts;
        private final int payableHours;
        private final String hourlyRateLabel;
        private final String baseLabel;
        private final String allowanceLabel;
        private final String deductionLabel;
        private final String netLabel;
        private final String paidLabel;
        private final String remainingLabel;
        /** Số còn phải trả, dạng số thuần để điền sẵn vào ô số tiền. */
        private final String remainingValue;
        private final boolean owed;
        private final boolean noSalary;
        private final String paymentStatusKey;
        private final String paymentStatusLabel;
        private final LocalDate lastPaidDate;
    }

    @Getter
    @Builder
    public static class PaymentRow {
        private final Integer paymentId;
        private final Integer employeeId;
        private final String employeeCode;
        private final String employeeName;
        private final String periodLabel;
        private final String storeName;
        private final String amountLabel;
        private final LocalDate paidDate;
        private final String methodLabel;
        private final String transactionCode;
        private final boolean hasProof;
        private final String proofFileName;
        private final String paidByName;
        private final String note;
        private final LocalDateTime createdAt;
    }

    /** Chi tiết lương và thanh toán của một nhân viên trong một báo cáo (Admin). */
    @Getter
    @Builder
    public static class EmployeePayroll {
        private final ReportPayroll report;
        private final EmployeeRow employee;
        @Builder.Default
        private final List<ShiftRow> shifts = List.of();
        @Builder.Default
        private final List<PaymentRow> payments = List.of();
    }

    @Getter
    @Builder
    public static class ShiftRow {
        private final LocalDate workDate;
        private final String shiftName;
        private final String scheduleLabel;
        private final String checkInLabel;
        private final String checkOutLabel;
        private final String attendanceStatus;
        private final int scheduledHours;
        private final int deductedHours;
        private final int payableHours;
        private final String hourlyRateLabel;
        private final String salaryLabel;
        private final boolean payable;
        private final String unpaidReason;
    }

    /** Một kỳ lương đã được Admin duyệt, trên màn hình lương cá nhân của Staff. */
    @Getter
    @Builder
    public static class StaffPeriod {
        private final int year;
        private final int month;
        private final String periodLabel;
        private final String storeName;
        private final int assignedShifts;
        private final int workedShifts;
        private final String baseLabel;
        private final String allowanceLabel;
        private final String deductionLabel;
        private final String netLabel;
        private final String receivedLabel;
        private final String remainingLabel;
        private final String paymentStatusKey;
        private final String paymentStatusLabel;
        private final LocalDate lastPaidDate;
        @Builder.Default
        private final List<PaymentRow> payments = List.of();
    }
}

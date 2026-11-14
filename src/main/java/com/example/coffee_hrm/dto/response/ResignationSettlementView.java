package com.example.coffee_hrm.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Đối soát lương của một nhân sự trước khi Admin duyệt chấm dứt hợp đồng. Mọi số liệu lấy từ bảng lương
 * (MonthlyPayrolls / PayrollEmployeeLines) và giao dịch thanh toán (PayrollPayments) hiện có; không lưu bản sao.
 */
@Getter
@Builder
public class ResignationSettlementView {

    /** Đủ điều kiện duyệt: không còn kỳ chờ tính/đối soát và không còn lương đã xác định chưa trả. */
    public static final String READY = "READY";
    /** Còn lương đã xác định nhưng chưa thanh toán đủ. */
    public static final String OWED = "OWED";
    /** Còn kỳ lương chưa tính, chưa chốt hoặc báo cáo chưa được duyệt. */
    public static final String PENDING = "PENDING";

    private final Integer employeeId;
    private final String employeeName;

    private final ContractInfo contract;

    private final BigDecimal total;
    private final BigDecimal paid;
    private final BigDecimal remaining;
    private final String totalLabel;
    private final String paidLabel;
    private final String remainingLabel;

    private final int periodCount;
    private final int pendingCount;
    private final int owedCount;

    private final boolean eligible;
    private final String statusKey;
    private final String statusLabel;
    private final String statusCss;
    @Builder.Default
    private final List<String> reasons = List.of();

    @Builder.Default
    private final List<Period> periods = List.of();
    @Builder.Default
    private final List<Payment> payments = List.of();

    /** Khoá chống gửi trùng cho form thanh toán vừa hiển thị. */
    private final String requestKey;
    private final LocalDate today;

    public boolean isHasPayable() {
        return periods.stream().anyMatch(Period::isPayable);
    }

    @Getter
    @Builder
    public static class ContractInfo {
        private final String contractCode;
        private final String termLabel;
        private final LocalDate startDate;
        private final LocalDate endDate;
        private final String jobTitle;
        private final String rateLabel;
    }

    /** Một kỳ lương (cửa hàng + tháng) có liên quan tới nhân sự. */
    @Getter
    @Builder
    public static class Period {
        private final Integer reportId;
        private final String storeName;
        private final String periodLabel;
        private final String payrollStatusLabel;
        private final String reportStatusLabel;
        /** Bảng lương đã chốt và báo cáo đã duyệt: số tiền là số chính thức. */
        private final boolean finalized;
        private final String totalLabel;
        private final String paidLabel;
        private final String remainingLabel;
        private final String remainingValue;
        /** Có thể thanh toán ngay tại màn hình đề xuất. */
        private final boolean payable;
        private final String statusKey;
        private final String statusLabel;
        private final String statusCss;
        private final String note;
    }

    @Getter
    @Builder
    public static class Payment {
        private final Integer paymentId;
        private final Integer reportId;
        private final String periodLabel;
        private final String storeName;
        private final String amountLabel;
        private final LocalDate paidDate;
        private final String methodLabel;
        private final String transactionCode;
        private final String paidByName;
        private final String note;
        private final LocalDateTime createdAt;
    }
}

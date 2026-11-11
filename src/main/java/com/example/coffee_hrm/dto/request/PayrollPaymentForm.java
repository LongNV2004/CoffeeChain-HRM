package com.example.coffee_hrm.dto.request;

import lombok.Getter;
import lombok.Setter;

/**
 * Admin xác nhận đã trả lương (chuyển khoản hoặc tiền mặt ngoài hệ thống). Máy chủ kiểm tra lại số tiền còn phải trả.
 */
@Getter
@Setter
public class PayrollPaymentForm {

    /** Chỉ dùng khi thanh toán từng nhân viên; thanh toán toàn bộ luôn trả đúng số còn lại của từng người. */
    private String amount;
    private String method;
    private String paidDate;
    private String transactionCode;
    private String note;
    private String requestKey;
    /** Tổng tiền và số nhân viên Admin đã thấy khi xác nhận "Thanh toán toàn bộ". */
    private String expectedTotal;
    private Integer expectedCount;
}

package com.example.coffee_hrm.service;

import com.example.coffee_hrm.dto.request.PayrollPaymentForm;
import com.example.coffee_hrm.dto.response.ResignationSettlementView;
import com.example.coffee_hrm.security.AuthenticatedUser;

/**
 * Đối soát và thanh toán lương còn thiếu trước khi duyệt chấm dứt hợp đồng.
 * Dùng lại bảng lương, báo cáo tháng và {@link PayrollPaymentService}; không tính lương lại.
 */
public interface ResignationSettlementService {

    /** Đối soát lương của nhân sự (Admin). */
    ResignationSettlementView settlement(AuthenticatedUser admin, Integer employeeId);

    /**
     * Kiểm tra lại ở backend trong transaction duyệt: khoá các báo cáo tháng liên quan rồi đối soát.
     * Ném BusinessException kèm lý do nếu chưa đủ điều kiện.
     */
    void assertSettled(Integer employeeId, String employeeName);

    /**
     * Thanh toán một kỳ lương còn thiếu của nhân sự trong đề xuất chấm dứt đang chờ duyệt.
     * Ghi nhận qua {@link PayrollPaymentService#payEmployee}. Trả về số tiền đã ghi nhận (định dạng hiển thị).
     */
    String pay(AuthenticatedUser admin,
               Integer proposalId,
               Integer itemId,
               int reportId,
               PayrollPaymentForm form);
}

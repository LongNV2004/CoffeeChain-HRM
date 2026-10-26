package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.ContractValidityStatus;
import com.example.coffee_hrm.common.exception.BusinessException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Trạng thái hiệu lực tính từ ngày, không lưu thành cột.
 * Ngày kết thúc là ngày cuối cùng hợp đồng còn hiệu lực.
 * SAP_HET_HAN chỉ xuất hiện khi đã cấu hình số ngày cảnh báo dương.
 */
public final class ContractValidityRules {

    private ContractValidityRules() {
    }

    public static ContractValidityStatus resolve(LocalDate startDate,
                                                 LocalDate endDate,
                                                 LocalDate terminatedDate,
                                                 LocalDate today,
                                                 Integer expiringWithinDays) {
        if (startDate == null || today == null) {
            throw new BusinessException("Thiếu ngày bắt đầu hiệu lực hoặc ngày hiện tại.");
        }
        if (terminatedDate != null && !terminatedDate.isAfter(today)) {
            return ContractValidityStatus.DA_CHAM_DUT;
        }
        if (startDate.isAfter(today)) {
            return ContractValidityStatus.CHUA_HIEU_LUC;
        }
        if (endDate != null && endDate.isBefore(today)) {
            return ContractValidityStatus.DA_HET_HAN;
        }
        if (endDate != null && expiringWithinDays != null) {
            if (expiringWithinDays < 1) {
                throw new BusinessException(
                        "Ngưỡng sắp hết hạn hợp đồng không hợp lệ. Cấu hình coffee-hrm.contract.expiring-within-days phải là số nguyên dương.");
            }
            long daysRemaining = ChronoUnit.DAYS.between(today, endDate);
            if (daysRemaining <= expiringWithinDays) {
                return ContractValidityStatus.SAP_HET_HAN;
            }
        }
        return ContractValidityStatus.DANG_HIEU_LUC;
    }
}

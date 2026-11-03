package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.ContractValidityStatus;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.entity.EmploymentContract;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Trạng thái hiệu lực tính từ ngày, không lưu thành cột.
 * Ngày kết thúc là ngày cuối cùng hợp đồng còn hiệu lực.
 * Ngày chấm dứt là ngày hợp đồng không còn hiệu lực: ca làm từ ngày này trở đi không được đăng ký.
 * Chờ gia hạn chỉ trong 7 ngày cuối, tính cả ngày hết hạn, khi hợp đồng đã bắt đầu và chưa chấm dứt.
 * Hợp đồng đã tạo nhưng chưa tới ngày bắt đầu vẫn là Đang hiệu lực, vì hệ thống không có trạng thái thứ năm.
 */
public final class ContractValidityRules {

    public static final int RENEWAL_WINDOW_DAYS = 7;

    private ContractValidityRules() {
    }

    public static ContractValidityStatus resolve(LocalDate startDate,
                                                 LocalDate endDate,
                                                 LocalDate terminatedDate,
                                                 LocalDate today) {
        if (startDate == null || today == null) {
            throw new BusinessException("Thiếu ngày bắt đầu hiệu lực hoặc ngày hiện tại.");
        }
        if (terminatedDate != null && !terminatedDate.isAfter(today)) {
            if (endDate != null && terminatedDate.isBefore(endDate)) {
                return ContractValidityStatus.DA_CHAM_DUT_TRUOC_THOI_HAN;
            }
            return ContractValidityStatus.DA_CHAM_DUT;
        }
        if (endDate != null && endDate.isBefore(today)) {
            return ContractValidityStatus.DA_CHAM_DUT;
        }
        if (!startDate.isAfter(today) && endDate != null) {
            long daysRemaining = ChronoUnit.DAYS.between(today, endDate);
            if (daysRemaining <= RENEWAL_WINDOW_DAYS) {
                return ContractValidityStatus.CHO_GIA_HAN;
            }
        }
        return ContractValidityStatus.DANG_HIEU_LUC;
    }

    public static ContractValidityStatus resolve(EmploymentContract contract, LocalDate today) {
        if (contract == null) {
            throw new BusinessException("Không tìm thấy hợp đồng.");
        }
        return resolve(contract.getStartDate(), contract.getEndDate(), contract.getTerminatedDate(), today);
    }

    /**
     * Ngày cuối được làm. Ngày kết thúc vẫn được tính. Ngày chấm dứt thì không.
     * Không có mốc kết thúc thì trả về null.
     */
    public static LocalDate lastWorkDay(LocalDate endDate, LocalDate terminatedDate) {
        LocalDate last = endDate;
        if (terminatedDate != null) {
            LocalDate beforeTermination = terminatedDate.minusDays(1);
            if (last == null || beforeTermination.isBefore(last)) {
                last = beforeTermination;
            }
        }
        return last;
    }

    public static boolean coversWorkDay(EmploymentContract contract, LocalDate day) {
        if (contract == null || contract.getStartDate() == null || day == null || contract.getStartDate().isAfter(day)) {
            return false;
        }
        LocalDate last = lastWorkDay(contract.getEndDate(), contract.getTerminatedDate());
        return last == null || !day.isAfter(last);
    }
}

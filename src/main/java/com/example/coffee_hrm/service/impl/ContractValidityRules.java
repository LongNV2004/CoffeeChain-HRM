package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.ContractValidityStatus;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.entity.EmploymentContract;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Trạng thái hiệu lực tính từ ngày, không lưu thành cột.
 * Ngày kết thúc là ngày cuối cùng hợp đồng còn hiệu lực.
 * Ngày chấm dứt là ngày hợp đồng không còn hiệu lực: ca làm từ ngày này trở đi không được đăng ký.
 * Chờ gia hạn chỉ trong 7 ngày cuối, tính cả ngày hết hạn, khi hợp đồng đã bắt đầu và chưa chấm dứt.
 * Hợp đồng tạo mới chưa tới ngày bắt đầu vẫn là Đang hiệu lực.
 * Hợp đồng nguồn vừa được gia hạn là Đã gia hạn cho đến hết ngày kết thúc của chính nó, rồi sang Đã hết hạn.
 * Hợp đồng gia hạn chưa tới ngày bắt đầu là Chờ hiệu lực. Đúng ngày bắt đầu thì sang Đang hiệu lực.
 * Ngưng gia hạn là Đã chấm dứt trước thời hạn cho đến hết ngày kết thúc, rồi sang Đã chấm dứt.
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
        return resolve(startDate, endDate, terminatedDate, today, null, false);
    }

    public static ContractValidityStatus resolve(LocalDate startDate,
                                                 LocalDate endDate,
                                                 LocalDate terminatedDate,
                                                 LocalDate today,
                                                 Integer previousContractId,
                                                 boolean hasDirectRenewal) {
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
            return ContractValidityStatus.DA_HET_HAN;
        }
        if (previousContractId != null && startDate.isAfter(today)) {
            return ContractValidityStatus.CHO_HIEU_LUC;
        }
        if (hasDirectRenewal) {
            return ContractValidityStatus.DA_GIA_HAN;
        }
        if (!startDate.isAfter(today) && endDate != null) {
            long daysRemaining = ChronoUnit.DAYS.between(today, endDate);
            if (daysRemaining <= RENEWAL_WINDOW_DAYS) {
                return ContractValidityStatus.CHO_GIA_HAN;
            }
        }
        return ContractValidityStatus.DANG_HIEU_LUC;
    }

    /**
     * Hợp đồng nguồn đã có bản gia hạn nhưng bản mới chưa tới ngày hiệu lực thì hiển thị Đã gia hạn.
     * Lương và số ca vẫn theo hợp đồng nguồn cho đến khi bản mới được kích hoạt.
     */
    public static ContractValidityStatus display(EmploymentContract contract, LocalDate today, boolean hasDirectRenewal) {
        return resolve(contract, today, hasDirectRenewal);
    }

    public static ContractValidityStatus resolve(EmploymentContract contract, LocalDate today) {
        return resolve(contract, today, false);
    }

    public static ContractValidityStatus resolve(EmploymentContract contract, LocalDate today, boolean hasDirectRenewal) {
        if (contract == null) {
            throw new BusinessException("Không tìm thấy hợp đồng.");
        }
        if (contract.getRenewalDeclinedAt() != null) {
            if (contract.getEndDate() != null && contract.getEndDate().isBefore(today)) {
                return ContractValidityStatus.DA_CHAM_DUT;
            }
            return ContractValidityStatus.DA_CHAM_DUT_TRUOC_THOI_HAN;
        }
        Integer previousId = contract.getPreviousContract() == null ? null : contract.getPreviousContract().getId();
        return resolve(contract.getStartDate(), contract.getEndDate(), contract.getTerminatedDate(), today,
                previousId, hasDirectRenewal);
    }

    /** Hợp đồng áp dụng cho một ngày làm: bản bắt đầu muộn hơn được ưu tiên khi hai bản cùng bao ngày đó. */
    public static EmploymentContract applicable(List<EmploymentContract> contracts, LocalDate day) {
        EmploymentContract chosen = null;
        if (contracts == null || day == null) {
            return null;
        }
        for (EmploymentContract contract : contracts) {
            if (!coversWorkDay(contract, day)) {
                continue;
            }
            if (chosen == null || startsLater(contract, chosen)) {
                chosen = contract;
            }
        }
        return chosen;
    }

    /**
     * Ngày cuối được làm. Ngày kết thúc vẫn được tính. Ngày chấm dứt thì không.
     * Không có mốc kết thúc thì trả về null.
     */
    public static LocalDate lastWorkDay(LocalDate endDate, LocalDate terminatedDate) {
        return lastWorkDay(endDate, terminatedDate, null);
    }

    public static LocalDate lastWorkDay(LocalDate endDate, LocalDate terminatedDate, LocalDate supersededDate) {
        LocalDate last = endDate;
        if (terminatedDate != null) {
            last = earlier(last, terminatedDate.minusDays(1));
        }
        if (supersededDate != null) {
            last = earlier(last, supersededDate.minusDays(1));
        }
        return last;
    }

    public static LocalDate lastWorkDay(EmploymentContract contract) {
        if (contract == null) {
            return null;
        }
        return lastWorkDay(contract.getEndDate(), contract.getTerminatedDate(), null);
    }

    private static boolean startsLater(EmploymentContract candidate, EmploymentContract current) {
        LocalDate candidateStart = candidate.getStartDate();
        LocalDate currentStart = current.getStartDate();
        if (candidateStart == null) {
            return false;
        }
        if (currentStart == null || candidateStart.isAfter(currentStart)) {
            return true;
        }
        if (candidateStart.isBefore(currentStart)) {
            return false;
        }
        Integer candidateId = candidate.getId();
        Integer currentId = current.getId();
        return candidateId != null && (currentId == null || candidateId > currentId);
    }

    public static boolean coversWorkDay(EmploymentContract contract, LocalDate day) {
        if (contract == null || contract.getStartDate() == null || day == null || contract.getStartDate().isAfter(day)) {
            return false;
        }
        LocalDate last = lastWorkDay(contract);
        return last == null || !day.isAfter(last);
    }

    private static LocalDate earlier(LocalDate current, LocalDate candidate) {
        if (current == null || candidate.isBefore(current)) {
            return candidate;
        }
        return current;
    }
}

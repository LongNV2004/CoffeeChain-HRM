package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.ContractValidityStatus;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.entity.EmploymentContract;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Trạng thái hiệu lực tính từ ngày, dùng chung cho Manager và Staff.
 * Ngày kết thúc là ngày cuối cùng còn được làm. Ngày chấm dứt do Admin phê duyệt thì hợp đồng sang Đã chấm dứt ngay.
 * Hợp đồng chưa tới ngày bắt đầu là Chờ hiệu lực, kể cả hợp đồng đầu tiên.
 * Hợp đồng không xác định thời hạn chỉ có Chờ hiệu lực, Đang hiệu lực và Đã chấm dứt.
 * Chờ gia hạn chỉ trong 7 ngày cuối của hợp đồng xác định thời hạn.
 * Đã gia hạn giữ đến hết ngày kết thúc của bản cũ rồi sang Đã hết hạn.
 * Không gia hạn giữ đến hết ngày kết thúc rồi sang Đã hết hạn.
 */
public final class ContractValidityRules {

    public static final int RENEWAL_WINDOW_DAYS = 7;
    /** Bộ luật Lao động: hợp đồng xác định thời hạn không quá 36 tháng. */
    public static final int FIXED_TERM_MAX_MONTHS = 36;

    private ContractValidityRules() {
    }

    public static void requireFixedTermLength(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new BusinessException("Hợp đồng xác định thời hạn phải có ngày bắt đầu và ngày kết thúc.");
        }
        if (endDate.isAfter(startDate.plusMonths(FIXED_TERM_MAX_MONTHS))) {
            throw new BusinessException("Hợp đồng xác định thời hạn không được dài quá 36 tháng.");
        }
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
            return ContractValidityStatus.DA_CHAM_DUT;
        }
        if (endDate == null) {
            return startDate.isAfter(today)
                    ? ContractValidityStatus.CHO_HIEU_LUC
                    : ContractValidityStatus.DANG_HIEU_LUC;
        }
        if (endDate.isBefore(today)) {
            return ContractValidityStatus.DA_HET_HAN;
        }
        if (startDate.isAfter(today)) {
            return ContractValidityStatus.CHO_HIEU_LUC;
        }
        if (hasDirectRenewal) {
            return ContractValidityStatus.DA_GIA_HAN;
        }
        long daysRemaining = ChronoUnit.DAYS.between(today, endDate);
        if (daysRemaining <= RENEWAL_WINDOW_DAYS) {
            return ContractValidityStatus.CHO_GIA_HAN;
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
        if (contract.getTerminatedDate() != null && !contract.getTerminatedDate().isAfter(today)) {
            return ContractValidityStatus.DA_CHAM_DUT;
        }
        if (contract.getRenewalDeclinedAt() != null && contract.getEndDate() != null) {
            if (contract.getEndDate().isBefore(today)) {
                return ContractValidityStatus.DA_HET_HAN;
            }
            return ContractValidityStatus.KHONG_GIA_HAN;
        }
        Integer previousId = contract.getPreviousContract() == null ? null : contract.getPreviousContract().getId();
        return resolve(contract.getStartDate(), contract.getEndDate(), contract.getTerminatedDate(), today,
                previousId, hasDirectRenewal);
    }

    public enum WorkAccess {
        /** Có hợp đồng còn cho làm việc trong ngày. */
        WORKING,
        /** Chưa có hợp đồng hoặc chỉ có hợp đồng chờ hiệu lực. */
        WAITING,
        /** Mọi hợp đồng đã hết hạn hoặc đã chấm dứt, không có bản tiếp nối đang mở. */
        ENDED
    }

    public static boolean hasDirectRenewal(List<EmploymentContract> contracts, Integer contractId) {
        if (contractId == null || contracts == null) {
            return false;
        }
        for (EmploymentContract contract : contracts) {
            EmploymentContract previous = contract == null ? null : contract.getPreviousContract();
            if (previous != null && contractId.equals(previous.getId())) {
                return true;
            }
        }
        return false;
    }

    /** Quyền làm việc trong ngày, dùng chung cho Manager và Staff. */
    public static WorkAccess workAccess(List<EmploymentContract> contracts, LocalDate today) {
        if (contracts == null || contracts.isEmpty() || today == null) {
            return WorkAccess.WAITING;
        }
        EmploymentContract current = applicable(contracts, today);
        if (current != null) {
            ContractValidityStatus status = resolve(current, today, hasDirectRenewal(contracts, current.getId()));
            if (status.allowsWork()) {
                return WorkAccess.WORKING;
            }
        }
        for (EmploymentContract contract : contracts) {
            if (contract != null && resolve(contract, today, hasDirectRenewal(contracts, contract.getId()))
                    == ContractValidityStatus.CHO_HIEU_LUC) {
                return WorkAccess.WAITING;
            }
        }
        return WorkAccess.ENDED;
    }

    /**
     * Hợp đồng hiện tại được phép đề xuất chấm dứt: bản còn cho làm việc, hoặc bản chờ hiệu lực nếu chưa có bản đang làm.
     */
    public static EmploymentContract openContract(List<EmploymentContract> contracts, LocalDate today) {
        if (contracts == null || today == null) {
            return null;
        }
        EmploymentContract current = applicable(contracts, today);
        if (current != null) {
            ContractValidityStatus status = resolve(current, today, hasDirectRenewal(contracts, current.getId()));
            if (status.allowsWork()) {
                return current;
            }
        }
        EmploymentContract future = null;
        for (EmploymentContract contract : contracts) {
            if (contract == null) {
                continue;
            }
            if (resolve(contract, today, hasDirectRenewal(contracts, contract.getId())) != ContractValidityStatus.CHO_HIEU_LUC) {
                continue;
            }
            if (future == null || startsLater(contract, future)) {
                future = contract;
            }
        }
        return future;
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

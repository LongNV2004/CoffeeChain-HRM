package com.example.coffee_hrm.service.support;

import com.example.coffee_hrm.common.enums.ContractValidityStatus;
import com.example.coffee_hrm.common.enums.EmployeeStatus;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.entity.Employee;
import com.example.coffee_hrm.entity.EmploymentContract;
import com.example.coffee_hrm.service.impl.ContractValidityRules;

import java.time.LocalDate;
import java.util.List;

/**
 * Ghi nhận chấm dứt có hiệu lực và chặn gia hạn hợp đồng đã chấm dứt trước thời hạn.
 * Đề xuất nghỉ việc chưa tới ngày hiệu lực không đi qua lớp này.
 */
public final class ContractLifecycle {

    public static final String CANNOT_RENEW_EARLY_TERMINATION =
            "Không thể gia hạn hợp đồng đã chấm dứt trước thời hạn.";

    private ContractLifecycle() {
    }

    /**
     * Ghi ngày chấm dứt lên hợp đồng còn hiệu lực. Hợp đồng đã chấm dứt được giữ nguyên.
     *
     * @return số hợp đồng vừa được ghi nhận
     */
    public static int applyEffectiveTermination(List<EmploymentContract> contracts,
                                                LocalDate effectiveDate,
                                                LocalDate today) {
        if (contracts == null || effectiveDate == null || today == null) {
            return 0;
        }
        int changed = 0;
        for (EmploymentContract contract : contracts) {
            if (contract == null || contract.getStartDate() == null) {
                continue;
            }
            ContractValidityStatus status = ContractValidityRules.resolve(contract, today);
            if (status.isClosed()) {
                continue;
            }
            if (contract.getEndDate() != null
                    && effectiveDate.isAfter(contract.getEndDate())
                    && contract.getEndDate().isBefore(today)) {
                continue;
            }
            if (contract.getTerminatedDate() != null && !contract.getTerminatedDate().isAfter(today)) {
                continue;
            }
            contract.setTerminatedDate(effectiveDate);
            changed++;
        }
        return changed;
    }

    public static void requireRenewable(List<EmploymentContract> contracts, Employee employee, LocalDate today) {
        EmploymentContract latest = latest(contracts);
        if (latest == null || latest.getStartDate() == null || today == null) {
            return;
        }
        if (ContractValidityRules.resolve(latest, today) != ContractValidityStatus.DA_CHAM_DUT_TRUOC_THOI_HAN) {
            return;
        }
        boolean rehired = employee != null
                && employee.getStatus() == EmployeeStatus.ACTIVE
                && employee.getTerminationDate() == null;
        if (!rehired) {
            throw new BusinessException(CANNOT_RENEW_EARLY_TERMINATION);
        }
    }

    private static EmploymentContract latest(List<EmploymentContract> contracts) {
        EmploymentContract latest = null;
        if (contracts == null) {
            return null;
        }
        for (EmploymentContract contract : contracts) {
            if (contract == null) {
                continue;
            }
            if (latest == null || startsAfter(contract, latest)) {
                latest = contract;
            }
        }
        return latest;
    }

    private static boolean startsAfter(EmploymentContract candidate, EmploymentContract current) {
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
}

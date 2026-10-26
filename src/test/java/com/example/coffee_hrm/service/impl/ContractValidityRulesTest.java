package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.ContractValidityStatus;
import com.example.coffee_hrm.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContractValidityRulesTest {

    private final LocalDate today = LocalDate.of(2026, 10, 25);

    @Test
    void futureStartIsNotYetEffective() {
        assertEquals(ContractValidityStatus.CHUA_HIEU_LUC,
                ContractValidityRules.resolve(today.plusDays(1), today.plusDays(40), null, today, 30));
    }

    @Test
    void openEndedContractStaysActiveAfterStart() {
        assertEquals(ContractValidityStatus.DANG_HIEU_LUC,
                ContractValidityRules.resolve(today.minusDays(10), null, null, today, 30));
    }

    @Test
    void endDateIsInclusiveAndUsesConfiguredWarningWindow() {
        assertEquals(ContractValidityStatus.DANG_HIEU_LUC,
                ContractValidityRules.resolve(today.minusDays(10), today.plusDays(31), null, today, 30));
        assertEquals(ContractValidityStatus.SAP_HET_HAN,
                ContractValidityRules.resolve(today.minusDays(10), today.plusDays(30), null, today, 30));
        assertEquals(ContractValidityStatus.SAP_HET_HAN,
                ContractValidityRules.resolve(today.minusDays(10), today, null, today, 30));
    }

    @Test
    void missingWarningWindowDoesNotMarkExpiring() {
        assertEquals(ContractValidityStatus.DANG_HIEU_LUC,
                ContractValidityRules.resolve(today.minusDays(10), today.plusDays(5), null, today, null));
    }

    @Test
    void dayAfterEndDateIsExpired() {
        assertEquals(ContractValidityStatus.DA_HET_HAN,
                ContractValidityRules.resolve(today.minusDays(40), today.minusDays(1), null, today, 30));
    }

    @Test
    void reachedTerminationDateOverridesTheTerm() {
        assertEquals(ContractValidityStatus.DA_CHAM_DUT,
                ContractValidityRules.resolve(today.minusDays(10), today.plusDays(100), today, today, 30));
        assertEquals(ContractValidityStatus.DANG_HIEU_LUC,
                ContractValidityRules.resolve(today.minusDays(10), today.plusDays(100), today.plusDays(1), today, 30));
    }

    @Test
    void invalidWarningWindowIsRejected() {
        assertThrows(BusinessException.class,
                () -> ContractValidityRules.resolve(today, today.plusDays(1), null, today, 0));
    }
}

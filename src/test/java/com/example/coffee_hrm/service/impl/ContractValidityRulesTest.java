package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.ContractValidityStatus;
import com.example.coffee_hrm.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractValidityRulesTest {

    private final LocalDate today = LocalDate.of(2026, 10, 25);

    @Test
    void createdContractBeforeStartStaysInForce() {
        assertEquals(ContractValidityStatus.DANG_HIEU_LUC,
                ContractValidityRules.resolve(today.plusDays(1), today.plusDays(40), null, today));
    }

    @Test
    void openEndedContractStaysActiveAfterStart() {
        assertEquals(ContractValidityStatus.DANG_HIEU_LUC,
                ContractValidityRules.resolve(today.minusDays(10), null, null, today));
    }

    @Test
    void renewalWindowIsTheLastSevenDaysIncludingTheEndDate() {
        assertEquals(ContractValidityStatus.DANG_HIEU_LUC,
                ContractValidityRules.resolve(today.minusDays(10), today.plusDays(8), null, today));
        assertEquals(ContractValidityStatus.CHO_GIA_HAN,
                ContractValidityRules.resolve(today.minusDays(10), today.plusDays(7), null, today));
        assertEquals(ContractValidityStatus.CHO_GIA_HAN,
                ContractValidityRules.resolve(today.minusDays(10), today, null, today));
    }

    @Test
    void dayAfterEndDateIsTerminated() {
        assertEquals(ContractValidityStatus.DA_CHAM_DUT,
                ContractValidityRules.resolve(today.minusDays(40), today.minusDays(1), null, today));
    }

    @Test
    void earlyTerminationIsDistinctFromEndingOnTheOriginalDate() {
        assertEquals(ContractValidityStatus.DA_CHAM_DUT_TRUOC_THOI_HAN,
                ContractValidityRules.resolve(today.minusDays(10), today.plusDays(100), today, today));
        assertEquals(ContractValidityStatus.DA_CHAM_DUT,
                ContractValidityRules.resolve(today.minusDays(30), today, today, today));
        assertEquals(ContractValidityStatus.CHO_GIA_HAN,
                ContractValidityRules.resolve(today.minusDays(10), today.plusDays(3), today.plusDays(1), today));
    }

    @Test
    void futureTerminationDoesNotCloseAContractThatStillHasTime() {
        ContractValidityStatus status = ContractValidityRules.resolve(
                today.minusDays(10), today.plusDays(100), today.plusDays(1), today);
        assertEquals(ContractValidityStatus.DANG_HIEU_LUC, status);
        assertTrue(status.isInForce());
        assertFalse(status.isClosed());
    }

    @Test
    void missingDatesAreRejected() {
        assertThrows(BusinessException.class,
                () -> ContractValidityRules.resolve(null, today.plusDays(1), null, today));
    }

    @Test
    void terminationDateIsNotAWorkDayAndEndDateIs() {
        assertEquals(today, ContractValidityRules.lastWorkDay(today.plusDays(10), today.plusDays(1)));
        assertEquals(today.minusDays(1), ContractValidityRules.lastWorkDay(today.plusDays(10), today));
        assertEquals(today, ContractValidityRules.lastWorkDay(today, null));
    }
}

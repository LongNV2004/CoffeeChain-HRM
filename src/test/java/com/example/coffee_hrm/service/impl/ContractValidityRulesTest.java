package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.ContractValidityStatus;
import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.entity.EmploymentContract;
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
        assertEquals(ContractValidityStatus.CHO_HIEU_LUC,
                ContractValidityRules.resolve(today.plusDays(1), today.plusDays(40), null, today));
    }

    @Test
    void fixedTermCannotExceedThirtySixMonthsAndOpenEndedDoesNotWaitForRenewal() {
        assertThrows(BusinessException.class, () -> ContractValidityRules.requireFixedTermLength(
                today, today.plusMonths(36).plusDays(1)));
        ContractValidityRules.requireFixedTermLength(today, today.plusMonths(36));
        assertEquals(ContractValidityStatus.DANG_HIEU_LUC,
                ContractValidityRules.resolve(today.minusDays(10), null, null, today));
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
    void dayAfterEndDateIsExpired() {
        assertEquals(ContractValidityStatus.DA_HET_HAN,
                ContractValidityRules.resolve(today.minusDays(40), today.minusDays(1), null, today));
    }

    @Test
    void renewalStatusesFollowEachContractDate() {
        assertEquals(ContractValidityStatus.CHO_HIEU_LUC,
                ContractValidityRules.resolve(today.plusDays(3), today.plusDays(40), null, today, 8, false));
        assertEquals(ContractValidityStatus.DANG_HIEU_LUC,
                ContractValidityRules.resolve(today, today.plusDays(40), null, today, 8, false));
        assertEquals(ContractValidityStatus.DA_GIA_HAN,
                ContractValidityRules.resolve(today.minusDays(20), today.plusDays(10), null, today, null, true));
        assertEquals(ContractValidityStatus.DA_HET_HAN,
                ContractValidityRules.resolve(today.minusDays(40), today.minusDays(1), null, today, null, true));
        EmploymentContract source = EmploymentContract.builder()
                .id(1)
                .startDate(today.minusDays(20))
                .endDate(today.plusDays(10))
                .salary(new java.math.BigDecimal("10000"))
                .build();
        EmploymentContract renewal = EmploymentContract.builder()
                .id(2)
                .previousContract(source)
                .startDate(today.plusDays(3))
                .endDate(today.plusDays(40))
                .salary(new java.math.BigDecimal("25000"))
                .build();
        assertEquals(source, ContractValidityRules.applicable(java.util.List.of(source, renewal), today));
        assertEquals(renewal, ContractValidityRules.applicable(java.util.List.of(source, renewal), today.plusDays(3)));
        source.setRenewalDeclinedAt(java.time.LocalDateTime.now());
        assertEquals(ContractValidityStatus.KHONG_GIA_HAN,
                ContractValidityRules.resolve(source, today));
        source.setEndDate(today.minusDays(1));
        assertEquals(ContractValidityStatus.DA_HET_HAN, ContractValidityRules.resolve(source, today));
        assertTrue(ContractValidityRules.coversWorkDay(
                EmploymentContract.builder().startDate(today.minusDays(20)).endDate(today.plusDays(10))
                        .renewalDeclinedAt(java.time.LocalDateTime.now()).build(),
                today));
    }

    @Test
    void earlyTerminationIsDistinctFromEndingOnTheOriginalDate() {
        assertEquals(ContractValidityStatus.DA_CHAM_DUT,
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

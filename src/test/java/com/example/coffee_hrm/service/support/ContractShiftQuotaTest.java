package com.example.coffee_hrm.service.support;

import com.example.coffee_hrm.common.exception.BusinessException;
import com.example.coffee_hrm.entity.EmploymentContract;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractShiftQuotaTest {

    private static final LocalDate WEEK = LocalDate.of(2026, 11, 2);

    @Test
    void fiveAndSixShiftsAreShortOfSeven() {
        BusinessException five = assertThrows(BusinessException.class,
                () -> ContractShiftQuota.requireExact(List.of(contract(7)), WEEK, WEEK.plusDays(6), Map.of(WEEK, 5)));
        BusinessException six = assertThrows(BusinessException.class,
                () -> ContractShiftQuota.requireExact(List.of(contract(7)), WEEK, WEEK.plusDays(6), Map.of(WEEK, 6)));

        assertEquals(
                "Bạn cần đăng ký đủ 7 ca/tuần theo hợp đồng. Hiện tại bạn mới chọn 5 ca, còn thiếu 2 ca.",
                five.getMessage());
        assertEquals(
                "Bạn cần đăng ký đủ 7 ca/tuần theo hợp đồng. Hiện tại bạn mới chọn 6 ca, còn thiếu 1 ca.",
                six.getMessage());
    }

    @Test
    void sevenShiftsMatchTheContract() {
        ContractShiftQuota.requireExact(List.of(contract(7)), WEEK, WEEK.plusDays(6), Map.of(WEEK, 7));
        assertEquals("Đã đăng ký đủ 7/7 ca.", ContractShiftQuota.readyLabel(7));
    }

    @Test
    void eightShiftsExceedTheContract() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> ContractShiftQuota.requireExact(List.of(contract(7)), WEEK, WEEK.plusDays(6), Map.of(WEEK, 8)));

        assertEquals("Số ca đăng ký vượt quy định hợp đồng. Bạn chỉ được đăng ký tối đa 7 ca/tuần.", ex.getMessage());
    }

    @Test
    void adjacentWeeksAreCheckedSeparately() {
        LocalDate next = WEEK.plusWeeks(1);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> ContractShiftQuota.requireExact(
                        List.of(contract(7)), WEEK, next.plusDays(6), Map.of(WEEK, 4, next, 4)));

        assertTrue(ex.getMessage().contains("mới chọn 4 ca"));
        assertTrue(!ex.getMessage().contains("8"));
    }

    @Test
    void eachFullWeekCanPassOnItsOwn() {
        LocalDate next = WEEK.plusWeeks(1);
        ContractShiftQuota.requireExact(
                List.of(contract(7)), WEEK, next.plusDays(6), Map.of(WEEK, 7, next, 7));
    }

    @Test
    void missingContractAndMissingQuotaBlockTheWeek() {
        assertEquals(ContractShiftQuota.NO_CONTRACT,
                ContractShiftQuota.describe(List.of(), WEEK).blockMessage());
        EmploymentContract blank = contract(null);
        assertEquals(ContractShiftQuota.NO_QUOTA,
                ContractShiftQuota.describe(List.of(blank), WEEK).blockMessage());
    }

    @Test
    void expiredOrFutureContractDoesNotCoverTheWeek() {
        EmploymentContract expired = contract(7);
        expired.setEndDate(WEEK.minusDays(1));
        EmploymentContract future = contract(7);
        future.setStartDate(WEEK.plusWeeks(2));
        future.setEndDate(WEEK.plusMonths(2));

        assertEquals(ContractShiftQuota.NO_CONTRACT, ContractShiftQuota.describe(List.of(expired), WEEK).blockMessage());
        assertEquals(ContractShiftQuota.NO_CONTRACT, ContractShiftQuota.describe(List.of(future), WEEK).blockMessage());
    }

    @Test
    void partialContractWeekIsNotProrated() {
        EmploymentContract midweek = contract(7);
        midweek.setStartDate(WEEK.plusDays(2));

        assertEquals(ContractShiftQuota.PARTIAL_WEEK,
                ContractShiftQuota.describe(List.of(midweek), WEEK).blockMessage());
        assertNull(ContractShiftQuota.describe(List.of(midweek), WEEK).required());
    }

    @Test
    void durationTailInsideAFullContractWeekDoesNotChangeTheQuota() {
        ContractShiftQuota.requireExact(
                List.of(contract(7)),
                WEEK,
                WEEK.plusWeeks(1).plusDays(2),
                Map.of(WEEK, 7));
    }

    private static EmploymentContract contract(Integer shifts) {
        return EmploymentContract.builder()
                .startDate(LocalDate.of(2026, 1, 5))
                .endDate(LocalDate.of(2026, 12, 31))
                .weeklyShiftCount(shifts)
                .build();
    }
}

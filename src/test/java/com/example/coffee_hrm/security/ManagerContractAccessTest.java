package com.example.coffee_hrm.security;

import com.example.coffee_hrm.entity.EmploymentContract;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManagerContractAccessTest {

    private final LocalDate today = LocalDate.of(2026, 11, 6);

    @Test
    void noContractBlocksStoreWorkAndKeepsProfile() {
        assertEquals(ManagerContractAccess.Limit.NO_CONTRACT, ManagerContractAccess.assess(List.of(), today));
        assertEquals(
                "Tài khoản của bạn đang chờ hoàn thiện hợp đồng lao động. Vui lòng liên hệ Admin để được hỗ trợ.",
                ManagerContractAccess.message(ManagerContractAccess.Limit.NO_CONTRACT));
        assertTrue(ManagerContractAccess.allowsWhenLimited("/profile", "GET"));
        assertTrue(ManagerContractAccess.allowsWhenLimited("/profile", "POST"));
        assertTrue(ManagerContractAccess.allowsWhenLimited("/profile/password", "POST"));
        assertFalse(ManagerContractAccess.allowsWhenLimited("/dashboard/manager", "GET"));
        assertFalse(ManagerContractAccess.allowsWhenLimited("/manager/employees", "GET"));
        assertFalse(ManagerContractAccess.allowsWhenLimited("/schedule/manager", "GET"));
        assertFalse(ManagerContractAccess.allowsWhenLimited("/attendance/manager/choose", "GET"));
        assertFalse(ManagerContractAccess.allowsWhenLimited("/manager/salary", "GET"));
        assertFalse(ManagerContractAccess.allowsWhenLimited("/training", "GET"));
        assertFalse(ManagerContractAccess.allowsWhenLimited("/manager/recruitment", "POST"));
    }

    @Test
    void contractWaitingToStartDoesNotGrantAccess() {
        EmploymentContract waiting = EmploymentContract.builder()
                .id(2)
                .previousContract(EmploymentContract.builder().id(1).build())
                .startDate(today.plusDays(10))
                .endDate(today.plusMonths(12))
                .build();

        assertEquals(ManagerContractAccess.Limit.NOT_IN_FORCE,
                ManagerContractAccess.assess(List.of(waiting), today));
    }

    @Test
    void futureFirstContractIsNotGrantedFromTheInForceLabelAlone() {
        EmploymentContract future = EmploymentContract.builder()
                .id(3)
                .startDate(today.plusDays(1))
                .endDate(today.plusMonths(12))
                .build();

        assertEquals(ManagerContractAccess.Limit.NOT_IN_FORCE,
                ManagerContractAccess.assess(List.of(future), today));
    }

    @Test
    void contractInForceGrantsStoreWork() {
        EmploymentContract current = EmploymentContract.builder()
                .id(4)
                .startDate(today.minusMonths(2))
                .endDate(today.plusMonths(10))
                .build();

        assertEquals(ManagerContractAccess.Limit.NONE, ManagerContractAccess.assess(List.of(current), today));
    }

    @Test
    void renewalWindowStillGrantsStoreWork() {
        EmploymentContract current = EmploymentContract.builder()
                .id(5)
                .startDate(today.minusMonths(11))
                .endDate(today.plusDays(3))
                .build();

        assertEquals(ManagerContractAccess.Limit.NONE, ManagerContractAccess.assess(List.of(current), today));
    }

    @Test
    void renewedContractStillCoversWorkUntilItsOwnEndDate() {
        EmploymentContract source = EmploymentContract.builder()
                .id(6)
                .startDate(today.minusMonths(11))
                .endDate(today.plusDays(20))
                .build();
        EmploymentContract renewal = EmploymentContract.builder()
                .id(7)
                .previousContract(source)
                .startDate(today.plusDays(21))
                .endDate(today.plusMonths(12))
                .build();

        assertEquals(ManagerContractAccess.Limit.NONE,
                ManagerContractAccess.assess(List.of(source, renewal), today));
    }

    @Test
    void accessFollowsTheNewContractOnceItStarts() {
        EmploymentContract source = EmploymentContract.builder()
                .id(6)
                .startDate(today.minusMonths(12))
                .endDate(today.minusDays(1))
                .build();
        EmploymentContract renewal = EmploymentContract.builder()
                .id(7)
                .previousContract(source)
                .startDate(today)
                .endDate(today.plusMonths(12))
                .build();

        assertEquals(ManagerContractAccess.Limit.NONE,
                ManagerContractAccess.assess(List.of(source, renewal), today));
    }

    @Test
    void renewedSourceDoesNotGrantAccessAfterItEndsAndBeforeTheNewContractStarts() {
        EmploymentContract source = EmploymentContract.builder()
                .id(6)
                .startDate(today.minusMonths(12))
                .endDate(today.minusDays(1))
                .build();
        EmploymentContract renewal = EmploymentContract.builder()
                .id(7)
                .previousContract(source)
                .startDate(today.plusDays(5))
                .endDate(today.plusMonths(12))
                .build();

        assertEquals(ManagerContractAccess.Limit.NOT_IN_FORCE,
                ManagerContractAccess.assess(List.of(source, renewal), today));
    }

    @Test
    void expiredContractBlocksStoreWork() {
        EmploymentContract expired = EmploymentContract.builder()
                .id(8)
                .startDate(today.minusYears(2))
                .endDate(today.minusDays(1))
                .build();

        assertEquals(ManagerContractAccess.Limit.NOT_IN_FORCE,
                ManagerContractAccess.assess(List.of(expired), today));
    }

    @Test
    void earlyTerminationAndFullTerminationBlockStoreWork() {
        EmploymentContract declined = EmploymentContract.builder()
                .id(9)
                .startDate(today.minusMonths(6))
                .endDate(today.plusMonths(6))
                .renewalDeclinedAt(LocalDateTime.of(2026, 11, 1, 8, 0))
                .build();
        EmploymentContract ended = EmploymentContract.builder()
                .id(10)
                .startDate(today.minusYears(1))
                .endDate(today.plusMonths(1))
                .terminatedDate(today)
                .build();

        assertEquals(ManagerContractAccess.Limit.NONE,
                ManagerContractAccess.assess(List.of(declined), today));
        assertEquals(ManagerContractAccess.Limit.NOT_IN_FORCE,
                ManagerContractAccess.assess(List.of(ended), today));
    }
}

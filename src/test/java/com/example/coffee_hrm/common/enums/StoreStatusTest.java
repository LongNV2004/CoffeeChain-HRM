package com.example.coffee_hrm.common.enums;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoreStatusTest {

    private final LocalDate today = LocalDate.of(2026, 10, 15);

    @Test
    void openingDateSelectsPreOpeningOnlyWhenItIsStillInTheFuture() {
        assertEquals(StoreStatus.PRE_OPENING, StoreStatus.fromOpeningDate(today.plusDays(1), today));
        assertEquals(StoreStatus.ACTIVE, StoreStatus.fromOpeningDate(today, today));
        assertEquals(StoreStatus.ACTIVE, StoreStatus.fromOpeningDate(today.minusDays(1), today));
    }

    @Test
    void onlyInactiveBlocksManagerAndStaff() {
        assertFalse(StoreStatus.ACTIVE.blocksStoreStaff());
        assertFalse(StoreStatus.PRE_OPENING.blocksStoreStaff());
        assertTrue(StoreStatus.INACTIVE.blocksStoreStaff());
    }
}

package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.repository.StoreRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StoreStatusTransitionTest {

    private final StoreRepository storeRepository = mock(StoreRepository.class);
    private final StoreStatusTransition transition = new StoreStatusTransition(storeRepository);

    @Test
    void promotionUpdatesOnlyPreOpeningRowsAndCanRunTwice() throws Exception {
        Method method = StoreRepository.class.getMethod("promoteDuePreOpening", LocalDate.class);
        String sql = method.getAnnotation(Query.class).value();

        assertTrue(sql.contains("Status = 'PRE_OPENING'"));
        assertTrue(sql.contains("Status = 'ACTIVE'"));
        assertFalse(sql.contains("INACTIVE"));
        assertTrue(method.getAnnotation(Query.class).nativeQuery());

        LocalDate today = LocalDate.of(2026, 10, 15);
        when(storeRepository.promoteDuePreOpening(today)).thenReturn(2, 0);

        assertEquals(2, transition.promoteDueStores(today));
        assertEquals(0, transition.promoteDueStores(today));
        verify(storeRepository, never()).promoteDuePreOpeningById(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void singleStorePromotionReadsTheStatusCommittedInTheSameTransaction() {
        LocalDate today = LocalDate.of(2026, 10, 15);
        when(storeRepository.findStatusCodeById(4)).thenReturn("ACTIVE");

        assertEquals(com.example.coffee_hrm.common.enums.StoreStatus.ACTIVE, transition.promoteAndRead(4, today));
        verify(storeRepository).promoteDuePreOpeningById(4, today);
    }
}

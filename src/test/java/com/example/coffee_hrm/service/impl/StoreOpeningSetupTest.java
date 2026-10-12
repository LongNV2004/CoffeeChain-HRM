package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.entity.Shift;
import com.example.coffee_hrm.entity.ShiftSlotLimit;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.repository.ShiftRepository;
import com.example.coffee_hrm.repository.ShiftSlotLimitRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreOpeningSetupTest {

    @Mock
    private StoreRepository storeRepository;
    @Mock
    private ShiftRepository shiftRepository;
    @Mock
    private ShiftSlotLimitRepository shiftSlotLimitRepository;

    @InjectMocks
    private StoreOpeningSetup storeOpeningSetup;

    @Test
    void newStoreReceivesThreeShiftsAndWeeklyLimits() {
        Store store = Store.builder().id(8).storeName("Mỹ Đình").address("Mỹ Đình").build();
        when(shiftRepository.findByStore_IdOrderByStartTimeAsc(8)).thenReturn(List.of());
        when(shiftRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        storeOpeningSetup.ensureOpeningShifts(store);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Shift>> shiftsCaptor = ArgumentCaptor.forClass(List.class);
        verify(shiftRepository).saveAll(shiftsCaptor.capture());
        List<Shift> shifts = shiftsCaptor.getValue();
        assertEquals(List.of("Ca Sáng", "Ca Chiều", "Ca Tối"),
                shifts.stream().map(Shift::getShiftName).toList());
        assertEquals(LocalTime.of(7, 0), shifts.get(0).getStartTime());
        assertEquals(LocalTime.of(12, 0), shifts.get(0).getEndTime());
        assertEquals(LocalTime.of(12, 0), shifts.get(1).getStartTime());
        assertEquals(LocalTime.of(17, 0), shifts.get(1).getEndTime());
        assertEquals(LocalTime.of(17, 0), shifts.get(2).getStartTime());
        assertEquals(LocalTime.of(22, 0), shifts.get(2).getEndTime());
        assertSame(store, shifts.get(0).getStore());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ShiftSlotLimit>> limitsCaptor = ArgumentCaptor.forClass(List.class);
        verify(shiftSlotLimitRepository).saveAll(limitsCaptor.capture());
        List<ShiftSlotLimit> limits = limitsCaptor.getValue();
        assertEquals(21, limits.size());
        assertEquals(3, maxOn(limits, shifts.get(0), 1));
        assertEquals(3, maxOn(limits, shifts.get(1), 5));
        assertEquals(4, maxOn(limits, shifts.get(2), 6));
        assertEquals(4, maxOn(limits, shifts.get(2), 7));
    }

    @Test
    void storeThatAlreadyHasShiftsIsLeftUntouched() {
        Store store = Store.builder().id(1).storeName("Cầu Giấy").address("Cầu Giấy").build();
        when(shiftRepository.findByStore_IdOrderByStartTimeAsc(1))
                .thenReturn(List.of(Shift.builder().id(1).store(store).shiftName("Ca Sáng").build()));

        storeOpeningSetup.ensureOpeningShifts(store);

        verify(shiftRepository, never()).saveAll(any());
        verify(shiftSlotLimitRepository, never()).saveAll(any());
    }

    private int maxOn(List<ShiftSlotLimit> limits, Shift shift, int dayOfWeek) {
        return limits.stream()
                .filter(limit -> limit.getShift() == shift && limit.getDayOfWeek() == dayOfWeek)
                .findFirst()
                .orElseThrow()
                .getMaxEmployees();
    }
}

package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.entity.Shift;
import com.example.coffee_hrm.entity.Store;
import com.example.coffee_hrm.repository.ShiftRepository;
import com.example.coffee_hrm.repository.ShiftSlotLimitRepository;
import com.example.coffee_hrm.repository.StoreRepository;
import com.example.coffee_hrm.service.support.DefaultStoreShifts;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Gắn ca và giới hạn chỗ cho cửa hàng chưa có ca.
 * Chạy khi tạo cửa hàng và lúc khởi động, để cơ sở tạo trước bản vá cũng dùng được lịch.
 */
@Service
@RequiredArgsConstructor
public class StoreOpeningSetup implements ApplicationRunner {

    private final StoreRepository storeRepository;
    private final ShiftRepository shiftRepository;
    private final ShiftSlotLimitRepository shiftSlotLimitRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (Store store : storeRepository.findAll()) {
            ensureOpeningShifts(store);
        }
    }

    @Transactional
    public void ensureOpeningShifts(Store store) {
        if (store == null || store.getId() == null) {
            return;
        }
        if (!shiftRepository.findByStore_IdOrderByStartTimeAsc(store.getId()).isEmpty()) {
            return;
        }
        List<Shift> shifts = shiftRepository.saveAll(DefaultStoreShifts.shiftsFor(store));
        shiftSlotLimitRepository.saveAll(DefaultStoreShifts.limitsFor(shifts));
    }
}

package com.example.coffee_hrm.service.impl;

import com.example.coffee_hrm.common.enums.StoreStatus;
import com.example.coffee_hrm.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Chuyển PRE_OPENING sang ACTIVE khi đến ngày khai trương.
 * Câu lệnh chỉ cập nhật PRE_OPENING nên chạy lặp lại không đụng tới INACTIVE và không tạo dữ liệu mới.
 */
@Service
@RequiredArgsConstructor
public class StoreStatusTransition {

    private final StoreRepository storeRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int promoteDueStores(LocalDate today) {
        if (today == null) {
            return 0;
        }
        return storeRepository.promoteDuePreOpening(today);
    }

    /**
     * Cập nhật rồi đọc trạng thái trong cùng một giao dịch mới, tránh giữ trạng thái cũ của session đang mở.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StoreStatus promoteAndRead(Integer storeId, LocalDate today) {
        if (storeId == null || today == null) {
            return null;
        }
        storeRepository.promoteDuePreOpeningById(storeId, today);
        String code = storeRepository.findStatusCodeById(storeId);
        if (code == null || code.isBlank()) {
            return null;
        }
        try {
            return StoreStatus.valueOf(code.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}

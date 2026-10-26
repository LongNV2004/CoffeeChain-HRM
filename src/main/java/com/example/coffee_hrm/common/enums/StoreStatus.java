package com.example.coffee_hrm.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;

/**
 * Trạng thái vận hành cửa hàng.
 * INACTIVE là quyết định chủ động của Admin và không được suy ra từ ngày khai trương.
 */
@Getter
@RequiredArgsConstructor
public enum StoreStatus {

    ACTIVE("Hoạt động"),
    PRE_OPENING("Chuẩn bị khai trương"),
    INACTIVE("Ngừng hoạt động");

    private final String label;

    public boolean blocksStoreStaff() {
        return this == INACTIVE;
    }

    /**
     * Trạng thái tự động khi tạo mới hoặc khi Admin kích hoạt lại cửa hàng đang ngừng hoạt động.
     * Ngày khai trương ở tương lai so với hôm nay tại Việt Nam thì là PRE_OPENING, còn lại là ACTIVE.
     */
    public static StoreStatus fromOpeningDate(LocalDate openingDate, LocalDate today) {
        if (openingDate == null || today == null) {
            throw new IllegalArgumentException("Ngày khai trương không hợp lệ");
        }
        return openingDate.isAfter(today) ? PRE_OPENING : ACTIVE;
    }
}

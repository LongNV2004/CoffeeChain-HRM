package com.example.coffee_hrm.service;

import com.example.coffee_hrm.common.time.VietnamTime;
import com.example.coffee_hrm.service.impl.StoreStatusTransition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StoreOpeningScheduler {

    private final StoreStatusTransition storeStatusTransition;

    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Ho_Chi_Minh")
    public void promoteStoresOpeningToday() {
        int updated = storeStatusTransition.promoteDueStores(VietnamTime.today());
        if (updated > 0) {
            log.info("Đã chuyển {} cửa hàng từ chuẩn bị khai trương sang hoạt động.", updated);
        }
    }
}

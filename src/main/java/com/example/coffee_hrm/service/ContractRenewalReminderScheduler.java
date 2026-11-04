package com.example.coffee_hrm.service;

import com.example.coffee_hrm.common.time.VietnamTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContractRenewalReminderScheduler {

    private final ContractRenewalReminder contractRenewalReminder;

    @Scheduled(cron = "0 15 0 * * *", zone = "Asia/Ho_Chi_Minh")
    public void remindAfterMidnight() {
        int sent = contractRenewalReminder.remindDue(VietnamTime.today());
        if (sent > 0) {
            log.info("Đã nhắc {} Manager vì hợp đồng nhân viên chuyển sang Chờ gia hạn.", sent);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void catchUpAfterDowntime() {
        try {
            remindAfterMidnight();
        } catch (RuntimeException ex) {
            log.warn("Chưa chạy bù nhắc hợp đồng chờ gia hạn: {}", ex.getMessage());
        }
    }
}

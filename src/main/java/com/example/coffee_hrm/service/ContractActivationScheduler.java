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
public class ContractActivationScheduler {

    private final ContractRenewalService contractRenewalService;
    private final PendingEmployeeActivation pendingEmployeeActivation;

    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Ho_Chi_Minh")
    public void activateAfterMidnight() {
        var today = VietnamTime.today();
        int activated = contractRenewalService.activateDue(today);
        int expired = contractRenewalService.recordExpiredRenewals(today);
        int locked = contractRenewalService.lockDeclinedAccounts(today);
        int promoted = pendingEmployeeActivation.promoteReady(today);
        if (activated > 0 || expired > 0 || locked > 0 || promoted > 0) {
            log.info("Kích hoạt hợp đồng: {} sang Đang hiệu lực, {} sang Đã hết hạn, {} tài khoản bị khóa, {} hồ sơ được đồng bộ theo hợp đồng.",
                    activated, expired, locked, promoted);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void catchUpAfterDowntime() {
        try {
            activateAfterMidnight();
        } catch (RuntimeException ex) {
            log.error("Chưa chạy bù được việc kích hoạt hợp đồng chờ hiệu lực.", ex);
        }
    }
}

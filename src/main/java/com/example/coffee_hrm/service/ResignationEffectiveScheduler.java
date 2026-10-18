package com.example.coffee_hrm.service;

import com.example.coffee_hrm.common.time.VietnamTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResignationEffectiveScheduler {

    private final ResignationProposalService resignationProposalService;

    @Scheduled(cron = "0 10 0 * * *", zone = "Asia/Ho_Chi_Minh")
    public void applyDueResignations() {
        try {
            int updated = resignationProposalService.applyDueTerminations(VietnamTime.today());
            if (updated > 0) {
                log.info("Đã cập nhật trạng thái nghỉ việc cho {} nhân sự đến ngày hiệu lực.", updated);
            }
        } catch (RuntimeException ex) {
            log.error("Không cập nhật được các nghỉ việc đến ngày hiệu lực", ex);
        }
    }
}

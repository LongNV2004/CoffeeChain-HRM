package com.example.coffee_hrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Định kỳ chuyển các lời mời hỗ trợ ca gấp quá hạn phản hồi sang "Hết hạn" (tính như từ chối).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShiftCoverInvitationScheduler {

    private final ShiftCoverInvitationService shiftCoverInvitationService;

    @Scheduled(fixedDelay = 5 * 60 * 1000L, initialDelay = 60 * 1000L)
    public void expireOverdueInvitations() {
        int expired = shiftCoverInvitationService.expireOverdue();
        if (expired > 0) {
            log.info("Đã chuyển {} lời mời hỗ trợ ca sang trạng thái hết hạn.", expired);
        }
    }
}


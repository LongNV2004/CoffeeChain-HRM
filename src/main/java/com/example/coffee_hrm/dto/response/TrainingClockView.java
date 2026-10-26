package com.example.coffee_hrm.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class TrainingClockView {

    private final String employeeName;
    private final String storeName;
    private final List<Session> sessions;

    @Getter
    @Builder
    public static class Session {
        private final Integer classId;
        private final String className;
        private final String typeLabel;
        private final String dateLabel;
        private final String timeLabel;
        private final String statusKey;
        private final String statusLabel;
        private final String checkInLabel;
        private final String checkOutLabel;
        private final boolean canCheckIn;
        private final String checkInNotice;
        private final boolean canCheckOut;
        private final String checkOutNotice;
        private final String checkoutHint;
    }
}

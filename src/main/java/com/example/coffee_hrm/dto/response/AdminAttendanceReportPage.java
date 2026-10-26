package com.example.coffee_hrm.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class AdminAttendanceReportPage {

    private final int year;
    private final int month;
    private final String periodLabel;
    private final Integer filterStoreId;
    private final String filterStatus;
    private final int totalStores;
    private final int submittedStores;
    private final int unsentStores;
    private final int staleStores;
    private final List<StoreOption> stores;
    private final List<Row> rows;

    @Getter
    @Builder
    public static class StoreOption {
        private final int storeId;
        private final String storeName;
    }

    @Getter
    @Builder
    public static class Row {
        private final int order;
        private final Integer reportId;
        private final int storeId;
        private final String storeName;
        private final String managerName;
        private final String periodLabel;
        private final LocalDate periodStart;
        private final LocalDate periodEnd;
        private final String statusCode;
        private final String statusLabel;
        private final String statusCss;
        private final String reviewNote;
        private final boolean submitted;
        private final boolean stale;
        private final String senderName;
        private final LocalDateTime submittedAt;
        private final Integer versionNumber;
    }
}

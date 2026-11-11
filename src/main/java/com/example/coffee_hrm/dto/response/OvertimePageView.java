package com.example.coffee_hrm.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

/** Trang Đăng kí tăng ca: Nhân viên gửi/hủy đơn, Quản lý duyệt/từ chối. */
@Getter
@Builder
public class OvertimePageView {

    private final String storeName;
    private final int normalDailyLimit;
    private final int overtimeDailyLimit;
    private final LocalDate minDate;
    private final LocalDate maxDate;
    @Builder.Default
    private final List<ShiftOption> shifts = List.of();
    @Builder.Default
    private final List<Item> pending = List.of();
    @Builder.Default
    private final List<Item> history = List.of();
    private final int pendingCount;
    private final int approvedCount;

    @Getter
    @Builder
    public static class ShiftOption {
        private final Integer shiftId;
        private final String label;
        private final String startTime;
    }

    @Getter
    @Builder
    public static class Item {
        private final Integer id;
        private final Integer employeeId;
        private final String employeeName;
        private final String employeeCode;
        private final String dateLabel;
        private final String shiftName;
        private final String timeRange;
        private final String reason;
        private final String statusKey;
        private final String statusLabel;
        private final String createdLabel;
        private final String resolvedLabel;
        private final String resolverName;
        private final String rejectReason;
        /** Số ca của nhân viên trong ngày đó (gồm ca tăng ca nếu đã duyệt). */
        private final int dayShiftCount;
        private final boolean canCancel;
        private final boolean canReview;
    }
}

package com.example.coffee_hrm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class OvertimePageView {

    private final String storeName;
    private final String employeeName;
    private final LocalDate weekStart;
    private final LocalDate weekEnd;
    private final LocalDate previousWeek;
    private final LocalDate nextWeek;
    private final LocalDate currentWeek;
    private final String formattedWeekRange;

    /** Định mức số ca tối đa/tuần theo hợp đồng của nhân viên (dành cho Staff). */
    private final Integer contractWeeklyQuota;
    private final String contractBlockMessage;

    /** Số ca định kỳ / đã phân công trong tuần đang xem (không tính ca từ đơn tăng ca). */
    private final int regularShiftsInWeek;
    private final int approvedOvertimeInWeek;
    private final int pendingOvertimeInWeek;
    private final boolean quotaReached;
    private final boolean canSubmitOvertimeInWeek;
    private final String quotaStatusMessage;

    /** Lưới ca trong tuần để nhân viên chọn nhanh hoặc quản lý theo dõi. */
    private final List<DayHeader> days;
    private final List<ShiftRow> shiftRows;

    /** Danh sách đơn xin tăng ca. */
    private final List<RequestItem> requests;

    /** Thống kê tổng hợp đơn xin tăng ca. */
    private final SummaryTotals totals;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class DayHeader {
        private final int dayOfWeek;
        private final String dayName;
        private final LocalDate date;
        private final String formattedDate;
        private final boolean today;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class ShiftRow {
        private final Integer shiftId;
        private final String shiftName;
        private final String formattedTime;
        private final List<SlotCell> cells;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class SlotCell {
        private final Integer shiftId;
        private final String shiftName;
        private final String formattedTime;
        private final int dayOfWeek;
        private final String dayName;
        private final LocalDate workDate;
        private final String formattedDate;
        private final Integer maxEmployees;
        private final int occupiedCount;
        private final String capacityLabel;
        private final String stateKey;
        private final String stateLabel;
        private final String stateHint;
        private final boolean selectable;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class RequestItem {
        private final Integer id;
        private final Integer employeeId;
        private final String employeeName;
        private final String employeePhone;
        private final Integer shiftId;
        private final String shiftName;
        private final String shiftTime;
        private final LocalDate workDate;
        private final String formattedWorkDate;
        private final String dayOfWeekName;
        private final Integer contractWeeklyQuota;
        private final int employeeRegularShiftsInWeek;
        private final int employeeApprovedOvertimeInWeek;
        private final Integer slotMaxEmployees;
        private final int slotOccupiedCount;
        private final String reason;
        private final String statusKey;
        private final String statusLabel;
        private final boolean pending;
        private final boolean canCancel;
        private final String reviewedByName;
        private final LocalDateTime reviewedAt;
        private final String formattedReviewedAt;
        private final String reviewNote;
        private final LocalDateTime createdAt;
        private final String formattedCreatedAt;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class SummaryTotals {
        private final int total;
        private final int pending;
        private final int approved;
        private final int rejected;
    }
}


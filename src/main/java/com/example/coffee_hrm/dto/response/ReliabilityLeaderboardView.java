package com.example.coffee_hrm.dto.response;

import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReliabilityLeaderboardView {

    private Integer storeId;
    private String storeName;
    private String storeAddress;

    private String selectedMonth;
    private String periodLabel;
    private String prevMonthParam;
    private String nextMonthParam;

    private int totalStaffCount;
    private String averageReliabilityScore;
    private int totalRescueShiftsMonth;
    private int totalCompletedSwapsMonth;
    private int totalLateCancelsMonth;
    private int openRescueShiftsCount;

    private List<RankEntry> podiumEntries;
    private List<RankEntry> leaderboardEntries;

    // Dành cho giao diện Nhân viên (Staff)
    private RankEntry mySummary;
    private String nextTierLabel;
    private int pointsToNextTier;
    private int progressPercent;
    private List<ScoreLogItem> myRecentLogs;

    // Danh sách ca đang cần "Cứu bồ" tại cửa hàng
    private List<RescueShiftItem> openRescueShifts;

    // Danh sách ca đổi đã duyệt chờ Quản lý xác nhận hoàn thành tốt (+2đ)
    private List<CompletedSwapCandidate> completedSwapCandidates;

    // Nhật ký biến động điểm uy tín gần đây tại cửa hàng
    private List<ScoreLogItem> recentLogs;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RankEntry {
        private int rank;
        private Integer employeeId;
        private String fullName;
        private String email;
        private String phone;
        private boolean certified;
        private boolean currentStaff;

        private int reliabilityScore;
        private int monthlyNetDelta;
        private int monthlyPositivePoints;
        private int monthlyNegativePoints;

        private int rescueShiftsCount;
        private int completedSwapsCount;
        private int lateCancelsCount;
        private int emergencyDeclinesCount;

        private String tierCode;
        private String tierLabel;
        private String tierBadgeClass;

        private boolean kpiRewardEligible;
        private boolean priorityScheduleEligible;
        private String rewardBadgeLabel;

        public String getFormattedMonthlyDelta() {
            if (monthlyNetDelta > 0) {
                return "+" + monthlyNetDelta + " đ";
            }
            return monthlyNetDelta + " đ";
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RescueShiftItem {
        private Integer requestId;
        private Integer assignmentId;
        private Integer requesterId;
        private String requesterName;
        private String shiftName;
        private LocalDate workDate;
        private String formattedDate;
        private String timeRange;
        private String reason;
        private boolean urgentWithin24h;
        private long hoursUntilStart;
        private int rescueBonusPoints;
        private boolean canVolunteer;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CompletedSwapCandidate {
        private Integer requestId;
        private Integer assignmentId;
        private Integer replacementEmployeeId;
        private String replacementEmployeeName;
        private String originalEmployeeName;
        private String shiftName;
        private LocalDate workDate;
        private String formattedDate;
        private String timeRange;
        private String changeTypeLabel;
        private boolean alreadyRewarded;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ScoreLogItem {
        private Integer logId;
        private Integer employeeId;
        private String employeeName;
        private String actionType;
        private String actionLabel;
        private int pointsDelta;
        private int scoreAfter;
        private String reason;
        private String createdAtFormatted;
        private boolean positive;
        private boolean warningOnly;

        public String getFormattedDelta() {
            if (pointsDelta > 0) {
                return "+" + pointsDelta + " đ";
            }
            if (pointsDelta == 0) {
                return "0 đ (Nhắc nhở)";
            }
            return pointsDelta + " đ";
        }
    }
}

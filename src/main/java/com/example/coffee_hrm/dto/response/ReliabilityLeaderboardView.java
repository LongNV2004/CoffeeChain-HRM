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
    /** true khi đang xem tháng hiện tại (mới cho phép điều chỉnh điểm thủ công). */
    private boolean currentMonth;
    /** Tóm tắt điểm chuyên cần các tháng gần nhất, mới nhất trước. */
    private List<MonthHistoryItem> monthlyHistory;

    // Chốt danh hiệu Nhân viên xuất sắc tháng
    private boolean monthClosed;
    private boolean canCloseMonth;
    private List<RankEntry> awardProposals;
    private List<AwardItem> awards;
    private int minShiftsForAward;
    private int minScoreForAward;
    private int manualAdjustCap;

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

    // Danh sách ca đang cần "Hỗ trợ ca gấp" tại cửa hàng
    private List<RescueShiftItem> openRescueShifts;

    // Danh sách ca đổi đã duyệt chờ Quản lý xác nhận hoàn thành tốt (+2đ)
    private List<CompletedSwapCandidate> completedSwapCandidates;

    // Nhật ký biến động điểm chuyên cần gần đây tại cửa hàng
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
        private int punctualityViolationsCount;

        // Điều kiện xét danh hiệu tháng
        private int shiftsWorked;
        private int absencesCount;
        private int abandonedCount;
        private boolean awardEligible;
        private String ineligibleReason;
        /** Hạng danh hiệu đã chốt (1–3), null nếu tháng chưa chốt hoặc không đạt. */
        private Integer awardRank;
        private String awardTitle;

        // Điều chỉnh thủ công đã dùng trong tháng (hạn mức ±15)
        private int manualBonusUsed;
        private int manualPenaltyUsed;

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
        private boolean monthlyCapped;
        private int rewardsCountThisMonth;
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

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MonthHistoryItem {
        private String monthParam;
        private String label;
        private boolean selected;
        private boolean hasActivity;
        private String topEmployeeName;
        private Integer topScore;
        // Chỉ có giá trị ở giao diện Nhân viên
        private Integer myScore;
        private Integer myRank;
        private int totalStaff;
        /** Người đạt "Nhân viên xuất sắc tháng" đã được chốt (null nếu chưa chốt). */
        private String awardWinnerName;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AwardItem {
        private int rank;
        private String title;
        private Integer employeeId;
        private String employeeName;
        private int monthScore;
        private String overrideReason;
        private String decidedAt;
    }
}

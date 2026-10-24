package com.example.coffee_hrm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WeeklyAvailabilityView {

    private Integer storeId;
    private String storeName;
    private LocalDate weekStartDate;
    private LocalDate weekEndDate;
    private int selectedCount;
    private boolean patternMode;
    private List<DayHeader> days;
    private List<ShiftRow> shiftRows;
    private List<RegistrationGroup> registrations;
    private List<String> shortageAlerts;

    public String getFormattedWeekRange() {
        if (patternMode) {
            return "Lặp theo thứ trong tuần, trong thời hạn của từng đăng ký";
        }
        if (weekStartDate == null || weekEndDate == null) {
            return "";
        }
        return String.format("Tuần kế tiếp: %02d/%02d/%d – %02d/%02d/%d",
                weekStartDate.getDayOfMonth(), weekStartDate.getMonthValue(), weekStartDate.getYear(),
                weekEndDate.getDayOfMonth(), weekEndDate.getMonthValue(), weekEndDate.getYear());
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DayHeader {
        private LocalDate date;
        private String dayOfWeekName;
        private String formattedDate;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShiftRow {
        private Integer shiftId;
        private String shiftName;
        private LocalTime startTime;
        private LocalTime endTime;
        private String formattedTime;
        private List<DayCell> dayCells;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DayCell {
        private LocalDate date;
        private Integer shiftId;
        private String slotKey;
        private int dayOfWeek;
        private String dayOfWeekName;
        private boolean selected;
        private List<StaffProposal> proposals;
        private Integer maxEmployees;
        private int approvedCount;
        private int pendingCount;
        private int remaining;
        private String remainingText;
        private boolean configured;
        private boolean overCapacity;
        private boolean registrationClosed;
        private String slotStatusKey;
        private String slotStatusLabel;
        private String registrationHint;
        private int registeredCount;
        private int positionsFilled;
        private int positionsTracked;
        private int shortfall;
        private boolean positionsConfigured;
        private boolean awaitingApproval;
        private int staffingProgress;
        private String staffingStatusKey;
        private String staffingStatusLabel;
        private String shortageSummary;
        @Builder.Default
        private List<PositionLine> positionLines = List.of();
        @Builder.Default
        private Map<Integer, SkillActivity> skillActivity = Map.of();

        public Integer requiredCountOf(Integer skillId) {
            if (!positionsConfigured || positionLines == null || skillId == null) {
                return null;
            }
            for (PositionLine line : positionLines) {
                if (skillId.equals(line.getSkillId())) {
                    return line.getRequired();
                }
            }
            return 0;
        }

        public SkillActivity activityOf(Integer skillId) {
            if (skillActivity == null || skillId == null) {
                return SkillActivity.EMPTY;
            }
            return skillActivity.getOrDefault(skillId, SkillActivity.EMPTY);
        }
    }

    @Getter
    @AllArgsConstructor
    public static class SkillActivity {
        public static final SkillActivity EMPTY = new SkillActivity(0, 0, 0);

        private final int registered;
        private final int pending;
        private final int approved;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PositionLine {
        private Integer skillId;
        private String skillName;
        private int required;
        private int registered;
        private int pending;
        private int approved;
        private int shortfall;
        private String statusKey;
        private String statusLabel;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StaffProposal {
        private Integer availabilityId;
        private Integer employeeId;
        private String employeeName;
        private String statusKey;
        private String statusLabel;
        private boolean pending;
        private String detail;
        private String certificationLabel;
        private String positionName;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RegistrationGroup {
        private String registrationKey;
        private Integer availabilityId;
        private Integer employeeId;
        private String employeeName;
        private String certificationLabel;
        private String durationLabel;
        private String formattedPeriod;
        private String slotsByDay;
        private String projectionSummary;
        private String statusKey;
        private String statusLabel;
        private boolean pending;
        private String positionName;
        private boolean activeInWeek;
        /** Các ô ca của đơn, dạng {@code ;thứ|tên ca;}. */
        private String slotKeys;
    }
}

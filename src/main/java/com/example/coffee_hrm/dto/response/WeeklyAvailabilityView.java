package com.example.coffee_hrm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

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
    private List<DayHeader> days;
    private List<ShiftRow> shiftRows;

    public String getFormattedWeekRange() {
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
        private boolean selected;
        private List<StaffProposal> proposals;
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
    }
}

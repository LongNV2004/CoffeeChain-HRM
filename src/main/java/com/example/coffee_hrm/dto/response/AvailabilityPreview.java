package com.example.coffee_hrm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AvailabilityPreview {

    private String durationLabel;
    private LocalDate validFrom;
    private LocalDate validTo;
    private String formattedPeriod;
    private int occurrenceCount;
    private String replacementNote;
    private List<Week> weeks;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Week {
        private LocalDate weekStart;
        private LocalDate weekEnd;
        private String formattedRange;
        private boolean partialStart;
        private boolean partialEnd;
        private boolean crossesMonth;
        private List<String> includedSlots;
        private String note;
    }
}

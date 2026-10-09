package com.example.coffee_hrm.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.util.Optional;

@Getter
@RequiredArgsConstructor
public enum AvailabilityDuration {

    WEEK_1("W1", "1 tuần", 0, 1),
    MONTH_1("M1", "1 tháng", 1, 0),
    MONTH_2("M2", "2 tháng", 2, 0),
    MONTH_6("M6", "6 tháng", 6, 0),
    YEAR_1("Y1", "1 năm", 12, 0);

    private final String code;
    private final String label;
    private final int months;
    private final int weeks;

    public LocalDate endInclusive(LocalDate start) {
        if (weeks > 0) {
            return start.plusWeeks(weeks).minusDays(1);
        }
        return start.plusMonths(months).minusDays(1);
    }

    public static Optional<AvailabilityDuration> fromCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String normalized = code.trim();
        for (AvailabilityDuration duration : values()) {
            if (duration.code.equalsIgnoreCase(normalized)) {
                return Optional.of(duration);
            }
        }
        return Optional.empty();
    }
}

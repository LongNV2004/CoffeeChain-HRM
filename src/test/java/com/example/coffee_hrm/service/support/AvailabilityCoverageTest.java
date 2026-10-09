package com.example.coffee_hrm.service.support;

import com.example.coffee_hrm.common.enums.ApprovalStatus;
import com.example.coffee_hrm.dto.response.AvailabilityPreview;
import com.example.coffee_hrm.entity.WorkAvailability;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvailabilityCoverageTest {

    private static final LocalDate START = LocalDate.of(2026, 10, 12);
    private static final LocalDate END = LocalDate.of(2026, 11, 11);

    @Test
    void appliesOnlyOnMatchingWeekdayInsidePeriod() {
        WorkAvailability monday = pattern(1);

        assertTrue(AvailabilityCoverage.appliesOn(monday, LocalDate.of(2026, 10, 12)));
        assertTrue(AvailabilityCoverage.appliesOn(monday, LocalDate.of(2026, 11, 9)));
        assertFalse(AvailabilityCoverage.appliesOn(monday, LocalDate.of(2026, 10, 5)));
        assertFalse(AvailabilityCoverage.appliesOn(monday, LocalDate.of(2026, 11, 16)));
        assertFalse(AvailabilityCoverage.appliesOn(monday, LocalDate.of(2026, 10, 13)));
    }

    @Test
    void lastPartialWeekAndCrossMonthWeekKeepOnlyInRangeDates() {
        List<AvailabilityCoverage.SlotChoice> slots = List.of(
                new AvailabilityCoverage.SlotChoice(1, "Ca sáng"),
                new AvailabilityCoverage.SlotChoice(7, "Ca sáng"));
        AvailabilityPreview preview = AvailabilityCoverage.buildPreview("1 tháng", START, END, slots, null);

        AvailabilityPreview.Week crossing = preview.getWeeks().stream()
                .filter(week -> week.getWeekStart().equals(LocalDate.of(2026, 10, 26)))
                .findFirst()
                .orElseThrow();
        assertTrue(crossing.isCrossesMonth());
        assertEquals(2, crossing.getIncludedSlots().size());

        AvailabilityPreview.Week last = preview.getWeeks().getLast();
        assertEquals(LocalDate.of(2026, 11, 9), last.getWeekStart());
        assertTrue(last.isPartialEnd());
        assertEquals(1, last.getIncludedSlots().size());
        assertTrue(last.getIncludedSlots().getFirst().startsWith("Thứ 2"));
    }

    @Test
    void legacyDatedRowStillMatchesItsOwnDate() {
        WorkAvailability legacy = WorkAvailability.builder()
                .workDate(LocalDate.of(2026, 10, 14))
                .status(ApprovalStatus.APPROVED)
                .build();

        assertTrue(AvailabilityCoverage.appliesOn(legacy, LocalDate.of(2026, 10, 14)));
        assertFalse(AvailabilityCoverage.appliesOn(legacy, LocalDate.of(2026, 10, 21)));
    }

    private WorkAvailability pattern(int dayOfWeek) {
        return WorkAvailability.builder()
                .dayOfWeek(dayOfWeek)
                .validFrom(START)
                .validTo(END)
                .status(ApprovalStatus.APPROVED)
                .build();
    }
}

package com.example.coffee_hrm.service.support;

import com.example.coffee_hrm.dto.response.AvailabilityPreview;
import com.example.coffee_hrm.entity.WorkAvailability;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * Quy tắc dùng chung khi bung lịch định kỳ ra từng ngày:
 * đúng thứ trong tuần và nằm trong [validFrom, validTo].
 */
public final class AvailabilityCoverage {

    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String[] VI_DAYS = {"Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7", "Chủ nhật"};

    private AvailabilityCoverage() {
    }

    public static boolean appliesOn(WorkAvailability availability, LocalDate date) {
        if (availability == null || date == null) {
            return false;
        }
        if (availability.getDayOfWeek() == null) {
            return date.equals(availability.getWorkDate());
        }
        LocalDate from = availability.getValidFrom();
        LocalDate to = availability.getValidTo();
        if (from == null || to == null || date.isBefore(from) || date.isAfter(to)) {
            return false;
        }
        return date.getDayOfWeek().getValue() == availability.getDayOfWeek();
    }

    public static boolean rangesOverlap(LocalDate aFrom, LocalDate aTo, LocalDate bFrom, LocalDate bTo) {
        if (aFrom == null || aTo == null || bFrom == null || bTo == null) {
            return false;
        }
        return !aFrom.isAfter(bTo) && !bFrom.isAfter(aTo);
    }

    public static List<LocalDate> occurrences(int dayOfWeek, LocalDate from, LocalDate to) {
        List<LocalDate> dates = new ArrayList<>();
        if (from == null || to == null || dayOfWeek < 1 || dayOfWeek > 7 || to.isBefore(from)) {
            return dates;
        }
        LocalDate cursor = from;
        while (cursor.getDayOfWeek().getValue() != dayOfWeek && !cursor.isAfter(to)) {
            cursor = cursor.plusDays(1);
        }
        while (!cursor.isAfter(to)) {
            dates.add(cursor);
            cursor = cursor.plusWeeks(1);
        }
        return dates;
    }

    public static String dayName(int dayOfWeek) {
        if (dayOfWeek < 1 || dayOfWeek > 7) {
            return "";
        }
        return VI_DAYS[dayOfWeek - 1];
    }

    public static AvailabilityPreview buildPreview(String durationLabel,
                                                   LocalDate validFrom,
                                                   LocalDate validTo,
                                                   List<SlotChoice> slots,
                                                   String replacementNote) {
        List<AvailabilityPreview.Week> weeks = new ArrayList<>();
        int occurrenceCount = 0;
        if (validFrom != null && validTo != null && !validTo.isBefore(validFrom)) {
            LocalDate weekStart = validFrom.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate lastWeek = validTo.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            for (LocalDate start = weekStart; !start.isAfter(lastWeek); start = start.plusWeeks(1)) {
                LocalDate end = start.plusDays(6);
                List<String> included = new ArrayList<>();
                for (int i = 0; i < 7; i++) {
                    LocalDate date = start.plusDays(i);
                    if (date.isBefore(validFrom) || date.isAfter(validTo)) {
                        continue;
                    }
                    int day = date.getDayOfWeek().getValue();
                    for (SlotChoice slot : slots) {
                        if (slot.dayOfWeek() == day) {
                            included.add(dayName(day) + " " + date.format(DAY_MONTH) + " · " + slot.label());
                            occurrenceCount++;
                        }
                    }
                }
                boolean partialStart = start.isBefore(validFrom);
                boolean partialEnd = end.isAfter(validTo);
                boolean crossesMonth = start.getMonthValue() != end.getMonthValue()
                        || start.getYear() != end.getYear();
                weeks.add(AvailabilityPreview.Week.builder()
                        .weekStart(start)
                        .weekEnd(end)
                        .formattedRange(start.format(FULL) + " – " + end.format(FULL))
                        .partialStart(partialStart)
                        .partialEnd(partialEnd)
                        .crossesMonth(crossesMonth)
                        .includedSlots(included)
                        .note(weekNote(partialStart, partialEnd, crossesMonth, included.isEmpty()))
                        .build());
            }
        }
        return AvailabilityPreview.builder()
                .durationLabel(durationLabel)
                .validFrom(validFrom)
                .validTo(validTo)
                .formattedPeriod(formatPeriod(validFrom, validTo))
                .occurrenceCount(occurrenceCount)
                .replacementNote(replacementNote)
                .weeks(weeks)
                .build();
    }

    public static String projectionSummary(LocalDate validFrom, LocalDate validTo, List<Integer> daysOfWeek) {
        if (validFrom == null || validTo == null) {
            return "";
        }
        List<SlotChoice> slots = new ArrayList<>();
        if (daysOfWeek != null) {
            for (Integer day : daysOfWeek) {
                if (day != null) {
                    slots.add(new SlotChoice(day, dayName(day)));
                }
            }
        }
        AvailabilityPreview preview = buildPreview("", validFrom, validTo, slots, null);
        if (preview.getWeeks() == null || preview.getWeeks().isEmpty()) {
            return "Không có ngày nào trong thời hạn.";
        }
        AvailabilityPreview.Week first = preview.getWeeks().getFirst();
        AvailabilityPreview.Week last = preview.getWeeks().getLast();
        boolean crosses = preview.getWeeks().stream().anyMatch(AvailabilityPreview.Week::isCrossesMonth);
        String summary = preview.getOccurrenceCount() + " ngày, từ " + formatPeriod(validFrom, validTo)
                + ". Tuần đầu " + first.getFormattedRange()
                + ". Tuần cuối " + last.getFormattedRange();
        if (last.isPartialEnd() || first.isPartialStart()) {
            summary += " (tuần đầu/cuối chỉ lấy ngày còn trong thời hạn)";
        }
        if (crosses) {
            summary += ". Có tuần giao giữa hai tháng";
        }
        return summary;
    }

    public static String formatPeriod(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            return "";
        }
        return from.format(FULL) + " – " + to.format(FULL);
    }

    private static String weekNote(boolean partialStart, boolean partialEnd, boolean crossesMonth, boolean empty) {
        List<String> notes = new ArrayList<>();
        if (partialStart) {
            notes.add("Tuần đầu: chỉ áp dụng từ ngày bắt đầu");
        }
        if (partialEnd) {
            notes.add("Tuần cuối: chỉ áp dụng đến ngày kết thúc");
        }
        if (crossesMonth) {
            notes.add("Tuần giao giữa hai tháng");
        }
        if (empty) {
            notes.add("Không có slot vì các thứ đã chọn nằm ngoài thời hạn của tuần này");
        }
        return String.join(". ", notes);
    }

    public record SlotChoice(int dayOfWeek, String label) {
    }
}

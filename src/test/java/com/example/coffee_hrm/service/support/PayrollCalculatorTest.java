package com.example.coffee_hrm.service.support;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayrollCalculatorTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 6);
    private static final LocalTime START = LocalTime.of(8, 0);
    private static final LocalTime END = LocalTime.of(16, 0);
    private static final BigDecimal RATE = new BigDecimal("30000");

    @Test
    void onTimePaysEveryScheduledHour() {
        PayrollCalculator.Outcome outcome = pay(0, 0);
        assertEquals(8, outcome.scheduledHours());
        assertEquals(8, outcome.payableHours());
        assertEquals(new BigDecimal("240000"), outcome.shiftSalary());
        assertTrue(outcome.payable());
        assertFalse(outcome.needsReview());
    }

    @Test
    void lateExactlyTwentyMinutesKeepsTheFirstHour() {
        PayrollCalculator.Outcome outcome = pay(20, 0);
        assertEquals(8, outcome.payableHours());
        assertEquals(new BigDecimal("240000"), outcome.shiftSalary());
        assertFalse(outcome.lateDeducted());
    }

    @Test
    void lateTwentyOneMinutesDropsOnlyTheFirstHour() {
        PayrollCalculator.Outcome outcome = pay(21, 0);
        assertEquals(7, outcome.payableHours());
        assertEquals(new BigDecimal("210000"), outcome.shiftSalary());
        assertTrue(outcome.lateDeducted());
    }

    @Test
    void lateNinetyMinutesStillDropsOnlyOneHour() {
        PayrollCalculator.Outcome outcome = pay(90, 0);
        assertEquals(7, outcome.payableHours());
        assertEquals(new BigDecimal("210000"), outcome.shiftSalary());
    }

    @Test
    void earlyExactlyTwentyMinutesKeepsTheLastHour() {
        PayrollCalculator.Outcome outcome = pay(0, 20);
        assertEquals(8, outcome.payableHours());
        assertEquals(new BigDecimal("240000"), outcome.shiftSalary());
        assertFalse(outcome.earlyDeducted());
    }

    @Test
    void earlyTwentyOneMinutesDropsOnlyTheLastHour() {
        PayrollCalculator.Outcome outcome = pay(0, 21);
        assertEquals(7, outcome.payableHours());
        assertEquals(new BigDecimal("210000"), outcome.shiftSalary());
        assertTrue(outcome.earlyDeducted());
    }

    @Test
    void earlyEightyMinutesStillDropsOnlyOneHour() {
        PayrollCalculator.Outcome outcome = pay(0, 80);
        assertEquals(7, outcome.payableHours());
        assertEquals(new BigDecimal("210000"), outcome.shiftSalary());
    }

    @Test
    void lateAndEarlyAreDeductedIndependently() {
        PayrollCalculator.Outcome outcome = pay(25, 25);
        assertEquals(6, outcome.payableHours());
        assertEquals(new BigDecimal("180000"), outcome.shiftSalary());
        assertTrue(outcome.lateDeducted());
        assertTrue(outcome.earlyDeducted());
    }

    @Test
    void missingCheckInIsNotPaid() {
        PayrollCalculator.Outcome outcome = PayrollCalculator.evaluate(
                DAY, START, END, false, false, true, 0, 0, RATE);
        assertEquals(0, outcome.payableHours());
        assertEquals("Thiếu check-in", outcome.unpaidReason());
        assertTrue(outcome.needsReview());
    }

    @Test
    void missingCheckOutIsNotPaid() {
        PayrollCalculator.Outcome outcome = PayrollCalculator.evaluate(
                DAY, START, END, false, true, false, 0, 0, RATE);
        assertEquals(0, outcome.payableHours());
        assertEquals("Thiếu check-out", outcome.unpaidReason());
    }

    @Test
    void missingBothPunchesIsNotPaid() {
        PayrollCalculator.Outcome outcome = PayrollCalculator.evaluate(
                DAY, START, END, false, false, false, 0, 0, RATE);
        assertEquals(0, outcome.payableHours());
        assertEquals("Không check-in, không check-out", outcome.unpaidReason());
    }

    @Test
    void unknownShiftIsNotPaid() {
        PayrollCalculator.Outcome outcome = PayrollCalculator.evaluate(
                DAY, null, null, false, true, true, 0, 0, RATE);
        assertEquals("Không xác định được ca làm", outcome.unpaidReason());
        assertFalse(outcome.payable());
    }

    @Test
    void unknownRateIsNotPaid() {
        PayrollCalculator.Outcome outcome = PayrollCalculator.evaluate(
                DAY, START, END, false, true, true, 0, 0, null);
        assertEquals("Không xác định được mức lương", outcome.unpaidReason());
        assertEquals(0, outcome.shiftSalary().signum());
    }

    @Test
    void duplicateAttendanceIsNotPaid() {
        PayrollCalculator.Outcome outcome = PayrollCalculator.evaluate(
                DAY, START, END, true, true, true, 0, 0, RATE);
        assertEquals("Bản ghi chấm công bị trùng", outcome.unpaidReason());
    }

    @Test
    void overnightShiftUsesWholeHoursAcrossMidnight() {
        PayrollCalculator.Outcome outcome = PayrollCalculator.evaluate(
                DAY, LocalTime.of(22, 0), LocalTime.of(6, 0), false, true, true, 0, 0, RATE);
        assertEquals(8, outcome.scheduledHours());
        assertEquals(8, outcome.payableHours());
        assertEquals(new BigDecimal("240000"), outcome.shiftSalary());
    }

    @Test
    void partialHourIsTruncatedAndMoneyRoundsToDong() {
        assertEquals(7, PayrollCalculator.scheduledHours(DAY, LocalTime.of(8, 0), LocalTime.of(15, 40)));
        PayrollCalculator.Outcome outcome = PayrollCalculator.evaluate(
                DAY, START, END, false, true, true, 0, 0, new BigDecimal("30000.5"));
        assertEquals(new BigDecimal("240004"), outcome.shiftSalary());
    }

    private PayrollCalculator.Outcome pay(int lateMinutes, int earlyMinutes) {
        return PayrollCalculator.evaluate(DAY, START, END, false, true, true, lateMinutes, earlyMinutes, RATE);
    }
}

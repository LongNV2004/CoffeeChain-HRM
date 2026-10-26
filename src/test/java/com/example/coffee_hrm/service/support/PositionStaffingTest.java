package com.example.coffee_hrm.service.support;

import com.example.coffee_hrm.common.enums.ApprovalStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PositionStaffingTest {

    private static final PositionStaffing.Requirement BREW = new PositionStaffing.Requirement(1, "Pha chế", 2);
    private static final PositionStaffing.Requirement CASH = new PositionStaffing.Requirement(2, "Thu ngân", 1);
    private static final PositionStaffing.Requirement SERVE = new PositionStaffing.Requirement(3, "Phục vụ", 2);

    @Test
    void countsApprovedQualifiedStaffOnceAndKeepsPendingSeparate() {
        PositionStaffing.Snapshot snapshot = PositionStaffing.assess(5, List.of(BREW, CASH, SERVE), List.of(
                seat(10, ApprovalStatus.APPROVED, 1, true),
                seat(10, ApprovalStatus.PENDING, 3, true),
                seat(11, ApprovalStatus.PENDING, 1, true),
                seat(12, ApprovalStatus.APPROVED, 2, true),
                seat(13, ApprovalStatus.APPROVED, 3, true),
                seat(14, ApprovalStatus.REJECTED, 1, true),
                seat(15, ApprovalStatus.CANCELLED, 3, true),
                seat(16, ApprovalStatus.APPROVED, 1, false)));

        assertEquals(5, snapshot.requiredTotal());
        assertEquals(5, snapshot.registeredCount());
        assertEquals(1, snapshot.pendingCount());
        assertEquals(4, snapshot.approvedCount());
        assertEquals(1, snapshot.shortfall());
        assertTrue(snapshot.awaitingApproval());
        assertEquals(PositionStaffing.SHORT, snapshot.staffingStatusKey());
        assertEquals("Thiếu nhân sự", snapshot.staffingStatusLabel());
        assertEquals(1, snapshot.positionsFilled());
        assertEquals(3, snapshot.positionsTracked());
        assertEquals("Thiếu 1 Pha chế, Thiếu 1 Phục vụ", snapshot.shortageSummary());

        PositionStaffing.PositionLine brew = snapshot.positions().get(0);
        PositionStaffing.PositionLine cash = snapshot.positions().get(1);
        PositionStaffing.PositionLine serve = snapshot.positions().get(2);
        assertEquals(1, brew.approved());
        assertEquals(1, brew.shortfall());
        assertEquals("Thiếu", brew.statusLabel());
        assertEquals(1, cash.approved());
        assertEquals(0, cash.shortfall());
        assertEquals("Đủ", cash.statusLabel());
        assertEquals(1, serve.approved());
        assertEquals(1, serve.shortfall());
    }

    @Test
    void enoughOnlyWhenHeadcountAndEveryPositionAreFilled() {
        PositionStaffing.Snapshot snapshot = PositionStaffing.assess(5, List.of(BREW, CASH, SERVE), List.of(
                seat(1, ApprovalStatus.APPROVED, 1, true),
                seat(2, ApprovalStatus.APPROVED, 1, true),
                seat(3, ApprovalStatus.APPROVED, 2, true),
                seat(4, ApprovalStatus.APPROVED, 3, true),
                seat(5, ApprovalStatus.APPROVED, 3, true),
                seat(6, ApprovalStatus.PENDING, 1, true)));

        assertEquals(5, snapshot.approvedCount());
        assertEquals(1, snapshot.pendingCount());
        assertEquals(6, snapshot.registeredCount());
        assertEquals(0, snapshot.shortfall());
        assertEquals(3, snapshot.positionsFilled());
        assertEquals(PositionStaffing.ENOUGH, snapshot.staffingStatusKey());
        assertTrue(snapshot.awaitingApproval());
        assertNull(snapshot.shortageSummary());
    }

    @Test
    void headcountCanBeEnoughWhileAPositionIsStillShort() {
        PositionStaffing.Snapshot snapshot = PositionStaffing.assess(2, List.of(
                new PositionStaffing.Requirement(1, "Pha chế", 1),
                new PositionStaffing.Requirement(3, "Phục vụ", 1)), List.of(
                seat(1, ApprovalStatus.APPROVED, 1, true),
                seat(2, ApprovalStatus.APPROVED, 1, true)));

        assertEquals(0, snapshot.shortfall());
        assertEquals(2, snapshot.approvedCount());
        assertEquals(PositionStaffing.SHORT, snapshot.staffingStatusKey());
        assertEquals("Thiếu 1 Phục vụ", snapshot.shortageSummary());
        assertEquals(1, snapshot.positionsFilled());
    }

    @Test
    void legacyShiftWithoutPositionsUsesApprovedHeadcount() {
        PositionStaffing.Snapshot shortfall = PositionStaffing.assess(3, List.of(), List.of(
                seat(1, ApprovalStatus.APPROVED, null, false),
                seat(2, ApprovalStatus.PENDING, null, false)));
        assertFalse(shortfall.positionsConfigured());
        assertEquals(2, shortfall.shortfall());
        assertEquals(PositionStaffing.SHORT, shortfall.staffingStatusKey());
        assertTrue(shortfall.awaitingApproval());

        PositionStaffing.Snapshot enough = PositionStaffing.assess(1, List.of(), List.of(
                seat(1, ApprovalStatus.APPROVED, null, false)));
        assertEquals(PositionStaffing.ENOUGH, enough.staffingStatusKey());
        assertEquals(0, enough.shortfall());
    }

    @Test
    void personWithSeveralSkillsIsNotCountedAsSeveralPeople() {
        PositionStaffing.Snapshot snapshot = PositionStaffing.assess(2, List.of(BREW, SERVE), List.of(
                seat(8, ApprovalStatus.APPROVED, 1, true),
                new PositionStaffing.Seat(8, ApprovalStatus.APPROVED, 3, true)));

        assertEquals(1, snapshot.approvedCount());
        assertEquals(1, snapshot.positions().get(0).approved());
        assertEquals(0, snapshot.positions().get(1).approved());
    }

    private static PositionStaffing.Seat seat(int employeeId, ApprovalStatus status, Integer skillId, boolean qualified) {
        return new PositionStaffing.Seat(employeeId, status, skillId, qualified);
    }
}

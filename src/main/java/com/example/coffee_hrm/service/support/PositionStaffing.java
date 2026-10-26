package com.example.coffee_hrm.service.support;

import com.example.coffee_hrm.common.enums.ApprovalStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Đối chiếu đăng ký thực tế với chỉ tiêu ca và nhu cầu từng vị trí.
 * Một nhân viên chỉ chiếm một chỗ của một ca, và chỉ tính vào đúng vị trí đã đăng ký.
 */
public final class PositionStaffing {

    public static final String ENOUGH = "enough";
    public static final String SHORT = "short";
    public static final String UNCONFIGURED = "unconfigured";
    public static final String ENOUGH_LABEL = "Đủ nhân sự";
    public static final String SHORT_LABEL = "Thiếu nhân sự";
    public static final String UNCONFIGURED_LABEL = "Chưa cấu hình";
    public static final String POSITION_ENOUGH = "Đủ";
    public static final String POSITION_SHORT = "Thiếu";

    private PositionStaffing() {
    }

    public record Requirement(int skillId, String skillName, int required) {
    }

    /**
     * @param qualifiedForSkill nhân viên đã được duyệt/chờ và có chứng chỉ đúng vị trí đã đăng ký
     */
    public record Seat(Integer employeeId, ApprovalStatus status, Integer skillId, boolean qualifiedForSkill) {
    }

    public record PositionLine(int skillId, String skillName, int required, int registered, int pending, int approved,
                                int shortfall, String statusKey, String statusLabel) {
    }

    public record SkillCount(int registered, int pending, int approved) {
    }

    public record Snapshot(int requiredTotal,
                           int registeredCount,
                           int pendingCount,
                           int approvedCount,
                           int positionsFilled,
                           int positionsTracked,
                           int shortfall,
                           boolean positionsConfigured,
                           boolean awaitingApproval,
                           String staffingStatusKey,
                           String staffingStatusLabel,
                           String shortageSummary,
                           int staffingProgress,
                           List<PositionLine> positions,
                           Map<Integer, SkillCount> skillCounts) {
    }

    public static Snapshot assess(Integer maxEmployees, List<Requirement> requirements, List<Seat> seats) {
        Map<String, Seat> unique = new LinkedHashMap<>();
        int anonymous = 0;
        for (Seat seat : seats == null ? List.<Seat>of() : seats) {
            if (seat == null || !occupies(seat.status())) {
                continue;
            }
            String key = seat.employeeId() != null ? "e:" + seat.employeeId() : "a:" + anonymous++;
            Seat current = unique.get(key);
            if (current != null && current.status() == ApprovalStatus.APPROVED) {
                continue;
            }
            unique.put(key, seat);
        }

        int pending = 0;
        int approved = 0;
        for (Seat seat : unique.values()) {
            if (seat.status() == ApprovalStatus.APPROVED) {
                approved++;
            } else {
                pending++;
            }
        }

        Map<Integer, int[]> bySkill = new LinkedHashMap<>();
        for (Seat seat : unique.values()) {
            if (!seat.qualifiedForSkill() || seat.skillId() == null) {
                continue;
            }
            int[] counts = bySkill.computeIfAbsent(seat.skillId(), ignored -> new int[2]);
            if (seat.status() == ApprovalStatus.APPROVED) {
                counts[1]++;
            } else {
                counts[0]++;
            }
        }
        Map<Integer, SkillCount> skillCounts = new LinkedHashMap<>();
        bySkill.forEach((skillId, counts) -> skillCounts.put(
                skillId, new SkillCount(counts[0] + counts[1], counts[0], counts[1])));

        List<Requirement> requiredPositions = requirements == null ? List.of() : requirements;
        List<PositionLine> lines = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        int tracked = 0;
        int filled = 0;
        for (Requirement requirement : requiredPositions) {
            SkillCount count = skillCounts.getOrDefault(requirement.skillId(), new SkillCount(0, 0, 0));
            int approvedForPosition = count.approved();
            int positionShortfall = Math.max(0, requirement.required() - approvedForPosition);
            boolean positionEnough = positionShortfall == 0;
            if (requirement.required() > 0) {
                tracked++;
                if (positionEnough) {
                    filled++;
                } else {
                    missing.add("Thiếu " + positionShortfall + " " + requirement.skillName());
                }
            }
            lines.add(new PositionLine(
                    requirement.skillId(),
                    requirement.skillName(),
                    requirement.required(),
                    count.registered(),
                    count.pending(),
                    approvedForPosition,
                    positionShortfall,
                    positionEnough ? ENOUGH : SHORT,
                    positionEnough ? POSITION_ENOUGH : POSITION_SHORT));
        }

        boolean configuredLimit = maxEmployees != null && maxEmployees > 0;
        int requiredTotal = configuredLimit ? maxEmployees : 0;
        int shortfall = configuredLimit ? Math.max(0, requiredTotal - approved) : 0;
        boolean positionsConfigured = !requiredPositions.isEmpty();
        boolean headcountEnough = configuredLimit && approved >= requiredTotal;
        boolean positionsEnough = !positionsConfigured || filled == tracked;
        boolean enough = configuredLimit && headcountEnough && positionsEnough;
        int progress = configuredLimit ? Math.min(100, approved * 100 / requiredTotal) : 0;
        String summary = missing.isEmpty() ? null : String.join(", ", missing);
        String statusKey = !configuredLimit ? UNCONFIGURED : enough ? ENOUGH : SHORT;
        String statusLabel = !configuredLimit ? UNCONFIGURED_LABEL : enough ? ENOUGH_LABEL : SHORT_LABEL;
        return new Snapshot(
                requiredTotal,
                pending + approved,
                pending,
                approved,
                filled,
                tracked,
                shortfall,
                positionsConfigured,
                pending > 0,
                statusKey,
                statusLabel,
                summary,
                progress,
                List.copyOf(lines),
                Map.copyOf(skillCounts));
    }

    private static boolean occupies(ApprovalStatus status) {
        return status == ApprovalStatus.PENDING || status == ApprovalStatus.APPROVED;
    }
}

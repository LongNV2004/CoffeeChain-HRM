package com.example.coffee_hrm.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class AdminDashboardView {

    private final String username;
    private final String displayName;
    private final LocalDate today;
    private final String todayLabel;
    private final long totalStores;
    private final long activeStores;
    private final long totalEmployees;
    private final long waitingChainReportCount;
    private final long managerCount;
    private final long staffCount;
    private final long unassignedRoleCount;
    private final long otherRoleCount;
    private final List<TrendPoint> trend;

    public boolean isTrendQuiet() {
        return trend == null || trend.stream().allMatch(point -> point.getHired() == 0 && point.getResigned() == 0);
    }

    public String getTrendLabelsJoined() {
        return join(point -> point.getLabel());
    }

    public String getHiredJoined() {
        return join(point -> Long.toString(point.getHired()));
    }

    public String getResignedJoined() {
        return join(point -> Long.toString(point.getResigned()));
    }

    private String join(java.util.function.Function<TrendPoint, String> value) {
        if (trend == null || trend.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (TrendPoint point : trend) {
            if (!builder.isEmpty()) {
                builder.append(',');
            }
            builder.append(value.apply(point));
        }
        return builder.toString();
    }

    @Getter
    @Builder
    public static class TrendPoint {
        private final String label;
        private final long hired;
        private final long resigned;
    }
}

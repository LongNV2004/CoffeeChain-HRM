package com.example.coffee_hrm.dto.request;

import java.math.BigDecimal;

public record AttendanceLocation(BigDecimal latitude, BigDecimal longitude, Double accuracyMeters) {

    public static AttendanceLocation parse(String latitude, String longitude, String accuracyMeters) {
        return new AttendanceLocation(decimal(latitude), decimal(longitude), number(accuracyMeters));
    }

    private static BigDecimal decimal(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Double number(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            double value = Double.parseDouble(raw.trim());
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}

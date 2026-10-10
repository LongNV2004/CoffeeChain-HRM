package com.example.coffee_hrm.common.geo;

import java.math.BigDecimal;

public final class GeoDistance {

    public static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private GeoDistance() {
    }

    public static double meters(BigDecimal latitude1, BigDecimal longitude1,
                                BigDecimal latitude2, BigDecimal longitude2) {
        if (latitude1 == null || longitude1 == null || latitude2 == null || longitude2 == null) {
            return Double.NaN;
        }
        return meters(latitude1.doubleValue(), longitude1.doubleValue(),
                latitude2.doubleValue(), longitude2.doubleValue());
    }

    public static double meters(double latitude1, double longitude1, double latitude2, double longitude2) {
        double lat1 = Math.toRadians(latitude1);
        double lat2 = Math.toRadians(latitude2);
        double deltaLat = lat2 - lat1;
        double deltaLon = Math.toRadians(longitude2 - longitude1);
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

    public static boolean isValidLatitude(BigDecimal latitude) {
        return latitude != null
                && latitude.compareTo(BigDecimal.valueOf(-90)) >= 0
                && latitude.compareTo(BigDecimal.valueOf(90)) <= 0;
    }

    public static boolean isValidLongitude(BigDecimal longitude) {
        return longitude != null
                && longitude.compareTo(BigDecimal.valueOf(-180)) >= 0
                && longitude.compareTo(BigDecimal.valueOf(180)) <= 0;
    }
}

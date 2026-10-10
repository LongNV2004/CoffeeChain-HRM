package com.example.coffee_hrm.common.geo;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeoDistanceTest {

    @Test
    void samePointIsZeroMeters() {
        BigDecimal latitude = new BigDecimal("21.028511");
        BigDecimal longitude = new BigDecimal("105.854167");

        assertEquals(0, GeoDistance.meters(latitude, longitude, latitude, longitude), 0.001);
    }

    @Test
    void pureNorthOffsetMatchesTheRequestedDistance() {
        double origin = 21.0;
        double longitude = 105.0;
        double twoHundred = origin + Math.toDegrees(200.0 / GeoDistance.EARTH_RADIUS_METERS);
        double twoHundredOne = origin + Math.toDegrees(201.0 / GeoDistance.EARTH_RADIUS_METERS);

        assertEquals(200, GeoDistance.meters(origin, longitude, twoHundred, longitude), 0.01);
        assertTrue(GeoDistance.meters(origin, longitude, twoHundredOne, longitude) > 200);
    }

    @Test
    void coordinateRangesIncludeThePolesAndTheAntimeridian() {
        assertTrue(GeoDistance.isValidLatitude(new BigDecimal("-90")));
        assertTrue(GeoDistance.isValidLatitude(new BigDecimal("90")));
        assertFalse(GeoDistance.isValidLatitude(new BigDecimal("90.0001")));
        assertTrue(GeoDistance.isValidLongitude(new BigDecimal("-180")));
        assertTrue(GeoDistance.isValidLongitude(new BigDecimal("180")));
        assertFalse(GeoDistance.isValidLongitude(new BigDecimal("180.1")));
    }
}

package com.cph.alpharoute.util;

/**
 * Geographic and range constants used across the app.
 *
 * <p>Kept in one place so a limit is never written twice and the two copies cannot disagree. The
 * arrival radius bounds match the Settings validation described in the Phase 0.3 screen plan.
 */
public final class GeoConstants {

    /** Utility class - never instantiated. */
    private GeoConstants() {
    }

    /**
     * IUGG mean Earth radius, in metres. Used by the haversine distance calculation.
     */
    public static final double EARTH_RADIUS_M = 6371008.8;

    /**
     * Default arrival radius, in metres.
     *
     * <p>The specification recommends 50 m as the starting value, and it must stay configurable from
     * the Settings screen. Exact coordinate equality is never required, because GPS readings vary.
     */
    public static final int DEFAULT_ARRIVAL_RADIUS_M = 50;

    /** Smallest arrival radius the Settings screen will accept, in metres. */
    public static final int MIN_ARRIVAL_RADIUS_M = 10;

    /** Largest arrival radius the Settings screen will accept, in metres. */
    public static final int MAX_ARRIVAL_RADIUS_M = 1000;

    public static final double MIN_LATITUDE = -90.0;
    public static final double MAX_LATITUDE = 90.0;
    public static final double MIN_LONGITUDE = -180.0;
    public static final double MAX_LONGITUDE = 180.0;

    /** True when the pair is a real coordinate rather than a default or a corrupted value. */
    public static boolean isValidLatitude(double latitude) {
        return latitude >= MIN_LATITUDE && latitude <= MAX_LATITUDE;
    }

    /** True when the pair is a real coordinate rather than a default or a corrupted value. */
    public static boolean isValidLongitude(double longitude) {
        return longitude >= MIN_LONGITUDE && longitude <= MAX_LONGITUDE;
    }

    /** True when the arrival radius is inside the range the Settings screen allows. */
    public static boolean isValidArrivalRadius(int radiusMetres) {
        return radiusMetres >= MIN_ARRIVAL_RADIUS_M && radiusMetres <= MAX_ARRIVAL_RADIUS_M;
    }
}

package com.cph.alpharoute.domain;

/**
 * How far apart two points are, on the ground.
 *
 * <p><b>Pure Java, zero Android imports (AD-11).</b> Distance is what the arrival decision rests on, and
 * the arrival decision is the only thing in this app that can offer to count a store as completed. That
 * makes it the most consequential arithmetic in the project, and it deserves to be provable on a laptop
 * in milliseconds rather than by driving around a barangay.
 *
 * <p><b>Why a hand-written haversine rather than a library.</b> Every map library in the app already
 * smuggles its own distance function - osmdroid has {@code GeoPoint.distanceToAsDouble()}, and Play
 * Services has {@code SphericalUtil} and {@code Location.distanceBetween()}. Using any of them would mean
 * the number that decides "you have arrived" could only be tested on a device, and could silently change
 * if a library ever changed its formula or its Earth radius. This is twenty lines, it is the one formula
 * every navigation system uses, and it is ours.
 *
 * <p><b>The Earth is treated as a sphere, and that is deliberate.</b> The haversine formula assumes a
 * sphere of radius {@link com.cph.alpharoute.util.GeoConstants#EARTH_RADIUS_M}. Over the distances that
 * matter here - tens of metres - the error against a true ellipsoid is far smaller than the accuracy of
 * any phone's GPS fix. Using a more elaborate formula would add code without adding truth; the GPS is
 * already the limiting factor by an order of magnitude.
 *
 * <p><b>Coordinates are validated, not trusted.</b> A fix with nonsense in it must never produce a
 * plausible-looking zero, because zero distance means "you have arrived" - the single most dangerous
 * wrong answer this class could give. {@link #distanceMetres} returns {@link Double#NaN} instead, and
 * every caller is written to treat that as "cannot tell", never as "close enough".
 */
public final class DistanceCalculator {

    /** Utility class - never instantiated. */
    private DistanceCalculator() {
    }

    /**
     * The great-circle distance between two points, in metres.
     *
     * @return the distance in metres, or {@link Double#NaN} when either coordinate is unusable. NaN
     *         rather than a number, because a caller that mistakenly treats a failure as
     *         "zero metres away" would offer a completion the user has not earned.
     */
    public static double distanceMetres(double fromLatitude,
                                        double fromLongitude,
                                        double toLatitude,
                                        double toLongitude) {
        if (!isUsable(fromLatitude, fromLongitude) || !isUsable(toLatitude, toLongitude)) {
            return Double.NaN;
        }

        double fromLatRad = Math.toRadians(fromLatitude);
        double toLatRad = Math.toRadians(toLatitude);
        double deltaLatRad = Math.toRadians(toLatitude - fromLatitude);
        double deltaLngRad = Math.toRadians(toLongitude - fromLongitude);

        double sinHalfLat = Math.sin(deltaLatRad / 2.0);
        double sinHalfLng = Math.sin(deltaLngRad / 2.0);

        // The haversine of the central angle. The first term is the north-south separation and the
        // second is the east-west one, squeezed by the cosine of the latitude - which is why two points
        // one degree apart are about 111 km apart near the equator and much closer near a pole.
        double haversine = sinHalfLat * sinHalfLat
                + Math.cos(fromLatRad) * Math.cos(toLatRad) * sinHalfLng * sinHalfLng;

        // Clamped before asin. Floating-point rounding can push this a hair above 1.0 for two points
        // very close together, and asin(1.0000000001) is NaN - which would turn the shortest, safest
        // distance in the app into "cannot tell".
        double clamped = Math.min(1.0, Math.max(0.0, haversine));

        // The arc is 2*asin(sqrt(h)), which stays accurate for the small angles this app actually
        // measures. The alternative form, 2*atan2(sqrt(h), sqrt(1-h)), is more robust for antipodal
        // points - a case that cannot arise here, and one this app has no business handling.
        return 2.0 * com.cph.alpharoute.util.GeoConstants.EARTH_RADIUS_M * Math.asin(Math.sqrt(clamped));
    }

    /**
     * The same distance for two {@link GeoCoordinate}s.
     *
     * @return metres, or {@link Double#NaN} when either point is unusable
     */
    public static double distanceMetres(GeoCoordinate from, GeoCoordinate to) {
        if (from == null || to == null) {
            return Double.NaN;
        }
        return distanceMetres(from.latitude(), from.longitude(), to.latitude(), to.longitude());
    }

    /**
     * True when both values are real numbers inside the globe.
     *
     * <p>A {@code NaN} latitude would otherwise propagate through the trigonometry and come out as a
     * {@code NaN} result anyway - but it is checked explicitly so the reason is visible in the code
     * rather than implied by the maths.
     */
    private static boolean isUsable(double latitude, double longitude) {
        return com.cph.alpharoute.util.GeoConstants.isValidLatitude(latitude)
                && com.cph.alpharoute.util.GeoConstants.isValidLongitude(longitude);
    }
}

package com.cph.alpharoute.domain;

/**
 * A latitude and a longitude.
 *
 * <p>Pure Java with no Android imports (AD-11), so a coordinate can be built, compared and asserted on the
 * JVM without a device or a map.
 *
 * <p><b>One type for every coordinate in the app.</b> A point on a route, the place a store is pinned at,
 * and the location read out of a shared link are all the same thing, and having one class for them means
 * there is no way to pass the wrong one or for two of them to drift apart in how they validate.
 *
 * <p><b>Latitude first, here.</b> The routing service sends its coordinates as {@code [longitude,
 * latitude]} - the opposite order - and the parsers are the places that swap them. From this class
 * onwards, {@link #latitude()} really is the latitude. Doing the swap once, at each edge, is what stops
 * the two orders leaking into the rest of the app.
 */
public final class GeoCoordinate {

    private final double latitude;
    private final double longitude;

    public GeoCoordinate(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public double latitude() {
        return latitude;
    }

    public double longitude() {
        return longitude;
    }

    /**
     * Equal when both coordinates are equal.
     *
     * <p>Present so a parsed result can be compared against an expected one in a test with a single
     * assertion, rather than coordinate by coordinate - which is where a test usually ends up hiding the
     * very difference it was written to catch.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GeoCoordinate)) {
            return false;
        }
        GeoCoordinate that = (GeoCoordinate) other;
        return Double.compare(latitude, that.latitude) == 0
                && Double.compare(longitude, that.longitude) == 0;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(latitude) * 31 + Double.hashCode(longitude);
    }

    @Override
    public String toString() {
        return "GeoCoordinate{" + latitude + ", " + longitude + '}';
    }
}

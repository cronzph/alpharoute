package com.cph.alpharoute.location;

import com.cph.alpharoute.domain.ArrivalEvaluator;

/**
 * One GPS reading, reduced to the four things this app actually needs.
 *
 * <p><b>Why not just use Android's {@code Location}?</b> Because {@code Location} is an Android class,
 * and anything that touches it can only be tested on a device. This app's hardest correctness problems
 * all hinge on the quality of a location reading - is it precise enough to decide that the user has
 * arrived? is it too old to trust? - and those decisions deserve to be provable in milliseconds on a
 * laptop rather than by driving around.
 *
 * <p>So this class is <b>pure Java with zero Android imports</b> (AD-11), and the translation from
 * Android's {@code Location} happens once, in {@code LocationProvider}. Everything downstream works
 * with this instead.
 *
 * <p><b>Unknown accuracy is represented by {@link Float#NaN}, not by zero.</b> That distinction
 * matters: Android reports "no accuracy value" for a fix whose accuracy was never set, and treating
 * that as {@code 0} would make the worst possible fix look like the best possible one. {@code NaN}
 * propagates honestly, and {@link #hasAccuracy()} is the single question to ask.
 */
public final class LocationFix implements ArrivalEvaluator.LocationFixLike {

    private final double latitude;
    private final double longitude;
    private final float accuracyMetres;
    private final long timestampMillis;

    /**
     * @param accuracyMetres the radius of 68% confidence, in metres, or {@link Float#NaN} when the
     *                       reading did not supply one
     */
    public LocationFix(double latitude, double longitude, float accuracyMetres, long timestampMillis) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracyMetres = accuracyMetres;
        this.timestampMillis = timestampMillis;
    }

    public double latitude() {
        return latitude;
    }

    public double longitude() {
        return longitude;
    }

    /** The accuracy radius in metres, or {@code NaN} when unknown. Always check {@link #hasAccuracy()}. */
    public float accuracyMetres() {
        return accuracyMetres;
    }

    /** When the fix was taken, epoch millis, as reported by the provider. */
    public long timestampMillis() {
        return timestampMillis;
    }

    /**
     * True when the reading carried a usable accuracy value.
     *
     * <p>Zero is treated as "not usable", not as "perfect". A real GPS accuracy radius is never exactly
     * zero, but some devices report it that way when the value is missing - and a spurious zero would
     * make the worst possible fix look like the best possible one, which is precisely the reading the
     * arrival check must not trust. Negative values are nonsense and are treated the same way.
     */
    public boolean hasAccuracy() {
        return !Float.isNaN(accuracyMetres) && accuracyMetres > 0f;
    }

    /** True when the coordinates are inside the globe. A fix can be reported with nonsense in it. */
    public boolean hasUsableCoordinates() {
        return !Double.isNaN(latitude) && !Double.isNaN(longitude)
                && latitude >= -90.0 && latitude <= 90.0
                && longitude >= -180.0 && longitude <= 180.0;
    }

    /**
     * True when this fix is precise enough for a given purpose.
     *
     * <p>A fix with <b>no</b> accuracy value is never "accurate enough". That is deliberately
     * conservative: the arrival check in Phase 5.1 decides whether to offer the completion dialog, and
     * offering it on a fix of unknown quality is exactly the mistake that would let a store be counted
     * from the wrong side of the street.
     *
     * @param maxAccuracyMetres the largest acceptable radius, in metres
     */
    public boolean isAccurateEnough(float maxAccuracyMetres) {
        return hasAccuracy() && accuracyMetres <= maxAccuracyMetres;
    }

    /**
     * True when the reading is older than {@code maxAgeMillis}.
     *
     * <p>A cached "last known location" can be minutes or hours old, and on a moving vehicle an old fix
     * is a wrong fix. The arrival check must not act on one.
     */
    public boolean isStale(long nowMillis, long maxAgeMillis) {
        return (nowMillis - timestampMillis) > maxAgeMillis;
    }

    @Override
    public String toString() {
        return "LocationFix{lat=" + latitude
                + ", lng=" + longitude
                + ", accuracy=" + (hasAccuracy() ? accuracyMetres + "m" : "unknown")
                + ", at=" + timestampMillis + '}';
    }
}

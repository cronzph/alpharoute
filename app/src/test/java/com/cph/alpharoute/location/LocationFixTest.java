package com.cph.alpharoute.location;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests for {@link LocationFix} - the quality rules every location decision in the app rests on.
 *
 * <p>These run on the JVM, no device and no emulator, because the class is pure Java. That is the whole
 * reason it exists rather than the app passing Android's {@code Location} around: the rules below decide
 * whether a store may be marked as visited, and they deserve to be provable in milliseconds rather than
 * by walking around a car park.
 *
 * <p>Three of these tests guard against failure modes that are genuinely nasty in the field:
 *
 * <ul>
 *   <li><b>A missing accuracy must never read as "accurate".</b> Android reports no accuracy for some
 *       readings, and treating that as zero would make the worst fix look like the best one.</li>
 *   <li><b>A reported accuracy of exactly zero must also not read as "accurate".</b> A real GPS radius
 *       is never zero; some devices report it that way when the value is missing.</li>
 *   <li><b>Nonsense coordinates must be rejected.</b> A fix can arrive with the latitude and longitude
 *       swapped or blank, and a marker placed from one would be somewhere impossible.</li>
 * </ul>
 */
public class LocationFixTest {

    /** A real Metro Manila position. */
    private static final double LATITUDE = 14.5995;
    private static final double LONGITUDE = 120.9842;

    private static final float ACCURACY_GOOD = 8f;
    private static final float ACCURACY_POOR = 250f;

    private static final long NOW = 1_700_000_000_000L;

    private static LocationFix fix(float accuracyMetres) {
        return new LocationFix(LATITUDE, LONGITUDE, accuracyMetres, NOW);
    }

    // ---------------------------------------------------------------- coordinates

    @Test
    public void aNormalFixHasUsableCoordinates() {
        assertTrue(fix(ACCURACY_GOOD).hasUsableCoordinates());
    }

    @Test
    public void aNaNLatitudeIsRejected() {
        LocationFix broken = new LocationFix(Double.NaN, LONGITUDE, ACCURACY_GOOD, NOW);

        assertFalse(broken.hasUsableCoordinates());
    }

    @Test
    public void aNaNLongitudeIsRejected() {
        LocationFix broken = new LocationFix(LATITUDE, Double.NaN, ACCURACY_GOOD, NOW);

        assertFalse(broken.hasUsableCoordinates());
    }

    @Test
    public void coordinatesOutsideTheGlobeAreRejected() {
        assertFalse(new LocationFix(90.0001, LONGITUDE, ACCURACY_GOOD, NOW).hasUsableCoordinates());
        assertFalse(new LocationFix(-90.0001, LONGITUDE, ACCURACY_GOOD, NOW).hasUsableCoordinates());
        assertFalse(new LocationFix(LATITUDE, 180.0001, ACCURACY_GOOD, NOW).hasUsableCoordinates());
        assertFalse(new LocationFix(LATITUDE, -180.0001, ACCURACY_GOOD, NOW).hasUsableCoordinates());
    }

    @Test
    public void coordinatesAtTheExactBoundariesAreUsable() {
        // The poles and the antimeridian are real places, so the bounds are inclusive.
        assertTrue(new LocationFix(90.0, 180.0, ACCURACY_GOOD, NOW).hasUsableCoordinates());
        assertTrue(new LocationFix(-90.0, -180.0, ACCURACY_GOOD, NOW).hasUsableCoordinates());
    }

    @Test
    public void latitudeAndLongitudeZeroIsARealPosition() {
        // The Gulf of Guinea. Conflating 0.0 with "missing" is the classic bug this guards.
        assertTrue(new LocationFix(0.0, 0.0, ACCURACY_GOOD, NOW).hasUsableCoordinates());
    }

    // ---------------------------------------------------------------- accuracy

    @Test
    public void aRealAccuracyValueIsUsable() {
        assertTrue(fix(ACCURACY_GOOD).hasAccuracy());
    }

    @Test
    public void anUnknownAccuracyIsNotUsable() {
        assertFalse(fix(Float.NaN).hasAccuracy());
    }

    @Test
    public void aReportedAccuracyOfZeroIsTreatedAsUnknown() {
        // A real accuracy radius is never zero. Some devices report 0 when they mean "no value", and
        // believing it would make every fix look perfect.
        assertFalse(fix(0f).hasAccuracy());
    }

    @Test
    public void aNegativeAccuracyIsTreatedAsUnknown() {
        assertFalse(fix(-5f).hasAccuracy());
    }

    @Test
    public void theRawAccuracyValueIsStillReadable() {
        // The accessor is honest about what was reported, even when the value is not usable.
        assertEquals(Float.NaN, fix(Float.NaN).accuracyMetres(), 0f);
        assertEquals(0f, fix(0f).accuracyMetres(), 0f);
    }

    // ---------------------------------------------------------------- isAccurateEnough

    @Test
    public void aFixInsideTheThresholdIsAccurateEnough() {
        assertTrue(fix(ACCURACY_GOOD).isAccurateEnough(50f));
    }

    @Test
    public void aFixExactlyAtTheThresholdIsAccurateEnough() {
        assertTrue("The threshold is inclusive", fix(50f).isAccurateEnough(50f));
    }

    @Test
    public void aFixOutsideTheThresholdIsNotAccurateEnough() {
        assertFalse(fix(ACCURACY_POOR).isAccurateEnough(50f));
    }

    @Test
    public void aFixWithUnknownAccuracyIsNeverAccurateEnough() {
        // The conservative rule, and the important one: the arrival check must not offer the completion
        // dialog on a fix whose quality nobody knows.
        assertFalse(fix(Float.NaN).isAccurateEnough(1000f));
        assertFalse(fix(0f).isAccurateEnough(1000f));
    }

    // ---------------------------------------------------------------- isStale

    @Test
    public void aFreshFixIsNotStale() {
        LocationFix recent = new LocationFix(LATITUDE, LONGITUDE, ACCURACY_GOOD, NOW);

        assertFalse(recent.isStale(NOW + 1_000L, 30_000L));
    }

    @Test
    public void anOldFixIsStale() {
        LocationFix old = new LocationFix(LATITUDE, LONGITUDE, ACCURACY_GOOD, NOW);

        assertTrue(old.isStale(NOW + 60_000L, 30_000L));
    }

    @Test
    public void aFixExactlyAtTheAgeLimitIsNotStale() {
        LocationFix borderline = new LocationFix(LATITUDE, LONGITUDE, ACCURACY_GOOD, NOW);

        assertFalse("The age limit is inclusive", borderline.isStale(NOW + 30_000L, 30_000L));
    }

    // ---------------------------------------------------------------- accessors

    @Test
    public void theAccessorsReturnWhatWasGiven() {
        LocationFix fix = fix(ACCURACY_GOOD);

        assertEquals(LATITUDE, fix.latitude(), 0.0);
        assertEquals(LONGITUDE, fix.longitude(), 0.0);
        assertEquals(ACCURACY_GOOD, fix.accuracyMetres(), 0f);
        assertEquals(NOW, fix.timestampMillis());
    }

    @Test
    public void toStringSaysUnknownRatherThanPrintingNaN() {
        assertTrue(fix(Float.NaN).toString().contains("unknown"));
        assertTrue(fix(ACCURACY_GOOD).toString().contains("8.0m"));
    }
}

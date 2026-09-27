package com.cph.alpharoute.domain;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * The arrival decision - the gate in front of the only action that changes a completion count.
 *
 * <p><b>What is being proved here.</b> The other completion guards in this app are enforced by the
 * database: a UNIQUE index stops a double count (AD-5) and a foreign key stops a completed store being
 * deleted (AD-6). Arrival has no such backstop. If this decides wrongly, the app offers to count a store
 * the user is not standing in, and nothing downstream can tell that the resulting record is false.
 *
 * <p>So the tests below are not a smoke test. They pin down the two rules that carry the weight:
 *
 * <ul>
 *   <li><b>The accuracy circle widens the radius.</b> Arrival is only claimed when the worst plausible
 *       position is still inside the radius. This is the conservative direction.</li>
 *   <li><b>An unusable reading is never "arrived".</b> Stale, imprecise, malformed and missing readings
 *       all produce a refusal to decide - never a plausible-looking yes.</li>
 * </ul>
 *
 * <p>Pure JVM tests. No device, no GPS, no waiting for the clock to move: the current time is passed in.
 */
public class ArrivalEvaluatorTest {

    /** A point in Manila, used as the destination throughout. */
    private static final GeoCoordinate DESTINATION = new GeoCoordinate(14.5995, 120.9842);

    /** The default arrival radius, as the Settings screen writes it. */
    private static final int RADIUS_50 = 50;

    private static final long NOW = 1_700_000_000_000L;

    /**
     * A point the given number of metres due north of the destination.
     *
     * <p>North rather than east on purpose: a degree of latitude is the same length everywhere, so the
     * arithmetic is a single division and the test cannot be confused by the cosine term. The constant
     * matches the one in {@code DistanceCalculatorTest}.
     */
    private static GeoCoordinate metresNorth(double metres) {
        double degrees = metres / 111_195.0;
        return new GeoCoordinate(DESTINATION.latitude() + degrees, DESTINATION.longitude());
    }

    // ------------------------------------------------------------------ the happy path

    @Test
    public void standingOnThePinIsArrived() {
        ArrivalState state = ArrivalEvaluator.evaluate(
                fixAt(metresNorth(0), 10f), DESTINATION, RADIUS_50, NOW);
        assertEquals(ArrivalState.ARRIVED, state);
    }

    @Test
    public void twentyMetresAwayWithAGoodFixIsArrived() {
        ArrivalState state = ArrivalEvaluator.evaluate(
                fixAt(metresNorth(20), 10f), DESTINATION, RADIUS_50, NOW);
        assertEquals(ArrivalState.ARRIVED, state);
    }

    @Test
    public void fourKilometresAwayIsOutside() {
        ArrivalState state = ArrivalEvaluator.evaluate(
                fixAt(metresNorth(4000), 10f), DESTINATION, RADIUS_50, NOW);
        assertEquals(ArrivalState.OUTSIDE, state);
    }

    /**
     * Three hundred metres with a fifty-metre radius is still outside.
     *
     * <p>The bound is 50 + 10 + 150 = 210 m. Past that the honest answer is "outside" - the approaching
     * band is a courtesy for the last stretch, not a second radius.
     */
    @Test
    public void threeHundredMetresAwayIsOutside() {
        ArrivalState state = ArrivalEvaluator.evaluate(
                fixAt(metresNorth(300), 10f), DESTINATION, RADIUS_50, NOW);
        assertEquals(ArrivalState.OUTSIDE, state);
    }

    /**
     * The upper edge of the approaching band. 209 m is inside it, 211 m is not - and both are checked, so
     * a test that happened to sit just inside the boundary could not pass by accident.
     */
    @Test
    public void theApproachingBandEndsAtTheRadiusPlusTheMargin() {
        assertEquals(ArrivalState.APPROACHING,
                ArrivalEvaluator.evaluate(fixAt(metresNorth(209), 10f), DESTINATION, RADIUS_50, NOW));
        assertEquals(ArrivalState.OUTSIDE,
                ArrivalEvaluator.evaluate(fixAt(metresNorth(211), 10f), DESTINATION, RADIUS_50, NOW));
    }

    // ------------------------------------------------------------------ the accuracy circle

    /**
     * <b>The central rule.</b> Sixty metres away with a ten-metre fix is outside a bare fifty-metre
     * radius - but the effective radius is 50 + 10 = 60, so the worst plausible position is right on the
     * line. This is the case that would flip if the accuracy were ignored.
     */
    @Test
    public void accuracyWidensTheRadiusSoAborderLineFixCounts() {
        ArrivalState state = ArrivalEvaluator.evaluate(
                fixAt(metresNorth(59), 10f), DESTINATION, RADIUS_50, NOW);
        assertEquals(ArrivalState.ARRIVED, state);
    }

    @Test
    public void justBeyondTheWidenedRadiusIsNotArrived() {
        ArrivalState state = ArrivalEvaluator.evaluate(
                fixAt(metresNorth(62), 10f), DESTINATION, RADIUS_50, NOW);
        assertEquals(ArrivalState.APPROACHING, state);
    }

    /**
     * A good fix must not be beaten by a bad one. The same sixty metres counts as arrived with a
     * ten-metre accuracy and must not count with a one-metre accuracy - otherwise a worse reading would
     * make arrival easier, which is backwards.
     */
    @Test
    public void abetterFixIsStricterAboutArrivalNotLooser() {
        ArrivalState withTenMetres = ArrivalEvaluator.evaluate(
                fixAt(metresNorth(59), 10f), DESTINATION, RADIUS_50, NOW);
        ArrivalState withOneMetre = ArrivalEvaluator.evaluate(
                fixAt(metresNorth(59), 1f), DESTINATION, RADIUS_50, NOW);

        assertEquals(ArrivalState.ARRIVED, withTenMetres);
        assertEquals(ArrivalState.APPROACHING, withOneMetre);
    }

    // ------------------------------------------------------------------ readings that never count

    @Test
    public void noFixAtAllIsNoFix() {
        assertEquals(ArrivalState.NO_FIX,
                ArrivalEvaluator.evaluate(null, DESTINATION, RADIUS_50, NOW));
    }

    @Test
    public void noDestinationIsItsOwnStateNotOutside() {
        assertEquals(ArrivalState.NO_DESTINATION,
                ArrivalEvaluator.evaluate(fixAt(metresNorth(0), 10f), null, RADIUS_50, NOW));
    }

    /**
     * A fix with no accuracy value is refused. Zero would be the most flattering possible lie: it would
     * leave the effective radius at exactly fifty metres and make an unknown-quality reading look like
     * the best possible one.
     */
    @Test
    public void aFixWithNoAccuracyValueIsRefused() {
        AgeingFix fix = new AgeingFix(metresNorth(5).latitude(), metresNorth(5).longitude(), Float.NaN, NOW);
        assertEquals(ArrivalState.NO_FIX,
                ArrivalEvaluator.evaluate(fix, DESTINATION, RADIUS_50, NOW));
    }

    @Test
    public void aZeroAccuracyValueIsTreatedAsMissingNotPerfect() {
        AgeingFix fix = new AgeingFix(metresNorth(5).latitude(), metresNorth(5).longitude(), 0f, NOW);
        assertEquals(ArrivalState.NO_FIX,
                ArrivalEvaluator.evaluate(fix, DESTINATION, RADIUS_50, NOW));
    }

    /**
     * A reading older than the staleness limit is refused even when it says the user is right on the pin,
     * because a cached position from a minute ago on a moving vehicle describes somewhere else.
     */
    @Test
    public void aStaleFixIsRefusedEvenWhenItLooksPerfect() {
        AgeingFix fix = new AgeingFix(
                metresNorth(0).latitude(), metresNorth(0).longitude(), 5f,
                NOW - ArrivalEvaluator.MAX_FIX_AGE_MILLIS - 1);

        assertEquals(ArrivalState.NO_FIX,
                ArrivalEvaluator.evaluate(fix, DESTINATION, RADIUS_50, NOW));
    }

    @Test
    public void aFixExactlyAtTheStalenessLimitIsStillUsable() {
        AgeingFix fix = new AgeingFix(
                metresNorth(0).latitude(), metresNorth(0).longitude(), 5f,
                NOW - ArrivalEvaluator.MAX_FIX_AGE_MILLIS);

        assertEquals(ArrivalState.ARRIVED,
                ArrivalEvaluator.evaluate(fix, DESTINATION, RADIUS_50, NOW));
    }

    /**
     * A three-kilometre accuracy radius means "you are somewhere in this city". That is still worth
     * drawing on a map as a big circle, but it cannot be used to decide a specific shopfront was reached.
     */
    @Test
    public void aVeryImpreciseFixIsRefusedEvenWhenTheDistanceIsSmall() {
        AgeingFix fix = new AgeingFix(
                metresNorth(1).latitude(), metresNorth(1).longitude(),
                ArrivalEvaluator.MAX_TRUSTED_ACCURACY_M + 1f, NOW);

        assertEquals(ArrivalState.NO_FIX,
                ArrivalEvaluator.evaluate(fix, DESTINATION, RADIUS_50, NOW));
    }

    @Test
    public void aFixAtTheAccuracyCeilingIsStillUsable() {
        AgeingFix fix = new AgeingFix(
                metresNorth(0).latitude(), metresNorth(0).longitude(),
                ArrivalEvaluator.MAX_TRUSTED_ACCURACY_M, NOW);

        assertEquals(ArrivalState.ARRIVED,
                ArrivalEvaluator.evaluate(fix, DESTINATION, RADIUS_50, NOW));
    }

    @Test
    public void coordinatesOutsideTheGlobeAreRefused() {
        // A phone cannot be at latitude 91, so a reading saying so is corrupt rather than unusual.
        AgeingFix fix = new AgeingFix(91.0, 121.0, 5f, NOW);
        assertEquals(ArrivalState.NO_FIX,
                ArrivalEvaluator.evaluate(fix, DESTINATION, RADIUS_50, NOW));
    }

    @Test
    public void aNaNLatitudeIsRefused() {
        AgeingFix fix = new AgeingFix(Double.NaN, 121.0, 5f, NOW);
        assertEquals(ArrivalState.NO_FIX,
                ArrivalEvaluator.evaluate(fix, DESTINATION, RADIUS_50, NOW));
    }

    /**
     * A radius of zero would mean "stand exactly on the pin" with a real GPS, which is unsatisfiable. The
     * settings repository guarantees a usable radius, so reaching this means something upstream broke -
     * and refusing to decide is the safe way to report it.
     */
    @Test
    public void aNonsenseRadiusProducesNoFixRatherThanArrived() {
        assertEquals(ArrivalState.NO_FIX,
                ArrivalEvaluator.evaluate(fixAt(metresNorth(0), 5f), DESTINATION, 0, NOW));
        assertEquals(ArrivalState.NO_FIX,
                ArrivalEvaluator.evaluate(fixAt(metresNorth(0), 5f), DESTINATION, -10, NOW));
    }

    // ------------------------------------------------------------------ a configurable radius

    @Test
    public void aWiderConfiguredRadiusMakesArrivalEasier() {
        GeoCoordinate threeHundredMetresAway = fixAt(metresNorth(300), 10f).toCoordinate();

        // At 50 m the same position is outside; at 500 m it is comfortably inside. The radius is the one
        // setting the user can change, and this is what changing it does.
        assertEquals(ArrivalState.OUTSIDE,
                ArrivalEvaluator.evaluate(fixAt(threeHundredMetresAway, 10f), DESTINATION, RADIUS_50, NOW));
        assertEquals(ArrivalState.ARRIVED,
                ArrivalEvaluator.evaluate(fixAt(threeHundredMetresAway, 10f), DESTINATION, 500, NOW));
    }

    // ------------------------------------------------------------------ helpers

    private static AgeingFix fixAt(GeoCoordinate coordinate, float accuracyMetres) {
        return new AgeingFix(coordinate.latitude(), coordinate.longitude(), accuracyMetres, NOW);
    }

    /**
     * A location reading built for a test.
     *
     * <p>Points straight at {@link ArrivalEvaluator.LocationFixLike} rather than at the app's real
     * {@code LocationFix}, so a test can construct a reading that could never come off a real device -
     * a negative age, a NaN coordinate, an accuracy of exactly zero - without those possibilities having
     * to exist in production code.
     */
    private static final class AgeingFix implements ArrivalEvaluator.LocationFixLike {

        private final double latitude;
        private final double longitude;
        private final float accuracyMetres;
        private final long timestampMillis;

        AgeingFix(double latitude, double longitude, float accuracyMetres, long timestampMillis) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.accuracyMetres = accuracyMetres;
            this.timestampMillis = timestampMillis;
        }

        GeoCoordinate toCoordinate() {
            return new GeoCoordinate(latitude, longitude);
        }

        @Override
        public double latitude() {
            return latitude;
        }

        @Override
        public double longitude() {
            return longitude;
        }

        @Override
        public float accuracyMetres() {
            return accuracyMetres;
        }

        @Override
        public long timestampMillis() {
            return timestampMillis;
        }

        @Override
        public boolean hasAccuracy() {
            return !Float.isNaN(accuracyMetres) && accuracyMetres > 0f;
        }

        @Override
        public boolean hasUsableCoordinates() {
            return !Double.isNaN(latitude) && !Double.isNaN(longitude)
                    && latitude >= -90.0 && latitude <= 90.0
                    && longitude >= -180.0 && longitude <= 180.0;
        }

        @Override
        public boolean isStale(long nowMillis, long maxAgeMillis) {
            return (nowMillis - timestampMillis) > maxAgeMillis;
        }
    }
}

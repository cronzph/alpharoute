package com.cph.alpharoute.domain;

/**
 * The arrival decision - whether the driver has reached the store they are navigating to.
 *
 * <p><b>Pure Java, zero Android imports (AD-11), and that matters more here than anywhere else in the
 * app.</b> This class is the gate in front of the only action that changes a completion count. Every
 * other correctness property in the project has a second guard behind it: completion history has a
 * UNIQUE index (AD-5) and a foreign key (AD-6), so a double count is impossible even if the UI is wrong.
 * Arrival has no such backstop. If this decides wrongly, the app offers to count a store the user is not
 * standing in, and a yes to that dialog is a false record that nothing downstream can detect. So the
 * decision is written to be provable, and tested in {@code ArrivalEvaluatorTest} against real distances.
 *
 * <p><b>The accuracy circle widens the radius, and this is the whole design.</b> A GPS fix is not a
 * point; it is a point surrounded by a radius of 68% confidence. If the app compared the bare distance
 * against the bare arrival radius, it would be reasoning about a precision it does not have - a driver
 * shown as 45 m away with a 30 m accuracy might be 75 m away. So the effective radius is
 * {@code arrivalRadius + accuracy}: the app only claims arrival when the <i>worst plausible position</i>
 * is still inside the radius. That is the conservative direction, and the conservative direction is the
 * correct one when the cost of being wrong is a false completion.
 *
 * <p><b>A fix of unknown quality never counts.</b> Readings with no accuracy value, readings older than
 * the staleness limit, and readings with nonsense coordinates all produce {@link ArrivalState#NO_FIX}.
 * Being unable to tell is reported as being unable to tell.
 */
public final class ArrivalEvaluator {

    /**
     * How old a reading may be before it stops counting, in milliseconds.
     *
     * <p>Thirty seconds. The location provider asks for a fix every five seconds, so a reading this old
     * means updates have actually stopped - the service was killed, the permission was revoked, or the
     * phone lost its satellites. A cached position from a minute ago on a moving vehicle is a position
     * somewhere else entirely, and acting on it is how a store gets counted from the wrong street.
     */
    public static final long MAX_FIX_AGE_MILLIS = 30_000L;

    /**
     * The largest accuracy radius that may be trusted for an arrival, in metres.
     *
     * <p>A hundred metres is already generous - it is roughly a city block. Beyond that the reading is
     * telling the app "I am somewhere in this area" and the area is larger than the question being asked.
     * A fix that imprecise can still be shown on the map as a blue dot with a big circle, because a
     * rough position is better than none for drawing; it just cannot be used to decide that a specific
     * shopfront has been reached.
     */
    public static final float MAX_TRUSTED_ACCURACY_M = 100f;

    /**
     * A fix this far inside the radius begins reporting {@link ArrivalState#APPROACHING}, in metres.
     *
     * <p>It exists so the screen can say "nearly there" rather than switching from a long distance
     * straight to "you have arrived" with nothing in between. It changes no rule: crossing the radius
     * itself is still what produces {@link ArrivalState#ARRIVED}.
     */
    public static final int APPROACHING_MARGIN_M = 150;

    /** Utility class - never instantiated. */
    private ArrivalEvaluator() {
    }

    /**
     * Decides where the driver stands relative to the destination.
     *
     * @param fix               the current reading, or {@code null} if none has arrived
     * @param destination       where the drive ends, or {@code null} if nothing is being navigated to
     * @param arrivalRadiusMetres the configured radius, in metres
     * @param nowMillis         the current time, passed in rather than read from the clock so the
     *                          staleness rule is testable without waiting
     */
    public static ArrivalState evaluate(LocationFixLike fix,
                                        GeoCoordinate destination,
                                        int arrivalRadiusMetres,
                                        long nowMillis) {
        if (destination == null) {
            return ArrivalState.NO_DESTINATION;
        }
        if (fix == null || !isTrustworthy(fix, nowMillis)) {
            return ArrivalState.NO_FIX;
        }
        if (arrivalRadiusMetres <= 0) {
            // A radius of zero would mean "you must be exactly on the pin", which can never be satisfied
            // by a real GPS. Callers read this value from the settings repository, which guarantees a
            // usable one, so reaching here means something upstream is broken - and reporting "no fix"
            // rather than "arrived" keeps the failure on the safe side.
            return ArrivalState.NO_FIX;
        }

        double distance = DistanceCalculator.distanceMetres(
                fix.latitude(), fix.longitude(),
                destination.latitude(), destination.longitude());

        if (Double.isNaN(distance)) {
            return ArrivalState.NO_FIX;
        }

        double effectiveRadius = arrivalRadiusMetres + fix.accuracyMetres();
        if (distance <= effectiveRadius) {
            return ArrivalState.ARRIVED;
        }
        if (distance <= effectiveRadius + APPROACHING_MARGIN_M) {
            return ArrivalState.APPROACHING;
        }
        return ArrivalState.OUTSIDE;
    }

    /**
     * Whether a reading is fit to make a decision on.
     *
     * <p>Four questions, and all four have to pass. They are asked in the order that fails fastest, but
     * the order carries no meaning beyond that - each one is independently disqualifying.
     */
    private static boolean isTrustworthy(LocationFixLike fix, long nowMillis) {
        // 1. Are the coordinates real numbers inside the globe?
        if (!fix.hasUsableCoordinates()) {
            return false;
        }
        // 2. Did the reading carry an accuracy value at all? A missing one is not a good one; see
        //    LocationFix.hasAccuracy() for why zero is treated as "missing" rather than "perfect".
        if (!fix.hasAccuracy()) {
            return false;
        }
        // 3. Is it precise enough that "within the radius" means something? A 3 km fix could put the
        //    user anywhere in the city.
        if (fix.accuracyMetres() > MAX_TRUSTED_ACCURACY_M) {
            return false;
        }
        // 4. Is it recent enough to still describe where the user is?
        return !fix.isStale(nowMillis, MAX_FIX_AGE_MILLIS);
    }

    /**
     * The part of a location reading this class needs.
     *
     * <p><b>Why an interface rather than using {@code LocationFix} directly.</b> {@code LocationFix} is
     * already pure Java and could have been used as is. The interface exists so the arrival rules can be
     * tested against readings that are <i>deliberately awkward</i> - a fix claiming a perfect zero
     * accuracy, a fix from three minutes ago, a fix whose coordinates are NaN - without the test having
     * to construct a real object for each. {@code LocationFix} implements this interface directly, so
     * production code passes the real thing and nothing is duplicated.
     */
    public interface LocationFixLike {

        double latitude();

        double longitude();

        /** The accuracy radius in metres, or NaN when unknown. */
        float accuracyMetres();

        long timestampMillis();

        /** True when the reading carried a usable accuracy value. */
        boolean hasAccuracy();

        /** True when the coordinates are inside the globe. */
        boolean hasUsableCoordinates();

        /** True when the reading is older than the given age. */
        boolean isStale(long nowMillis, long maxAgeMillis);
    }
}

package com.cph.alpharoute.util;

import java.util.Locale;

/**
 * Turns a route's distance and duration into the text a person reads.
 *
 * <p>Pure Java with no Android imports (AD-11), so the rules can be tested on the JVM. That is the same
 * reasoning as {@link DateTimeHelper}: text that looks like a one-liner, where every interesting case is a
 * boundary, and where the boundaries are exactly what a reader skips past.
 *
 * <p><b>The boundaries, and why each one is here:</b>
 *
 * <ul>
 *   <li><b>999 m stays in metres, 1000 m becomes kilometres.</b> "0.9 km" for a store you can see is less
 *       useful than "900 m".</li>
 *   <li><b>59 s stays in seconds, 60 s becomes minutes.</b> Same idea at the other end.</li>
 *   <li><b>Minutes are rounded, and then the hours form is chosen from the rounded value.</b> Rounding
 *       after choosing the unit would let 3599 seconds print as "60 min" - a minute count no clock ever
 *       shows, and one that looks like a bug.</li>
 *   <li><b>A whole number of hours prints without a trailing "0 min".</b> "1 h" rather than "1 h 0 min",
 *       which reads as a rendering mistake.</li>
 * </ul>
 *
 * <p><b>{@link Locale#US} on every format, deliberately.</b> In a locale that writes decimals with a comma
 * - German, French, most of Europe - the default formatter turns 1.7 into "1,7", so a distance would read
 * "1,7 km". That is not wrong so much as confusing, and it is a one-word fix to avoid.
 */
public final class RouteTextFormatter {

    /** Utility class - never instantiated. */
    private RouteTextFormatter() {
    }

    private static final int METRES_PER_KILOMETRE = 1000;
    private static final int SECONDS_PER_MINUTE = 60;
    private static final int SECONDS_PER_HOUR = 3600;

    /**
     * A distance, in the unit that reads best for its size.
     *
     * @param metres whole metres, as {@code RoutePlan} provides
     */
    public static String distanceText(int metres) {
        if (metres < METRES_PER_KILOMETRE) {
            return metres + " m";
        }
        return String.format(Locale.US, "%.1f km", metres / (double) METRES_PER_KILOMETRE);
    }

    /**
     * A duration, in the unit that reads best for its size.
     *
     * @param seconds whole seconds, as {@code RoutePlan} provides
     */
    public static String durationText(int seconds) {
        if (seconds < SECONDS_PER_MINUTE) {
            return seconds + " s";
        }

        // Rounded to whole minutes BEFORE the unit is chosen, so the boundary cannot produce "60 min".
        int totalMinutes = Math.round(seconds / (float) SECONDS_PER_MINUTE);

        if (totalMinutes < SECONDS_PER_MINUTE) {
            return totalMinutes + " min";
        }

        int hours = totalMinutes / SECONDS_PER_MINUTE;
        int minutes = totalMinutes % SECONDS_PER_MINUTE;

        if (minutes == 0) {
            return hours + " h";
        }
        return hours + " h " + minutes + " min";
    }
}

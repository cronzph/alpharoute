package com.cph.alpharoute.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Locale;

/**
 * Unit tests for {@link RouteTextFormatter} - the distance and duration text on the Route Preview sheet.
 *
 * <p>These run on the JVM with no emulator, because the formatter is pure Java (AD-11).
 *
 * <p><b>Why boundaries and not just the happy path.</b> Every one of these numbers is decided by a
 * comparison against a constant, and each comparison has a case either side of it that a reader skips
 * past. The interesting ones are not "1734 m becomes 1.7 km" - they are 999 versus 1000, 59 versus 60, and
 * the rounding that would otherwise let a duration print as "60 min".
 */
public class RouteTextFormatterTest {

    // ------------------------------------------------------------------ distance

    @Test
    public void aShortDistanceIsShownInMetres() {
        assertEquals("450 m", RouteTextFormatter.distanceText(450));
    }

    @Test
    public void aDistanceJustUnderAKilometreStaysInMetres() {
        // "0.9 km" for a store you can almost see is less useful than "999 m".
        assertEquals("999 m", RouteTextFormatter.distanceText(999));
    }

    @Test
    public void aWholeKilometreBecomesKilometres() {
        assertEquals("1.0 km", RouteTextFormatter.distanceText(1000));
    }

    @Test
    public void theRealRouteDistanceIsShownInKilometres() {
        // The distance the live service returned for the Manila probe route in Phase 4.1.
        assertEquals("1.7 km", RouteTextFormatter.distanceText(1734));
    }

    @Test
    public void aLongDistanceIsStillKilometres() {
        // Somewhere across Metro Manila. It must not become "45300 m".
        assertEquals("45.3 km", RouteTextFormatter.distanceText(45300));
    }

    @Test
    public void aCommaDecimalLocaleStillProducesADot() {
        // In a locale that writes decimals with a comma the default formatter turns 1.7 into "1,7", so
        // the sheet would read "1,7 km". Not wrong so much as confusing - and a one-word fix to avoid.
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);

            assertEquals("1.7 km", RouteTextFormatter.distanceText(1734));
        } finally {
            Locale.setDefault(original);
        }
    }

    // ------------------------------------------------------------------ duration

    @Test
    public void aShortDurationIsShownInSeconds() {
        assertEquals("45 s", RouteTextFormatter.durationText(45));
    }

    @Test
    public void aDurationJustUnderAMinuteStaysInSeconds() {
        assertEquals("59 s", RouteTextFormatter.durationText(59));
    }

    @Test
    public void aWholeMinuteBecomesMinutes() {
        assertEquals("1 min", RouteTextFormatter.durationText(60));
    }

    @Test
    public void theRealRouteDurationIsShownInMinutes() {
        // 298 seconds, from the same live response.
        assertEquals("5 min", RouteTextFormatter.durationText(298));
    }

    @Test
    public void aDurationJustUnderAnHourNeverPrintsSixtyMinutes() {
        // The case the rounding order exists for. 3599 seconds is 59.98 minutes; rounding while still in
        // the minutes unit would print "60 min", which no clock shows and which reads as a bug.
        assertEquals("1 h", RouteTextFormatter.durationText(3599));
    }

    @Test
    public void aWholeHourPrintsWithoutATrailingZeroMinutes() {
        assertEquals("1 h", RouteTextFormatter.durationText(3600));
    }

    @Test
    public void anHourAndAQuarterPrintsAsHoursAndMinutes() {
        assertEquals("1 h 15 min", RouteTextFormatter.durationText(4500));
    }

    @Test
    public void aLongDrivePrintsAsHoursAndMinutes() {
        assertEquals("2 h 30 min", RouteTextFormatter.durationText(9000));
    }

    @Test
    public void zeroIsHandled() {
        // Not a real route, but a formatter that throws or prints nonsense on zero is a trap for whoever
        // uses it next. The parser refuses a zero-length route long before this.
        assertEquals("0 m", RouteTextFormatter.distanceText(0));
        assertEquals("0 s", RouteTextFormatter.durationText(0));
    }
}

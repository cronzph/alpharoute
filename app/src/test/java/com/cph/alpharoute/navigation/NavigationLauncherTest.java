package com.cph.alpharoute.navigation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.util.Locale;

/**
 * Unit tests for the coordinate formatting in {@link NavigationLauncher}.
 *
 * <p>These run on the JVM with no device and no navigation app installed. Only
 * {@code formatCoordinate} is checked - the Intent itself needs Android - and that is enough, because
 * this one line is where the two ways the hand-off can silently send a driver to the wrong place both
 * live.
 *
 * <p><b>The failure mode is what makes this worth testing.</b> A malformed coordinate does not throw. The
 * URI is still a valid URI, the navigation app still opens, and it still shows a route - to a point that
 * is not the store. Nothing in the app would ever report a problem.
 */
public class NavigationLauncherTest {

    @Test
    public void aManilaLatitudeBecomesAPlainDecimal() {
        assertEquals("14.599500", NavigationLauncher.formatCoordinate(14.5995));
    }

    @Test
    public void aNegativeCoordinateKeepsItsSign() {
        assertEquals("-77.036900", NavigationLauncher.formatCoordinate(-77.0369));
    }

    @Test
    public void aVerySmallValueDoesNotBecomeScientificNotation() {
        // Double.toString(0.00001) is "1.0E-5". The navigation URI cannot read that, and the app would
        // open at the wrong place rather than refuse.
        String formatted = NavigationLauncher.formatCoordinate(0.00001);

        assertEquals("0.000010", formatted);
        assertFalse("No exponent may reach the URI", formatted.contains("E"));
    }

    @Test
    public void aCommaDecimalLocaleStillProducesADot() {
        // The trap this test exists for. In a locale that writes decimals with a comma, the default
        // formatter turns 14.5995 into "14,599500" - which the URI reads as TWO values, sending the
        // driver somewhere else entirely. A phone set to German or French is an ordinary phone.
        //
        // The default locale is changed and put back in a finally. Gradle runs this module's tests in a
        // single JVM one after another, so nothing else observes the change.
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);

            assertEquals("14.599500", NavigationLauncher.formatCoordinate(14.5995));
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    public void everyCoordinateHasSixDecimalPlaces() {
        // Six decimals is about 11 cm, and a fixed width keeps the URI the same shape every time rather
        // than sometimes carrying an exponent and sometimes not.
        assertEquals("0.000000", NavigationLauncher.formatCoordinate(0.0));
        assertEquals("120.984200", NavigationLauncher.formatCoordinate(120.9842));
    }
}

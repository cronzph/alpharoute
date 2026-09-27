package com.cph.alpharoute.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.cph.alpharoute.util.GeoConstants;

import org.junit.Test;

/**
 * Unit tests for {@link ArrivalRadiusValidator} - what the user typed into the arrival-radius field.
 *
 * <p>These run on the JVM with no emulator, because the validator is pure Java (AD-11).
 *
 * <p><b>Why this is worth a test class.</b> The arrival radius decides whether the app ever offers to
 * complete a store. A radius that came out as <b>0</b> would mean "you must be standing exactly on the
 * pin", which no GPS reading can satisfy - so the app would appear to work perfectly and never offer a
 * completion, and nothing in the logs would say why. The user would be left with a tracker that never
 * tracks.
 *
 * <p>The other reason is that this is the only place in the app where a <b>typed number</b> is trusted.
 * Every other value is chosen from a map or a fixed set. So the parsing edge cases - blank, spaces,
 * fractions, negatives, and numbers too large for an {@code int} - all belong here, checked once,
 * rather than being rediscovered by a user whose radius silently refused to save.
 */
public class ArrivalRadiusValidatorTest {

    // ------------------------------------------------------------------ accepted

    @Test
    public void aPlainNumberOfMetresIsAccepted() {
        assertEquals(Integer.valueOf(50), ArrivalRadiusValidator.parseMetres("50"));
    }

    @Test
    public void theSmallestAllowedRadiusIsAccepted() {
        assertEquals(Integer.valueOf(GeoConstants.MIN_ARRIVAL_RADIUS_M),
                ArrivalRadiusValidator.parseMetres(String.valueOf(GeoConstants.MIN_ARRIVAL_RADIUS_M)));
    }

    @Test
    public void theLargestAllowedRadiusIsAccepted() {
        assertEquals(Integer.valueOf(GeoConstants.MAX_ARRIVAL_RADIUS_M),
                ArrivalRadiusValidator.parseMetres(String.valueOf(GeoConstants.MAX_ARRIVAL_RADIUS_M)));
    }

    @Test
    public void theAppDefaultIsItselfAccepted() {
        // A default that the app's own parser would reject would be an embarrassing contradiction.
        assertEquals(Integer.valueOf(GeoConstants.DEFAULT_ARRIVAL_RADIUS_M),
                ArrivalRadiusValidator.parseMetres(
                        String.valueOf(GeoConstants.DEFAULT_ARRIVAL_RADIUS_M)));
    }

    @Test
    public void surroundingWhitespaceIsIgnored() {
        // A value pasted from somewhere else often carries a space. Rejecting it as "not a number" would
        // be technically true and completely unhelpful.
        assertEquals(Integer.valueOf(50), ArrivalRadiusValidator.parseMetres("  50  "));
    }

    // ------------------------------------------------------------------ the radius that must never get through

    @Test
    public void zeroIsRejected() {
        // The headline case: a zero radius requires exact coordinate equality, which the specification
        // forbids and GPS cannot deliver. This is why zero must not be usable.
        assertNull(ArrivalRadiusValidator.parseMetres("0"));
    }

    @Test
    public void aNegativeRadiusIsRejected() {
        assertNull(ArrivalRadiusValidator.parseMetres("-50"));
    }

    @Test
    public void aRadiusJustBelowTheMinimumIsRejected() {
        assertNull(ArrivalRadiusValidator.parseMetres(
                String.valueOf(GeoConstants.MIN_ARRIVAL_RADIUS_M - 1)));
    }

    @Test
    public void aRadiusJustAboveTheMaximumIsRejected() {
        assertNull(ArrivalRadiusValidator.parseMetres(
                String.valueOf(GeoConstants.MAX_ARRIVAL_RADIUS_M + 1)));
    }

    // ------------------------------------------------------------------ not a number at all

    @Test
    public void nothingAtAllIsRejected() {
        assertNull(ArrivalRadiusValidator.parseMetres(null));
    }

    @Test
    public void anEmptyStringIsRejected() {
        assertNull(ArrivalRadiusValidator.parseMetres(""));
    }

    @Test
    public void whitespaceOnlyIsRejected() {
        assertNull(ArrivalRadiusValidator.parseMetres("   "));
    }

    @Test
    public void aWordIsRejected() {
        assertNull(ArrivalRadiusValidator.parseMetres("fifty"));
    }

    @Test
    public void aFractionIsRejected() {
        // The setting is whole metres. "50.5" is not rounded - it is refused, so the user is told rather
        // than quietly given a value they did not type.
        assertNull(ArrivalRadiusValidator.parseMetres("50.5"));
    }

    // ------------------------------------------------------------------ the overflow case

    @Test
    public void aNumberTooLargeForAnIntIsRejected() {
        // Integer.parseInt throws for this rather than wrapping round to some other number, which is what
        // makes it safe to parse inside the same try as the range check. A wrapping parse could have
        // produced a value that happened to land inside 10..1000, and the user would have saved a radius
        // they never typed.
        assertNull(ArrivalRadiusValidator.parseMetres("999999999999"));
    }

    @Test
    public void theLargestPossibleIntIsStillOutOfRange() {
        // Parses successfully, then fails the range check - the other half of the overflow story.
        assertNull(ArrivalRadiusValidator.parseMetres(String.valueOf(Integer.MAX_VALUE)));
    }
}

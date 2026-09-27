package com.cph.alpharoute.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests for {@link GeoConstants} - the range rules the whole app depends on.
 *
 * <p>These run on the JVM with no emulator, because {@code GeoConstants} is pure Java.
 *
 * <p>Why these specific rules matter enough to test:
 *
 * <ul>
 *   <li>The <b>arrival radius</b> decides whether the completion dialog is ever offered. A radius that
 *       silently accepted 0 would mean "you must stand exactly on the pin", which GPS can never
 *       satisfy - the app would appear to be broken in the field.</li>
 *   <li>The <b>coordinate bounds</b> are the only thing standing between a mis-tap and a marker placed
 *       somewhere impossible. {@code StoreValidator} and {@code StoreRepository} both call them.</li>
 * </ul>
 */
public class GeoConstantsTest {

    // ---------------------------------------------------------------- arrival radius

    @Test
    public void theDefaultArrivalRadiusIsFiftyMetres() {
        assertEquals("The specification recommends 50 m as the starting radius",
                50, GeoConstants.DEFAULT_ARRIVAL_RADIUS_M);
    }

    @Test
    public void theDefaultArrivalRadiusIsItselfValid() {
        assertTrue("The default must satisfy the app's own rule",
                GeoConstants.isValidArrivalRadius(GeoConstants.DEFAULT_ARRIVAL_RADIUS_M));
    }

    @Test
    public void arrivalRadiusAtTheMinimumIsAccepted() {
        assertTrue(GeoConstants.isValidArrivalRadius(GeoConstants.MIN_ARRIVAL_RADIUS_M));
    }

    @Test
    public void arrivalRadiusAtTheMaximumIsAccepted() {
        assertTrue(GeoConstants.isValidArrivalRadius(GeoConstants.MAX_ARRIVAL_RADIUS_M));
    }

    @Test
    public void arrivalRadiusJustBelowTheMinimumIsRejected() {
        assertFalse(GeoConstants.isValidArrivalRadius(GeoConstants.MIN_ARRIVAL_RADIUS_M - 1));
    }

    @Test
    public void arrivalRadiusJustAboveTheMaximumIsRejected() {
        assertFalse(GeoConstants.isValidArrivalRadius(GeoConstants.MAX_ARRIVAL_RADIUS_M + 1));
    }

    @Test
    public void zeroArrivalRadiusIsRejected() {
        // This is the important one: a zero radius would require exact coordinate equality, which the
        // specification forbids and GPS cannot deliver.
        assertFalse(GeoConstants.isValidArrivalRadius(0));
    }

    @Test
    public void negativeArrivalRadiusIsRejected() {
        assertFalse(GeoConstants.isValidArrivalRadius(-50));
    }

    @Test
    public void anAbsurdlyLargeArrivalRadiusIsRejected() {
        assertFalse(GeoConstants.isValidArrivalRadius(Integer.MAX_VALUE));
    }

    // ---------------------------------------------------------------- latitude

    @Test
    public void latitudeBoundsAreInclusive() {
        assertTrue("The equator and the poles are real places",
                GeoConstants.isValidLatitude(-90.0));
        assertTrue(GeoConstants.isValidLatitude(0.0));
        assertTrue(GeoConstants.isValidLatitude(90.0));
    }

    @Test
    public void latitudeOutsideTheGlobeIsRejected() {
        assertFalse(GeoConstants.isValidLatitude(-90.0001));
        assertFalse(GeoConstants.isValidLatitude(90.0001));
    }

    @Test
    public void aManilaLatitudeIsAccepted() {
        assertTrue(GeoConstants.isValidLatitude(14.5995));
    }

    // ---------------------------------------------------------------- longitude

    @Test
    public void longitudeBoundsAreInclusive() {
        assertTrue(GeoConstants.isValidLongitude(-180.0));
        assertTrue(GeoConstants.isValidLongitude(0.0));
        assertTrue(GeoConstants.isValidLongitude(180.0));
    }

    @Test
    public void longitudeOutsideTheGlobeIsRejected() {
        assertFalse(GeoConstants.isValidLongitude(-180.0001));
        assertFalse(GeoConstants.isValidLongitude(180.0001));
    }

    @Test
    public void aManilaLongitudeIsAccepted() {
        assertTrue(GeoConstants.isValidLongitude(120.9842));
    }

    // ---------------------------------------------------------------- earth radius

    @Test
    public void theEarthRadiusIsPlausible() {
        // Guards against a typo such as 6371 instead of 6371008.8, which would make every distance in
        // the app a thousand times too small and the arrival check useless.
        assertTrue("The mean Earth radius is about 6.371 million metres",
                GeoConstants.EARTH_RADIUS_M > 6_300_000);
        assertTrue(GeoConstants.EARTH_RADIUS_M < 6_500_000);
    }

    // ---------------------------------------------------------------- range sanity

    @Test
    public void theArrivalRadiusBoundsAreTheRightWayRound() {
        assertTrue("MIN must be below MAX, or every radius would be rejected",
                GeoConstants.MIN_ARRIVAL_RADIUS_M < GeoConstants.MAX_ARRIVAL_RADIUS_M);
    }
}

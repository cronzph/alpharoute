package com.cph.alpharoute.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The haversine calculation, checked against distances that are known independently.
 *
 * <p><b>These tests deliberately use numbers that can be verified without trusting this code.</b>
 * Distances along a meridian are the cleanest case: one degree of latitude is very close to 111.2 km
 * everywhere on Earth, because lines of longitude converge but lines of latitude do not. A test that
 * asserted whatever this class happened to return would prove nothing at all.
 *
 * <p>Pure JVM tests: no device, no map, no Android (AD-11).
 */
public class DistanceCalculatorTest {

    /** One degree of latitude, in metres, on a sphere of the radius this app uses. */
    private static final double ONE_DEGREE_LATITUDE_M = 111_195.0;

    /** A tolerance wide enough for the sphere-versus-ellipsoid difference and narrow enough to catch a bug. */
    private static final double TOLERANCE_M = 50.0;

    // ------------------------------------------------------------------ known distances

    @Test
    public void oneDegreeOfLatitudeIsAboutOneHundredAndElevenKilometres() {
        double metres = DistanceCalculator.distanceMetres(14.0, 121.0, 15.0, 121.0);
        assertEquals(ONE_DEGREE_LATITUDE_M, metres, TOLERANCE_M);
    }

    @Test
    public void theSamePointIsZeroMetresAway() {
        double metres = DistanceCalculator.distanceMetres(14.5995, 120.9842, 14.5995, 120.9842);
        assertEquals(0.0, metres, 0.001);
    }

    /**
     * The classic worked example from the haversine literature, and the reason it is worth having: it is
     * a pair of points whose true separation is published independently of any code.
     */
    @Test
    public void thePublishedHaversineExampleMatches() {
        // Manila to Cebu City.
        double metres = DistanceCalculator.distanceMetres(14.5995, 120.9842, 10.3157, 123.8854);
        // About 570 km. The tolerance below is 3 km, which is 0.5% - tight enough that a wrong Earth
        // radius, a degrees/radians slip, or a swapped pair would all fail it.
        assertEquals(570_000.0, metres, 3_000.0);
    }

    /**
     * <b>Longitude is not latitude.</b> This is the test that catches the one mistake this class is most
     * likely to be given by a caller: the routing service sends {@code [longitude, latitude]} and the
     * whole app is built around swapping it exactly once, at each edge.
     *
     * <p>The right-hand pair here is the interesting part. Swap the order and the "latitude" becomes 121,
     * which is not a place on Earth - so this class refuses to answer rather than returning a number.
     * That refusal is what makes the coordinate order a loud bug instead of a quiet one: a caller that
     * forgot to swap gets {@code NaN}, which propagates into "cannot tell" and never into "arrived".
     */
    @Test
    public void swappingLatitudeAndLongitudeIsRefusedRatherThanAnswered() {
        double correct = DistanceCalculator.distanceMetres(14.0, 121.0, 14.0, 121.1);
        // A tenth of a degree of longitude at 14 degrees north: 11.1 km x 0.1 x cos(14 degrees).
        assertEquals(10_789.0, correct, 20.0);

        // Latitude 121 does not exist, so this is not a distance - it is a bug report.
        double swapped = DistanceCalculator.distanceMetres(121.0, 14.0, 121.1, 14.0);
        assertTrue("A swapped pair must be refused, not answered with a plausible number",
                Double.isNaN(swapped));
    }

    /**
     * One degree of longitude is about 111 km at the equator and shrinks towards the poles, because the
     * lines converge. The Philippines sits near 14 degrees north, where it is about 108 km. If the
     * cosine term were dropped from the formula, this test would fail while the latitude one still passed.
     */
    @Test
    public void oneDegreeOfLongitudeShrinksWithLatitude() {
        double atEquator = DistanceCalculator.distanceMetres(0.0, 121.0, 0.0, 122.0);
        double atManila = DistanceCalculator.distanceMetres(14.5995, 121.0, 14.5995, 122.0);

        assertEquals(ONE_DEGREE_LATITUDE_M, atEquator, TOLERANCE_M);
        assertTrue("Longitude degrees must be shorter away from the equator", atManila < atEquator);
        // cos(14.5995 degrees) is about 0.968, so just under 108 km.
        assertEquals(107_600.0, atManila, 500.0);
    }

    @Test
    public void distanceIsSymmetric() {
        double forwards = DistanceCalculator.distanceMetres(14.5995, 120.9842, 14.6095, 121.0042);
        double backwards = DistanceCalculator.distanceMetres(14.6095, 121.0042, 14.5995, 120.9842);
        assertEquals(forwards, backwards, 0.001);
    }

    @Test
    public void distanceAcceptsAnIdentityItCannotDistinguish() {
        // Two points differing by less than a micrometre are "zero apart" as far as a double is
        // concerned. Documented rather than fixed: no GPS on Earth can tell them apart.
        double metres = DistanceCalculator.distanceMetres(14.5995, 120.9842, 14.5995, 120.9842);
        assertFalse(Double.isNaN(metres));
    }

    // ------------------------------------------------------------------ the 50 m arrival radius

    /**
     * A sanity check at the scale that actually matters. Twenty metres along a meridian should come back
     * as twenty metres, because twenty metres is the difference between "you have arrived" and "you have
     * not" at the default radius of fifty.
     */
    @Test
    public void twentyMetresIsTwentyMetres() {
        double metres = DistanceCalculator.distanceMetres(14.5995, 120.9842, 14.5996797, 120.9842);
        assertEquals(20.0, metres, 0.5);
    }

    // ------------------------------------------------------------------ unusable input

    @Test
    public void anUnusableCoordinateGivesNotANumberAndNotZero() {
        assertTrue(Double.isNaN(DistanceCalculator.distanceMetres(Double.NaN, 121.0, 14.0, 121.0)));
        assertTrue(Double.isNaN(DistanceCalculator.distanceMetres(14.0, 121.0, 14.0, Double.NaN)));
    }

    /**
     * <b>The most important test in this file.</b> An out-of-range latitude must never be silently
     * accepted. If it were, the trigonometry could still produce a small number for a nonsense input, and
     * a small number means "arrived".
     */
    @Test
    public void anOutOfRangeCoordinateIsRejectedRatherThanClamped() {
        assertTrue(Double.isNaN(DistanceCalculator.distanceMetres(91.0, 121.0, 14.0, 121.0)));
        assertTrue(Double.isNaN(DistanceCalculator.distanceMetres(-91.0, 121.0, 14.0, 121.0)));
        assertTrue(Double.isNaN(DistanceCalculator.distanceMetres(14.0, 181.0, 14.0, 121.0)));
        assertTrue(Double.isNaN(DistanceCalculator.distanceMetres(14.0, -181.0, 14.0, 121.0)));
    }

    @Test
    public void aNullCoordinateReturnsNotANumberRatherThanThrowing() {
        GeoCoordinate point = new GeoCoordinate(14.0, 121.0);
        assertTrue(Double.isNaN(DistanceCalculator.distanceMetres(point, null)));
        assertTrue(Double.isNaN(DistanceCalculator.distanceMetres(null, point)));
        assertTrue(Double.isNaN(DistanceCalculator.distanceMetres((GeoCoordinate) null, null)));
    }

    @Test
    public void theCoordinateOverloadAgreesWithThePrimitiveOne() {
        double direct = DistanceCalculator.distanceMetres(14.5995, 120.9842, 14.6095, 121.0042);
        double viaObjects = DistanceCalculator.distanceMetres(
                new GeoCoordinate(14.5995, 120.9842),
                new GeoCoordinate(14.6095, 121.0042));
        assertEquals(direct, viaObjects, 0.0001);
    }

    // ------------------------------------------------------------------ the bounds are inclusive

    @Test
    public void theEdgeOfTheGlobeIsUsable() {
        assertFalse(Double.isNaN(DistanceCalculator.distanceMetres(90.0, 180.0, -90.0, -180.0)));
        assertFalse(Double.isNaN(DistanceCalculator.distanceMetres(0.0, 0.0, 0.0, 0.0)));
    }
}

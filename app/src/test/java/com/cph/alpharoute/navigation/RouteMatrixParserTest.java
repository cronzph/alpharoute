package com.cph.alpharoute.navigation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Reading the matrix service's answer.
 *
 * <p>Pure JVM. The trap this file exists for is the grid width: the distances come back as a
 * {@code sources × destinations} grid, and a row of the wrong length would pair each distance with the
 * wrong store - a wrong answer that looks exactly like a right one.
 */
public class RouteMatrixParserTest {

    /**
     * One source, three destinations. The shape the app always sends.
     *
     * <p><b>There is no leading zero, and that matters.</b> The grid is {@code sources × destinations}, so
     * with one source the row holds one distance per store - there is no "distance to yourself" entry,
     * because the source is not one of the destinations. A fixture with a leading zero would model a store
     * sitting exactly where the user is, and {@code indexOfNearest} would correctly pick that store.
     *
     * <p>The distances are deliberately not in order, so "nearest" is not simply "first".
     */
    private static final String THREE_DESTINATIONS = "{"
            + "\"durations\":[[280,120,340]],"
            + "\"distances\":[[3336,1112,2152]],"
            + "\"destinations\":["
            + "  {\"location\":[121.01,14.62],\"snapped_distance\":2.4},"
            + "  {\"location\":[120.98,14.61],\"snapped_distance\":3.2},"
            + "  {\"location\":[121.00,14.60],\"snapped_distance\":1.1}],"
            + "\"sources\":[{\"location\":[120.98,14.60],\"snapped_distance\":0.9}]}";

    // ------------------------------------------------------------------ the happy path

    @Test
    public void theDistancesRowIsReadInOrder() {
        // The order matches the order the destinations were sent, which is what lets the caller pair each
        // distance with its store.
        double[] distances = RouteMatrixParser.parseDistances(THREE_DESTINATIONS, 3);

        assertEquals(3, distances.length);
        assertEquals(3336.0, distances[0], 0.5);
        assertEquals(1112.0, distances[1], 0.5);
        assertEquals(2152.0, distances[2], 0.5);
    }

    @Test
    public void theNearestIsFound() {
        // Index 1 is 1,112 m - the smallest, and deliberately not the first.
        double[] distances = RouteMatrixParser.parseDistances(THREE_DESTINATIONS, 3);

        assertEquals(1, RouteMatrixParser.indexOfNearest(distances));
    }

    @Test
    public void theRowHasSomethingUsable() {
        assertTrue(RouteMatrixParser.hasAnyUsable(
                RouteMatrixParser.parseDistances(THREE_DESTINATIONS, 3)));
    }

    // ------------------------------------------------------------------ the width check

    @Test
    public void aRowOfTheWrongWidthIsRefused() {
        // The critical check. Returning a short row would pair each distance with the wrong store, and the
        // suggestion would confidently name a shop that is not the nearest.
        assertNull(RouteMatrixParser.parseDistances(THREE_DESTINATIONS, 5));
        assertNull(RouteMatrixParser.parseDistances(THREE_DESTINATIONS, 1));
    }

    @Test
    public void anImpossibleCountIsRefused() {
        assertNull(RouteMatrixParser.parseDistances(THREE_DESTINATIONS, 0));
        assertNull(RouteMatrixParser.parseDistances(THREE_DESTINATIONS, -1));
    }

    // ------------------------------------------------------------------ unusable legs

    @Test
    public void anUnreachableLegIsNaNNotZero() {
        // The service returns null for a leg it cannot compute. A zero here would mean "this store is where
        // you are standing", and would win every comparison.
        String body = "{\"distances\":[[1112,null,2152]]}";

        double[] distances = RouteMatrixParser.parseDistances(body, 3);

        assertTrue(Double.isNaN(distances[1]));
        assertFalse(Double.isNaN(distances[2]));
    }

    @Test
    public void theNearestIgnoresUnusableLegs() {
        // The NaN is at index 0. If it were treated as a zero it would win; expecting index 1 proves it is
        // skipped rather than minimised.
        String body = "{\"distances\":[[null,1112,2152]]}";

        double[] distances = RouteMatrixParser.parseDistances(body, 3);

        assertEquals(1, RouteMatrixParser.indexOfNearest(distances));
    }

    @Test
    public void aRowOfOnlyNaNIsNotUsable() {
        // The caller falls back to straight lines rather than showing a suggestion built from no
        // measurements at all.
        String body = "{\"distances\":[[null,null]]}";

        assertFalse(RouteMatrixParser.hasAnyUsable(RouteMatrixParser.parseDistances(body, 2)));
    }

    @Test
    public void noNearestIsFoundInAnEmptyOrAllNaNRow() {
        assertEquals(-1, RouteMatrixParser.indexOfNearest(null));
        assertEquals(-1, RouteMatrixParser.indexOfNearest(new double[0]));
        assertEquals(-1, RouteMatrixParser.indexOfNearest(new double[]{Double.NaN, Double.NaN}));
    }

    @Test
    public void tiesAreBrokenByTheLowestIndex() {
        // The same tie-break the pure strategies use, so the choice does not depend on iteration order.
        double[] distances = new double[]{500.0, 500.0, 900.0};

        assertEquals(0, RouteMatrixParser.indexOfNearest(distances));
    }

    // ------------------------------------------------------------------ the refusals

    @Test
    public void anEmptyOrMissingBodyGivesNull() {
        assertNull(RouteMatrixParser.parseDistances(null, 3));
        assertNull(RouteMatrixParser.parseDistances("", 3));
        assertNull(RouteMatrixParser.parseDistances("   ", 3));
    }

    @Test
    public void aBodyWithNoDistancesGivesNull() {
        assertNull(RouteMatrixParser.parseDistances("{\"durations\":[[0,1]]}", 2));
    }

    @Test
    public void anEmptyGridGivesNull() {
        assertNull(RouteMatrixParser.parseDistances("{\"distances\":[]}", 1));
    }

    @Test
    public void malformedJsonGivesNullRatherThanThrowing() {
        assertNull(RouteMatrixParser.parseDistances("not json", 2));
    }

    // ------------------------------------------------------------------ the copy

    @Test
    public void copyOfProtectsTheOriginal() {
        // A caller cannot mutate what it was handed, so a cached row cannot be altered by accident.
        double[] original = new double[]{1.0, 2.0};
        double[] copy = RouteMatrixParser.copyOf(original);

        copy[0] = 99.0;

        assertEquals(1.0, original[0], 0.0001);
    }
}

package com.cph.alpharoute.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The road-distance rule.
 *
 * <p>Pure JVM (AD-11). Two things are being asserted, and the second matters more than the first:
 *
 * <ol>
 *   <li><b>It uses the road distance when there is one.</b></li>
 *   <li><b>It still answers when there is not.</b> The app works offline, so a strategy that only existed
 *       when the network did would be worse than the straight-line one it replaced.</li>
 * </ol>
 */
public class RoadDistanceStrategyTest {

    private static final GeoCoordinate CURRENT = new GeoCoordinate(14.6000, 120.9800);

    /** 1,112.0 m north - the NEAREST by straight line. */
    private static final StoreCandidate STORE_NORTH =
            new StoreCandidate(1, "N-01", "North", 14.6100, 120.9800);

    /** 2,152.1 m east - further by straight line, but on the way home. */
    private static final StoreCandidate STORE_EAST =
            new StoreCandidate(2, "E-01", "East", 14.6000, 121.0000);

    private static final GeoCoordinate HOME_EAST = new GeoCoordinate(14.6000, 121.0300);

    private final RouteSuggestionStrategy strategy = new RoadDistanceStrategy();

    private static List<StoreCandidate> list(StoreCandidate... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    private static Map<Long, Double> roads(Object... pairs) {
        Map<Long, Double> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((Long) pairs[i], (Double) pairs[i + 1]);
        }
        return map;
    }

    // ------------------------------------------------------------------ the point of the strategy

    @Test
    public void itUsesTheRoadDistanceNotTheStraightLine() {
        // THE test. North is 1,112 m away in a straight line and East is 2,152 m - so a straight-line rule
        // picks North. But the road to North is 5,000 m (a river in the way) and the road to East is
        // 2,400 m. The road-distance rule picks East, and that is the entire purpose of this phase.
        SuggestionContext context = new SuggestionContext(
                list(STORE_NORTH, STORE_EAST),
                2,
                CURRENT,
                HOME_EAST,
                roads(1L, 5000.0, 2L, 2400.0));

        SuggestionResult result = strategy.suggest(context);

        assertEquals(2L, result.store().id());
        assertEquals(SuggestionResult.Reason.ROAD_DISTANCE, result.reason());
        assertEquals(2400.0, result.distanceMetres(), 0.5);
    }

    @Test
    public void theReportedDistanceIsTheRoadDistance() {
        // Not the straight line. The card shows the number the decision was made on, so the user sees the
        // same figure the app did.
        SuggestionContext context = new SuggestionContext(
                list(STORE_NORTH), 1, CURRENT, HOME_EAST, roads(1L, 5000.0));

        SuggestionResult result = strategy.suggest(context);

        assertEquals(5000.0, result.distanceMetres(), 0.5);
    }

    @Test
    public void theReasonSaysItIsARoadDistance() {
        // A road distance and a straight line are different claims about the world. The card must not
        // present one as the other.
        SuggestionContext context = new SuggestionContext(
                list(STORE_NORTH), 1, CURRENT, HOME_EAST, roads(1L, 1200.0));

        assertEquals(SuggestionResult.Reason.ROAD_DISTANCE, strategy.suggest(context).reason());
    }

    // ------------------------------------------------------------------ the offline fallback

    @Test
    public void withNoRoadDistancesItFallsBackToTheHomeAwareRule() {
        // Offline, or the matrix has not answered yet. The Phase 7 behaviour, unchanged - so the app is
        // never worse off than it was before this phase.
        SuggestionContext context = new SuggestionContext(
                list(STORE_NORTH, STORE_EAST), 2, CURRENT, HOME_EAST);

        SuggestionResult result = strategy.suggest(context);

        assertEquals(SuggestionResult.Reason.HOME_AWARE, result.reason());
        assertEquals(2L, result.store().id());
    }

    @Test
    public void withNoRoadDistancesAndNoHomeItFallsBackToTheNearestRule() {
        SuggestionContext context = new SuggestionContext(
                list(STORE_NORTH, STORE_EAST), 2, CURRENT, null);

        SuggestionResult result = strategy.suggest(context);

        assertEquals(SuggestionResult.Reason.NEAREST, result.reason());
        assertEquals(1L, result.store().id());
    }

    @Test
    public void withNoReferenceAtAllItStillAnswers() {
        SuggestionContext context = new SuggestionContext(list(STORE_NORTH), 1, null, null);

        SuggestionResult result = strategy.suggest(context);

        assertEquals(SuggestionResult.Status.SUGGESTED, result.status());
        assertTrue(result.hasStore());
    }

    @Test
    public void anEmptyRoadDistanceMapIsTreatedAsNoRoadDistances() {
        SuggestionContext context = new SuggestionContext(
                list(STORE_NORTH, STORE_EAST), 2, CURRENT, HOME_EAST, Collections.emptyMap());

        assertFalse(context.hasRoadDistances());
        assertEquals(SuggestionResult.Reason.HOME_AWARE, strategy.suggest(context).reason());
    }

    // ------------------------------------------------------------------ a partial set

    @Test
    public void aStoreWithNoRoadDistanceFallsBackToItsStraightLine() {
        // The matrix answers for every store in one request, so in practice either all have road distances
        // or none do. But a partial set is handled rather than dropped: refusing to consider a store would
        // be worse than measuring it approximately.
        SuggestionContext context = new SuggestionContext(
                list(STORE_NORTH, STORE_EAST),
                2,
                CURRENT,
                HOME_EAST,
                roads(2L, 9000.0));   // only East has a road distance

        SuggestionResult result = strategy.suggest(context);

        // North has no road distance, so its straight line (1,112 m) is used, and it wins against East's
        // 9,000 m road distance.
        assertEquals(1L, result.store().id());
        assertEquals(SuggestionResult.Reason.NEAREST, result.reason());
    }

    @Test
    public void aStoreWithAnUnusableRoadDistanceFallsBackToItsStraightLine() {
        SuggestionContext context = new SuggestionContext(
                list(STORE_NORTH, STORE_EAST),
                2,
                CURRENT,
                HOME_EAST,
                roads(1L, Double.NaN, 2L, 9000.0));

        SuggestionResult result = strategy.suggest(context);

        assertEquals(1L, result.store().id());
        assertEquals(SuggestionResult.Reason.NEAREST, result.reason());
    }

    // ------------------------------------------------------------------ the terminal states

    @Test
    public void noStoresAtAllReportsNoStores() {
        SuggestionContext context = new SuggestionContext(
                Collections.emptyList(), 0, CURRENT, HOME_EAST, roads(1L, 100.0));

        assertEquals(SuggestionResult.Status.NO_STORES, strategy.suggest(context).status());
    }

    @Test
    public void nothingPendingReportsRouteComplete() {
        SuggestionContext context = new SuggestionContext(
                Collections.emptyList(), 4, CURRENT, HOME_EAST, roads(1L, 100.0));

        assertEquals(SuggestionResult.Status.ROUTE_COMPLETE, strategy.suggest(context).status());
    }

    // ------------------------------------------------------------------ defensive

    @Test
    public void tiesAreBrokenByTheLowestId() {
        SuggestionContext context = new SuggestionContext(
                list(STORE_EAST, STORE_NORTH),
                2,
                CURRENT,
                HOME_EAST,
                roads(1L, 1000.0, 2L, 1000.0));

        assertEquals(1L, strategy.suggest(context).store().id());
    }

    @Test
    public void nullCandidatesAreSkippedRatherThanThrowing() {
        List<StoreCandidate> withNull = new ArrayList<>();
        withNull.add(null);
        withNull.add(STORE_NORTH);

        SuggestionContext context = new SuggestionContext(
                withNull, 1, CURRENT, HOME_EAST, roads(1L, 1200.0));

        assertEquals(1L, strategy.suggest(context).store().id());
    }
}

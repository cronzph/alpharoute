package com.cph.alpharoute.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The Home-aware rule.
 *
 * <p>Pure JVM (AD-11). Every distance below was computed independently with the same earth radius
 * {@code GeoConstants} uses (6,371,008.8 m).
 *
 * <p>The test that matters most is {@link #itPicksADifferentStoreFromTheNearestOne()}: the whole reason
 * this strategy exists is that it can disagree with the nearest store. If it ever agreed on that layout,
 * the formula would not be doing anything.
 */
public class HomeAwareStrategyTest {

    private static final double TOLERANCE_M = 2.0;

    private static final GeoCoordinate CURRENT = new GeoCoordinate(14.6000, 120.9800);

    /** 1,112.0 m north of the current position - the NEAREST store. */
    private static final StoreCandidate STORE_NORTH =
            new StoreCandidate(1, "N-01", "North", 14.6100, 120.9800);

    /** 2,152.1 m east of the current position - further away, but on the way home. */
    private static final StoreCandidate STORE_EAST =
            new StoreCandidate(2, "E-01", "East", 14.6000, 121.0000);

    /** Home is to the EAST, so the eastern store is the one on the way back. */
    private static final GeoCoordinate HOME_EAST = new GeoCoordinate(14.6000, 121.0300);

    private final RouteSuggestionStrategy strategy = new HomeAwareStrategy();

    private static List<StoreCandidate> list(StoreCandidate... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    // ------------------------------------------------------------------ the point of the strategy

    @Test
    public void itPicksADifferentStoreFromTheNearestOne() {
        // THE test. North is 1,112.0 m away and East is 2,152.1 m, so a nearest-first rule picks North.
        // With Home to the east, the scores are:
        //   North: 1,112.0 + 5,493.8 = 6,605.8
        //   East:  2,152.1 + 3,228.1 = 5,380.2
        // East wins, and that is the entire purpose of this strategy.
        SuggestionResult nearest = new NearestStrategy().suggest(
                new SuggestionContext(list(STORE_NORTH, STORE_EAST), 2, CURRENT, HOME_EAST));
        SuggestionResult homeAware = strategy.suggest(
                new SuggestionContext(list(STORE_NORTH, STORE_EAST), 2, CURRENT, HOME_EAST));

        assertEquals("the nearest rule should pick the northern store", 1L, nearest.store().id());
        assertEquals("the home-aware rule should pick the eastern store", 2L, homeAware.store().id());
    }

    @Test
    public void theReportedDistanceIsFromTheUserNotTheScore() {
        // The score mixes in the way home, so showing it as a distance would be showing a number that
        // describes no single journey. The reported figure is the distance to the user: 2,152.1 m.
        SuggestionResult result = strategy.suggest(
                new SuggestionContext(list(STORE_NORTH, STORE_EAST), 2, CURRENT, HOME_EAST));

        assertEquals(2152.1, result.distanceMetres(), TOLERANCE_M);
        assertEquals(SuggestionResult.Reason.HOME_AWARE, result.reason());
    }

    @Test
    public void theChoiceDoesNotDependOnTheListOrder() {
        SuggestionResult forwards = strategy.suggest(
                new SuggestionContext(list(STORE_NORTH, STORE_EAST), 2, CURRENT, HOME_EAST));
        SuggestionResult backwards = strategy.suggest(
                new SuggestionContext(list(STORE_EAST, STORE_NORTH), 2, CURRENT, HOME_EAST));

        assertEquals(forwards.store().id(), backwards.store().id());
    }

    // ------------------------------------------------------------------ ties

    @Test
    public void exactTiesAreBrokenByTheLowestId() {
        // Two stores on the straight line between the user and home score identically:
        //   North (1,112.0 from user, 4,447.8 to home) = 5,559.75
        //   Far   (3,335.85 from user, 2,223.90 to home) = 5,559.75
        // Both are "on the way", so the tie-break is what makes the answer deterministic.
        StoreCandidate farNorth = new StoreCandidate(3, "F-01", "Far north", 14.6300, 120.9800);
        GeoCoordinate homeNorth = new GeoCoordinate(14.6500, 120.9800);

        SuggestionResult result = strategy.suggest(
                new SuggestionContext(list(farNorth, STORE_NORTH), 2, CURRENT, homeNorth));

        assertEquals("the lower id should win the tie", 1L, result.store().id());
    }

    // ------------------------------------------------------------------ when it declines

    @Test
    public void withoutAHomeItDelegatesToTheNearestRule() {
        // The formula needs both points. Rather than inventing one, it asks the simpler strategy - which
        // means the two cannot develop different ideas about what to do when Home is unset.
        SuggestionResult result = strategy.suggest(
                new SuggestionContext(list(STORE_NORTH, STORE_EAST), 2, CURRENT, null));

        assertEquals(SuggestionResult.Reason.NEAREST, result.reason());
        assertEquals(1L, result.store().id());
    }

    @Test
    public void withoutALocationItDelegatesToTheNearestRule() {
        // Which itself falls back to Home, then to the first by id.
        SuggestionResult result = strategy.suggest(
                new SuggestionContext(list(STORE_NORTH, STORE_EAST), 2, null, HOME_EAST));

        assertEquals(SuggestionResult.Reason.FROM_HOME, result.reason());
        assertEquals(2L, result.store().id());
    }

    @Test
    public void withNeitherReferenceItStillAnswers() {
        SuggestionResult result = strategy.suggest(
                new SuggestionContext(list(STORE_NORTH), 1, null, null));

        assertEquals(SuggestionResult.Status.SUGGESTED, result.status());
        assertTrue(result.hasStore());
    }

    // ------------------------------------------------------------------ the terminal states

    @Test
    public void noStoresAtAllReportsNoStores() {
        SuggestionResult result = strategy.suggest(
                new SuggestionContext(Collections.emptyList(), 0, CURRENT, HOME_EAST));

        assertEquals(SuggestionResult.Status.NO_STORES, result.status());
    }

    @Test
    public void nothingPendingReportsRouteComplete() {
        SuggestionResult result = strategy.suggest(
                new SuggestionContext(Collections.emptyList(), 4, CURRENT, HOME_EAST));

        assertEquals(SuggestionResult.Status.ROUTE_COMPLETE, result.status());
        assertFalse(result.hasStore());
    }

    // ------------------------------------------------------------------ defensive

    @Test
    public void aStoreWithAnUnusableCoordinateIsSkipped() {
        // Either measurement being NaN makes the whole score unusable. Treating it as zero would put a
        // broken store at the top of the list.
        StoreCandidate broken = new StoreCandidate(9, "X-99", "Broken", 999.0, 999.0);

        SuggestionResult result = strategy.suggest(
                new SuggestionContext(list(broken, STORE_EAST), 2, CURRENT, HOME_EAST));

        assertEquals(2L, result.store().id());
    }

    @Test
    public void nullCandidatesAreSkippedRatherThanThrowing() {
        List<StoreCandidate> withNull = new ArrayList<>();
        withNull.add(null);
        withNull.add(STORE_EAST);

        SuggestionResult result = strategy.suggest(
                new SuggestionContext(withNull, 1, CURRENT, HOME_EAST));

        assertEquals(2L, result.store().id());
    }

    @Test
    public void aSingleStoreIsStillChosen() {
        // With one candidate the score is computed once and it wins by default. It must not be reported as
        // "the only one left" - that wording belongs to the simpler strategy's own path.
        SuggestionResult result = strategy.suggest(
                new SuggestionContext(list(STORE_EAST), 1, CURRENT, HOME_EAST));

        assertEquals(SuggestionResult.Status.SUGGESTED, result.status());
        assertEquals(2L, result.store().id());
    }

    @Test
    public void theStrategyIsStateless() {
        // The same context twice must give the same answer - it holds no state between calls.
        SuggestionContext context =
                new SuggestionContext(list(STORE_NORTH, STORE_EAST), 2, CURRENT, HOME_EAST);

        assertEquals(strategy.suggest(context).store().id(), strategy.suggest(context).store().id());
    }
}

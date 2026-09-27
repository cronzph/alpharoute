package com.cph.alpharoute.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The nearest-store rule.
 *
 * <p>Pure JVM (AD-11). The distances below were computed independently from the haversine formula with the
 * same earth radius {@code GeoConstants} uses (6,371,008.8 m), not read back out of the code - a test that
 * asserts whatever the code already produces proves nothing.
 *
 * <p>The cases that matter are the ones where the answer is not "the closest one": no fix, no Home, one
 * store left, and an unusable coordinate.
 */
public class NearestStrategyTest {

    private static final double TOLERANCE_M = 2.0;

    /** Manila. Every coordinate below is a real position in the same city, so the distances are realistic. */
    private static final GeoCoordinate CURRENT = new GeoCoordinate(14.6000, 120.9800);

    /** 1,112.0 m north of the current position. */
    private static final StoreCandidate STORE_A =
            new StoreCandidate(1, "A-01", "Alpha", 14.6100, 120.9800);

    /** 2,152.1 m east of the current position - so A is nearer. */
    private static final StoreCandidate STORE_B =
            new StoreCandidate(2, "B-01", "Bravo", 14.6000, 121.0000);

    private static final GeoCoordinate HOME = new GeoCoordinate(14.6500, 120.9800);

    private final RouteSuggestionStrategy strategy = new NearestStrategy();

    private static List<StoreCandidate> list(StoreCandidate... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    // ------------------------------------------------------------------ the terminal states

    @Test
    public void noStoresAtAllReportsNoStores() {
        // Distinct from "the round is finished": an empty app needs the "add your first store" prompt, not
        // a "well done".
        SuggestionResult result = strategy.suggest(
                new SuggestionContext(Collections.emptyList(), 0, CURRENT, HOME));

        assertEquals(SuggestionResult.Status.NO_STORES, result.status());
        assertFalse(result.hasStore());
    }

    @Test
    public void nothingPendingWithStoresPresentReportsRouteComplete() {
        // The round is finished. That is a success, and the card offers Home instead.
        SuggestionResult result = strategy.suggest(
                new SuggestionContext(Collections.emptyList(), 5, CURRENT, HOME));

        assertEquals(SuggestionResult.Status.ROUTE_COMPLETE, result.status());
        assertNull(result.store());
    }

    @Test
    public void aNullPendingListIsTreatedAsEmpty() {
        SuggestionResult result = strategy.suggest(new SuggestionContext(null, 3, CURRENT, HOME));

        assertEquals(SuggestionResult.Status.ROUTE_COMPLETE, result.status());
    }

    // ------------------------------------------------------------------ the happy path

    @Test
    public void theClosestPendingStoreWins() {
        // A is 1,112.0 m away and B is 2,152.1 m, so A wins.
        SuggestionResult result = strategy.suggest(
                new SuggestionContext(list(STORE_A, STORE_B), 2, CURRENT, HOME));

        assertEquals(SuggestionResult.Status.SUGGESTED, result.status());
        assertEquals(SuggestionResult.Reason.NEAREST, result.reason());
        assertEquals(1L, result.store().id());
        assertEquals(1112.0, result.distanceMetres(), TOLERANCE_M);
    }

    @Test
    public void theOrderOfTheListDoesNotChangeTheAnswer() {
        // The list's order is a detail of whichever query produced it. Reversing it must not change which
        // store is suggested - a suggestion that flickers when nothing changed is one nobody trusts.
        SuggestionResult forwards = strategy.suggest(
                new SuggestionContext(list(STORE_A, STORE_B), 2, CURRENT, HOME));
        SuggestionResult backwards = strategy.suggest(
                new SuggestionContext(list(STORE_B, STORE_A), 2, CURRENT, HOME));

        assertEquals(forwards.store().id(), backwards.store().id());
    }

    @Test
    public void theDistanceIsReportedInMetres() {
        SuggestionResult result = strategy.suggest(
                new SuggestionContext(list(STORE_B), 1, CURRENT, HOME));

        assertEquals(2152.1, result.distanceMetres(), TOLERANCE_M);
        assertTrue(result.hasDistance());
    }

    // ------------------------------------------------------------------ one store left

    @Test
    public void theOnlyStoreLeftIsSuggestedWithoutComparing() {
        // Saying "the only one left" is more useful than saying "nearest" about a choice that was not made.
        SuggestionResult result = strategy.suggest(
                new SuggestionContext(list(STORE_A), 1, CURRENT, HOME));

        assertEquals(SuggestionResult.Reason.ONLY_ONE_LEFT, result.reason());
        assertEquals(1L, result.store().id());
    }

    @Test
    public void theOnlyStoreLeftStillReportsItsDistanceWhenThereIsALocation() {
        SuggestionResult result = strategy.suggest(
                new SuggestionContext(list(STORE_A), 1, CURRENT, HOME));

        assertEquals(1112.0, result.distanceMetres(), TOLERANCE_M);
    }

    // ------------------------------------------------------------------ no location

    @Test
    public void withoutALocationItFallsBackToHome() {
        // There is no fix, so "nearest to you" cannot be asked. "Nearest to home" is a real answer, and it
        // is labelled so the card does not claim the user is somewhere they are not.
        SuggestionResult result = strategy.suggest(
                new SuggestionContext(list(STORE_A, STORE_B), 2, null, HOME));

        assertEquals(SuggestionResult.Status.SUGGESTED, result.status());
        assertEquals(SuggestionResult.Reason.FROM_HOME, result.reason());
        // A is 4,447.8 m from home and B is 5,961.6 m, so A wins from home too.
        assertEquals(1L, result.store().id());
        assertEquals(4447.8, result.distanceMetres(), TOLERANCE_M);
    }

    @Test
    public void withoutALocationOrHomeItTakesTheFirstById() {
        // Arbitrary, and labelled as such - the card must not present it as a considered choice.
        SuggestionResult result = strategy.suggest(
                new SuggestionContext(list(STORE_B, STORE_A), 2, null, null));

        assertEquals(SuggestionResult.Reason.NO_REFERENCE, result.reason());
        assertEquals(1L, result.store().id());
    }

    @Test
    public void aSuggestionWithNoReferenceHasNoDistance() {
        // NaN, not zero. A zero would be a claim that the user is standing in the store.
        SuggestionResult result = strategy.suggest(
                new SuggestionContext(list(STORE_A), 1, null, null));

        assertFalse(result.hasDistance());
        assertTrue(Double.isNaN(result.distanceMetres()));
    }

    // ------------------------------------------------------------------ defensive

    @Test
    public void aStoreWithAnUnusableCoordinateIsSkipped() {
        // DistanceCalculator returns NaN for a coordinate outside the globe. Letting a NaN into the
        // comparison would put this store at the top of the list.
        StoreCandidate broken =
                new StoreCandidate(9, "X-99", "Broken", 999.0, 999.0);

        SuggestionResult result = strategy.suggest(
                new SuggestionContext(list(broken, STORE_B), 2, CURRENT, HOME));

        assertEquals(2L, result.store().id());
    }

    @Test
    public void nullCandidatesAreSkippedRatherThanThrowing() {
        List<StoreCandidate> withNull = new ArrayList<>();
        withNull.add(null);
        withNull.add(STORE_A);

        SuggestionResult result = strategy.suggest(
                new SuggestionContext(withNull, 1, CURRENT, HOME));

        assertEquals(1L, result.store().id());
    }

    @Test
    public void aListOfOnlyNullsEndsTheSuggestionRatherThanCrashing() {
        List<StoreCandidate> onlyNulls = new ArrayList<>();
        onlyNulls.add(null);

        SuggestionResult result = strategy.suggest(
                new SuggestionContext(onlyNulls, 1, CURRENT, HOME));

        assertEquals(SuggestionResult.Status.ROUTE_COMPLETE, result.status());
    }

    @Test
    public void tiesAreBrokenByTheLowestId() {
        // Two stores at the same coordinates - a real possibility, since nothing stops a user pinning the
        // same shop twice. Without a rule the choice would depend on list order.
        StoreCandidate twin = new StoreCandidate(7, "A-02", "Alpha twin", 14.6100, 120.9800);

        SuggestionResult result = strategy.suggest(
                new SuggestionContext(list(twin, STORE_A), 2, CURRENT, HOME));

        assertEquals(1L, result.store().id());
    }

    @Test
    public void theLabelReadsCodeThenName() {
        StoreCandidate candidate = new StoreCandidate(1, "R-01", "Alfamart Sampaloc", 0, 0);
        assertEquals("R-01 — Alfamart Sampaloc", candidate.label());
    }

    @Test
    public void theResultAlwaysHasAConsistentShape() {
        // A SUGGESTED result always has a store; the other two never do. Asserted so a future edit cannot
        // produce a "suggested" with nothing in it, which the card would render as a blank.
        SuggestionResult suggested = strategy.suggest(
                new SuggestionContext(list(STORE_A), 1, CURRENT, HOME));
        assertTrue(suggested.hasStore());
        assertTrue(suggested.store() != null);

        SuggestionResult complete = strategy.suggest(
                new SuggestionContext(Collections.emptyList(), 3, CURRENT, HOME));
        assertFalse(complete.hasStore());
        assertNull(complete.store());
    }
}

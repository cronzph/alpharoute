package com.cph.alpharoute.domain;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Putting a saved plan back together with what is still pending.
 *
 * <p>Pure JVM. This file exists because all three of the merge's jobs fail silently: a completed store left
 * in would put a shop the user has already done back on the route, a new store dropped would leave a shop
 * out of the round entirely, and a plan listing the same store twice would drive to it twice. None of those
 * throws, logs, or looks wrong on screen - the route would simply be wrong.
 */
public class PlanMergeTest {

    private static StoreCandidate store(long id) {
        // Coordinates are never read by the merge - it works on ids alone - so a real place is used only to
        // keep the fixture honest rather than a made-up pair.
        return new StoreCandidate(id, "C-" + id, "Store " + id, 14.6000 + id * 0.001, 120.9800);
    }

    private static List<StoreCandidate> pending(long... ids) {
        List<StoreCandidate> stores = new ArrayList<>(ids.length);
        for (long id : ids) {
            stores.add(store(id));
        }
        return stores;
    }

    private static List<Long> idsOf(List<StoreCandidate> stores) {
        List<Long> ids = new ArrayList<>(stores.size());
        for (StoreCandidate store : stores) {
            ids.add(store.id());
        }
        return ids;
    }

    // ------------------------------------------------------------------ the user's order wins

    @Test
    public void theSavedOrderIsKept() {
        // The planner's order is 1, 2, 3. The user's is 3, 1, 2. The user's is the answer.
        List<StoreCandidate> merged = PlanMerge.merge(
                Arrays.asList(3L, 1L, 2L),
                pending(1L, 2L, 3L));

        assertEquals(Arrays.asList(3L, 1L, 2L), idsOf(merged));
    }

    @Test
    public void theSavedOrderIsNotSecondGuessed() {
        // Deliberately the worst possible order. The merge must not improve it - a store the user put last
        // stays last, because "where should this go" is a judgement the app should not make for them.
        List<StoreCandidate> merged = PlanMerge.merge(
                Arrays.asList(3L, 2L, 1L),
                pending(1L, 2L, 3L));

        assertEquals(Arrays.asList(3L, 2L, 1L), idsOf(merged));
    }

    // ------------------------------------------------------------------ the three silent failures

    @Test
    public void aStoreCompletedSinceThePlanWasSavedDropsOut() {
        // Store 2 was visited this morning. Leaving it in would put a done shop back on the route.
        List<StoreCandidate> merged = PlanMerge.merge(
                Arrays.asList(3L, 2L, 1L),
                pending(1L, 3L));

        assertEquals(Arrays.asList(3L, 1L), idsOf(merged));
    }

    @Test
    public void aStoreAddedSinceThePlanWasSavedAppearsAtTheEnd() {
        // Store 4 was pinned this afternoon. Dropping it would leave a shop out of the round - and the user
        // would drive home believing the day was done, which is the one outcome this app exists to prevent.
        List<StoreCandidate> merged = PlanMerge.merge(
                Arrays.asList(3L, 1L),
                pending(1L, 2L, 3L, 4L));

        assertEquals(Arrays.asList(3L, 1L, 2L, 4L), idsOf(merged));
    }

    @Test
    public void aNewStoreGoesToTheEndInTheOrderItWasGiven() {
        // Two new stores, in the planner's order. Nothing in the database stops a plan listing the same
        // store twice, and nothing stops two stores being added at once either.
        List<StoreCandidate> merged = PlanMerge.merge(
                Arrays.asList(2L),
                pending(4L, 2L, 5L, 6L));

        assertEquals(Arrays.asList(2L, 4L, 5L, 6L), idsOf(merged));
    }

    @Test
    public void aStoreListedTwiceIsVisitedOnce() {
        // The unique index is on (planId, position), not on (planId, storeId), so a repeat can exist. The
        // first mention wins, so the user's earliest intent is the one kept.
        List<StoreCandidate> merged = PlanMerge.merge(
                Arrays.asList(2L, 3L, 2L, 1L),
                pending(1L, 2L, 3L));

        assertEquals(Arrays.asList(2L, 3L, 1L), idsOf(merged));
    }

    // ------------------------------------------------------------------ the edges

    @Test
    public void everyPendingStoreAppearsExactlyOnceWhateverThePlanSays() {
        // The invariant that matters: the route the user drives must visit every pending shop once. A plan
        // that is empty, stale, duplicated or full of strangers must not change that.
        List<StoreCandidate> merged = PlanMerge.merge(
                Arrays.asList(99L, 2L, 2L, 88L),
                pending(1L, 2L, 3L));

        assertEquals(3, merged.size());
        assertEquals(Arrays.asList(2L, 1L, 3L), idsOf(merged));
    }

    @Test
    public void anEmptyPlanGivesThePendingOrderUnchanged() {
        List<StoreCandidate> merged = PlanMerge.merge(
                Collections.emptyList(),
                pending(1L, 2L, 3L));

        assertEquals(Arrays.asList(1L, 2L, 3L), idsOf(merged));
    }

    @Test
    public void aPlanOfStoresThatAreAllGoneGivesThePendingOrderUnchanged() {
        List<StoreCandidate> merged = PlanMerge.merge(
                Arrays.asList(7L, 8L, 9L),
                pending(1L, 2L));

        assertEquals(Arrays.asList(1L, 2L), idsOf(merged));
    }

    @Test
    public void nothingPendingGivesNothingToDrive() {
        // Whatever the plan says. The plan is a memory of a round that is over.
        List<StoreCandidate> merged = PlanMerge.merge(
                Arrays.asList(1L, 2L),
                Collections.emptyList());

        assertEquals(0, merged.size());
    }

    @Test
    public void nullIdsInAPlanAreSkippedRatherThanThrowing() {
        List<Long> withNull = new ArrayList<>();
        withNull.add(null);
        withNull.add(2L);
        withNull.add(null);

        List<StoreCandidate> merged = PlanMerge.merge(withNull, pending(1L, 2L));

        assertEquals(Arrays.asList(2L, 1L), idsOf(merged));
    }

    @Test
    public void nullStoresInThePendingListAreSkipped() {
        List<StoreCandidate> withNull = new ArrayList<>();
        withNull.add(null);
        withNull.add(store(1));
        withNull.add(null);

        List<StoreCandidate> merged = PlanMerge.merge(Collections.emptyList(), withNull);

        assertEquals(Arrays.asList(1L), idsOf(merged));
    }
}

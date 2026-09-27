package com.cph.alpharoute.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 * Ordering the stops, on the device.
 *
 * <p>Pure JVM, no network, no key. That is the point of the class being in {@code domain/} (AD-11): the
 * ordering rule is the whole feature, and a wrong order is not an error - it is a plausible list of shops
 * in a strange sequence, with nothing on screen to say why. It has to be assertable on a laptop.
 *
 * <p>Every coordinate here is a real place in Metro Manila, used only as a fixture. No store coordinate is
 * invented anywhere in this project, and nothing in these tests touches the database.
 *
 * <p><b>How the fixtures are scaled.</b> At 14.6° north, 0.002° of latitude is about 221 m and 0.002° of
 * longitude about 215 m - close enough to treat as a square grid, which is what makes the expected orders
 * below readable rather than a wall of decimals.
 */
public class RouteOrderPlannerTest {

    private static final GeoCoordinate START = new GeoCoordinate(14.6000, 120.9800);
    private static final GeoCoordinate HOME = new GeoCoordinate(14.6600, 120.9800);

    // Three stores in a line north of the start: 221 m, 442 m and 1,107 m away.
    private static final StoreCandidate NORTH_1 = store(1, 14.6020, 120.9800);
    private static final StoreCandidate NORTH_2 = store(2, 14.6040, 120.9800);
    private static final StoreCandidate EAST_3 = store(3, 14.6000, 120.9900);
    private static final StoreCandidate EAST_4 = store(4, 14.6040, 120.9900);

    private static StoreCandidate store(long id, double latitude, double longitude) {
        return new StoreCandidate(id, "C-" + id, "Store " + id, latitude, longitude);
    }

    private static List<StoreCandidate> list(StoreCandidate... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    private static List<Long> idsOf(OptimizationPlan plan) {
        List<Long> ids = new ArrayList<>(plan.stopCount());
        for (OptimizationStop stop : plan.stops()) {
            ids.add(stop.storeId());
        }
        return ids;
    }

    // ------------------------------------------------------------------ the order

    @Test
    public void theNearestStoreIsVisitedFirst() {
        OptimizationPlan plan = RouteOrderPlanner.plan(list(EAST_4, NORTH_2, NORTH_1), START, null);

        assertEquals(1L, plan.stops().get(0).storeId());
    }

    @Test
    public void theRouteChainsFromEachStopToTheNextNotFromTheStart() {
        // THE test that separates a real chain from "sort the stores by how far they are from the start".
        //
        // Distances from the start are NORTH_1 (221 m), NORTH_2 (442 m), EAST_3 (1,077 m), EAST_4 (1,164 m).
        // Sorting by those would give 1, 2, 3, 4.
        //
        // Nearest neighbour gives something else. It takes NORTH_1, then NORTH_2, and then - standing at
        // NORTH_2 - EAST_4 is 1,077 m away while EAST_3 is 1,164 m away, because EAST_4 shares its latitude.
        // So it goes to EAST_4 first, and the answer is 1, 2, 4, 3.
        //
        // That difference is the whole value of chaining, and it is invisible unless it is asserted.
        OptimizationPlan plan =
                RouteOrderPlanner.plan(list(NORTH_1, NORTH_2, EAST_3, EAST_4), START, null);

        assertEquals(Arrays.asList(1L, 2L, 4L, 3L), idsOf(plan));
    }

    @Test
    public void theSequenceIsOneBased() {
        OptimizationPlan plan = RouteOrderPlanner.plan(list(NORTH_1, NORTH_2), START, null);

        assertEquals(1, plan.stops().get(0).sequence());
        assertEquals(2, plan.stops().get(1).sequence());
    }

    @Test
    public void twoRunsOverTheSameStoresGiveTheSameRoute() {
        // The input order is deliberately not the id order. Without the sort inside the planner, whichever
        // store the list happened to hold first would win every tie.
        OptimizationPlan first = RouteOrderPlanner.plan(list(EAST_4, NORTH_1, EAST_3, NORTH_2), START, null);
        OptimizationPlan second = RouteOrderPlanner.plan(list(NORTH_2, EAST_3, NORTH_1, EAST_4), START, null);

        assertEquals(idsOf(first), idsOf(second));
    }

    @Test
    public void aTieGoesToTheLowerId() {
        // Two pins on the same spot - a real thing, from importing a link twice. The distances are exactly
        // equal, so the choice falls to the id, and it must be the lower one every time.
        StoreCandidate higher = store(9, 14.6100, 120.9800);
        StoreCandidate lower = store(4, 14.6100, 120.9800);

        OptimizationPlan plan = RouteOrderPlanner.plan(list(higher, lower), START, null);

        assertEquals(Arrays.asList(4L, 9L), idsOf(plan));
    }

    // ------------------------------------------------------------------ the refinement

    /**
     * Six stores scattered around the start, found by brute force to be a case where nearest neighbour is
     * genuinely bad: it wanders 1,187 m further than it needs to.
     */
    private static final StoreCandidate R1 = store(1, 14.6060, 120.9760);
    private static final StoreCandidate R2 = store(2, 14.6100, 120.9780);
    private static final StoreCandidate R3 = store(3, 14.6120, 120.9900);
    private static final StoreCandidate R4 = store(4, 14.6100, 120.9880);
    private static final StoreCandidate R5 = store(5, 14.6040, 120.9680);
    private static final StoreCandidate R6 = store(6, 14.6020, 120.9920);

    @Test
    public void theRefinementFindsTheShortestOrderForAScatteredSet() {
        // THE test that proves the second step does real work.
        //
        // Nearest neighbour alone answers 1, 2, 4, 3, 6, 5 - 6,398 m of straight-line driving, because it
        // commits early and never reconsiders. Reversing stretches brings it down to 5,211 m.
        //
        // 5,211 m is not just "better than greedy": it is the provably shortest of all 720 possible orders
        // for these six stores, checked by brute force. A route the planner can reach the optimum on is a
        // route worth trusting on the ones it cannot.
        OptimizationPlan plan =
                RouteOrderPlanner.plan(list(R1, R2, R3, R4, R5, R6), START, null);

        assertEquals(Arrays.asList(6L, 3L, 4L, 2L, 1L, 5L), idsOf(plan));
        assertEquals(5211.0, plan.totalDistanceMetres(), 10.0);
    }

    @Test
    public void theRefinementNeverLosesAStoreOrRepeatsOne() {
        // Reversing stretches permutes the list, so this is the invariant that could break silently.
        OptimizationPlan plan =
                RouteOrderPlanner.plan(list(R1, R2, R3, R4, R5, R6), START, HOME);

        assertEquals(6, plan.stopCount());
        assertEquals(6, new HashSet<>(idsOf(plan)).size());
    }

    @Test
    public void theRefinementLeavesASingleStoreAlone() {
        // No stretch to reverse, and no off-by-one to fall into.
        OptimizationPlan plan = RouteOrderPlanner.plan(list(NORTH_1), START, null);

        assertEquals(1, plan.stopCount());
        assertEquals(1L, plan.stops().get(0).storeId());
    }

    @Test
    public void theRefinementKeepsAnUnusableCoordinateAtTheEnd() {
        // An unusable coordinate has an infinite leg in the comparison, so no reversal may pull it forward
        // into a measurable slot. Treating that leg as zero instead would make the broken pin look free to
        // move, and the refinement would shuffle the whole route around it - which is the bug this caught.
        StoreCandidate broken = store(1, Double.NaN, Double.NaN);

        OptimizationPlan plan =
                RouteOrderPlanner.plan(list(broken, R2, R3, R4, R5, R6), START, null);

        assertEquals(6, plan.stopCount());
        assertEquals(1L, plan.stops().get(5).storeId());
    }

    // ------------------------------------------------------------------ every store, once

    @Test
    public void everyStoreIsVisitedExactlyOnce() {
        OptimizationPlan plan =
                RouteOrderPlanner.plan(list(NORTH_1, NORTH_2, EAST_3, EAST_4), START, HOME);

        assertEquals(4, plan.stopCount());
        assertEquals(4, new HashSet<>(idsOf(plan)).size());
    }

    @Test
    public void nothingIsEverReportedAsUnassigned() {
        // That field belongs to the service, which could refuse to place a store. The planner places all of
        // them or it would be a bug, and a non-zero count here would put an error on the screen.
        OptimizationPlan plan =
                RouteOrderPlanner.plan(list(NORTH_1, NORTH_2, EAST_3, EAST_4), START, HOME);

        assertEquals(0, plan.unassignedCount());
    }

    @Test
    public void nullStoresAreSkippedRatherThanThrowing() {
        List<StoreCandidate> withNull = new ArrayList<>();
        withNull.add(null);
        withNull.add(NORTH_1);
        withNull.add(null);

        OptimizationPlan plan = RouteOrderPlanner.plan(withNull, START, null);

        assertEquals(1, plan.stopCount());
        assertEquals(1L, plan.stops().get(0).storeId());
    }

    @Test
    public void anEmptyListGivesAnEmptyPlan() {
        OptimizationPlan plan = RouteOrderPlanner.plan(Collections.emptyList(), START, HOME);

        assertNotNull(plan);
        assertEquals(0, plan.stopCount());
        assertFalse(plan.hasStops());
    }

    @Test
    public void anUnusableCoordinateIsStillVisitedButNeverCountedAsNearest() {
        // DistanceCalculator answers NaN for a nonsense point, and a NaN is not greater than anything - so
        // a careless comparison would let it win. Dropping it instead would make the count on screen a lie.
        StoreCandidate broken = store(1, Double.NaN, Double.NaN);

        OptimizationPlan plan = RouteOrderPlanner.plan(list(broken, NORTH_2, EAST_3), START, null);

        assertEquals(3, plan.stopCount());
        // It sorts to the end, where it is still visited.
        assertEquals(1L, plan.stops().get(2).storeId());
        assertEquals(2L, plan.stops().get(0).storeId());
    }

    // ------------------------------------------------------------------ where the order is measured from

    @Test
    public void withoutAStartPointTheFirstLegHasNoDistance() {
        // There is nothing to measure the first leg from, and a zero would read as "you are already here".
        // The screen shows the stop with no distance rather than a number that means something else.
        OptimizationPlan plan = RouteOrderPlanner.plan(list(NORTH_1, NORTH_2), null, null);

        assertFalse(plan.startsAtCurrentLocation());
        assertEquals(0.0, plan.stops().get(0).legDistanceMetres(), 0.0001);
    }

    @Test
    public void withoutAStartPointTheTotalStillCountsTheLegsBetweenStops() {
        // Only the leg INTO the first stop is unknown. The driving from one shop to the next is real and
        // belongs in the total - dropping it would understate the day, which is the direction that matters
        // when the number is being used to judge whether the round is feasible.
        OptimizationPlan plan = RouteOrderPlanner.plan(list(NORTH_1, NORTH_2), null, null);

        assertEquals(
                DistanceCalculator.distanceMetres(NORTH_1.coordinate(), NORTH_2.coordinate()),
                plan.totalDistanceMetres(),
                0.5);
    }

    @Test
    public void withoutAStartPointTheRouteStillFinishesAtTheStoreNearestHome() {
        // With no fix, Home is the only reference the app has - and it is the finish line. So the last shop
        // of the day is the one closest to it, which is the shortest possible run home. That falls out of
        // the refinement rather than being special-cased, and it is the behaviour that matters on a day the
        // user sets out without a usable position.
        StoreCandidate farFromHome = store(1, 14.6010, 120.9800);
        StoreCandidate closeToHome = store(2, 14.6590, 120.9800);

        OptimizationPlan plan = RouteOrderPlanner.plan(list(farFromHome, closeToHome), null, HOME);

        assertFalse(plan.startsAtCurrentLocation());
        assertTrue(plan.endsAtHome());
        assertEquals(2L, plan.stops().get(plan.stopCount() - 1).storeId());
    }

    @Test
    public void withNoReferenceAtAllTheRouteStartsFromTheLowestId() {
        // With no fix and no Home there is nothing to measure from, so the first store is the lowest id -
        // arbitrary, and no worse than a list in whatever order the database happened to return. The
        // chaining then carries on from there, so only the seed is arbitrary, not the whole route.
        //
        // What matters is that the seed is the same every time, which is why the planner sorts by id
        // before it chooses. The stores are deliberately passed in a different order here.
        OptimizationPlan plan = RouteOrderPlanner.plan(list(EAST_4, NORTH_2, NORTH_1), null, null);

        assertEquals(1L, plan.stops().get(0).storeId());
        assertEquals(3, plan.stopCount());
        assertEquals(3, new HashSet<>(idsOf(plan)).size());
    }

    // ------------------------------------------------------------------ the totals

    @Test
    public void theTotalIsTheSumOfTheLegs() {
        OptimizationPlan plan = RouteOrderPlanner.plan(list(NORTH_1, NORTH_2), START, null);

        double first = DistanceCalculator.distanceMetres(START, NORTH_1.coordinate());
        double second = DistanceCalculator.distanceMetres(NORTH_1.coordinate(), NORTH_2.coordinate());

        assertEquals(first + second, plan.totalDistanceMetres(), 0.5);
    }

    @Test
    public void theDriveHomeCountsTowardsTheTotalButHomeIsNotAStop() {
        // The user really does drive it, so it belongs in the distance. It is not a shop to visit, so it
        // must not appear in the list - the same rule that keeps the service's start and end steps out.
        OptimizationPlan plan = RouteOrderPlanner.plan(list(NORTH_1), START, HOME);

        double toStore = DistanceCalculator.distanceMetres(START, NORTH_1.coordinate());
        double toHome = DistanceCalculator.distanceMetres(NORTH_1.coordinate(), HOME);

        assertEquals(1, plan.stopCount());
        assertTrue(plan.endsAtHome());
        assertEquals(toStore + toHome, plan.totalDistanceMetres(), 0.5);
    }

    @Test
    public void noDurationIsReportedBecauseNoneWasMeasured() {
        // A time needs a speed, and the app has never measured how fast this driver drives. Zero here means
        // "not known" - the screen drops the time rather than showing a number that was made up.
        OptimizationPlan plan = RouteOrderPlanner.plan(list(NORTH_1, NORTH_2), START, HOME);

        assertEquals(0L, plan.totalDurationSeconds());
        for (OptimizationStop stop : plan.stops()) {
            assertEquals(0L, stop.legDurationSeconds());
            assertEquals(0L, stop.arrivalSeconds());
        }
    }

    @Test
    public void theEndpointFlagsSayWhatTheRouteWasGiven() {
        assertTrue(RouteOrderPlanner.plan(list(NORTH_1), START, HOME).startsAtCurrentLocation());
        assertTrue(RouteOrderPlanner.plan(list(NORTH_1), START, HOME).endsAtHome());
        assertFalse(RouteOrderPlanner.plan(list(NORTH_1), START, null).endsAtHome());
        assertFalse(RouteOrderPlanner.plan(list(NORTH_1), null, HOME).startsAtCurrentLocation());
    }

    @Test
    public void theFirstLegIsMeasuredFromTheStartPointWhenThereIsOne() {
        OptimizationPlan plan = RouteOrderPlanner.plan(list(NORTH_1, NORTH_2), START, null);

        assertEquals(
                DistanceCalculator.distanceMetres(START, NORTH_1.coordinate()),
                plan.stops().get(0).legDistanceMetres(),
                0.5);
    }

    // ------------------------------------------------------------------ scoring an order the user decided

    @Test
    public void measureKeepsTheOrderItIsGiven() {
        // THE rule for a hand-made plan. The refinement would reorder this list in a heartbeat - it is the
        // same scattered set the refinement test uses - and measure must not, because the user's order is
        // the answer rather than a starting point.
        List<StoreCandidate> userOrder = list(R1, R5, R3, R6, R2, R4);

        OptimizationPlan plan = RouteOrderPlanner.measure(userOrder, START, null);

        assertEquals(Arrays.asList(1L, 5L, 3L, 6L, 2L, 4L), idsOf(plan));
    }

    @Test
    public void measureSumsTheLegsOfTheOrderItIsGiven() {
        // Scored with the same arithmetic the planner uses, so a hand-made route and a suggested one can be
        // compared honestly rather than being two different kinds of number.
        List<StoreCandidate> userOrder = list(R1, R5, R3);

        OptimizationPlan plan = RouteOrderPlanner.measure(userOrder, START, HOME);

        double expected =
                DistanceCalculator.distanceMetres(START, R1.coordinate())
                        + DistanceCalculator.distanceMetres(R1.coordinate(), R5.coordinate())
                        + DistanceCalculator.distanceMetres(R5.coordinate(), R3.coordinate())
                        + DistanceCalculator.distanceMetres(R3.coordinate(), HOME);

        assertEquals(expected, plan.totalDistanceMetres(), 0.5);
        assertEquals(3, plan.stopCount());
    }

    @Test
    public void measureNeverDropsAStore() {
        // A route that quietly loses a shop is the failure this whole app exists to prevent, so measure is
        // held to the same promise the planner makes: every store, exactly once.
        List<StoreCandidate> userOrder = list(R1, R2, R3, R4, R5, R6);

        OptimizationPlan plan = RouteOrderPlanner.measure(userOrder, START, HOME);

        assertEquals(6, plan.stopCount());
        assertEquals(6, new HashSet<>(idsOf(plan)).size());
    }

    @Test
    public void measureReportsNoDurationJustLikeThePlanner() {
        OptimizationPlan plan = RouteOrderPlanner.measure(list(R1, R2), START, HOME);

        assertEquals(0L, plan.totalDurationSeconds());
        for (OptimizationStop stop : plan.stops()) {
            assertEquals(0L, stop.legDurationSeconds());
        }
    }

    @Test
    public void measureCarriesTheEndpointFlags() {
        assertTrue(RouteOrderPlanner.measure(list(R1), START, HOME).startsAtCurrentLocation());
        assertTrue(RouteOrderPlanner.measure(list(R1), START, HOME).endsAtHome());
        assertFalse(RouteOrderPlanner.measure(list(R1), START, null).endsAtHome());
    }
}

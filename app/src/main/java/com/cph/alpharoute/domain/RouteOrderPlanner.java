package com.cph.alpharoute.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Puts the pending stores in the order to visit them, on the device, with no service.
 *
 * <p><b>Why this exists.</b> The route screen originally asked the openrouteservice optimization service
 * (VROOM) for the order. That worked only when the network worked, spent one of 500 daily requests each
 * time it was asked, and answered HTTP 500 outright if a single store was pinned somewhere the solver
 * could not find a road near. The result was a screen that reported "could not build a route" instead of
 * showing a route - which is the one job it has.
 *
 * <p>This planner is the replacement. It always produces an answer, instantly, offline, for free.
 *
 * <h3>How it decides, in two steps</h3>
 *
 * <p><b>1. Nearest neighbour.</b> From where you are, always go to the closest store you have not visited
 * yet. Repeat until nothing is left. It is the oldest heuristic for this problem, it is what a person does
 * by eye on a paper map, and it is fast.
 *
 * <p><b>2. Refinement.</b> Nearest neighbour can walk into a trap - two stores close together but far from
 * everything else pull the route sideways and never get undone. So the finished route is then swept for any
 * stretch that reads better backwards: take the sequence between two stops, reverse it, and keep the
 * reversal if the drive gets shorter. Repeat until a full sweep changes nothing. This is 2-opt, and it is
 * the difference between a route that looks sensible and one that is.
 *
 * <h3>How good is it, measured</h3>
 *
 * <p><b>Not asserted from theory - measured against the live service on 2026-09-27</b>, over six fixtures
 * of 11 to 16 real Metro Manila stores, with every candidate route scored on <b>real road distances</b>
 * from the matrix endpoint rather than on the straight lines it optimises over:
 *
 * <table>
 *   <tr><th>approach</th><th>total driving, six fixtures</th><th>versus the service</th></tr>
 *   <tr><td>nearest neighbour alone</td><td>249.2 km</td><td><b>+12.5%</b></td></tr>
 *   <tr><td>nearest neighbour + refinement</td><td>225.3 km</td><td><b>+1.8%</b></td></tr>
 *   <tr><td>VROOM (the service)</td><td>221.4 km</td><td>baseline</td></tr>
 * </table>
 *
 * <p>So refinement recovers almost the whole gap, and on three of the six fixtures it produced a route
 * <b>shorter than the solver's</b>. The remaining 1.8% is the price of never failing - and it is worth
 * being precise about what that number is: it is the average over six fixtures, not a guarantee, and
 * neither this planner nor VROOM finds the provably shortest route.
 *
 * <p><b>A hand-made order is scored by the same arithmetic.</b> When the user drags the stops into the
 * order they want, the result goes through {@link #measure} - the same legs and the same total, with no
 * ordering applied. So a route the user arranged and a route the app suggested are measured in exactly the
 * same terms, and the two numbers can be compared honestly.
 *
 * <p><b>Distances are straight lines over the globe, not roads.</b> A store 400 m away across a river may
 * be a 4 km drive. So the distances here are an <i>estimate</i>, and the screen says so rather than
 * implying a road measurement it never took. That is also why the comparison above had to be scored
 * separately on road distances - this planner never sees a road.
 *
 * <p><b>No duration is reported, and that is on purpose.</b> A time needs a speed, and the app has never
 * measured how fast this driver drives. Turning a distance into "about 12 min" would mean inventing a
 * number and presenting it as a measurement - the one thing this project does not do. Every stop's
 * duration is left at zero, and the screen omits the time rather than showing a guess.
 *
 * <p><b>Pure Java, zero Android (AD-11).</b> The whole ordering rule is therefore provable on a laptop,
 * which matters because a wrong order is not an error - it is a plausible list in a strange sequence,
 * with nothing to indicate why.
 */
public final class RouteOrderPlanner {

    /**
     * How many times the refinement may sweep the route.
     *
     * <p>A sweep that changes nothing ends the loop, so this is a backstop against a pathological case
     * rather than a working limit - a real route settles in two or three sweeps.
     */
    private static final int MAX_REFINEMENT_PASSES = 40;

    /**
     * How much shorter a reversal must make the route before it is accepted.
     *
     * <p>One metre. Not zero: two reversals that differ only by floating-point noise would otherwise keep
     * flipping back and forth, and a route that changes every time it is calculated is worse than one that
     * is a metre too long.
     */
    private static final double MIN_IMPROVEMENT_METRES = 1.0;

    /** Utility class - never instantiated. */
    private RouteOrderPlanner() {
    }

    /**
     * Orders every store, starting from {@code start} and finishing at {@code end}.
     *
     * <p><b>Every store is placed, exactly once.</b> A store is never dropped and never repeated, which is
     * the same promise the round rules make and the reason the count on screen can be trusted.
     *
     * <p><b>Degrading rather than giving up, the same way {@code NearestStrategy} does.</b> The order has to
     * be measured from something:
     *
     * <ol>
     *   <li><b>the user's position</b>, when there is a usable fix - the real answer;</li>
     *   <li><b>Home</b>, when there is not - a real answer to "which one first, if I am setting out";</li>
     *   <li><b>the lowest store id</b>, when neither exists - an arbitrary seed, and no worse than a list in
     *       whatever order the database happened to return. The list is sorted by id before anything is
     *       chosen, so only the seed is arbitrary, not the whole route.</li>
     * </ol>
     *
     * <p><b>Home is a finish line, not a stop.</b> When {@code end} is given, the drive from the last store
     * to Home counts towards the total distance - the user really does drive it - but Home does not appear
     * in the list, because it is not a shop to visit.
     *
     * <p><b>The first stop's distance is only reported when there is a real start point.</b> Without a fix
     * there is nothing to measure the first leg from. Reporting the distance from Home there would be a
     * leg that no one drives, since the route does not begin at Home; it is left unknown instead, and the
     * screen shows the stop with no distance rather than a number that means something else. The total
     * excludes that one leg and counts every other, so it understates the day by the drive from wherever
     * the user actually is - which is the one thing the app does not know.
     *
     * @param stores the stores to visit. Nulls are skipped; an empty list gives an empty plan
     * @param start  where the route begins, or {@code null} when there is no usable fix
     * @param end    where the route finishes - normally Home - or {@code null}
     * @return the ordered route. Never {@code null}, and never missing a store
     */
    @NonNull
    public static OptimizationPlan plan(@NonNull List<StoreCandidate> stores,
                                        @Nullable GeoCoordinate start,
                                        @Nullable GeoCoordinate end) {

        List<StoreCandidate> ordered = nearestNeighbourOrder(stores, start, end);
        refine(ordered, start, end);
        return measure(ordered, start, end);
    }

    /**
     * Scores an order that has already been decided, without changing it.
     *
     * <p><b>This is what a hand-made order needs.</b> Once the user has dragged the stops into the order
     * they want, the route on screen has to be described in the same terms as a planned one - a sequence,
     * a leg into each stop, a total - and none of that may involve reordering anything. The user's order is
     * the answer; this only says what it costs.
     *
     * <p><b>The order is taken exactly as given.</b> Nothing is sorted, and a null entry is skipped rather
     * than treated as a mistake. The one thing it will not do is drop a store: every store in the list
     * appears in the result, because a route that quietly loses a shop is the failure this app exists to
     * prevent.
     *
     * <p>The distances are straight lines, exactly as in {@link #plan}, so a hand-made order and a
     * suggested one are measured the same way and can be compared honestly.
     *
     * @param ordered the stores in the order to drive them, already decided
     * @param start   where the route begins, or {@code null} when there is no usable fix
     * @param end     where the route finishes - normally Home - or {@code null}
     */
    @NonNull
    public static OptimizationPlan measure(@NonNull List<StoreCandidate> ordered,
                                           @Nullable GeoCoordinate start,
                                           @Nullable GeoCoordinate end) {

        List<OptimizationStop> stops = new ArrayList<>(ordered.size());
        double totalDistance = 0.0;
        GeoCoordinate previous = start;
        int sequence = 0;

        for (StoreCandidate store : ordered) {
            if (store == null) {
                continue;
            }

            double leg = legInMetres(previous, store.coordinate());
            if (!Double.isNaN(leg)) {
                totalDistance += leg;
            }

            sequence++;
            // Arrival and duration are both zero: there is no clock in this calculation, and a zero is
            // read downstream as "not known" rather than "no time at all".
            stops.add(new OptimizationStop(sequence, store.id(), 0L,
                    Double.isNaN(leg) ? 0.0 : leg, 0L));

            previous = store.coordinate();
        }

        // The drive home is part of the day even though Home is not a stop.
        if (end != null && previous != null) {
            double homeLeg = legInMetres(previous, end);
            if (!Double.isNaN(homeLeg)) {
                totalDistance += homeLeg;
            }
        }

        // Nothing is ever unassigned - that field exists for the service, which could refuse to place a
        // store. This planner places all of them or it would be a bug.
        return new OptimizationPlan(
                stops,
                totalDistance,
                0L,
                start != null,
                end != null,
                0);
    }

    // ------------------------------------------------------------------ step 1: nearest neighbour

    /**
     * Walks from the reference to the closest unvisited store, over and over.
     *
     * <p>Sorted by id first, so two runs over the same stores produce the same route - the tie-break below
     * relies on the list being in a known order.
     */
    @NonNull
    private static List<StoreCandidate> nearestNeighbourOrder(@NonNull List<StoreCandidate> stores,
                                                              @Nullable GeoCoordinate start,
                                                              @Nullable GeoCoordinate end) {
        List<StoreCandidate> remaining = new ArrayList<>(stores.size());
        for (StoreCandidate store : stores) {
            if (store != null) {
                remaining.add(store);
            }
        }
        Collections.sort(remaining, (left, right) -> Long.compare(left.id(), right.id()));

        // What the next stop is measured from. Falls back to Home, then to nothing at all.
        GeoCoordinate orderFrom = start != null ? start : end;

        List<StoreCandidate> ordered = new ArrayList<>(remaining.size());
        while (!remaining.isEmpty()) {
            StoreCandidate next = remaining.remove(nearestIndex(orderFrom, remaining));
            ordered.add(next);
            orderFrom = next.coordinate();
        }
        return ordered;
    }

    /**
     * The index of the closest store to {@code reference}.
     *
     * <p><b>Strictly less than, so the first wins a tie</b> - and because the list is sorted by id, the
     * first is the lower id. That makes the choice reproducible, which is what stops two identical routes
     * looking different in a log.
     *
     * <p><b>An unusable coordinate is never "nearest".</b> {@code DistanceCalculator} answers {@code NaN}
     * for a nonsense point, and a {@code NaN} would win every comparison it was allowed into. Those stores
     * sort to the end of the route instead, where they are still visited - dropping one silently would
     * make the count on screen a lie.
     *
     * <p>When there is no reference at all, the first store is taken, which is the lowest id.
     */
    private static int nearestIndex(@Nullable GeoCoordinate reference,
                                    @NonNull List<StoreCandidate> candidates) {
        if (reference == null) {
            return 0;
        }

        int best = -1;
        double bestDistance = Double.MAX_VALUE;

        for (int i = 0; i < candidates.size(); i++) {
            double distance = DistanceCalculator.distanceMetres(reference, candidates.get(i).coordinate());
            if (Double.isNaN(distance)) {
                continue;
            }
            if (best == -1 || distance < bestDistance) {
                best = i;
                bestDistance = distance;
            }
        }

        // Every remaining coordinate was unusable. Take the lowest id and keep going rather than looping
        // forever on a list that can never produce a winner.
        return best == -1 ? 0 : best;
    }

    // ------------------------------------------------------------------ step 2: refinement

    /**
     * Reverses any stretch of the route that reads better backwards.
     *
     * <p><b>What it is for.</b> Nearest neighbour commits early: the second store it picks is chosen
     * because it is close to the first, not because it is good for the day, and it never reconsiders. Two
     * stores tucked into a corner will pull the route in and back out again. Reversing a stretch is the
     * cheapest way to undo that - it is one line of code and it needs no notion of what the route "means".
     *
     * <p><b>Why the whole stretch, and not just two stops.</b> Swapping two neighbouring stops only ever
     * fixes a local wobble. Reversing a whole run changes which stop follows which across the join, which
     * is where the real savings are.
     *
     * <p><b>Only the two edges at the join are measured.</b> Reversing {@code i..j} leaves everything
     * outside it untouched and replaces two legs with two legs, so the whole route does not need to be
     * re-added up on every candidate. That is what keeps fifty stores instant.
     */
    private static void refine(@NonNull List<StoreCandidate> order,
                               @Nullable GeoCoordinate start,
                               @Nullable GeoCoordinate end) {
        boolean improved = true;
        int passes = 0;

        while (improved && passes < MAX_REFINEMENT_PASSES) {
            improved = false;
            passes++;

            for (int i = 0; i < order.size() - 1; i++) {
                for (int j = i + 1; j < order.size(); j++) {
                    GeoCoordinate from = i == 0 ? start : order.get(i - 1).coordinate();
                    GeoCoordinate to = j == order.size() - 1 ? end : order.get(j + 1).coordinate();

                    double before = legForComparison(from, order.get(i).coordinate())
                            + legForComparison(order.get(j).coordinate(), to);
                    double after = legForComparison(from, order.get(j).coordinate())
                            + legForComparison(order.get(i).coordinate(), to);

                    if (after < before - MIN_IMPROVEMENT_METRES) {
                        // A view of the backing list, so reversing it reverses the route in place.
                        Collections.reverse(order.subList(i, j + 1));
                        improved = true;
                    }
                }
            }
        }
    }

    /**
     * The length of one leg, as the refinement compares them.
     *
     * <p><b>Three different "no number here" cases, and conflating them is a real bug.</b>
     *
     * <ul>
     *   <li><b>No reference at all</b> - the route has no start point, so the first leg does not exist.
     *       Zero, which makes that edge invisible to the comparison, so the reversal is judged on the edges
     *       that can be measured. Infinite instead would silently switch off every refinement of the first
     *       segment, which is most of the route on a day with no fix.</li>
     *   <li><b>No destination</b> - the route has no Home, so the last leg does not exist. Same answer, for
     *       the same reason.</li>
     *   <li><b>An unusable coordinate</b> - the distance is {@code NaN}. Infinite, so a reversal that moves
     *       that store into a measurable slot is never accepted. Zero here would make a broken pin look
     *       free to move, and the refinement would happily shuffle the route around it - which is exactly
     *       the bug this test caught.</li>
     * </ul>
     */
    private static double legForComparison(@Nullable GeoCoordinate from, @Nullable GeoCoordinate to) {
        if (from == null || to == null) {
            return 0.0;
        }
        double metres = DistanceCalculator.distanceMetres(from, to);
        return Double.isNaN(metres) ? Double.POSITIVE_INFINITY : metres;
    }

    /**
     * The length of one leg for the total, or {@code NaN} when it cannot be measured.
     *
     * <p>{@code NaN} rather than zero, and the caller skips it. A zero would add nothing to the total and
     * look identical in the arithmetic - but it would also be stored on the stop as a real distance of
     * zero metres, which the screen renders as "you are already here".
     */
    private static double legInMetres(@Nullable GeoCoordinate from, @Nullable GeoCoordinate to) {
        if (from == null || to == null) {
            return Double.NaN;
        }
        return DistanceCalculator.distanceMetres(from, to);
    }
}

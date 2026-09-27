package com.cph.alpharoute.domain;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.List;

/**
 * The whole route, in the order to drive it.
 *
 * <p><b>What this is, and what a suggestion is not.</b> {@code HomeAwareStrategy} looks one store ahead and
 * picks the best next move; it cannot reorder anything, so it produces a good route only by accident. This
 * holds the whole set, already ordered - which is why the route screen can show a sequence and the
 * suggestion card cannot.
 *
 * <p><b>Where the order comes from changed, and the shape did not.</b> It used to be the openrouteservice
 * optimization service (VROOM). It is now {@link RouteOrderPlanner}, which runs on the device - see that
 * class for why, and for what the local heuristic gives up. Both produce this same object, so a screen
 * cannot tell them apart and does not need to.
 *
 * <p><b>Not a guarantee, and the wording matters.</b> Nearest neighbour is a heuristic: it finds a good
 * order, not provably the best one. The screen says "optimised", never "shortest possible".
 *
 * <p><b>A locally planned route carries no time.</b> {@code totalDurationSeconds} is zero and every stop's
 * duration is zero, because a time needs a speed the app has never measured. Zero means "not known" here,
 * never "no time at all" - and the screen omits the time rather than showing a guess.
 *
 * <p>Pure Java.
 */
public final class OptimizationPlan {

    private final List<OptimizationStop> stops;
    private final double totalDistanceMetres;
    private final long totalDurationSeconds;
    private final boolean startsAtCurrentLocation;
    private final boolean endsAtHome;
    private final int unassignedCount;

    /**
     * @param stops                   the stops, in order, 1-based in {@code sequence}
     * @param totalDistanceMetres     the whole route's distance. A straight-line estimate when the plan
     *                                was made locally, so it is always shorter than the drive
     * @param totalDurationSeconds    the whole route's driving time, or {@code 0} when it is not known -
     *                                which is every locally planned route
     * @param startsAtCurrentLocation whether the route was given a start point
     * @param endsAtHome              whether the route was given Home as its finish
     * @param unassignedCount         how many stores could not be placed. Always 0 for a local plan - the
     *                                planner places every store. A non-zero value means a service answer
     *                                left shops out, and the screen says so rather than showing a route
     *                                that silently skips them
     */
    public OptimizationPlan(@NonNull List<OptimizationStop> stops,
                            double totalDistanceMetres,
                            long totalDurationSeconds,
                            boolean startsAtCurrentLocation,
                            boolean endsAtHome,
                            int unassignedCount) {
        this.stops = Collections.unmodifiableList(stops);
        this.totalDistanceMetres = totalDistanceMetres;
        this.totalDurationSeconds = totalDurationSeconds;
        this.startsAtCurrentLocation = startsAtCurrentLocation;
        this.endsAtHome = endsAtHome;
        this.unassignedCount = Math.max(0, unassignedCount);
    }

    /** The stops, in the order they should be visited. */
    @NonNull
    public List<OptimizationStop> stops() {
        return stops;
    }

    /** How many stores the route visits. */
    public int stopCount() {
        return stops.size();
    }

    /** The whole route's distance - a straight-line estimate when it was planned on the device. */
    public double totalDistanceMetres() {
        return totalDistanceMetres;
    }

    /**
     * The whole route's driving time, or {@code 0} when it is not known.
     *
     * <p>Every locally planned route answers {@code 0}, because the planner has no speed to work from.
     * Callers must treat it as "not known" and leave the time out, never as "no time at all".
     */
    public long totalDurationSeconds() {
        return totalDurationSeconds;
    }

    /** Whether the route begins where the user is. */
    public boolean startsAtCurrentLocation() {
        return startsAtCurrentLocation;
    }

    /** Whether the route finishes at Home. */
    public boolean endsAtHome() {
        return endsAtHome;
    }

    /**
     * How many stores could not be placed in the route.
     *
     * <p>Exposed rather than swallowed: a route that quietly leaves shops out is worse than one that says
     * it could not fit them, because the user would drive home believing the day was done.
     */
    public int unassignedCount() {
        return unassignedCount;
    }

    /** Whether there is a route to show. */
    public boolean hasStops() {
        return !stops.isEmpty();
    }

    @NonNull
    @Override
    public String toString() {
        return "OptimizationPlan(" + stops.size() + " stops, "
                + Math.round(totalDistanceMetres) + " m)";
    }
}

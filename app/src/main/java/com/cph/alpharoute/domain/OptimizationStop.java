package com.cph.alpharoute.domain;

import androidx.annotation.NonNull;

/**
 * One stop in an optimised route, in the order the solver put it.
 *
 * <p>Pure Java. Built either by {@code RouteOrderPlanner} on the device or by
 * {@code OptimizationResponseParser} from a service answer, and read by the route screen - which is why it
 * carries the arrival time and the leg distance as well as the id: the screen shows a journey, not just a
 * list of names.
 *
 * <p><b>Zero means "not known" in both time fields, not "no time".</b> A locally planned route has no
 * clock behind it, so {@code arrivalSeconds} and {@code legDurationSeconds} are both zero and the screen
 * leaves the time out. A caller that rendered them as "0 s" would be stating something the app never
 * measured.
 */
public final class OptimizationStop {

    private final int sequence;
    private final long storeId;
    private final long arrivalSeconds;
    private final double legDistanceMetres;
    private final long legDurationSeconds;

    /**
     * @param sequence           1 for the first stop, 2 for the second, and so on
     * @param storeId            the store to visit
     * @param arrivalSeconds     seconds from the start of the route to reaching this stop, or {@code 0}
     *                           when the route was planned locally and there is no clock behind it
     * @param legDistanceMetres  metres from the previous stop. A straight-line estimate for a local plan,
     *                           and {@code 0} for the first stop when the route had no start point
     * @param legDurationSeconds seconds driven since the previous stop, or {@code 0} when not known
     */
    public OptimizationStop(int sequence,
                            long storeId,
                            long arrivalSeconds,
                            double legDistanceMetres,
                            long legDurationSeconds) {
        this.sequence = sequence;
        this.storeId = storeId;
        this.arrivalSeconds = arrivalSeconds;
        this.legDistanceMetres = legDistanceMetres;
        this.legDurationSeconds = legDurationSeconds;
    }

    public int sequence() {
        return sequence;
    }

    public long storeId() {
        return storeId;
    }

    /**
     * Seconds from the start of the route, so the screen can show "about 2 h 10 min in".
     *
     * <p>{@code 0} when the route was planned locally - no clock, so nothing to report.
     */
    public long arrivalSeconds() {
        return arrivalSeconds;
    }

    /**
     * Metres from the previous stop - a straight-line estimate when the plan was made on the device.
     *
     * <p>{@code 0} on the first stop when the route had no start point. There was nothing to measure from,
     * and the screen shows no distance there rather than a zero that would read as "you are already here".
     */
    public double legDistanceMetres() {
        return legDistanceMetres;
    }

    /** Seconds from the previous stop, or {@code 0} when it is not known. */
    public long legDurationSeconds() {
        return legDurationSeconds;
    }

    @NonNull
    @Override
    public String toString() {
        return "OptimizationStop(#" + sequence + ", store " + storeId + ")";
    }
}

package com.cph.alpharoute.navigation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cph.alpharoute.domain.RoutePlan;

/**
 * Where a route request currently stands.
 *
 * <p>One object rather than two pieces of state, and that is the point. A screen showing "Calculating
 * route…" while a stale route is still on the map - or showing an error while a route is drawn - is what
 * happens when the loading flag and the result are kept separately and drift apart. Here a state either
 * has a route or it does not, and there is no way to express the combination that makes no sense.
 *
 * <p>The statuses are exactly the situations the screen plan asks the Route Preview screen to handle
 * (§13.8), plus one it could not have known about: {@link Status#NOT_CONFIGURED}, because the routing key
 * is a build-time setting and a checkout without one must say so rather than send a request that comes
 * back 401.
 */
public final class RouteState {

    /** What a route request is doing, or why it did not work. */
    public enum Status {

        /** Nothing has been asked for. No route is drawn. */
        IDLE,

        /** The request is in flight. The polyline is deliberately not drawn yet. */
        LOADING,

        /** A real route arrived. This is the only status that carries a plan. */
        READY,

        /** No usable GPS fix yet, so there is nowhere to route from. */
        NO_LOCATION,

        /** This build has no routing API key - see {@code RoutingConfig}. */
        NOT_CONFIGURED,

        /** The request never reached the service, or the answer never came back. */
        OFFLINE,

        /** The free plan's daily allowance is used up. */
        QUOTA_EXHAUSTED,

        /** The service answered, but not with a route. */
        FAILED
    }

    private final Status status;

    @Nullable
    private final RoutePlan plan;

    private RouteState(@NonNull Status status, @Nullable RoutePlan plan) {
        this.status = status;
        this.plan = plan;
    }

    public static RouteState idle() {
        return new RouteState(Status.IDLE, null);
    }

    public static RouteState loading() {
        return new RouteState(Status.LOADING, null);
    }

    /** A route arrived. The only way to reach {@link Status#READY}. */
    public static RouteState ready(@NonNull RoutePlan plan) {
        return new RouteState(Status.READY, plan);
    }

    /**
     * A request that did not produce a route.
     *
     * @param status one of the failure statuses. Passing {@code IDLE}, {@code LOADING} or {@code READY}
     *               throws, because a caller that did so has confused a state with a failure and would
     *               otherwise get a screen showing an error with no message.
     */
    public static RouteState failed(@NonNull Status status) {
        if (status == Status.IDLE || status == Status.LOADING || status == Status.READY) {
            throw new IllegalArgumentException("Not a failure status: " + status);
        }
        return new RouteState(status, null);
    }

    @NonNull
    public Status status() {
        return status;
    }

    /** The route, or {@code null} for every status except {@link Status#READY}. */
    @Nullable
    public RoutePlan plan() {
        return plan;
    }

    public boolean isReady() {
        return status == Status.READY;
    }

    public boolean isLoading() {
        return status == Status.LOADING;
    }

    @Override
    public String toString() {
        return "RouteState{" + status + (plan == null ? "" : ", " + plan) + '}';
    }
}

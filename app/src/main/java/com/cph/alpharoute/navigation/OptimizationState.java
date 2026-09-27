package com.cph.alpharoute.navigation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cph.alpharoute.domain.OptimizationPlan;

/**
 * The state of an optimization request, as one object.
 *
 * <p>The same shape as {@link RouteState}, and for the same reason: a screen that read a status and then a
 * plan as two separate values could catch them disagreeing, and render a failure beside a stale route. One
 * immutable object makes that unrepresentable.
 */
public final class OptimizationState {

    /** What happened. */
    public enum Status {

        /** Nothing has been asked for. */
        IDLE,

        /** A request is in flight. */
        LOADING,

        /** A route came back. */
        READY,

        /**
         * This build has no API key, so no request was sent.
         *
         * <p>Distinct from {@link #FAILED} because the user's next move is different: this one needs the key
         * configured, not a retry.
         */
        NOT_CONFIGURED,

        /**
         * More stores than the service accepts in one request.
         *
         * <p>A service limit, not a bug - and the screen says so, with the number, rather than reporting a
         * generic failure the user cannot act on.
         */
        TOO_MANY_STOPS,

        /** The service could not be reached. */
        OFFLINE,

        /** The daily or per-minute allowance is used up. */
        QUOTA_EXHAUSTED,

        /** Anything else, including an answer that could not be read. */
        FAILED
    }

    private final Status status;
    @Nullable
    private final OptimizationPlan plan;
    @NonNull
    private final String detail;

    private OptimizationState(Status status, @Nullable OptimizationPlan plan, @NonNull String detail) {
        this.status = status;
        this.plan = plan;
        this.detail = detail;
    }

    @NonNull
    public static OptimizationState idle() {
        return new OptimizationState(Status.IDLE, null, "");
    }

    @NonNull
    public static OptimizationState loading() {
        return new OptimizationState(Status.LOADING, null, "");
    }

    @NonNull
    public static OptimizationState ready(@NonNull OptimizationPlan plan) {
        return new OptimizationState(Status.READY, plan, "");
    }

    @NonNull
    public static OptimizationState failed(@NonNull Status status) {
        if (status == Status.READY) {
            throw new IllegalArgumentException("Use ready(plan) for READY");
        }
        return new OptimizationState(status, null, "");
    }

    /**
     * A failure with the raw reason attached.
     *
     * <p>Carried for the same reason the sign-in failure carries one: several very different problems -
     * a rejected key, a quota, a malformed answer - all arrive as "could not build a route", and the raw
     * reason is the only thing that tells them apart from outside the app.
     */
    @NonNull
    public static OptimizationState failed(@NonNull Status status, @NonNull String detail) {
        if (status == Status.READY) {
            throw new IllegalArgumentException("Use ready(plan) for READY");
        }
        return new OptimizationState(status, null, detail);
    }

    /** The raw reason a failure happened, or an empty string. */
    @NonNull
    public String detail() {
        return detail;
    }

    @NonNull
    public Status status() {
        return status;
    }

    @Nullable
    public OptimizationPlan plan() {
        return plan;
    }

    public boolean isReady() {
        return status == Status.READY && plan != null;
    }

    public boolean isLoading() {
        return status == Status.LOADING;
    }

    @NonNull
    @Override
    public String toString() {
        return "OptimizationState(" + status + ")";
    }
}

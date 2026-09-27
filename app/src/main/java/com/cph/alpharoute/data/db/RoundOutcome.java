package com.cph.alpharoute.data.db;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * How an attempt to start a new round ended.
 *
 * <p>Same shape as {@link CompletionOutcome}, and for the same reason: the caller has to say three
 * different things, and only one of them means the database changed.
 *
 * <p><b>Only {@link Status#SUCCESS} means anything was written.</b> The two refusals leave every store and
 * every completion exactly as they were.
 */
public final class RoundOutcome {

    /** What happened. */
    public enum Status {

        /**
         * The new round began: {@code currentRound} advanced and every store was reset to PENDING.
         *
         * <p>This is the only status in which anything was written.
         */
        SUCCESS,

        /**
         * The current round is not finished yet. Nothing was written.
         *
         * <p>Starting a new round early would discard the round in progress - the stores already visited
         * would go back to orange with nothing recording that the pass was abandoned. The user is told
         * which stores are still to go, and nothing changes.
         */
        ROUND_NOT_COMPLETE,

        /**
         * There are no stores at all. Nothing was written.
         *
         * <p>"Every store is complete" is vacuously true over an empty database, so without this guard the
         * user could start round 2 of nothing.
         */
        NO_STORES,

        /** Something unexpected went wrong. Nothing was written; the cause is logged. */
        FAILED
    }

    private final Status status;

    /** The round now in progress, or {@code 0} unless {@link Status#SUCCESS}. */
    private final int newRound;

    /** How many stores were reset, or {@code 0} unless {@link Status#SUCCESS}. */
    private final int storesReset;

    /** How many stores were still to visit, on {@link Status#ROUND_NOT_COMPLETE}. */
    private final int stillToVisit;

    private RoundOutcome(Status status, int newRound, int storesReset, int stillToVisit) {
        this.status = status;
        this.newRound = newRound;
        this.storesReset = storesReset;
        this.stillToVisit = stillToVisit;
    }

    /** A round that began. */
    @NonNull
    public static RoundOutcome success(int newRound, int storesReset) {
        return new RoundOutcome(Status.SUCCESS, newRound, storesReset, 0);
    }

    /** A refusal. Nothing was written. */
    @NonNull
    public static RoundOutcome refusal(@NonNull Status status, int stillToVisit) {
        if (status == Status.SUCCESS) {
            throw new IllegalArgumentException("Use success(newRound, storesReset) for SUCCESS");
        }
        return new RoundOutcome(status, 0, 0, stillToVisit);
    }

    @NonNull
    public Status status() {
        return status;
    }

    /** Whether the round began. The only status in which the database changed. */
    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    /** The round now in progress, or {@code 0} when nothing changed. */
    public int newRound() {
        return newRound;
    }

    /** How many stores went back to PENDING, or {@code 0} when nothing changed. */
    public int storesReset() {
        return storesReset;
    }

    /** How many stores were still to visit, when the round was refused as incomplete. */
    public int stillToVisit() {
        return stillToVisit;
    }

    @NonNull
    @Override
    public String toString() {
        return isSuccess()
                ? "RoundOutcome(SUCCESS, round " + newRound + ", " + storesReset + " reset)"
                : "RoundOutcome(" + status + (stillToVisit > 0 ? ", " + stillToVisit + " to go" : "") + ")";
    }
}

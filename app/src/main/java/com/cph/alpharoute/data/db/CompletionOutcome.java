package com.cph.alpharoute.data.db;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * How the completion transaction ended.
 *
 * <p><b>Why this is a value and not a boolean or an enum.</b> A completion is the only write in the app
 * that changes a count, and the caller has to say three different things about it: whether it committed,
 * which day it was filed under, and which number it got that day. A bare {@code boolean} would throw away
 * the last two, and the screen plan's success message names the daily sequence - "Store #3 today" - which
 * cannot be built from a boolean.
 *
 * <p><b>Only {@link Status#SUCCESS} means anything changed.</b> Every other status is a refusal, and in
 * every one of them the database is exactly as it was. That is the property the whole design exists to
 * provide, and it is why the refusals are distinct values rather than one "failed": the screen has to tell
 * the user *which* thing happened, and "Already counted" is not an error - it is the duplicate guard doing
 * its job.
 *
 * <p>Instances are created by the factory methods below and are immutable.
 */
public final class CompletionOutcome {

    /** What happened. */
    public enum Status {

        /**
         * The completion committed: one history row was inserted and the store is now COMPLETED.
         *
         * <p>This is the only status in which anything was written.
         */
        SUCCESS,

        /**
         * The store was already completed, or already had a history row. Nothing was written.
         *
         * <p>Reached from any of the three guards: the store's own status, an existing history row, or -
         * in the narrowest race - the UNIQUE index on {@code storeId} rejecting the insert (AD-5). The
         * user is told "Already counted", which is information, not a failure.
         */
        ALREADY_COUNTED,

        /**
         * The store id does not exist. Nothing was written.
         *
         * <p>Usually means the store was deleted while the confirmation dialog was open - a real
         * possibility, because the dialog survives a rotation and the user can leave it sitting there.
         */
        STORE_NOT_FOUND,

        /**
         * The completion row the caller named does not exist. Nothing was written.
         *
         * <p>Only produced by the undo (Version 7): the row was already removed, by a second tap or by
         * another screen. It is not a failure - the requested end state is already the current state - so
         * the caller treats it the same as success and simply says the record is gone.
         */
        COMPLETION_NOT_FOUND,

        /** Something unexpected went wrong. Nothing was written; the cause is logged. */
        FAILED
    }

    private final Status status;

    /** The local day the completion was filed under, or {@code null} unless {@link Status#SUCCESS}. */
    @Nullable
    private final String completionDateKey;

    /** The 1-based position within that day, or {@code 0} unless {@link Status#SUCCESS}. */
    private final int dailySequence;

    private CompletionOutcome(@NonNull Status status,
                              @Nullable String completionDateKey,
                              int dailySequence) {
        this.status = status;
        this.completionDateKey = completionDateKey;
        this.dailySequence = dailySequence;
    }

    /** A committed completion, carrying the day it was filed under and its position in that day. */
    @NonNull
    public static CompletionOutcome success(@NonNull String completionDateKey, int dailySequence) {
        return new CompletionOutcome(Status.SUCCESS, completionDateKey, dailySequence);
    }

    /** A refusal. Nothing was written. */
    @NonNull
    public static CompletionOutcome refusal(@NonNull Status status) {
        if (status == Status.SUCCESS) {
            // Guarding the guard. A "refusal" that claims success would carry a null date key and a
            // sequence of 0, and the caller would try to build a success message out of them.
            throw new IllegalArgumentException("Use success(dateKey, sequence) for SUCCESS");
        }
        return new CompletionOutcome(status, null, 0);
    }

    @NonNull
    public Status status() {
        return status;
    }

    /** Whether the completion committed. The only status in which the database changed. */
    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    /**
     * The local day the completion was filed under, {@code YYYY-MM-DD}.
     *
     * @return the key on {@link Status#SUCCESS}, {@code null} otherwise
     */
    @Nullable
    public String completionDateKey() {
        return completionDateKey;
    }

    /**
     * The 1-based position within that day - 1 for the first store completed that day.
     *
     * @return the sequence on {@link Status#SUCCESS}, {@code 0} otherwise
     */
    public int dailySequence() {
        return dailySequence;
    }

    @NonNull
    @Override
    public String toString() {
        return isSuccess()
                ? "CompletionOutcome(SUCCESS, " + completionDateKey + ", #" + dailySequence + ")"
                : "CompletionOutcome(" + status + ")";
    }
}

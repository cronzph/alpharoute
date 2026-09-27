package com.cph.alpharoute.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * Every figure the Dashboard shows, computed once from the database.
 *
 * <p><b>One immutable object per emission, not a dozen loose numbers.</b> A reader that fetched them one at
 * a time could catch the set half-updated - "12 remaining" next to "18 of 30 done" from two different
 * moments, which do not add up. A single snapshot makes that unrepresentable, exactly as
 * {@code NavigationSession.Snapshot} does for the arrival screen.
 *
 * <p><b>Two sets of figures, and the difference matters.</b> Since Version 7 the app counts rounds, so
 * there is a <i>lifetime</i> view and a <i>this round</i> view:
 *
 * <ul>
 *   <li>{@link #overallCompleted()} is every completion ever recorded, across every round. It never goes
 *       down when a new round starts.</li>
 *   <li>{@link #completedInRound()} and {@link #remainingInRound()} are about the pass in progress. These
 *       are the ones that answer "what do I still have to do today", and they are what the Dashboard's
 *       Remaining and Completion cards show.</li>
 * </ul>
 *
 * <p><b>Nothing here is stored.</b> Every value is derived from the {@code stores} and
 * {@code completion_history} rows on every change, because a stored counter can drift out of step with the
 * records and then there is no way to tell which one is right.
 *
 * <p>Pure Java, zero Android imports (AD-11), so the arithmetic is testable on the JVM.
 */
public final class TrackerSnapshot {

    private final int currentRound;
    private final int totalStores;
    private final int completedToday;
    private final int completedYesterday;
    private final int overallCompleted;
    private final int completedInRound;
    private final int remainingInRound;
    private final double roundPercentage;
    @Nullable
    private final String latestStoreCode;
    private final long latestCompletionTimestamp;

    TrackerSnapshot(int currentRound,
                    int totalStores,
                    int completedToday,
                    int completedYesterday,
                    int overallCompleted,
                    int completedInRound,
                    int remainingInRound,
                    double roundPercentage,
                    @Nullable String latestStoreCode,
                    long latestCompletionTimestamp) {
        this.currentRound = currentRound;
        this.totalStores = totalStores;
        this.completedToday = completedToday;
        this.completedYesterday = completedYesterday;
        this.overallCompleted = overallCompleted;
        this.completedInRound = completedInRound;
        this.remainingInRound = remainingInRound;
        this.roundPercentage = roundPercentage;
        this.latestStoreCode = latestStoreCode;
        this.latestCompletionTimestamp = latestCompletionTimestamp;
    }

    /** The round being worked through. Starts at 1. */
    public int currentRound() {
        return currentRound;
    }

    /**
     * How many rounds have been finished.
     *
     * <p>{@code currentRound - 1}. The round in progress is not finished, so it is not counted - a route
     * that has just begun its third pass has completed two.
     */
    public int roundsFinished() {
        return Math.max(0, currentRound - 1);
    }

    /** Every store in the database, completed or not. */
    public int totalStores() {
        return totalStores;
    }

    /** Stores completed on the device's local calendar day. */
    public int completedToday() {
        return completedToday;
    }

    /** Stores completed on the previous local calendar day. */
    public int completedYesterday() {
        return completedYesterday;
    }

    /** Every completion ever recorded, across every round - the number of history rows. */
    public int overallCompleted() {
        return overallCompleted;
    }

    /** Stores completed in the round in progress. */
    public int completedInRound() {
        return completedInRound;
    }

    /**
     * Stores still to visit in the round in progress.
     *
     * <p>Never negative. If a round somehow counted more completions than there are stores, a negative
     * figure would be meaningless on screen - and the discrepancy itself is the consistency check's
     * business, not a number to show here.
     */
    public int remainingInRound() {
        return remainingInRound;
    }

    /**
     * How much of the round in progress is done, as a percentage.
     *
     * <p><b>Zero when there are no stores, not a division by zero.</b> An empty database is the state the
     * app starts in, so it is an ordinary case rather than an error.
     */
    public double roundPercentage() {
        return roundPercentage;
    }

    /** The round percentage to one decimal place, which is what the screen plan asks for. */
    @NonNull
    public String roundPercentageText() {
        return String.format(Locale.US, "%.1f%%", roundPercentage);
    }

    /** Whether every store has been completed in the round in progress. */
    public boolean isRoundComplete() {
        return totalStores > 0 && completedInRound >= totalStores;
    }

    /**
     * The store code of the most recent completion, or {@code null} when nothing has been completed.
     *
     * <p>{@code null} rather than an empty string: the Dashboard shows a dash for "nothing yet", and an
     * empty string would render as a blank card that looks broken.
     */
    @Nullable
    public String latestStoreCode() {
        return latestStoreCode;
    }

    /** When the most recent completion happened, or {@code 0} when there is none. */
    public long latestCompletionTimestamp() {
        return latestCompletionTimestamp;
    }

    /** Whether there is a latest completion to name. */
    public boolean hasLatest() {
        return latestStoreCode != null && latestCompletionTimestamp > 0L;
    }

    /** Whether the database holds any store at all. Drives the Dashboard's empty state. */
    public boolean hasStores() {
        return totalStores > 0;
    }
}

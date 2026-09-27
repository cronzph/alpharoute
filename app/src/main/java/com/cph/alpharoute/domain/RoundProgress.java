package com.cph.alpharoute.domain;

/**
 * How far through the current round the user is.
 *
 * <p><b>One round is one complete pass of every store.</b> A store is completed once per round, and the
 * round is finished when every store in the database has a completion belonging to it. That is the whole
 * definition - there is no separate "round" table, because a round is entirely described by which
 * completions carry its number.
 *
 * <p><b>Computed, never stored.</b> The progress is derived from the rows each time it is asked for, so it
 * cannot drift from the completions it is describing. The only stored part is the round <i>number</i>
 * ({@code app_settings.currentRound}), because a round with nothing completed yet has no rows at all and
 * could not otherwise be told apart from the previous round.
 *
 * <p>Pure Java, zero Android imports (AD-11).
 */
public final class RoundProgress {

    private final int currentRound;
    private final int completedInRound;
    private final int totalStores;

    RoundProgress(int currentRound, int completedInRound, int totalStores) {
        this.currentRound = currentRound;
        this.completedInRound = completedInRound;
        this.totalStores = totalStores;
    }

    /** The round being worked through. Starts at 1. */
    public int currentRound() {
        return currentRound;
    }

    /** How many stores have been completed in this round. */
    public int completedInRound() {
        return completedInRound;
    }

    /** How many stores exist. */
    public int totalStores() {
        return totalStores;
    }

    /** How many stores are still to visit in this round. Never negative. */
    public int remainingInRound() {
        return Math.max(0, totalStores - completedInRound);
    }

    /**
     * Whether every store has been completed in this round.
     *
     * <p><b>False when there are no stores at all.</b> "Every store is done" is vacuously true over an
     * empty set, and a round that completed itself before the user added anything would be nonsense - it
     * would let them start round 2 of nothing.
     */
    public boolean isComplete() {
        return totalStores > 0 && completedInRound >= totalStores;
    }

    /**
     * Whether a new round can be started.
     *
     * <p>The same condition as {@link #isComplete()}, named separately because the two are asked in
     * different places and mean different things to the caller: the Dashboard asks whether to show a
     * "round complete" message, the Settings screen asks whether the button should be enabled.
     */
    public boolean canStartNewRound() {
        return isComplete();
    }

    /** The fraction of this round done, as a percentage with one decimal place. */
    public double percentage() {
        return totalStores == 0 ? 0.0 : (completedInRound * 100.0) / totalStores;
    }
}

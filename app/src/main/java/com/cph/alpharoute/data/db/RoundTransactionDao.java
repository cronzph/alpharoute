package com.cph.alpharoute.data.db;

import androidx.annotation.NonNull;
import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.Transaction;

import com.cph.alpharoute.domain.StoreStatus;

/**
 * Starting a new round - the only thing in the app that resets a store's status.
 *
 * <p><b>What a new round is.</b> One round is one complete pass of every store. When the last store is
 * confirmed, the user can begin the next pass: the round number advances and every store goes back to
 * PENDING so the map shows what is still to visit. The history rows from the finished round stay exactly
 * where they are - they are the record of what happened, and nothing here touches them.
 *
 * <p><b>Why the reset is a transaction spanning both tables.</b> Advancing the round number and resetting
 * the stores must happen together. If the number advanced and the reset failed, the app would be in round 2
 * with every store still reading COMPLETED - the map would show a finished route and the round would look
 * already complete. If the reset happened and the number did not, every store would be PENDING while its
 * completion was filed under the old round, and confirming them again would be refused by the unique index.
 * Neither state is recoverable by the user, so neither is allowed to exist.
 *
 * <p><b>It does not delete anything.</b> The reset is a status change on the stores. Every completion row
 * survives, which is what makes the History screen able to show past rounds at all.
 */
@Dao
public abstract class RoundTransactionDao {

    // ------------------------------------------------------------------ the pieces

    /** The round in progress, or 1 if the settings row is somehow missing. */
    @Query("SELECT IFNULL((SELECT currentRound FROM app_settings WHERE id = 1), 1)")
    abstract int currentRound();

    /** Every store. */
    @Query("SELECT COUNT(*) FROM stores")
    abstract int countStores();

    /**
     * How many stores have <b>no</b> completion in the given round.
     *
     * <p>{@code NOT EXISTS} rather than comparing two counts. Comparing counts would be satisfied by any
     * combination that happened to add up - a store with two rows in the round and another with none would
     * look complete. This asks the actual question, per store.
     */
    @Query("SELECT COUNT(*) FROM stores s WHERE NOT EXISTS ("
            + "SELECT 1 FROM completion_history h "
            + "WHERE h.storeId = s.id AND h.roundNumber = :roundNumber)")
    abstract int countStoresWithoutCompletionIn(int roundNumber);

    /** Advances the round and clears the active-navigation reference, which belonged to the old round. */
    @Query("UPDATE app_settings SET currentRound = :roundNumber, "
            + "lastSelectedStoreId = NULL, lastUpdated = :nowMillis WHERE id = 1")
    abstract int setCurrentRound(int roundNumber, long nowMillis);

    /**
     * Puts every store back to PENDING.
     *
     * <p>{@code completedAt = NULL} as SQL {@code NULL}, never {@code 0} - {@code 0} is a real timestamp
     * and would put a completion date on a store that has not been completed in this round.
     *
     * <p>{@code status} is passed as the enum rather than written as a {@code 'PENDING'} literal, so
     * {@link StoreStatus} stays the single definition of the status. A literal would silently disagree the
     * moment a constant was renamed, and the symptom would be every store staying green after a new round.
     */
    @Query("UPDATE stores SET status = :status, completedAt = NULL, updatedAt = :nowMillis")
    abstract int resetAllStores(StoreStatus status, long nowMillis);

    // ------------------------------------------------------------------ the transaction

    /**
     * Begins the next round.
     *
     * <p><b>The round is re-checked here, inside the transaction.</b> The Settings screen asks the pure
     * {@code RoundCalculator} whether the button should be enabled, but that answer was computed from an
     * earlier read of the database. A store could have been added - or a completion undone - in between,
     * which would make the round incomplete again. Deciding on the caller's answer would start round 2
     * with round 1 unfinished.
     *
     * @param nowMillis the moment the round began, read once by the repository
     * @return {@link RoundOutcome}. Only {@link RoundOutcome.Status#SUCCESS} means anything was written
     * @throws RuntimeException if the stores could not be reset after the round number advanced. Throwing
     *         rolls the whole thing back, so the app can never be in round 2 with a finished-looking map
     */
    @Transaction
    @NonNull
    public RoundOutcome startNewRound(long nowMillis) {

        int stores = countStores();
        if (stores == 0) {
            // "Every store is complete" is vacuously true over nothing, so this guard is what stops the
            // user starting round 2 of an empty route.
            return RoundOutcome.refusal(RoundOutcome.Status.NO_STORES, 0);
        }

        int current = currentRound();
        int outstanding = countStoresWithoutCompletionIn(current);
        if (outstanding > 0) {
            return RoundOutcome.refusal(RoundOutcome.Status.ROUND_NOT_COMPLETE, outstanding);
        }

        int next = current + 1;
        setCurrentRound(next, nowMillis);

        int reset = resetAllStores(StoreStatus.PENDING, nowMillis);
        if (reset != stores) {
            // Some stores were not reset. Throwing rolls back both the reset and the round number, because
            // a partially reset route is the one state that would leave the map lying about what is left.
            throw new IllegalStateException(
                    "Round rollback: reset " + reset + " of " + stores + " stores");
        }

        return RoundOutcome.success(next, reset);
    }

    /** The round in progress, for callers that only need to read it. */
    public int readCurrentRound() {
        return currentRound();
    }

    /**
     * How many stores are still to visit in the round in progress.
     *
     * <p>Zero means the round is finished and a new one can begin. Used by the Settings screen to explain
     * why the button is disabled, so the user is told what is missing rather than left with a dead control.
     */
    public int outstandingInCurrentRound() {
        return countStoresWithoutCompletionIn(currentRound());
    }
}

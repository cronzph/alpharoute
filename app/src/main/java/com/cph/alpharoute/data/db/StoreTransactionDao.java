package com.cph.alpharoute.data.db;

import androidx.annotation.NonNull;
import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.Transaction;

/**
 * Deleting a store together with its completion history.
 *
 * <p><b>Version 13: a completed store can now be deleted.</b> Until now AD-6's {@code onDelete = RESTRICT}
 * foreign key made that impossible, and the app refused it with "this store has completion history and
 * cannot be deleted". That was the right default - but it left no way at all to remove a store that was
 * completed by mistake, or that has closed down.
 *
 * <h3>Why this does NOT change the foreign key</h3>
 *
 * <p>Changing {@code RESTRICT} to {@code CASCADE} would mean recreating the {@code completion_history}
 * table, because a foreign key is part of a table's {@code CREATE} statement in SQLite. That is a schema
 * change, a migration, and a rewrite of every row - for a behaviour that this method already provides
 * without any of it.
 *
 * <p><b>So AD-6 stays exactly as it was, and the cascade is done here instead:</b> the history rows go
 * first, then the store. By the time the store row is deleted, nothing references it, so the constraint is
 * satisfied and nothing is ever orphaned. The database refuses a delete that would orphan a row; this
 * method simply does both halves of the job.
 *
 * <h3>The transaction is the safety</h3>
 *
 * <p>One {@code @Transaction}, and every failure path throws - so a delete that got half way rolls the
 * whole thing back. The alternative is a store with its history gone but the store still listed, or a
 * store gone with its history still pointing at nothing.
 */
@Dao
public abstract class StoreTransactionDao {

    @Query("DELETE FROM completion_history WHERE storeId = :storeId")
    abstract int deleteHistoryForStore(long storeId);

    @Query("DELETE FROM stores WHERE id = :storeId")
    abstract int deleteStoreById(long storeId);

    @Query("SELECT COUNT(*) FROM completion_history WHERE storeId = :storeId")
    public abstract int countHistoryForStore(long storeId);

    /**
     * Deletes a store and every completion recorded against it.
     *
     * <p><b>The order is not optional.</b> History first, store second. The other order is refused by
     * AD-6's foreign key - SQLite will not delete a store that still has history rows pointing at it.
     *
     * @param storeId the store to remove
     * @return how many completion records went with it, for the message that follows
     * @throws IllegalStateException if either delete did not happen. The throw is what rolls the
     *                               transaction back, so a failed delete leaves the database untouched
     */
    @Transaction
    public int deleteWithHistory(long storeId) {
        int historyDeleted = deleteHistoryForStore(storeId);
        int storeDeleted = deleteStoreById(storeId);

        if (storeDeleted != 1) {
            // Nothing was deleted - the id did not exist. Throwing rolls the history delete back too, so a
            // store cannot lose its records to a delete that did not remove it.
            throw new IllegalStateException(
                    "Delete store " + storeId + " removed no rows; the delete was rolled back");
        }

        return historyDeleted;
    }
}

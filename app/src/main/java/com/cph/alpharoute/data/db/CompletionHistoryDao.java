package com.cph.alpharoute.data.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

/**
 * Database access for {@link CompletionHistoryEntity}.
 *
 * <p>This table is the tracker's source of truth. Every figure on the Dashboard is derived from it
 * joined with {@code stores} - nothing is stored as a counter, because a counter can drift out of step
 * with the records and then there is no way to know which one is right.
 *
 * <p><b>No update or delete method exists on this DAO, and that is deliberate.</b> A completion event
 * is a historical fact: once recorded it is only ever read. Phase 9's import may rewrite the table, but
 * that will be an explicit, transactional operation, not a routine edit.
 */
@Dao
public interface CompletionHistoryDao {

    // ---------------------------------------------------------------- writes

    /** @return the new row id. Rejected by the unique index if the store already has a completion. */
    @Insert
    long insert(CompletionHistoryEntity history);

    /** @return the new row ids, in input order. */
    @Insert
    List<Long> insertAll(List<CompletionHistoryEntity> history);

    // ---------------------------------------------------------------- reads

    /**
     * Every completion, newest day first and, within a day, in the real order of completion.
     *
     * <p>The secondary sort on {@code dailySequence} is what preserves the true order the user
     * completed stores in - not the route-number order.
     */
    @Query("SELECT * FROM completion_history ORDER BY completionDateKey DESC, dailySequence ASC")
    LiveData<List<CompletionHistoryEntity>> observeAllOrdered();

    /** One local day's completions, earliest first. */
    @Query("SELECT * FROM completion_history WHERE completionDateKey = :dateKey ORDER BY dailySequence ASC")
    LiveData<List<CompletionHistoryEntity>> observeByDateKey(String dateKey);

    /**
     * The most recent completion for a store, or {@code null} if it has none.
     *
     * <p>Since Version 7 a store can have one row per round, so this returns the newest - the one that
     * describes the store's current state.
     */
    @Query("SELECT * FROM completion_history WHERE storeId = :storeId "
            + "ORDER BY roundNumber DESC LIMIT 1")
    CompletionHistoryEntity findByStoreId(long storeId);

    /** One row by its own id, for the Completion Detail screen and its undo. */
    @Query("SELECT * FROM completion_history WHERE id = :historyId")
    CompletionHistoryEntity findById(long historyId);

    @Query("SELECT * FROM completion_history WHERE completionDateKey = :dateKey ORDER BY dailySequence ASC")
    List<CompletionHistoryEntity> getByDateKeyNow(String dateKey);

    @Query("SELECT * FROM completion_history ORDER BY completionTimestamp ASC")
    List<CompletionHistoryEntity> getAllNow();

    @Query("SELECT * FROM completion_history ORDER BY completionTimestamp DESC LIMIT 1")
    CompletionHistoryEntity getLatestNow();

    @Query("SELECT COUNT(*) FROM completion_history")
    int countAll();

    /** Used by the Dashboard's "Completed today" and "Completed yesterday" figures. */
    @Query("SELECT COUNT(*) FROM completion_history WHERE completionDateKey = :dateKey")
    int countByDateKey(String dateKey);

    /** Used by the Dashboard's "Latest completed" card. */
    @Query("SELECT COUNT(*) FROM completion_history WHERE storeId = :storeId")
    int countForStore(long storeId);

    /**
     * The next daily sequence number for a local day: 1 when nothing has been completed yet.
     *
     * <p>Read and used inside the completion transaction in Phase 5.3, together with the
     * single-threaded disk executor (AD-3), so two completions can never be assigned the same
     * sequence.
     *
     * <p>{@code IFNULL} rather than {@code MAX(...) + 1} alone, because {@code MAX} returns NULL for
     * an empty set and NULL + 1 is still NULL.
     */
    @Query("SELECT IFNULL(MAX(dailySequence), 0) + 1 FROM completion_history WHERE completionDateKey = :dateKey")
    int getNextDailySequence(String dateKey);
}

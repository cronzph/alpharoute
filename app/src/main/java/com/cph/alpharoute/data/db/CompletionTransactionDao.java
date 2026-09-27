package com.cph.alpharoute.data.db;

import android.database.sqlite.SQLiteConstraintException;

import androidx.annotation.NonNull;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;

import com.cph.alpharoute.domain.StoreStatus;
import com.cph.alpharoute.util.DateKeyHelper;

import java.time.ZoneId;

/**
 * The completion transaction - the only thing in the app that writes a completion.
 *
 * <p><b>Why this is a separate DAO rather than a method on {@code CompletionHistoryDao}.</b> The
 * operation spans two tables: it inserts into {@code completion_history} and flips a row in
 * {@code stores}. Room's {@code @Transaction} only covers one DAO, so a method that did half the work in
 * one DAO and half in the other would not be atomic at all - and a half-applied completion is the one
 * corruption this project cannot tolerate: a store marked COMPLETED with no history row, or a history
 * row for a store still shown as pending. Both would be invisible until the counts drifted.
 *
 * <p><b>Adding a DAO does not change the database schema.</b> Only entities do. The exported schema
 * stays {@code 1.json}, no {@code Migration} is written, and {@code DATABASE_VERSION} does not move.
 *
 * <p><b>The contract is {@code docs/PHASE_0.2_ARCHITECTURE.md} §13.7</b>, and every step below maps to a
 * numbered line of it. It is reproduced here as code because it is the single most safety-critical
 * operation in the project.
 *
 * <p><b>Three independent guards make double counting impossible, and they are not redundant:</b>
 *
 * <ol>
 *   <li><b>The re-read inside the transaction.</b> The store is fetched again here, not passed in. A
 *       {@code StoreEntity} handed in by a screen was read at some earlier moment and may already be
 *       stale - the whole point is to decide on what is true <i>now</i>.</li>
 *   <li><b>The UNIQUE index on {@code storeId} (AD-5).</b> Even if the two checks above somehow passed, the
 *       INSERT itself is refused by SQLite. This is the guard that turns "we check first" into "it cannot
 *       happen".</li>
 *   <li><b>The single-threaded disk executor (AD-3).</b> Two completions cannot interleave, so the
 *       read-then-write sequence below is never observed half-finished by another write.</li>
 * </ol>
 *
 * <p>Each guard catches a different failure: stale data, a logic bug, and a threading mistake.
 */
@Dao
public abstract class CompletionTransactionDao {

    // ------------------------------------------------------------------ the pieces of the transaction

    /**
     * Step 1 - the store, read again inside the transaction.
     *
     * <p>Deliberately not "get by id from the caller". A cached entity would let a store that was deleted
     * a moment ago be completed anyway, and would let a store already flipped to COMPLETED slip past
     * step 3 on a stale PENDING value.
     */
    @Query("SELECT * FROM stores WHERE id = :storeId")
    abstract StoreEntity findStoreById(long storeId);

    /**
     * Step 4 - how many completion rows already exist for this store <b>in this round</b>.
     *
     * <p>The round is part of the question, not an afterthought. A store completed in round 1 is
     * <i>not</i> completed in round 2, so counting all its history rows would refuse a perfectly valid
     * second-round completion. This is the same scoping the UNIQUE index uses.
     */
    @Query("SELECT COUNT(*) FROM completion_history WHERE storeId = :storeId AND roundNumber = :roundNumber")
    abstract int countHistoryForStore(long storeId, int roundNumber);

    /**
     * Step 6 - the next position within a day.
     *
     * <p>{@code IFNULL} rather than a bare {@code MAX(...) + 1}: {@code MAX} returns NULL over an empty
     * set, and NULL + 1 is still NULL, so the first completion of a day would be filed as nothing at all.
     */
    @Query("SELECT IFNULL(MAX(dailySequence), 0) + 1 FROM completion_history WHERE completionDateKey = :dateKey")
    abstract int nextDailySequence(String dateKey);

    /** Step 7 - the history row. @return the new row id. */
    @Insert
    abstract long insertHistory(CompletionHistoryEntity history);

    /**
     * Step 8 - flip the store to COMPLETED.
     *
     * <p><b>A targeted UPDATE, not {@code @Update} on the entity.</b> {@code @Update} rewrites every
     * column, so a bug anywhere in this method could silently overwrite {@code createdAt},
     * {@code storeCode} or a coordinate. This statement is incapable of touching anything except the three
     * columns named here - the write is constrained by its own shape.
     *
     * <p>{@code status} is passed as the enum rather than hardcoding {@code 'COMPLETED'} in the SQL. The
     * literal would be a second definition of the status, free to disagree with {@link StoreStatus} the
     * moment anyone renamed a constant - and the symptom would be a store showing as pending while a
     * history row for it existed.
     */
    @Query("UPDATE stores SET status = :status, completedAt = :completedAt, updatedAt = :updatedAt WHERE id = :storeId")
    abstract int markCompleted(long storeId, StoreStatus status, long completedAt, long updatedAt);

    /**
     * The reverse of {@link #markCompleted}, for the undo: the store goes back to PENDING.
     *
     * <p>{@code completedAt = NULL} is written as SQL {@code NULL}, not as a Java {@code 0}. The two are
     * different things and the project treats them as such everywhere: {@code 0} is a real timestamp
     * (1 January 1970) and a real coordinate, so conflating it with "absent" would put a completed date on
     * a store that has never been completed.
     */
    @Query("UPDATE stores SET status = :status, completedAt = NULL, updatedAt = :updatedAt WHERE id = :storeId")
    abstract int markPending(long storeId, StoreStatus status, long updatedAt);

    // ------------------------------------------------------------------ undo

    /** The completion row being undone, or {@code null} if it is already gone. */
    @Query("SELECT * FROM completion_history WHERE id = :historyId")
    abstract CompletionHistoryEntity findHistoryById(long historyId);

    /** @return the number of rows deleted - 0 or 1. */
    @Query("DELETE FROM completion_history WHERE id = :historyId")
    abstract int deleteHistory(long historyId);

    // ------------------------------------------------------------------ the transaction

    /**
     * Records a completion, atomically. Either everything below commits or nothing does.
     *
     * <p><b>What "atomic" buys here.</b> There is no state in which a store is COMPLETED with no history
     * row, or has a history row while still reading as PENDING. Both would break the tracker, and neither
     * would be detectable afterwards - the counts would simply be wrong.
     *
     * @param storeId   the store to complete
     * @param nowMillis the moment of completion, read <b>once</b> by the caller and used for both the
     *                  timestamp and the date key. Reading the clock twice would let a completion that
     *                  started at 23:59:59.999 be filed under the previous day while carrying the new
     *                  day's timestamp - the classic midnight bug, and it is avoided by construction
     * @param zone      the timezone the calendar day is judged in - the device's own in production, and a
     *                  parameter so a test can pin it
     * @param roundNumber the round being completed. Read once by the repository from
     *                  {@code app_settings.currentRound} and passed down, for the same reason the clock is:
     *                  two readings could straddle a round change, and the row would then be filed under a
     *                  round whose guard never checked it
     * @return a {@link CompletionOutcome}. Only {@link CompletionOutcome.Status#SUCCESS} means the
     *         database changed; every other status leaves it exactly as it was
     * @throws RuntimeException if the store row could not be updated after the history row was inserted.
     *         The throw is the point: it makes Room roll the whole transaction back, so the alternative -
     *         a history row for a store still marked pending - never reaches the database
     */
    @Transaction
    @NonNull
    public CompletionOutcome complete(long storeId, long nowMillis, @NonNull ZoneId zone, int roundNumber) {

        // Step 1-2. The store, re-read. Gone means the user deleted it while the dialog was open.
        StoreEntity store = findStoreById(storeId);
        if (store == null) {
            return CompletionOutcome.refusal(CompletionOutcome.Status.STORE_NOT_FOUND);
        }

        // Step 3. Already completed. Refusing here is not an error path - it is the guard working.
        if (store.status == StoreStatus.COMPLETED) {
            return CompletionOutcome.refusal(CompletionOutcome.Status.ALREADY_COUNTED);
        }

        // Step 4. A history row in THIS ROUND without the status flip would mean a half-applied write from
        // an earlier build, or a row written by a round change that did not reset the store. Trusting the
        // row over the status is the conservative reading: history is the record of what happened, and it
        // says this store was already counted.
        //
        // Scoped to the round. A row from round 1 is not a reason to refuse a round-2 completion.
        if (countHistoryForStore(storeId, roundNumber) > 0) {
            return CompletionOutcome.refusal(CompletionOutcome.Status.ALREADY_COUNTED);
        }

        // Step 5. The local calendar day, derived from the same `now` as the timestamp.
        String dateKey = DateKeyHelper.keyOf(nowMillis, zone);

        // Step 6. This day's next position.
        int dailySequence = nextDailySequence(dateKey);

        // Step 7. The history row. The UNIQUE index on (storeId, roundNumber) rejects a second one in the
        // same round - and that rejection is reported, not thrown at the user, because "already counted"
        // is a legitimate answer.
        long rowId;
        try {
            rowId = insertHistory(new CompletionHistoryEntity(
                    storeId, nowMillis, dateKey, dailySequence, roundNumber));
        } catch (SQLiteConstraintException e) {
            // The unique index refused the insert. The two checks above said this store had no history in
            // this round, so reaching here means a write raced past them - which the single-threaded
            // executor makes very unlikely but which the index catches regardless. That is exactly why the
            // index exists: the checks are a courtesy, the index is the guarantee.
            return CompletionOutcome.refusal(CompletionOutcome.Status.ALREADY_COUNTED);
        }

        if (rowId <= 0) {
            // Room returns -1 when it could not report a row id. Treat it as a refusal rather than
            // continuing: without a confirmed row there is nothing to build a success on.
            return CompletionOutcome.refusal(CompletionOutcome.Status.ALREADY_COUNTED);
        }

        // Step 8. Flip the store. completedAt is set to the same `now`, so the store's own timestamp and
        // the history row agree to the millisecond.
        int updated = markCompleted(storeId, StoreStatus.COMPLETED, nowMillis, nowMillis);
        if (updated != 1) {
            // The row vanished between the read and the write. Throwing rolls back the history row that
            // was just inserted - leaving it would be the one corruption this class exists to prevent.
            throw new IllegalStateException(
                    "Completion rollback: store " + storeId + " could not be marked COMPLETED");
        }

        // Step 9-10. The method returning normally is what commits.
        return CompletionOutcome.success(dateKey, dailySequence);
    }

    /**
     * Undoes a completion - the mirror of {@link #complete}, and the only other place in the app that
     * touches {@code completion_history}.
     *
     * <p><b>Why an undo exists at all.</b> A completion is meant to be a historical fact, and for six
     * versions nothing could remove one. But a mis-tap on YES was then unrecoverable: the store could not
     * be completed again, and - because the foreign key uses {@code onDelete = RESTRICT} (AD-6) - it could
     * not even be deleted. That is not a guarantee, it is a trap. The undo is deliberately narrow: it
     * removes one named row, and it cannot touch anything else.
     *
     * <p><b>Atomic, like the completion.</b> The row is deleted and the store reset in one transaction, so
     * there is no state in which a store reads COMPLETED with no history row behind it.
     *
     * <p><b>The store is only reset when the row belonged to the current round.</b> Undoing a completion
     * from an earlier round must not change whether the store is done <i>now</i> - a store is green because
     * of its current-round completion, and an old row has no bearing on that.
     *
     * @param historyId  the exact row to remove. An id, not a store id: with rounds a store has one row per
     *                   round, so "the store's completion" is no longer a single thing
     * @param currentRound the round the user is working in, read once by the repository
     * @param nowMillis  the moment of the undo, for {@code updatedAt}
     * @return {@link CompletionOutcome.Status#SUCCESS} when the row was removed,
     *         {@link CompletionOutcome.Status#COMPLETION_NOT_FOUND} when it was already gone, or
     *         {@link CompletionOutcome.Status#STORE_NOT_FOUND} when the store has since been deleted
     * @throws RuntimeException if the store could not be reset. The throw rolls the delete back, so the
     *         store and its history cannot end up disagreeing
     */
    @Transaction
    @NonNull
    public CompletionOutcome undo(long historyId, int currentRound, long nowMillis) {

        CompletionHistoryEntity row = findHistoryById(historyId);
        if (row == null) {
            // Already undone - a double tap, or the screen was open while the row went. Nothing to do,
            // and nothing wrong: the requested end state is the current state.
            return CompletionOutcome.refusal(CompletionOutcome.Status.COMPLETION_NOT_FOUND);
        }

        int deleted = deleteHistory(historyId);
        if (deleted != 1) {
            throw new IllegalStateException(
                    "Undo rollback: completion " + historyId + " could not be deleted");
        }

        if (row.roundNumber == currentRound) {
            StoreEntity store = findStoreById(row.storeId);
            if (store == null) {
                // The store is gone. AD-6 should make this impossible while history exists, but the row
                // has just been deleted, so the constraint no longer holds. The delete stands - the row
                // was the thing the user asked to remove - and the caller is told the store is missing.
                return CompletionOutcome.refusal(CompletionOutcome.Status.STORE_NOT_FOUND);
            }

            int updated = markPending(row.storeId, StoreStatus.PENDING, nowMillis);
            if (updated != 1) {
                // Throwing rolls back the delete, so a store cannot be left COMPLETED with the history
                // row that justified it already removed.
                throw new IllegalStateException(
                        "Undo rollback: store " + row.storeId + " could not be reset to PENDING");
            }
        }

        // The date key is reported back so the caller can say which day's record was removed. The daily
        // sequence is not meaningful after a delete - it leaves a gap in the day's numbering, which is
        // correct: the sequence records the order things happened in, and renumbering the survivors would
        // rewrite history.
        return CompletionOutcome.success(row.completionDateKey, row.dailySequence);
    }
}

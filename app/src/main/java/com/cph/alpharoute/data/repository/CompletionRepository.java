package com.cph.alpharoute.data.repository;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import com.cph.alpharoute.data.db.CompletionHistoryDao;
import com.cph.alpharoute.data.db.CompletionHistoryEntity;
import com.cph.alpharoute.data.db.CompletionOutcome;
import com.cph.alpharoute.data.db.CompletionTransactionDao;
import com.cph.alpharoute.data.db.RoundOutcome;
import com.cph.alpharoute.data.db.RoundTransactionDao;
import com.cph.alpharoute.util.AppExecutors;

import java.time.ZoneId;
import java.util.List;

/**
 * Reads the completion history, and - from Phase 5.3 - writes it.
 *
 * <p><b>The read half has been here since Phase 2.1 and was deliberately read-only.</b> A completion is
 * not an ordinary write: it must insert a history row and flip the store to COMPLETED in a single
 * transaction, after re-reading the store inside that transaction and confirming it is still PENDING.
 * A plain "insert a completion" method would have been a way to bypass that, so none existed until the
 * transaction did.
 *
 * <p><b>There is still no plain insert here, and there never should be.</b> {@link #completeStore} is the
 * only way a completion can be recorded, and it delegates the whole decision to
 * {@link CompletionTransactionDao}. Anything that wants to count a store goes through it.
 *
 * <p>Every query below feeds the Dashboard and the History screens, and all of them read the actual
 * rows. No count is ever stored, because a stored counter can drift out of step with the records and
 * then there is no way to tell which one is right.
 */
public class CompletionRepository {

    private final CompletionHistoryDao completionHistoryDao;
    /** Log tag. Short enough to read in logcat, specific enough to filter on. */
    private static final String TAG = "AlphaRouteCompletion";

    private final CompletionTransactionDao completionTransactionDao;
    private final RoundTransactionDao roundTransactionDao;
    private final SettingsRepository settingsRepository;
    private final AppExecutors executors;

    public CompletionRepository(@NonNull CompletionHistoryDao completionHistoryDao,
                                @NonNull CompletionTransactionDao completionTransactionDao,
                                @NonNull RoundTransactionDao roundTransactionDao,
                                @NonNull SettingsRepository settingsRepository,
                                @NonNull AppExecutors executors) {
        this.completionHistoryDao = completionHistoryDao;
        this.completionTransactionDao = completionTransactionDao;
        this.roundTransactionDao = roundTransactionDao;
        this.settingsRepository = settingsRepository;
        this.executors = executors;
    }

    // ------------------------------------------------------------------ the write path

    /**
     * Records that a store was completed - the only write in the app that changes a count.
     *
     * <p><b>Called only from the user's YES.</b> Nothing calls this on a timer, on an arrival, or from a
     * service. Arrival opens the dialog; this is what the dialog's YES button does. The distinction is the
     * project's central rule: a store is never counted without an explicit confirmation.
     *
     * <p><b>The clock is read here, once, and passed down.</b> {@code now} is captured on the caller's
     * thread and used for both the completion timestamp and the calendar day. Letting the transaction read
     * the clock itself, or reading it twice, would let a completion started at 23:59:59.999 be filed under
     * the previous day while carrying the new day's timestamp.
     *
     * <p><b>The ordering is the project's local-first write pattern.</b> Everything happens on the
     * single-threaded disk executor (AD-3), which is what serialises two rapid taps: the second one runs
     * only after the first has committed, and by then the store is COMPLETED and it is refused. The
     * callback is posted to the main thread, so a Fragment can touch its views without checking.
     *
     * <p><b>The network is not in this path.</b> {@code SyncManager} is notified after the commit, and a
     * failure there cannot undo the completion - the local database is the source of truth (AD-4).
     *
     * @param storeId  the store the user confirmed
     * @param callback called on the main thread with the outcome. Only
     *                 {@link CompletionOutcome.Status#SUCCESS} means anything was written
     */
    public void completeStore(long storeId, @NonNull RepositoryCallback<CompletionOutcome> callback) {
        // One reading of the clock for the whole operation. See the note above on the midnight bug.
        long now = System.currentTimeMillis();
        ZoneId zone = ZoneId.systemDefault();

        // The round is read here too, and passed down for exactly the same reason as the clock: the
        // transaction must file the completion under the round whose guards it ran against. Reading it
        // inside the transaction as well would let a round change slip between the guard and the insert.
        settingsRepository.getNow(settings -> {
            final int roundNumber = settings == null ? 1 : settings.currentRound;

            executors.diskIO().execute(() -> {
                CompletionOutcome outcome;
                try {
                    outcome = completionTransactionDao.complete(storeId, now, zone, roundNumber);
                } catch (Exception e) {
                    // The transaction rolled itself back - Room discards everything when the method throws.
                    // So the honest report is "nothing was written", which is what FAILED means. The screen
                    // tells the user to try again, and trying again is safe precisely because nothing stuck.
                    outcome = CompletionOutcome.refusal(CompletionOutcome.Status.FAILED);
                }

                final CompletionOutcome result = outcome;
                executors.postToMain(() -> callback.onResult(result));
            });
        });
    }

    /**
     * Undoes a completion the user confirmed by mistake.
     *
     * <p><b>Why this exists.</b> For six versions a completion could not be removed, on the principle that
     * it is a historical fact. The principle is right, but a mis-tap was then unrecoverable: the store
     * could not be completed again, and - because the foreign key refuses to delete a store that has
     * history (AD-6) - it could not even be removed from the list. A guarantee with no way out is a trap.
     *
     * <p><b>It is deliberately narrow.</b> It removes exactly one named row. There is no "clear the
     * history", no bulk delete, and no way to edit a completion - the only other operation on this table
     * remains the atomic completion itself.
     *
     * <p>The current round is read here and passed down, so the transaction can tell whether the row being
     * removed belongs to the round in progress. Undoing an old round's completion must not change whether
     * the store is done <i>now</i>.
     *
     * @param historyId the exact completion row, not a store id - with rounds a store has one row per round
     * @param callback  called on the main thread with the outcome
     */
    public void undoCompletion(long historyId, @NonNull RepositoryCallback<CompletionOutcome> callback) {
        long now = System.currentTimeMillis();

        settingsRepository.getNow(settings -> {
            final int currentRound = settings == null ? 1 : settings.currentRound;

            executors.diskIO().execute(() -> {
                CompletionOutcome outcome;
                try {
                    outcome = completionTransactionDao.undo(historyId, currentRound, now);
                } catch (Exception e) {
                    // Rolled back: the row is still there and the store is untouched. Nothing was lost,
                    // which is what FAILED promises.
                    outcome = CompletionOutcome.refusal(CompletionOutcome.Status.FAILED);
                }

                final CompletionOutcome result = outcome;
                executors.postToMain(() -> callback.onResult(result));
            });
        });
    }

    /**
     * Begins the next round: advances the round number and puts every store back to PENDING.
     *
     * <p>The current round is <b>not</b> checked here. {@code RoundTransactionDao} re-checks it inside the
     * transaction, because the answer the caller has may be a few moments old - a store could have been
     * added or a completion undone in between. Checking here as well would be a second opinion that could
     * disagree with the one that matters.
     *
     * @param callback called on the main thread with the outcome. Only
     *                 {@link RoundOutcome.Status#SUCCESS} means anything was written
     */
    public void startNewRound(@NonNull RepositoryCallback<RoundOutcome> callback) {
        long now = System.currentTimeMillis();

        executors.diskIO().execute(() -> {
            RoundOutcome outcome;
            try {
                outcome = roundTransactionDao.startNewRound(now);
            } catch (Exception e) {
                outcome = RoundOutcome.refusal(RoundOutcome.Status.FAILED, 0);
            }

            final RoundOutcome result = outcome;
            executors.postToMain(() -> callback.onResult(result));
        });
    }

    /**
     * How many stores are still to visit in the round in progress.
     *
     * <p>Reads the round itself, so a caller that only wants to explain why the button is disabled does not
     * have to read the settings row first.
     */
    public void countOutstandingInCurrentRound(@NonNull RepositoryCallback<Integer> callback) {
        executors.diskIO().execute(() -> {
            int outstanding = roundTransactionDao.outstandingInCurrentRound();
            executors.postToMain(() -> callback.onResult(outstanding));
        });
    }

    /** The round in progress. */
    public void currentRound(@NonNull RepositoryCallback<Integer> callback) {
        executors.diskIO().execute(() -> {
            int round = roundTransactionDao.readCurrentRound();
            executors.postToMain(() -> callback.onResult(round));
        });
    }

    // ------------------------------------------------------------------ reads

    /**
     * Every completion, newest local day first, and within a day in the real order of completion.
     *
     * <p>This is the single query behind the History tab. It is ordered by
     * {@code completionDateKey DESC, dailySequence ASC} - so the newest day is at the top, and inside a
     * day the stores appear in the order they were actually completed, never in route-number order.
     */
    public LiveData<List<CompletionHistoryEntity>> observeAllOrdered() {
        return completionHistoryDao.observeAllOrdered();
    }

    /** One local day's completions, earliest first. Backs the Daily History screen. */
    public LiveData<List<CompletionHistoryEntity>> observeByDateKey(@NonNull String dateKey) {
        return completionHistoryDao.observeByDateKey(dateKey);
    }

    /**
     * The completion for one store, or {@code null} if it has none.
     *
     * <p>Used by the "already counted?" guard and by the Store Details sheet, which shows the
     * completion date, time and daily sequence for a completed store.
     *
     * <p><b>Reads the most recent round's row.</b> Since Version 7 a store can have one row per round, so
     * "the store's completion" is not a single thing. The newest is the one that describes the store's
     * current state, which is what both callers want.
     */
    public void findForStore(long storeId, @NonNull RepositoryCallback<CompletionHistoryEntity> callback) {
        executors.diskIO().execute(() -> {
            CompletionHistoryEntity history = completionHistoryDao.findByStoreId(storeId);
            executors.postToMain(() -> callback.onResult(history));
        });
    }

    /**
     * One completion row by its own id.
     *
     * <p>The Completion Detail screen is opened with a row id rather than a store id, because with rounds a
     * store has one row per round and the screen has to know <i>which</i> completion it is describing - and
     * which one the undo would remove.
     */
    public void findByHistoryId(long historyId, @NonNull RepositoryCallback<CompletionHistoryEntity> callback) {
        executors.diskIO().execute(() -> {
            CompletionHistoryEntity history = completionHistoryDao.findById(historyId);
            executors.postToMain(() -> callback.onResult(history));
        });
    }

    /** The most recent completion, or {@code null} when nothing has been completed yet. */
    public void getLatest(@NonNull RepositoryCallback<CompletionHistoryEntity> callback) {
        executors.diskIO().execute(() -> {
            CompletionHistoryEntity latest = completionHistoryDao.getLatestNow();
            executors.postToMain(() -> callback.onResult(latest));
        });
    }

    /**
     * Every completion row, read once.
     *
     * <p>For callers that need a point-in-time answer rather than a live one - the consistency check
     * (Phase 6.5) reports what was true when it ran. Everything that <i>displays</i> completions uses
     * {@link #observeAllOrdered()} instead, so Room keeps the screen current (AD-4).
     */
    public void findAllNow(@NonNull RepositoryCallback<List<CompletionHistoryEntity>> callback) {
        executors.diskIO().execute(() -> {
            List<CompletionHistoryEntity> all = completionHistoryDao.getAllNow();
            executors.postToMain(() -> callback.onResult(all));
        });
    }

    /**
     * How many stores were completed on a given local day.
     *
     * <p>{@code dateKey} is {@code YYYY-MM-DD} in the device's local timezone, produced by
     * {@code DateKeyHelper} in Phase 2.1's sibling phase, so "today" means today where the user is.
     */
    /**
     * How many completions a store has, across every round.
     *
     * <p><b>Version 13, for the delete confirmation.</b> A confirmation that says "this store will be
     * deleted" is not enough when deleting it also removes three completion records - so the dialog states
     * the number, and this is where it comes from.
     *
     * <p>One-shot rather than observed, for the reason AD-37 records: the count is read once, at the moment
     * the dialog is built, and a read must not depend on another screen having observed the same LiveData
     * first.
     *
     * @param callback receives the count, or {@code null} if it could not be read - in which case the
     *                 caller should say what it is deleting in general terms rather than not at all
     */
    public void countForStore(long storeId, @NonNull RepositoryCallback<Integer> callback) {
        executors.diskIO().execute(() -> {
            Integer count = null;
            try {
                count = completionHistoryDao.countForStore(storeId);
            } catch (Exception e) {
                Log.e(TAG, "Could not count the history for store " + storeId, e);
            }
            final Integer result = count;
            executors.postToMain(() -> callback.onResult(result));
        });
    }

    /** How many completions are recorded on a given day. */
    public void countForDateKey(@NonNull String dateKey, @NonNull RepositoryCallback<Integer> callback) {
        executors.diskIO().execute(() -> {
            int count = completionHistoryDao.countByDateKey(dateKey);
            executors.postToMain(() -> callback.onResult(count));
        });
    }
}

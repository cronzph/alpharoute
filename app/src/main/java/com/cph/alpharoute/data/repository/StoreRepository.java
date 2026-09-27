package com.cph.alpharoute.data.repository;

import android.database.sqlite.SQLiteConstraintException;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import com.cph.alpharoute.data.db.CompletionHistoryDao;
import com.cph.alpharoute.data.db.StoreDao;
import com.cph.alpharoute.data.db.StoreTransactionDao;
import com.cph.alpharoute.data.db.StoreEntity;
import com.cph.alpharoute.data.sync.SyncManager;
import com.cph.alpharoute.domain.StoreStatus;
import com.cph.alpharoute.domain.StoreValidator;
import com.cph.alpharoute.util.AppExecutors;
import com.cph.alpharoute.util.GeoConstants;

import java.util.List;

/**
 * The only way the rest of the app reads or writes stores.
 *
 * <p>No Fragment, Activity or ViewModel touches {@link StoreDao} directly. Everything funnels through
 * here, which is what makes three things true in one place rather than scattered everywhere:
 *
 * <ul>
 *   <li>Every write runs on {@code AppExecutors.diskIO()} - a <b>single</b> thread - so writes are
 *       serialised and can never interleave.</li>
 *   <li>Every write returns immediately after the local transaction commits. Nothing in the write path
 *       touches the network, so a slow or absent internet connection cannot make the app feel slow.
 *       That is the "save locally first" requirement, satisfied by structure rather than by care.</li>
 *   <li>Only <i>after</i> a successful local write is {@link SyncManager#onLocalDataChanged()} called,
 *       and always inside a guard that swallows any failure.</li>
 * </ul>
 *
 * <p>Reads come in two forms. The {@code observe*} methods return {@link LiveData} straight from Room,
 * which re-runs the query whenever the table changes - so a screen can never show stale markers. The
 * callback methods run a one-off query on the disk thread and deliver the result on the main thread.
 */
public class StoreRepository {

    private static final String TAG = "AlphaRouteStoreRepo";

    private final StoreDao storeDao;
    private final StoreTransactionDao storeTransactionDao;
    private final CompletionHistoryDao completionHistoryDao;
    private final SettingsRepository settingsRepository;
    private final AppExecutors executors;
    private final SyncManager syncManager;

    /**
     * @param completionHistoryDao injected because a store's <i>deletion</i> rule depends on whether it
     *                             has completion history - see {@link #delete}
     * @param settingsRepository   injected so a delete can clear a dangling last-selected reference -
     *                             see {@link #delete}
     */
    public StoreRepository(@NonNull StoreDao storeDao,
                           @NonNull StoreTransactionDao storeTransactionDao,
                           @NonNull CompletionHistoryDao completionHistoryDao,
                           @NonNull SettingsRepository settingsRepository,
                           @NonNull AppExecutors executors,
                           @NonNull SyncManager syncManager) {
        this.storeDao = storeDao;
        this.storeTransactionDao = storeTransactionDao;
        this.completionHistoryDao = completionHistoryDao;
        this.settingsRepository = settingsRepository;
        this.executors = executors;
        this.syncManager = syncManager;
    }

    // ------------------------------------------------------------------ reads

    /** Every store, pending and completed. Drives the map markers. */
    public LiveData<List<StoreEntity>> observeAll() {
        return storeDao.observeAll();
    }

    /** Pending stores only - the orange markers, and the input to route suggestion. */
    public LiveData<List<StoreEntity>> observePending() {
        return storeDao.observeByStatus(StoreStatus.PENDING);
    }

    /** Completed stores only - the green markers. */
    public LiveData<List<StoreEntity>> observeCompleted() {
        return storeDao.observeByStatus(StoreStatus.COMPLETED);
    }

    /** One-off read by id, on the disk thread, delivered on the main thread. */
    public void findById(long storeId, @NonNull RepositoryCallback<StoreEntity> callback) {
        executors.diskIO().execute(() -> {
            StoreEntity store = storeDao.findById(storeId);
            executors.postToMain(() -> callback.onResult(store));
        });
    }

    /**
     * Every store, read once.
     *
     * <p>For callers that need a point-in-time answer rather than a live one - the consistency check
     * (Phase 6.5) reports what was true when it ran, and observing would make its result change under the
     * user while they read it. Everything that <i>displays</i> stores uses {@link #observeAll()} instead,
     * so Room stays the thing that keeps the screen current (AD-4).
     */
    public void findAllNow(@NonNull RepositoryCallback<List<StoreEntity>> callback) {
        executors.diskIO().execute(() -> {
            List<StoreEntity> stores = storeDao.getAllNow();
            executors.postToMain(() -> callback.onResult(stores));
        });
    }

    /**
     * Looks a store up by its code - the check behind "this store code is already taken".
     *
     * <p>The input is trimmed first, so a user who types a trailing space does not get a false "not
     * found" and then a duplicate-key error on save.
     *
     * <p>Delivers {@code null} when no store has that code.
     */
    public void findByCode(@NonNull String storeCode, @NonNull RepositoryCallback<StoreEntity> callback) {
        final String code = StoreValidator.trimToEmpty(storeCode);
        executors.diskIO().execute(() -> {
            StoreEntity store = storeDao.findByCode(code);
            executors.postToMain(() -> callback.onResult(store));
        });
    }

    /**
     * Looks a store up by its route number.
     *
     * <p>Delivers {@code null} when no store has that route number.
     */
    public void findByRouteNumber(@NonNull String routeNumber,
                                  @NonNull RepositoryCallback<StoreEntity> callback) {
        final String number = StoreValidator.trimToEmpty(routeNumber);
        executors.diskIO().execute(() -> {
            StoreEntity store = storeDao.findByRouteNumber(number);
            executors.postToMain(() -> callback.onResult(store));
        });
    }

    // ----------------------------------------------------------------- writes

    /**
     * Inserts a new store.
     *
     * <p>On {@link WriteOutcome#SUCCESS} the generated row id has been written back into
     * {@code store.id}, so the caller can use it immediately.
     *
     * <p>Duplicates are checked <b>before</b> the insert so the user gets a precise message ("store
     * code already exists") rather than a raw database error. The UNIQUE indices are still the real
     * guarantee - the check is a courtesy, and the {@code catch} below is the safety net for the case
     * where the check and the insert somehow disagree.
     */
    public void insert(@NonNull StoreEntity store, @NonNull RepositoryCallback<WriteOutcome> callback) {
        executors.diskIO().execute(() -> {
            WriteOutcome outcome;
            try {
                if (!normalise(store)) {
                    outcome = WriteOutcome.INVALID_DATA;
                } else if (storeDao.isStoreCodeTaken(store.storeCode, -1L)) {
                    outcome = WriteOutcome.DUPLICATE_STORE_CODE;
                } else if (storeDao.isRouteNumberTaken(store.routeNumber, -1L)) {
                    outcome = WriteOutcome.DUPLICATE_ROUTE_NUMBER;
                } else {
                    store.id = storeDao.insert(store);
                    outcome = WriteOutcome.SUCCESS;
                    notifySyncSafely();
                }
            } catch (SQLiteConstraintException e) {
                Log.w(TAG, "Insert rejected by a UNIQUE index", e);
                outcome = WriteOutcome.DUPLICATE_STORE_CODE;
            } catch (Exception e) {
                Log.e(TAG, "Failed to insert store", e);
                outcome = WriteOutcome.FAILED;
            }
            final WriteOutcome result = outcome;
            executors.postToMain(() -> callback.onResult(result));
        });
    }

    /**
     * Updates an existing store.
     *
     * <p><b>Editing a store can never change its status, and that is enforced here rather than trusted
     * to the caller.</b> {@link StoreDao#update} is a Room {@code @Update}, which replaces the whole row
     * - so a form that filled in only the fields it edits and left the rest at their Java defaults would
     * write {@code PENDING} and {@code null} over a completed store's status and completion time. The
     * store would go back to orange on the map while its completion history row still existed, which is
     * a contradiction nothing else in the app could repair.
     *
     * <p>The defence is {@link #preserveUneditableFields}: the fields an edit has no business touching
     * are copied from the row that is actually stored, after it has been re-read and before the write.
     * A caller therefore cannot un-complete a store by forgetting a field, however the form is built.
     *
     * <p>The duplicate checks and the not-found check all run against the stored row, so an edit to a
     * store that was deleted a moment ago reports {@link WriteOutcome#NOT_FOUND} rather than silently
     * doing nothing.
     */
    public void update(@NonNull StoreEntity store, @NonNull RepositoryCallback<WriteOutcome> callback) {
        executors.diskIO().execute(() -> {
            WriteOutcome outcome;
            try {
                // Read once, and keep it: the stored row is both the existence check and the source of
                // the fields an edit is not allowed to change.
                StoreEntity stored = storeDao.findById(store.id);

                if (!normalise(store)) {
                    outcome = WriteOutcome.INVALID_DATA;
                } else if (stored == null) {
                    outcome = WriteOutcome.NOT_FOUND;
                } else if (storeDao.isStoreCodeTaken(store.storeCode, store.id)) {
                    outcome = WriteOutcome.DUPLICATE_STORE_CODE;
                } else if (storeDao.isRouteNumberTaken(store.routeNumber, store.id)) {
                    outcome = WriteOutcome.DUPLICATE_ROUTE_NUMBER;
                } else {
                    preserveUneditableFields(store, stored);
                    store.updatedAt = System.currentTimeMillis();
                    storeDao.update(store);
                    outcome = WriteOutcome.SUCCESS;
                    notifySyncSafely();
                }
            } catch (SQLiteConstraintException e) {
                Log.w(TAG, "Update rejected by a UNIQUE index", e);
                outcome = WriteOutcome.DUPLICATE_STORE_CODE;
            } catch (Exception e) {
                Log.e(TAG, "Failed to update store " + store.id, e);
                outcome = WriteOutcome.FAILED;
            }
            final WriteOutcome result = outcome;
            executors.postToMain(() -> callback.onResult(result));
        });
    }

    /**
     * Copies the fields an edit is not allowed to change from the stored row onto the incoming one.
     *
     * <p><b>What is on each side of the line, and why:</b>
     *
     * <ul>
     *   <li><b>From the stored row:</b> {@code id}, {@code status}, {@code createdAt} and
     *       {@code completedAt}. None of these is editable on any screen. Status and completion time
     *       belong to the arrival flow and the Phase 5.3 completion transaction; {@code createdAt} is
     *       history; and the id is the identity being updated.</li>
     *   <li><b>From the incoming entity:</b> the four text fields, the coordinates, and
     *       {@code updatedAt} (which {@link #update} stamps itself). These are exactly what the Add /
     *       Edit form collects.</li>
     * </ul>
     *
     * <p>Package-private rather than private so that {@code StoreRepositoryTest} can prove the rule
     * holds. That is not ceremony: this method is the only thing standing between an ordinary "fix a
     * typo in the store name" edit and a completed store silently reverting to pending, and the failure
     * would be invisible until someone looked at the Dashboard and found a store counted twice or not
     * at all. The repository's own write path needs a database, so it cannot be tested on the JVM; this
     * rule can be, and is.
     *
     * @param incoming the entity about to be written - modified in place
     * @param stored   the row as it exists in the database right now
     */
    static void preserveUneditableFields(@NonNull StoreEntity incoming, @NonNull StoreEntity stored) {
        incoming.id = stored.id;
        incoming.status = stored.status;
        incoming.createdAt = stored.createdAt;
        incoming.completedAt = stored.completedAt;
    }

    /**
     * Deletes a store - but only a pending one.
     * <p><b>Version 13: a completed store can now be deleted.</b> Until now a store with any completion
     * history was protected and the delete was refused outright. That left no way at all to remove a store
     * that was completed by mistake, or that has closed down - so the delete is now allowed, and it takes
     * the history with it.
     *
     * <p><b>AD-6's foreign key is unchanged.</b> It still refuses an orphaning delete, and that is the
     * point: this method does both halves of the job in one transaction rather than weakening the
     * constraint that guarantees no row is ever left pointing at nothing.
     *
     * <p><b>The caller must have confirmed first.</b> The confirmation lives in the UI because only the UI
     * can say what is about to be destroyed - the store's name and how many records go with it. This method
     * trusts that it was asked intentionally.
     *
     * @param callback receives {@code SUCCESS} when the store is gone, {@code NOT_FOUND} when it had
     *                 already gone, and {@code FAILED} when the transaction rolled back
     */
    public void delete(long storeId, @NonNull RepositoryCallback<WriteOutcome> callback) {
        executors.diskIO().execute(() -> {
            WriteOutcome outcome;
            try {
                if (storeDao.findById(storeId) == null) {
                    // Already gone - by an earlier tap, or from somewhere else. Not an error: the outcome
                    // the user wanted is already true.
                    outcome = WriteOutcome.NOT_FOUND;
                } else {
                    // One transaction: the history rows go first, then the store. History-first is not a
                    // preference - AD-6's foreign key refuses the other order.
                    storeTransactionDao.deleteWithHistory(storeId);
                    outcome = WriteOutcome.SUCCESS;

                    // Integrity: if this store was the active navigation target, clear that reference
                    // so it cannot dangle. This queues its own task on the SAME single disk thread, so
                    // it cannot interleave with the delete above and cannot run before it.
                    settingsRepository.clearLastSelectedStoreIdIf(storeId, null);

                    notifySyncSafely();
                }
            } catch (SQLiteConstraintException e) {
                // Should be impossible now - the transaction removes the history first - but if the
                // constraint ever does fire, the delete has been refused and nothing has changed.
                Log.w(TAG, "Delete refused by a foreign key", e);
                outcome = WriteOutcome.FAILED;
            } catch (Exception e) {
                // The transaction threw, so it rolled back. Nothing was deleted.
                Log.e(TAG, "Failed to delete store " + storeId, e);
                outcome = WriteOutcome.FAILED;
            }
            final WriteOutcome result = outcome;
            executors.postToMain(() -> callback.onResult(result));
        });
    }

    // ----------------------------------------------------------------- helpers

    /**
     * The two rules the repository enforces no matter who called it.
     *
     * <p><b>Trimming.</b> Every text field is trimmed before it is written, so the stored data is
     * always clean even if a caller forgot. This is why {@code StoreValidator.trimToEmpty} is the one
     * definition of "trimmed" in the app - the same rule runs in both places rather than two similar
     * ones that can drift apart.
     *
     * <p><b>Coordinates.</b> A latitude outside -90..90 or a longitude outside -180..180 is not a GPS
     * inaccuracy, it is a broken value, and storing it would put a marker somewhere impossible. The
     * screen's validator catches this first and says so per field; this is the backstop.
     *
     * <p>The entity is modified in place, which is safe because the caller hands over an object it has
     * finished with and the same instance is what gets persisted.
     *
     * @return true when the entity is safe to write
     */
    private static boolean normalise(@NonNull StoreEntity store) {
        store.routeNumber = StoreValidator.trimToEmpty(store.routeNumber);
        store.storeCode = StoreValidator.trimToEmpty(store.storeCode);
        store.storeName = StoreValidator.trimToEmpty(store.storeName);
        store.area = StoreValidator.trimToEmpty(store.area);

        return GeoConstants.isValidLatitude(store.latitude)
                && GeoConstants.isValidLongitude(store.longitude);
    }

    /**
     * Tells the sync layer that something changed - and makes absolutely sure it cannot hurt.
     *
     * <p>The local write has already committed by the time this runs. A sync implementation that
     * throws, blocks or is simply absent must never turn a successful save into a visible failure, so
     * the call is wrapped and any problem is logged and dropped.
     */
    private void notifySyncSafely() {
        try {
            syncManager.onLocalDataChanged();
        } catch (Exception e) {
            Log.e(TAG, "Sync notification failed. The local write is already committed.", e);
        }
    }
}

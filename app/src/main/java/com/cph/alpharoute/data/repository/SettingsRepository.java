package com.cph.alpharoute.data.repository;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.cph.alpharoute.data.db.AppSettingDao;
import com.cph.alpharoute.data.db.AppSettingEntity;
import com.cph.alpharoute.data.sync.SyncManager;
import com.cph.alpharoute.util.AppExecutors;
import com.cph.alpharoute.util.GeoConstants;

/**
 * The single settings row: Home, the arrival radius, and the last selected store.
 *
 * <p>Home lives here rather than in the stores table on purpose. Home is the permanent final
 * destination, it is drawn with its own house marker, and it must never be counted as a store or
 * affect any total - keeping it in a different table makes that impossible to get wrong by accident.
 *
 * <p>The settings row is created on first launch by {@link #ensureDefaults()}, called once from
 * {@link com.cph.alpharoute.AlphaRouteApp}. Every later change updates that same row, so there is
 * never a "settings not found" case for callers to handle.
 */
public class SettingsRepository {

    private static final String TAG = "AlphaRouteSettingsRepo";

    private final AppSettingDao appSettingDao;
    private final AppExecutors executors;
    private final SyncManager syncManager;

    public SettingsRepository(@NonNull AppSettingDao appSettingDao,
                              @NonNull AppExecutors executors,
                              @NonNull SyncManager syncManager) {
        this.appSettingDao = appSettingDao;
        this.executors = executors;
        this.syncManager = syncManager;
    }

    // ------------------------------------------------------------------ reads

    /** The settings row, observed. Emits as soon as the row exists. */
    public LiveData<AppSettingEntity> observe() {
        return appSettingDao.observe();
    }

    /** One-off read of the settings row, delivered on the main thread. May deliver {@code null}. */
    public void getNow(@NonNull RepositoryCallback<AppSettingEntity> callback) {
        executors.diskIO().execute(() -> {
            AppSettingEntity settings = appSettingDao.getNow();
            executors.postToMain(() -> callback.onResult(settings));
        });
    }

    /**
     * The arrival radius in metres, <b>guaranteed to be usable</b>.
     *
     * <p>This never delivers {@code null}, never delivers zero, and never delivers a value outside
     * {@link GeoConstants#MIN_ARRIVAL_RADIUS_M}..{@link GeoConstants#MAX_ARRIVAL_RADIUS_M}. If the
     * settings row is missing, unreadable, or somehow holds a nonsense radius, it falls back to
     * {@link GeoConstants#DEFAULT_ARRIVAL_RADIUS_M}.
     *
     * <p>That guarantee matters because of what reads it: the arrival check in Phase 5.1 decides
     * whether to offer the completion dialog, and the foreground service in Phase 4.3 runs it with no
     * screen present. Neither can afford to branch on a null or to treat a corrupt 0 as "you must be
     * standing exactly on the pin". A safe default is always better than a correct-looking crash.
     */
    public void getArrivalRadius(@NonNull RepositoryCallback<Integer> callback) {
        executors.diskIO().execute(() -> {
            int radius = GeoConstants.DEFAULT_ARRIVAL_RADIUS_M;
            try {
                AppSettingEntity settings = appSettingDao.getNow();
                if (settings != null && GeoConstants.isValidArrivalRadius(settings.arrivalRadius)) {
                    radius = settings.arrivalRadius;
                }
            } catch (Exception e) {
                Log.e(TAG, "Could not read the arrival radius; using the default", e);
            }
            final int result = radius;
            executors.postToMain(() -> callback.onResult(result));
        });
    }

    // ----------------------------------------------------------------- writes

    /**
     * Creates the settings row with its defaults if it does not exist yet.
     *
     * <p>Called once from {@code AlphaRouteApp.onCreate}. It does <b>not</b> notify the sync layer: a
     * freshly created defaults row is not a user change worth backing up, and notifying would push a
     * sync on every cold start.
     */
    public void ensureDefaults() {
        executors.diskIO().execute(() -> {
            try {
                if (appSettingDao.getNow() == null) {
                    AppSettingEntity settings = new AppSettingEntity();
                    settings.id = AppSettingEntity.SINGLETON_ID;
                    settings.homeSet = false;
                    settings.homeLatitude = null;
                    settings.homeLongitude = null;
                    settings.arrivalRadius = GeoConstants.DEFAULT_ARRIVAL_RADIUS_M;
                    settings.lastSelectedStoreId = null;
                    settings.lastUpdated = System.currentTimeMillis();
                    appSettingDao.upsert(settings);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to create the default settings row", e);
            }
        });
    }

    /** Stores Home. Home is never added to the stores table. */
    public void setHome(double latitude,
                        double longitude,
                        @NonNull RepositoryCallback<WriteOutcome> callback) {
        if (!GeoConstants.isValidLatitude(latitude) || !GeoConstants.isValidLongitude(longitude)) {
            Log.w(TAG, "Refusing to store an out-of-range Home coordinate");
            executors.postToMain(() -> callback.onResult(WriteOutcome.FAILED));
            return;
        }

        mutate(settings -> {
            settings.homeLatitude = latitude;
            settings.homeLongitude = longitude;
            settings.homeSet = true;
        }, callback);
    }

    /** Clears Home, so the app falls back to "Home not set". */
    public void clearHome(@NonNull RepositoryCallback<WriteOutcome> callback) {
        mutate(settings -> {
            settings.homeLatitude = null;
            settings.homeLongitude = null;
            settings.homeSet = false;
        }, callback);
    }

    /**
     * Sets the arrival radius in metres.
     *
     * <p>Out-of-range values are refused here rather than stored, so the arrival check in Phase 5.1 can
     * trust whatever it reads. Changing the radius never rewrites completion history.
     */
    public void setArrivalRadius(int radiusMetres,
                                 @NonNull RepositoryCallback<WriteOutcome> callback) {
        if (!GeoConstants.isValidArrivalRadius(radiusMetres)) {
            Log.w(TAG, "Refusing an out-of-range arrival radius: " + radiusMetres);
            executors.postToMain(() -> callback.onResult(WriteOutcome.FAILED));
            return;
        }

        mutate(settings -> settings.arrivalRadius = radiusMetres, callback);
    }

    /**
     * Records which store the user last selected, or the one currently being navigated to.
     *
     * <p>Architecture decision AD-10: this is what lets an in-progress destination survive a restart
     * without any extra schema.
     *
     * @param storeId the store id, or {@code null} to clear it
     */
    public void setLastSelectedStoreId(@Nullable Long storeId,
                                       @NonNull RepositoryCallback<WriteOutcome> callback) {
        mutate(settings -> settings.lastSelectedStoreId = storeId, callback);
    }

    /**
     * Clears the last-selected reference, but only if it points at the given store.
     *
     * <p><b>Why this exists.</b> {@code lastSelectedStoreId} is a reference to a store in a different
     * table, and unlike completion history it has no foreign key behind it. A pending store can be
     * deleted, so the reference can end up pointing at a row that no longer exists. That is a dangling
     * reference: harmless today, but it means "resume the active navigation" would try to load a store
     * that is not there, and Phase 6.5's consistency check would have to report it as a defect.
     *
     * <p>Clearing it at the moment the store is deleted removes the whole class of problem, and the
     * {@code If} in the name is the important part: this must never clobber a reference to a
     * <i>different</i> store. Deleting store 7 must not clear a selection of store 9.
     *
     * <p>Called automatically by {@code StoreRepository.delete()}. It is public as well, so a future
     * consistency check can use it directly.
     *
     * @param callback optional - pass {@code null} when the caller does not need the result, as
     *                 {@code StoreRepository.delete()} does
     */
    public void clearLastSelectedStoreIdIf(long storeId,
                                           @Nullable RepositoryCallback<WriteOutcome> callback) {
        executors.diskIO().execute(() -> {
            WriteOutcome outcome = WriteOutcome.SUCCESS;
            try {
                AppSettingEntity settings = appSettingDao.getNow();
                if (settings != null
                        && settings.lastSelectedStoreId != null
                        && settings.lastSelectedStoreId == storeId) {
                    settings.lastSelectedStoreId = null;
                    settings.lastUpdated = System.currentTimeMillis();
                    appSettingDao.upsert(settings);
                    notifySyncSafely();
                }
                // Nothing to clear is a SUCCESS, not a failure: it is the desired end state.
            } catch (Exception e) {
                Log.e(TAG, "Could not clear the last selected store", e);
                outcome = WriteOutcome.FAILED;
            }
            if (callback != null) {
                final WriteOutcome result = outcome;
                executors.postToMain(() -> callback.onResult(result));
            }
        });
    }

    // ----------------------------------------------------------------- helpers

    /** The shape every settings write shares: load, change, save, notify. */
    private void mutate(@NonNull SettingsChange change,
                        @NonNull RepositoryCallback<WriteOutcome> callback) {
        executors.diskIO().execute(() -> {
            WriteOutcome outcome;
            try {
                AppSettingEntity settings = appSettingDao.getNow();
                if (settings == null) {
                    // Defensive: ensureDefaults() runs at app start, but a wiped database or a race
                    // would otherwise leave this write with nothing to update.
                    settings = new AppSettingEntity();
                    settings.id = AppSettingEntity.SINGLETON_ID;
                    settings.arrivalRadius = GeoConstants.DEFAULT_ARRIVAL_RADIUS_M;
                }
                change.apply(settings);
                settings.lastUpdated = System.currentTimeMillis();
                appSettingDao.upsert(settings);
                outcome = WriteOutcome.SUCCESS;
                notifySyncSafely();
            } catch (Exception e) {
                Log.e(TAG, "Failed to save settings", e);
                outcome = WriteOutcome.FAILED;
            }
            final WriteOutcome result = outcome;
            executors.postToMain(() -> callback.onResult(result));
        });
    }

    /** As in {@code StoreRepository}: a sync problem must never break a committed local write. */
    private void notifySyncSafely() {
        try {
            syncManager.onLocalDataChanged();
        } catch (Exception e) {
            Log.e(TAG, "Sync notification failed. The local write is already committed.", e);
        }
    }

    /** A single field change to apply to the settings row. */
    private interface SettingsChange {
        void apply(AppSettingEntity settings);
    }
}

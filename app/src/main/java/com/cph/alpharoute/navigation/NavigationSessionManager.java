package com.cph.alpharoute.navigation;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cph.alpharoute.AlphaRouteApp;
import com.cph.alpharoute.data.db.StoreEntity;
import com.cph.alpharoute.data.repository.RepositoryCallback;
import com.cph.alpharoute.data.repository.SettingsRepository;
import com.cph.alpharoute.domain.GeoCoordinate;
import com.cph.alpharoute.domain.NavigationRecovery;
import com.cph.alpharoute.domain.RecoveryState;

/**
 * Starts and ends a navigation session.
 *
 * <p><b>This is the only place that knows a session has two halves.</b> Beginning one means three things
 * that must not drift apart:
 *
 * <ol>
 *   <li>the {@link NavigationSession} is filled in, so the screen can show a destination immediately;</li>
 *   <li>the store id is written to {@code app_settings.lastSelectedStoreId} (AD-10), so the session
 *       survives a restart;</li>
 *   <li>the foreground service is started, so the position is watched while Google Maps is in front.</li>
 * </ol>
 *
 * <p>Ending one is the mirror image. Keeping the three together here means no caller can do one without
 * the others - which is how a session ends up half-alive, with a notification running and no
 * destination behind it.
 *
 * <p><b>The service is started from a tap, never from a lifecycle callback.</b> Android 12 and later
 * forbid starting a foreground service from the background, and a fragment's {@code onResume} is not a
 * reliable "the user is here" signal - tabs in this app are hidden rather than destroyed (A27), so a
 * hidden Map tab still resumes. Every entry point into this class is a button the user pressed.
 */
public class NavigationSessionManager {

    private static final String TAG = "AlphaRouteNavSession";

    private final NavigationSession session;
    private final SettingsRepository settingsRepository;

    public NavigationSessionManager(@NonNull NavigationSession session,
                                    @NonNull SettingsRepository settingsRepository) {
        this.session = session;
        this.settingsRepository = settingsRepository;
    }

    /** The observable session, for screens that only need to read it. */
    @NonNull
    public NavigationSession session() {
        return session;
    }

    /**
     * Begins navigating to a store.
     *
     * @param context     the context that starts the service - an Activity is required on Android 12+,
     *                    see the class note
     * @param store       the store to drive to. Its coordinates become the destination.
     * @param onRecorded  called on the main thread once the session has been recorded, or immediately if
     *                    it could not be. Passing {@code null} is allowed when the caller does not care.
     */
    public void start(@NonNull Context context,
                      @NonNull StoreEntity store,
                      @Nullable Runnable onRecorded) {
        GeoCoordinate destination = new GeoCoordinate(store.latitude, store.longitude);

        // 1. The in-memory session, so the sheet and the map react at once rather than after a disk
        //    round-trip. Everything that can be done synchronously is done synchronously.
        session.begin(store.id, destination);

        // 2. AD-10: the persisted reference. Written on the disk thread and confirmed on the main thread,
        //    so a restart can offer to resume. The service is started regardless of the outcome: failing
        //    to record the reference costs the ability to resume, and stopping the monitoring because of
        //    it would cost the arrival alert too.
        settingsRepository.setLastSelectedStoreId(store.id, outcome ->
                Log.d(TAG, "Recorded the active store: " + outcome));

        // 3. The foreground service, which is what actually watches for arrival.
        startService(context, store.id);

        if (onRecorded != null) {
            onRecorded.run();
        }
    }

    /**
     * Ends the active session, whichever way it ended.
     *
     * <p>Called by "End navigation", and also after a completion is confirmed in Phase 5 - a session
     * pointing at a store that is already counted would offer the same store again.
     */
    public void stop(@NonNull Context context) {
        session.end();

        // Clearing the persisted reference is what stops a cold start from offering to resume a drive
        // that is already over. Failure here is logged and ignored: the in-memory session is already
        // gone, so the app will not present it as active - a stale id is a nuisance, not a fault.
        settingsRepository.setLastSelectedStoreId(null, outcome ->
                Log.d(TAG, "Cleared the active store: " + outcome));

        stopService(context);
    }

    /**
     * Checks whether a recorded session is worth offering back, on a cold start.
     *
     * <p><b>It deliberately does not resume.</b> Phase 4.4's rule, and both halves of it matter:
     *
     * <ul>
     *   <li><b>It cannot start the service.</b> Android 12+ forbids starting a foreground service from the
     *       background, and this runs as the app is opening - which may be a cold start the user did not
     *       ask for. Resuming here would throw on those devices.</li>
     *   <li><b>It should not decide.</b> "Was I still driving?" is a question about the user's intent, and
     *       the app cannot answer it. A drive interrupted by a low-memory kill is worth offering; a
     *       leftover from last week is not, and {@link NavigationRecovery} draws that line.</li>
     * </ul>
     *
     * <p>So it reports {@link RecoveryState#OFFER_RESUME} and lets the Map tab ask. The tap is what starts
     * the service, which is the only legal way and the only honest one.
     *
     * <p>A stale reference is cleared here rather than left for later. The next launch would find it again,
     * and a reference that is never going to be offered should not survive to be re-judged.
     */
    public void checkForResumableSession(@NonNull RepositoryCallback<RecoveryState> callback) {
        settingsRepository.getNow(settings -> {
            if (settings == null) {
                callback.onResult(RecoveryState.NOTHING);
                return;
            }

            RecoveryState state = NavigationRecovery.evaluate(
                    settings.lastSelectedStoreId,
                    settings.lastUpdated,
                    System.currentTimeMillis());

            if (state == RecoveryState.STALE) {
                // Cleared without asking, and the result is reported as NOTHING rather than STALE: the
                // caller's job is to decide whether to show a prompt, and there is nothing to show.
                settingsRepository.setLastSelectedStoreId(null, null);
                callback.onResult(RecoveryState.NOTHING);
                return;
            }

            callback.onResult(state);
        });
    }

    /**
     * Resumes the offered session. <b>Only ever called from the user's tap on Resume.</b>
     *
     * <p>Reads the store back from Room rather than trusting the recorded id on its own: the store may have
     * been deleted, and navigating to a coordinate whose store is gone would leave the sheet with nothing to
     * name. A missing store clears the reference and reports nothing.
     *
     * @param onResumed called on the main thread with the store that was resumed, or {@code null} when the
     *                  session could not be resumed
     */
    public void resume(@NonNull Context context,
                       @NonNull RepositoryCallback<StoreEntity> onResumed) {
        settingsRepository.getNow(settings -> {
            Long storeId = settings == null ? null : settings.lastSelectedStoreId;
            if (storeId == null) {
                onResumed.onResult(null);
                return;
            }

            AlphaRouteApp.from(context).storeRepository().findById(storeId, store -> {
                if (store == null) {
                    // Deleted while the session was recorded. Clear the reference rather than leaving a
                    // dangling id to be offered again on the next launch.
                    settingsRepository.setLastSelectedStoreId(null, null);
                    onResumed.onResult(null);
                    return;
                }
                start(context, store, null);
                onResumed.onResult(store);
            });
        });
    }

    /** The arrival radius the session was set up with, for the resumed case. */
    public void findResumableStore(@NonNull RepositoryCallback<Long> callback) {
        settingsRepository.getNow(settings -> {
            if (settings == null || settings.lastSelectedStoreId == null) {
                callback.onResult(NavigationSession.NO_STORE);
            } else {
                callback.onResult(settings.lastSelectedStoreId);
            }
        });
    }

    /**
     * Restarts the monitoring service for a session that is already active.
     *
     * <p><b>Why this is separate from {@link #start}.</b> {@code start} begins a <i>new</i> session: it
     * rewrites the session, re-records the store and clears the route. This does none of that - the session
     * is already correct and the destination has not changed. Only the service is missing, which happens
     * when the system reclaimed it under memory pressure.
     *
     * <p>Re-recording the reference here would be wrong twice over: it would reset the recovery timestamp,
     * making a stale session look fresh, and it would fire a database write for a drive the user has not
     * changed.
     *
     * @return {@code true} when the service was started, {@code false} when there is no active session or
     *         the start was refused
     */
    public boolean restartMonitoring(@NonNull Context context) {
        NavigationSession.Snapshot snapshot = session.current();
        if (snapshot == null) {
            // No session to monitor, so there is nothing to restart. Returning false rather than starting a
            // service with no destination, which would post a notification for a drive that does not exist.
            Log.w(TAG, "Monitoring was asked to restart with no active session.");
            return false;
        }

        return startService(context, snapshot.storeId());
    }

    private boolean startService(@NonNull Context context, long storeId) {
        try {
            context.startForegroundService(NavigationMonitorService.intent(context, storeId));
            return true;
        } catch (Exception e) {
            // Two possible causes, and both are worth surviving. The service could not be started because
            // the app was in the background, or the device refused a foreground service of this type. The
            // in-memory session is already running, so the sheet still works and the arrival check still
            // runs while the user has the app open - only monitoring with the app in the background is
            // lost. Ending the whole session here would be a bigger loss than the fault.
            Log.e(TAG, "Could not start the navigation monitor service", e);
            return false;
        }
    }

    private void stopService(@NonNull Context context) {
        try {
            context.stopService(new Intent(context, NavigationMonitorService.class));
        } catch (Exception e) {
            // The service was never running, or the system had already stopped it. Nothing to do, and
            // nothing the user needs to hear about - the session itself is already ended.
            Log.w(TAG, "Could not stop the navigation monitor service", e);
        }
    }
}

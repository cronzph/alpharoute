package com.cph.alpharoute.data.sync;

import android.util.Log;

/**
 * The sync implementation in force until a transport exists: a Null Object.
 *
 * <p>This is a deliberate design pattern, not a stub waiting to be filled in with
 * {@code // TODO}. It has a real, useful behaviour: it lets the entire write path be written, wired
 * and tested today - every repository already calls {@code onLocalDataChanged()} after a successful
 * local write - so that adding Firestore later changes exactly one line in
 * {@link com.cph.alpharoute.AlphaRouteApp} and nothing else.
 *
 * <p>The debug log is the point of the class. When you add a store or change a setting, you can see
 * in Logcat that the local write committed <i>and</i> that the sync hook fired afterwards, which
 * proves the ordering the requirement depends on: local first, sync second, never the other way
 * round.
 *
 * <p>Firestore is not wired up yet because it needs a Firebase project's own configuration values
 * (project id, application id, API key). Those are project specific, so they cannot be invented - see
 * the Firebase notes in the phase document.
 */
public class NoOpSyncManager implements SyncManager {

    private static final String TAG = "AlphaRouteSync";

    @Override
    public void onLocalDataChanged() {
        Log.d(TAG, "Local write committed. No sync transport configured yet, "
                + "so this change stays on this device. The app is fully usable regardless.");
    }
}

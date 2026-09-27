package com.cph.alpharoute.navigation;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;

import com.cph.alpharoute.AlphaRouteApp;
import com.cph.alpharoute.data.db.AppSettingEntity;
import com.cph.alpharoute.data.repository.RepositoryCallback;

/**
 * Clears a navigation session that did not survive a reboot.
 *
 * <p><b>Why this exists, and why it clears rather than resumes.</b> {@code lastSelectedStoreId} is written
 * to the database so an active destination survives a restart (AD-10). That is what makes "resume
 * navigation" possible after the app is killed. A reboot is different: the drive is over, the service is
 * gone, and nothing is watching. If the reference survived, the next launch would offer to resume
 * navigation to a store the user may have visited days ago.
 *
 * <p>The honest fix would be to restart the monitoring service on boot. <b>Android 12 and later forbid
 * it</b>: starting a foreground service from the background throws
 * {@code ForegroundServiceStartNotAllowedException}, and a {@code BOOT_COMPLETED} receiver is exactly a
 * background context. There is no permission that lifts this - it is a deliberate restriction, and the
 * only legal way to start such a service is from a screen the user is looking at.
 *
 * <p>So 4.3 does not support resuming across a reboot, and this receiver makes that explicit rather than
 * leaving a stale session to confuse the next launch. Resuming after an ordinary app kill - where the
 * process died but the phone did not reboot - is still possible, and is Phase 5's business.
 */
public class NavigationBootReceiver extends BroadcastReceiver {

    private static final String TAG = "AlphaRouteNavBoot";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) {
            return;
        }

        String action = intent.getAction();
        // Defensive: the receiver is declared with this one action, so anything else arriving means the
        // manifest was changed. The default of "do nothing" is the safe one.
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            return;
        }

        Log.d(TAG, "Device restarted or app updated - clearing any recorded navigation session");

        AlphaRouteApp.from(context)
                .settingsRepository()
                .setLastSelectedStoreId(null, outcome ->
                        Log.d(TAG, "Recorded navigation session cleared: " + outcome));
    }
}

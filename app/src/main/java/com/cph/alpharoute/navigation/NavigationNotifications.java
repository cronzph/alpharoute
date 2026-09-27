package com.cph.alpharoute.navigation;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.cph.alpharoute.MainActivity;
import com.cph.alpharoute.R;

/**
 * The two notifications a navigation session posts.
 *
 * <p><b>Two, with two different ids, because they behave differently:</b>
 *
 * <ul>
 *   <li>the <b>ongoing</b> one is the foreground service's own notification. It cannot be swiped away -
 *       it is the visible evidence that the app is watching the position, which a foreground service is
 *       required to show. It updates its text as the distance closes.</li>
 *   <li>the <b>arrival</b> one is a normal, dismissible notification. It is the entry point to the
 *       completion dialog in Phase 5, and the driver has to be able to clear it.</li>
 * </ul>
 *
 * <p>Using one id for both would mean the arrival alert either replaced the service notification -
 * leaving the app running a foreground service with no visible notification, which the system kills - or
 * was itself undismissable. Neither is acceptable, so they are separate.
 *
 * <p><b>The channel is created here, in the service, rather than at app start.</b> A notification channel
 * is a user-visible setting, and creating one for a feature the user has never touched would put a
 * permanent entry in their notification settings for nothing. It is created the first time a session
 * starts, which is the first moment it can matter.
 *
 * <p><b>A denied notification permission does not stop the monitoring.</b> From Android 13 the user can
 * refuse notifications. When they have, the arrival alert cannot be shown - but the foreground service,
 * the arrival check and the on-screen sheet all keep working. An app that abandoned the drive because it
 * could not post a notification would be silently broken, and the user would have no way to connect the
 * two.
 */
final class NavigationNotifications {

    /** The ongoing notification the foreground service must show. */
    static final int ONGOING_NOTIFICATION_ID = 4101;

    /** The dismissible arrival alert. A different id, deliberately - see the class note. */
    static final int ARRIVAL_NOTIFICATION_ID = 4102;

    /**
     * The arrival notification's PendingIntent request code.
     *
     * <p>Distinct from the ongoing notification's {@code 0} so the two do not share one PendingIntent and
     * overwrite each other's extras - see {@code openAppIntent}.
     */
    private static final int ARRIVAL_REQUEST_CODE = 4102;

    /**
     * The channel id.
     *
     * <p>Once a channel exists its settings belong to the user, so this string must never change for a
     * released build: a new id means a new channel, and their customisations would be silently discarded.
     */
    private static final String CHANNEL_ID = "alpharoute_navigation";

    /** Utility class - never instantiated. */
    private NavigationNotifications() {
    }

    /** Creates the channel if it is not there yet. Safe to call on every start. */
    static void ensureChannel(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // Channels did not exist before Android 8; the notification is posted directly there.
            return;
        }

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }

        // IMPORTANCE_LOW, not HIGH or DEFAULT. The ongoing notification changes every few seconds while
        // driving, and at a higher importance that would mean a sound or a vibration each time - which
        // is worse than useless behind the wheel. LOW keeps it silent and without a heads-up popup.
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.nav_channel_name),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(context.getString(R.string.nav_channel_description));
        channel.setShowBadge(false);

        manager.createNotificationChannel(channel);
    }

    /**
     * The ongoing notification, showing how far away the destination is.
     *
     * @param distanceText a ready-made string, because the formatting rules live in
     *                     {@code RouteTextFormatter} and must not be duplicated here
     */
    static Notification ongoing(@NonNull Context context,
                                @NonNull String distanceText,
                                boolean arrived) {
        return new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_navigation_notification)
                .setContentTitle(context.getString(R.string.nav_notification_title))
                .setContentText(arrived
                        ? context.getString(R.string.nav_notification_arrived)
                        : context.getString(R.string.nav_notification_distance_format, distanceText))
                .setContentIntent(openAppIntent(context))
                // Ongoing and not dismissible: this notification IS the foreground service's visible
                // evidence. Swiping it away would leave a foreground service running with nothing to
                // show for it, which the system is entitled to kill.
                .setOngoing(true)
                .setSilent(true)
                .setShowWhen(false)
                .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    /** The arrival alert - the entry point to the completion dialog. */
    static Notification arrival(@NonNull Context context,
                                @NonNull String storeLabel,
                                long storeId) {
        return new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_navigation_notification)
                .setContentTitle(context.getString(R.string.nav_arrival_title))
                .setContentText(context.getString(R.string.nav_arrival_message, storeLabel))
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(context.getString(R.string.nav_arrival_message, storeLabel)))
                .setContentIntent(openAppIntent(context, storeId))
                // Auto-cancel: the driver has seen it, and it is not something to keep. The completion
                // itself is confirmed in the app, not from the notification.
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                // DEFAULT, not LOW: this one is worth a sound. It fires once, at the moment the drive
                // reaches the store, and it is the whole point of the monitoring.
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build();
    }

    /**
     * Tapping the ongoing notification opens the app.
     *
     * <p>{@code FLAG_ACTIVITY_SINGLE_TOP} rather than a fresh task: the app is almost certainly already
     * running behind Google Maps, and the driver wants to be returned to the screen they left, not given
     * a second copy of the app stacked on the first.
     *
     * <p>{@code FLAG_IMMUTABLE} is required from Android 12. These intents carry no mutable data, so
     * nothing is lost by it - and without it the app would crash when posting the notification.
     */
    private static PendingIntent openAppIntent(@NonNull Context context) {
        return openAppIntent(context, 0L);
    }

    /**
     * Tapping the arrival notification opens the app <b>at the completion dialog for that store</b>.
     *
     * <p><b>The store id rides along as an extra, and that is the whole point.</b> The arrival notification
     * is the app's only chance to reach the driver while they are standing in the store - they are in
     * Google Maps, not in this app. Opening the map and leaving them to find the store themselves would
     * throw that away, so the tap carries the one thing the dialog cannot guess.
     *
     * <p><b>Two request codes, not one.</b> A {@code PendingIntent} is identified by its intent's action,
     * data and component - not by its extras. The ongoing and arrival notifications both target
     * {@code MainActivity} with no data, so with the same request code they would be the <i>same</i>
     * PendingIntent, and {@code FLAG_UPDATE_CURRENT} would have the last one posted overwrite the other's
     * extras. The ongoing notification is re-posted every few seconds while driving, so the store id would
     * be wiped almost immediately and the arrival tap would open a bare map. Distinct request codes keep
     * them separate.
     *
     * @param storeId the store to confirm, or {@code 0} for a plain "open the app" tap
     */
    private static PendingIntent openAppIntent(@NonNull Context context, long storeId) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int requestCode = 0;
        if (storeId > 0L) {
            intent.putExtra(MainActivity.EXTRA_CONFIRM_STORE_ID, storeId);
            requestCode = ARRIVAL_REQUEST_CODE;
        }

        return PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}

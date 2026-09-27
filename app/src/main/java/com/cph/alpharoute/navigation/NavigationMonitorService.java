package com.cph.alpharoute.navigation;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cph.alpharoute.AlphaRouteApp;
import com.cph.alpharoute.R;
import com.cph.alpharoute.data.db.StoreEntity;
import com.cph.alpharoute.domain.ArrivalState;
import com.cph.alpharoute.location.LocationFix;
import com.cph.alpharoute.location.LocationPermissionHelper;
import com.cph.alpharoute.location.LocationProvider;
import com.cph.alpharoute.util.RouteTextFormatter;

/**
 * Watches the driver's position while they are being guided by the Google Maps app.
 *
 * <p><b>What it is for.</b> Under D-6/D-7 this app hands the drive to Google Maps and does not guide it.
 * But the app still has to know when the destination has been reached, and it cannot do that from a
 * screen: while the driver is navigating, Google Maps is in front and this app's Map tab is not visible -
 * and on Android a background app gets no location updates at all. A <b>foreground</b> service is the only
 * structure that keeps receiving them, and it is also the honest one: the user can see, at all times,
 * that the app is watching.
 *
 * <p><b>It observes; it does not write.</b> The service never touches Room and never marks anything
 * complete. It publishes position readings to {@link NavigationSession} and posts a notification when the
 * driver arrives. The completion itself is Phase 5, and it happens in the UI, behind an explicit YES.
 *
 * <p><b>What ends it.</b> Three things, and all three are normal:
 *
 * <ul>
 *   <li>"End navigation" - the user's decision, via {@link NavigationSessionManager#stop};</li>
 *   <li>the arrival radius being reached - the job is done, so the service stops monitoring and leaves
 *       the arrival notification behind as the entry point to the completion dialog;</li>
 *   <li>location permission being revoked while driving, which makes monitoring impossible. The service
 *       stops and says why rather than sitting there silently doing nothing.</li>
 * </ul>
 *
 * <p><b>It does not stop itself just because the screen went away.</b> The opposite, in fact: the whole
 * point is that it keeps running while the app is in the background. That is why it is a foreground
 * service and why there is no {@code onTaskRemoved} handler stopping it.
 */
public class NavigationMonitorService extends Service {

    private static final String TAG = "AlphaRouteNavMonitor";

    /** The store being navigated to, passed in when the service is started. */
    private static final String EXTRA_STORE_ID = "extra_store_id";

    private LocationProvider locationProvider;
    private NavigationSession session;
    private NavigationSessionManager sessionManager;
    private NotificationManager notificationManager;

    /** The store being driven to, read from Room once at startup and then held. */
    private long storeId = NavigationSession.NO_STORE;

    private String storeLabel = "";

    /** The configured arrival radius, read once at startup. */
    private int arrivalRadiusMetres = com.cph.alpharoute.util.GeoConstants.DEFAULT_ARRIVAL_RADIUS_M;

    /**
     * Whether the arrival notification has already been posted for this session.
     *
     * <p>A boolean rather than a "is this the first arrival" check, because a fix arrives every few
     * seconds and the driver may sit inside the radius for a while. Without this the alert would fire
     * again and again, which is exactly the kind of notification the user turns off - and then the one
     * that mattered is missed.
     */
    private boolean arrivalNotified;

    /** True once the service has called {@code startForeground} successfully. */
    private boolean foregroundStarted;

    /**
     * The location observer, held as a field.
     *
     * <p><b>It has to be a field, not a method reference used twice.</b>
     * {@code locationProvider.currentFix().removeObserver(this::onFix)} reads as though it removes the
     * observer added by {@code observeForever(this::onFix)} - but those are two different lambda objects,
     * so nothing is removed, the service keeps receiving fixes after it was told to stop, and the
     * {@code LiveData} holds a reference to a service that will never be collected.
     */
    private final androidx.lifecycle.Observer<LocationFix> fixObserver = this::onFix;

    /** Builds the Intent that starts this service for a store. */
    static Intent intent(@NonNull Context context, long storeId) {
        Intent intent = new Intent(context, NavigationMonitorService.class);
        intent.putExtra(EXTRA_STORE_ID, storeId);
        return intent;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // The session learns from the service itself whether monitoring is running. See
        // NavigationSession.isMonitoring() for why the sheet needs to know.
        AlphaRouteApp.from(this).navigationSession().setMonitoring(true);
        Log.d(TAG, "Monitor service created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        long requestedStoreId = intent == null
                ? NavigationSession.NO_STORE
                : intent.getLongExtra(EXTRA_STORE_ID, NavigationSession.NO_STORE);

        // A restart with a null Intent - the system re-creating the service after a low-memory kill -
        // leaves us with no store. There is nothing sensible to monitor, so the service ends rather than
        // running on with no destination. START_NOT_STICKY below makes this rare, but not impossible.
        if (requestedStoreId == NavigationSession.NO_STORE) {
            Log.w(TAG, "Started without a store id - stopping.");
            stopSelf();
            return START_NOT_STICKY;
        }

        storeId = requestedStoreId;

        // The foreground notification must be posted within a few seconds of the start or the system
        // throws. Posted first, before any database read, so the deadline cannot be missed while waiting
        // on disk.
        startForegroundNow();

        // Everything else is set up once. A second start for the same store is a no-op beyond the
        // notification refresh; a start for a different store re-reads.
        if (locationProvider == null) {
            AlphaRouteApp app = AlphaRouteApp.from(this);
            locationProvider = app.locationProvider();
            session = app.navigationSession();
            sessionManager = app.navigationSessionManager();
            notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

            NavigationNotifications.ensureChannel(this);
            loadStoreAndBeginMonitoring();
        }

        // NOT sticky. If the system kills this service, it must not silently bring it back with no
        // destination and no notification - the driver would have no idea whether they are being watched.
        return START_NOT_STICKY;
    }

    // ------------------------------------------------------------------ startup

    /**
     * Reads the store, then starts watching.
     *
     * <p>The radius and the store's label both come from the database, and both are needed before the
     * first notification can say anything meaningful. Reading once here rather than on every fix also
     * means the arrival rule cannot change halfway through a drive if the user edits the setting.
     */
    private void loadStoreAndBeginMonitoring() {
        AlphaRouteApp app = AlphaRouteApp.from(this);

        app.settingsRepository().getArrivalRadius(radius -> {
            arrivalRadiusMetres = radius;
            loadStore();
        });
    }

    private void loadStore() {
        AlphaRouteApp app = AlphaRouteApp.from(this);

        app.storeRepository().findById(storeId, store -> {
            if (store == null) {
                // The store was deleted while the session was being set up. Unlikely, but not impossible
                // - and monitoring a destination that does not exist would post "arriving at" nothing.
                Log.w(TAG, "The store to navigate to no longer exists - stopping.");
                sessionManager.stop(this);
                return;
            }

            storeLabel = store.routeNumber + " — " + store.storeName;
            monitor();
        });
    }

    private void monitor() {
        if (!LocationPermissionHelper.hasLocationPermission(this)) {
            // Without permission the fused provider throws, and there would be nothing to watch. Ending
            // the session is the honest response: a notification claiming to monitor the drive while it
            // cannot would be a lie the user has no way to detect.
            Log.w(TAG, "Location permission is not granted - cannot monitor arrival.");
            publishStopped();
            sessionManager.stop(this);
            return;
        }

        // The session is already begun by the manager; this is the belt-and-braces case of the service
        // being restarted by the system with an in-memory session that no longer exists.
        NavigationSession.Snapshot current = session.current();
        if (current == null || current.storeId() != storeId) {
            AlphaRouteApp.from(this).storeRepository().findById(storeId, store -> {
                if (store != null && session.current() == null) {
                    session.begin(store.id, new com.cph.alpharoute.domain.GeoCoordinate(
                            store.latitude, store.longitude));
                }
            });
        }

        // Observe the app's single location source, so the blue dot on the map and the arrival check can
        // never disagree about where the driver is. This is the same LiveData the Map tab reads.
        //
        // observeForever, not observe(owner, ...): a service has no LifecycleOwner. That is why the
        // observer is held in a field and removed explicitly in stopWatching() and onDestroy - a
        // LiveData never forgets an observer on its own, and one left behind keeps this service alive.
        locationProvider.currentFix().observeForever(fixObserver);

        // Start delivering. On Android 9 and later this is throttled to roughly once every ten seconds
        // while the app is in the background, which is ample: at 60 km/h that is one reading per 170 m,
        // and the arrival radius is measured in tens of metres with an approaching band in front of it.
        locationProvider.startUpdates();

        updateOngoingNotification(getString(R.string.nav_notification_waiting), false);
    }

    // ------------------------------------------------------------------ the arrival check

    private void onFix(@Nullable LocationFix fix) {
        if (fix == null) {
            return;
        }

        long now = System.currentTimeMillis();
        session.update(fix, arrivalRadiusMetres, now);

        NavigationSession.Snapshot snapshot = session.current();
        if (snapshot == null) {
            // The session ended between the update and here - the user tapped "End navigation". Nothing
            // to render, and stopping is already in progress.
            return;
        }

        ArrivalState state = snapshot.arrivalState();
        boolean arrived = state == ArrivalState.ARRIVED;

        if (Double.isNaN(snapshot.distanceMetres())) {
            updateOngoingNotification(getString(R.string.nav_notification_waiting), false);
        } else {
            updateOngoingNotification(
                    RouteTextFormatter.distanceText((int) Math.round(snapshot.distanceMetres())),
                    arrived);
        }

        if (arrived && !arrivalNotified) {
            arrivalNotified = true;
            postArrivalNotification();
            // The job is done. Monitoring stops, and the arrival notification stays behind as the way
            // back into the app to confirm. The session is deliberately NOT ended here: the sheet still
            // has to show "you have arrived" until the user taps End navigation or confirms in Phase 5.
            stopWatching();
        }
    }

    // ------------------------------------------------------------------ notifications

    /**
     * Posts the foreground notification and promotes the service to the foreground.
     *
     * <p>Two Android details are load-bearing here:
     *
     * <ul>
     *   <li>On Android 14 (API 34) and later, a foreground service of type
     *       {@code location} must pass the type to {@code startForeground} <b>and</b> declare it in the
     *       manifest. Missing either throws a {@code SecurityException}, and the manifest permission
     *       {@code FOREGROUND_SERVICE_LOCATION} must be present from API 34 as well.</li>
     *   <li>{@code FOREGROUND_SERVICE_TYPE_LOCATION} is also passed on Android 10-13, where the
     *       overload exists. On older levels the plain two-argument call is the only one available.</li>
     * </ul>
     */
    private void startForegroundNow() {
        Notification notification = NavigationNotifications.ongoing(
                this, getString(R.string.nav_notification_starting), false);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NavigationNotifications.ONGOING_NOTIFICATION_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            } else {
                startForeground(NavigationNotifications.ONGOING_NOTIFICATION_ID, notification);
            }
            foregroundStarted = true;
        } catch (Exception e) {
            // The system refused the foreground promotion - most often because the app was in the
            // background when it was requested, or because a permission is missing from the manifest.
            // There is no point monitoring: without foreground status the location updates would stop
            // as soon as the user switched back to Google Maps, and the arrival would never be detected.
            Log.e(TAG, "Could not promote the service to the foreground", e);
            stopSelf();
        }
    }

    private void updateOngoingNotification(@NonNull String distanceText, boolean arrived) {
        if (!foregroundStarted || notificationManager == null) {
            return;
        }
        notificationManager.notify(
                NavigationNotifications.ONGOING_NOTIFICATION_ID,
                NavigationNotifications.ongoing(this, distanceText, arrived));
    }

    /**
     * Posts the arrival alert.
     *
     * <p>Failure is logged and ignored on purpose. On Android 13+ the user may have refused the
     * notification permission, and {@code notify} then silently does nothing - the monitoring, the
     * session and the on-screen sheet are all unaffected, so the drive is not lost, only the alert.
     */
    private void postArrivalNotification() {
        if (notificationManager == null) {
            return;
        }
        try {
            notificationManager.notify(
                    NavigationNotifications.ARRIVAL_NOTIFICATION_ID,
                    NavigationNotifications.arrival(this, storeLabel, storeId));
        } catch (Exception e) {
            Log.e(TAG, "Could not post the arrival notification", e);
        }
    }

    // ------------------------------------------------------------------ teardown

    private void publishStopped() {
        if (session != null) {
            session.end();
        }
    }

    /** Stops location updates and drops the observer, but leaves the session and notifications alone. */
    private void stopWatching() {
        if (locationProvider == null) {
            return;
        }
        try {
            // The same object that was registered. See fixObserver for why this is a field.
            locationProvider.currentFix().removeObserver(fixObserver);
        } catch (Exception e) {
            Log.w(TAG, "Could not remove the location observer", e);
        }
        locationProvider.stopUpdates();
    }

    @Override
    public void onDestroy() {
        stopWatching();
        // Told last, so the sheet never sees "not monitoring" while the service is still delivering fixes.
        AlphaRouteApp.from(this).navigationSession().setMonitoring(false);
        Log.d(TAG, "Monitor service destroyed");
        super.onDestroy();
    }

    /**
     * Not a bound service.
     *
     * <p>Screens do not bind to it, and that is deliberate - a Fragment that bound would have to rebind
     * on every rotation and would hold a stale distance in between. Both sides go through
     * {@link NavigationSession} instead.
     */
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

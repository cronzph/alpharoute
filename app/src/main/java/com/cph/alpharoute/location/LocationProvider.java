package com.cph.alpharoute.location;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

/**
 * The app's single source of location.
 *
 * <p>Wraps the fused location provider and exposes two observable values: the latest
 * {@link LocationFix}, and whether location services are switched on at all. Both are {@link LiveData},
 * so a screen can watch them without worrying about threads.
 *
 * <p><b>One source, not two.</b> The blue dot on the map and the arrival check in Phase 5.1 must never
 * disagree about where the user is. If the map drew its own position - osmdroid's
 * {@code MyLocationNewOverlay} would happily do so - there would be two independent location streams,
 * and a store could be completed using a fix the user never saw. Everything therefore comes from here.
 *
 * <p><b>Accuracy is never invented.</b> Android reports "no accuracy value" for some readings, and the
 * translation below turns that into {@code NaN} rather than {@code 0}. Zero would be the most
 * flattering possible lie - it would make the worst fix look like the best one, and the arrival check
 * would trust it.
 *
 * <p>All fixes are delivered on the main thread, because the request is made with
 * {@code Looper.getMainLooper()} and the value is posted from there.
 */
public class LocationProvider {

    private static final String TAG = "AlphaRouteLocation";

    /**
     * How often we ask for a fix while a screen is watching.
     *
     * <p>Five seconds is a deliberate compromise. The map's blue dot should feel live, but a faster
     * interval would flatten the battery for no benefit - the user is driving, not sprinting.
     */
    private static final long UPDATE_INTERVAL_MS = 5000L;

    /** Android may deliver faster than the interval when another app requests it; accept that, no faster. */
    private static final long MIN_UPDATE_INTERVAL_MS = 2000L;

    /**
     * No minimum movement distance - every fix at the requested interval is delivered, even while the
     * device is stationary.
     *
     * <p><b>Version 14 fix.</b> This used to be 3f, to stop GPS jitter from redrawing the blue dot
     * constantly. But the arrival check (see {@code ArrivalEvaluator}) reads this same stream, and a
     * distance filter has a sharp edge for it: a driver standing still at a store never moves 3 m, so
     * once one degraded fix (poor accuracy, momentarily outside the radius) came in, no further fix
     * would ever arrive to correct it - the "Store completed?" button would disappear and stay gone
     * until the screen restarted location updates (e.g. switching tabs and back). Correctness of the
     * arrival state matters more than saving a few redundant marker redraws, so the filter is removed.
     */
    private static final float MIN_UPDATE_DISTANCE_M = 0f;

    private final Context appContext;
    private final FusedLocationProviderClient client;

    private final MutableLiveData<LocationFix> currentFix = new MutableLiveData<>();
    private final MutableLiveData<Boolean> locationEnabled = new MutableLiveData<>();

    private LocationCallback callback;
    private boolean updating;

    /**
     * @param context any context; the application context is taken from it, so holding this provider
     *                for the life of the process cannot leak an Activity
     */
    public LocationProvider(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.client = LocationServices.getFusedLocationProviderClient(appContext);
        this.locationEnabled.setValue(isLocationEnabled());
    }

    /** The latest fix. Emits nothing until one arrives; use {@code getValue() == null} for "not yet". */
    public LiveData<LocationFix> currentFix() {
        return currentFix;
    }

    /** Whether the device's location services are switched on. */
    public LiveData<Boolean> locationEnabled() {
        return locationEnabled;
    }

    /** True when either precise or approximate location has been granted. */
    public boolean hasPermission() {
        return LocationPermissionHelper.hasLocationPermission(appContext);
    }

    /**
     * Re-reads whether location services are on.
     *
     * <p>Called when a screen resumes, which is the moment that matters: the usual sequence is that the
     * user taps "Open location settings", switches GPS on, and comes back. A live
     * {@code PROVIDERS_CHANGED_ACTION} receiver would catch the change a few seconds earlier, at the
     * cost of a registered receiver with its own lifecycle and its own Android 14 registration flags.
     * Checking on resume is simpler and covers the real case.
     */
    public void refreshLocationEnabled() {
        locationEnabled.setValue(isLocationEnabled());
    }

    /**
     * Begins delivering fixes. Safe to call repeatedly.
     *
     * <p>Does nothing without permission - the fused provider throws {@link SecurityException} if asked,
     * and a thrown exception here would crash the map. Callers check
     * {@link #hasPermission()} first; this is the backstop.
     */
    public void startUpdates() {
        if (updating) {
            return;
        }
        if (!hasPermission()) {
            Log.w(TAG, "Location updates requested without permission - ignoring.");
            return;
        }

        LocationRequest request =
                new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
                        .setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MS)
                        .setMinUpdateDistanceMeters(MIN_UPDATE_DISTANCE_M)
                        .build();

        callback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                Location location = result.getLastLocation();
                if (location != null) {
                    currentFix.postValue(toFix(location));
                }
            }
        };

        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper());
            updating = true;
        } catch (SecurityException e) {
            // Permission was revoked between the check above and here. Not fatal.
            Log.e(TAG, "Location permission was revoked while starting updates", e);
            callback = null;
        } catch (Exception e) {
            Log.e(TAG, "Could not start location updates", e);
            callback = null;
        }
    }

    /**
     * Stops delivering fixes.
     *
     * <p>Called from the map's {@code onPause}, so a screen the user has left stops draining the
     * battery. Safe to call when updates were never started.
     */
    public void stopUpdates() {
        if (!updating || callback == null) {
            return;
        }
        try {
            client.removeLocationUpdates(callback);
        } catch (Exception e) {
            Log.e(TAG, "Could not stop location updates", e);
        }
        updating = false;
        callback = null;
    }

    /** The one place Android's {@code Location} becomes our own {@link LocationFix}. */
    private static LocationFix toFix(@NonNull Location location) {
        float accuracy = location.hasAccuracy() ? location.getAccuracy() : Float.NaN;
        return new LocationFix(
                location.getLatitude(),
                location.getLongitude(),
                accuracy,
                location.getTime());
    }

    /** Checks both providers, which works on every API level this app supports. */
    private boolean isLocationEnabled() {
        LocationManager manager =
                (LocationManager) appContext.getSystemService(Context.LOCATION_SERVICE);
        if (manager == null) {
            return false;
        }
        try {
            return manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    || manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception e) {
            Log.e(TAG, "Could not read the location provider state", e);
            return false;
        }
    }
}

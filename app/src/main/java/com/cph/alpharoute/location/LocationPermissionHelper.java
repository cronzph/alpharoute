package com.cph.alpharoute.location;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import androidx.core.content.ContextCompat;

/**
 * Everything about the location permission: whether it is granted, whether we may ask again, and
 * whether the user has refused for good.
 *
 * <p><b>Why "permanently denied" needs a stored flag.</b> Android gives us
 * {@code shouldShowRequestPermissionRationale}, which is {@code true} only between a first refusal and
 * a permanent one. It returns {@code false} both when the user has <i>never</i> been asked and when they
 * have refused for good - the two states that need completely different wording and completely
 * different buttons. The only way to tell them apart is to remember that we asked, which is what the
 * small preferences flag below is for.
 *
 * <p><b>Request both permissions.</b> Since Android 12 the user may answer a location request with
 * "approximate only". Requesting only {@code ACCESS_FINE_LOCATION} in that case leaves the app with
 * nothing at all. Requesting both means "approximate" still yields a usable - if coarser - position,
 * and {@link #isApproximateOnly} lets the screen say so honestly.
 */
public final class LocationPermissionHelper {

    /** Utility class - never instantiated. */
    private LocationPermissionHelper() {
    }

    /**
     * The permissions to request, in the order Android expects.
     *
     * <p>Precise first, approximate second. On Android 12 and later the dialog then offers the user the
     * "Precise / Approximate" choice itself.
     */
    public static final String[] PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };

    /**
     * What the app should do about the permission right now.
     */
    public enum State {

        /** We can show the user's position. */
        GRANTED,

        /** Not granted, but we may still show the system dialog. */
        DENIED_CAN_ASK,

        /**
         * The user refused for good. Asking again does nothing - Android silently denies without showing
         * a dialog - so the only route is the app's settings page.
         */
        DENIED_PERMANENTLY
    }

    private static final String PREFS_NAME = "alpharoute_prefs";
    private static final String KEY_REQUESTED = "location_permission_requested";

    /** True when precise location has been granted. */
    public static boolean hasFineLocation(Context context) {
        return isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION);
    }

    /** True when approximate location has been granted. */
    public static boolean hasCoarseLocation(Context context) {
        return isGranted(context, Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    /** True when either level has been granted, which is enough to show a position. */
    public static boolean hasLocationPermission(Context context) {
        return hasFineLocation(context) || hasCoarseLocation(context);
    }

    /**
     * True when the user granted only approximate location.
     *
     * <p>Worth surfacing: on Android 12 and later this is a very easy choice to make by accident, and
     * the resulting position can be off by a kilometre. The map says so rather than letting the user
     * wonder why the blue dot keeps drifting.
     */
    public static boolean isApproximateOnly(Context context) {
        return hasCoarseLocation(context) && !hasFineLocation(context);
    }

    /**
     * Works out what to do next.
     *
     * @param activity used for {@code shouldShowRequestPermissionRationale}, which only an Activity can
     *                 answer
     */
    public static State evaluate(Activity activity) {
        if (hasLocationPermission(activity)) {
            return State.GRANTED;
        }

        boolean askedBefore = preferences(activity).getBoolean(KEY_REQUESTED, false);
        boolean rationaleWouldShow =
                activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION);

        // Either we have never asked, or Android is telling us a rationale would be shown - which only
        // happens after a refusal that was not permanent. Both mean the dialog is still available.
        if (!askedBefore || rationaleWouldShow) {
            return State.DENIED_CAN_ASK;
        }

        return State.DENIED_PERMANENTLY;
    }

    /**
     * Records that the system dialog has been shown.
     *
     * <p>Must be called at the moment of requesting, not after the result arrives: if the user dismisses
     * the dialog by leaving the app, no result is ever delivered, and without this flag the next launch
     * would think it had never asked.
     */
    public static void markRequested(Activity activity) {
        preferences(activity).edit().putBoolean(KEY_REQUESTED, true).apply();
    }

    private static boolean isGranted(Context context, String permission) {
        return ContextCompat.checkSelfPermission(context, permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}

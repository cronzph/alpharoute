package com.cph.alpharoute.navigation;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.Locale;

/**
 * Hands the drive off to a navigation app - architecture decision AD-9.
 *
 * <p><b>This is the whole of this app's turn-by-turn.</b> Under decisions D-6 and D-7 the app does not
 * render guidance of its own: it opens Google Maps at the destination and lets it do the talking, the
 * live traffic and the road rules. That is the only $0 route to real traffic, because no free routing
 * service has any - openrouteservice, OSRM and Organic Maps all return a route with no idea what the
 * roads are doing today.
 *
 * <p><b>Everything Android-specific about that hand-off lives here</b>, so the rest of the app deals in
 * "launch navigation to this store" and never in Intents. That is the same isolation osmdroid gets
 * behind {@code MapMarkerController}.
 *
 * <p><b>The URI format was taken from Google's own documented scheme, not invented:</b>
 * {@code google.navigation:q=<lat>,<lng>&mode=d}, with {@code mode=d} for driving. It is launched with
 * {@code ACTION_VIEW} and restricted to the Google Maps package so it cannot be answered by anything
 * else that happens to accept the scheme.
 *
 * <p><b>Waze is the fallback</b>, using its documented deep link, because a driver without Google Maps
 * should still be able to go. Both are declared in the manifest's {@code <queries>} element - without
 * that, {@code resolveActivity()} cannot see them on Android 11 and the hand-off fails silently.
 */
public final class NavigationLauncher {

    private static final String TAG = "AlphaRouteNavigation";

    /** Google Maps, the primary target (D-6). */
    public static final String GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps";

    /** Waze, the fallback. Free, no key, and already on many phones. */
    public static final String WAZE_PACKAGE = "com.waze";

    /** Utility class - never instantiated. */
    private NavigationLauncher() {
    }

    /**
     * Opens navigation to a point, in whichever navigation app will take it.
     *
     * @param context an Activity context is preferred; anything else is handled, see below
     * @return {@code true} when an app was opened, {@code false} when none of them is installed - which
     *         the caller is expected to turn into a message rather than leaving the user with a button
     *         that did nothing
     */
    public static boolean launch(@NonNull Context context, double latitude, double longitude) {
        if (launchGoogleMaps(context, latitude, longitude)) {
            return true;
        }
        if (launchWaze(context, latitude, longitude)) {
            return true;
        }
        Log.w(TAG, "No navigation app could handle the hand-off");
        return false;
    }

    private static boolean launchGoogleMaps(@NonNull Context context,
                                            double latitude,
                                            double longitude) {
        Uri uri = Uri.parse("google.navigation:q="
                + formatCoordinate(latitude) + "," + formatCoordinate(longitude)
                + "&mode=d");

        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.setPackage(GOOGLE_MAPS_PACKAGE);
        return startIfResolvable(context, intent);
    }

    private static boolean launchWaze(@NonNull Context context, double latitude, double longitude) {
        // Waze's documented deep link. The comma between the two coordinates is percent-encoded, because
        // an unescaped comma in a query string separates values rather than being part of one.
        Uri uri = Uri.parse("https://waze.com/ul?ll="
                + formatCoordinate(latitude) + "%2C" + formatCoordinate(longitude)
                + "&navigate=yes");

        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.setPackage(WAZE_PACKAGE);
        return startIfResolvable(context, intent);
    }

    /**
     * Launches the Intent, but only if something can actually answer it.
     *
     * <p>{@code resolveActivity()} is the guard that stops an {@code ActivityNotFoundException} on a
     * phone with no navigation app installed. On Android 11 and later it can only see packages named in
     * the manifest's {@code <queries>} element, so a null answer here means "not installed" only because
     * that element is in place.
     */
    private static boolean startIfResolvable(@NonNull Context context, @NonNull Intent intent) {
        if (intent.resolveActivity(context.getPackageManager()) == null) {
            return false;
        }

        // An Activity context can start this directly. Anything else - the application context, say -
        // needs its own task, and without this the launch throws instead of opening the app.
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }

        try {
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            // resolveActivity() said yes and the launch still failed: a disabled app, or a device policy
            // blocking it. Treated as "no app", so the fallback is tried and the user is told.
            Log.w(TAG, "The navigation app could not be started", e);
            return false;
        }
    }

    /**
     * A coordinate as the URI needs it.
     *
     * <p>Two decisions are in this one line, and both of them are bugs waiting to happen:
     *
     * <ul>
     *   <li><b>{@link Locale#US} on purpose.</b> In a locale that writes decimals with a comma - German,
     *       French, most of Europe - the default formatter would produce {@code 14,599500}. The URI would
     *       then read that as two separate values, and the driver would be sent somewhere else entirely.
     *       A phone set to a comma-decimal locale is not exotic; it is a normal phone.</li>
     *   <li><b>{@code %.6f} rather than {@code Double.toString}.</b> A value small enough comes out of
     *       {@code toString} in scientific notation - {@code 1.0E-5} - which the URI cannot read.</li>
     * </ul>
     *
     * <p>Package-private and static so {@code NavigationLauncherTest} can check both, without a device
     * and without an installed navigation app.
     */
    static String formatCoordinate(double value) {
        return String.format(Locale.US, "%.6f", value);
    }
}

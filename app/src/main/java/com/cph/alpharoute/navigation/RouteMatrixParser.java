package com.cph.alpharoute.navigation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Turns the matrix service's answer into driving distances.
 *
 * <p><b>Phase 8.2.</b> The suggestion needs to know which store is genuinely closest to <i>drive</i> to.
 * A straight line says a shop across the river is 400 m away; the road says it is 4 km. This is where the
 * real number comes from.
 *
 * <p><b>One request, many destinations.</b> The matrix endpoint takes one origin and a list of
 * destinations and returns a grid of distances. Asking the directions endpoint once per store would be
 * twenty requests to answer one question; this is one.
 *
 * <p>Pure apart from {@code org.json} (AD-11).
 */
public final class RouteMatrixParser {

    /** Utility class - never instantiated. */
    private RouteMatrixParser() {
    }

    /**
     * Parses the distances row for a single source.
     *
     * <p>The grid is {@code sources × destinations}. This app always sends one source - where the user is -
     * so the row of interest is always the first, and the order of the returned array matches the order the
     * destinations were sent in. That is what lets the caller pair each distance with its store.
     *
     * @param body          the response body
     * @param expectedCount how many destinations were sent. Used to reject a grid of the wrong width rather
     *                      than silently returning a shorter array, which would pair distances with the
     *                      wrong stores
     * @return the distances in metres, in the order the destinations were sent, or {@code null} when the
     *         body is not a usable answer
     */
    @Nullable
    public static double[] parseDistances(@Nullable String body, int expectedCount) {
        if (body == null || body.trim().isEmpty() || expectedCount <= 0) {
            return null;
        }

        try {
            JSONObject root = new JSONObject(body);

            JSONArray distances = root.optJSONArray("distances");
            if (distances == null || distances.length() == 0) {
                return null;
            }

            JSONArray row = distances.optJSONArray(0);
            if (row == null) {
                return null;
            }

            if (row.length() != expectedCount) {
                // A grid of the wrong width means the service interpreted the request differently from how
                // it was sent. Returning it anyway would pair each distance with the wrong store - a wrong
                // answer that looks exactly like a right one.
                return null;
            }

            double[] result = new double[expectedCount];
            for (int i = 0; i < expectedCount; i++) {
                // NaN rather than a fallback number when a leg could not be computed. A zero here would
                // mean "this store is where you are standing", and would win every comparison.
                result[i] = row.isNull(i) ? Double.NaN : row.optDouble(i, Double.NaN);
            }
            return result;

        } catch (JSONException e) {
            return null;
        }
    }

    /**
     * Whether a parsed row contains at least one usable distance.
     *
     * <p>A row of nothing but {@code NaN} is not worth acting on, and the caller falls back to straight
     * lines rather than showing a suggestion built from no measurements at all.
     */
    public static boolean hasAnyUsable(@Nullable double[] distances) {
        if (distances == null) {
            return false;
        }
        for (double distance : distances) {
            if (!Double.isNaN(distance)) {
                return true;
            }
        }
        return false;
    }

    /** The index of the smallest usable distance, or {@code -1} when there is none. */
    public static int indexOfNearest(@Nullable double[] distances) {
        if (distances == null) {
            return -1;
        }
        int best = -1;
        for (int i = 0; i < distances.length; i++) {
            double distance = distances[i];
            if (Double.isNaN(distance)) {
                continue;
            }
            // Strictly less than, so the FIRST of two equal distances wins - the same lowest-index
            // tie-break the pure strategies use, so the choice does not depend on iteration order.
            if (best == -1 || distance < distances[best]) {
                best = i;
            }
        }
        return best;
    }

    /** A defensive copy of a row, so a caller cannot mutate what it was handed. */
    @NonNull
    public static double[] copyOf(@NonNull double[] source) {
        double[] copy = new double[source.length];
        System.arraycopy(source, 0, copy, 0, source.length);
        return copy;
    }
}

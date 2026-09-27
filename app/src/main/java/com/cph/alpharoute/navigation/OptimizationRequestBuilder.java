package com.cph.alpharoute.navigation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cph.alpharoute.domain.GeoCoordinate;
import com.cph.alpharoute.domain.StoreCandidate;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * Builds the body of an optimization request.
 *
 * <p><b>Pure apart from {@code org.json} (AD-11), and that matters here more than anywhere else.</b> The
 * request body is where the app tells the solver what the problem is, and a mistake in it produces a
 * confident, well-formed answer to the wrong question - a route that visits every shop in a strange order,
 * with nothing to indicate why. So the body is built by a class with no network, no clock and no Android,
 * and every field of it is asserted in {@code OptimizationRequestBuilderTest}.
 *
 * <h3>The shape of the problem</h3>
 *
 * <pre>
 *   jobs      one per store to visit. The job's id IS the store id, which is what lets the response be
 *             turned back into store ids with no lookup table
 *   vehicles  exactly one, because this is a solo app with one driver and one car
 *   start     the user's current position, when there is one
 *   end       Home, when it is set - the route finishes there
 * </pre>
 *
 * <h3>Two deliberate choices</h3>
 *
 * <ul>
 *   <li><b>{@code "options": {"g": true}} IS sent</b> - see {@link #OPTIONS_GEOMETRY} below. It is here for
 *       the distance, not for the line.</li>
 *   <li><b>No service time.</b> {@code "service"} is how long the driver spends at each stop. The app has
 *       no idea how long a delivery takes, and inventing a number would change the order the solver
 *       returns. Left at zero, the solver optimises the driving, which is the part the app actually
 *       knows.</li>
 * </ul>
 */
public final class OptimizationRequestBuilder {

    /**
     * Asks the service for geometry - and, unavoidably, that is also the only way to get a distance.
     *
     * <p><b>This used to be omitted, and the reason given was wrong.</b> The original note said geometry
     * "roughly triples the response for a line this app does not draw". Both halves were wrong. It does not
     * triple it, and the app does not get a line out of it anyway - {@code OptimizationResponseParser}
     * ignores {@code geometry} entirely, because the map draws straight lines between stops and the real
     * driving line comes from the directions call.
     *
     * <p><b>What actually happens without it: the service returns no distance anywhere.</b> Not in the
     * summary, not on the steps - only {@code duration} and {@code cost}. Measured against the live service
     * on <b>2026-09-27</b>, a 50-stop request:
     *
     * <table>
     *   <tr><th></th><th>response size</th><th>{@code summary.distance}</th><th>per-stop distance</th></tr>
     *   <tr><td>without this option</td><td>8,058 bytes</td><td><b>absent</b></td><td><b>absent</b></td></tr>
     *   <tr><td>with it</td><td>13,304 bytes</td><td>66,335 m</td><td>present</td></tr>
     * </table>
     *
     * <p>So the honest cost is <b>about 5 KB on the largest request the app can make</b>, against a summary
     * line that otherwise always reads "0 m" and a per-stop leg distance that is always zero. The screen
     * promises a distance; this is the only way to have one.
     */
    public static final boolean OPTIONS_GEOMETRY = true;

    /** Utility class - never instantiated. */
    private OptimizationRequestBuilder() {
    }

    /**
     * Builds the request body.
     *
     * @param stores            the stores to visit. Must not be empty, and must not exceed
     *                          {@link RoutingConfig#MAX_OPTIMIZATION_JOBS}
     * @param start             where the route begins, or {@code null} for "anywhere" - used when there is
     *                          no usable fix
     * @param end               where the route finishes, or {@code null} when Home is not set
     * @return the JSON body
     * @throws JSONException if the body could not be assembled. The caller reports this as a failed request
     *                       rather than sending a malformed one
     * @throws IllegalArgumentException if the list is empty or over the service's limit. Both are caller
     *                       mistakes, and both would be refused by the service anyway - failing here means
     *                       the reason is in the app's own terms
     */
    @NonNull
    public static JSONObject build(@NonNull List<StoreCandidate> stores,
                                   @Nullable GeoCoordinate start,
                                   @Nullable GeoCoordinate end) throws JSONException {

        if (stores.isEmpty()) {
            throw new IllegalArgumentException("An optimization needs at least one store");
        }
        if (stores.size() > RoutingConfig.MAX_OPTIMIZATION_JOBS) {
            // Checked here rather than at the call site, so every caller is protected by the same rule.
            throw new IllegalArgumentException(
                    "At most " + RoutingConfig.MAX_OPTIMIZATION_JOBS + " stops, got " + stores.size());
        }

        JSONArray jobs = new JSONArray();
        for (StoreCandidate store : stores) {
            if (store == null) {
                continue;
            }
            JSONObject job = new JSONObject();
            // The store id IS the job id. That is what makes the response turn back into stores without a
            // lookup table - and a lookup table is a thing that can be wrong.
            job.put("id", store.id());
            // [longitude, latitude] - the order the service uses, and the opposite of how a coordinate is
            // spoken. Getting it backwards puts every stop in the sea.
            job.put("location", coordinateArray(store.longitude(), store.latitude()));
            job.put("service", 0);
            jobs.put(job);
        }

        JSONObject vehicle = new JSONObject();
        vehicle.put("id", 1);
        vehicle.put("profile", RoutingConfig.PROFILE_DRIVING_CAR);
        if (start != null) {
            vehicle.put("start", coordinateArray(start.longitude(), start.latitude()));
        }
        if (end != null) {
            vehicle.put("end", coordinateArray(end.longitude(), end.latitude()));
        }

        JSONArray vehicles = new JSONArray();
        vehicles.put(vehicle);

        JSONObject body = new JSONObject();
        body.put("jobs", jobs);
        body.put("vehicles", vehicles);

        // The one option the app sends, and the reason is the distance rather than the line - see
        // OPTIONS_GEOMETRY. The response's "geometry" field is ignored by the parser.
        JSONObject options = new JSONObject();
        options.put("g", OPTIONS_GEOMETRY);
        body.put("options", options);

        return body;
    }

    /** {@code [longitude, latitude]}, which is the order the service expects. */
    @NonNull
    private static JSONArray coordinateArray(double longitude, double latitude) throws JSONException {
        JSONArray pair = new JSONArray();
        pair.put(longitude);
        pair.put(latitude);
        return pair;
    }
}

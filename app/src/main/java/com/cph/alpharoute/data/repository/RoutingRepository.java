package com.cph.alpharoute.data.repository;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cph.alpharoute.domain.OptimizationPlan;
import com.cph.alpharoute.domain.RoutePlan;
import com.cph.alpharoute.domain.RouteOrderPlanner;
import com.cph.alpharoute.domain.GeoCoordinate;
import com.cph.alpharoute.domain.StoreCandidate;
import com.cph.alpharoute.navigation.OptimizationRequestBuilder;
import com.cph.alpharoute.navigation.OptimizationResponseParser;
import com.cph.alpharoute.navigation.OptimizationState;
import com.cph.alpharoute.navigation.RouteMatrixParser;
import com.cph.alpharoute.navigation.RouteResponseParser;
import com.cph.alpharoute.navigation.RouteState;
import com.cph.alpharoute.navigation.RoutingConfig;
import com.cph.alpharoute.util.AppExecutors;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Asks the routing service for the drive from one point to another.
 *
 * <p>This is the first and only place in the app that makes a network request, and it is a Repository
 * because the project's rule is that a screen never touches HTTP itself - it talks to a repository, and
 * the repository talks to the outside world. The call runs on
 * {@link AppExecutors#networkIO()}, never on the disk thread, so a slow service can never hold up a
 * database write.
 *
 * <p><b>It holds the current route as {@link LiveData} rather than handing a result back.</b> That is the
 * same shape as {@code LocationProvider.currentFix()}, and it is what lets two things that cannot see
 * each other stay in step: the Route Preview sheet asks for the route, and the Map tab - which owns the
 * map and therefore owns the polyline - draws it. Neither needs a reference to the other, and nothing has
 * to be passed between fragments, which is exactly the plumbing Phase 3.4 removed in favour of
 * observation.
 *
 * <p><b>Nothing here invents a route.</b> If the request fails, or the answer cannot be read as a route,
 * the state becomes a failure and the screen says so. A line drawn from a partial answer would look
 * plausible and be wrong, and a driver following it is worse off than one who was told to try again.
 *
 * <p><b>This class is read-only with respect to the database.</b> A route is a transient thing; nothing
 * about it is stored. The only table involved in navigation is {@code app_settings}, and Phase 4.3 is
 * where the active destination is recorded.
 */
public class RoutingRepository {

    private static final String TAG = "AlphaRouteRoutingRepo";

    /**
     * Ten seconds to reach the service, fifteen to hear back.
     *
     * <p>Short enough that a driver waiting on a dead connection is told quickly, and long enough for a
     * cold TLS handshake plus a route calculation over a mobile network.
     */
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 15_000;

    /**
     * Forty-five seconds for an optimization answer.
     *
     * <p>Longer than a route calculation on purpose: the solver runs over up to fifty stops, and a plan for
     * a whole day's route genuinely takes longer than the drive to one shop. Too short a timeout here would
     * report a working service as unreachable.
     */
    private static final int OPTIMIZATION_READ_TIMEOUT_MS = 45_000;

    private static final int HTTP_OK = 200;
    private static final int HTTP_TOO_MANY_REQUESTS = 429;

    /**
     * How much of the service's own error message is kept.
     *
     * <p>The reason is rendered inside a sentence on the route screen. VROOM's "unfound route" message
     * lists every coordinate it could not place, so a fifty-stop request with several bad pins would
     * otherwise put a paragraph into a {@code TextView}.
     */
    private static final int MAX_ERROR_DETAIL_CHARS = 160;

    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String JSON_CONTENT_TYPE = "application/json";

    private final AppExecutors executors;
    private final MutableLiveData<RouteState> state = new MutableLiveData<>(RouteState.idle());

    /**
     * The current optimization, observed.
     *
     * <p>Separate from {@link #state} rather than folded into it, because the two are different questions
     * asked at different times: the route state is "the drive to this one store", and this is "the order to
     * visit all of them". One object holding both would have to be half-empty most of the time.
     */
    private final MutableLiveData<OptimizationState> optimization =
            new MutableLiveData<>(OptimizationState.idle());

    public RoutingRepository(@NonNull AppExecutors executors) {
        this.executors = executors;
    }

    /**
     * The current route request, observed.
     *
     * <p>Starts at {@link RouteState.Status#IDLE}, and returns to it when {@link #clearRoute()} is called.
     * Because it lives in the application container, a route survives a rotation - so turning the phone
     * does not spend a second routing request against the free plan's daily allowance.
     */
    public LiveData<RouteState> observeState() {
        return state;
    }

    /**
     * Asks for the route from {@code origin} to {@code destination}.
     *
     * @param origin      the current position, or {@code null} when there is no usable fix yet - which is
     *                    reported as {@link RouteState.Status#NO_LOCATION} rather than sent to the service
     * @param destination the store being driven to
     */
    public void calculateRoute(@Nullable GeoCoordinate origin, @NonNull GeoCoordinate destination) {
        if (origin == null) {
            publish(RouteState.failed(RouteState.Status.NO_LOCATION));
            return;
        }

        // A build with no key can never succeed, so it is not sent. This is the seam Phase 4.1 built:
        // without it the request would come back 401 and look like a rejected key rather than a missing
        // one.
        if (!RoutingConfig.isConfigured()) {
            publish(RouteState.failed(RouteState.Status.NOT_CONFIGURED));
            return;
        }

        publish(RouteState.loading());

        executors.networkIO().execute(() -> {
            final RouteState result = requestRoute(origin, destination);
            executors.postToMain(() -> state.setValue(result));
        });
    }

    /**
     * Drops the route and returns to {@link RouteState.Status#IDLE}.
     *
     * <p>Called when the preview is cancelled or closed, so a polyline cannot outlive the screen that
     * asked for it.
     */
    public void clearRoute() {
        publish(RouteState.idle());
    }

    // ------------------------------------------------------------------ the request

    private RouteState requestRoute(@NonNull GeoCoordinate origin, @NonNull GeoCoordinate destination) {
        HttpURLConnection connection = null;

        try {
            connection = (HttpURLConnection) new URL(RoutingConfig.directionsUrl()).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setDoOutput(true);
            connection.setRequestProperty(CONTENT_TYPE_HEADER, JSON_CONTENT_TYPE);

            // The key goes in a header, never in the URL. Verified against the live service in Phase 4.1:
            // a request without this header answers 401 "Authorization field missing", and a URL carrying
            // the key would also end up in logs and crash reports.
            connection.setRequestProperty(RoutingConfig.AUTHORIZATION_HEADER, RoutingConfig.apiKey());

            byte[] body = buildRequestBody(origin, destination).getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body);
            }

            int code = connection.getResponseCode();

            if (code == HTTP_OK) {
                RoutePlan plan = RouteResponseParser.parse(readBody(connection.getInputStream()));
                return plan == null
                        ? RouteState.failed(RouteState.Status.FAILED)
                        : RouteState.ready(plan);
            }

            if (code == HTTP_TOO_MANY_REQUESTS) {
                return RouteState.failed(RouteState.Status.QUOTA_EXHAUSTED);
            }

            // 401, 403, 5xx and anything else. The user's next move is the same for all of them, so they
            // share one message; the code is logged so the difference is not lost.
            Log.w(TAG, "The routing service answered HTTP " + code);
            return RouteState.failed(RouteState.Status.FAILED);

        } catch (IOException e) {
            // No connection, no DNS, a timeout, a socket dropped mid-answer. From the user's side these
            // are all "the routing service is not reachable right now".
            Log.w(TAG, "Could not reach the routing service", e);
            return RouteState.failed(RouteState.Status.OFFLINE);

        } catch (JSONException e) {
            // The body could not be built. Nothing was sent.
            Log.e(TAG, "Could not build the routing request body", e);
            return RouteState.failed(RouteState.Status.FAILED);

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String readBody(@Nullable InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        try (InputStream in = stream; ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[4096];
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    // ------------------------------------------------------------------ the request body

    /**
     * The JSON body the service expects.
     *
     * <p>Built with {@code org.json} rather than by joining strings, so a value can never break the JSON
     * it is inside.
     *
     * <p>Package-private and static so {@code RoutingRepositoryTest} can check it without a network call.
     * That is not ceremony: the coordinate order is the single easiest mistake in this whole feature, and
     * it is silent - a swapped pair produces a perfectly valid request for a point in the wrong place, and
     * the service answers with a perfectly valid route to it.
     */
    static String buildRequestBody(@NonNull GeoCoordinate origin, @NonNull GeoCoordinate destination)
            throws JSONException {
        JSONArray coordinates = new JSONArray();
        coordinates.put(coordinatePair(origin));
        coordinates.put(coordinatePair(destination));

        JSONObject body = new JSONObject();
        body.put("coordinates", coordinates);
        return body.toString();
    }

    /**
     * One pair, <b>longitude first</b>.
     *
     * <p>The service's coordinates are {@code [longitude, latitude]} in the request and in the answer
     * alike - the opposite of how a coordinate is spoken aloud. The answer is swapped back by
     * {@code RouteResponseParser}; this is the other half of the same rule, and the two must stay
     * symmetric.
     */
    private static JSONArray coordinatePair(@NonNull GeoCoordinate point) throws JSONException {
        JSONArray pair = new JSONArray();
        pair.put(point.longitude());
        pair.put(point.latitude());
        return pair;
    }

    // ------------------------------------------------------------------ the matrix (Phase 8.2)

    /**
     * Asks for the real driving distances from one point to many.
     *
     * <p><b>Why the suggestion needs this.</b> A straight line says a shop across the river is 400 m away
     * and the road says 4 km. The nearest store by straight line is not always the nearest to drive to, and
     * a suggestion that is confidently wrong is worse than one that admits it is approximate.
     *
     * <p><b>One request, not one per store.</b> The free plan allows 500 matrix requests a day; asking the
     * directions endpoint once per candidate would spend twenty to answer one question and hit the
     * per-minute limit.
     *
     * <p><b>The callback always runs, and always on the main thread.</b> A failure delivers {@code null}
     * rather than nothing happening - a caller waiting for an answer that never comes is how a screen ends
     * up stuck on a spinner. The suggestion treats {@code null} as "use the straight lines", which is
     * exactly the Phase 7 behaviour and needs no special case.
     *
     * @param origin       where the user is. Must not be {@code null} - there is nothing to measure from
     * @param destinations the stores, in the order the distances should come back
     * @param callback     receives the distances in metres, in the same order, or {@code null} when the
     *                     service could not answer
     */
    public void loadDrivingDistances(@NonNull GeoCoordinate origin,
                                     @NonNull List<GeoCoordinate> destinations,
                                     @NonNull RepositoryCallback<double[]> callback) {
        if (destinations.isEmpty()) {
            executors.postToMain(() -> callback.onResult(null));
            return;
        }

        if (!RoutingConfig.isConfigured()) {
            executors.postToMain(() -> callback.onResult(null));
            return;
        }

        executors.networkIO().execute(() -> {
            final double[] result = requestMatrix(origin, destinations);
            executors.postToMain(() -> callback.onResult(result));
        });
    }

    @Nullable
    private double[] requestMatrix(@NonNull GeoCoordinate origin,
                                   @NonNull List<GeoCoordinate> destinations) {
        HttpURLConnection connection = null;

        try {
            connection = (HttpURLConnection) new URL(RoutingConfig.matrixUrl()).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setDoOutput(true);
            connection.setRequestProperty(CONTENT_TYPE_HEADER, JSON_CONTENT_TYPE);
            connection.setRequestProperty(RoutingConfig.AUTHORIZATION_HEADER, RoutingConfig.apiKey());

            byte[] body = buildMatrixBody(origin, destinations).getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body);
            }

            int code = connection.getResponseCode();
            if (code != HTTP_OK) {
                // Any non-200 - a bad key, a quota, an outage. The caller's fallback is the same for all of
                // them, so the distinction is logged rather than surfaced.
                Log.w(TAG, "The matrix service answered HTTP " + code);
                return null;
            }

            double[] distances =
                    RouteMatrixParser.parseDistances(readBody(connection.getInputStream()), destinations.size());

            // A row of nothing but NaN is not worth acting on - the caller falls back to straight lines.
            return RouteMatrixParser.hasAnyUsable(distances) ? distances : null;

        } catch (IOException e) {
            Log.w(TAG, "Could not reach the matrix service", e);
            return null;

        } catch (JSONException e) {
            Log.e(TAG, "Could not build the matrix request body", e);
            return null;

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * The matrix request body.
     *
     * <p>{@code metrics} asks for distances only. Durations would roughly double the response and the
     * suggestion does not use them.
     *
     * <p><b>{@code sources} and {@code destinations} are INDICES into {@code locations}, not coordinates -
     * and sending coordinates is the mistake this method used to make.</b> It is a natural one: the
     * directions endpoint takes bare coordinate pairs, so passing the same pairs here looks right. The
     * service disagrees and answers <b>400</b> with
     * {@code "Parameter 'sources' has incorrect value or format."} - verified against the live service on
     * 2026-09-27.
     *
     * <p>That failure was silent in the app: {@code requestMatrix} returns {@code null} for any non-200, the
     * caller reads {@code null} as "no answer", and the suggestion quietly fell back to straight-line
     * distance - the exact approximation Phase 8.2 exists to replace. Nothing crashed and nothing was
     * logged above a warning, so a permanently broken request looked like a working feature that was simply
     * never very accurate.
     *
     * <p>The layout is fixed by {@link #concatenatedLocations}: the origin is index 0 and the destinations
     * are 1..n, so the two index arrays are {@code [0]} and {@code [1..n]}.
     *
     * <p>Package-private and static so {@code RoutingRepositoryTest} can check both the index format and the
     * coordinate order without a network call - the same trap as the directions body, and just as silent.
     */
    static String buildMatrixBody(@NonNull GeoCoordinate origin,
                                  @NonNull List<GeoCoordinate> destinations) throws JSONException {
        JSONArray sources = new JSONArray();
        sources.put(0);

        JSONArray destinationIndices = new JSONArray();
        for (int i = 0; i < destinations.size(); i++) {
            // 1-based because the origin occupies index 0. Off by one here pairs every distance with the
            // wrong store, and the grid would still have the width the parser expects - so it would be
            // accepted as a good answer.
            destinationIndices.put(i + 1);
        }

        JSONArray metrics = new JSONArray();
        metrics.put("distance");

        JSONObject body = new JSONObject();
        body.put("locations", concatenatedLocations(origin, destinations));
        body.put("sources", sources);
        body.put("destinations", destinationIndices);
        body.put("metrics", metrics);
        return body.toString();
    }

    /**
     * Every point in one array, sources first.
     *
     * <p>The matrix endpoint takes a single {@code locations} list and then indexes into it with
     * {@code sources} and {@code destinations}. So the source is index 0 and the destinations are 1..n, and
     * the two index arrays must agree with this layout exactly.
     */
    private static JSONArray concatenatedLocations(@NonNull GeoCoordinate origin,
                                                   @NonNull List<GeoCoordinate> destinations)
            throws JSONException {
        JSONArray locations = new JSONArray();
        locations.put(coordinatePair(origin));
        for (GeoCoordinate destination : destinations) {
            locations.put(coordinatePair(destination));
        }
        return locations;
    }

    // ------------------------------------------------------------------ the optimization (Phase 8.3)

    /** The current optimization request, observed. */
    @NonNull
    public LiveData<OptimizationState> observeOptimization() {
        return optimization;
    }

    /**
     * Asks the service for the best order to visit every pending store in.
     *
     * <p><b>No screen calls this any more.</b> The route screen plans locally through
     * {@link #planRoute(List, GeoCoordinate, GeoCoordinate)} - see that method for why. This is kept
     * because it is the only path to a solver-quality order, it is verified against the live service, and
     * going back to it is one line in {@code OptimizedRouteFragment}. It is dead code until then, and
     * deleting it would throw away the only way to check the local order against a real solver.
     *
     * <p><b>What it answers, when it answers.</b> This is the only call that answers "in what order", as
     * opposed to "which one next". VROOM examines the whole set at once, where the local planner walks
     * greedily from one store to the next.
     *
     * <p><b>The result is not stored.</b> Like the directions call, an optimisation is transient - the app
     * holds it as {@link LiveData} so the screen can draw it, and nothing about it reaches the database.
     *
     * @param stores the stores to visit. An empty list, or one over the service's limit, is reported as a
     *               state rather than sent
     * @param start  where the route begins, or {@code null} when there is no fix - the service then chooses
     */
    public void optimize(@NonNull List<StoreCandidate> stores,
                         @Nullable GeoCoordinate start,
                         @Nullable GeoCoordinate end) {

        if (stores.isEmpty()) {
            publishOptimization(OptimizationState.failed(OptimizationState.Status.FAILED));
            return;
        }

        if (stores.size() > RoutingConfig.MAX_OPTIMIZATION_JOBS) {
            // Checked before sending, so the user is told the real reason rather than seeing a 400.
            publishOptimization(OptimizationState.failed(OptimizationState.Status.TOO_MANY_STOPS));
            return;
        }

        if (!RoutingConfig.isConfigured()) {
            publishOptimization(OptimizationState.failed(OptimizationState.Status.NOT_CONFIGURED));
            return;
        }

        publishOptimization(OptimizationState.loading());

        final boolean startsAtLocation = start != null;
        final boolean endsAtHome = end != null;

        executors.networkIO().execute(() -> {
            final OptimizationState result = requestOptimization(stores, start, end);
            executors.postToMain(() -> optimization.setValue(result));
        });
    }

    /** Drops the optimisation and returns to idle. */
    public void clearOptimization() {
        publishOptimization(OptimizationState.idle());
    }

    // ------------------------------------------------------------------ the local plan

    /**
     * Orders every pending store, on the device. No network, no key, no quota.
     *
     * <p><b>This is what the route screen uses, and it is the answer to a screen that could fail.</b> The
     * service call below produced a route only when the network worked, the daily allowance was not spent,
     * and every store happened to sit near a road the solver knew. Any one of those going wrong replaced
     * the route with "could not build a route" - a screen that does not do its one job.
     *
     * <p><b>Nothing here can fail.</b> The planner is pure arithmetic over coordinates the app already
     * holds, so there is no status to report beyond READY, and the only way to get a failure state is to
     * ask for a route with no stores in it. The screen keeps its failure handling because
     * {@link OptimizationState} is shared with the service path, but in practice the route now always
     * appears.
     *
     * <p><b>Runs on the calling thread, deliberately.</b> The other methods here push work to
     * {@code networkIO()} because they wait on a socket; this one is about 2,500 distance calculations for
     * fifty stores, which is well under a frame. Putting it on an executor would add a thread hop, a
     * callback, and a window where the screen is empty for no measurable gain.
     *
     * <p><b>The service's 50-stop cap does not apply.</b> That is VROOM's limit, not the planner's, so a
     * round with more than fifty stores now plans in full instead of being refused.
     *
     * @param stores the stores to visit
     * @param start  where the route begins, or {@code null} when there is no usable fix
     * @param end    where the route finishes - normally Home - or {@code null}
     */
    public void planRoute(@NonNull List<StoreCandidate> stores,
                          @Nullable GeoCoordinate start,
                          @Nullable GeoCoordinate end) {

        if (stores.isEmpty()) {
            // The only way to fail. The screen checks this first and shows "nothing left to visit", so
            // reaching here means the two checks disagree.
            publishOptimization(OptimizationState.failed(OptimizationState.Status.FAILED));
            return;
        }

        publishOptimization(OptimizationState.ready(RouteOrderPlanner.plan(stores, start, end)));
    }

    /**
     * Scores an order the user has already decided, without changing it.
     *
     * <p><b>The other half of the manual plan.</b> Once the stops have been dragged into the order the user
     * wants, the route has to be described in the same terms as a suggested one - a sequence, a leg into
     * each stop, a total - and none of that may involve reordering anything. The user's order is the answer;
     * this only says what it costs.
     *
     * <p>Same thread, same reasons as {@link #planRoute} - it is arithmetic over coordinates the app already
     * holds, so there is nothing to wait for.
     *
     * @param ordered the stores in the order to drive them, already decided
     */
    public void measureRoute(@NonNull List<StoreCandidate> ordered,
                             @Nullable GeoCoordinate start,
                             @Nullable GeoCoordinate end) {

        if (ordered.isEmpty()) {
            publishOptimization(OptimizationState.failed(OptimizationState.Status.FAILED));
            return;
        }

        publishOptimization(OptimizationState.ready(RouteOrderPlanner.measure(ordered, start, end)));
    }

    @NonNull
    private OptimizationState requestOptimization(@NonNull List<StoreCandidate> stores,
                                                  @Nullable GeoCoordinate start,
                                                  @Nullable GeoCoordinate end) {
        HttpURLConnection connection = null;

        try {
            connection = (HttpURLConnection) new URL(RoutingConfig.optimizationUrl()).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            // Longer than the directions timeout: this is a solver run over up to fifty stops, and it takes
            // noticeably longer than a single route calculation.
            connection.setReadTimeout(OPTIMIZATION_READ_TIMEOUT_MS);
            connection.setDoOutput(true);
            connection.setRequestProperty(CONTENT_TYPE_HEADER, JSON_CONTENT_TYPE);
            connection.setRequestProperty(RoutingConfig.AUTHORIZATION_HEADER, RoutingConfig.apiKey());

            byte[] body = OptimizationRequestBuilder.build(stores, start, end)
                    .toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body);
            }

            int code = connection.getResponseCode();

            if (code == HTTP_OK) {
                OptimizationPlan plan = OptimizationResponseParser.parse(
                        readBody(connection.getInputStream()), start != null, end != null);
                return plan == null
                        ? OptimizationState.failed(OptimizationState.Status.FAILED,
                                "HTTP 200 but the answer could not be read")
                        : OptimizationState.ready(plan);
            }

            if (code == HTTP_TOO_MANY_REQUESTS) {
                return OptimizationState.failed(OptimizationState.Status.QUOTA_EXHAUSTED);
            }

            // The body carries the real reason, and it is worth more than the status code. A 500 from this
            // endpoint means the solver found no road near one of the coordinates, and the message names
            // which one - which is the difference between "try again" and "fix that pin".
            //
            // Read from the ERROR stream: a failed response's body is not on the input stream, and reading
            // the wrong one returns an empty string that looks like the service said nothing.
            String reason = serviceError(readBody(connection.getErrorStream()));
            Log.w(TAG, "The optimization service answered HTTP " + code
                    + (reason.isEmpty() ? "" : ": " + reason));
            return OptimizationState.failed(OptimizationState.Status.FAILED,
                    reason.isEmpty() ? "HTTP " + code : "HTTP " + code + " - " + reason);

        } catch (IOException e) {
            Log.w(TAG, "Could not reach the optimization service", e);
            return OptimizationState.failed(OptimizationState.Status.OFFLINE,
                    e.getClass().getSimpleName());

        } catch (JSONException e) {
            Log.e(TAG, "Could not build the optimization request body", e);
            return OptimizationState.failed(OptimizationState.Status.FAILED, "bad request body");

        } catch (IllegalArgumentException e) {
            // The builder refused the request - an empty list or one over the limit. Both are checked
            // before sending too, so reaching here means the checks and the builder disagree.
            Log.e(TAG, "The optimization request was refused", e);
            return OptimizationState.failed(OptimizationState.Status.TOO_MANY_STOPS);

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void publishOptimization(@NonNull OptimizationState next) {
        optimization.setValue(next);
    }

    // ------------------------------------------------------------------ reading a failure

    /**
     * The service's own error message, pulled out of a failed response body.
     *
     * <p><b>Two shapes arrive here and both are read.</b> VROOM reports its own failures as
     * {@code {"code":3,"error":"Unfound route(s) from location [...]"}}, where the message is the string
     * itself; openrouteservice's own validation reports
     * {@code {"error":{"code":6002,"message":"Parameter 'sources' has incorrect value or format."}}}, where
     * it is nested one level down. Both were captured from the live service on 2026-09-27.
     *
     * <p>This exists because the status code alone cannot tell these apart, and the user's next move is
     * different for each: a 401 wants the key checked, a 429 wants tomorrow, and a 500 naming a coordinate
     * wants that one pin moved. All three used to read "HTTP &lt;code&gt;".
     *
     * @return the message, or an empty string when the body is not JSON or carries no message - an nginx
     *         "404 Not Found" page is a real case and has nothing to say
     */
    @NonNull
    private static String serviceError(@Nullable String body) {
        if (body == null || body.trim().isEmpty()) {
            return "";
        }
        try {
            Object error = new JSONObject(body).opt("error");
            if (error instanceof JSONObject) {
                return truncate(((JSONObject) error).optString("message", ""));
            }
            if (error instanceof String) {
                return truncate((String) error);
            }
            return "";
        } catch (JSONException e) {
            // Not JSON at all. Nothing useful to pass on, and not worth a log line of its own - the caller
            // logs the status code.
            return "";
        }
    }

    /** Keeps a service message short enough to sit inside a sentence. */
    @NonNull
    private static String truncate(@NonNull String message) {
        String trimmed = message.trim();
        return trimmed.length() <= MAX_ERROR_DETAIL_CHARS
                ? trimmed
                : trimmed.substring(0, MAX_ERROR_DETAIL_CHARS) + "...";
    }

    // ------------------------------------------------------------------ state

    /** Publishes a state on the main thread, which is where LiveData is allowed to be set from. */
    private void publish(@NonNull RouteState next) {
        state.setValue(next);
    }
}

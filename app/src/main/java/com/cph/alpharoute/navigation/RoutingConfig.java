package com.cph.alpharoute.navigation;

import androidx.annotation.Nullable;

import com.cph.alpharoute.BuildConfig;

/**
 * Where the routing service is, and whether this build can use it.
 *
 * <p>openrouteservice supplies the driving route between the user's position and a store: the distance,
 * the duration and the line to draw on the map. It is the one service in this project that needs a key,
 * and the key is free - the Standard plan is EUR 0 with no card, which is what decision D-2 requires.
 *
 * <p><b>This class is the only place in the app that reads {@code BuildConfig.ORS_API_KEY}.</b> That is
 * the same isolation the project applies to osmdroid ({@code MapConfig}, {@code MapMarkerController}) and
 * to the map hand-off ({@code NavigationLauncher}): one class knows how the outside world is configured,
 * and everything else asks it a question. When Phase 4.2 adds the call itself, it will read this class
 * rather than reaching for the build constant.
 *
 * <p><b>The facts below were checked against the live service on 2026-09-25, not taken from a tutorial:</b>
 *
 * <table>
 *   <tr><td>POST {@code https://api.openrouteservice.org/v2/directions/driving-car/geojson}</td>
 *       <td>with no key at all returns <b>401</b> and {@code {"error":"Authorization field missing"}}</td></tr>
 *   <tr><td>the same request with a bad key</td>
 *       <td>returns <b>403</b> and {@code {"error":"Access to this API has been disallowed"}}</td></tr>
 * </table>
 *
 * <p>Two things are worth taking from that. First, the endpoint path is right - a wrong path would have
 * answered 404 rather than 401. Second, <b>the key travels in the {@code Authorization} header, not as a
 * query parameter.</b> Older versions of this API accepted {@code ?api_key=...}, and plenty of examples
 * online still show it; the error above says plainly which one the service is actually looking for.
 */
public final class RoutingConfig {

    /** Utility class - never instantiated. */
    private RoutingConfig() {
    }

    /**
     * The service's base URL.
     *
     * <p><b>HTTPS, and it must stay that way.</b> The API key is sent with every request, so a plain
     * {@code http://} URL would put it on the wire in clear text for anyone on the same network to read.
     * {@code RoutingConfigTest} fails if this ever stops starting with {@code https://}.
     */
    public static final String BASE_URL = "https://api.openrouteservice.org/";

    /**
     * The profile used for every request.
     *
     * <p>The app exists to drive between shops, so there is exactly one profile and it is not a setting.
     * openrouteservice also offers cycling, walking, hiking and wheelchair profiles; none of them
     * describes what this app does.
     */
    public static final String PROFILE_DRIVING_CAR = "driving-car";

    /**
     * The header the key goes in.
     *
     * <p>A constant rather than a literal at the call site, because getting it wrong produces a 401 that
     * looks exactly like an expired key. See the class note for how this was verified.
     */
    public static final String AUTHORIZATION_HEADER = "Authorization";

    /**
     * The attribution the service asks to be shown wherever its routes appear.
     *
     * <p>The exact string the service returns in {@code metadata.attribution}, kept here rather than in
     * {@code strings.xml} for the same reason the OpenStreetMap attribution lives in {@code MapConfig}:
     * it is provider metadata that has to match the service it describes, so the two are written next to
     * each other and cannot drift apart. It is the one deliberate exception to this project's "all
     * user-visible text lives in strings.xml" rule, and it is the same exception, for the same reason.
     */
    private static final String ATTRIBUTION =
            "openrouteservice.org | OpenStreetMap contributors";

    /** The attribution text to show beside any route this app draws. */
    public static String attribution() {
        return ATTRIBUTION;
    }

    /**
     * The directions endpoint, which returns the route as GeoJSON.
     *
     * <p>GeoJSON rather than the encoded-polyline format because it is the one that is readable without a
     * decoder: the coordinates arrive as a plain array of {@code [longitude, latitude]} pairs, which is
     * what the map needs to draw the line, and the distance and duration sit beside them.
     *
     * <p>Note the coordinate order if you are reading the response: <b>longitude first</b>, which is the
     * opposite of how a coordinate is usually spoken aloud. Getting that backwards puts every route in
     * the sea somewhere off the coast of Africa.
     */
    public static String directionsUrl() {
        return BASE_URL + "v2/directions/" + PROFILE_DRIVING_CAR + "/geojson";
    }

    /**
     * The matrix endpoint, which returns real driving distances and durations between many points at once.
     *
     * <p><b>Phase 8.2.</b> The suggestion needs to know which store is genuinely closest to drive to. Asking
     * the directions endpoint once per store would be one request per candidate - twenty requests to answer
     * one question - and the free plan allows 40 a minute. The matrix endpoint answers the whole question in
     * a single request: one origin, every destination, one distances array.
     *
     * <p><b>The limits were re-verified on 2026-09-27</b>, which the roadmap asks for before building
     * anything on a service:
     *
     * <table>
     *   <tr><td>directions</td><td>2,000 requests/day</td></tr>
     *   <tr><td>matrix</td><td>500 requests/day</td></tr>
     *   <tr><td>optimization</td><td>500 requests/day, up to 50 jobs, up to 3 vehicles</td></tr>
     *   <tr><td>rate</td><td>40 requests/minute on most endpoints</td></tr>
     * </table>
     *
     * <p>500 matrix requests a day is far more than a solo user can consume - the suggestion is recomputed
     * only when the position or the store list changes.
     */
    public static String matrixUrl() {
        return BASE_URL + "v2/matrix/" + PROFILE_DRIVING_CAR;
    }

    /**
     * The optimization endpoint - the VROOM engine, which solves the ordering problem.
     *
     * <p><b>Phase 8.3.</b> This is the only part of the app that answers "in what order should I visit
     * these", as opposed to "which one next". It is a genuine solver: the heuristic in
     * {@code HomeAwareStrategy} looks one store ahead and cannot reorder a route, while this examines the
     * whole set.
     *
     * <p><b>The service's limits are the app's limits.</b> {@link #MAX_OPTIMIZATION_JOBS} is a hard cap on
     * the service's side, and a request over it is refused - so the app must check before sending rather
     * than discovering it from a 400. The check lives in {@code OptimizationRequestBuilder}, which is pure
     * Java and tested, rather than at the call site.
     *
     * <p><b>No {@code v2} in this path, and that is not an oversight - it is the bug that made every
     * optimised route fail.</b> Directions and matrix both live under {@code /v2/}, so
     * {@code /v2/optimization} looks like the consistent guess, and the class note above about "a wrong
     * path answers 404 rather than 401" was written from the directions endpoint without being re-checked
     * here. Verified against the live service on <b>2026-09-27</b>:
     *
     * <table>
     *   <tr><td>POST {@code /v2/optimization}</td><td><b>404</b> - plain nginx "Not Found", not a JSON
     *       error. The key was never even looked at</td></tr>
     *   <tr><td>POST {@code /optimization}</td><td><b>200</b> and a solved route</td></tr>
     * </table>
     *
     * <p>A 404 is not an exception - it is an ordinary response code, so the repository read it as "the
     * service answered HTTP 404" and reported a failed request. The screen said "Could not build a route",
     * which was true and completely unhelpful. {@code RoutingConfigTest} now pins this path so the next
     * person cannot re-introduce it by pattern-matching on the other two endpoints.
     */
    public static String optimizationUrl() {
        return BASE_URL + "optimization";
    }

    /** How many stops one optimization request may contain. A service limit, not a preference. */
    public static final int MAX_OPTIMIZATION_JOBS = 50;

    /**
     * How many vehicles the app ever asks for.
     *
     * <p>One. This is a solo app with one driver and one car (D-5); the service allows up to three, and
     * asking for more would produce a plan the user cannot carry out.
     */
    public static final int OPTIMIZATION_VEHICLES = 1;

    /**
     * The API key for this build, trimmed, or an empty string when none was configured.
     *
     * <p><b>Never {@code null}.</b> Callers get a string they can put straight into a header, and
     * {@link #isConfigured()} is the question to ask before they do.
     *
     * <p>The trim is not tidiness. A key pasted from a browser very often arrives with a trailing
     * newline, and a key with a newline in it is rejected by the service while looking perfectly correct
     * in the editor.
     */
    public static String apiKey() {
        return normalise(BuildConfig.ORS_API_KEY);
    }

    /**
     * Whether this build has a usable key.
     *
     * <p>The app must build, install and run with no key at all - that is the state it is in until the
     * account is created, and the state a fresh checkout would be in. So a missing key is not an error
     * here; it is an answer, and the screen that needs routing is expected to say "not set up yet"
     * rather than to send a request that comes back 401.
     */
    public static boolean isConfigured() {
        return isUsable(BuildConfig.ORS_API_KEY);
    }

    /**
     * Whether a key value is usable - package-private so {@code RoutingConfigTest} can check every case
     * without depending on whatever key happens to be configured on the machine running the tests.
     */
    static boolean isUsable(@Nullable String key) {
        return !normalise(key).isEmpty();
    }

    /**
     * Trims a key and turns {@code null} into an empty string.
     *
     * <p>One definition of "the key we would actually send", used by both {@link #apiKey()} and
     * {@link #isUsable}, so the two can never disagree about whether a given value is a key.
     */
    static String normalise(@Nullable String key) {
        return key == null ? "" : key.trim();
    }
}

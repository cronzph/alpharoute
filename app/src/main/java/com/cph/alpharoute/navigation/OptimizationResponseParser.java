package com.cph.alpharoute.navigation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cph.alpharoute.domain.OptimizationPlan;
import com.cph.alpharoute.domain.OptimizationStop;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns the optimization service's answer into an {@link OptimizationPlan}.
 *
 * <p><b>Pure apart from {@code org.json} (AD-11)</b>, so every shape the service can return is asserted in
 * {@code OptimizationResponseParserTest} rather than discovered on a drive.
 *
 * <p><b>The answer is a list of steps, and only the {@code "job"} steps are stops.</b> A route begins with
 * a {@code "start"} step and ends with an {@code "end"} step - both of which carry a location and a
 * distance but are not stores. Treating them as stops would put a phantom shop at the beginning and end of
 * every route, and the count would be wrong by two.
 *
 * <p><b>The job id is the store id.</b> {@code OptimizationRequestBuilder} sends it that way, so no lookup
 * table is needed here and none can be wrong.
 */
public final class OptimizationResponseParser {

    /** The step type that represents a store. Everything else is the start, the end, or a break. */
    private static final String STEP_TYPE_JOB = "job";

    /** Utility class - never instantiated. */
    private OptimizationResponseParser() {
    }

    /**
     * Parses a successful response.
     *
     * @param body the response body
     * @param startsAtCurrentLocation whether the request had a start point, carried through so the plan
     *                                can say so without the caller re-deriving it
     * @param endsAtHome              whether the request had Home as its finish
     * @return the plan, or {@code null} when the body is not a usable answer. {@code null} rather than an
     *         exception because a malformed answer is a service problem the screen reports, not a bug that
     *         should crash it
     */
    @Nullable
    public static OptimizationPlan parse(@Nullable String body,
                                         boolean startsAtCurrentLocation,
                                         boolean endsAtHome) {
        if (body == null || body.trim().isEmpty()) {
            return null;
        }

        try {
            JSONObject root = new JSONObject(body);

            // VROOM reports its own failures with a non-zero code and an error message inside a 200 body.
            // Checking the status line alone would miss it.
            if (root.has("code") && root.getInt("code") != 0) {
                return null;
            }

            JSONArray routes = root.optJSONArray("routes");
            if (routes == null || routes.length() == 0) {
                return null;
            }

            JSONObject route = routes.getJSONObject(0);
            JSONArray steps = route.optJSONArray("steps");
            if (steps == null) {
                return null;
            }

            List<OptimizationStop> stops = new ArrayList<>();
            int sequence = 0;

            for (int i = 0; i < steps.length(); i++) {
                JSONObject step = steps.optJSONObject(i);
                if (step == null) {
                    continue;
                }
                // Only jobs are stores. The start and end steps carry locations and distances too, and
                // counting them would add two phantom stops to every route.
                if (!STEP_TYPE_JOB.equals(step.optString("type"))) {
                    continue;
                }

                sequence++;
                stops.add(new OptimizationStop(
                        sequence,
                        step.optLong("id", -1L),
                        step.optLong("arrival", 0L),
                        step.optDouble("distance", 0.0),
                        step.optLong("duration", 0L)));
            }

            // The totals.
            //
            // VROOM puts them in a "summary" at the TOP LEVEL of the response, not inside the route - which
            // is where this parser originally looked, so both totals were always zero and the screen's
            // summary line read "0 m". A route also carries its own distance and duration, and an older
            // shape nests a summary inside the route, so all three are tried in order.
            JSONObject summary = summaryOf(root, route);
            double totalDistance = hasNumber(summary, "distance")
                    ? summary.optDouble("distance", 0.0)
                    : route.optDouble("distance", 0.0);
            long totalDuration = hasNumber(summary, "duration")
                    ? summary.optLong("duration", 0L)
                    : route.optLong("duration", 0L);

            // Stores the solver could not place. Counted from both places it can appear: the route's own
            // list and the top-level one, because a request that fails validation puts them at the top.
            int unassigned = 0;
            JSONArray routeUnassigned = route.optJSONArray("unassigned");
            if (routeUnassigned != null) {
                unassigned += routeUnassigned.length();
            }
            JSONArray rootUnassigned = root.optJSONArray("unassigned");
            if (rootUnassigned != null) {
                unassigned += rootUnassigned.length();
            }

            return new OptimizationPlan(
                    stops,
                    totalDistance,
                    totalDuration,
                    startsAtCurrentLocation,
                    endsAtHome,
                    unassigned);

        } catch (JSONException e) {
            // A body that is not the shape the service documents. Reported as "no plan" rather than thrown:
            // the screen says the service could not be used and offers Retry.
            return null;
        }
    }

    /**
     * The summary block, from wherever this response keeps it.
     *
     * <p>The top level wins, because that is where VROOM actually puts it. The route's own copy is kept as
     * a fallback so a response in either shape reads the same - the alternative was to trust one shape and
     * silently report zero for the other, which is exactly the bug this replaced.
     */
    @Nullable
    private static JSONObject summaryOf(@NonNull JSONObject root, @NonNull JSONObject route) {
        JSONObject rootSummary = root.optJSONObject("summary");
        return rootSummary != null ? rootSummary : route.optJSONObject("summary");
    }

    /**
     * Whether a summary carries a field as a real number.
     *
     * <p>Not the same question as {@code has()}. The service omits {@code distance} altogether unless
     * geometry was requested - so a summary that exists may still have no distance in it, and treating
     * "absent" as "zero" is how a route ends up claiming it is 0 m long.
     */
    private static boolean hasNumber(@Nullable JSONObject summary, @NonNull String field) {
        return summary != null && summary.has(field) && !summary.isNull(field);
    }
}

package com.cph.alpharoute.navigation;

import androidx.annotation.Nullable;

import com.cph.alpharoute.domain.RoutePlan;
import com.cph.alpharoute.domain.GeoCoordinate;
import com.cph.alpharoute.util.GeoConstants;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns an openrouteservice directions response into a {@link RoutePlan}.
 *
 * <p>Pure Java with no Android imports (AD-11). It does use {@code org.json}, which Android provides on
 * the platform - so the app needs no dependency for it - and which is why a <b>test-only</b> copy is on
 * the test classpath: the platform's version is a stub on the JVM where every method throws
 * "not mocked", and without the real one this class could not be tested at all.
 *
 * <p><b>The response's real shape, read from the live service on 2026-09-25:</b>
 *
 * <pre>
 * {
 *   "type": "FeatureCollection",
 *   "features": [ {
 *       "type": "Feature",
 *       "geometry":   { "type": "LineString", "coordinates": [ [120.984206, 14.599528], ... ] },
 *       "properties": { "summary": { "distance": 1733.6, "duration": 298.4 },
 *                       "segments": [ { "distance": ..., "duration": ..., "steps": [...] } ] }
 *   } ],
 *   "metadata": { "attribution": "openrouteservice.org | OpenStreetMap contributors", ... }
 * }
 * </pre>
 *
 * <p><b>Only two things are read out of that: the summary and the line.</b> The summary is used rather
 * than {@code segments[0]} because it is the total for the whole route, which is what the screen shows;
 * the segments are per-leg and would need summing. The {@code steps} are ignored - turn-by-turn is the
 * Google Maps app's job under D-7, and rendering it here would be a second, worse set of directions.
 *
 * <p><b>Every coordinate pair arrives as {@code [longitude, latitude]}</b> - the opposite of how a
 * coordinate is spoken aloud. Getting that backwards puts the entire route in the sea off the coast of
 * Africa, and nothing fails: the map simply draws a line in the wrong place. This is the one place the
 * swap happens.
 *
 * <p><b>Anything unexpected returns {@code null}, and the caller shows an error.</b> There is
 * deliberately no "best effort" parse. A half-read route would be drawn as a line that looks plausible
 * and is wrong, and a driver following it is worse off than one who is told the route is unavailable.
 * That is the same reasoning as "we never fake a route" in the screen plan.
 */
public final class RouteResponseParser {

    /** Utility class - never instantiated. */
    private RouteResponseParser() {
    }

    /**
     * Parses a directions response.
     *
     * @param json the response body, possibly {@code null} or malformed
     * @return the route, or {@code null} when the body is not a usable route
     */
    @Nullable
    public static RoutePlan parse(@Nullable String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }

        try {
            JSONArray features = new JSONObject(json).optJSONArray("features");
            if (features == null || features.length() == 0) {
                return null;
            }

            JSONObject feature = features.optJSONObject(0);
            if (feature == null) {
                return null;
            }

            List<GeoCoordinate> points = parsePoints(feature);
            if (points == null) {
                return null;
            }

            return parseSummary(feature, points);
        } catch (JSONException e) {
            // Not JSON at all, or truncated. Either way there is no route here.
            return null;
        }
    }

    /**
     * The line, or {@code null} when it is missing or contains a coordinate that is not a real place.
     *
     * <p>A single bad pair rejects the whole route rather than being skipped. Skipping would leave a line
     * that joins two points which are not actually joined, and the map would draw a straight cut across
     * whatever lies between them - a wrong route that looks like a right one.
     */
    @Nullable
    private static List<GeoCoordinate> parsePoints(JSONObject feature) {
        JSONObject geometry = feature.optJSONObject("geometry");
        JSONArray coordinates = geometry == null ? null : geometry.optJSONArray("coordinates");

        // Two points is the minimum that can be drawn as a line at all.
        if (coordinates == null || coordinates.length() < 2) {
            return null;
        }

        List<GeoCoordinate> points = new ArrayList<>(coordinates.length());

        for (int i = 0; i < coordinates.length(); i++) {
            JSONArray pair = coordinates.optJSONArray(i);
            if (pair == null || pair.length() < 2) {
                return null;
            }

            // GeoJSON order: longitude first. See the class note.
            double longitude = pair.optDouble(0, Double.NaN);
            double latitude = pair.optDouble(1, Double.NaN);

            if (!GeoConstants.isValidLatitude(latitude)
                    || !GeoConstants.isValidLongitude(longitude)) {
                return null;
            }

            points.add(new GeoCoordinate(latitude, longitude));
        }

        return points;
    }

    /** The distance and duration, or {@code null} when the summary is missing or not a number. */
    @Nullable
    private static RoutePlan parseSummary(JSONObject feature, List<GeoCoordinate> points) {
        JSONObject properties = feature.optJSONObject("properties");
        JSONObject summary = properties == null ? null : properties.optJSONObject("summary");
        if (summary == null) {
            return null;
        }

        double distance = summary.optDouble("distance", Double.NaN);
        double duration = summary.optDouble("duration", Double.NaN);
        if (Double.isNaN(distance) || Double.isNaN(duration)) {
            return null;
        }

        // Rounded to whole metres and whole seconds: the service returns 1733.6 and 298.4, and sub-metre
        // precision means nothing for a drive.
        return new RoutePlan(
                (int) Math.round(distance),
                (int) Math.round(duration),
                points);
    }
}

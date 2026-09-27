package com.cph.alpharoute.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A route: how far it is, how long it takes, and the line to draw.
 *
 * <p>Pure Java with no Android imports (AD-11), so the shape of a route is testable without a map.
 *
 * <p><b>What this deliberately does not contain: anything about the destination.</b> It is the answer to
 * "what does the drive look like", not "where am I going" - the caller already knows the store. Keeping
 * the two apart means a route can be parsed, tested and drawn without a store existing at all.
 *
 * <p><b>Distance and duration are whole numbers.</b> The service returns them as decimals
 * ({@code 1733.6} metres), and the parser rounds them. Sub-metre precision is meaningless for a drive,
 * and an {@code int} means a caller cannot accidentally format "1733.6000000000001 m" onto a screen.
 */
public final class RoutePlan {

    private final int distanceMetres;
    private final int durationSeconds;
    private final List<GeoCoordinate> points;

    /**
     * @param points the line, in order, at least two points - a route with fewer cannot be drawn
     * @throws IllegalArgumentException if fewer than two points are given. That is not a user error:
     *         the parser refuses such a response before it gets here, so this is a guard against a
     *         future caller building a route by hand and getting it wrong.
     */
    public RoutePlan(int distanceMetres, int durationSeconds, List<GeoCoordinate> points) {
        if (points == null || points.size() < 2) {
            throw new IllegalArgumentException("A route needs at least two points to be drawn");
        }
        this.distanceMetres = distanceMetres;
        this.durationSeconds = durationSeconds;

        // Copied and wrapped rather than stored as given: the caller keeps no handle on this list, so a
        // route cannot change under a screen that is drawing it.
        this.points = Collections.unmodifiableList(new ArrayList<>(points));
    }

    public int distanceMetres() {
        return distanceMetres;
    }

    public int durationSeconds() {
        return durationSeconds;
    }

    /** The line, in order, as an unmodifiable list. */
    public List<GeoCoordinate> points() {
        return points;
    }

    @Override
    public String toString() {
        return "RoutePlan{" + distanceMetres + " m, " + durationSeconds + " s, "
                + points.size() + " points}";
    }
}

package com.cph.alpharoute.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Where a pasted link has got to.
 *
 * <p>The same shape as {@code RouteState}: a status and, when there is one, the thing that was found -
 * held in one object so a screen cannot show a success message with nothing behind it, or a spinner over a
 * result that already arrived.
 *
 * <p>Pure Java with no Android imports (AD-11).
 */
public final class PlaceLinkState {

    /** What a paste is doing, or why it did not produce a location. */
    public enum Status {

        /** Nothing has been pasted yet. */
        IDLE,

        /** The link is being followed. Only a short link ever reaches this. */
        RESOLVING,

        /** A location was read. The only status that carries a coordinate. */
        FOUND,

        /** The text is not a Google Maps link and holds no coordinates. */
        NOT_A_LINK,

        /** It <i>is</i> a short link, but following it produced no readable location. */
        UNRESOLVED,

        /** The short link could not be followed at all - no connection, or the request failed. */
        OFFLINE,

        /** Something else went wrong. */
        FAILED
    }

    private final Status status;

    @Nullable
    private final GeoCoordinate coordinate;

    private PlaceLinkState(@NonNull Status status, @Nullable GeoCoordinate coordinate) {
        this.status = status;
        this.coordinate = coordinate;
    }

    public static PlaceLinkState idle() {
        return new PlaceLinkState(Status.IDLE, null);
    }

    public static PlaceLinkState resolving() {
        return new PlaceLinkState(Status.RESOLVING, null);
    }

    /** A location was read. The only way to reach {@link Status#FOUND}. */
    public static PlaceLinkState found(@NonNull GeoCoordinate coordinate) {
        return new PlaceLinkState(Status.FOUND, coordinate);
    }

    /**
     * A paste that produced no location.
     *
     * @param status one of the failure statuses. Passing {@code IDLE}, {@code RESOLVING} or {@code FOUND}
     *               throws, because a caller that did so has confused a state with a failure and would
     *               otherwise get a screen reporting an error with no message.
     */
    public static PlaceLinkState failed(@NonNull Status status) {
        if (status == Status.IDLE || status == Status.RESOLVING || status == Status.FOUND) {
            throw new IllegalArgumentException("Not a failure status: " + status);
        }
        return new PlaceLinkState(status, null);
    }

    @NonNull
    public Status status() {
        return status;
    }

    /** The location, or {@code null} for every status except {@link Status#FOUND}. */
    @Nullable
    public GeoCoordinate coordinate() {
        return coordinate;
    }

    public boolean isFound() {
        return status == Status.FOUND;
    }

    public boolean isResolving() {
        return status == Status.RESOLVING;
    }

    @Override
    public String toString() {
        return "PlaceLinkState{" + status + (coordinate == null ? "" : ", " + coordinate) + '}';
    }
}

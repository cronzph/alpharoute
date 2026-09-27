package com.cph.alpharoute.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Everything a suggestion strategy is allowed to look at.
 *
 * <p><b>One object, so a strategy cannot reach for anything else.</b> A strategy receives this and
 * returns a {@link SuggestionResult} - it has no database, no clock and no location provider. That is what
 * makes every strategy testable on the JVM with no device (AD-11), and it is why the interface can be
 * swapped (AD-12) without any of them knowing about the others.
 *
 * <p><b>Only the stores that are still to visit in the current round.</b> The caller does the filtering,
 * because "pending" is a round rule and it belongs with the rest of the round logic - not repeated inside
 * each strategy where the two could drift apart.
 *
 * <p>Immutable. Pure Java.
 */
public final class SuggestionContext {

    private final List<StoreCandidate> pending;
    private final int totalStores;
    @Nullable
    private final GeoCoordinate currentLocation;
    @Nullable
    private final GeoCoordinate home;
    @Nullable
    private final Map<Long, Double> roadDistances;

    /**
     * @param pending         the stores still to visit in this round. Empty is a normal state, not an error
     * @param totalStores     how many stores exist in total, including the completed ones. This is what
     *                        separates "the round is finished" from "there are no stores at all" - the
     *                        pending list alone cannot tell those apart, and they need different messages
     * @param currentLocation where the user is, or {@code null} when there is no usable fix
     * @param home            the configured Home, or {@code null} when it has not been set
     */
    public SuggestionContext(@Nullable List<StoreCandidate> pending,
                             int totalStores,
                             @Nullable GeoCoordinate currentLocation,
                             @Nullable GeoCoordinate home) {
        this(pending, totalStores, currentLocation, home, null);
    }

    /**
     * The full constructor, with real driving distances.
     *
     * @param roadDistances store id → driving distance in metres, from the matrix service. {@code null} or
     *                      empty when they have not been fetched or could not be - which is the ordinary
     *                      state offline, and the reason {@link RoadDistanceStrategy} has a fallback
     */
    public SuggestionContext(@Nullable List<StoreCandidate> pending,
                             int totalStores,
                             @Nullable GeoCoordinate currentLocation,
                             @Nullable GeoCoordinate home,
                             @Nullable Map<Long, Double> roadDistances) {
        this.pending = pending == null ? Collections.emptyList() : pending;
        this.totalStores = Math.max(0, totalStores);
        this.currentLocation = currentLocation;
        this.home = home;
        this.roadDistances = roadDistances == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(roadDistances);
    }

    /** The stores still to visit in the round in progress, in no particular order. */
    @NonNull
    public List<StoreCandidate> pending() {
        return pending;
    }

    /** How many stores exist in total, completed or not. */
    public int totalStores() {
        return totalStores;
    }

    /**
     * Real driving distances, by store id.
     *
     * <p>Empty when they have not been fetched. A strategy that uses them must therefore have an answer for
     * the empty case - the app works offline, and a suggestion that only existed when the network did would
     * be worse than the straight-line one it replaced.
     */
    @NonNull
    public Map<Long, Double> roadDistances() {
        return roadDistances;
    }

    /** Whether real driving distances are available. */
    public boolean hasRoadDistances() {
        return !roadDistances.isEmpty();
    }

    /** Where the user is, or {@code null} when there is no usable fix. */
    @Nullable
    public GeoCoordinate currentLocation() {
        return currentLocation;
    }

    /** The configured Home, or {@code null} when it has not been set. */
    @Nullable
    public GeoCoordinate home() {
        return home;
    }

    /** Whether there is a position to measure from. */
    public boolean hasLocation() {
        return currentLocation != null;
    }

    /** Whether Home has been configured. */
    public boolean hasHome() {
        return home != null;
    }

    /** Whether anything is left to visit in this round. */
    public boolean hasPending() {
        return !pending.isEmpty();
    }
}

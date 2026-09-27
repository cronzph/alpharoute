package com.cph.alpharoute.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * What a suggestion strategy decided.
 *
 * <p><b>A status, not a store-or-null.</b> "No suggestion" has three completely different causes, and the
 * card has to say which one: there is nothing left to visit (a <i>success</i>), there are no stores at all
 * (an empty app), or a strategy declined to choose. Collapsing them into {@code null} would make the card
 * show the same message for "well done" and "something is wrong".
 *
 * <p>Immutable. Pure Java.
 */
public final class SuggestionResult {

    /** What the strategy concluded. */
    public enum Status {

        /** A store was chosen. */
        SUGGESTED,

        /**
         * Nothing is left to visit in this round - the round is complete.
         *
         * <p>Not a failure. The card offers Home instead, because Home is where the route finishes.
         */
        ROUTE_COMPLETE,

        /** There are no stores at all, so there is nothing to suggest. */
        NO_STORES
    }

    /**
     * Why this store was chosen, so the card can say so rather than presenting a guess as a fact.
     *
     * <p><b>The honesty of the suggestion depends on this.</b> "Nearest to you" and "picked without your
     * location" are very different claims, and a card that showed the same words for both would be
     * overstating what the app knows.
     */
    public enum Reason {

        /** Nearest to the user's current position. */
        NEAREST,

        /**
         * Nearest by real driving distance, from the routing service.
         *
         * <p>A different claim from {@link #NEAREST}: the number is a road distance, not a straight line,
         * so a shop across a river is measured the way it would actually be driven.
         */
        ROAD_DISTANCE,

        /** Best balance of close to you and close to home. */
        HOME_AWARE,

        /** Chosen without a position - measured from Home instead. */
        FROM_HOME,

        /** No position and no Home, so the first pending store was taken. */
        NO_REFERENCE,

        /** The only store left, so no choice was needed. */
        ONLY_ONE_LEFT,

        /** Nothing is pending. */
        ROUTE_COMPLETE,

        /** No stores exist. */
        NO_STORES
    }

    private final Status status;
    private final Reason reason;
    @Nullable
    private final StoreCandidate store;
    private final double distanceMetres;

    private SuggestionResult(Status status,
                             Reason reason,
                             @Nullable StoreCandidate store,
                             double distanceMetres) {
        this.status = status;
        this.reason = reason;
        this.store = store;
        this.distanceMetres = distanceMetres;
    }

    /** A chosen store. */
    @NonNull
    public static SuggestionResult suggested(@NonNull StoreCandidate store,
                                             @NonNull Reason reason,
                                             double distanceMetres) {
        return new SuggestionResult(Status.SUGGESTED, reason, store, distanceMetres);
    }

    /** Nothing left to visit. */
    @NonNull
    public static SuggestionResult routeComplete() {
        return new SuggestionResult(Status.ROUTE_COMPLETE, Reason.ROUTE_COMPLETE, null, Double.NaN);
    }

    /** No stores at all. */
    @NonNull
    public static SuggestionResult noStores() {
        return new SuggestionResult(Status.NO_STORES, Reason.NO_STORES, null, Double.NaN);
    }

    @NonNull
    public Status status() {
        return status;
    }

    @NonNull
    public Reason reason() {
        return reason;
    }

    /** The chosen store, or {@code null} when there is nothing to suggest. */
    @Nullable
    public StoreCandidate store() {
        return store;
    }

    /**
     * How far away the suggested store is, in metres.
     *
     * <p>{@link Double#NaN} when it could not be measured - a suggestion made without a position has no
     * distance to report, and a {@code 0} would be a claim that the user is standing in the store.
     */
    public double distanceMetres() {
        return distanceMetres;
    }

    /** Whether a distance can be shown. */
    public boolean hasDistance() {
        return !Double.isNaN(distanceMetres);
    }

    /** Whether there is a store to act on. */
    public boolean hasStore() {
        return status == Status.SUGGESTED && store != null;
    }

    @NonNull
    @Override
    public String toString() {
        return "SuggestionResult(" + status + ", " + reason
                + (store == null ? "" : ", " + store.storeCode()) + ")";
    }
}

package com.cph.alpharoute.domain;

import androidx.annotation.NonNull;

/**
 * A store as the suggestion strategies see it.
 *
 * <p><b>Why a value object rather than the Room entity.</b> {@code domain/} has zero
 * {@code androidx.room} imports (AD-11), so the strategies cannot take a {@code StoreEntity}. The
 * ViewModel builds these from the entities, which keeps the mapping in the layer that is allowed to know
 * about Room and keeps the strategies testable on a laptop.
 *
 * <p><b>Five fields, and that is the whole list.</b> A strategy needs to know which store it is, what to
 * call it, and where it is - and nothing else. A status or a completion timestamp here would invite a
 * strategy to make a decision that belongs to the round rules.
 *
 * <p>Immutable. Pure Java.
 */
public final class StoreCandidate {

    private final long id;
    private final String storeCode;
    private final String storeName;
    private final double latitude;
    private final double longitude;

    public StoreCandidate(long id,
                          @NonNull String storeCode,
                          @NonNull String storeName,
                          double latitude,
                          double longitude) {
        this.id = id;
        this.storeCode = storeCode;
        this.storeName = storeName;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public long id() {
        return id;
    }

    @NonNull
    public String storeCode() {
        return storeCode;
    }

    @NonNull
    public String storeName() {
        return storeName;
    }

    public double latitude() {
        return latitude;
    }

    public double longitude() {
        return longitude;
    }

    /** This store's position, for the distance calculations. */
    @NonNull
    public GeoCoordinate coordinate() {
        return new GeoCoordinate(latitude, longitude);
    }

    /** {@code "R-01 — Alfamart Sampaloc"}, for the suggestion card. */
    @NonNull
    public String label() {
        return storeCode + " — " + storeName;
    }

    @NonNull
    @Override
    public String toString() {
        return "StoreCandidate(" + id + ", " + storeCode + ")";
    }
}

package com.cph.alpharoute.domain;

/**
 * The lifecycle state of a store.
 *
 * <p>Stored in the database as the enum's name, via
 * {@link com.cph.alpharoute.data.db.Converters}.
 *
 * <p>These are the only two values the specification allows. Home is deliberately NOT a status and
 * NOT a store - it lives in {@link com.cph.alpharoute.data.db.AppSettingEntity} and never affects any
 * count.
 */
public enum StoreStatus {

    /** Not yet visited. Drawn as an orange marker. */
    PENDING,

    /** Visited and confirmed by the user. Drawn as a green marker. Never counted twice. */
    COMPLETED
}

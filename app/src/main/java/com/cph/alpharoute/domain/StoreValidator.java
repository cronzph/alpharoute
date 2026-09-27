package com.cph.alpharoute.domain;

import com.cph.alpharoute.util.GeoConstants;

/**
 * The rules a store must satisfy before it can be saved.
 *
 * <p><b>Pure Java, no Android imports (AD-11).</b> Nothing here needs a {@code Context}, a database or
 * a device, so every rule below is exercised by {@code StoreValidatorTest} on the JVM in milliseconds.
 * For an app whose whole value is the correctness of its records, being able to test the rules without
 * an emulator is worth more than it sounds.
 *
 * <p><b>What this class deliberately does NOT check:</b> whether a store code or route number is
 * already taken. That needs the database, which is asynchronous, so it is reported separately by
 * {@code StoreRepository} as {@code WriteOutcome.DUPLICATE_STORE_CODE} or
 * {@code DUPLICATE_ROUTE_NUMBER}. Validation here is the instant, synchronous half of the check; the
 * database is the authoritative half. The screen runs both, in that order, so a blank field is
 * reported without a database round trip and a genuine duplicate is still caught.
 *
 * <p><b>Trimming.</b> {@link #trimToEmpty(String)} is the single definition of "trim" in this app. The
 * validator applies it before testing, and {@code StoreRepository} applies it before writing, so
 * stored text is always trimmed no matter which path the data arrived by.
 */
public final class StoreValidator {

    /** Utility class - never instantiated. */
    private StoreValidator() {
    }

    /** Longest accepted route number. */
    public static final int MAX_ROUTE_NUMBER_LENGTH = 20;

    /** Longest accepted store code. */
    public static final int MAX_STORE_CODE_LENGTH = 30;

    /** Longest accepted store name. */
    public static final int MAX_STORE_NAME_LENGTH = 100;

    /** Longest accepted area, per the Phase 0.1 assumption A3. */
    public static final int MAX_AREA_LENGTH = 60;

    /**
     * The one and only definition of "trimmed" in this app.
     *
     * <p>Null-safe on purpose: a {@code null} from an empty {@code EditText} or a partially filled
     * form becomes {@code ""}, so callers never have to null-check before calling it.
     */
    public static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * Validates a store's fields.
     *
     * <p>Inputs may be raw - they are trimmed here before being measured, so
     * {@code "  SM-001  "} is accepted and counts as six characters, not ten.
     *
     * @return a result with one error slot per field; {@link StoreValidationResult#isValid()} is true
     *         when every field passed
     */
    public static StoreValidationResult validate(String routeNumber,
                                                 String storeCode,
                                                 String storeName,
                                                 String area,
                                                 double latitude,
                                                 double longitude) {
        StoreValidationResult result = StoreValidationResult.valid();

        result.routeNumberError = checkRequiredText(routeNumber, MAX_ROUTE_NUMBER_LENGTH);
        result.storeCodeError = checkRequiredText(storeCode, MAX_STORE_CODE_LENGTH);
        result.storeNameError = checkRequiredText(storeName, MAX_STORE_NAME_LENGTH);
        result.areaError = checkRequiredText(area, MAX_AREA_LENGTH);

        // Exact coordinate equality is never required anywhere in this app, but a coordinate outside
        // the globe is not a GPS inaccuracy - it is a broken value, and storing it would put a marker
        // somewhere impossible.
        boolean coordinatesUsable = GeoConstants.isValidLatitude(latitude)
                && GeoConstants.isValidLongitude(longitude);
        result.coordinatesError = coordinatesUsable ? null : StoreFieldError.INVALID_COORDINATES;

        return result;
    }

    /** Shared rule for the four text fields: not blank, and not too long. */
    private static StoreFieldError checkRequiredText(String rawValue, int maxLength) {
        String trimmed = trimToEmpty(rawValue);

        if (trimmed.isEmpty()) {
            return StoreFieldError.REQUIRED;
        }
        if (trimmed.length() > maxLength) {
            return StoreFieldError.TOO_LONG;
        }
        return null;
    }
}

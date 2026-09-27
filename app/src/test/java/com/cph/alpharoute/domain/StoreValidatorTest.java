package com.cph.alpharoute.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests for {@link StoreValidator}.
 *
 * <p>These run on the JVM - no emulator, no device, no database - because the validator is pure Java
 * with zero Android imports (AD-11). That is the whole point of keeping the rules out of the Fragment:
 * the validation this app depends on can be proven in milliseconds.
 *
 * <p>Phase 10.2 extends this class with the remaining tracker, distance and date-boundary tests. It
 * covers the store rules now because those are what Phase 2.2 delivers.
 */
public class StoreValidatorTest {

    /** A real Metro Manila coordinate, used as the known-good pair. */
    private static final double VALID_LATITUDE = 14.5995;
    private static final double VALID_LONGITUDE = 120.9842;

    private static StoreValidationResult validateWith(String routeNumber, String storeCode,
                                                      String storeName, String area,
                                                      double latitude, double longitude) {
        return StoreValidator.validate(routeNumber, storeCode, storeName, area, latitude, longitude);
    }

    private static StoreValidationResult validateGoodFields() {
        return validateWith("54", "SM-001", "Alfamart Sample", "Sample area",
                VALID_LATITUDE, VALID_LONGITUDE);
    }

    // ---------------------------------------------------------------- the happy path

    @Test
    public void completeInputPasses() {
        StoreValidationResult result = validateGoodFields();

        assertTrue("A fully filled form must be valid", result.isValid());
        assertNull(result.routeNumberError());
        assertNull(result.storeCodeError());
        assertNull(result.storeNameError());
        assertNull(result.areaError());
        assertNull(result.coordinatesError());
    }

    @Test
    public void surroundingWhitespaceIsTrimmedAndAccepted() {
        StoreValidationResult result = validateWith("  54  ", "  SM-001  ", "  Alfamart Sample  ",
                "  Sample area  ", VALID_LATITUDE, VALID_LONGITUDE);

        assertTrue("Padding must not make a field invalid", result.isValid());
    }

    // ---------------------------------------------------------------- blank fields

    @Test
    public void blankRouteNumberIsRejected() {
        StoreValidationResult result = validateWith("", "SM-001", "Alfamart Sample", "Sample area",
                VALID_LATITUDE, VALID_LONGITUDE);

        assertFalse(result.isValid());
        assertEquals(StoreFieldError.REQUIRED, result.routeNumberError());
    }

    @Test
    public void blankOnlyWhitespaceIsRejected() {
        // The rule the specification states explicitly: blank-only values are invalid.
        StoreValidationResult result = validateWith("54", "     ", "Alfamart Sample", "Sample area",
                VALID_LATITUDE, VALID_LONGITUDE);

        assertFalse(result.isValid());
        assertEquals(StoreFieldError.REQUIRED, result.storeCodeError());
    }

    @Test
    public void nullFieldsAreRejectedRatherThanCrashing() {
        StoreValidationResult result = validateWith(null, null, null, null,
                VALID_LATITUDE, VALID_LONGITUDE);

        assertFalse(result.isValid());
        assertEquals(StoreFieldError.REQUIRED, result.routeNumberError());
        assertEquals(StoreFieldError.REQUIRED, result.storeCodeError());
        assertEquals(StoreFieldError.REQUIRED, result.storeNameError());
        assertEquals(StoreFieldError.REQUIRED, result.areaError());
    }

    @Test
    public void everyProblemIsReportedAtOnce() {
        // The screen shows all field errors together, so the user fixes one pass rather than four.
        StoreValidationResult result = validateWith("", "", "", "",
                VALID_LATITUDE, VALID_LONGITUDE);

        assertFalse(result.isValid());
        assertEquals(StoreFieldError.REQUIRED, result.routeNumberError());
        assertEquals(StoreFieldError.REQUIRED, result.storeCodeError());
        assertEquals(StoreFieldError.REQUIRED, result.storeNameError());
        assertEquals(StoreFieldError.REQUIRED, result.areaError());
    }

    // ---------------------------------------------------------------- length limits

    @Test
    public void areaAtTheLimitIsAccepted() {
        String area = repeat('a', StoreValidator.MAX_AREA_LENGTH);

        StoreValidationResult result = validateWith("54", "SM-001", "Alfamart Sample", area,
                VALID_LATITUDE, VALID_LONGITUDE);

        assertTrue("Exactly the maximum length must be allowed", result.isValid());
    }

    @Test
    public void areaOverTheLimitIsRejected() {
        String area = repeat('a', StoreValidator.MAX_AREA_LENGTH + 1);

        StoreValidationResult result = validateWith("54", "SM-001", "Alfamart Sample", area,
                VALID_LATITUDE, VALID_LONGITUDE);

        assertFalse(result.isValid());
        assertEquals(StoreFieldError.TOO_LONG, result.areaError());
    }

    @Test
    public void overlongStoreNameIsRejected() {
        String name = repeat('n', StoreValidator.MAX_STORE_NAME_LENGTH + 1);

        StoreValidationResult result = validateWith("54", "SM-001", name, "Sample area",
                VALID_LATITUDE, VALID_LONGITUDE);

        assertFalse(result.isValid());
        assertEquals(StoreFieldError.TOO_LONG, result.storeNameError());
    }

    @Test
    public void overlongRouteNumberIsRejected() {
        String routeNumber = repeat('9', StoreValidator.MAX_ROUTE_NUMBER_LENGTH + 1);

        StoreValidationResult result = validateWith(routeNumber, "SM-001", "Alfamart Sample",
                "Sample area", VALID_LATITUDE, VALID_LONGITUDE);

        assertFalse(result.isValid());
        assertEquals(StoreFieldError.TOO_LONG, result.routeNumberError());
    }

    @Test
    public void overlongStoreCodeIsRejected() {
        String code = repeat('X', StoreValidator.MAX_STORE_CODE_LENGTH + 1);

        StoreValidationResult result = validateWith("54", code, "Alfamart Sample", "Sample area",
                VALID_LATITUDE, VALID_LONGITUDE);

        assertFalse(result.isValid());
        assertEquals(StoreFieldError.TOO_LONG, result.storeCodeError());
    }

    // ---------------------------------------------------------------- coordinates

    @Test
    public void latitudeAboveTheRangeIsRejected() {
        StoreValidationResult result = validateWith("54", "SM-001", "Alfamart Sample", "Sample area",
                GeoConstantsProbe.LATITUDE_ABOVE, VALID_LONGITUDE);

        assertFalse(result.isValid());
        assertEquals(StoreFieldError.INVALID_COORDINATES, result.coordinatesError());
    }

    @Test
    public void latitudeBelowTheRangeIsRejected() {
        StoreValidationResult result = validateWith("54", "SM-001", "Alfamart Sample", "Sample area",
                GeoConstantsProbe.LATITUDE_BELOW, VALID_LONGITUDE);

        assertFalse(result.isValid());
        assertEquals(StoreFieldError.INVALID_COORDINATES, result.coordinatesError());
    }

    @Test
    public void longitudeAboveTheRangeIsRejected() {
        StoreValidationResult result = validateWith("54", "SM-001", "Alfamart Sample", "Sample area",
                VALID_LATITUDE, GeoConstantsProbe.LONGITUDE_ABOVE);

        assertFalse(result.isValid());
        assertEquals(StoreFieldError.INVALID_COORDINATES, result.coordinatesError());
    }

    @Test
    public void longitudeBelowTheRangeIsRejected() {
        StoreValidationResult result = validateWith("54", "SM-001", "Alfamart Sample", "Sample area",
                VALID_LATITUDE, GeoConstantsProbe.LONGITUDE_BELOW);

        assertFalse(result.isValid());
        assertEquals(StoreFieldError.INVALID_COORDINATES, result.coordinatesError());
    }

    @Test
    public void coordinatesAtTheExactBoundariesAreAccepted() {
        // The poles and the antimeridian are real places, so the bounds are inclusive.
        StoreValidationResult result = validateWith("54", "SM-001", "Alfamart Sample", "Sample area",
                90.0, 180.0);

        assertTrue("The boundary values themselves are valid coordinates", result.isValid());
    }

    // ---------------------------------------------------------------- trimToEmpty

    @Test
    public void trimToEmptyTurnsNullIntoAnEmptyString() {
        assertEquals("", StoreValidator.trimToEmpty(null));
    }

    @Test
    public void trimToEmptyRemovesLeadingAndTrailingWhitespace() {
        assertEquals("SM-001", StoreValidator.trimToEmpty("   SM-001\t\n "));
    }

    @Test
    public void trimToEmptyLeavesInnerWhitespaceAlone() {
        assertEquals("Sample area", StoreValidator.trimToEmpty("  Sample area  "));
    }

    private static String repeat(char character, int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            builder.append(character);
        }
        return builder.toString();
    }

    /**
     * The boundary values, written out here rather than imported, so these tests still mean something
     * if {@code GeoConstants} is ever edited by mistake.
     */
    private static final class GeoConstantsProbe {
        static final double LATITUDE_ABOVE = 90.0001;
        static final double LATITUDE_BELOW = -90.0001;
        static final double LONGITUDE_ABOVE = 180.0001;
        static final double LONGITUDE_BELOW = -180.0001;

        private GeoConstantsProbe() {
        }
    }
}

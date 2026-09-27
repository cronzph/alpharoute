package com.cph.alpharoute.domain;

import com.cph.alpharoute.util.GeoConstants;

/**
 * Turns what the user typed into the arrival radius, or reports that it is not usable.
 *
 * <p>The arrival radius is the distance from a store at which the app will offer to complete it. It is
 * a setting, so it is typed by a person, so it can be typed wrongly - and a wrong radius is not a
 * cosmetic problem: a radius of 0 would mean "you must be standing exactly on the pin", which GPS can
 * never satisfy, and the app would simply never offer to complete anything.
 *
 * <p><b>Why this is its own class rather than a few lines in the Settings fragment.</b> Text in, a
 * number out, is exactly the kind of logic that is easy to get subtly wrong and impossible to test once
 * it is buried in a view callback. Here it is pure Java with no Android imports (AD-11), so
 * {@code ArrivalRadiusValidatorTest} can check it on the JVM.
 *
 * <p><b>The bounds come from {@link GeoConstants}, never from a literal here.</b> The same limits are
 * enforced by {@code SettingsRepository.setArrivalRadius}, which refuses an out-of-range value rather
 * than storing it. Two copies of "10 to 1000" would eventually disagree, and the one in the UI is the
 * one a user would believe.
 *
 * <p><b>The overflow case is the one worth knowing about.</b> {@code Integer.parseInt} throws
 * {@link NumberFormatException} for a value too large to be an {@code int} - it does not silently wrap.
 * That means a user typing "999999999999" is rejected rather than having it land on some plausible
 * number, which is why the parse is inside the {@code try} together with the range check rather than
 * being trusted to fail a comparison.
 */
public final class ArrivalRadiusValidator {

    /** Utility class - never instantiated. */
    private ArrivalRadiusValidator() {
    }

    /**
     * The arrival radius in metres, or {@code null} when the text is not a usable radius.
     *
     * <p>Returns {@code null} for every kind of "no": blank, not a number, a fraction, a negative
     * number, zero, below the minimum, above the maximum, or too large to be an {@code int} at all.
     *
     * <p><b>{@code null} rather than {@code 0} or {@code -1}.</b> Those are values a caller could
     * accidentally use - {@code 0} in particular is a radius the arrival check must never be given. A
     * null is a value the caller has to deal with, and the compiler will not let it be used as a
     * distance by accident.
     *
     * @param text whatever is in the field, possibly {@code null}
     * @return metres, or {@code null} when unusable
     */
    public static Integer parseMetres(String text) {
        // The same trim the rest of the app uses, so a value pasted with a trailing space behaves the
        // way the user expects rather than being rejected as "not a number".
        String trimmed = StoreValidator.trimToEmpty(text);
        if (trimmed.isEmpty()) {
            return null;
        }

        try {
            int metres = Integer.parseInt(trimmed);
            // Zero and negatives fail here too, so "you must be exactly on the pin" can never be stored.
            return GeoConstants.isValidArrivalRadius(metres) ? metres : null;
        } catch (NumberFormatException e) {
            // Not a number at all, or a number too large for an int. Both mean the same thing to the
            // user, and both are refused rather than guessed at.
            return null;
        }
    }
}

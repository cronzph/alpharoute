package com.cph.alpharoute.domain;

/**
 * What is wrong with one field of the Add / Edit Store form.
 *
 * <p><b>Why an enum rather than a message:</b> the {@code domain} package is pure Java with zero
 * Android imports (AD-11), so it cannot reach {@code R.string} and must not hold user-facing text.
 * The validator reports <i>what kind</i> of problem a field has; the screen decides how to word it.
 * That also means the validation rules can be unit-tested on the JVM in milliseconds, with no device
 * and no emulator.
 *
 * <p>Phase 3.3 maps these to the inline field errors listed in the Phase 0.3 screen plan.
 */
public enum StoreFieldError {

    /** Blank, or nothing but whitespace, after trimming. */
    REQUIRED,

    /** Longer than the field allows. */
    TOO_LONG,

    /** Latitude outside -90..90, or longitude outside -180..180. */
    INVALID_COORDINATES
}

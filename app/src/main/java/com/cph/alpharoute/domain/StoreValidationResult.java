package com.cph.alpharoute.domain;

/**
 * The outcome of validating an Add / Edit Store form: one error slot per field, or none.
 *
 * <p>Every field is checked independently, so the screen can show all of a form's problems at once
 * rather than making the user fix them one at a time.
 *
 * <p>The fields are package-private and the getters are public, because the only thing allowed to
 * write them is {@link StoreValidator}, which lives in this same package. Callers outside the package
 * can read the result but cannot fabricate one.
 *
 * <p>A {@code null} error means that field is fine. Note that {@link #isValid()} is the only thing a
 * caller normally needs to branch on.
 */
public class StoreValidationResult {

    StoreFieldError routeNumberError;
    StoreFieldError storeCodeError;
    StoreFieldError storeNameError;
    StoreFieldError areaError;
    StoreFieldError coordinatesError;

    /** A result with no errors at all. */
    public static StoreValidationResult valid() {
        return new StoreValidationResult();
    }

    /** True when every field passed. */
    public boolean isValid() {
        return routeNumberError == null
                && storeCodeError == null
                && storeNameError == null
                && areaError == null
                && coordinatesError == null;
    }

    public StoreFieldError routeNumberError() {
        return routeNumberError;
    }

    public StoreFieldError storeCodeError() {
        return storeCodeError;
    }

    public StoreFieldError storeNameError() {
        return storeNameError;
    }

    public StoreFieldError areaError() {
        return areaError;
    }

    public StoreFieldError coordinatesError() {
        return coordinatesError;
    }
}

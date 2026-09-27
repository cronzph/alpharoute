package com.cph.alpharoute.domain;

/**
 * Decides whether a recorded navigation session is worth offering back to the user.
 *
 * <p>Pure Java, zero Android imports (AD-11). The rule is one comparison, but it is the difference between
 * silently losing a drive and nagging about one that finished last week, so it gets its own tested class.
 */
public final class NavigationRecovery {

    /**
     * How long a recorded session stays worth offering, in milliseconds.
     *
     * <p><b>Four hours.</b> Long enough to cover a real delivery run interrupted by a low-memory kill -
     * those are minutes to an hour. Short enough that "you were navigating yesterday" never happens.
     *
     * <p>The judgement being made: a session this old is far more likely to be a forgotten leftover than a
     * drive still in progress, and the cost of asking wrongly is higher than the cost of not asking. A user
     * who was genuinely still driving will simply start navigation again - two taps - whereas a user shown
     * a stale prompt has to reason about what it means before dismissing it.
     */
    public static final long RESUME_WINDOW_MILLIS = 4L * 60L * 60L * 1000L;

    /** Utility class - never instantiated. */
    private NavigationRecovery() {
    }

    /**
     * Decides what to do about a recorded session.
     *
     * @param recordedStoreId the value from {@code app_settings.lastSelectedStoreId}, or {@code null}
     * @param lastUpdatedMillis when the settings row was last written, epoch millis. This is the only
     *                          timestamp available and it is the right one: the row is touched when the
     *                          session starts, so it records when navigation was last begun or ended.
     * @param nowMillis the current time, passed in so the rule is testable without waiting
     */
    public static RecoveryState evaluate(Long recordedStoreId,
                                         long lastUpdatedMillis,
                                         long nowMillis) {
        if (recordedStoreId == null || recordedStoreId <= 0L) {
            // A null is the ordinary "no session" case. A non-positive id cannot be a real store - Room's
            // autoGenerate keys start at 1 - so it is treated as absent rather than looked up and missed.
            return RecoveryState.NOTHING;
        }

        if (lastUpdatedMillis <= 0L) {
            // A row with no usable timestamp. Cannot tell how old the reference is, so it is not offered.
            // Clearing it is the safe direction: the worst outcome is starting navigation again.
            return RecoveryState.STALE;
        }

        // A timestamp in the future means the clock moved backwards - the user changed the date, or the
        // device synced its time mid-session. Subtracting would give a negative age, which reads as
        // "brand new" and would offer a resume for something arbitrarily old. Treat it as unusable.
        if (lastUpdatedMillis > nowMillis) {
            return RecoveryState.STALE;
        }

        long age = nowMillis - lastUpdatedMillis;
        return age <= RESUME_WINDOW_MILLIS ? RecoveryState.OFFER_RESUME : RecoveryState.STALE;
    }
}

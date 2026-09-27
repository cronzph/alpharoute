package com.cph.alpharoute.domain;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * The rules that decide whether a recorded session is offered back.
 *
 * <p>Pure JVM, no Android, no clock - {@code nowMillis} is a parameter (AD-11), which is why the
 * four-hour boundary can be asserted exactly instead of being "roughly right" after a device test.
 *
 * <p>The tests are grouped by the four ways the input can be unusable, then by the boundary itself.
 * The boundary cases (exactly at the limit, one millisecond either side) are the ones that matter: an
 * off-by-one here either nags the user about a drive from yesterday or silently drops a live one.
 */
public class NavigationRecoveryTest {

    /** A fixed reference point, so the arithmetic in the assertions is readable. */
    private static final long NOW = 1_700_000_000_000L;

    private static final long ONE_HOUR = 60L * 60L * 1000L;

    // ------------------------------------------------------------------ nothing recorded

    @Test
    public void noRecordedStoreIsNothing() {
        assertEquals(RecoveryState.NOTHING, NavigationRecovery.evaluate(null, NOW, NOW));
    }

    @Test
    public void aZeroStoreIdIsTreatedAsNoStore() {
        // Room's autoGenerate keys start at 1, so 0 cannot be a real store. Treating it as absent is the
        // safe reading - the alternative is a lookup that always misses.
        assertEquals(RecoveryState.NOTHING, NavigationRecovery.evaluate(0L, NOW, NOW));
    }

    @Test
    public void aNegativeStoreIdIsTreatedAsNoStore() {
        assertEquals(RecoveryState.NOTHING, NavigationRecovery.evaluate(-1L, NOW, NOW));
    }

    @Test
    public void aMissingStoreIdIsNothingRegardlessOfTheTimestamp() {
        // The timestamp is only meaningful once there is a store to go with it.
        assertEquals(RecoveryState.NOTHING, NavigationRecovery.evaluate(null, 0L, NOW));
    }

    // ------------------------------------------------------------------ unusable timestamps

    @Test
    public void aRowWithNoTimestampIsStale() {
        // Cannot tell how old the reference is, so it is not offered. Clearing it costs the user two
        // taps; offering a possibly ancient drive costs them a confusing prompt.
        assertEquals(RecoveryState.STALE, NavigationRecovery.evaluate(7L, 0L, NOW));
    }

    @Test
    public void aNegativeTimestampIsStale() {
        assertEquals(RecoveryState.STALE, NavigationRecovery.evaluate(7L, -5L, NOW));
    }

    @Test
    public void aFutureTimestampIsStaleRatherThanFresh() {
        // The clock moved backwards - a date change, or a time sync mid-session. Subtracting would give a
        // negative age, which reads as "brand new" and would offer a resume for something arbitrarily old.
        assertEquals(RecoveryState.STALE, NavigationRecovery.evaluate(7L, NOW + ONE_HOUR, NOW));
    }

    @Test
    public void aTimestampExactlyNowIsFresh() {
        assertEquals(RecoveryState.OFFER_RESUME, NavigationRecovery.evaluate(7L, NOW, NOW));
    }

    // ------------------------------------------------------------------ the four-hour window

    @Test
    public void aSessionStartedMinutesAgoIsOffered() {
        assertEquals(RecoveryState.OFFER_RESUME,
                NavigationRecovery.evaluate(7L, NOW - (5L * 60L * 1000L), NOW));
    }

    @Test
    public void aSessionStartedAnHourAgoIsOffered() {
        // A low-memory kill during a delivery run is the case this whole feature exists for.
        assertEquals(RecoveryState.OFFER_RESUME, NavigationRecovery.evaluate(7L, NOW - ONE_HOUR, NOW));
    }

    @Test
    public void exactlyAtTheLimitIsStillOffered() {
        // The boundary is inclusive. "Four hours" reads as "up to and including four hours", and the
        // alternative reading would mean a session touched four hours ago is thrown away one millisecond
        // earlier than the constant claims.
        assertEquals(RecoveryState.OFFER_RESUME,
                NavigationRecovery.evaluate(7L, NOW - NavigationRecovery.RESUME_WINDOW_MILLIS, NOW));
    }

    @Test
    public void oneMillisecondPastTheLimitIsStale() {
        assertEquals(RecoveryState.STALE,
                NavigationRecovery.evaluate(
                        7L, NOW - NavigationRecovery.RESUME_WINDOW_MILLIS - 1L, NOW));
    }

    @Test
    public void yesterdayIsStale() {
        // The prompt must never say "you were navigating yesterday".
        assertEquals(RecoveryState.STALE, NavigationRecovery.evaluate(7L, NOW - (25L * ONE_HOUR), NOW));
    }

    @Test
    public void aSessionFromLastWeekIsStale() {
        assertEquals(RecoveryState.STALE, NavigationRecovery.evaluate(7L, NOW - (7L * 24L * ONE_HOUR), NOW));
    }

    // ------------------------------------------------------------------ the constant itself

    @Test
    public void theWindowIsFourHours() {
        // Asserted explicitly, because the whole judgement - "probably still driving" versus "forgotten" -
        // rests on this number, and a silent change to it would change behaviour without failing anything.
        assertEquals(4L * ONE_HOUR, NavigationRecovery.RESUME_WINDOW_MILLIS);
    }
}

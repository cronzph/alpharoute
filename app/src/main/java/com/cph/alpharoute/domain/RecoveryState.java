package com.cph.alpharoute.domain;

/**
 * What the app should do about a navigation session it found recorded in the database.
 *
 * <p><b>The problem this answers.</b> {@code app_settings.lastSelectedStoreId} is written when navigation
 * starts and cleared when it ends (AD-10). If the app is still running and the user is mid-drive, the
 * value is a live session. If the app was killed by the system, or the process died, the value is a
 * leftover - and the two look identical from the database alone.
 *
 * <p><b>It must not be resumed automatically, and it must not be discarded silently either.</b> Resuming
 * on its own would mean starting a foreground service (illegal from the background on Android 12+) and
 * could mean offering to navigate to a store the user drove to last week. Discarding it would lose a real
 * in-progress drive after a low-memory kill, which is a normal thing to happen on a phone.
 *
 * <p>So the app asks. This enum is the decision, and it is pure Java (AD-11) so the rule can be tested
 * without a device, a service or a clock.
 */
public enum RecoveryState {

    /**
     * Nothing was recorded. The ordinary case: no session was ever started, or the last one ended cleanly.
     */
    NOTHING,

    /**
     * A session was recorded and it is recent enough to offer back to the user.
     *
     * <p><b>An offer, never a resume.</b> The user gets "Resume navigation?" with Resume and Discard. Only
     * the tap starts the service, which is what makes the start legal - and what stops the app deciding on
     * the driver's behalf that a drive is still happening.
     */
    OFFER_RESUME,

    /**
     * A session was recorded, but it is too old to be a live drive.
     *
     * <p>The reference is cleared without asking. A drive that was last touched longer ago than the
     * freshness limit is over - the user either arrived, gave up, or finished days ago - and offering to
     * resume it would be noise at best and confusing at worst.
     */
    STALE
}

package com.cph.alpharoute.data.sync;

/**
 * The seam between "the user changed something locally" and "somewhere, eventually, a copy is made
 * off-device".
 *
 * <p><b>This exists because of a specific requirement:</b> the app must save locally first and sync
 * afterwards, so the interface never waits on a slow or absent internet connection. The way that is
 * guaranteed is architectural, not a matter of clever code:
 *
 * <ol>
 *   <li>Every write goes to Room on {@code AppExecutors.diskIO()} and returns as soon as the
 *       transaction commits.</li>
 *   <li>Only <i>after</i> the local write has succeeded does the repository call
 *       {@link #onLocalDataChanged()}.</li>
 *   <li>That call is made inside a try/catch that swallows and logs any failure, so a bug in a sync
 *       implementation can never break a local write or surface an error to the user.</li>
 *   <li>Nothing in the UI ever observes sync state. The screens read Room.</li>
 * </ol>
 *
 * <p>The result: with no signal at all, the app is fully functional. With a slow connection, it is
 * still fully functional - the sync simply takes longer, invisibly.
 *
 * <p><b>Current implementation: {@link NoOpSyncManager}.</b> The Firestore implementation lands in a
 * later phase, once the Firebase project and its configuration values exist - they are project
 * specific and must not be invented. Because the call sites are already in place, that change is
 * additive: no repository, no ViewModel and no screen has to change.
 */
public interface SyncManager {

    /**
     * Called after a local write has committed successfully.
     *
     * <p>Implementations must return promptly and must not do network work on the calling thread. Must
     * never throw - the caller treats any exception as a local-only outcome.
     */
    void onLocalDataChanged();
}

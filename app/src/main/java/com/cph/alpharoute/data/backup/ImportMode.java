package com.cph.alpharoute.data.backup;

/**
 * How a restore treats data that is already in the app.
 *
 * <p><b>The user chooses, and the choice is not reversible.</b> That is why it is an explicit parameter
 * rather than a default: there is no safe guess between "throw my data away" and "keep it".
 */
public enum ImportMode {

    /**
     * Delete everything, then restore the backup.
     *
     * <p>The app ends up exactly as the backup describes it - which is what "restore" usually means, and the
     * only way to undo a mistake made after the backup was taken.
     *
     * <p><b>It destroys data.</b> Anything added since the backup was written is gone.
     */
    REPLACE,

    /**
     * Add what is missing and leave the rest alone.
     *
     * <p>A store whose code is already present is kept as it is - the app's copy wins, because it may have
     * been completed since. Completions are added only where the store has none for that round.
     *
     * <p>Nothing is deleted, so this cannot lose data. It also cannot fully restore: a store deleted since
     * the backup comes back, but a completion removed since then may not.
     */
    MERGE
}

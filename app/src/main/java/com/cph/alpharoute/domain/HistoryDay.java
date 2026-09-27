package com.cph.alpharoute.domain;

import androidx.annotation.NonNull;

/**
 * One day's worth of completions, as the History screen shows it.
 *
 * <p><b>The count is derived, never stored.</b> It is the number of rows in this group, counted from the
 * actual history - the screen plan is explicit that the per-day figure "comes from the actual history
 * rows, never from a stored counter". A stored daily total could drift from the rows it claims to
 * summarise, and there would be no way to tell which was right.
 *
 * <p>Pure Java, zero Android imports (AD-11).
 */
public final class HistoryDay {

    private final String dateKey;
    private final int completedCount;

    HistoryDay(@NonNull String dateKey, int completedCount) {
        this.dateKey = dateKey;
        this.completedCount = completedCount;
    }

    /** The local calendar day, {@code YYYY-MM-DD} - the same key the completion transaction stored. */
    @NonNull
    public String dateKey() {
        return dateKey;
    }

    /** How many stores were completed that day. Always at least 1: a day with no rows is not a group. */
    public int completedCount() {
        return completedCount;
    }
}

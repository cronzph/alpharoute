package com.cph.alpharoute.domain;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One day's work inside one round.
 *
 * <p><b>Phase 12.</b> A round is one complete pass of every store, and a round is not finished in a day -
 * so the question "how long did this round take" is answered by looking at the days it was worked on.
 *
 * <p>Pure Java.
 */
public final class RoundDay {

    private final int dayNumber;
    private final String dateKey;
    private final List<Long> storeIds;

    /**
     * @param dayNumber 1 for the first day of the round, 2 for the second, and so on. <b>Not</b> the day of
     *                  the month - that is {@code dateKey}'s job, and conflating the two is how "day 1" ends
     *                  up meaning the 1st of the month
     * @param dateKey   the calendar day, {@code YYYY-MM-DD}
     * @param storeIds  the stores completed that day, in the order they were done
     */
    public RoundDay(int dayNumber, @NonNull String dateKey, @NonNull List<Long> storeIds) {
        this.dayNumber = dayNumber;
        this.dateKey = dateKey;
        this.storeIds = Collections.unmodifiableList(new ArrayList<>(storeIds));
    }

    /** Which day of the round this was: 1, 2, 3… */
    public int dayNumber() {
        return dayNumber;
    }

    /** The calendar day, {@code YYYY-MM-DD}. */
    @NonNull
    public String dateKey() {
        return dateKey;
    }

    /** The stores completed that day, in the order they were done. */
    @NonNull
    public List<Long> storeIds() {
        return storeIds;
    }

    /** How many stores were completed that day. */
    public int count() {
        return storeIds.size();
    }

    @NonNull
    @Override
    public String toString() {
        return "RoundDay(day " + dayNumber + ", " + dateKey + ", " + storeIds.size() + " stores)";
    }
}

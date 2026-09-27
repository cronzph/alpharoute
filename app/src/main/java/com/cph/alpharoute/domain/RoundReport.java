package com.cph.alpharoute.domain;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One round, summarised: how many stores, over how many days, and what was done on each of them.
 *
 * <p><b>Phase 12.</b> Answers the question the app could not answer before: <i>"round 2 was 65 stores and
 * took 4 days — 23 on day 1, 18 on day 2…"</i>
 *
 * <p><b>{@link #totalStores} counts the stores actually completed in this round</b>, not the size of the
 * route. For a finished round the two are the same; for the round in progress they are not, and reporting
 * the route size would claim work that has not happened.
 *
 * <p><b>{@link #daysTaken} counts the days worked</b>, not the calendar span. A round worked on the 1st and
 * then the 8th is two days of work with a week in between, and "8 days" would be a misleading answer to
 * "how long did it take".
 *
 * <p>Pure Java.
 */
public final class RoundReport {

    private final int roundNumber;
    private final List<RoundDay> days;
    private final boolean complete;

    /**
     * @param roundNumber which round this is
     * @param days        the days worked, oldest first
     * @param complete    whether every store in the route has been done in this round
     */
    public RoundReport(int roundNumber, @NonNull List<RoundDay> days, boolean complete) {
        this.roundNumber = roundNumber;
        this.days = Collections.unmodifiableList(new ArrayList<>(days));
        this.complete = complete;
    }

    public int roundNumber() {
        return roundNumber;
    }

    /** The days this round was worked on, oldest first. */
    @NonNull
    public List<RoundDay> days() {
        return days;
    }

    /** Whether the round is finished. */
    public boolean isComplete() {
        return complete;
    }

    /** How many stores were completed in this round. */
    public int totalStores() {
        int total = 0;
        for (RoundDay day : days) {
            total += day.count();
        }
        return total;
    }

    /** How many days of work the round took. */
    public int daysTaken() {
        return days.size();
    }

    /** The first day worked, or {@code null} for a round with nothing in it. */
    public String startDateKey() {
        return days.isEmpty() ? null : days.get(0).dateKey();
    }

    /** The last day worked, or {@code null} for a round with nothing in it. */
    public String endDateKey() {
        return days.isEmpty() ? null : days.get(days.size() - 1).dateKey();
    }

    /** Whether anything was done in this round at all. */
    public boolean hasWork() {
        return !days.isEmpty();
    }

    @NonNull
    @Override
    public String toString() {
        return "RoundReport(round " + roundNumber + ", " + totalStores()
                + " stores over " + daysTaken() + " days)";
    }
}

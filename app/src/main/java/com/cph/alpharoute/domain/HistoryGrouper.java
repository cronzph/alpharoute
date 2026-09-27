package com.cph.alpharoute.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Groups completion rows into the days the History screen shows.
 *
 * <p><b>Why this is not a SQL {@code GROUP BY}.</b> It could be, and the count would be the same - but the
 * Daily History screen needs the individual rows for a day anyway, so the same data would be read twice
 * through two queries, at two moments. Grouping the one list in memory means the History screen and the
 * day it opens can never disagree.
 *
 * <p><b>The ordering rule, stated once.</b> Days are newest first. That is a rule rather than a preference
 * - a history that opened on the oldest day would make the user scroll to find out what they did today -
 * so it is asserted in {@code HistoryGrouperTest} rather than left to whatever order the query returned.
 *
 * <p>Pure Java, zero Android imports (AD-11). The input is the {@code CompletionLike} seam, so the real
 * Room entities can be passed in without {@code domain/} depending on {@code androidx.room}.
 */
public final class HistoryGrouper {

    /** Utility class - never instantiated. */
    private HistoryGrouper() {
    }

    /**
     * Groups rows by local calendar day, newest day first.
     *
     * @param history every completion row. {@code null} and empty both produce an empty list
     * @return one {@link HistoryDay} per distinct {@code completionDateKey}, newest first. Never
     *         {@code null}
     */
    @NonNull
    public static List<HistoryDay> group(
            @Nullable List<? extends TrackerCalculator.CompletionLike> history) {

        if (history == null || history.isEmpty()) {
            return Collections.emptyList();
        }

        // A LinkedHashMap keeps insertion order, which is then sorted explicitly below. Relying on the
        // insertion order alone would make the result depend on the order of the input list - a detail of
        // whichever query produced it.
        Map<String, Integer> countsByDay = new LinkedHashMap<>();

        for (TrackerCalculator.CompletionLike completion : history) {
            if (completion == null) {
                // A null in the list would be a repository bug, but it must not throw inside a LiveData
                // observer - the same defensiveness TrackerCalculator applies.
                continue;
            }
            String dateKey = completion.completionDateKey();
            if (dateKey == null || dateKey.isEmpty()) {
                // A row with no day cannot be placed on a day. Skipping it is the only honest option;
                // inventing "today" would put it in the wrong group and hide the real problem, which is
                // Phase 6.5's business to report.
                continue;
            }

            Integer existing = countsByDay.get(dateKey);
            countsByDay.put(dateKey, existing == null ? 1 : existing + 1);
        }

        List<HistoryDay> days = new ArrayList<>(countsByDay.size());
        for (Map.Entry<String, Integer> entry : countsByDay.entrySet()) {
            days.add(new HistoryDay(entry.getKey(), entry.getValue()));
        }

        // Newest first. The keys are YYYY-MM-DD, so a plain string comparison is also a date comparison -
        // which is the whole reason the key is stored in that shape. No date parsing, no timezone, and
        // no chance of the sort disagreeing with the grouping.
        Collections.sort(days, (left, right) -> right.dateKey().compareTo(left.dateKey()));

        return days;
    }
}

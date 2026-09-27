package com.cph.alpharoute.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Groups the completion history into rounds, and each round into the days it was worked on.
 *
 * <p><b>Phase 12.</b> The History tab answers "what did I do on this day"; this answers "how did this round
 * go" — which is a different question, because a round is not a day. A round of 65 stores might be 23 on
 * day 1 and 18 on day 2, and no single day's view can show that.
 *
 * <h3>The shape it produces</h3>
 *
 * <pre>
 *   Round 3   in progress   12 stores   2 days
 *     Day 1   2026-09-25     8 stores   [ids…]
 *     Day 2   2026-09-27     4 stores   [ids…]
 *   Round 2   complete      65 stores   4 days
 *     Day 1   2026-09-01    23 stores   [ids…]
 *     …
 * </pre>
 *
 * <p><b>Rounds come back newest first</b> — the round in progress is what the user is living in. <b>Days
 * come back oldest first</b>, because "day 1, day 2, day 3" is the order they happened in and the order the
 * story makes sense in. The two orders are deliberately different.
 *
 * <p>Pure Java (AD-11), so every one of those decisions is asserted on the JVM.
 */
public final class RoundGrouper {

    /** Utility class - never instantiated. */
    private RoundGrouper() {
    }

    /**
     * Builds the report for every round that has any work in it.
     *
     * @param history      every completion row, across every round
     * @param totalStores  how many stores exist in the route. Used to decide whether the round in progress
     *                     is finished; {@code 0} or less means "unknown", in which case nothing is marked
     *                     complete
     * @param currentRound the round in progress, from {@code app_settings.currentRound}
     * @return one report per round, newest first. Empty when there is no history at all
     */
    @NonNull
    public static List<RoundReport> group(
            @Nullable List<? extends TrackerCalculator.CompletionLike> history,
            int totalStores,
            int currentRound) {

        if (history == null || history.isEmpty()) {
            return Collections.emptyList();
        }

        // roundNumber → (dateKey → storeIds). A LinkedHashMap for the rounds so the insertion order is the
        // order they were first seen, and a TreeMap for the days so they sort by date without a comparator.
        Map<Integer, Map<String, List<Long>>> byRound = new LinkedHashMap<>();

        for (TrackerCalculator.CompletionLike completion : history) {
            if (completion == null) {
                continue;
            }

            String dateKey = completion.completionDateKey();
            if (dateKey == null || dateKey.trim().isEmpty()) {
                // A row with no date cannot be placed on a day, and guessing one would put it on the wrong
                // day with no indication that it had been guessed.
                continue;
            }

            Map<String, List<Long>> days = byRound.get(completion.roundNumber());
            if (days == null) {
                days = new TreeMap<>();
                byRound.put(completion.roundNumber(), days);
            }

            List<Long> stores = days.get(dateKey);
            if (stores == null) {
                stores = new ArrayList<>();
                days.put(dateKey, stores);
            }

            // A store counted twice in one round is impossible through the app - the unique index forbids
            // it - but the total would be wrong if a corrupt row were counted twice, so it is guarded.
            if (!stores.contains(completion.storeId())) {
                stores.add(completion.storeId());
            }
        }

        List<RoundReport> reports = new ArrayList<>(byRound.size());
        for (Map.Entry<Integer, Map<String, List<Long>>> entry : byRound.entrySet()) {
            int roundNumber = entry.getKey();

            List<RoundDay> days = new ArrayList<>(entry.getValue().size());
            int dayNumber = 0;
            for (Map.Entry<String, List<Long>> day : entry.getValue().entrySet()) {
                dayNumber++;
                days.add(new RoundDay(dayNumber, day.getKey(), day.getValue()));
            }

            // A round is finished if the user has moved past it, or if it covered the whole route.
            //
            // Both halves are needed. The count alone gets it wrong for a round the user closed early and
            // then added a store to - it would read as still open for ever. The round number alone gets it
            // wrong for the round in progress, which is finished the moment its last store is done.
            int done = 0;
            for (RoundDay day : days) {
                done += day.count();
            }
            boolean complete = roundNumber < currentRound
                    || (totalStores > 0 && done >= totalStores);

            reports.add(new RoundReport(roundNumber, days, complete));
        }

        // Newest round first. Sorted here rather than relying on insertion order, because the history could
        // arrive in any order and the display order must not depend on it.
        Collections.sort(reports, (left, right) ->
                Integer.compare(right.roundNumber(), left.roundNumber()));

        return reports;
    }
}

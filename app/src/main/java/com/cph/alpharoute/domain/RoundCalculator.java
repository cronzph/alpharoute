package com.cph.alpharoute.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Works out how far through the current round the user is.
 *
 * <p><b>The rule, stated once:</b> a round is complete when <i>every store in the database</i> has a
 * completion row carrying the current round number. Not "every store that existed when the round started"
 * - a store added halfway through belongs to the round in progress, because the user added it precisely
 * because they still have to visit it. The honest consequence is that adding a store re-opens a round that
 * was about to finish, which is correct: there is now one more shop to go to.
 *
 * <p><b>Why this is not a SQL {@code COUNT}.</b> Counting completions in the round and comparing with the
 * store count would give the wrong answer the moment a store's completion is missing while another store
 * has an extra row - which the unique index makes impossible, but which a corrupt database could still
 * produce. Comparing <i>sets of ids</i> answers the actual question: which stores have no completion this
 * round. It also means the same calculation can be tested on the JVM.
 *
 * <p>Pure Java, zero Android imports (AD-11). The inputs are the {@code StoreLike} and
 * {@code CompletionLike} seams, so the Room entities can be passed in without {@code domain/} depending on
 * {@code androidx.room}.
 */
public final class RoundCalculator {

    /** Utility class - never instantiated. */
    private RoundCalculator() {
    }

    /**
     * Computes the current round's progress.
     *
     * @param stores       every store
     * @param history      every completion row, across every round
     * @param currentRound the round being worked through, from {@code app_settings.currentRound}
     * @return one immutable value. Never {@code null}
     */
    @NonNull
    public static RoundProgress compute(
            @Nullable List<? extends TrackerCalculator.StoreLike> stores,
            @Nullable List<? extends TrackerCalculator.CompletionLike> history,
            int currentRound) {

        if (stores == null || stores.isEmpty()) {
            return new RoundProgress(currentRound, 0, 0);
        }

        // Which stores were completed in THIS round. Rows from earlier rounds are ignored entirely -
        // that is the whole point of the round number.
        Set<Long> completedThisRound = new HashSet<>();
        if (history != null) {
            for (TrackerCalculator.CompletionLike completion : history) {
                if (completion == null || completion.roundNumber() != currentRound) {
                    continue;
                }
                completedThisRound.add(completion.storeId());
            }
        }

        // Counted against the STORES, not against the history. A completion row for a store that no
        // longer exists - which the foreign key makes impossible - must not be able to make the round look
        // further along than it is. Phase 6.5's consistency check is what reports such a row.
        int total = 0;
        int completed = 0;
        Set<Long> countedStoreIds = new HashSet<>();
        for (TrackerCalculator.StoreLike store : stores) {
            if (store == null) {
                continue;
            }
            // A duplicate id in the list would otherwise be counted twice and could push the round to
            // "complete" while a real store was still unvisited.
            if (!countedStoreIds.add(store.id())) {
                continue;
            }
            total++;
            if (completedThisRound.contains(store.id())) {
                completed++;
            }
        }

        return new RoundProgress(currentRound, completed, total);
    }
}

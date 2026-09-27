package com.cph.alpharoute.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Which stores are still to visit in the round in progress.
 *
 * <p><b>One definition, used by everything that needs it.</b> The Dashboard's suggestion, the optimized
 * route and the map's marker colours all have to agree about which stores are outstanding - and they are
 * three different places, reading the same two tables. Writing the rule three times would let them drift,
 * and the symptom would be a suggestion pointing at a store the map shows as done.
 *
 * <p>Pure Java, zero Android imports (AD-11). The callers map the ids onto whatever type they need - a
 * {@code StoreCandidate}, a {@code StoreEntity} - which is why this returns ids rather than objects.
 */
public final class PendingStores {

    /** Utility class - never instantiated. */
    private PendingStores() {
    }

    /**
     * The ids of the stores with no completion in the given round.
     *
     * @param history      every completion row, across every round
     * @param currentRound the round being worked through
     * @return the outstanding store ids. Never {@code null}
     */
    @NonNull
    public static Set<Long> idsPendingInRound(
            @Nullable List<? extends TrackerCalculator.CompletionLike> history,
            int currentRound) {

        if (history == null || history.isEmpty()) {
            // Nothing completed at all, so every store is pending. The caller subtracts from its own store
            // list, which is the only place that knows what "every store" means.
            return Collections.emptySet();
        }

        Set<Long> completed = new HashSet<>();
        for (TrackerCalculator.CompletionLike completion : history) {
            if (completion == null) {
                continue;
            }
            // Scoped to the round. A completion from an earlier round says nothing about whether the store
            // is done now - which is exactly what starting a new round changes.
            if (completion.roundNumber() == currentRound) {
                completed.add(completion.storeId());
            }
        }
        return completed;
    }

    /**
     * Whether a store is still to visit.
     *
     * @param completedIds the set from {@link #idsPendingInRound}
     */
    public static boolean isPending(long storeId, @NonNull Set<Long> completedIds) {
        return !completedIds.contains(storeId);
    }
}

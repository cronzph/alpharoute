package com.cph.alpharoute.domain;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Puts a saved plan back together with the stores that are actually left to visit.
 *
 * <p><b>Why this is a class of its own rather than a few lines in the screen.</b> A saved order and a list
 * of pending stores are two different things that were true at two different times, and merging them has
 * three ways to go wrong - each of which is silent:
 *
 * <ul>
 *   <li><b>A store in the plan has since been completed.</b> It must drop out. Leaving it in would put a
 *       shop the user has already done back on the route, and the count on screen would be wrong.</li>
 *   <li><b>A store has been added since the plan was saved.</b> It must appear. Dropping it would leave a
 *       shop out of the round entirely - and the user would drive home believing the day was done, which
 *       is the one outcome the whole app is built to prevent.</li>
 *   <li><b>The plan lists the same store twice.</b> Nothing stops that at the database level - the unique
 *       index is on {@code (planId, position)}, not on {@code (planId, storeId)} - so a plan can hold a
 *       repeat and the route would visit the same shop twice.</li>
 * </ul>
 *
 * <p><b>The user's order is never second-guessed.</b> This does not sort, score or improve anything. It
 * keeps the order the user set, drops what is gone, and appends what is new at the end. Where a new store
 * goes is a judgement the app should not make on the user's behalf - the end is the honest default, and
 * the user can drag it wherever it belongs.
 *
 * <p>Pure Java (AD-11), so all three cases above are asserted on the JVM.
 */
public final class PlanMerge {

    /** Utility class - never instantiated. */
    private PlanMerge() {
    }

    /**
     * The order to drive, given what was saved and what is left.
     *
     * @param savedOrder the store ids in the order the user set them. Ids that are no longer pending are
     *                   ignored, and so are repeats
     * @param pending    the stores still to visit, in the order the planner suggested. Anything the plan
     *                   does not mention is appended in this order
     * @return the merged order. Empty when there is nothing pending, whatever the plan says
     */
    @NonNull
    public static List<StoreCandidate> merge(@NonNull List<Long> savedOrder,
                                             @NonNull List<StoreCandidate> pending) {

        Map<Long, StoreCandidate> pendingById = new HashMap<>();
        for (StoreCandidate store : pending) {
            if (store != null) {
                pendingById.put(store.id(), store);
            }
        }

        List<StoreCandidate> merged = new ArrayList<>(pending.size());
        Set<Long> placed = new HashSet<>();

        // 1. The user's order, minus everything that is no longer true.
        for (Long id : savedOrder) {
            if (id == null) {
                continue;
            }
            StoreCandidate store = pendingById.get(id);
            if (store == null) {
                // Completed, or deleted since the plan was saved. Either way it is not part of today.
                continue;
            }
            if (!placed.add(id)) {
                // The plan mentions it twice. The first mention wins, so the user's earliest intent is
                // the one kept.
                continue;
            }
            merged.add(store);
        }

        // 2. Everything the plan did not know about, in the order the planner put them.
        for (StoreCandidate store : pending) {
            if (store == null) {
                continue;
            }
            if (placed.add(store.id())) {
                merged.add(store);
            }
        }

        return merged;
    }
}

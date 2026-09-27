package com.cph.alpharoute.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Checks that the store records and the completion history still agree with each other.
 *
 * <p><b>What this is for.</b> The tracker's every figure is derived from two tables that are supposed to be
 * kept in step by the completion transaction (Phase 5.3) and protected by two database guards - the UNIQUE
 * index on {@code storeId} (AD-5) and the foreign key with {@code onDelete = RESTRICT} (AD-6). If those
 * guarantees ever failed, the symptom would not be an error: it would be a count that is quietly wrong.
 * This is the one place that looks for the disagreement rather than trusting the arithmetic.
 *
 * <p><b>It reports. It never repairs.</b> The screen plan is explicit - "do not silently delete data" - and
 * the reason is that a checker which fixes what it finds can also destroy what it does not understand.
 * Deleting an orphaned history row would erase the only evidence that a completion happened; flipping a
 * store's status would rewrite a record the user may have confirmed. So every finding is reported with
 * enough detail to act on, and the acting is left to a human.
 *
 * <p><b>Four things are checked, and they are genuinely different faults:</b>
 *
 * <ol>
 *   <li><b>A store is COMPLETED but has no history row.</b> The dashboard would count it in "remaining"
 *       while the map shows it green - the two screens disagree about the same store.</li>
 *   <li><b>A history row exists but its store is still PENDING.</b> The opposite disagreement: the store
 *       is counted, but it would still be offered as somewhere to visit.</li>
 *   <li><b>A history row points at a store that does not exist.</b> An orphan. The foreign key should make
 *       this impossible, so its presence means the constraint is not being enforced - see the
 *       {@code PRAGMA foreign_keys} note on {@code AppDatabase}.</li>
 *   <li><b>The number of markers drawn is not the number of stores.</b> A UI-level fault rather than a data
 *       one, and worth catching because a missing pin is easy to miss and impossible to notice later.</li>
 * </ol>
 *
 * <p>Pure Java, zero Android imports (AD-11), so the whole check runs on the JVM with no device.
 */
public final class ConsistencyChecker {

    /** What kind of disagreement was found. */
    public enum Issue {

        /** The store reads COMPLETED, but no completion history row references it. */
        COMPLETED_WITHOUT_HISTORY,

        /** A completion history row exists for a store that is still PENDING. */
        HISTORY_WITHOUT_COMPLETION,

        /** A completion history row references a store that is not in the database at all. */
        ORPHANED_HISTORY,

        /** The map drew a different number of store markers than there are stores. */
        MARKER_COUNT_MISMATCH
    }

    /** One finding, with enough detail to find the row it is about. */
    public static final class Finding {

        private final Issue issue;
        private final long storeId;
        private final String detail;

        Finding(@NonNull Issue issue, long storeId, @NonNull String detail) {
            this.issue = issue;
            this.storeId = storeId;
            this.detail = detail;
        }

        @NonNull
        public Issue issue() {
            return issue;
        }

        /** The store the finding is about, or {@code -1} when it is not about one store. */
        public long storeId() {
            return storeId;
        }

        /** A short, human-readable description, e.g. {@code "123456 (R-01)"}. */
        @NonNull
        public String detail() {
            return detail;
        }

        @NonNull
        @Override
        public String toString() {
            return issue + ": " + detail;
        }
    }

    /** Utility class - never instantiated. */
    private ConsistencyChecker() {
    }

    /**
     * Runs every check.
     *
     * @param stores          every store row
     * @param history         every completion row, across every round
     * @param drawnMarkerCount how many store markers the map currently has drawn, or {@code -1} when the
     *                         map has not been opened and the figure is unknown. Passing a made-up zero
     *                         would report a mismatch that is not real
     * @param currentRound    the round in progress, from {@code app_settings.currentRound}. The first two
     *                        checks are scoped to it - see the note below
     * @return the findings, in a stable order. Never {@code null}; an empty report means everything agrees
     */
    @NonNull
    public static List<Finding> check(
            @Nullable List<? extends TrackerCalculator.StoreLike> stores,
            @Nullable List<? extends TrackerCalculator.CompletionLike> history,
            int drawnMarkerCount,
            int currentRound) {

        List<Finding> findings = new ArrayList<>();

        List<? extends TrackerCalculator.StoreLike> safeStores =
                stores == null ? Collections.emptyList() : stores;
        List<? extends TrackerCalculator.CompletionLike> safeHistory =
                history == null ? Collections.emptyList() : history;

        // Which stores have a completion IN THE CURRENT ROUND, and which stores exist at all. Two sets
        // rather than nested loops, so the check stays linear in the number of rows.
        //
        // The round scoping is what keeps this check honest after rounds arrived. A store with history from
        // an earlier round and a PENDING status is the NORMAL state - that is exactly what starting a new
        // round produces - so an unscoped check would report every store as inconsistent the moment the
        // second round began.
        Set<Long> storeIds = new HashSet<>();
        for (TrackerCalculator.StoreLike store : safeStores) {
            if (store != null) {
                storeIds.add(store.id());
            }
        }

        Set<Long> storesWithHistoryThisRound = new HashSet<>();
        for (TrackerCalculator.CompletionLike completion : safeHistory) {
            if (completion != null && completion.roundNumber() == currentRound) {
                storesWithHistoryThisRound.add(completion.storeId());
            }
        }

        // 1. COMPLETED but not counted in this round.
        for (TrackerCalculator.StoreLike store : safeStores) {
            if (store == null || !store.isCompleted()) {
                continue;
            }
            if (!storesWithHistoryThisRound.contains(store.id())) {
                findings.add(new Finding(
                        Issue.COMPLETED_WITHOUT_HISTORY,
                        store.id(),
                        describe(store)));
            }
        }

        // 2 and 3. Every completion in the current round must belong to a real store that is COMPLETED.
        for (TrackerCalculator.CompletionLike completion : safeHistory) {
            if (completion == null || completion.roundNumber() != currentRound) {
                // Older rounds are history. They are checked for orphans below, but a row from round 1
                // saying nothing about a store that is pending in round 3 is not a fault.
                continue;
            }
            long storeId = completion.storeId();

            if (!storeIds.contains(storeId)) {
                findings.add(new Finding(
                        Issue.ORPHANED_HISTORY,
                        storeId,
                        "store " + storeId + " (missing)"));
                continue;
            }

            TrackerCalculator.StoreLike store = findById(safeStores, storeId);
            if (store != null && !store.isCompleted()) {
                findings.add(new Finding(
                        Issue.HISTORY_WITHOUT_COMPLETION,
                        storeId,
                        describe(store)));
            }
        }

        // 3b. Orphans in ANY round. A completion pointing at a store that does not exist is wrong
        // whatever round it belongs to, and it is the one fault the foreign key should make impossible -
        // so its presence says the constraint is not being enforced at all.
        for (TrackerCalculator.CompletionLike completion : safeHistory) {
            if (completion == null || completion.roundNumber() == currentRound) {
                continue;
            }
            if (!storeIds.contains(completion.storeId())) {
                findings.add(new Finding(
                        Issue.ORPHANED_HISTORY,
                        completion.storeId(),
                        "store " + completion.storeId() + " (missing, round "
                                + completion.roundNumber() + ")"));
            }
        }

        // 4. The map's marker count, when it is known. A negative value means "not known" rather than
        // "zero" - the map may simply never have been opened.
        //
        // Compared against the number of REAL stores, counted the same way the loops above count them. A
        // null element is skipped by every other check in this class, so it must not be counted here
        // either - otherwise a single null would be reported as a marker mismatch, which is a false alarm
        // about the map when the real problem is the list.
        int realStoreCount = 0;
        for (TrackerCalculator.StoreLike store : safeStores) {
            if (store != null) {
                realStoreCount++;
            }
        }

        if (drawnMarkerCount >= 0 && drawnMarkerCount != realStoreCount) {
            findings.add(new Finding(
                    Issue.MARKER_COUNT_MISMATCH,
                    -1L,
                    drawnMarkerCount + " drawn, " + realStoreCount + " stored"));
        }

        return findings;
    }

    @Nullable
    private static TrackerCalculator.StoreLike findById(
            @NonNull List<? extends TrackerCalculator.StoreLike> stores, long storeId) {
        for (TrackerCalculator.StoreLike store : stores) {
            if (store != null && store.id() == storeId) {
                return store;
            }
        }
        return null;
    }

    /** {@code "123456 (R-01)"} - the code a person recognises, with the route to disambiguate it. */
    @NonNull
    private static String describe(@NonNull TrackerCalculator.StoreLike store) {
        return store.storeCode() + " (store " + store.id() + ")";
    }
}

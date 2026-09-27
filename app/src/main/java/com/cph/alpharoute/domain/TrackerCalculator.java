package com.cph.alpharoute.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Works out every figure the Dashboard shows.
 *
 * <p><b>Why this is not done in SQL.</b> Six of the seven figures could each be a {@code COUNT} query,
 * but then the Dashboard would run six queries per change and, worse, read them at six different moments -
 * so the cards could show a set of numbers that never existed together. Computing them in one pass over
 * two lists means every figure on screen describes the same instant.
 *
 * <p><b>Nothing is stored.</b> The tracker is a pure function of the {@code stores} and
 * {@code completion_history} rows. A stored counter would be a second source of truth, free to drift, and
 * when it did there would be no way to tell which of the two was right.
 *
 * <p><b>The {@code Like} seams, and why they are here.</b> The real inputs are {@code StoreEntity} and
 * {@code CompletionHistoryEntity} - Room entities carrying {@code androidx.room} annotations. Importing
 * them would drag the data layer into {@code domain/} and break AD-11, which exists so this arithmetic can
 * be tested on a laptop with no emulator. The entities implement these two interfaces instead, so the
 * dependency points data → domain, never the other way. This is the same seam
 * {@code ArrivalEvaluator.LocationFixLike} uses for the location fix, and for the same reason.
 *
 * <p>Pure Java, zero Android imports.
 */
public final class TrackerCalculator {

    /**
     * The part of a store this calculation needs.
     *
     * <p>Three members, not eleven - so it is obvious at a glance that nothing here can change a store.
     */
    public interface StoreLike {

        /** The primary key, used to match a completion to its store. */
        long id();

        /** The code shown on the "Latest completed" card, for example {@code 123456}. */
        @NonNull
        String storeCode();

        /** Whether this store has been completed. */
        boolean isCompleted();
    }

    /**
     * The part of a completion row this calculation needs.
     *
     * <p>{@code dailySequence} is deliberately absent: it orders the Daily History screen, and no figure
     * on the Dashboard depends on it. {@code roundNumber} <i>is</i> here, because the tracker has to tell
     * this round's completions from every earlier round's.
     */
    public interface CompletionLike {

        /** The store this completion belongs to. */
        long storeId();

        /** The local calendar day, {@code YYYY-MM-DD}. */
        @NonNull
        String completionDateKey();

        /** The exact moment of completion, epoch millis. */
        long completionTimestamp();

        /** Which round this completion belongs to. 1 for the first pass over every store. */
        int roundNumber();
    }

    /** Utility class - never instantiated. */
    private TrackerCalculator() {
    }

    /**
     * Computes every Dashboard figure from the current contents of the database.
     *
     * <p>Both lists are expected to be the complete tables, not filtered views: the percentages and the
     * remaining count are only meaningful against the whole picture.
     *
     * @param stores       every store, completed or not
     * @param history      every completion row, across every round
     * @param todayKey     the device's local day, from {@code DateKeyHelper.today()}
     * @param yesterdayKey the device's local previous day, from {@code DateKeyHelper.yesterday()}
     * @param currentRound the round being worked through, from {@code app_settings.currentRound}
     * @return one immutable snapshot. Never {@code null}, even when both lists are empty
     */
    @NonNull
    public static TrackerSnapshot compute(@Nullable List<? extends StoreLike> stores,
                                          @Nullable List<? extends CompletionLike> history,
                                          @NonNull String todayKey,
                                          @NonNull String yesterdayKey,
                                          int currentRound) {

        // Counted by walking the lists rather than by calling size(), because a null element is skipped
        // everywhere else in this method. Using size() would let a single null make the total disagree
        // with the day counters and the percentage - the totals and the counts have to be the same
        // definition of "a row", or the Dashboard contradicts itself.
        int totalStores = countNonNull(stores);
        int overallCompleted = countNonNull(history);

        int completedToday = 0;
        int completedYesterday = 0;
        CompletionLike latest = null;

        // Which stores are done in the round in progress. Rows from earlier rounds are counted in the
        // lifetime figures above but are ignored here - that is what the round number is for.
        Set<Long> completedThisRound = new HashSet<>();

        if (history != null) {
            for (CompletionLike completion : history) {
                if (completion == null) {
                    continue;
                }

                // Compared as strings. YYYY-MM-DD is the one common date format where plain text equality
                // is also date equality, which is why the key is stored in that shape.
                if (todayKey.equals(completion.completionDateKey())) {
                    completedToday++;
                }
                if (yesterdayKey.equals(completion.completionDateKey())) {
                    completedYesterday++;
                }

                if (completion.roundNumber() == currentRound) {
                    completedThisRound.add(completion.storeId());
                }

                // The newest by timestamp, not by position: the list's order is a detail of whichever
                // query produced it, and this must not depend on that.
                if (latest == null
                        || completion.completionTimestamp() > latest.completionTimestamp()) {
                    latest = completion;
                }
            }
        }

        // Counted against the STORES, not against the history. A completion row for a store that no longer
        // exists - which the foreign key makes impossible - must not be able to make the round look
        // further along than it is.
        int completedInRound = 0;
        Set<Long> countedStoreIds = new HashSet<>();
        if (stores != null) {
            for (StoreLike store : stores) {
                if (store == null) {
                    continue;
                }
                // A duplicate id would otherwise be counted twice and could push the round to "complete"
                // while a real store was still unvisited.
                if (!countedStoreIds.add(store.id())) {
                    continue;
                }
                if (completedThisRound.contains(store.id())) {
                    completedInRound++;
                }
            }
        }

        // Cannot go negative. The foreign key guarantees every completion belongs to a real store and the
        // unique index guarantees one per store per round, so this can never exceed the total - but a
        // negative card would be meaningless on screen even if it could.
        int remaining = Math.max(0, totalStores - completedInRound);

        // An empty database is the state the app starts in, so this is an ordinary case rather than an
        // error: no stores means 0.0%, not a division by zero.
        double percentage = totalStores == 0
                ? 0.0
                : (completedInRound * 100.0) / totalStores;

        String latestCode = null;
        long latestTimestamp = 0L;
        if (latest != null) {
            latestCode = storeCodeFor(stores, latest.storeId());
            latestTimestamp = latest.completionTimestamp();
        }

        return new TrackerSnapshot(
                currentRound,
                totalStores,
                completedToday,
                completedYesterday,
                overallCompleted,
                completedInRound,
                remaining,
                percentage,
                latestCode,
                latestTimestamp);
    }

    /**
     * How many non-null elements a list holds.
     *
     * <p>One definition of "a row", used for both totals. A null element is a repository bug rather than
     * data, and it is skipped by every loop in {@link #compute} - so it must not be counted either. The
     * two would otherwise disagree, and the Dashboard would show a total that its own day counters
     * contradict.
     */
    private static int countNonNull(@Nullable List<?> list) {
        if (list == null) {
            return 0;
        }
        int count = 0;
        for (Object element : list) {
            if (element != null) {
                count++;
            }
        }
        return count;
    }

    /**
     * The code of the store a completion belongs to.
     *
     * <p>Returns {@code null} when the store cannot be found, which should be impossible: the foreign key
     * on {@code completion_history} uses {@code onDelete = RESTRICT} (AD-6), so a store with history
     * cannot be deleted. If it ever does happen, the card shows a dash rather than inventing a code -
     * and Phase 6.5's consistency check is what reports it.
     */
    @Nullable
    private static String storeCodeFor(@Nullable List<? extends StoreLike> stores, long storeId) {
        if (stores == null) {
            return null;
        }
        for (StoreLike store : stores) {
            if (store != null && store.id() == storeId) {
                return store.storeCode();
            }
        }
        return null;
    }
}

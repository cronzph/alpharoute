package com.cph.alpharoute.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The Dashboard's arithmetic, on the JVM.
 *
 * <p>Pure Java with no Android and no database (AD-11), which is the whole reason the
 * {@code StoreLike} / {@code CompletionLike} seams exist - the real inputs are Room entities, and
 * importing them here would have made this file untestable without an emulator.
 *
 * <p>The cases that matter most are the ones the screen plan calls out: an empty database must not divide
 * by zero, "today" and "yesterday" must come from the stored date keys rather than from arithmetic on
 * timestamps, and "latest" must be the newest <i>moment</i> rather than whichever row happened to be last
 * in the list.
 */
public class TrackerCalculatorTest {

    private static final String TODAY = "2026-09-27";
    private static final String YESTERDAY = "2026-09-26";
    private static final String LAST_WEEK = "2026-09-20";

    /** The round the tests work in. A second round is added explicitly where it matters. */
    private static final int CURRENT_ROUND = 1;

    // ------------------------------------------------------------------ fakes

    /** A store, with only the three members the calculator may see. */
    private static final class FakeStore implements TrackerCalculator.StoreLike {
        private final long id;
        private final String code;
        private final boolean completed;

        FakeStore(long id, String code, boolean completed) {
            this.id = id;
            this.code = code;
            this.completed = completed;
        }

        @Override
        public long id() {
            return id;
        }

        @NonNull
        @Override
        public String storeCode() {
            return code;
        }

        @Override
        public boolean isCompleted() {
            return completed;
        }
    }

    /** A completion row, with only the members the calculator may see. */
    private static final class FakeCompletion implements TrackerCalculator.CompletionLike {
        private final long storeId;
        private final String dateKey;
        private final long timestamp;
        private final int roundNumber;

        FakeCompletion(long storeId, String dateKey, long timestamp) {
            this(storeId, dateKey, timestamp, CURRENT_ROUND);
        }

        FakeCompletion(long storeId, String dateKey, long timestamp, int roundNumber) {
            this.storeId = storeId;
            this.dateKey = dateKey;
            this.timestamp = timestamp;
            this.roundNumber = roundNumber;
        }

        @Override
        public long storeId() {
            return storeId;
        }

        @NonNull
        @Override
        public String completionDateKey() {
            return dateKey;
        }

        @Override
        public long completionTimestamp() {
            return timestamp;
        }

        @Override
        public int roundNumber() {
            return roundNumber;
        }
    }

    private static List<FakeStore> stores(FakeStore... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    private static List<FakeCompletion> history(FakeCompletion... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    private static TrackerSnapshot compute(List<FakeStore> stores, List<FakeCompletion> history) {
        return TrackerCalculator.compute(stores, history, TODAY, YESTERDAY, CURRENT_ROUND);
    }

    private static TrackerSnapshot compute(List<FakeStore> stores,
                                           List<FakeCompletion> history,
                                           int currentRound) {
        return TrackerCalculator.compute(stores, history, TODAY, YESTERDAY, currentRound);
    }

    // ------------------------------------------------------------------ the empty database

    @Test
    public void anEmptyDatabaseIsAllZeros() {
        // The state the app starts in, so it is an ordinary case rather than an error.
        TrackerSnapshot snapshot = compute(stores(), history());

        assertEquals(0, snapshot.totalStores());
        assertEquals(0, snapshot.completedToday());
        assertEquals(0, snapshot.completedYesterday());
        assertEquals(0, snapshot.overallCompleted());
        assertEquals(0, snapshot.remainingInRound());
        assertEquals(0.0, snapshot.roundPercentage(), 0.0001);
        assertFalse(snapshot.hasStores());
        assertFalse(snapshot.hasLatest());
        assertNull(snapshot.latestStoreCode());
    }

    @Test
    public void anEmptyDatabaseDoesNotDivideByZero() {
        // Stated separately from the test above because it is the specific failure the screen plan warns
        // about: "No divide-by-zero". A NaN or an Infinity here would render as "NaN%" on the card.
        assertEquals("0.0%", compute(stores(), history()).roundPercentageText());
    }

    @Test
    public void nullListsAreTreatedAsEmpty() {
        // Defensive: a repository that has not emitted yet passes null rather than an empty list.
        TrackerSnapshot snapshot = TrackerCalculator.compute(null, null, TODAY, YESTERDAY, CURRENT_ROUND);

        assertEquals(0, snapshot.totalStores());
        assertEquals(0, snapshot.overallCompleted());
        assertEquals(0.0, snapshot.roundPercentage(), 0.0001);
    }

    // ------------------------------------------------------------------ stores with no completions

    @Test
    public void storesWithNoCompletionsAreAllRemaining() {
        TrackerSnapshot snapshot = compute(
                stores(new FakeStore(1, "100001", false), new FakeStore(2, "100002", false)),
                history());

        assertEquals(2, snapshot.totalStores());
        assertEquals(0, snapshot.overallCompleted());
        assertEquals(2, snapshot.remainingInRound());
        assertEquals(0.0, snapshot.roundPercentage(), 0.0001);
    }

    // ------------------------------------------------------------------ the two day counters

    @Test
    public void onlyTodaysRowsCountAsToday() {
        TrackerSnapshot snapshot = compute(
                stores(new FakeStore(1, "A", true), new FakeStore(2, "B", true),
                        new FakeStore(3, "C", true), new FakeStore(4, "D", false)),
                history(
                        new FakeCompletion(1, TODAY, 1_000L),
                        new FakeCompletion(2, TODAY, 2_000L),
                        new FakeCompletion(3, YESTERDAY, 3_000L)));

        assertEquals(2, snapshot.completedToday());
        assertEquals(1, snapshot.completedYesterday());
    }

    @Test
    public void yesterdayIsReadFromTheStoredKeyNotFromArithmetic() {
        // The stored key is the single definition of "which day". Deriving yesterday by subtracting
        // 86,400,000 ms would be wrong across a daylight-saving change, and would also disagree with the
        // key the completion transaction wrote.
        TrackerSnapshot snapshot = compute(
                stores(new FakeStore(1, "A", true)),
                history(new FakeCompletion(1, YESTERDAY, 1_000L)));

        assertEquals(0, snapshot.completedToday());
        assertEquals(1, snapshot.completedYesterday());
    }

    @Test
    public void anOlderDayCountsInNeitherDayButStillCountsOverall() {
        TrackerSnapshot snapshot = compute(
                stores(new FakeStore(1, "A", true)),
                history(new FakeCompletion(1, LAST_WEEK, 1_000L)));

        assertEquals(0, snapshot.completedToday());
        assertEquals(0, snapshot.completedYesterday());
        assertEquals(1, snapshot.overallCompleted());
    }

    // ------------------------------------------------------------------ the totals

    @Test
    public void overallIsEveryHistoryRow() {
        TrackerSnapshot snapshot = compute(
                stores(new FakeStore(1, "A", true), new FakeStore(2, "B", true),
                        new FakeStore(3, "C", true)),
                history(
                        new FakeCompletion(1, TODAY, 1_000L),
                        new FakeCompletion(2, YESTERDAY, 2_000L),
                        new FakeCompletion(3, LAST_WEEK, 3_000L)));

        assertEquals(3, snapshot.overallCompleted());
        assertEquals(3, snapshot.totalStores());
        assertEquals(0, snapshot.remainingInRound());
    }

    @Test
    public void remainingIsTheTotalMinusTheCompleted() {
        TrackerSnapshot snapshot = compute(
                stores(new FakeStore(1, "A", true), new FakeStore(2, "B", true),
                        new FakeStore(3, "C", false), new FakeStore(4, "D", false),
                        new FakeStore(5, "E", false)),
                history(
                        new FakeCompletion(1, TODAY, 1_000L),
                        new FakeCompletion(2, LAST_WEEK, 2_000L)));

        assertEquals(5, snapshot.totalStores());
        assertEquals(2, snapshot.overallCompleted());
        assertEquals(3, snapshot.remainingInRound());
    }

    @Test
    public void remainingIsNeverNegative() {
        // Impossible through the app - the foreign key (AD-6) and the unique index (AD-5) both prevent it -
        // but a negative card would be meaningless, so the clamp is asserted rather than assumed.
        TrackerSnapshot snapshot = compute(
                stores(new FakeStore(1, "A", true)),
                history(
                        new FakeCompletion(1, TODAY, 1_000L),
                        new FakeCompletion(1, TODAY, 2_000L)));

        assertEquals(0, snapshot.remainingInRound());
    }

    // ------------------------------------------------------------------ the percentage

    @Test
    public void thePercentageIsOneDecimalPlace() {
        // Two of five is 40.0, and the screen plan asks for exactly one decimal place.
        TrackerSnapshot snapshot = compute(
                stores(new FakeStore(1, "A", true), new FakeStore(2, "B", true),
                        new FakeStore(3, "C", false), new FakeStore(4, "D", false),
                        new FakeStore(5, "E", false)),
                history(
                        new FakeCompletion(1, TODAY, 1_000L),
                        new FakeCompletion(2, TODAY, 2_000L)));

        assertEquals(40.0, snapshot.roundPercentage(), 0.0001);
        assertEquals("40.0%", snapshot.roundPercentageText());
    }

    @Test
    public void aThirdRoundsToOneDecimalPlace() {
        // 1 of 3 is 33.333… - the rounding must not produce "33.33333333333333%".
        TrackerSnapshot snapshot = compute(
                stores(new FakeStore(1, "A", true), new FakeStore(2, "B", false),
                        new FakeStore(3, "C", false)),
                history(new FakeCompletion(1, TODAY, 1_000L)));

        assertEquals("33.3%", snapshot.roundPercentageText());
    }

    @Test
    public void aFullyCompletedRouteIsOneHundredPercent() {
        TrackerSnapshot snapshot = compute(
                stores(new FakeStore(1, "A", true), new FakeStore(2, "B", true)),
                history(
                        new FakeCompletion(1, TODAY, 1_000L),
                        new FakeCompletion(2, LAST_WEEK, 2_000L)));

        assertEquals(100.0, snapshot.roundPercentage(), 0.0001);
        assertEquals("100.0%", snapshot.roundPercentageText());
    }

    @Test
    public void thePercentageUsesTheLocaleIndependentFormat() {
        // Pinned to Locale.US, so a device with comma decimal separators still renders 40.0% rather than
        // 40,0% - the same rule the rest of the app follows for numbers.
        TrackerSnapshot snapshot = compute(
                stores(new FakeStore(1, "A", true), new FakeStore(2, "B", false)),
                history(new FakeCompletion(1, TODAY, 1_000L)));

        assertTrue("expected a dot separator", snapshot.roundPercentageText().contains("."));
        assertEquals("50.0%", snapshot.roundPercentageText());
    }

    // ------------------------------------------------------------------ latest completed

    @Test
    public void latestIsTheNewestMomentNotTheLastInTheList() {
        // The list order is a detail of whichever query produced it. Picking the last element would make
        // the card depend on that, and the two orders genuinely differ here.
        TrackerSnapshot snapshot = compute(
                stores(new FakeStore(1, "AAA", true), new FakeStore(2, "BBB", true)),
                history(
                        new FakeCompletion(1, TODAY, 9_000L),
                        new FakeCompletion(2, TODAY, 1_000L)));

        assertEquals("AAA", snapshot.latestStoreCode());
        assertEquals(9_000L, snapshot.latestCompletionTimestamp());
        assertTrue(snapshot.hasLatest());
    }

    @Test
    public void latestResolvesTheStoreCodeFromTheStoreList() {
        TrackerSnapshot snapshot = compute(
                stores(new FakeStore(7, "777777", true)),
                history(new FakeCompletion(7, TODAY, 5_000L)));

        assertEquals("777777", snapshot.latestStoreCode());
    }

    @Test
    public void latestIsNullWhenNothingHasBeenCompleted() {
        TrackerSnapshot snapshot = compute(
                stores(new FakeStore(1, "A", false)),
                history());

        assertNull(snapshot.latestStoreCode());
        assertEquals(0L, snapshot.latestCompletionTimestamp());
        assertFalse(snapshot.hasLatest());
    }

    @Test
    public void latestCodeIsNullWhenTheStoreCannotBeFound() {
        // Should be impossible - AD-6 refuses to delete a store that has history - but the card must show
        // a dash rather than invent a code. Phase 6.5's consistency check reports the real problem.
        TrackerSnapshot snapshot = compute(
                stores(new FakeStore(1, "A", false)),
                history(new FakeCompletion(99, TODAY, 5_000L)));

        assertNull(snapshot.latestStoreCode());
        // The moment is still known, so hasLatest() is false only because there is no code to show.
        assertFalse(snapshot.hasLatest());
        assertEquals(1, snapshot.overallCompleted());
    }

    @Test
    public void latestIgnoresNullRows() {
        // A null in the list would be a repository bug, but it must not throw inside a LiveData observer.
        List<FakeCompletion> withNull = new ArrayList<>();
        withNull.add(null);
        withNull.add(new FakeCompletion(1, TODAY, 4_000L));

        TrackerSnapshot snapshot = compute(
                stores(new FakeStore(1, "A", true)),
                withNull);

        assertEquals("A", snapshot.latestStoreCode());
        assertEquals(1, snapshot.overallCompleted());
    }

    // ------------------------------------------------------------------ hasStores

    @Test
    public void hasStoresReflectsTheTotal() {
        assertFalse(compute(Collections.emptyList(), history()).hasStores());
        assertTrue(compute(stores(new FakeStore(1, "A", false)), history()).hasStores());
    }

    // ------------------------------------------------------------------ rounds

    @Test
    public void theCurrentRoundIsReported() {
        assertEquals(1, compute(stores(), history()).currentRound());
        assertEquals(3, compute(stores(), history(), 3).currentRound());
    }

    @Test
    public void roundsFinishedIsOneLessThanTheRoundInProgress() {
        // The round in progress is not finished, so a route that has just begun its third pass has
        // completed two.
        assertEquals(0, compute(stores(), history(), 1).roundsFinished());
        assertEquals(2, compute(stores(), history(), 3).roundsFinished());
    }

    @Test
    public void roundsFinishedIsNeverNegative() {
        // Defensive: a corrupt round number of 0 must not produce -1 on a card.
        assertEquals(0, compute(stores(), history(), 0).roundsFinished());
    }

    @Test
    public void aStoreCompletedInAnEarlierRoundCountsAsRemainingNow() {
        // THE test for rounds. Round 1 is done; round 2 has just begun, so every store is pending again
        // even though its history row still exists. This is what makes "Remaining" able to go back up.
        TrackerSnapshot snapshot = compute(
                stores(new FakeStore(1, "A", false), new FakeStore(2, "B", false)),
                history(
                        new FakeCompletion(1, LAST_WEEK, 1_000L, 1),
                        new FakeCompletion(2, LAST_WEEK, 2_000L, 1)),
                2);

        assertEquals(0, snapshot.completedInRound());
        assertEquals(2, snapshot.remainingInRound());
        assertEquals(0.0, snapshot.roundPercentage(), 0.0001);
        assertFalse(snapshot.isRoundComplete());
    }

    @Test
    public void overallStillCountsEveryRound() {
        // The lifetime figure never goes down when a new round starts - that is the difference between
        // "overall" and "this round".
        TrackerSnapshot snapshot = compute(
                stores(new FakeStore(1, "A", false), new FakeStore(2, "B", false)),
                history(
                        new FakeCompletion(1, LAST_WEEK, 1_000L, 1),
                        new FakeCompletion(2, LAST_WEEK, 2_000L, 1)),
                2);

        assertEquals(2, snapshot.overallCompleted());
        assertEquals(0, snapshot.completedInRound());
    }

    @Test
    public void completedInRoundCountsOnlyThisRoundsRows() {
        // Round 1 and round 2 rows mixed in one list: only round 2's count towards the round in progress.
        TrackerSnapshot snapshot = compute(
                stores(new FakeStore(1, "A", true), new FakeStore(2, "B", false)),
                history(
                        new FakeCompletion(1, LAST_WEEK, 1_000L, 1),
                        new FakeCompletion(1, TODAY, 2_000L, 2)),
                2);

        assertEquals(1, snapshot.completedInRound());
        assertEquals(1, snapshot.remainingInRound());
        assertEquals(2, snapshot.overallCompleted());
    }

    @Test
    public void theDayCountersSpanEveryRound() {
        // "Completed today" is about the DAY, not the round. A completion made today in round 1 is still
        // a completion made today when round 2 is in progress.
        TrackerSnapshot snapshot = compute(
                stores(new FakeStore(1, "A", false)),
                history(new FakeCompletion(1, TODAY, 1_000L, 1)),
                2);

        assertEquals(1, snapshot.completedToday());
        assertEquals(0, snapshot.completedInRound());
    }

    @Test
    public void aRoundIsCompleteWhenEveryStoreIsDone() {
        TrackerSnapshot snapshot = compute(
                stores(new FakeStore(1, "A", true), new FakeStore(2, "B", true)),
                history(
                        new FakeCompletion(1, TODAY, 1_000L, 2),
                        new FakeCompletion(2, TODAY, 2_000L, 2)),
                2);

        assertTrue(snapshot.isRoundComplete());
        assertEquals(0, snapshot.remainingInRound());
        assertEquals("100.0%", snapshot.roundPercentageText());
    }

    @Test
    public void anEmptyRouteIsNeverARoundComplete() {
        // "Every store is done" is vacuously true over nothing, and a round that completed itself before
        // the user added anything would be nonsense.
        assertFalse(compute(stores(), history(), 1).isRoundComplete());
    }

    @Test
    public void aStoreAddedMidRoundMakesTheRoundIncompleteAgain() {
        // The honest consequence of "a round is every store": adding a shop means there is now one more to
        // visit, so the round is open again.
        TrackerSnapshot snapshot = compute(
                stores(new FakeStore(1, "A", true), new FakeStore(2, "B", true),
                        new FakeStore(3, "C", false)),
                history(
                        new FakeCompletion(1, TODAY, 1_000L, 1),
                        new FakeCompletion(2, TODAY, 2_000L, 1)),
                1);

        assertFalse(snapshot.isRoundComplete());
        assertEquals(1, snapshot.remainingInRound());
    }
}

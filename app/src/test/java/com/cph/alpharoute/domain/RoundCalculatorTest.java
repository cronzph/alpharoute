package com.cph.alpharoute.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The round rule: a round is complete when every store has been completed in it.
 *
 * <p>Pure JVM (AD-11). The rule is one sentence, and every test here is a case where a plausible
 * implementation would get it wrong:
 *
 * <ul>
 *   <li>an empty route, where "every store is done" is vacuously true and must not count;</li>
 *   <li>a store added halfway through, which re-opens a round that was about to finish;</li>
 *   <li>a completion from an earlier round, which must not count towards this one;</li>
 *   <li>a completion for a store that no longer exists, which must not make the round look further along
 *       than it is.</li>
 * </ul>
 */
public class RoundCalculatorTest {

    private static final int ROUND_1 = 1;
    private static final int ROUND_2 = 2;

    private static final class FakeStore implements TrackerCalculator.StoreLike {
        private final long id;
        private final String code;

        FakeStore(long id, String code) {
            this.id = id;
            this.code = code;
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
            // The calculator reads the completion rows, not this flag - so it is deliberately always
            // false here, which also proves the calculation does not depend on it.
            return false;
        }
    }

    private static final class FakeCompletion implements TrackerCalculator.CompletionLike {
        private final long storeId;
        private final int roundNumber;

        FakeCompletion(long storeId, int roundNumber) {
            this.storeId = storeId;
            this.roundNumber = roundNumber;
        }

        @Override
        public long storeId() {
            return storeId;
        }

        @NonNull
        @Override
        public String completionDateKey() {
            return "2026-09-27";
        }

        @Override
        public long completionTimestamp() {
            return 1_000L;
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

    // ------------------------------------------------------------------ the empty route

    @Test
    public void anEmptyRouteIsNotComplete() {
        // "Every store is done" is vacuously true over nothing. Without this guard the user could start
        // round 2 of an empty route.
        RoundProgress progress = RoundCalculator.compute(
                Collections.emptyList(), Collections.emptyList(), ROUND_1);

        assertFalse(progress.isComplete());
        assertFalse(progress.canStartNewRound());
        assertEquals(0, progress.totalStores());
        assertEquals(0, progress.completedInRound());
    }

    @Test
    public void aNullStoreListIsNotComplete() {
        assertFalse(RoundCalculator.compute(null, null, ROUND_1).isComplete());
    }

    @Test
    public void anEmptyRouteHasNoPercentage() {
        // The same divide-by-zero guard the Dashboard needs.
        assertEquals(0.0, RoundCalculator.compute(
                Collections.emptyList(), Collections.emptyList(), ROUND_1).percentage(), 0.0001);
    }

    // ------------------------------------------------------------------ a round in progress

    @Test
    public void storesWithNoCompletionsLeaveTheRoundOpen() {
        RoundProgress progress = RoundCalculator.compute(
                stores(new FakeStore(1, "A"), new FakeStore(2, "B")),
                history(),
                ROUND_1);

        assertEquals(2, progress.totalStores());
        assertEquals(0, progress.completedInRound());
        assertEquals(2, progress.remainingInRound());
        assertFalse(progress.isComplete());
    }

    @Test
    public void aPartlyDoneRoundIsNotComplete() {
        RoundProgress progress = RoundCalculator.compute(
                stores(new FakeStore(1, "A"), new FakeStore(2, "B"), new FakeStore(3, "C")),
                history(new FakeCompletion(1, ROUND_1), new FakeCompletion(2, ROUND_1)),
                ROUND_1);

        assertEquals(2, progress.completedInRound());
        assertEquals(1, progress.remainingInRound());
        assertFalse(progress.isComplete());
        assertFalse(progress.canStartNewRound());
    }

    @Test
    public void everyStoreDoneCompletesTheRound() {
        RoundProgress progress = RoundCalculator.compute(
                stores(new FakeStore(1, "A"), new FakeStore(2, "B")),
                history(new FakeCompletion(1, ROUND_1), new FakeCompletion(2, ROUND_1)),
                ROUND_1);

        assertTrue(progress.isComplete());
        assertTrue(progress.canStartNewRound());
        assertEquals(0, progress.remainingInRound());
        assertEquals(100.0, progress.percentage(), 0.0001);
    }

    // ------------------------------------------------------------------ the round scoping

    @Test
    public void aPreviousRoundsCompletionsDoNotCount() {
        // THE test for rounds. Round 1 is finished and round 2 has just begun: every store is pending
        // again, so the round is open even though the history is full.
        RoundProgress progress = RoundCalculator.compute(
                stores(new FakeStore(1, "A"), new FakeStore(2, "B")),
                history(new FakeCompletion(1, ROUND_1), new FakeCompletion(2, ROUND_1)),
                ROUND_2);

        assertEquals(0, progress.completedInRound());
        assertEquals(2, progress.remainingInRound());
        assertFalse(progress.isComplete());
    }

    @Test
    public void thisRoundsCompletionsCountEvenWithOlderOnesPresent() {
        RoundProgress progress = RoundCalculator.compute(
                stores(new FakeStore(1, "A"), new FakeStore(2, "B")),
                history(
                        new FakeCompletion(1, ROUND_1),
                        new FakeCompletion(2, ROUND_1),
                        new FakeCompletion(1, ROUND_2)),
                ROUND_2);

        assertEquals(1, progress.completedInRound());
        assertEquals(1, progress.remainingInRound());
        assertFalse(progress.isComplete());
    }

    @Test
    public void aStoreCompletedTwiceInTheSameRoundIsCountedOnce() {
        // Impossible through the app - the unique index forbids it - but a set is what makes it harmless.
        // Counting rows would report 2 of 2 complete while one store had never been visited.
        RoundProgress progress = RoundCalculator.compute(
                stores(new FakeStore(1, "A"), new FakeStore(2, "B")),
                history(new FakeCompletion(1, ROUND_1), new FakeCompletion(1, ROUND_1)),
                ROUND_1);

        assertEquals(1, progress.completedInRound());
        assertEquals(1, progress.remainingInRound());
        assertFalse(progress.isComplete());
    }

    // ------------------------------------------------------------------ stores added mid-round

    @Test
    public void aStoreAddedMidRoundReopensIt() {
        // The honest consequence of "a round is every store": adding a shop means there is one more to
        // visit, so a round that was about to finish is open again.
        RoundProgress progress = RoundCalculator.compute(
                stores(new FakeStore(1, "A"), new FakeStore(2, "B"), new FakeStore(3, "C")),
                history(new FakeCompletion(1, ROUND_1), new FakeCompletion(2, ROUND_1)),
                ROUND_1);

        assertFalse(progress.isComplete());
        assertEquals(1, progress.remainingInRound());
    }

    // ------------------------------------------------------------------ defensive

    @Test
    public void aCompletionForAMissingStoreDoesNotCount() {
        // Counted against the STORES, not the history. An orphaned row - which the foreign key makes
        // impossible - must not be able to make the round look further along than it is.
        RoundProgress progress = RoundCalculator.compute(
                stores(new FakeStore(1, "A")),
                history(new FakeCompletion(99, ROUND_1)),
                ROUND_1);

        assertEquals(1, progress.totalStores());
        assertEquals(0, progress.completedInRound());
        assertFalse(progress.isComplete());
    }

    @Test
    public void nullRowsAreSkippedRatherThanThrowing() {
        List<FakeStore> withNullStore = new ArrayList<>();
        withNullStore.add(null);
        withNullStore.add(new FakeStore(1, "A"));

        List<FakeCompletion> withNullCompletion = new ArrayList<>();
        withNullCompletion.add(null);
        withNullCompletion.add(new FakeCompletion(1, ROUND_1));

        RoundProgress progress = RoundCalculator.compute(withNullStore, withNullCompletion, ROUND_1);

        assertEquals(1, progress.totalStores());
        assertTrue(progress.isComplete());
    }

    @Test
    public void aDuplicateStoreIdIsCountedOnce() {
        // A duplicate in the list would otherwise push the round to complete while a real store was
        // unvisited.
        RoundProgress progress = RoundCalculator.compute(
                stores(new FakeStore(1, "A"), new FakeStore(1, "A")),
                history(new FakeCompletion(1, ROUND_1)),
                ROUND_1);

        assertEquals(1, progress.totalStores());
        assertTrue(progress.isComplete());
    }

    @Test
    public void theRoundNumberIsReportedBack() {
        assertEquals(7, RoundCalculator.compute(stores(), history(), 7).currentRound());
    }
}

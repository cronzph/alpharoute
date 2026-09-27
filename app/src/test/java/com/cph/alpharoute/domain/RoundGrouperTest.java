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
 * The round report: how many stores, over how many days, and what was done on each.
 *
 * <p>Pure JVM (AD-11). The cases that matter are the ones where a plausible implementation gets the
 * <i>order</i> or the <i>count</i> wrong, because both are invisible in a screenshot: a round that says
 * "4 days" when it was worked on 4 days spread over a month, and a day numbered 1 that is actually the
 * 1st of the month.
 */
public class RoundGrouperTest {

    private static final class FakeCompletion implements TrackerCalculator.CompletionLike {
        private final long storeId;
        private final String dateKey;
        private final int roundNumber;

        FakeCompletion(long storeId, String dateKey, int roundNumber) {
            this.storeId = storeId;
            this.dateKey = dateKey;
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
            return 1_000L;
        }

        @Override
        public int roundNumber() {
            return roundNumber;
        }
    }

    private static List<FakeCompletion> history(FakeCompletion... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    // ------------------------------------------------------------------ the headline case

    @Test
    public void itReportsStoresPerDayAndDaysTaken() {
        // THE case from the request: a round of 4 stores over 2 days, 3 then 1.
        List<RoundReport> reports = RoundGrouper.group(
                history(
                        new FakeCompletion(1, "2026-09-01", 1),
                        new FakeCompletion(2, "2026-09-01", 1),
                        new FakeCompletion(3, "2026-09-01", 1),
                        new FakeCompletion(4, "2026-09-02", 1)),
                4,
                1);

        assertEquals(1, reports.size());
        RoundReport round = reports.get(0);

        assertEquals(1, round.roundNumber());
        assertEquals(4, round.totalStores());
        assertEquals(2, round.daysTaken());
        assertEquals(3, round.days().get(0).count());
        assertEquals(1, round.days().get(1).count());
    }

    @Test
    public void theDaysAreNumberedFromOneAndInDateOrder() {
        // Day 1 is the first day WORKED, not the 1st of the month, and the days come back oldest first so
        // the story reads in the order it happened.
        List<RoundReport> reports = RoundGrouper.group(
                history(
                        new FakeCompletion(1, "2026-09-20", 1),
                        new FakeCompletion(2, "2026-09-18", 1),
                        new FakeCompletion(3, "2026-09-22", 1)),
                3,
                1);

        RoundDay first = reports.get(0).days().get(0);
        assertEquals("2026-09-18", first.dateKey());
        assertEquals(1, first.dayNumber());
        assertEquals("2026-09-20", reports.get(0).days().get(1).dateKey());
        assertEquals(2, reports.get(0).days().get(1).dayNumber());
        assertEquals("2026-09-22", reports.get(0).days().get(2).dateKey());
    }

    @Test
    public void theInputOrderDoesNotChangeTheOutput() {
        // The history arrives in whatever order the query returned. Reversing it must not reorder the days
        // or renumber them.
        List<RoundReport> forwards = RoundGrouper.group(
                history(
                        new FakeCompletion(1, "2026-09-18", 1),
                        new FakeCompletion(2, "2026-09-20", 1)),
                2, 1);

        List<RoundReport> backwards = RoundGrouper.group(
                history(
                        new FakeCompletion(2, "2026-09-20", 1),
                        new FakeCompletion(1, "2026-09-18", 1)),
                2, 1);

        assertEquals(forwards.get(0).days().get(0).dateKey(),
                backwards.get(0).days().get(0).dateKey());
        assertEquals(1, backwards.get(0).days().get(0).dayNumber());
    }

    // ------------------------------------------------------------------ days taken, not calendar span

    @Test
    public void daysTakenCountsDaysWorkedNotTheCalendarSpan() {
        // Worked on the 1st and then the 10th. That is TWO days of work with nine days in between, and
        // "10 days" would be a misleading answer to "how long did it take".
        List<RoundReport> reports = RoundGrouper.group(
                history(
                        new FakeCompletion(1, "2026-09-01", 1),
                        new FakeCompletion(2, "2026-09-10", 1)),
                2, 1);

        assertEquals(2, reports.get(0).daysTaken());
        assertEquals("2026-09-01", reports.get(0).startDateKey());
        assertEquals("2026-09-10", reports.get(0).endDateKey());
    }

    // ------------------------------------------------------------------ multiple rounds

    @Test
    public void roundsComeBackNewestFirst() {
        // The round in progress is what the user is living in, so it leads.
        List<RoundReport> reports = RoundGrouper.group(
                history(
                        new FakeCompletion(1, "2026-09-01", 1),
                        new FakeCompletion(2, "2026-09-05", 2),
                        new FakeCompletion(3, "2026-09-09", 3)),
                3, 3);

        assertEquals(3, reports.size());
        assertEquals(3, reports.get(0).roundNumber());
        assertEquals(2, reports.get(1).roundNumber());
        assertEquals(1, reports.get(2).roundNumber());
    }

    @Test
    public void eachRoundIsCountedSeparately() {
        List<RoundReport> reports = RoundGrouper.group(
                history(
                        new FakeCompletion(1, "2026-09-01", 1),
                        new FakeCompletion(2, "2026-09-01", 1),
                        new FakeCompletion(1, "2026-09-05", 2)),
                2, 2);

        RoundReport roundTwo = reports.get(0);
        RoundReport roundOne = reports.get(1);

        assertEquals(2, roundOne.totalStores());
        assertEquals(1, roundOne.daysTaken());
        assertEquals(1, roundTwo.totalStores());
        assertEquals(1, roundTwo.daysTaken());
    }

    @Test
    public void aStoreCompletedInTwoRoundsAppearsInBoth() {
        // A store is completed once per round, so it legitimately appears twice across the report - once in
        // each round's total.
        List<RoundReport> reports = RoundGrouper.group(
                history(
                        new FakeCompletion(7, "2026-09-01", 1),
                        new FakeCompletion(7, "2026-09-05", 2)),
                1, 2);

        assertEquals(2, reports.size());
        assertEquals(1, reports.get(0).totalStores());
        assertEquals(1, reports.get(1).totalStores());
    }

    // ------------------------------------------------------------------ completeness

    @Test
    public void aRoundThatCoveredTheRouteIsComplete() {
        List<RoundReport> reports = RoundGrouper.group(
                history(
                        new FakeCompletion(1, "2026-09-01", 1),
                        new FakeCompletion(2, "2026-09-01", 1)),
                2, 1);

        assertTrue(reports.get(0).isComplete());
    }

    @Test
    public void theRoundInProgressIsNotComplete() {
        List<RoundReport> reports = RoundGrouper.group(
                history(new FakeCompletion(1, "2026-09-05", 2)),
                5, 2);

        assertFalse(reports.get(0).isComplete());
    }

    @Test
    public void aRoundTheUserMovedPastIsCompleteEvenIfAStoreWasAddedLater() {
        // The user closed round 1 with 2 of 2 done, then added a third store. Round 1's count no longer
        // matches the route size, but round 1 is still finished - they moved on.
        List<RoundReport> reports = RoundGrouper.group(
                history(
                        new FakeCompletion(1, "2026-09-01", 1),
                        new FakeCompletion(2, "2026-09-01", 1)),
                3, 2);

        assertTrue("a round the user moved past is complete", reports.get(0).isComplete());
    }

    @Test
    public void anUnknownRouteSizeMarksNothingComplete() {
        // A total of 0 means "unknown", not "an empty route". Marking complete on an unknown would be a
        // claim the app cannot support.
        List<RoundReport> reports = RoundGrouper.group(
                history(new FakeCompletion(1, "2026-09-01", 1)),
                0, 1);

        assertFalse(reports.get(0).isComplete());
    }

    // ------------------------------------------------------------------ empty and defensive

    @Test
    public void noHistoryGivesNoRounds() {
        assertTrue(RoundGrouper.group(null, 5, 1).isEmpty());
        assertTrue(RoundGrouper.group(Collections.emptyList(), 5, 1).isEmpty());
    }

    @Test
    public void nullRowsAreSkippedRatherThanThrowing() {
        List<FakeCompletion> withNull = new ArrayList<>();
        withNull.add(null);
        withNull.add(new FakeCompletion(1, "2026-09-01", 1));

        List<RoundReport> reports = RoundGrouper.group(withNull, 1, 1);

        assertEquals(1, reports.size());
        assertEquals(1, reports.get(0).totalStores());
    }

    @Test
    public void aRowWithNoDateIsSkippedRatherThanGuessedAt() {
        // Placing it on a day would be a guess with nothing to show it was one.
        List<RoundReport> reports = RoundGrouper.group(
                history(
                        new FakeCompletion(1, "", 1),
                        new FakeCompletion(2, "2026-09-01", 1)),
                2, 1);

        assertEquals(1, reports.get(0).totalStores());
        assertEquals(1, reports.get(0).daysTaken());
    }

    @Test
    public void theSameStoreTwiceInOneRoundIsCountedOnce() {
        // Impossible through the app - the unique index forbids it - but a corrupt row must not inflate the
        // total, because the total is the number the user reads.
        List<RoundReport> reports = RoundGrouper.group(
                history(
                        new FakeCompletion(1, "2026-09-01", 1),
                        new FakeCompletion(1, "2026-09-01", 1)),
                2, 1);

        assertEquals(1, reports.get(0).totalStores());
    }

    @Test
    public void anEmptyRoundReportsNullDatesRatherThanCrashing() {
        RoundReport empty = new RoundReport(9, Collections.emptyList(), false);

        assertNull(empty.startDateKey());
        assertNull(empty.endDateKey());
        assertEquals(0, empty.daysTaken());
        assertEquals(0, empty.totalStores());
        assertFalse(empty.hasWork());
    }

    @Test
    public void theStoreIdsAreCarriedThroughInOrder() {
        // The screen lists which stores were done, so the ids have to survive the grouping - and in the
        // order they were done, which is what dailySequence records.
        List<RoundReport> reports = RoundGrouper.group(
                history(
                        new FakeCompletion(11, "2026-09-01", 1),
                        new FakeCompletion(22, "2026-09-01", 1),
                        new FakeCompletion(33, "2026-09-01", 1)),
                3, 1);

        List<Long> ids = reports.get(0).days().get(0).storeIds();

        assertEquals(Arrays.asList(11L, 22L, 33L), ids);
    }
}

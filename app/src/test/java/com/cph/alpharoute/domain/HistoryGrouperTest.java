package com.cph.alpharoute.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The History screen's grouping and ordering.
 *
 * <p>Pure JVM (AD-11). Two rules are being asserted, and the second is the one that would be easy to get
 * wrong silently:
 *
 * <ol>
 *   <li><b>One group per day</b>, with the count taken from the rows.</li>
 *   <li><b>Newest day first.</b> A history that opened on the oldest day would make the user scroll to
 *       find out what they did today. This is why the sort is explicit rather than left to the order the
 *       query happened to return.</li>
 * </ol>
 */
public class HistoryGrouperTest {

    private static final class FakeCompletion implements TrackerCalculator.CompletionLike {
        private final long storeId;
        private final String dateKey;
        private final long timestamp;

        FakeCompletion(long storeId, String dateKey, long timestamp) {
            this.storeId = storeId;
            this.dateKey = dateKey;
            this.timestamp = timestamp;
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

        /** Always round 1: grouping is by day and the round does not enter into it. */
        @Override
        public int roundNumber() {
            return 1;
        }
    }

    private static List<FakeCompletion> list(FakeCompletion... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    // ------------------------------------------------------------------ the empty cases

    @Test
    public void noHistoryGivesNoGroups() {
        assertTrue(HistoryGrouper.group(Collections.emptyList()).isEmpty());
    }

    @Test
    public void aNullListGivesNoGroups() {
        // A repository that has not emitted yet passes null rather than an empty list.
        assertTrue(HistoryGrouper.group(null).isEmpty());
    }

    // ------------------------------------------------------------------ grouping

    @Test
    public void rowsOnTheSameDayBecomeOneGroup() {
        List<HistoryDay> days = HistoryGrouper.group(list(
                new FakeCompletion(1, "2026-09-25", 1_000L),
                new FakeCompletion(2, "2026-09-25", 2_000L),
                new FakeCompletion(3, "2026-09-25", 3_000L)));

        assertEquals(1, days.size());
        assertEquals("2026-09-25", days.get(0).dateKey());
        assertEquals(3, days.get(0).completedCount());
    }

    @Test
    public void rowsOnDifferentDaysBecomeSeparateGroups() {
        List<HistoryDay> days = HistoryGrouper.group(list(
                new FakeCompletion(1, "2026-09-25", 1_000L),
                new FakeCompletion(2, "2026-09-26", 2_000L),
                new FakeCompletion(3, "2026-09-27", 3_000L)));

        assertEquals(3, days.size());
    }

    @Test
    public void theCountIsTheNumberOfRowsInThatDay() {
        // The screen plan is explicit that the per-day figure comes from the actual rows, never from a
        // stored counter - so this is asserted against rows that are deliberately uneven.
        List<HistoryDay> days = HistoryGrouper.group(list(
                new FakeCompletion(1, "2026-09-25", 1_000L),
                new FakeCompletion(2, "2026-09-26", 2_000L),
                new FakeCompletion(3, "2026-09-26", 3_000L),
                new FakeCompletion(4, "2026-09-26", 4_000L),
                new FakeCompletion(5, "2026-09-26", 5_000L),
                new FakeCompletion(6, "2026-09-26", 6_000L)));

        assertEquals(2, days.size());
        assertEquals("2026-09-26", days.get(0).dateKey());
        assertEquals(5, days.get(0).completedCount());
        assertEquals("2026-09-25", days.get(1).dateKey());
        assertEquals(1, days.get(1).completedCount());
    }

    // ------------------------------------------------------------------ ordering

    @Test
    public void daysAreNewestFirst() {
        // Deliberately supplied out of order, so a pass-through would fail.
        List<HistoryDay> days = HistoryGrouper.group(list(
                new FakeCompletion(1, "2026-09-20", 1_000L),
                new FakeCompletion(2, "2026-09-27", 2_000L),
                new FakeCompletion(3, "2026-09-25", 3_000L)));

        assertEquals("2026-09-27", days.get(0).dateKey());
        assertEquals("2026-09-25", days.get(1).dateKey());
        assertEquals("2026-09-20", days.get(2).dateKey());
    }

    @Test
    public void theOrderDoesNotDependOnTheInputOrder() {
        // The same three days, supplied in two different orders, must produce the same result. Without
        // this, the sort could be right for one query's ordering and wrong for another's.
        List<HistoryDay> ascending = HistoryGrouper.group(list(
                new FakeCompletion(1, "2026-09-20", 1_000L),
                new FakeCompletion(2, "2026-09-25", 2_000L),
                new FakeCompletion(3, "2026-09-27", 3_000L)));

        List<HistoryDay> descending = HistoryGrouper.group(list(
                new FakeCompletion(3, "2026-09-27", 3_000L),
                new FakeCompletion(2, "2026-09-25", 2_000L),
                new FakeCompletion(1, "2026-09-20", 1_000L)));

        assertEquals(ascending.size(), descending.size());
        for (int i = 0; i < ascending.size(); i++) {
            assertEquals(ascending.get(i).dateKey(), descending.get(i).dateKey());
            assertEquals(ascending.get(i).completedCount(), descending.get(i).completedCount());
        }
    }

    @Test
    public void aMonthBoundarySortsCorrectlyAsText() {
        // The zero padding is what makes this work. Without it, "2026-9-30" would sort after "2026-10-01".
        List<HistoryDay> days = HistoryGrouper.group(list(
                new FakeCompletion(1, "2026-09-30", 1_000L),
                new FakeCompletion(2, "2026-10-01", 2_000L)));

        assertEquals("2026-10-01", days.get(0).dateKey());
        assertEquals("2026-09-30", days.get(1).dateKey());
    }

    @Test
    public void aYearBoundarySortsCorrectlyAsText() {
        List<HistoryDay> days = HistoryGrouper.group(list(
                new FakeCompletion(1, "2026-12-31", 1_000L),
                new FakeCompletion(2, "2027-01-01", 2_000L)));

        assertEquals("2027-01-01", days.get(0).dateKey());
        assertEquals("2026-12-31", days.get(1).dateKey());
    }

    // ------------------------------------------------------------------ defensive

    @Test
    public void nullRowsAreSkippedRatherThanThrowing() {
        List<FakeCompletion> withNull = new ArrayList<>();
        withNull.add(null);
        withNull.add(new FakeCompletion(1, "2026-09-25", 1_000L));

        List<HistoryDay> days = HistoryGrouper.group(withNull);

        assertEquals(1, days.size());
        assertEquals(1, days.get(0).completedCount());
    }

    @Test
    public void aRowWithNoDayIsSkippedRatherThanGuessed() {
        // Inventing "today" would put the row in the wrong group and hide the real problem. Phase 6.5's
        // consistency check is what reports it.
        List<HistoryDay> days = HistoryGrouper.group(list(
                new FakeCompletion(1, "", 1_000L),
                new FakeCompletion(2, "2026-09-25", 2_000L)));

        assertEquals(1, days.size());
        assertEquals("2026-09-25", days.get(0).dateKey());
        assertEquals(1, days.get(0).completedCount());
    }
}

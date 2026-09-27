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
 * The consistency checker, on the JVM.
 *
 * <p>Pure Java (AD-11). The point of testing this at all is that a real inconsistency is invisible: nothing
 * crashes, no error is logged, and the only symptom is a number that is quietly wrong. So each of the four
 * faults is provoked deliberately here, and the healthy cases are asserted too - a checker that reported
 * something on a healthy database would be worse than no checker, because the user would learn to ignore
 * it.
 */
public class ConsistencyCheckerTest {

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

    private static final class FakeCompletion implements TrackerCalculator.CompletionLike {
        private final long storeId;
        private final int roundNumber;

        FakeCompletion(long storeId) {
            this(storeId, CURRENT_ROUND);
        }

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

    /** The round the tests work in. */
    private static final int CURRENT_ROUND = 1;

    /**
     * The checker, with the round pinned.
     *
     * <p>A helper rather than repeating the round in seventeen calls: the round is a parameter of the
     * checker, not something any individual test is exercising, and only the tests that are <i>about</i>
     * rounds pass a different one.
     */
    private static List<ConsistencyChecker.Finding> check(
            List<FakeStore> stores, List<FakeCompletion> history, int drawnMarkerCount) {
        return ConsistencyChecker.check(stores, history, drawnMarkerCount, CURRENT_ROUND);
    }

    private static List<FakeStore> stores(FakeStore... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    private static List<FakeCompletion> history(FakeCompletion... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    private static boolean has(List<ConsistencyChecker.Finding> findings,
                               ConsistencyChecker.Issue issue) {
        for (ConsistencyChecker.Finding finding : findings) {
            if (finding.issue() == issue) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ the healthy cases

    @Test
    public void aHealthyDatabaseReportsNothing() {
        // The most important test in this file. A checker that cries wolf is one the user learns to ignore.
        List<ConsistencyChecker.Finding> findings = check(
                stores(new FakeStore(1, "A", true), new FakeStore(2, "B", false)),
                history(new FakeCompletion(1)),
                2);

        assertTrue("expected no findings, got " + findings, findings.isEmpty());
    }

    @Test
    public void anEmptyDatabaseReportsNothing() {
        assertTrue(check(Collections.emptyList(), Collections.emptyList(), 0).isEmpty());
    }

    @Test
    public void aPendingStoreWithNoHistoryIsHealthy() {
        // The ordinary case: a store waiting to be visited.
        assertTrue(check(
                stores(new FakeStore(1, "A", false)), history(), 1).isEmpty());
    }

    @Test
    public void aCompletedStoreWithHistoryIsHealthy() {
        assertTrue(check(
                stores(new FakeStore(1, "A", true)), history(new FakeCompletion(1)), 1).isEmpty());
    }

    @Test
    public void anUnknownMarkerCountIsNotChecked() {
        // -1 means the map has never been opened, so its marker count is unknown. Treating that as zero
        // would report a mismatch against every store.
        assertTrue(check(
                stores(new FakeStore(1, "A", false)), history(), -1).isEmpty());
    }

    // ------------------------------------------------------------------ fault 1

    @Test
    public void aCompletedStoreWithNoHistoryIsReported() {
        List<ConsistencyChecker.Finding> findings = check(
                stores(new FakeStore(1, "A", true)),
                history(),
                -1);

        assertTrue(has(findings, ConsistencyChecker.Issue.COMPLETED_WITHOUT_HISTORY));
        assertEquals(1, findings.size());
    }

    @Test
    public void theFindingNamesTheStoreItIsAbout() {
        // A finding the user cannot locate is not actionable.
        List<ConsistencyChecker.Finding> findings = check(
                stores(new FakeStore(42, "999888", true)),
                history(),
                -1);

        assertEquals(42L, findings.get(0).storeId());
        assertTrue("expected the code in the detail", findings.get(0).detail().contains("999888"));
    }

    // ------------------------------------------------------------------ fault 2

    @Test
    public void aHistoryRowForAPendingStoreIsReported() {
        // The store is counted but still offered as somewhere to visit - the opposite disagreement.
        List<ConsistencyChecker.Finding> findings = check(
                stores(new FakeStore(1, "A", false)),
                history(new FakeCompletion(1)),
                -1);

        assertTrue(has(findings, ConsistencyChecker.Issue.HISTORY_WITHOUT_COMPLETION));
    }

    @Test
    public void bothDirectionsCanBeReportedAtOnce() {
        // Two different stores, each wrong in a different direction.
        List<ConsistencyChecker.Finding> findings = check(
                stores(new FakeStore(1, "A", true), new FakeStore(2, "B", false)),
                history(new FakeCompletion(2)),
                -1);

        assertTrue(has(findings, ConsistencyChecker.Issue.COMPLETED_WITHOUT_HISTORY));
        assertTrue(has(findings, ConsistencyChecker.Issue.HISTORY_WITHOUT_COMPLETION));
        assertEquals(2, findings.size());
    }

    // ------------------------------------------------------------------ fault 3

    @Test
    public void aHistoryRowWithNoStoreIsReportedAsOrphaned() {
        // The foreign key (AD-6) should make this impossible. If it appears, the constraint is not being
        // enforced - see the PRAGMA foreign_keys note on AppDatabase.
        List<ConsistencyChecker.Finding> findings = check(
                stores(),
                history(new FakeCompletion(99)),
                -1);

        assertTrue(has(findings, ConsistencyChecker.Issue.ORPHANED_HISTORY));
        assertEquals(99L, findings.get(0).storeId());
    }

    @Test
    public void anOrphanIsNotAlsoReportedAsHistoryWithoutCompletion() {
        // One fault, one finding. Reporting the same row twice would inflate the count and make the report
        // harder to act on.
        List<ConsistencyChecker.Finding> findings = check(
                stores(),
                history(new FakeCompletion(99)),
                -1);

        assertEquals(1, findings.size());
    }

    // ------------------------------------------------------------------ fault 4

    @Test
    public void aMarkerCountMismatchIsReported() {
        List<ConsistencyChecker.Finding> findings = check(
                stores(new FakeStore(1, "A", false), new FakeStore(2, "B", false)),
                history(),
                1);

        assertTrue(has(findings, ConsistencyChecker.Issue.MARKER_COUNT_MISMATCH));
        assertEquals(-1L, findings.get(0).storeId());
    }

    @Test
    public void aMatchingMarkerCountIsNotReported() {
        assertTrue(check(
                stores(new FakeStore(1, "A", false), new FakeStore(2, "B", false)),
                history(),
                2).isEmpty());
    }

    @Test
    public void theMismatchDetailCarriesBothNumbers() {
        // "1 drawn, 2 stored" is actionable; "mismatch" is not.
        List<ConsistencyChecker.Finding> findings = check(
                stores(new FakeStore(1, "A", false), new FakeStore(2, "B", false)),
                history(),
                1);

        String detail = findings.get(0).detail();
        assertTrue("expected the drawn count in: " + detail, detail.contains("1"));
        assertTrue("expected the stored count in: " + detail, detail.contains("2"));
    }

    // ------------------------------------------------------------------ defensive

    @Test
    public void nullInputsAreTreatedAsEmpty() {
        assertTrue(check(null, null, -1).isEmpty());
    }

    @Test
    public void nullRowsAreSkippedRatherThanThrowing() {
        List<FakeStore> withNullStore = new ArrayList<>();
        withNullStore.add(null);
        withNullStore.add(new FakeStore(1, "A", false));

        List<FakeCompletion> withNullCompletion = new ArrayList<>();
        withNullCompletion.add(null);

        assertTrue(check(withNullStore, withNullCompletion, 1).isEmpty());
    }

    @Test
    public void theCheckIsLinearAndDoesNotDuplicateFindings() {
        // A store completed with no history, checked against a larger set, must produce exactly one
        // finding - not one per store it was compared against.
        List<FakeStore> manyStores = new ArrayList<>();
        manyStores.add(new FakeStore(1, "A", true));
        for (long id = 2; id <= 20; id++) {
            manyStores.add(new FakeStore(id, "S" + id, false));
        }

        List<ConsistencyChecker.Finding> findings =
                check(manyStores, history(), -1);

        assertEquals(1, findings.size());
        assertEquals(1L, findings.get(0).storeId());
    }

    // ------------------------------------------------------------------ rounds

    @Test
    public void aPendingStoreWithHistoryFromAnOlderRoundIsHealthy() {
        // THE round test for this checker. This is exactly what starting a new round produces: the store
        // is PENDING and its completion is filed under round 1. An unscoped check would report every store
        // in the route as inconsistent the moment the second round began.
        List<ConsistencyChecker.Finding> findings = ConsistencyChecker.check(
                stores(new FakeStore(1, "A", false)),
                history(new FakeCompletion(1, 1)),
                -1,
                2);

        assertTrue("expected no findings, got " + findings, findings.isEmpty());
    }

    @Test
    public void aCompletedStoreWithHistoryOnlyInAnOlderRoundIsReported() {
        // The other side of the same coin: the store says COMPLETED but has no completion in the round
        // being worked through, so the map and the count disagree.
        List<ConsistencyChecker.Finding> findings = ConsistencyChecker.check(
                stores(new FakeStore(1, "A", true)),
                history(new FakeCompletion(1, 1)),
                -1,
                2);

        assertTrue(has(findings, ConsistencyChecker.Issue.COMPLETED_WITHOUT_HISTORY));
        assertEquals(1, findings.size());
    }

    @Test
    public void anOrphanFromAnOlderRoundIsStillReported() {
        // A completion pointing at a store that does not exist is wrong whatever round it belongs to -
        // it is the fault the foreign key should make impossible.
        List<ConsistencyChecker.Finding> findings = ConsistencyChecker.check(
                stores(),
                history(new FakeCompletion(99, 1)),
                -1,
                2);

        assertTrue(has(findings, ConsistencyChecker.Issue.ORPHANED_HISTORY));
    }
}

package com.cph.alpharoute.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Working out what a cloud restore would do, before it does it.
 *
 * <p>Pure JVM. This file exists because the preview is a promise: the user reads "2 stores will be added"
 * and then decides whether to let it happen. If the number is wrong, the confirmation dialog is decoration
 * and the user has been taught to tap through it - which is worse than having no dialog at all.
 *
 * <p>So every case below mirrors a branch of {@code BackupTransactionDao.importAll}, and the two must
 * agree. Where they could drift, the test says which line of the transaction it is pinning.
 */
public class CloudSyncSummaryTest {

    private static CloudSyncSummary.CompletionKey completion(String storeCode, int round) {
        return new CloudSyncSummary.CompletionKey(storeCode, round);
    }

    private static Set<String> codes(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }

    private static Set<CloudSyncSummary.CompletionKey> keys(
            CloudSyncSummary.CompletionKey... values) {
        return new HashSet<>(Arrays.asList(values));
    }

    private static List<String> list(String... values) {
        return new ArrayList<>(Arrays.asList(values));
    }

    // ------------------------------------------------------------------ stores

    @Test
    public void aStoreOnlyInTheCloudWouldBeAdded() {
        CloudSyncSummary summary = CloudSyncSummary.of(
                list("B"), Collections.emptyList(), codes("A"), Collections.emptySet());

        assertEquals(1, summary.storesNew());
        assertEquals(0, summary.storesAlreadyHere());
    }

    @Test
    public void aStoreAlreadyOnThePhoneWouldBeKeptAsItIs() {
        // importAll: "The app's copy wins. It may have been completed, edited or moved since the backup,
        // and overwriting it with the older version would silently undo that."
        CloudSyncSummary summary = CloudSyncSummary.of(
                list("A"), Collections.emptyList(), codes("A"), Collections.emptySet());

        assertEquals(0, summary.storesNew());
        assertEquals(1, summary.storesAlreadyHere());
    }

    @Test
    public void aRepeatedStoreCodeInTheDocumentIsCountedOnce() {
        // The stores table has a unique storeCode, so a well-formed document cannot do this - but counting
        // it twice would inflate the preview on a document that is merely odd rather than impossible.
        CloudSyncSummary summary = CloudSyncSummary.of(
                list("B", "B"), Collections.emptyList(), Collections.emptySet(), Collections.emptySet());

        assertEquals(1, summary.storesNew());
        assertEquals(1, summary.cloudStoreCount());
    }

    // ------------------------------------------------------------------ completions

    @Test
    public void aCompletionForANewStoreWouldBeAdded() {
        // The store arrives in the same merge, so its completion has something to attach to. This is the
        // case that makes a restore useful at all.
        CloudSyncSummary summary = CloudSyncSummary.of(
                list("B"),
                Collections.singletonList(completion("B", 1)),
                Collections.emptySet(),
                Collections.emptySet());

        assertEquals(1, summary.storesNew());
        assertEquals(1, summary.completionsNew());
        assertEquals(0, summary.completionsUnresolved());
    }

    @Test
    public void aCompletionForAStoreAlreadyHereIsStillAddedWhenThatRoundIsMissing() {
        // The store is kept as it is - but it has never been completed in round 1 on this phone, so the
        // cloud's record of that completion is new information. "The app's copy wins" is about the store
        // row, not about suppressing its history.
        CloudSyncSummary summary = CloudSyncSummary.of(
                list("A"),
                Collections.singletonList(completion("A", 1)),
                codes("A"),
                Collections.emptySet());

        assertEquals(0, summary.storesNew());
        assertEquals(1, summary.completionsNew());
    }

    @Test
    public void aCompletionAlreadyCountedOnThisPhoneIsSkipped() {
        // importAll: "Already counted in that round. The unique index would refuse it anyway."
        CloudSyncSummary summary = CloudSyncSummary.of(
                list("A"),
                Collections.singletonList(completion("A", 1)),
                codes("A"),
                keys(completion("A", 1)));

        assertEquals(0, summary.completionsNew());
        assertEquals(1, summary.completionsAlreadyHere());
    }

    @Test
    public void theSameStoreInADifferentRoundIsANewCompletion() {
        // A round is part of the identity. Round 2's visit to a store is not round 1's.
        CloudSyncSummary summary = CloudSyncSummary.of(
                list("A"),
                Collections.singletonList(completion("A", 2)),
                codes("A"),
                keys(completion("A", 1)));

        assertEquals(1, summary.completionsNew());
    }

    @Test
    public void aCompletionForAStoreThatIsNowhereIsReportedAsUnresolved() {
        // The validator refuses a document like this, so a non-zero count means the validator and the
        // transaction disagree. Surfacing it beats quietly dropping records.
        CloudSyncSummary summary = CloudSyncSummary.of(
                Collections.emptyList(),
                Collections.singletonList(completion("GHOST", 1)),
                codes("A"),
                Collections.emptySet());

        assertEquals(0, summary.completionsNew());
        assertEquals(1, summary.completionsUnresolved());
    }

    @Test
    public void aRepeatedCompletionInTheDocumentIsCountedOnce() {
        // importAll checks hasCompletionInRound before inserting, so the second one is skipped - including
        // when the first one came from this same document a moment earlier.
        CloudSyncSummary summary = CloudSyncSummary.of(
                list("B"),
                Arrays.asList(completion("B", 1), completion("B", 1)),
                Collections.emptySet(),
                Collections.emptySet());

        assertEquals(1, summary.completionsNew());
        assertEquals(1, summary.completionsAlreadyHere());
    }

    // ------------------------------------------------------------------ what the screen says

    @Test
    public void anIdenticalPhoneAndCloudHaveNothingToDo() {
        // "Nothing new" is a real answer, and much better than a silent success that changed nothing and
        // looks like a failure.
        CloudSyncSummary summary = CloudSyncSummary.of(
                list("A", "B"),
                Arrays.asList(completion("A", 1), completion("B", 1)),
                codes("A", "B"),
                keys(completion("A", 1), completion("B", 1)));

        assertTrue(summary.nothingNew());
        assertEquals(0, summary.storesNew());
        assertEquals(0, summary.completionsNew());
    }

    @Test
    public void theCloudTotalsDescribeTheWholeDocument() {
        // What the document holds, not what would be added - the preview shows both, and confusing the two
        // is how "12 stores" gets read as "12 new stores".
        CloudSyncSummary summary = CloudSyncSummary.of(
                list("A", "B", "C"),
                Arrays.asList(completion("A", 1), completion("B", 1), completion("C", 1)),
                codes("A", "B"),
                keys(completion("A", 1)));

        assertEquals(3, summary.cloudStoreCount());
        assertEquals(3, summary.cloudCompletionCount());
        assertEquals(1, summary.storesNew());
        assertEquals(2, summary.completionsNew());
        assertFalse(summary.nothingNew());
    }

    @Test
    public void anEmptyDocumentAddsNothing() {
        CloudSyncSummary summary = CloudSyncSummary.of(
                Collections.emptyList(),
                Collections.emptyList(),
                codes("A"),
                keys(completion("A", 1)));

        assertTrue(summary.nothingNew());
        assertEquals(0, summary.cloudStoreCount());
        assertEquals(0, summary.cloudCompletionCount());
    }

    @Test
    public void anEmptyPhoneTakesEverything() {
        // The new-phone case: nothing here, so every store and every completion is new.
        CloudSyncSummary summary = CloudSyncSummary.of(
                list("A", "B"),
                Arrays.asList(completion("A", 1), completion("A", 2), completion("B", 1)),
                Collections.emptySet(),
                Collections.emptySet());

        assertEquals(2, summary.storesNew());
        assertEquals(3, summary.completionsNew());
        assertEquals(0, summary.completionsUnresolved());
    }
}

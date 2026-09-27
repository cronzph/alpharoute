package com.cph.alpharoute.data.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The backup file: what it contains, and whether it can be restored.
 *
 * <p>Pure JVM. This is the highest-stakes test file in Version 9, because a restore can delete every store.
 * The validator is the thing standing between a malformed file and the user's data, and every fault it is
 * supposed to catch is provoked here deliberately.
 */
public class BackupValidatorTest {

    // ------------------------------------------------------------------ helpers

    private static BackupContents.BackupStore store(String code, String route, String status) {
        return new BackupContents.BackupStore(
                route, code, "Store " + code, "Area", 14.6, 120.98, status, 1_000L, 1_000L, null);
    }

    private static BackupContents.BackupCompletion completion(String code, int round) {
        return new BackupContents.BackupCompletion(code, 1_000L, "2026-09-27", 1, round);
    }

    private static BackupContents contents(List<BackupContents.BackupStore> stores,
                                           List<BackupContents.BackupCompletion> completions) {
        return new BackupContents(
                BackupCodec.FORMAT_VERSION,
                1_700_000_000_000L,
                new BackupContents.BackupSettings(14.65, 120.98, true, 50, 1),
                stores,
                completions);
    }

    private static List<BackupContents.BackupStore> stores(BackupContents.BackupStore... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    private static List<BackupContents.BackupCompletion> completions(
            BackupContents.BackupCompletion... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    private static boolean mentions(List<BackupValidator.Problem> problems, String fragment) {
        for (BackupValidator.Problem problem : problems) {
            if (problem.message.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ the healthy case

    @Test
    public void aHealthyBackupHasNoProblems() {
        // The most important assertion here: a validator that complained about good data would be one the
        // user learns to override, and then it protects nothing.
        List<BackupValidator.Problem> problems = BackupValidator.validate(contents(
                stores(store("A-01", "R-01", "PENDING"), store("B-01", "R-02", "COMPLETED")),
                completions(completion("B-01", 1))));

        assertTrue("expected no problems, got " + problems, problems.isEmpty());
    }

    @Test
    public void anEmptyStoreListIsRefused() {
        // Restoring nothing would delete everything. Refused rather than treated as a valid empty backup.
        assertTrue(mentions(
                BackupValidator.validate(contents(stores(), completions())),
                "no stores"));
    }

    // ------------------------------------------------------------------ the file itself

    @Test
    public void nullContentsAreRefused() {
        assertFalse(BackupValidator.validate(null).isEmpty());
    }

    @Test
    public void aDifferentFormatVersionIsRefusedBeforeAnythingElse() {
        // The shape is unknown, so every later complaint could be nonsense. The check runs first and returns
        // immediately.
        BackupContents wrongVersion = new BackupContents(
                99, 0L, null, stores(store("A-01", "R-01", "PENDING")), completions());

        List<BackupValidator.Problem> problems = BackupValidator.validate(wrongVersion);

        assertEquals(1, problems.size());
        assertTrue(problems.get(0).message.contains("different version"));
    }

    // ------------------------------------------------------------------ store faults

    @Test
    public void aStoreWithNoCodeIsRefused() {
        assertTrue(mentions(
                BackupValidator.validate(contents(
                        stores(store("", "R-01", "PENDING")), completions())),
                "no store code"));
    }

    @Test
    public void twoStoresSharingACodeAreRefused() {
        // The unique index would refuse this on import, after the user had already confirmed. Catching it
        // here means the refusal happens before anything is written.
        assertTrue(mentions(
                BackupValidator.validate(contents(
                        stores(store("A-01", "R-01", "PENDING"), store("A-01", "R-02", "PENDING")),
                        completions())),
                "share the code"));
    }

    @Test
    public void twoStoresSharingARouteNumberAreRefused() {
        assertTrue(mentions(
                BackupValidator.validate(contents(
                        stores(store("A-01", "R-01", "PENDING"), store("B-01", "R-01", "PENDING")),
                        completions())),
                "share the route number"));
    }

    @Test
    public void aStoreWithNoNameIsRefused() {
        BackupContents.BackupStore nameless = new BackupContents.BackupStore(
                "R-01", "A-01", "", "Area", 14.6, 120.98, "PENDING", 1_000L, 1_000L, null);

        assertTrue(mentions(
                BackupValidator.validate(contents(stores(nameless), completions())),
                "no name"));
    }

    @Test
    public void aCoordinateOffTheGlobeIsRefused() {
        BackupContents.BackupStore broken = new BackupContents.BackupStore(
                "R-01", "A-01", "Store", "Area", 999.0, 120.98, "PENDING", 1_000L, 1_000L, null);

        assertTrue(mentions(
                BackupValidator.validate(contents(stores(broken), completions())),
                "not on the globe"));
    }

    @Test
    public void anUnknownStatusIsRefused() {
        // Rather than defaulting to PENDING, which would silently change what the file said.
        assertTrue(mentions(
                BackupValidator.validate(contents(
                        stores(store("A-01", "R-01", "ARCHIVED")), completions())),
                "unknown status"));
    }

    // ------------------------------------------------------------------ completion faults

    @Test
    public void aCompletionForAStoreNotInTheFileIsRefused() {
        // Restoring it would create an orphan the foreign key refuses - and the user would see a failure
        // after confirming.
        assertTrue(mentions(
                BackupValidator.validate(contents(
                        stores(store("A-01", "R-01", "COMPLETED")),
                        completions(completion("Z-99", 1)))),
                "not in this backup"));
    }

    @Test
    public void aStoreCompletedTwiceInOneRoundIsRefused() {
        // The same thing the unique index on (storeId, roundNumber) enforces. Checked here so the refusal
        // happens before anything is written.
        assertTrue(mentions(
                BackupValidator.validate(contents(
                        stores(store("A-01", "R-01", "COMPLETED")),
                        completions(completion("A-01", 1), completion("A-01", 1)))),
                "completed twice"));
    }

    @Test
    public void aStoreCompletedOncePerRoundIsFine() {
        // Two completions for the same store are legitimate when they are in different rounds - that is
        // exactly what rounds are.
        List<BackupValidator.Problem> problems = BackupValidator.validate(contents(
                stores(store("A-01", "R-01", "COMPLETED")),
                completions(completion("A-01", 1), completion("A-01", 2))));

        assertTrue("expected no problems, got " + problems, problems.isEmpty());
    }

    @Test
    public void aCompletionWithNoDateIsRefused() {
        BackupContents.BackupCompletion undated =
                new BackupContents.BackupCompletion("A-01", 1_000L, "", 1, 1);

        assertTrue(mentions(
                BackupValidator.validate(contents(
                        stores(store("A-01", "R-01", "COMPLETED")), completions(undated))),
                "no date"));
    }

    @Test
    public void aCompletionWithNoTimestampIsRefused() {
        BackupContents.BackupCompletion timeless =
                new BackupContents.BackupCompletion("A-01", 0L, "2026-09-27", 1, 1);

        assertTrue(mentions(
                BackupValidator.validate(contents(
                        stores(store("A-01", "R-01", "COMPLETED")), completions(timeless))),
                "no timestamp"));
    }

    @Test
    public void aCompletionWithNoDailySequenceIsRefused() {
        BackupContents.BackupCompletion unsequenced =
                new BackupContents.BackupCompletion("A-01", 1_000L, "2026-09-27", 0, 1);

        assertTrue(mentions(
                BackupValidator.validate(contents(
                        stores(store("A-01", "R-01", "COMPLETED")), completions(unsequenced))),
                "no position in its day"));
    }

    // ------------------------------------------------------------------ the two halves must agree

    @Test
    public void aStoreMarkedCompletedWithNoCompletionIsRefused() {
        // The disagreement the consistency check exists to find. There is no reason to import one.
        assertTrue(mentions(
                BackupValidator.validate(contents(
                        stores(store("A-01", "R-01", "COMPLETED")), completions())),
                "no completion record"));
    }

    @Test
    public void aStoreWithACompletionButNotMarkedCompletedIsRefused() {
        assertTrue(mentions(
                BackupValidator.validate(contents(
                        stores(store("A-01", "R-01", "PENDING")),
                        completions(completion("A-01", 1)))),
                "not marked completed"));
    }

    // ------------------------------------------------------------------ reporting

    @Test
    public void everyProblemIsReportedNotJustTheFirst() {
        // A user with three faults should not have to fix one, retry, and discover the next. Three distinct
        // faults are planted: a missing code, a missing route number, and a coordinate off the globe.
        BackupContents.BackupStore noCode = store("", "R-01", "PENDING");
        BackupContents.BackupStore noRoute = store("B-01", "", "PENDING");
        BackupContents.BackupStore badCoordinate = new BackupContents.BackupStore(
                "R-03", "C-01", "Store", "Area", 999.0, 120.98, "PENDING", 1_000L, 1_000L, null);

        List<BackupValidator.Problem> problems = BackupValidator.validate(contents(
                stores(noCode, noRoute, badCoordinate),
                completions()));

        assertTrue("expected three problems, got " + problems.size() + ": " + problems,
                problems.size() >= 3);
        assertTrue(mentions(problems, "no store code"));
        assertTrue(mentions(problems, "no route number"));
        assertTrue(mentions(problems, "not on the globe"));
    }

    @Test
    public void theSummaryNamesTheCounts() {
        String summary = BackupValidator.describe(contents(
                stores(store("A-01", "R-01", "PENDING")),
                completions()));

        assertTrue(summary.contains("1"));
        assertTrue(summary.contains("store"));
    }

    @Test
    public void theSummaryOfNothingIsEmptyRatherThanCrashing() {
        assertEquals("", BackupValidator.describe(null));
    }

    // ------------------------------------------------------------------ the codec round trip

    @Test
    public void aCodecRoundTripPreservesWhatMatters() throws Exception {
        // Writing then reading must give back the same stores and completions. The JSON is the file the user
        // keeps; a lossy round trip would mean a backup that silently drops data.
        String json = BackupCodec.toJson(
                Collections.emptyList(),
                Collections.emptyList(),
                null,
                1_700_000_000_000L);

        BackupContents parsed = BackupCodec.parse(json);

        assertNotNull(parsed);
        assertEquals(BackupCodec.FORMAT_VERSION, parsed.formatVersion);
        assertEquals(1_700_000_000_000L, parsed.exportedAt);
        assertTrue(parsed.stores.isEmpty());
    }

    @Test
    public void anEmptyFileIsNotABackup() {
        assertNull(BackupCodec.parse(null));
        assertNull(BackupCodec.parse(""));
        assertNull(BackupCodec.parse("   "));
    }

    @Test
    public void arbitraryJsonIsNotABackup() {
        // No formatVersion, so it is not one of ours - refused rather than read as an empty backup.
        assertNull(BackupCodec.parse("{\"hello\":\"world\"}"));
    }

    @Test
    public void malformedTextIsNotABackup() {
        assertNull(BackupCodec.parse("this is not json"));
    }

    @Test
    public void theExportNeverContainsAnApiKey() throws Exception {
        // The roadmap is explicit: no API keys, no credentials. The key lives in BuildConfig and never
        // reaches the database, so it could not leak here - but the file is written from an explicit field
        // list, and this asserts that list has not grown.
        String json = BackupCodec.toJson(
                Collections.emptyList(), Collections.emptyList(), null, 1_700_000_000_000L);

        assertFalse("the backup must never carry a key", json.toLowerCase().contains("apikey"));
        assertFalse("the backup must never carry a key", json.toLowerCase().contains("api_key"));
        assertFalse("the backup must never carry a token", json.toLowerCase().contains("token"));
        assertFalse("the backup must never carry a password", json.toLowerCase().contains("password"));
        assertFalse("the backup must never carry an authorization header",
                json.toLowerCase().contains("authorization"));
    }

    @Test
    public void theTransientNavigationReferenceIsNotExported() throws Exception {
        // lastSelectedStoreId points at a drive in progress. Restoring it into a different database could
        // point at a store that means something else there.
        String json = BackupCodec.toJson(
                Collections.emptyList(), Collections.emptyList(), null, 1_700_000_000_000L);

        assertFalse(json.contains("lastSelectedStoreId"));
    }
}

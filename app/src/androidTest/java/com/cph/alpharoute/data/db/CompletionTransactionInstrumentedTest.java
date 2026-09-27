package com.cph.alpharoute.data.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.cph.alpharoute.domain.StoreStatus;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.ZoneId;
import java.util.List;

/**
 * The completion transaction and the round change, on real SQLite.
 *
 * <p><b>This is the test the JVM suite cannot write.</b> Every rule in the transaction - the unique index,
 * the foreign key, the round scoping - is enforced by <i>SQLite</i>, and a JVM test with a fake DAO would
 * only prove that the fake behaves as the test expects. Room's own schema validation, the {@code PRAGMA
 * foreign_keys = ON} callback and the actual constraint behaviour are all reached for the first time here.
 *
 * <p>It runs against an <b>in-memory database</b>, so it is fast, leaves nothing behind, and cannot touch
 * the user's data. That also means it exercises the real migrations path for a fresh install rather than an
 * upgrade - the upgrade path is 7.0 §15.1's manual test.
 *
 * <p>Runs with {@code ./gradlew connectedDebugAndroidTest} on a device or emulator. It is in
 * {@code androidTest} rather than {@code test} precisely because it cannot run anywhere else.
 */
@RunWith(AndroidJUnit4.class)
public class CompletionTransactionInstrumentedTest {

    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");

    /** 2026-09-27 12:00 in Manila. A fixed instant, so the assertions are readable. */
    private static final long NOON = 1_790_553_600_000L;

    private AppDatabase database;
    private StoreDao storeDao;
    private CompletionHistoryDao historyDao;
    private CompletionTransactionDao completionDao;
    private RoundTransactionDao roundDao;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();

        storeDao = database.storeDao();
        historyDao = database.completionHistoryDao();
        completionDao = database.completionTransactionDao();
        roundDao = database.roundTransactionDao();

        // The settings row must exist: the round lives there, and a missing row would make every round
        // number default to 1 in a way the app never does.
        AppSettingEntity settings = new AppSettingEntity();
        settings.id = AppSettingEntity.SINGLETON_ID;
        settings.arrivalRadius = 50;
        settings.currentRound = 1;
        settings.lastUpdated = NOON;
        database.appSettingDao().upsert(settings);
    }

    @After
    public void tearDown() {
        database.close();
    }

    // ------------------------------------------------------------------ helpers

    private StoreEntity insertStore(String code) {
        StoreEntity store = new StoreEntity();
        store.routeNumber = "R-" + code;
        store.storeCode = code;
        store.storeName = "Store " + code;
        store.area = "Manila";
        store.latitude = 14.6;
        store.longitude = 120.98;
        store.status = StoreStatus.PENDING;
        store.createdAt = NOON;
        store.updatedAt = NOON;
        long id = storeDao.insert(store);
        store.id = id;
        return store;
    }

    // ------------------------------------------------------------------ the completion contract

    @Test
    public void completingAStoreWritesOneRowAndFlipsTheStore() {
        StoreEntity store = insertStore("A-01");

        CompletionOutcome outcome = completionDao.complete(store.id, NOON, MANILA, 1);

        assertTrue(outcome.isSuccess());
        assertEquals(1, outcome.dailySequence());
        assertEquals("2026-09-27", outcome.completionDateKey());

        assertEquals(1, historyDao.countAll());
        assertEquals(StoreStatus.COMPLETED, storeDao.findById(store.id).status);
        assertNotNull(storeDao.findById(store.id).completedAt);
    }

    @Test
    public void completingTheSameStoreTwiceInOneRoundIsRefused() {
        // The unique index on (storeId, roundNumber) is what makes this impossible, and this is the only
        // place it is actually exercised.
        StoreEntity store = insertStore("A-01");

        assertEquals(CompletionOutcome.Status.SUCCESS,
                completionDao.complete(store.id, NOON, MANILA, 1).status());
        assertEquals(CompletionOutcome.Status.ALREADY_COUNTED,
                completionDao.complete(store.id, NOON + 1_000L, MANILA, 1).status());

        assertEquals("still exactly one row", 1, historyDao.countAll());
    }

    @Test
    public void completingAMissingStoreIsRefused() {
        assertEquals(CompletionOutcome.Status.STORE_NOT_FOUND,
                completionDao.complete(9_999L, NOON, MANILA, 1).status());
        assertEquals(0, historyDao.countAll());
    }

    @Test
    public void theDailySequenceCountsWithinTheDay() {
        StoreEntity first = insertStore("A-01");
        StoreEntity second = insertStore("B-01");

        assertEquals(1, completionDao.complete(first.id, NOON, MANILA, 1).dailySequence());
        assertEquals(2, completionDao.complete(second.id, NOON + 60_000L, MANILA, 1).dailySequence());
    }

    @Test
    public void aStoreCompletedInAnEarlierRoundCanBeCompletedAgain() {
        // The whole point of rounds, and the reason AD-5 was scoped rather than removed.
        StoreEntity store = insertStore("A-01");

        assertEquals(CompletionOutcome.Status.SUCCESS,
                completionDao.complete(store.id, NOON, MANILA, 1).status());

        // A new round: the store goes back to PENDING.
        RoundOutcome round = roundDao.startNewRound(NOON + 1_000L);
        assertTrue(round.isSuccess());
        assertEquals(2, round.newRound());
        assertEquals(StoreStatus.PENDING, storeDao.findById(store.id).status);
        assertNull(storeDao.findById(store.id).completedAt);

        // And it can be completed again, in round 2.
        assertEquals(CompletionOutcome.Status.SUCCESS,
                completionDao.complete(store.id, NOON + 2_000L, MANILA, 2).status());

        assertEquals("one row per round", 2, historyDao.countAll());
    }

    // ------------------------------------------------------------------ the round change

    @Test
    public void aRoundCannotStartBeforeItIsFinished() {
        insertStore("A-01");
        insertStore("B-01");

        // Nothing completed yet, so two stores are outstanding.
        RoundOutcome outcome = roundDao.startNewRound(NOON);

        assertEquals(RoundOutcome.Status.ROUND_NOT_COMPLETE, outcome.status());
        assertEquals(2, outcome.stillToVisit());
        assertEquals("the round must not have advanced", 1, roundDao.readCurrentRound());
    }

    @Test
    public void aRoundStartsOnceEveryStoreIsDone() {
        StoreEntity first = insertStore("A-01");
        StoreEntity second = insertStore("B-01");

        completionDao.complete(first.id, NOON, MANILA, 1);
        completionDao.complete(second.id, NOON + 1_000L, MANILA, 1);

        RoundOutcome outcome = roundDao.startNewRound(NOON + 2_000L);

        assertTrue(outcome.isSuccess());
        assertEquals(2, outcome.newRound());
        assertEquals(2, outcome.storesReset());
        assertEquals(2, roundDao.readCurrentRound());
    }

    @Test
    public void anEmptyRouteCannotStartARound() {
        // "Every store is complete" is vacuously true over nothing, so this guard is what stops the user
        // starting round 2 of an empty route.
        assertEquals(RoundOutcome.Status.NO_STORES, roundDao.startNewRound(NOON).status());
    }

    @Test
    public void startingARoundDeletesNothing() {
        StoreEntity store = insertStore("A-01");
        completionDao.complete(store.id, NOON, MANILA, 1);

        roundDao.startNewRound(NOON + 1_000L);

        // The history survives. That is what lets the History screen show past rounds at all.
        assertEquals(1, historyDao.countAll());
        assertNotNull(historyDao.findByStoreId(store.id));
    }

    @Test
    public void aStoreAddedMidRoundReopensIt() {
        // The honest consequence of "a round is every store".
        StoreEntity first = insertStore("A-01");
        completionDao.complete(first.id, NOON, MANILA, 1);

        assertEquals(0, roundDao.outstandingInCurrentRound());

        insertStore("B-01");

        assertEquals(1, roundDao.outstandingInCurrentRound());
        assertEquals(RoundOutcome.Status.ROUND_NOT_COMPLETE, roundDao.startNewRound(NOON).status());
    }

    // ------------------------------------------------------------------ the undo

    @Test
    public void undoingPutsTheStoreBackAndLetsItBeCompletedAgain() {
        StoreEntity store = insertStore("A-01");
        completionDao.complete(store.id, NOON, MANILA, 1);

        CompletionHistoryEntity row = historyDao.findByStoreId(store.id);
        assertNotNull(row);

        CompletionOutcome undo = completionDao.undo(row.id, 1, NOON + 1_000L);

        assertTrue(undo.isSuccess());
        assertEquals(0, historyDao.countAll());
        assertEquals(StoreStatus.PENDING, storeDao.findById(store.id).status);
        assertNull("completedAt must be NULL, not 0", storeDao.findById(store.id).completedAt);

        // And the unique index allows it again, because the row is gone.
        assertEquals(CompletionOutcome.Status.SUCCESS,
                completionDao.complete(store.id, NOON + 2_000L, MANILA, 1).status());
        assertEquals(1, historyDao.countAll());
    }

    @Test
    public void undoingAnAlreadyGoneCompletionIsReportedNotThrown() {
        assertEquals(CompletionOutcome.Status.COMPLETION_NOT_FOUND,
                completionDao.undo(9_999L, 1, NOON).status());
    }

    @Test
    public void undoingAnOlderRoundsCompletionLeavesTheCurrentStatusAlone() {
        // A store is green because of its CURRENT-round completion. Removing an old round's row must not
        // turn it orange.
        StoreEntity store = insertStore("A-01");
        completionDao.complete(store.id, NOON, MANILA, 1);

        CompletionHistoryEntity roundOneRow = historyDao.findByStoreId(store.id);
        roundDao.startNewRound(NOON + 1_000L);
        completionDao.complete(store.id, NOON + 2_000L, MANILA, 2);

        // Undo round 1's row while round 2 is current.
        CompletionOutcome undo = completionDao.undo(roundOneRow.id, 2, NOON + 3_000L);

        assertTrue(undo.isSuccess());
        assertEquals(1, historyDao.countAll());
        assertEquals("round 2's completion still stands",
                StoreStatus.COMPLETED, storeDao.findById(store.id).status);
    }

    // ------------------------------------------------------------------ the foreign key (AD-6)

    @Test
    public void aCompletedStoreCannotBeDeleted() {
        // The one guarantee that depends on PRAGMA foreign_keys being ON. If that line is ever removed from
        // AppDatabase, this test fails - which is the only place it would be noticed.
        StoreEntity store = insertStore("A-01");
        completionDao.complete(store.id, NOON, MANILA, 1);

        try {
            storeDao.deleteById(store.id);
            // No exception means the constraint did not fire.
            assertEquals("the store must still exist", 1, storeDao.countAll());
        } catch (android.database.sqlite.SQLiteConstraintException expected) {
            // Correct: RESTRICT refused it.
            assertEquals(1, storeDao.countAll());
        }
    }

    @Test
    public void anUncompletedStoreCanBeDeleted() {
        StoreEntity store = insertStore("A-01");

        storeDao.deleteById(store.id);

        assertEquals(0, storeDao.countAll());
    }

    // ------------------------------------------------------------------ the date key

    @Test
    public void theDateKeyUsesTheLocalDay() {
        // 2026-09-27 12:00 in Manila. The key is the local calendar day, not the UTC one.
        StoreEntity store = insertStore("A-01");

        CompletionOutcome outcome = completionDao.complete(store.id, NOON, MANILA, 1);

        assertEquals("2026-09-27", outcome.completionDateKey());
    }

    @Test
    public void theHistoryIsOrderedNewestDayFirst() {
        StoreEntity first = insertStore("A-01");
        completionDao.complete(first.id, NOON, MANILA, 1);

        List<CompletionHistoryEntity> all = historyDao.getAllNow();

        assertEquals(1, all.size());
        assertEquals(1, all.get(0).roundNumber);
    }
}

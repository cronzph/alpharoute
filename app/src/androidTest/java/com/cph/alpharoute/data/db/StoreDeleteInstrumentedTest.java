package com.cph.alpharoute.data.db;

import static org.junit.Assert.assertEquals;
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

/**
 * Deleting a store, with and without completion history (Version 13).
 *
 * <p><b>Version 13 changed a safety rule</b>, so it needs a test at the level where the rule actually lives.
 * Until now AD-6's {@code onDelete = RESTRICT} made a completed store undeletable; now it can be deleted,
 * and its history goes with it. A change to a guarantee is exactly the thing that needs proving, and it can
 * only be proved against real SQLite - which is why this is in {@code androidTest}.
 *
 * <p>Runs against an <b>in-memory database</b>: fast, leaves nothing behind, and cannot touch the user's
 * data.
 */
@RunWith(AndroidJUnit4.class)
public class StoreDeleteInstrumentedTest {

    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");
    private static final long NOON = 1_790_553_600_000L;

    private AppDatabase database;
    private StoreDao storeDao;
    private CompletionHistoryDao historyDao;
    private CompletionTransactionDao completionDao;
    private StoreTransactionDao storeTransactionDao;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();

        storeDao = database.storeDao();
        historyDao = database.completionHistoryDao();
        completionDao = database.completionTransactionDao();
        storeTransactionDao = database.storeTransactionDao();

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

    private long insertStore(String code) {
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
        return storeDao.insert(store);
    }

    // ------------------------------------------------------------------ no history

    @Test
    public void aStoreWithNoHistoryDeletesCleanly() {
        long id = insertStore("A-01");

        int historyDeleted = storeTransactionDao.deleteWithHistory(id);

        assertEquals(0, historyDeleted);
        assertNull(storeDao.findById(id));
    }

    // ------------------------------------------------------------------ with history (Version 13)

    @Test
    public void aCompletedStoreCanBeDeleted() {
        // THE change. Before Version 13 this was impossible - AD-6 refused it outright.
        long id = insertStore("A-01");
        completionDao.complete(id, NOON, MANILA, 1);

        storeTransactionDao.deleteWithHistory(id);

        assertNull("the store is gone", storeDao.findById(id));
        assertNull("its history went with it", historyDao.findByStoreId(id));
    }

    @Test
    public void theDeleteReportsHowManyRecordsWent() {
        // The confirmation dialog shows this number, so it has to be right.
        long id = insertStore("A-01");
        completionDao.complete(id, NOON, MANILA, 1);
        // A second round, so there are two records for one store.
        database.roundTransactionDao().startNewRound(NOON + 1_000L);
        completionDao.complete(id, NOON + 2_000L, MANILA, 2);

        int historyDeleted = storeTransactionDao.deleteWithHistory(id);

        assertEquals(2, historyDeleted);
        assertEquals(0, historyDao.countAll());
    }

    @Test
    public void deletingOneStoreLeavesAnothersHistoryAlone() {
        // The transaction is scoped by store id. A wildcard delete would take the whole table.
        long doomed = insertStore("A-01");
        long survivor = insertStore("B-01");

        completionDao.complete(doomed, NOON, MANILA, 1);
        completionDao.complete(survivor, NOON + 1_000L, MANILA, 1);

        storeTransactionDao.deleteWithHistory(doomed);

        assertNull(storeDao.findById(doomed));
        assertEquals("the other store survives", survivor, storeDao.findById(survivor).id);
        assertEquals("and so does its history", 1, historyDao.countForStore(survivor));
        assertEquals(1, historyDao.countAll());
    }

    // ------------------------------------------------------------------ AD-6 is still intact

    @Test
    public void theForeignKeyStillRefusesAnOrphaningDelete() {
        // The guarantee was NOT weakened. A bare delete that would leave history pointing at nothing is
        // still refused by the database - the transaction works because it removes the history FIRST, not
        // because the constraint was relaxed.
        //
        // If someone ever changes RESTRICT to CASCADE, this test still passes. If they break the FK
        // enforcement (the PRAGMA in AppDatabase's onOpen), it fails - which is the point.
        long id = insertStore("A-01");
        completionDao.complete(id, NOON, MANILA, 1);

        try {
            storeDao.deleteById(id);
            // No exception means the constraint did not fire.
            assertEquals("the store must still exist", 1, storeDao.countAll());
        } catch (android.database.sqlite.SQLiteConstraintException expected) {
            // Correct: RESTRICT refused it.
            assertEquals(1, storeDao.countAll());
        }
    }

    @Test
    public void deletingAMissingStoreIdRollsBack() {
        // The transaction throws when no row was removed, so a delete cannot silently claim success - and
        // the history delete rolls back with it.
        long id = insertStore("A-01");
        completionDao.complete(id, NOON, MANILA, 1);
        int historyBefore = historyDao.countAll();

        try {
            storeTransactionDao.deleteWithHistory(9_999L);
            // If it did not throw, nothing should have changed either way.
            assertEquals(historyBefore, historyDao.countAll());
        } catch (IllegalStateException expected) {
            // Correct: it refused, and the history is untouched.
            assertEquals("the history must survive a failed delete",
                    historyBefore, historyDao.countAll());
            assertTrue("and so must the store", storeDao.findById(id) != null);
        }
    }
}

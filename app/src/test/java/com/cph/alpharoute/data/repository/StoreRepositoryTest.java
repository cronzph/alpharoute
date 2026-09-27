package com.cph.alpharoute.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.cph.alpharoute.data.db.StoreEntity;
import com.cph.alpharoute.domain.StoreStatus;

import org.junit.Test;

/**
 * Unit tests for the rule {@link StoreRepository#preserveUneditableFields} enforces - that editing a
 * store can never change what it is, only what it says.
 *
 * <p>These run on the JVM with no emulator and no database. The repository's write path needs Room and
 * therefore cannot be tested here, but this one rule can be, and it is the rule most worth testing:
 * everything else in an edit fails loudly (a duplicate code shows an inline error, a missing store shows
 * a message), whereas this one fails <b>silently</b>.
 *
 * <p><b>What is being protected.</b> {@code StoreDao.update} is a Room {@code @Update}, which replaces
 * the entire row. An edit form that fills in the four text fields and leaves the rest of the entity at
 * its Java defaults would write {@code PENDING} and {@code null} over a completed store's status and
 * completion time. The result is a store that is orange on the map, green on the Dashboard, and counted
 * in the history - a contradiction nothing in the app could detect or repair. So the repository copies
 * those fields back from the stored row before every update, and these tests hold that line.
 */
public class StoreRepositoryTest {

    private static final long STORE_ID = 7L;
    private static final long CREATED_AT = 1_700_000_000_000L;
    private static final long COMPLETED_AT = 1_700_000_500_000L;

    private static final double LATITUDE = 14.5995;
    private static final double LONGITUDE = 120.9842;

    /** A store row, built the way Room would materialise one. */
    private static StoreEntity store(long id,
                                     String routeNumber,
                                     String storeCode,
                                     String storeName,
                                     String area,
                                     StoreStatus status,
                                     long createdAt,
                                     Long completedAt) {
        StoreEntity store = new StoreEntity();
        store.id = id;
        store.routeNumber = routeNumber;
        store.storeCode = storeCode;
        store.storeName = storeName;
        store.area = area;
        store.latitude = LATITUDE;
        store.longitude = LONGITUDE;
        store.status = status;
        store.createdAt = createdAt;
        store.updatedAt = createdAt;
        store.completedAt = completedAt;
        return store;
    }

    /** A completed store as it sits in the database. */
    private static StoreEntity completedStoredRow() {
        return store(STORE_ID, "R-01", "123456", "Alfamart Sample Street", "Sample Barangay",
                StoreStatus.COMPLETED, CREATED_AT, COMPLETED_AT);
    }

    /**
     * The edit form's entity: the four text fields the user changed, and nothing else.
     *
     * <p>This is the shape the form actually builds - a bare {@code StoreEntity} with the id, the edited
     * text and the coordinates. Every other field is left at its Java default, which is precisely the
     * situation the rule exists to make safe.
     */
    private static StoreEntity editedFormEntity() {
        StoreEntity store = new StoreEntity();
        store.id = STORE_ID;
        store.routeNumber = "R-99";
        store.storeCode = "999999";
        store.storeName = "Renamed Store";
        store.area = "Renamed Area";
        store.latitude = LATITUDE;
        store.longitude = LONGITUDE;
        return store;
    }

    // ------------------------------------------------------------------ the headline rule

    @Test
    public void editingACompletedStoreDoesNotMakeItPending() {
        StoreEntity incoming = editedFormEntity();
        StoreEntity stored = completedStoredRow();

        // The form never set a status, so the entity says PENDING - and it is about to be written over
        // the whole row.
        assertEquals(StoreStatus.PENDING, incoming.status);

        StoreRepository.preserveUneditableFields(incoming, stored);

        assertEquals("A completed store must stay completed through an edit",
                StoreStatus.COMPLETED, incoming.status);
    }

    @Test
    public void theCompletionTimeSurvivesAnEdit() {
        StoreEntity incoming = editedFormEntity();
        assertNull("The form does not know about the completion time", incoming.completedAt);

        StoreRepository.preserveUneditableFields(incoming, completedStoredRow());

        assertNotNull("Losing this would leave a completed store with no completion time",
                incoming.completedAt);
        assertEquals(COMPLETED_AT, (long) incoming.completedAt);
    }

    @Test
    public void theCreatedTimeSurvivesAnEdit() {
        StoreEntity incoming = editedFormEntity();
        assertEquals("A fresh entity starts at zero", 0L, incoming.createdAt);

        StoreRepository.preserveUneditableFields(incoming, completedStoredRow());

        assertEquals("When the store was first pinned must not be rewritten by an edit",
                CREATED_AT, incoming.createdAt);
    }

    @Test
    public void aPendingStoreKeepsANullCompletionTimeRatherThanZero() {
        // The other half of the rule. A pending store's completedAt is genuinely absent, and writing 0
        // would claim it was completed at the epoch - which is exactly the sentinel the schema forbids.
        StoreEntity stored = store(STORE_ID, "R-02", "222222", "Still Pending", "Sample Barangay",
                StoreStatus.PENDING, CREATED_AT, null);
        StoreEntity incoming = editedFormEntity();

        StoreRepository.preserveUneditableFields(incoming, stored);

        assertEquals(StoreStatus.PENDING, incoming.status);
        assertNull("A pending store must keep a null completion time, never 0", incoming.completedAt);
    }

    @Test
    public void theStoredIdentityWins() {
        // Defensive: the lookup was made with the incoming id, so in practice these agree. Copying the
        // stored id anyway means a malformed entity cannot write its changes onto a different row.
        StoreEntity incoming = editedFormEntity();
        StoreEntity stored = completedStoredRow();

        StoreRepository.preserveUneditableFields(incoming, stored);

        assertEquals(STORE_ID, incoming.id);
    }

    // ------------------------------------------------------------------ what an edit IS allowed to change

    @Test
    public void theEditedTextIsKept() {
        // The rule must protect the store's identity without freezing its content, or the edit form
        // would have nothing to do.
        StoreEntity incoming = editedFormEntity();

        StoreRepository.preserveUneditableFields(incoming, completedStoredRow());

        assertEquals("R-99", incoming.routeNumber);
        assertEquals("999999", incoming.storeCode);
        assertEquals("Renamed Store", incoming.storeName);
        assertEquals("Renamed Area", incoming.area);
    }

    @Test
    public void theCoordinatesAreKept() {
        // Coordinates are shown read-only on the form and travel with it, so they are part of what the
        // caller supplies - not something to be overwritten from the stored row. This test records that
        // boundary, so a later phase that lets a store be re-pinned knows exactly where to change it.
        StoreEntity incoming = editedFormEntity();
        incoming.latitude = 14.6001;
        incoming.longitude = 120.9850;

        StoreRepository.preserveUneditableFields(incoming, completedStoredRow());

        assertEquals(14.6001, incoming.latitude, 0.0);
        assertEquals(120.9850, incoming.longitude, 0.0);
    }
}

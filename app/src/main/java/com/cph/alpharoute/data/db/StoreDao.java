package com.cph.alpharoute.data.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.cph.alpharoute.domain.StoreStatus;

import java.util.List;

/**
 * Database access for {@link StoreEntity}.
 *
 * <p><b>Ordering.</b> Queries return rows in insertion order ({@code ORDER BY id}). Route numbers are
 * free text, so sorting them in SQL would be lexicographic and would put "10" before "9". Any order
 * that needs numeric awareness is a presentation concern and belongs in a ViewModel, where it can be
 * unit-tested.
 *
 * <p><b>LiveData vs plain values.</b> The {@code observe*} methods return {@link LiveData} for the UI
 * to watch - Room re-runs them automatically whenever the {@code stores} table changes, so a marker or
 * a list can never go stale. The {@code *Now} methods return plain values and must only be called from
 * a background thread ({@code AppExecutors.diskIO()}); calling them on the main thread will throw.
 *
 * <p><b>Single-row lookups</b> return a bare entity, not a list. The unique indices on
 * {@code storeCode} and {@code routeNumber} mean there can only ever be one match.
 */
@Dao
public interface StoreDao {

    // ---------------------------------------------------------------- writes

    /** @return the new row id. */
    @Insert
    long insert(StoreEntity store);

    /** @return the new row ids, in the same order as the input list. */
    @Insert
    List<Long> insertAll(List<StoreEntity> stores);

    /** @return the number of rows updated. */
    @Update
    int update(StoreEntity store);

    /** @return the number of rows deleted. Zero for a completed store - see AD-6. */
    @Delete
    int delete(StoreEntity store);

    /** @return the number of rows deleted. Zero for a completed store - see AD-6. */
    @Query("DELETE FROM stores WHERE id = :storeId")
    int deleteById(long storeId);

    // ---------------------------------------------------------------- reads

    @Query("SELECT * FROM stores WHERE id = :storeId")
    StoreEntity findById(long storeId);

    @Query("SELECT * FROM stores WHERE storeCode = :storeCode LIMIT 1")
    StoreEntity findByCode(String storeCode);

    @Query("SELECT * FROM stores WHERE routeNumber = :routeNumber LIMIT 1")
    StoreEntity findByRouteNumber(String routeNumber);

    @Query("SELECT * FROM stores ORDER BY id ASC")
    LiveData<List<StoreEntity>> observeAll();

    @Query("SELECT * FROM stores WHERE status = :status ORDER BY id ASC")
    LiveData<List<StoreEntity>> observeByStatus(StoreStatus status);

    @Query("SELECT * FROM stores ORDER BY id ASC")
    List<StoreEntity> getAllNow();

    @Query("SELECT * FROM stores WHERE status = :status ORDER BY id ASC")
    List<StoreEntity> getByStatusNow(StoreStatus status);

    @Query("SELECT COUNT(*) FROM stores")
    int countAll();

    @Query("SELECT COUNT(*) FROM stores WHERE status = :status")
    int countByStatus(StoreStatus status);

    /**
     * True when the code is already taken by a different store.
     *
     * <p>{@code :excludeStoreId} lets an edit check "taken by someone else" rather than "taken by
     * anyone", so a store being edited does not collide with itself. Pass {@code -1} when creating.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM stores WHERE storeCode = :storeCode AND id <> :excludeStoreId)")
    boolean isStoreCodeTaken(String storeCode, long excludeStoreId);

    /** As {@link #isStoreCodeTaken}, for the route number. */
    @Query("SELECT EXISTS(SELECT 1 FROM stores WHERE routeNumber = :routeNumber AND id <> :excludeStoreId)")
    boolean isRouteNumberTaken(String routeNumber, long excludeStoreId);
}

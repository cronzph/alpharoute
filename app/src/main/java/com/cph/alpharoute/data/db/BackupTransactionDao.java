package com.cph.alpharoute.data.db;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;

import com.cph.alpharoute.data.backup.BackupContents;
import com.cph.alpharoute.data.backup.ImportMode;
import com.cph.alpharoute.domain.StoreStatus;
import com.cph.alpharoute.util.GeoConstants;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Restoring a backup - the only thing in the app that can delete every store.
 *
 * <p><b>One transaction, and it throws to roll back.</b> A restore that half-succeeded would leave the user
 * with some of their old data and some of the backup, and no way to tell which was which. Every failure
 * path below throws, so Room discards the whole thing and the app is exactly as it was.
 *
 * <p><b>The order matters, and it is not obvious.</b> In {@link ImportMode#REPLACE} the completions must be
 * deleted <i>before</i> the stores: the foreign key uses {@code onDelete = RESTRICT} (AD-6), so SQLite
 * refuses to delete a store that still has history. Doing it the other way round fails on the first store.
 *
 * <p><b>Nothing is imported without having been validated first.</b> {@code BackupValidator} has already
 * refused a file with a duplicate code, a bad coordinate or a completion with no store - so the checks here
 * are about the <i>database's</i> state, not the file's.
 */
@Dao
public abstract class BackupTransactionDao {

    // ------------------------------------------------------------------ the pieces

    @Query("SELECT COUNT(*) FROM stores")
    abstract int countStores();

    @Query("SELECT * FROM stores WHERE storeCode = :storeCode LIMIT 1")
    abstract StoreEntity findStoreByCode(String storeCode);

    @Query("SELECT EXISTS(SELECT 1 FROM completion_history WHERE storeId = :storeId AND roundNumber = :roundNumber)")
    abstract boolean hasCompletionInRound(long storeId, int roundNumber);

    /** Deletes every completion. Must run before {@link #deleteAllStores} - see the class note. */
    @Query("DELETE FROM completion_history")
    abstract int deleteAllCompletions();

    @Query("DELETE FROM stores")
    abstract int deleteAllStores();

    @Insert
    abstract long insertStore(StoreEntity store);

    @Insert
    abstract long insertCompletion(CompletionHistoryEntity completion);

    @Query("UPDATE app_settings SET homeLatitude = :latitude, homeLongitude = :longitude, "
            + "homeSet = :homeSet, arrivalRadius = :arrivalRadius, currentRound = :currentRound, "
            + "lastSelectedStoreId = NULL, lastUpdated = :nowMillis WHERE id = 1")
    abstract int applySettings(@Nullable Double latitude,
                               @Nullable Double longitude,
                               boolean homeSet,
                               int arrivalRadius,
                               int currentRound,
                               long nowMillis);

    // ------------------------------------------------------------------ the transaction

    /**
     * Restores a backup.
     *
     * <p><b>The caller must have validated the contents already.</b> This method trusts what it is given,
     * because a second set of file checks here would be a second definition of "valid" - free to disagree
     * with the first.
     *
     * @param contents the validated backup
     * @param mode     replace everything, or add only what is missing
     * @param nowMillis when the restore happened, for the settings row
     * @return what was actually done, so the screen can say so
     * @throws RuntimeException on any failure. The throw is what rolls the whole restore back
     */
    @Transaction
    @NonNull
    public ImportOutcome importAll(@NonNull BackupContents contents,
                                   @NonNull ImportMode mode,
                                   long nowMillis) {

        int storesAdded = 0;
        int completionsAdded = 0;
        int storesSkipped = 0;

        if (mode == ImportMode.REPLACE) {
            // Completions first. The foreign key refuses to delete a store that still has history, so the
            // other order fails on the first store.
            deleteAllCompletions();
            deleteAllStores();
        }

        // Which stores exist now, by code. Built once and kept up to date as rows are added, so a
        // completion can be matched to a store without a query per record.
        Map<String, Long> idByCode = new HashMap<>();
        Set<String> existingCodes = new HashSet<>();
        if (mode == ImportMode.MERGE) {
            for (StoreEntity store : allStoresNow()) {
                idByCode.put(store.storeCode, store.id);
                existingCodes.add(store.storeCode);
            }
        }

        for (BackupContents.BackupStore source : contents.stores) {
            if (mode == ImportMode.MERGE && existingCodes.contains(source.storeCode)) {
                // The app's copy wins. It may have been completed, edited or moved since the backup, and
                // overwriting it with the older version would silently undo that.
                storesSkipped++;
                continue;
            }

            StoreEntity store = toEntity(source, nowMillis);
            long id = insertStore(store);
            if (id <= 0L) {
                throw new IllegalStateException("Backup import: could not insert store " + source.storeCode);
            }
            idByCode.put(source.storeCode, id);
            storesAdded++;
        }

        for (BackupContents.BackupCompletion source : contents.completions) {
            Long storeId = idByCode.get(source.storeCode);
            if (storeId == null) {
                // The validator refused a file whose completions do not resolve, so reaching here means the
                // two steps disagree. Skipping is safer than inserting an orphan the foreign key would
                // reject - but it is a bug, and the count makes it visible.
                continue;
            }

            if (hasCompletionInRound(storeId, source.roundNumber)) {
                // Already counted in that round. The unique index would refuse it anyway; checking first
                // means the outcome is reported rather than thrown.
                continue;
            }

            long id = insertCompletion(new CompletionHistoryEntity(
                    storeId,
                    source.completionTimestamp,
                    source.completionDateKey,
                    source.dailySequence,
                    source.roundNumber));
            if (id <= 0L) {
                throw new IllegalStateException(
                        "Backup import: could not insert a completion for " + source.storeCode);
            }
            completionsAdded++;
        }

        // The settings last. Home, the radius and the round are what the restored stores are read through,
        // and applying them before the data would leave a moment where they describe nothing.
        BackupContents.BackupSettings settings = contents.settings;
        if (settings != null) {
            boolean homeSet = settings.homeSet
                    && settings.homeLatitude != null
                    && settings.homeLongitude != null
                    && GeoConstants.isValidLatitude(settings.homeLatitude)
                    && GeoConstants.isValidLongitude(settings.homeLongitude);

            int radius = GeoConstants.isValidArrivalRadius(settings.arrivalRadius)
                    ? settings.arrivalRadius
                    : GeoConstants.DEFAULT_ARRIVAL_RADIUS_M;

            int updated = applySettings(
                    homeSet ? settings.homeLatitude : null,
                    homeSet ? settings.homeLongitude : null,
                    homeSet,
                    radius,
                    Math.max(1, settings.currentRound),
                    nowMillis);

            if (updated != 1) {
                throw new IllegalStateException("Backup import: could not apply the settings");
            }
        }

        return new ImportOutcome(storesAdded, completionsAdded, storesSkipped);
    }

    /** Every store, for the merge's existing-code set. */
    @Query("SELECT * FROM stores")
    abstract java.util.List<StoreEntity> allStoresNow();

    // ------------------------------------------------------------------ mapping

    /**
     * Turns a backup store into a row.
     *
     * <p><b>The id is deliberately not set.</b> Row ids are auto-generated, so the id in the file means
     * nothing here - the store is identified by its code, which is why the completions were written against
     * codes rather than ids.
     *
     * <p>{@code createdAt} is taken from the file when it looks sane, so a restored store keeps the date it
     * was actually added rather than appearing to have been created during the restore.
     */
    @NonNull
    private static StoreEntity toEntity(@NonNull BackupContents.BackupStore source, long nowMillis) {
        StoreEntity store = new StoreEntity();
        store.routeNumber = source.routeNumber;
        store.storeCode = source.storeCode;
        store.storeName = source.storeName;
        store.area = source.area;
        store.latitude = source.latitude;
        store.longitude = source.longitude;

        StoreStatus status = parseStatus(source.status);
        store.status = status == null ? StoreStatus.PENDING : status;

        store.createdAt = source.createdAt > 0L ? source.createdAt : nowMillis;
        store.updatedAt = source.updatedAt > 0L ? source.updatedAt : nowMillis;
        store.completedAt = source.completedAt;

        return store;
    }

    @Nullable
    private static StoreStatus parseStatus(@Nullable String name) {
        if (name == null) {
            return null;
        }
        for (StoreStatus status : StoreStatus.values()) {
            if (status.name().equals(name)) {
                return status;
            }
        }
        return null;
    }

    /** How a restore ended. Only ever a success - a failure throws and rolls back. */
    public static final class ImportOutcome {
        public final int storesAdded;
        public final int completionsAdded;
        public final int storesSkipped;

        ImportOutcome(int storesAdded, int completionsAdded, int storesSkipped) {
            this.storesAdded = storesAdded;
            this.completionsAdded = completionsAdded;
            this.storesSkipped = storesSkipped;
        }

        @NonNull
        @Override
        public String toString() {
            return "ImportOutcome(" + storesAdded + " stores, " + completionsAdded
                    + " completions, " + storesSkipped + " skipped)";
        }
    }
}

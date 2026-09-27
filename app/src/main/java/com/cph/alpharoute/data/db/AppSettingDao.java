package com.cph.alpharoute.data.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

/**
 * Database access for {@link AppSettingEntity} - the single settings row.
 *
 * <p>Every method targets {@link AppSettingEntity#SINGLETON_ID}, so there is no way to accidentally
 * read or write a second settings row.
 *
 * <p>{@link #upsert} uses {@code OnConflictStrategy.REPLACE}, which makes it safe to call before the
 * row exists: the first call inserts the defaults, and every later call overwrites them. That is what
 * lets {@code SettingsRepository} guarantee the row exists without a separate "does it exist yet?"
 * branch that could race.
 */
@Dao
public interface AppSettingDao {

    @Query("SELECT * FROM app_settings WHERE id = " + AppSettingEntity.SINGLETON_ID)
    LiveData<AppSettingEntity> observe();

    @Query("SELECT * FROM app_settings WHERE id = " + AppSettingEntity.SINGLETON_ID)
    AppSettingEntity getNow();

    /** Inserts the row if it is missing, replaces it if it is present. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long upsert(AppSettingEntity settings);

    /** @return the number of rows updated (0 or 1). */
    @Update
    int update(AppSettingEntity settings);

    @Query("DELETE FROM app_settings")
    void deleteAll();
}

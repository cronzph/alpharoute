package com.cph.alpharoute.data.db;

import androidx.room.TypeConverter;

import com.cph.alpharoute.domain.StoreStatus;

/**
 * Room type converters.
 *
 * <p>SQLite has no enum type, so {@link StoreStatus} is stored as its {@code name()} - the readable
 * string {@code "PENDING"} or {@code "COMPLETED"} - rather than as an ordinal number. That choice
 * matters: an ordinal would silently change meaning if anyone ever reordered the enum constants or
 * inserted a new one in the middle, and the stored data would quietly become wrong.
 *
 * <p>{@code valueOf} throws {@link IllegalArgumentException} for an unrecognised string. That is
 * deliberate - a status the code does not understand means the database was written by a newer
 * version of the app, and failing loudly is better than silently treating a completed store as
 * pending. Phase 6.5's consistency check is where such a problem would be surfaced to the user.
 */
public class Converters {

    @TypeConverter
    public static String fromStoreStatus(StoreStatus status) {
        return status == null ? null : status.name();
    }

    @TypeConverter
    public static StoreStatus toStoreStatus(String value) {
        return value == null ? null : StoreStatus.valueOf(value);
    }
}

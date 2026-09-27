package com.cph.alpharoute.data.db;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.cph.alpharoute.domain.StoreStatus;
import com.cph.alpharoute.domain.TrackerCalculator;

/**
 * One Alfamart store that the user has pinned on the map.
 *
 * <p><b>Indices - these are load-bearing, not decoration:</b>
 * <ul>
 *   <li>{@code storeCode} is UNIQUE. A duplicate store code is rejected by the database itself, so
 *       the validation in {@code StoreValidator} is a convenience for the user, not the guarantee.</li>
 *   <li>{@code routeNumber} is UNIQUE, for the same reason.</li>
 *   <li>{@code status} is indexed because the map filters pending and completed stores on every
 *       database change.</li>
 * </ul>
 *
 * <p><b>Home is never a row in this table.</b> It lives in {@link AppSettingEntity} and must never
 * appear in any count, marker total or completion rule.
 *
 * <p>Timestamps are epoch milliseconds ({@code long}) in the device's local timezone. The
 * {@code YYYY-MM-DD} key used for daily grouping is derived from {@code completedAt} and stored on
 * {@link CompletionHistoryEntity}, not here.
 */
@Entity(
        tableName = "stores",
        indices = {
                @Index(value = "storeCode", unique = true),
                @Index(value = "routeNumber", unique = true),
                @Index(value = "status")
        }
)
public class StoreEntity implements TrackerCalculator.StoreLike {

    /** Table name, referenced by the DAO queries. */
    public static final String TABLE = "stores";

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    public long id;

    /** Route number as printed on the user's route sheet. Unique. Never invented by the app. */
    @NonNull
    @ColumnInfo(name = "routeNumber")
    public String routeNumber = "";

    /** The store's own code. Unique. Never invented by the app. */
    @NonNull
    @ColumnInfo(name = "storeCode")
    public String storeCode = "";

    @NonNull
    @ColumnInfo(name = "storeName")
    public String storeName = "";

    /** Free-text area / locality. Required, trimmed, at most 60 characters. */
    @NonNull
    @ColumnInfo(name = "area")
    public String area = "";

    /** Latitude of the map point the user long-pressed. */
    @ColumnInfo(name = "latitude")
    public double latitude;

    /** Longitude of the map point the user long-pressed. */
    @ColumnInfo(name = "longitude")
    public double longitude;

    /** PENDING (orange marker) or COMPLETED (green marker). */
    @NonNull
    @ColumnInfo(name = "status")
    public StoreStatus status = StoreStatus.PENDING;

    /** When the store was first saved, epoch millis. */
    @ColumnInfo(name = "createdAt")
    public long createdAt;

    /** When the store row was last edited, epoch millis. */
    @ColumnInfo(name = "updatedAt")
    public long updatedAt;

    /**
     * When the store was completed, epoch millis; {@code null} while it is still pending.
     *
     * <p>Nullable {@code Long} rather than a primitive {@code long}, because "never completed" is a
     * genuinely absent value and a sentinel like {@code 0} would be a lie.
     */
    @Nullable
    @ColumnInfo(name = "completedAt")
    public Long completedAt;

    /** Required by Room. Do not remove. */
    public StoreEntity() {
    }

    /**
     * Convenience constructor for a brand-new PENDING store.
     *
     * <p>{@code @Ignore} tells Room to ignore this constructor when it decides how to build the
     * object from a database row - it will use the no-argument constructor above instead.
     *
     * <p>The {@code id} is intentionally absent: {@code autoGenerate} assigns it on insert. The
     * status is always PENDING here, because a store is only ever created by pinning it on the map.
     * Completion is a separate, deliberate act that happens much later.
     */
    @Ignore
    public StoreEntity(@NonNull String routeNumber,
                       @NonNull String storeCode,
                       @NonNull String storeName,
                       @NonNull String area,
                       double latitude,
                       double longitude,
                       long now) {
        this.routeNumber = routeNumber;
        this.storeCode = storeCode;
        this.storeName = storeName;
        this.area = area;
        this.latitude = latitude;
        this.longitude = longitude;
        this.status = StoreStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
        this.completedAt = null;
    }

    public boolean isPending() {
        return status == StoreStatus.PENDING;
    }

    public boolean isCompleted() {
        return status == StoreStatus.COMPLETED;
    }

    /**
     * {@link TrackerCalculator.StoreLike} - the tracker's view of a store.
     *
     * <p>An accessor rather than a rename of the public {@code id} field: the field is what Room reads and
     * writes, and renaming it would change the column mapping. The seam exists so {@code domain/} can read
     * a store without importing a Room entity (AD-11).
     */
    @Override
    public long id() {
        return id;
    }

    @Override
    @NonNull
    public String storeCode() {
        return storeCode;
    }
}

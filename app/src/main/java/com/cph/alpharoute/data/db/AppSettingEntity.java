package com.cph.alpharoute.data.db;

import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * The app's settings - a SINGLE row, always with {@link #SINGLETON_ID}.
 *
 * <p>There is no settings table with many rows. One row is inserted on first launch by
 * {@link com.cph.alpharoute.data.repository.SettingsRepository}, and every change updates that same
 * row. Using a fixed primary key is what makes "read the settings" a single, unambiguous lookup.
 *
 * <p><b>Home is stored here, not in the stores table.</b> Home is the permanent final destination of
 * the route, it is drawn with its own house marker, and it must never be counted as a store or affect
 * any total. Keeping it in a different table makes that impossible to get wrong by accident.
 *
 * <p>The arrival radius defaults to {@code GeoConstants.DEFAULT_ARRIVAL_RADIUS_M} (50 m) and stays
 * configurable from the Settings screen. Exact coordinate equality is never required, because GPS
 * readings vary - see {@code ArrivalChecker} in Phase 5.1.
 */
@Entity(tableName = "app_settings")
public class AppSettingEntity {

    /** Table name, referenced by the DAO queries. */
    public static final String TABLE = "app_settings";

    /** The only primary key this table ever contains. */
    public static final long SINGLETON_ID = 1L;

    @PrimaryKey
    @ColumnInfo(name = "id")
    public long id = SINGLETON_ID;

    /** Home latitude; {@code null} until the user sets Home. */
    @Nullable
    @ColumnInfo(name = "homeLatitude")
    public Double homeLatitude;

    /** Home longitude; {@code null} until the user sets Home. */
    @Nullable
    @ColumnInfo(name = "homeLongitude")
    public Double homeLongitude;

    /**
     * Whether Home has been configured.
     *
     * <p>This is deliberately stored rather than derived from the coordinates being non-null. The
     * specification models it as its own field, and it keeps the intent explicit at the call site:
     * {@code if (settings.isHomeSet())} reads better than a null check that a future edit could
     * accidentally invert.
     */
    @ColumnInfo(name = "homeSet")
    public boolean homeSet;

    /** Arrival radius in metres. Never exact equality - GPS is not that precise. */
    @ColumnInfo(name = "arrivalRadius")
    public int arrivalRadius;

    /**
     * The store the user most recently selected, or the one currently being navigated to; {@code null}
     * when there is none.
     *
     * <p>Architecture decision AD-10: this single field doubles as the active-navigation target, so
     * the in-progress destination survives app restarts without needing a schema addition.
     */
    @Nullable
    @ColumnInfo(name = "lastSelectedStoreId")
    public Long lastSelectedStoreId;

    /**
     * The round the user is currently working through.
     *
     * <p><b>One round is one complete pass of every store.</b> A store is completed once per round, and
     * starting a new round puts every store back to PENDING so the map shows what is still to visit.
     * History rows from earlier rounds stay where they are - they are the record of what happened.
     *
     * <p>Stored rather than derived from {@code MAX(roundNumber)} in the history, and the difference
     * matters: a round with no completions yet has no history rows at all, so a derived value could not
     * distinguish "round 3, nothing done yet" from "round 2, everything done". The number has to exist
     * independently of the rows it will collect.
     *
     * <p>Defaults to 1, which is also what the migration gives every existing row.
     */
    @ColumnInfo(name = "currentRound", defaultValue = "1")
    public int currentRound = 1;

    /** When the settings row was last written, epoch millis. */
    @ColumnInfo(name = "lastUpdated")
    public long lastUpdated;

    /** Required by Room. Do not remove. */
    public AppSettingEntity() {
    }

    public boolean isHomeSet() {
        return homeSet && homeLatitude != null && homeLongitude != null;
    }
}

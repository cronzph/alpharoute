package com.cph.alpharoute.data.db;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * One stop in a saved route plan (Version 18).
 *
 * <h3>The two foreign keys do different jobs, and the difference matters</h3>
 *
 * <p><b>{@code planId → route_plans} cascades.</b> Deleting a plan removes its stops. A stop with no plan
 * is a row that can never be read and can never be cleaned up - it is not a record of anything.
 *
 * <p><b>{@code storeId → stores} cascades too</b>, and this is the one worth thinking about. A saved plan
 * names stores; if a store is deleted, the stop naming it cannot survive - the foreign key would refuse
 * the delete outright, which would break the delete feature. Cascading means deleting a store quietly
 * removes it from any saved plan, which is the honest outcome: <b>the plan no longer describes a route the
 * user can drive</b>, and leaving a dangling stop would show a stop with no name.
 *
 * <p>Note this is the OPPOSITE choice from {@code completion_history}, which uses RESTRICT. The difference
 * is what the row means: a completion is a record of something that happened and must never be lost,
 * while a stop in a plan is an instruction about the future and stops being true the moment the store is
 * gone.
 */
@Entity(
        tableName = "route_plan_stops",
        foreignKeys = {
                @ForeignKey(
                        entity = RoutePlanEntity.class,
                        parentColumns = "id",
                        childColumns = "planId",
                        onDelete = ForeignKey.CASCADE),
                @ForeignKey(
                        entity = StoreEntity.class,
                        parentColumns = "id",
                        childColumns = "storeId",
                        onDelete = ForeignKey.CASCADE)
        },
        indices = {
                @Index(value = "planId"),
                @Index(value = "storeId"),
                // A plan cannot contain the same store twice, and the position within a plan is unique -
                // two stops at position 3 would make the order ambiguous, which is the whole point of the
                // table.
                @Index(value = {"planId", "position"}, unique = true)
        }
)
public class RoutePlanStopEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    /** The plan this stop belongs to. */
    @ColumnInfo(name = "planId")
    public long planId;

    /** The store to visit. */
    @ColumnInfo(name = "storeId")
    public long storeId;

    /** 1 for the first stop, 2 for the second. The order IS the answer, so this is the important column. */
    @ColumnInfo(name = "position")
    public int position;

    /** The drive from the previous stop, in metres, as the solver reported it. */
    @ColumnInfo(name = "legDistanceMetres")
    public double legDistanceMetres;

    /** The drive from the previous stop, in seconds. */
    @ColumnInfo(name = "legDurationSeconds")
    public long legDurationSeconds;
}

package com.cph.alpharoute.data.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.cph.alpharoute.domain.TrackerCalculator;

/**
 * One completion event: the record that a store was visited and confirmed by the user.
 *
 * <p><b>This table is the tracker's source of truth.</b> No counter is ever stored anywhere. "Today",
 * "Yesterday", "Overall", "Remaining" and the percentage are all computed from this table joined with
 * {@link StoreEntity}, which is what the specification requires.
 *
 * <p><b>Two database-level guards live on this entity:</b>
 *
 * <ul>
 *   <li><b>AD-5 (revised in Version 7) - {@code (storeId, roundNumber)} is UNIQUE.</b> A store therefore
 *       <i>cannot</i> have two completion rows <i>in the same round</i>. Double-tapping YES, a race between
 *       two threads, or a bug in the UI code all end the same way: the second INSERT is rejected by SQLite.
 *       This is what makes "prevent one store from being counted more than once" a structural property
 *       rather than a hope.
 *
 *       <p><b>The round scoping is a generalisation, not a weakening.</b> The original rule was a single
 *       unique index on {@code storeId}, which made a second visit impossible <i>ever</i>. Counting a store
 *       once per round still cannot double-count; it simply says which round the count belongs to. A store
 *       may now have one row per round, and never two in the same one.</li>
 *
 *   <li><b>AD-6 - {@code onDelete = RESTRICT}.</b> The database refuses to delete a store row that
 *       still has completion history. Completed stores and their history are protected even if the
 *       delete guard in the UI is ever bypassed or broken.</li>
 * </ul>
 *
 * <p><b>Ordering.</b> {@code dailySequence} is the real order of completion within a local calendar
 * day: 1 for the first store completed that day, 2 for the second, and so on. It is based on when the
 * store was actually completed, never on the route number. The History screen sorts by this column,
 * so the recorded order is the true one.
 */
@Entity(
        tableName = "completion_history",
        foreignKeys = @ForeignKey(
                entity = StoreEntity.class,
                parentColumns = "id",
                childColumns = "storeId",
                onDelete = ForeignKey.RESTRICT
        ),
        indices = {
                @Index(value = {"storeId", "roundNumber"}, unique = true),
                @Index(value = "completionDateKey"),
                @Index(value = "roundNumber")
        }
)
public class CompletionHistoryEntity implements TrackerCalculator.CompletionLike {

    /** Table name, referenced by the DAO queries. */
    public static final String TABLE = "completion_history";

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    public long id;

    /** The completed store. Unique - one completion event per store, enforced by the index above. */
    @ColumnInfo(name = "storeId")
    public long storeId;

    /** Exact moment of completion, epoch millis, device local time. */
    @ColumnInfo(name = "completionTimestamp")
    public long completionTimestamp;

    /**
     * The local calendar day of the completion, formatted {@code YYYY-MM-DD}.
     *
     * <p>Derived from {@code completionTimestamp} in the device's local timezone by
     * {@code DateKeyHelper}, once, inside the completion transaction. Stored rather than recomputed so
     * that "today" and "yesterday" stay stable even if the device timezone later changes.
     */
    @NonNull
    @ColumnInfo(name = "completionDateKey")
    public String completionDateKey = "";

    /** 1 for the first store completed that day, 2 for the second, and so on. */
    @ColumnInfo(name = "dailySequence")
    public int dailySequence;

    /**
     * Which round this completion belongs to. 1 for the first pass over every store.
     *
     * <p>Part of the UNIQUE index with {@code storeId}, so a store can be completed once per round and
     * never twice in the same one.
     *
     * <p>{@code defaultValue = "1"} is what lets the Version 1 → 2 migration be a plain
     * {@code ALTER TABLE ... ADD COLUMN}: SQLite requires a default for a NOT NULL column added to a
     * table that already has rows, and every existing completion predates rounds, so 1 is the honest
     * value for all of them.
     */
    @ColumnInfo(name = "roundNumber", defaultValue = "1")
    public int roundNumber = 1;

    /** Required by Room. Do not remove. */
    public CompletionHistoryEntity() {
    }

    /**
     * Convenience constructor for a completion event.
     *
     * <p>{@code @Ignore} keeps Room on the no-argument constructor when it materialises a row.
     *
     * <p>This constructor is only ever called from inside the completion transaction in Phase 5.3,
     * after the store has been re-read and confirmed still PENDING <i>for the current round</i>.
     */
    @Ignore
    public CompletionHistoryEntity(long storeId,
                                   long completionTimestamp,
                                   @NonNull String completionDateKey,
                                   int dailySequence,
                                   int roundNumber) {
        this.storeId = storeId;
        this.completionTimestamp = completionTimestamp;
        this.completionDateKey = completionDateKey;
        this.dailySequence = dailySequence;
        this.roundNumber = roundNumber;
    }

    /**
     * {@link TrackerCalculator.CompletionLike} - the tracker's view of a completion.
     *
     * <p>Accessors rather than renames: the public fields are what Room maps to columns. The seam keeps
     * {@code domain/} free of {@code androidx.room} (AD-11).
     */
    @Override
    public long storeId() {
        return storeId;
    }

    @Override
    @NonNull
    public String completionDateKey() {
        return completionDateKey;
    }

    @Override
    public long completionTimestamp() {
        return completionTimestamp;
    }

    /** The round this completion belongs to, for the round-scoped rules. */
    @Override
    public int roundNumber() {
        return roundNumber;
    }
}

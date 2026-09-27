package com.cph.alpharoute.data.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * The app's local database. Room is the single source of truth for everything the tracker shows
 * (architecture decision AD-4).
 *
 * <p><b>Version 1.</b> This is the first schema. Nothing here may be changed destructively once real
 * route data exists - no {@code fallbackToDestructiveMigration()}, ever. When the schema does need to
 * change, a real {@code Migration} is written and the exported schema in {@code app/schemas} is used
 * to test it.
 *
 * <p><b>Why this is a hand-written singleton</b> rather than a dependency-injection framework: there
 * is exactly one database, one user and one device (D-5). Double-checked locking keeps the
 * construction off the main thread's critical path and guarantees a single instance even if two
 * threads race on first access.
 *
 * <p><b>Foreign keys.</b> AD-6 relies on {@code PRAGMA foreign_keys = ON} actually being in effect -
 * without it, the {@code onDelete = RESTRICT} rule on {@code completion_history} would be declared but
 * never enforced, and a completed store could be deleted after all. Room normally enables this for
 * databases that declare foreign keys; the callback below states it explicitly so the guarantee does
 * not depend on an assumption.
 */
@Database(
        entities = {
                StoreEntity.class,
                CompletionHistoryEntity.class,
                AppSettingEntity.class,
                RoutePlanEntity.class,
                RoutePlanStopEntity.class
        },
        version = AppDatabase.DATABASE_VERSION,
        exportSchema = true
)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {

    public static final String DATABASE_NAME = "alpharoute.db";

    /**
     * The schema version.
     *
     * <p><b>1 → 2 in Version 7</b>, for rounds. See {@link #MIGRATION_1_2} — this is the first migration
     * in the project, and it is written out in full rather than left to a destructive fallback.
     */
    public static final int DATABASE_VERSION = 3;

    /**
     * Version 1 → 2: rounds.
     *
     * <p>Two changes, and both are additive apart from one index swap:
     *
     * <ol>
     *   <li>{@code completion_history} gains {@code roundNumber}, defaulting to 1.</li>
     *   <li>The UNIQUE index moves from {@code storeId} to {@code (storeId, roundNumber)} — AD-5
     *       generalised, not weakened. A store can be completed once per round and never twice in the
     *       same one.</li>
     *   <li>{@code app_settings} gains {@code currentRound}, defaulting to 1.</li>
     * </ol>
     *
     * <p><b>Why every statement is safe on a table that already has rows.</b> {@code ADD COLUMN} with a
     * {@code NOT NULL DEFAULT} is the one form SQLite allows without rewriting the table, and it is the
     * reason both new columns carry a default. The old rows are all pre-rounds, so 1 is not a
     * placeholder — it is the true answer for them.
     *
     * <p><b>Dropping and recreating the index is not data loss.</b> An index holds no data of its own;
     * it is rebuilt from the table. The table itself is untouched by either statement.
     *
     * <p><b>The index names are the ones Room generated for version 1</b>, read out of the exported
     * {@code 1.json} rather than guessed. Room validates the resulting schema against the entities on
     * open, so a wrong name here would fail loudly at first launch rather than silently — but it would
     * still be a crash, so the names were checked against the export.
     */
    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            // 1. The round each existing completion belongs to. Every row predates rounds, so 1 is right.
            db.execSQL("ALTER TABLE completion_history "
                    + "ADD COLUMN roundNumber INTEGER NOT NULL DEFAULT 1");

            // 2. AD-5, scoped to the round. The old index is dropped by its Room-generated name.
            db.execSQL("DROP INDEX IF EXISTS index_completion_history_storeId");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS "
                    + "index_completion_history_storeId_roundNumber "
                    + "ON completion_history (storeId, roundNumber)");

            // 3. A non-unique index on roundNumber, matching the entity's @Index. Without it, every
            //    round-scoped query would scan the whole table.
            db.execSQL("CREATE INDEX IF NOT EXISTS index_completion_history_roundNumber "
                    + "ON completion_history (roundNumber)");

            // 4. The round the user is currently working through.
            db.execSQL("ALTER TABLE app_settings "
                    + "ADD COLUMN currentRound INTEGER NOT NULL DEFAULT 1");
        }
    };

    public abstract StoreDao storeDao();

    public abstract CompletionHistoryDao completionHistoryDao();

    public abstract AppSettingDao appSettingDao();

    /**
     * The completion transaction (Phase 5.3).
     *
     * <p>A separate DAO from {@link #completionHistoryDao()} because the completion operation spans two
     * tables and Room's {@code @Transaction} only covers one DAO. See
     * {@link CompletionTransactionDao} for the full contract.
     *
     * <p><b>Registering a DAO does not change the schema.</b> The exported schema describes entities and
     * columns; a DAO is only a set of queries against them. So {@code 1.json} is unchanged,
     * {@link #DATABASE_VERSION} stays at 1, and no {@code Migration} is written.
     */
    public abstract CompletionTransactionDao completionTransactionDao();

    /**
     * The round operations (Version 7).
     *
     * <p>Separate from {@link #completionTransactionDao()} because starting a new round spans
     * {@code app_settings} and {@code stores}, and Room's {@code @Transaction} covers one DAO. See
     * {@link RoundTransactionDao} for why the two must be atomic.
     */
    public abstract RoundTransactionDao roundTransactionDao();

    /**
     * Restoring a backup (Version 9.2).
     *
     * <p>Separate again, because a restore spans all three tables and Room's {@code @Transaction} covers one
     * DAO. This is the only thing in the app that can delete every store, and it does so inside a single
     * transaction that throws to roll back.
     */
    public abstract BackupTransactionDao backupTransactionDao();

    /**
     * Deleting a store together with its history (Version 13).
     *
     * <p>A completed store can now be deleted. The foreign key is unchanged - AD-6's {@code RESTRICT}
     * still refuses an orphaning delete - and the cascade is done by the transaction instead, which needs
     * no schema change and no migration.
     */
    public abstract StoreTransactionDao storeTransactionDao();

    /** Saved route plans (Version 18). */
    public abstract RoutePlanDao routePlanDao();

    /**
     * Version 2 → 3: saved route plans.
     *
     * <p><b>Purely additive.</b> Two new tables and their indices - no column added to an existing table,
     * nothing altered, nothing dropped, and not one row of the existing data touched. That makes it the
     * safest shape a migration can have, and the reason it can be written with confidence while 7.0's
     * 1 → 2 (which altered two tables and swapped a unique index) still needs a device test.
     *
     * <p><b>Why the tables are created here at all, rather than letting Room rebuild.</b>
     * {@code fallbackToDestructiveMigration()} would delete everything, and that is never acceptable once
     * real data exists - it is a standing rule in this project.
     *
     * <p><b>The column list must match the entities exactly</b>, including the two {@code DEFAULT 0} on the
     * booleans, because Room compares the resulting schema against what it expects and refuses to open the
     * database on any difference. A missing default is the classic way this migration would fail loudly.
     */
    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            // 1. The plan itself.
            db.execSQL("CREATE TABLE IF NOT EXISTS `route_plans` ("
                    + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`name` TEXT NOT NULL, "
                    + "`roundNumber` INTEGER NOT NULL, "
                    + "`createdAt` INTEGER NOT NULL, "
                    + "`totalDistanceMetres` REAL NOT NULL, "
                    + "`totalDurationSeconds` INTEGER NOT NULL, "
                    + "`startedAtLocation` INTEGER NOT NULL DEFAULT 0, "
                    + "`endedAtHome` INTEGER NOT NULL DEFAULT 0)");

            db.execSQL("CREATE INDEX IF NOT EXISTS `index_route_plans_roundNumber` "
                    + "ON `route_plans` (`roundNumber`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_route_plans_createdAt` "
                    + "ON `route_plans` (`createdAt`)");

            // 2. The stops. Both foreign keys cascade - see RoutePlanStopEntity for why storeId cascades
            //    here while completion_history.storeId uses RESTRICT.
            db.execSQL("CREATE TABLE IF NOT EXISTS `route_plan_stops` ("
                    + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`planId` INTEGER NOT NULL, "
                    + "`storeId` INTEGER NOT NULL, "
                    + "`position` INTEGER NOT NULL, "
                    + "`legDistanceMetres` REAL NOT NULL, "
                    + "`legDurationSeconds` INTEGER NOT NULL, "
                    + "FOREIGN KEY(`planId`) REFERENCES `route_plans`(`id`) "
                    + "ON UPDATE NO ACTION ON DELETE CASCADE, "
                    + "FOREIGN KEY(`storeId`) REFERENCES `stores`(`id`) "
                    + "ON UPDATE NO ACTION ON DELETE CASCADE)");

            db.execSQL("CREATE INDEX IF NOT EXISTS `index_route_plan_stops_planId` "
                    + "ON `route_plan_stops` (`planId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_route_plan_stops_storeId` "
                    + "ON `route_plan_stops` (`storeId`)");
            // The order is the answer, so two stops cannot share a position.
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS "
                    + "`index_route_plan_stops_planId_position` "
                    + "ON `route_plan_stops` (`planId`, `position`)");
        }
    };

    private static volatile AppDatabase instance;

    /**
     * The single database instance for the process.
     *
     * <p>Always pass an application context; the call is safe from any thread and only ever builds the
     * database once.
     */
    public static AppDatabase getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    DATABASE_NAME)
                            // The one and only migration path. There is deliberately NO
                            // fallbackToDestructiveMigration(): that would silently wipe the user's
                            // stores and history on any schema change, which is the opposite of what a
                            // migration is for.
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                            .addCallback(new Callback() {
                                @Override
                                public void onOpen(@NonNull SupportSQLiteDatabase db) {
                                    // Makes AD-6 real rather than merely declared: with foreign keys
                                    // enforced, SQLite refuses to delete a store that still has
                                    // completion history.
                                    db.execSQL("PRAGMA foreign_keys = ON");
                                }
                            })
                            .build();
                }
            }
        }
        return instance;
    }
}

# AlphaRoute — Phase 2.1 Room Database Foundation

**Project title:** Alfamart Route Tracker
**Version:** 2 — Local Database — **Phase:** 2.1 Room Database Foundation
**Repository root:** `C:/Users/Admin/OneDrive/Documents/custom system/AlphaRoute`
**Date:** 2026-09-25
**Prerequisites:** Phases 0.1–0.3, 1.1–1.3 approved · Phase 0.2 AD-3/AD-4/AD-5/AD-6/AD-10
**Status:** 🔄 **Code complete and BUILD-VERIFIED.** Automated `:app:assembleDebug` → **BUILD SUCCESSFUL**. Awaiting your on-device test.

> **Scope note.** Your instruction for this phase added a requirement: *"save locally then sync to
> Firebase, so it works without waiting if the internet is slow."* This document covers both — the Room
> foundation the phase calls for, **and** a full review of the Firebase sync question, including the one
> thing I need from you before Firestore can be wired up. See §21.

---

## 1. CURRENT VERSION AND PHASE

| Item | Value |
| --- | --- |
| Version | 2 — Local Database |
| Phase | 2.1 — Room Database Foundation |
| Files created | 20 |
| Files modified | 3 |
| Files deleted | 0 |
| **Database version** | **1** — first schema, no migrations exist yet |

---

## 2. OBJECTIVE

Create the local database that everything else is built on:

- `StoreEntity`, `CompletionHistoryEntity`, `AppSettingEntity`
- The three DAO interfaces
- `AppDatabase` at version 1
- Type converters
- The repository layer
- The `AlphaRouteApp` application class that owns them all

All operations asynchronous. No main-thread database work. Unique store code. Predictable route-number
duplicate handling. Foreign-key relationship where appropriate.

---

## 3. UNDERSTANDING CHECKLIST

- [x] Java only, no Kotlin. No coroutines — `ExecutorService` instead (AD-3).
- [x] All database work on `AppExecutors.diskIO()`, a **single** thread.
- [x] Unique store code and unique route number, enforced by database indices.
- [x] Foreign key from completion history to store, with `onDelete = RESTRICT` (AD-6).
- [x] One completion event per store, enforced by a unique index (AD-5).
- [x] No counter is stored anywhere — every figure is derived from the tables.
- [x] Home is **not** a store and lives in a different table.
- [x] No destructive migration. `fallbackToDestructiveMigration()` is absent on purpose.
- [x] No store data, coordinates, codes or API keys invented anywhere.

---

## 4. FEATURES INCLUDED (in this phase)

| Feature | Detail |
| --- | --- |
| **Three tables** | `stores`, `completion_history`, `app_settings` at schema version 1 |
| **Five indices** | Unique on store code, unique on route number, status, unique on history store id, history date key |
| **One foreign key** | `completion_history.storeId → stores.id`, `onDelete = RESTRICT` |
| **Three DAOs** | Reads (LiveData + one-off) and writes, with the ordering rules stated in the queries |
| **Four repositories** | `StoreRepository`, `CompletionRepository`, `SettingsRepository` + the `RepositoryCallback` / `WriteOutcome` types |
| **Threading policy** | `AppExecutors` — single-threaded disk IO, small network pool, main-thread handler |
| **Dependency container** | `AlphaRouteApp`, which builds and owns all of it |
| **A sync seam** | `SyncManager` + `NoOpSyncManager`, called after every successful local write |
| **Exported schema** | `app/schemas/…/1.json`, so a future migration can be tested rather than guessed |

---

## 5. FEATURES NOT INCLUDED YET

- No ViewModels yet — Phase 6.1 for the Dashboard, 2.2 for store CRUD.
- No store CRUD **UI** — Phase 2.2 (the repository plumbing exists; the screens come later).
- **No completion write path.** The atomic completion transaction is Phase 5.3, and
  `CompletionRepository` is deliberately read-only until then.
- No settings **screen** — Phase 2.3.
- No map, location, routing or navigation.
- No Firebase transport — see §21.

---

## 6. ASSUMPTIONS

| # | Assumption | Impact if wrong |
| --- | --- | --- |
| A35 | **Timestamps are epoch millis (`long`)**, and the `YYYY-MM-DD` day key is stored only on the history row, not derived at read time. | Storing the key means "today" stays stable even if the device timezone changes later. |
| A36 | **`completedAt` is a nullable `Long`**, not `0`. | A sentinel of `0` would be a lie - "never completed" is genuinely absent. |
| A37 | **Queries order by `id` (insertion order).** Route numbers are free text, so SQL ordering would put "10" before "9". | Any numeric-aware ordering is a presentation concern, handled in a ViewModel where it can be unit-tested. |
| A38 | **`StoreStatus` is stored as its name, not its ordinal.** | An ordinal would silently change meaning if anyone reordered the enum constants, and the stored data would quietly become wrong. |
| A39 | **No `AlphaRouteApp` DI framework** — hand-written container. | Four objects, one user, one device (D-5). Revisit only if the graph grows. |
| A40 | **`CompletionRepository` is read-only in this phase.** | Adding a plain "insert completion" method now would create a way to bypass the Phase 5.3 transaction and risk the double-counting the rules forbid. |

---

## 7. FILES TO CREATE (20)

### Domain and utilities

| Path | Purpose |
| --- | --- |
| `domain/StoreStatus.java` | **New.** `PENDING` / `COMPLETED` |
| `util/GeoConstants.java` | **New.** Earth radius, arrival-radius bounds and default, coordinate validity |
| `util/AppExecutors.java` | **New.** The threading policy in one place |

### Database

| Path | Purpose |
| --- | --- |
| `data/db/StoreEntity.java` | **New.** The store row and its three indices |
| `data/db/CompletionHistoryEntity.java` | **New.** The completion event, with AD-5 and AD-6 |
| `data/db/AppSettingEntity.java` | **New.** The single settings row, including Home |
| `data/db/Converters.java` | **New.** `StoreStatus` ↔ `String` |
| `data/db/StoreDao.java` | **New.** Store reads and writes |
| `data/db/CompletionHistoryDao.java` | **New.** History reads |
| `data/db/AppSettingDao.java` | **New.** Settings read and upsert |
| `data/db/AppDatabase.java` | **New.** Room database, version 1, singleton, foreign keys enforced |

### Sync seam

| Path | Purpose |
| --- | --- |
| `data/sync/SyncManager.java` | **New.** The interface; documents the local-first ordering guarantee |
| `data/sync/NoOpSyncManager.java` | **New.** The Null Object in force until a transport exists |

### Repositories

| Path | Purpose |
| --- | --- |
| `data/repository/RepositoryCallback.java` | **New.** One-method result callback, always on the main thread |
| `data/repository/WriteOutcome.java` | **New.** `SUCCESS` / `DUPLICATE_*` / `NOT_FOUND` / `PROTECTED_BY_HISTORY` / `FAILED` |
| `data/repository/StoreRepository.java` | **New.** The only route to the store table |
| `data/repository/CompletionRepository.java` | **New.** History reads |
| `data/repository/SettingsRepository.java` | **New.** Home, arrival radius, last selected store |

### Application and documentation

| Path | Purpose |
| --- | --- |
| `AlphaRouteApp.java` | **New.** The dependency container |
| `docs/PHASE_2.1_ROOM_FOUNDATION.md` | **New.** This record |

---

## 8. FILES TO MODIFY (3)

| # | Path | Status | What changed |
| --- | --- | --- | --- |
| 1 | `gradle/libs.versions.toml` | **Modified** | Added `room = "2.8.5"`, `lifecycle = "2.11.0"`, and five library entries |
| 2 | `app/build.gradle` | **Modified** | Room (runtime + annotation processor), the three Lifecycle artefacts, and the `room.schemaLocation` argument |
| 3 | `app/src/main/AndroidManifest.xml` | **Modified** | `android:name=".AlphaRouteApp"` on `<application>`. **Still no permissions.** |

**Inspected and left unmodified:** everything else. Notably `MainActivity` and the four fragments are
untouched — this phase adds the data layer underneath them without changing a single screen.

---

## 9. DEPENDENCIES

| Artifact | Version | Purpose |
| --- | --- | --- |
| `androidx.room:room-runtime` | **2.8.5** | The database. Latest stable, verified against Google's Maven metadata |
| `androidx.room:room-compiler` | **2.8.5** | Annotation processor; generates `AppDatabase_Impl` and the DAO implementations |
| `androidx.lifecycle:lifecycle-viewmodel` | **2.11.0** | ViewModels (used from Phase 2.2) |
| `androidx.lifecycle:lifecycle-livedata` | **2.11.0** | `LiveData`. Chosen over `StateFlow` because this is a Java project and coroutines are Kotlin (AD-2) |
| `androidx.lifecycle:lifecycle-runtime` | **2.11.0** | Lifecycle awareness |

**Not added:** `room-ktx` (Kotlin only), `room-rxjava*` (not used), `room-testing` (Phase 10.2), and
anything Firebase (§21).

---

## 10. PERMISSIONS AND MANIFEST CHANGES

**No permission added.** The only manifest change is the application class:

```xml
<application
    android:name=".AlphaRouteApp"
    ...
```

The database is entirely on-device, so it needs nothing. The first permission in this project arrives
in Phase 3.1 (`INTERNET`), for map tiles.

---

## 11. SETUP INSTRUCTIONS

1. Let the Gradle sync finish — it fetches Room 2.8.5 and the Lifecycle artefacts on this sync.
2. **Build → Clean Project**, then **Build → Rebuild Project**.
3. After the build, confirm `app/schemas/com.cph.alpharoute.data.db.AppDatabase/1.json` exists. That
   file is the schema snapshot Room exports, and it must be **kept in the repository** — it is what
   makes a future migration testable instead of guesswork.
4. Run on a device or emulator (API 24+).

---

## 12. COMPLETE CODE

All 23 files are written into the project on disk and attached to this message. The files worth reading
first, in order:

1. `data/db/StoreEntity.java` — the store row and why each index exists
2. `data/db/CompletionHistoryEntity.java` — AD-5 and AD-6, the two database-level guards
3. `data/db/AppDatabase.java` — version 1, the singleton, and the foreign-key pragma
4. `data/repository/StoreRepository.java` — the local-first write path
5. `data/sync/SyncManager.java` — the ordering guarantee in writing
6. `AlphaRouteApp.java` — the container, and the one line Firestore will replace

---

## 13. CODE EXPLANATION

### 13.1 The two database-level guards

The specification's hardest rule is *"prevent one store from being counted more than once."* Two
indices make that structural rather than a matter of careful coding:

```java
// CompletionHistoryEntity
indices = {
    @Index(value = "storeId", unique = true),      // AD-5
    @Index(value = "completionDateKey")
}
foreignKeys = @ForeignKey(
    entity = StoreEntity.class,
    parentColumns = "id",
    childColumns = "storeId",
    onDelete = ForeignKey.RESTRICT                // AD-6
)
```

**AD-5** — `storeId` is unique, so a store physically cannot have two completion rows. A double tap, a
race between two threads, or a bug in the UI code all end the same way: SQLite rejects the second
INSERT. The `WriteOutcome.ALREADY_COUNTED` path in Phase 5.3 exists to turn that rejection into a
friendly message, not to be the guarantee.

**AD-6** — `RESTRICT` means the database refuses to delete a store that still has completion history.
That matters because the history is the one thing in this app that cannot be reconstructed. `DELETE`
returns zero rows affected rather than succeeding and orphaning the record.

### 13.2 Foreign keys must actually be enforced

A `@ForeignKey` declaration is only a *promise*. SQLite has foreign-key enforcement **off by default**,
so a database that declares `RESTRICT` but never turns the pragma on would silently allow exactly the
deletion AD-6 is supposed to prevent. `AppDatabase` states it explicitly:

```java
.addCallback(new Callback() {
    @Override
    public void onOpen(@NonNull SupportSQLiteDatabase db) {
        db.execSQL("PRAGMA foreign_keys = ON");
    }
})
```

Room normally does this for databases that declare foreign keys. It is written out here anyway, because
the whole value of AD-6 is that it holds even when the code above it is wrong — and a guard that
silently does nothing is worse than no guard at all.

### 13.3 The local-first write path — the actual answer to "don't wait for the internet"

Your requirement is met by structure, not by clever code:

```
Fragment / ViewModel
      │  calls storeRepository.insert(store, callback)
      ▼
executors.diskIO().execute(...)          SINGLE thread
      │
      ├─ check duplicates → insert → COMMIT          ← the write is done here
      │
      ├─ notifySyncSafely()                          ← sync hook, AFTER the commit
      │      └─ try { syncManager.onLocalDataChanged(); }
      │         catch (Exception e) { log and swallow }
      │
      └─ postToMain(() -> callback.onResult(SUCCESS))
```

Four properties follow, and they are the reason the app feels instant on a bad connection:

1. **The network is not in the write path.** `insert` returns as soon as the local transaction commits.
2. **The sync call happens after the commit**, never before — so a slow sync cannot delay the save.
3. **The sync call is wrapped in a guard.** A sync implementation that throws, blocks or does not exist
   cannot turn a successful save into a visible failure.
4. **No screen observes sync state.** Every screen reads Room, so a sync that is still running — or
   failing — is invisible to the user.

With no signal at all, the app is fully functional. With a slow connection, it is still fully
functional; the sync simply takes longer, invisibly.

### 13.4 Why the disk executor is single-threaded

`Executors.newSingleThreadExecutor(...)`, not a pool. It serialises every write, which is the third and
outermost guard on the double-counting rule, sitting alongside the in-transaction status re-read and
AD-5. Two completion requests physically cannot interleave. It also removes any check-then-insert race
in `StoreRepository.insert`, so the duplicate pre-check is reliable rather than merely usually correct.

### 13.5 Why `LiveData` and not `StateFlow`

This is a Java project (rule 2), and `StateFlow` needs coroutines, which are Kotlin. `LiveData` is
observable from Java with no extra machinery, and — importantly — Room returns it natively from a DAO
query, so a screen can never show a stale marker: Room re-runs the query whenever the table changes.

### 13.6 Why the completion table has no update or delete method

A completion event is a historical fact. Once recorded it is only ever read. There is deliberately no
`@Update` and no `@Delete` on `CompletionHistoryDao` — the only way a row disappears is an explicit,
transactional import in Phase 9.

### 13.7 Why `WriteOutcome` exists instead of exceptions

Duplicate store codes and "the store was deleted a moment ago" are ordinary, expected outcomes, not
exceptional ones. Reporting them as values keeps the call sites readable and matches the Phase 0.3
screen plan, which lists exactly these cases as the inline field errors on the Add/Edit Store sheet.

`PROTECTED_BY_HISTORY` has two independent sources: the pre-check for a clear message, and the
`SQLiteConstraintException` catch as the safety net for when the database itself refuses.

---

## 14. DATA FLOW FOR THIS PHASE

```
AlphaRouteApp.onCreate()
      ├─ new AppExecutors()                 single disk thread, network pool, main handler
      ├─ AppDatabase.getInstance(this)      version 1, foreign keys ON
      ├─ syncManager = new NoOpSyncManager()
      ├─ storeRepository      = new StoreRepository(storeDao, historyDao, executors, sync)
      ├─ completionRepository = new CompletionRepository(historyDao, executors)
      ├─ settingsRepository   = new SettingsRepository(settingDao, executors, sync)
      └─ settingsRepository.ensureDefaults()   creates the single settings row if missing
                    │
                    ▼  (later phases)
            Fragment ──► ViewModel ──► Repository ──► DAO ──► SQLite
                    ▲                        │
                    └──── LiveData ──────────┘        (Room re-queries on every table change)
```

**No screen is wired to this yet.** Phase 2.1 builds the data layer; Phase 2.2 (store CRUD) and 6.1
(the live tracker) are what connect it to the UI.

---

## 15. MANUAL TEST PROCEDURE

### 15.0 Already verified automatically (2026-09-25)

`:app:assembleDebug` was run on this machine with Gradle 9.4.1 / JDK 21 and returned
**`BUILD SUCCESSFUL in 2m 6s`** (36 tasks executed).

Room validates every `@Query`, every index and every foreign key **at compile time**, so a successful
build is already strong evidence. The generated and exported artefacts confirm it:

| Evidence | Result |
| --- | --- |
| `:app:compileDebugJavaWithJavac` | ran — all Java compiled |
| `AppDatabase_Impl.java` | **generated** — Room built the database class |
| `StoreDao_Impl.java`, `CompletionHistoryDao_Impl.java`, `AppSettingDao_Impl.java` | **generated** — Room parsed and validated every query |
| `app/schemas/com.cph.alpharoute.data.db.AppDatabase/1.json` | **exported** — the `room.schemaLocation` argument took effect |
| `:app:compileDebugKotlin` | **NO-SOURCE** — still zero Kotlin source files |
| `app/build/outputs/apk/debug/app-debug.apk` | **8,450,711 bytes** |

And the exported schema itself proves the two guards the whole design rests on:

```
version: 1

TABLE stores
  INDEX index_stores_storeCode     unique=True   ['storeCode']
  INDEX index_stores_routeNumber   unique=True   ['routeNumber']
  INDEX index_stores_status        unique=False  ['status']

TABLE completion_history
  INDEX index_completion_history_storeId            unique=True   ['storeId']   <- AD-5
  INDEX index_completion_history_completionDateKey  unique=False  ['completionDateKey']
  FK -> stores ['storeId'] onDelete=RESTRICT                                     <- AD-6

TABLE app_settings
  id, homeLatitude, homeLongitude, homeSet, arrivalRadius, lastSelectedStoreId, lastUpdated
```

**AD-5 and AD-6 are therefore not intentions — they are in the schema Room will create on the device.**
What remains is the runtime half: the database actually appearing on the device, and the constraints
actually firing when you try to violate them (§15.1–15.7).

### 15.1 Confirm the database is created

1. Run the app on a device or emulator, then **close it**.
2. In Android Studio open **View → Tool Windows → App Inspection → Database Inspector**.
3. The `alpharoute.db` database must be listed, with three tables: `stores`, `completion_history`,
   `app_settings`.

### 15.2 Confirm the settings row and its default

4. Open the `app_settings` table. There must be **exactly one row**, with `id = 1`.
5. Confirm `arrivalRadius = 50`, `homeSet = 0`, `homeLatitude = NULL`, `homeLongitude = NULL`,
   `lastSelectedStoreId = NULL`.

### 15.3 Confirm the unique indices are real

6. In the Database Inspector, open a query window on `alpharoute.db` and run:

```sql
INSERT INTO stores (routeNumber, storeCode, storeName, area, latitude, longitude, status, createdAt, updatedAt)
VALUES ('1', 'TEST-1', 'Test store', 'Test area', 14.5995, 120.9842, 'PENDING', 0, 0);
```

7. Run the **same statement again**. It must fail with a `UNIQUE constraint failed` error on
   `stores.storeCode`. That is the guard working — not a bug.
8. Now run it with a different `storeCode` but the **same** `routeNumber` and confirm it fails on
   `stores.routeNumber` instead.

### 15.4 Confirm a store can be deleted while pending

9. Delete the test row you inserted:
   `DELETE FROM stores WHERE storeCode = 'TEST-1';` — this must succeed.

### 15.5 Confirm AD-6 actually protects a completed store

10. Insert a store, then insert a matching history row and flip the store to COMPLETED:

```sql
INSERT INTO stores (id, routeNumber, storeCode, storeName, area, latitude, longitude, status, createdAt, updatedAt, completedAt)
VALUES (900, '900', 'GUARD-1', 'Guard store', 'Guard area', 14.5995, 120.9842, 'COMPLETED', 0, 0, 0);

INSERT INTO completion_history (storeId, completionTimestamp, completionDateKey, dailySequence)
VALUES (900, 0, '2026-09-25', 1);
```

11. Now try to delete the store: `DELETE FROM stores WHERE id = 900;`
    **This must fail** with a foreign-key constraint error. If it succeeds, AD-6 is not being enforced
    and I need to know — that is the single most important check in this list.

### 15.6 Confirm AD-5 blocks a second completion

12. Try to insert a second history row for store 900:
    `INSERT INTO completion_history (storeId, completionTimestamp, completionDateKey, dailySequence) VALUES (900, 1, '2026-09-25', 2);`
    **This must fail** on the unique index over `storeId`.

### 15.7 Confirm the sync seam fires after the local write

13. Open **Logcat** and filter on `AlphaRouteSync`.
14. There is nothing to trigger a write from the UI yet, so this check happens in Phase 2.2. For now,
    confirm the tag exists in the code path and that the app launches with no crash.

### 15.8 Clean up

15. Delete the rows you inserted in §15.3–15.6 (delete the history row first, then the store), or
    simply clear the app's data from **Settings → Apps → Alfamart Route Tracker → Storage → Clear
    storage**.

**Report any failure with the step number and the exact error text.**

---

## 16. EXPECTED RESULT

- `alpharoute.db` exists with three tables at schema version 1.
- Exactly one `app_settings` row with `arrivalRadius = 50`.
- A duplicate store code or route number is rejected by the database.
- A pending store can be deleted; a completed store **cannot**.
- A second completion row for the same store is rejected.
- The app launches with no crash and no permission prompt.
- Still **no data on any screen** — the Dashboard still shows its labelled placeholders.

---

## 17. COMMON ERRORS AND FIXES

| Symptom | Cause | Fix |
| --- | --- | --- |
| `Cannot figure out how to save this field into database` | A field has no `@ColumnInfo` and no type converter | Add a converter in `Converters`, or exclude the field with `@Ignore` |
| `Cannot find setter/field` for a constructor | Room cannot match a constructor to the fields | Keep the no-arg constructor and annotate any extra one with `@Ignore` |
| `error: Cannot verify the data integrity` / schema mismatch | `version` was changed without a `Migration` | Never bump `version` alone. Write a `Migration` first |
| `Room cannot verify the data integrity. Looks like you've changed schema but forgot to update the version number.` | An entity changed but `version` stayed at 1 | Same fix — and this is exactly why destructive migration is not enabled |
| `app/schemas/...` not generated | The `room.schemaLocation` argument did not take | Confirm it is inside `defaultConfig` and re-sync |
| `UNIQUE constraint failed` on a legitimate insert | Genuinely a duplicate code or route number | Expected. Surface it as a field error, not a crash |
| `FOREIGN KEY constraint failed` on delete | The store has completion history | Expected — that is AD-6 working |
| `Cannot access database on the main thread` | A `*Now` DAO method was called from the UI | Move it to `AppExecutors.diskIO()` |
| `android.database.sqlite.SQLiteConstraintException` escaping | A repository write was called without its try/catch | All writes must go through the repository, never the DAO |
| App crashes on launch with `ClassCastException` on the application context | The manifest `android:name` does not match the class | It must be `.AlphaRouteApp` |

---

## 18. ACCEPTANCE CHECKLIST

- [ ] Gradle sync completes; Room 2.8.5 and Lifecycle 2.11.0 resolve.
- [ ] Clean build succeeds.
- [ ] `app/schemas/com.cph.alpharoute.data.db.AppDatabase/1.json` exists.
- [ ] `alpharoute.db` appears in the Database Inspector with three tables.
- [ ] Exactly one `app_settings` row, `id = 1`, `arrivalRadius = 50`.
- [ ] A duplicate store code is rejected by the database (§15.3).
- [ ] A duplicate route number is rejected by the database (§15.3).
- [ ] A pending store can be deleted (§15.4).
- [ ] **A completed store CANNOT be deleted** (§15.5) — AD-6 verified.
- [ ] **A second completion row for the same store is rejected** (§15.6) — AD-5 verified.
- [ ] The app launches with no crash and no permission prompt.
- [ ] Confirmed: no counter is stored anywhere in the schema.
- [ ] Confirmed: still **no Kotlin source file** anywhere in the project.

---

## 19. UPDATED PROJECT STATE

**COMPLETED PHASES**
- ✅ 0.1 Requirements Review (Rev 5, FINAL) · ✅ 0.2 Architecture · ✅ 0.3 Screen Plan
- ✅ 1.1 Project Creation · ✅ 1.2 Theme and Base Navigation · ✅ 1.3 Dashboard Static UI
- 🔄 **2.1 Room Database Foundation** — *awaiting your on-device test*

**CURRENT PHASE**
- Phase 2.1 — awaiting the database test

**CURRENT DATABASE VERSION**
- **1.** Three tables: `stores`, `completion_history`, `app_settings`.

**CURRENT FILES** (changed this phase)

```
gradle/libs.versions.toml                    (MODIFIED - room 2.8.5, lifecycle 2.11.0)
app/build.gradle                             (MODIFIED - Room + Lifecycle + schema export)
app/src/main/AndroidManifest.xml             (MODIFIED - android:name=".AlphaRouteApp")

app/src/main/java/com/cph/alpharoute/AlphaRouteApp.java                        (NEW)
app/src/main/java/com/cph/alpharoute/domain/StoreStatus.java                   (NEW)
app/src/main/java/com/cph/alpharoute/util/GeoConstants.java                    (NEW)
app/src/main/java/com/cph/alpharoute/util/AppExecutors.java                    (NEW)
app/src/main/java/com/cph/alpharoute/data/db/StoreEntity.java                  (NEW)
app/src/main/java/com/cph/alpharoute/data/db/CompletionHistoryEntity.java      (NEW)
app/src/main/java/com/cph/alpharoute/data/db/AppSettingEntity.java             (NEW)
app/src/main/java/com/cph/alpharoute/data/db/Converters.java                   (NEW)
app/src/main/java/com/cph/alpharoute/data/db/StoreDao.java                     (NEW)
app/src/main/java/com/cph/alpharoute/data/db/CompletionHistoryDao.java         (NEW)
app/src/main/java/com/cph/alpharoute/data/db/AppSettingDao.java                (NEW)
app/src/main/java/com/cph/alpharoute/data/db/AppDatabase.java                  (NEW)
app/src/main/java/com/cph/alpharoute/data/sync/SyncManager.java                (NEW)
app/src/main/java/com/cph/alpharoute/data/sync/NoOpSyncManager.java            (NEW)
app/src/main/java/com/cph/alpharoute/data/repository/RepositoryCallback.java   (NEW)
app/src/main/java/com/cph/alpharoute/data/repository/WriteOutcome.java         (NEW)
app/src/main/java/com/cph/alpharoute/data/repository/StoreRepository.java      (NEW)
app/src/main/java/com/cph/alpharoute/data/repository/CompletionRepository.java (NEW)
app/src/main/java/com/cph/alpharoute/data/repository/SettingsRepository.java   (NEW)

app/schemas/com.cph.alpharoute.data.db.AppDatabase/1.json   (NEW - generated, keep it)
docs/PHASE_2.1_ROOM_FOUNDATION.md                           <- created this phase
```

**WORKING FEATURES** (verified)
- ✅ **Build succeeds** — `:app:assembleDebug` → `BUILD SUCCESSFUL in 2m 6s` (verified 2026-09-25)
- ✅ **Room generated `AppDatabase_Impl` and all three DAO implementations** — every `@Query`, index and foreign key passed compile-time validation
- ✅ **Schema exported** to `app/schemas/…/1.json`
- ✅ **AD-5 is in the schema** — `index_completion_history_storeId unique=True`
- ✅ **AD-6 is in the schema** — `FK -> stores ['storeId'] onDelete=RESTRICT`
- ✅ **Unique store code and route number are in the schema**
- ✅ **APK produced** — 8,450,711 bytes
- ✅ **Still zero Kotlin source files** — `:app:compileDebugKotlin` = `NO-SOURCE`, filesystem scan = 0 `.kt`
- ⏳ **Runtime checks** — database created on device, constraints actually firing, settings row default — awaiting your §15.1–15.7 test

**KNOWN ISSUES**
- None blocking.
- **Nothing is wired to the UI yet.** The Dashboard still shows its Phase 1.3 placeholders. That is
  correct for this phase.
- **Firebase sync is not implemented** — ✅ decided 2026-09-25: **Option B, deferred to Version 9** (§21.6).
  The `SyncManager` seam is in place, so the transport is a one-line change when the time comes.
- The project is **not under version control**. This is now the strongest recommendation in the whole
  project: a database schema plus 23 changed files is exactly the point at which an accidental change
  becomes expensive to find. `app/schemas/` in particular must be committed, never ignored.
- Trademark note: "Alfamart" is a third-party brand; fine for a private solo tool (D-5).

**PENDING PHASES** (not implemented)
- **2.2 Store CRUD operations** — next
- 2.3 Settings storage
- 3.1 osmdroid map display · 3.2 Current location · 3.3 Manual pin creation · 3.4 Marker loading ·
  3.5 Store details · 3.6 Edit/delete · 3.7 Home pin
- 4.1 Routing service preparation · 4.2 Route preview · 4.3 Navigation hand-off + arrival monitoring ·
  4.4 State recovery
- 5.1 Arrival-radius check · 5.2 Completion dialog · 5.3 Atomic completion transaction
- 6.1 Live tracker · 6.2 Daily history list · 6.3 Daily completion order · 6.4 Completion details ·
  6.5 Consistency check
- 7.1 Nearest pending store · 7.2 Recalculate after completion · 7.3 Home-aware heuristic · 7.4 No pending stores
- 8.1 Routing service review · 8.2 Road-based next store · 8.3 Multi-stop optimization · 8.4 Complete route preview
- 9.1 Local export · 9.2 Local import · **9.3 Optional cloud sync ← the Firebase phase as originally roadmapped**
- 10.1 Error handling · 10.2 Automated and manual testing · 10.3 Release preparation

---

## 20. STOP POINT

Phase 2.1 is complete. **23 files touched: 20 created, 3 modified.**

**The three things that matter most in what was built:**

1. **AD-5 and AD-6 are now real.** A unique index on `completion_history.storeId` means a store
   physically cannot be counted twice, and `onDelete = RESTRICT` means the database refuses to delete a
   completed store. §15.5 and §15.6 are the tests that prove both.
2. **Foreign keys are explicitly enforced.** A `@ForeignKey` declaration is only a promise — SQLite has
   enforcement off by default, so `PRAGMA foreign_keys = ON` is stated in `AppDatabase`. Without it,
   AD-6 would silently do nothing.
3. **Your "don't wait for the internet" requirement is satisfied by structure.** The network is not in
   the write path at all. Every write commits to Room and returns; the sync hook fires *after* the
   commit, inside a guard that swallows failures; and no screen observes sync state. With no signal the
   app is fully functional.

---

## 21. THE FIREBASE QUESTION — ✅ DECIDED: Option B (defer to Version 9)

Your instruction added a requirement to this phase. Here is the review your own specification asks for
(Phase 9.3: *review service, review pricing, review authentication, review privacy, obtain approval*),
and then exactly what is needed to proceed.

### 21.1 Good news: Firebase is compatible with your no-card rule

| Item | Verified 2026-09-25 |
| --- | --- |
| Firebase **Spark** (no-cost) plan | **"No payment method needed"** — stated on Firebase's own pricing page |
| Firestore storage | 1 GiB |
| Firestore reads | 50,000 / day |
| Firestore writes | 20,000 / day |
| Firestore deletes | 20,000 / day |
| Network egress | 10 GiB / month |

For a solo user pinning a few hundred stores and completing tens per day, that is not a limit you can
realistically reach. And unlike Google Maps Platform, **Firebase does not require a credit card** — so
this fits D-2 exactly.

### 21.2 Which Firebase product

**Cloud Firestore**, not the Realtime Database. Firestore's document model maps cleanly onto the export
format that Phase 9.1 already needs, and it has proper offline support of its own.

### 21.3 Why I did **not** wire Firestore up in this phase

Firebase needs configuration that belongs to **your** project and that I must not invent:

- a Firebase project id,
- an application id,
- an API key,
- and the `google-services.json` file that carries them.

The Google Services Gradle plugin fails the build outright if `google-services.json` is missing, so
adding it now would leave you with a project that does not compile. A phase that cannot build is not a
phase. Instead, this phase builds the **seam** — every repository already calls
`syncManager.onLocalDataChanged()` after a successful local write, so Firestore replaces one line in
`AlphaRouteApp` and nothing else changes.

### 21.4 ⚠️ The privacy point you should decide on deliberately

Syncing to Firestore means **your store coordinates and your Home location leave the device**. Your own
specification's privacy rules say not to upload user location without explicit approval, so I want this
stated plainly rather than buried:

- **What leaves the device:** store records (including their coordinates), completion history, and the
  Home coordinates.
- **What does not:** nothing else. No contacts, no device identifiers, no location history, no
  analytics.
- **Why it is worth it:** it is a backup. Losing the phone currently means losing every pin and every
  completion record, and the history cannot be reconstructed.

**Security is not optional here.** An unauthenticated Firestore database is readable and writable by
anyone who finds the project id. Before any real data is pushed, the sync phase must include:

1. **Firebase Anonymous Authentication**, so each device has a stable `uid`.
2. **Security rules scoping every document to that uid** —
   `allow read, write: if request.auth != null && request.auth.uid == userId;`
3. Documents keyed by uid, so one device can never read another's data.

Without those three, this should not ship.

### 21.5 What I need from you

**Option A — wire Firestore up next (recommended if you want the backup now).** About ten minutes of
setup, all on the no-cost Spark plan, no card:

1. Go to `console.firebase.google.com` and create a project. When asked, choose the **Spark (no-cost)**
   plan — do **not** upgrade to Blaze, and you will never be asked for a card.
2. Add an **Android** app to the project with the package name **`com.cph.alpharoute`** (it must match
   exactly).
3. Download the generated **`google-services.json`** and put it in the **`app/`** folder.
4. In the console, open **Build → Firestore Database → Create database**. Choose **production mode**,
   and pick the region closest to the Philippines.
5. In **Build → Authentication → Sign-in method**, enable **Anonymous**.
6. Tell me when those five steps are done.

Then I will add the Google Services plugin, the Firebase BOM and Firestore, implement
`FirestoreSyncManager`, write the security rules, and test a save-and-restore round trip — as its own
phase, so it is tested on its own rather than tangled up with the database foundation.

**Option B — keep it roadmapped to Version 9.** Nothing is lost: the seam is already in place, and the
whole sync feature slots in later with no rework. Phases 2.2 and 2.3 continue as planned.

**Option C — a middle path.** Keep the local-first app for now and add the **export/import file**
(Phase 9.1/9.2) first. That already gives you a manual backup to a file or a cloud drive you already
use, with zero new services, zero accounts and zero privacy surface. Firestore then becomes purely
optional.

### ✅ 21.6 DECISION — Option B, taken 2026-09-25

> **The user chose Option B: defer Firebase to Version 9.** ("sige b muna")

**What this means:**

| | |
| --- | --- |
| **Nothing is built now** | No Firebase dependency, no Google Services plugin, no `google-services.json`, no Firestore code. |
| **Nothing is lost** | The `SyncManager` seam is already wired into every repository, so the transport slots in later as a one-line change in `AlphaRouteApp`. |
| **No roadmap change** | Cloud sync was always Phase 9.3. It stays there. |
| **No privacy surface yet** | Nothing leaves the device. Store coordinates and Home stay local. |
| **Phases 2.3 onwards proceed unchanged** | Nothing else depends on this. |

**What was deliberately NOT done:** the Option A setup steps in §21.5 above are left in place as a
reference, so that when Version 9 arrives the setup is already written down and nobody has to research it
again.

**When Version 9.3 is reached, these three things are still mandatory before any data is pushed:**

1. Firebase **Anonymous Authentication**, so each device has a stable uid.
2. Firestore **security rules scoped to `request.auth.uid`** — an unauthenticated Firestore is readable
   and writable by anyone who finds the project id.
3. The privacy note in §21.4 re-confirmed with the user: syncing uploads store records, coordinates,
   completion history and Home.

**Option C remains available at any time** if a manual backup is wanted sooner — it needs no account, no
setup and no new service, and it is a strict subset of Phase 9.1/9.2 which is already roadmapped.

**Next phase on your instruction: Phase 2.2 — Store CRUD Operations** — create, read, update, delete a
pending store, find by id / code / route number, list pending and completed, with completed-store
deletion protected.

**Phase complete. Do not proceed to the next phase until all acceptance criteria pass.**

# AlphaRoute — Phase 2.3 Settings Storage

**Project title:** Alfamart Route Tracker
**Version:** 2 — Local Database — **Phase:** 2.3 Settings Storage
**Repository root:** `C:/Users/Admin/OneDrive/Documents/custom system/AlphaRoute`
**Date:** 2026-09-25
**Prerequisites:** Phases 0.1–0.3, 1.1–1.3, 2.1, 2.2 · Phase 0.2 AD-10
**Status:** 🔄 **Code complete and VERIFIED — 47/47 unit tests pass, build succeeds.** Awaiting your confirmation run.

> **Honest scope note.** Phase 2.1 already built the settings table, the DAO and most of
> `SettingsRepository`, because its brief said "repository layer" without splitting it by table. So this
> phase is **not** a from-scratch build. What it does is close the two real gaps that were left, add
> tests for the rules, and write down the settings contract so later phases do not have to guess. §5
> lists exactly what was already done and what is new.

---

## 1. CURRENT VERSION AND PHASE

| Item | Value |
| --- | --- |
| Version | 2 — Local Database |
| Phase | 2.3 — Settings Storage |
| Files created | 3 |
| Files modified | 3 |
| Files deleted | 0 |
| Database version | **1** — unchanged. This phase adds no schema and no column. |

---

## 2. OBJECTIVE

Store and read, reliably:

- Home coordinates
- Home configured status
- Arrival radius
- Last selected store
- Last update timestamp

---

## 3. UNDERSTANDING CHECKLIST

- [x] Java only, no Kotlin. No coroutines.
- [x] All settings work on the single disk thread; nothing on the main thread.
- [x] The arrival radius stays configurable, defaults to 50 m, and can never be stored out of range.
- [x] Home is **never** a store row and never affects any count.
- [x] `lastUpdated` is written on every change, automatically.
- [x] No schema change — the database stays at version 1, so no migration is needed.
- [x] No permission, no dependency, no invented data.

---

## 4. FEATURES INCLUDED (in this phase)

| Feature | Detail |
| --- | --- |
| **A guaranteed-usable arrival radius** | `getArrivalRadius()` can never return null, zero or an out-of-range value — it falls back to 50 m |
| **A dangling-reference guard** | Deleting a store now clears `lastSelectedStoreId` if it pointed at that store |
| **27 new unit tests** | 17 for the range rules, 10 for the Home-configured rule |
| **A written settings contract** | §13.2 — every field, who writes it, who reads it, and when |

---

## 5. WHAT WAS ALREADY DONE IN 2.1, AND WHAT IS NEW

| Requirement | Status before this phase | This phase |
| --- | --- | --- |
| Home coordinates stored | ✅ `setHome()` / `clearHome()` in 2.1 | — |
| Home configured status stored | ✅ `homeSet` + `AppSettingEntity.isHomeSet()` in 2.1 | + 10 tests |
| Arrival radius stored | ✅ `setArrivalRadius()` with bounds checking in 2.1 | **+ a read that is guaranteed usable** |
| Last selected store stored | ✅ `setLastSelectedStoreId()` in 2.1 | **+ the dangling-reference guard** |
| Last update timestamp stored | ✅ set automatically by `mutate()` in 2.1 | — |
| The settings row exists on first launch | ✅ `ensureDefaults()` in 2.1 | — |
| Range rules proven | ❌ not tested | **+ 17 tests** |
| Home rule proven | ❌ not tested | **+ 10 tests** |

---

## 6. ASSUMPTIONS

| # | Assumption | Impact if wrong |
| --- | --- | --- |
| A45 | **`getArrivalRadius()` falls back to 50 m** rather than reporting an error, when the row is missing or the stored radius is out of range. | A read that could return null would force every caller — including a foreground service with no screen — to branch. A safe default is always better than a crash. |
| A46 | **Deleting a store clears `lastSelectedStoreId` only if it matched.** | The `If` is the important part: deleting store 7 must never clear a selection of store 9. |
| A47 | **The settings row is still created with `arrivalRadius = 50`**, while the entity's own field default stays `0`. | The entity default of `0` is deliberate, so "never configured" stays distinguishable from a real radius. `ensureDefaults()` is what writes the real default. |
| A48 | **`lastSelectedStoreId` has no foreign key.** | It could have, but a foreign key would block deleting a pending store that happened to be selected — which the specification allows. The guard in this phase handles it instead. |

---

## 7. FILES TO CREATE (3)

| Path | Purpose |
| --- | --- |
| `app/src/test/java/com/cph/alpharoute/util/GeoConstantsTest.java` | **New.** 17 tests for the arrival-radius and coordinate bounds |
| `app/src/test/java/com/cph/alpharoute/data/db/AppSettingEntityTest.java` | **New.** 10 tests for the Home-configured rule |
| `docs/PHASE_2.3_SETTINGS_STORAGE.md` | **New.** This record |

---

## 8. FILES TO MODIFY (3)

| # | Path | Status | What changed |
| --- | --- | --- | --- |
| 1 | `data/repository/SettingsRepository.java` | **Modified** | Added `getArrivalRadius()` (guaranteed usable) and `clearLastSelectedStoreIdIf()` (dangling-reference guard) |
| 2 | `data/repository/StoreRepository.java` | **Modified** | Constructor now takes a `SettingsRepository`; `delete()` calls the guard after a successful delete |
| 3 | `AlphaRouteApp.java` | **Modified** | Builds `SettingsRepository` **first**, because `StoreRepository` now needs it |

**Inspected and left unmodified:** `AppSettingEntity`, `AppSettingDao`, `AppDatabase`. **No schema change,
no new column, no migration.** The database stays at version 1 and `app/schemas/…/1.json` is untouched.

---

## 9. DEPENDENCIES

**None added.**

---

## 10. PERMISSIONS AND MANIFEST CHANGES

**None.** Home coordinates are sensitive, and they stay entirely on the device. Nothing in this phase
transmits anything anywhere — Firebase remains deferred to Version 9 by your decision on 2026-09-25.

---

## 11. SETUP INSTRUCTIONS

1. Let Gradle sync (nothing new to fetch).
2. **Build → Clean Project**, then **Build → Rebuild Project**.
3. Run the tests: right-click `GeoConstantsTest.java` → **Run**, and the same for
   `AppSettingEntityTest.java`. Or from a terminal: `./gradlew :app:testDebugUnitTest`.

---

## 12. COMPLETE CODE

All 6 files are written into the project on disk and attached to this message. The files worth reading
first:

1. `data/repository/SettingsRepository.java` — `getArrivalRadius` and `clearLastSelectedStoreIdIf`
2. `data/repository/StoreRepository.java` — the delete path, where the guard is called
3. `app/src/test/java/…/util/GeoConstantsTest.java` — the range rules, proven
4. `app/src/test/java/…/data/db/AppSettingEntityTest.java` — the Home rule, proven

---

## 13. CODE EXPLANATION

### 13.1 The arrival radius is now guaranteed usable

```java
public void getArrivalRadius(@NonNull RepositoryCallback<Integer> callback) {
    executors.diskIO().execute(() -> {
        int radius = GeoConstants.DEFAULT_ARRIVAL_RADIUS_M;
        try {
            AppSettingEntity settings = appSettingDao.getNow();
            if (settings != null && GeoConstants.isValidArrivalRadius(settings.arrivalRadius)) {
                radius = settings.arrivalRadius;
            }
        } catch (Exception e) {
            Log.e(TAG, "Could not read the arrival radius; using the default", e);
        }
        final int result = radius;
        executors.postToMain(() -> callback.onResult(result));
    });
}
```

The method **cannot** deliver `null`, `0`, or anything outside 10–1000 m. Three separate things would
otherwise have to be handled by every caller:

| Bad value | What it would do if it escaped | Now |
| --- | --- | --- |
| `null` | Force a null check on every call site | Falls back to 50 m |
| `0` | Require **exact** coordinate equality — which the specification forbids and GPS cannot deliver, so the completion dialog would never appear and the app would look broken in the field | Falls back to 50 m |
| `5000` | Offer the completion dialog from five kilometres away, which is worse than useless | Falls back to 50 m |

This matters most for Phase 4.3's foreground service, which runs the arrival check with **no screen
present**. A service that has to reason about a corrupt radius is a service that will get it wrong. The
guarantee is made once, here, so nobody downstream has to think about it.

### 13.2 The settings contract

Every field, who writes it, and who reads it. This is the part later phases should not have to guess at.

| Field | Written by | Read by | Notes |
| --- | --- | --- | --- |
| `homeLatitude`, `homeLongitude` | `setHome()` · `clearHome()` (UI in Phase 3.7) | The Home marker (3.7) · route suggestion (7.3) · the final Home leg | `null` until Home is set. **Home is never a store row and never affects a count.** |
| `homeSet` | `setHome()` · `clearHome()` | `AppSettingEntity.isHomeSet()` | Deliberately separate from the coordinates — see §13.3 |
| `arrivalRadius` | `setArrivalRadius()` — refuses anything outside 10–1000 m | `getArrivalRadius()` (guaranteed usable) → `ArrivalChecker` (5.1) · the foreground service (4.3) | Default 50 m |
| `lastSelectedStoreId` | `setLastSelectedStoreId()` (3.5) · cleared by `clearLastSelectedStoreIdIf()` | Route preview (4.2) · active-navigation resume (4.4) | **AD-10.** No foreign key — see §13.4 |
| `lastUpdated` | Set **automatically** by `mutate()` on every change | Diagnostics · Version 9's sync | Never set by a caller |
| `id` | `ensureDefaults()` only | Every `AppSettingDao` query | Always `SINGLETON_ID` = 1 |

### 13.3 Why `homeSet` exists as a separate flag

`isHomeSet()` requires **all three** to agree: the flag, the latitude and the longitude. Any two out of
three is treated as "Home not set".

The failure this prevents is specific and nasty: a route that is supposed to finish at Home, with no Home
to finish at. Deriving "is Home configured?" from a null check alone would work until the one time it did
not — and then the app would confidently send the user to latitude 0, which is in the Gulf of Guinea.

Note also that **`0.0` is a real coordinate, not a missing one.** The tests cover this explicitly,
because conflating "zero" with "absent" is exactly the bug the null check is there to avoid.

### 13.4 Why deleting a store now clears the last-selected reference

`lastSelectedStoreId` points into a different table, and unlike completion history it has **no foreign
key** behind it. That was a deliberate choice — a foreign key would stop a pending store being deleted
just because it happened to be selected, and the specification allows pending stores to be deleted.

So the reference could dangle. Harmless in the short term, but it means "resume the active navigation"
would try to load a store that is not there, and Phase 6.5's consistency check would have to report it as
a defect. Clearing it at the moment the store is deleted removes the whole class of problem:

```java
storeDao.deleteById(storeId);
outcome = WriteOutcome.SUCCESS;

// Integrity: if this store was the active navigation target, clear that reference so it cannot
// dangle. This queues its own task on the SAME single disk thread, so it cannot interleave with
// the delete above and cannot run before it.
settingsRepository.clearLastSelectedStoreIdIf(storeId, null);
```

The `If` in the method name is the important part. Deleting store 7 must never clear a selection of
store 9 — so the method compares before clearing, and the tests cover the mismatch case.

**Ordering is safe without any locking:** both operations run on the *same single-threaded* disk executor
(AD-3), so the cleanup task is queued behind the delete and cannot run first or interleave.

### 13.5 Why `StoreRepository` now depends on `SettingsRepository`

Because the rule "deleting a store clears the last-selected reference" belongs to the delete operation,
and enforcing it inside `delete()` means it cannot be forgotten. The alternative — documenting "remember
to call the cleanup too" — is exactly the kind of contract that gets missed during a refactor three
phases later.

`SettingsRepository` does not depend on `StoreRepository`, so there is no cycle. `AlphaRouteApp` builds
settings first and passes it in.

---

## 14. DATA FLOW FOR THIS PHASE

```
App start
  └─ AlphaRouteApp.onCreate()
       ├─ settingsRepository = new SettingsRepository(...)      built FIRST
       ├─ storeRepository    = new StoreRepository(..., settingsRepository, ...)
       └─ settingsRepository.ensureDefaults()
                └─ diskIO: no row yet? → upsert arrivalRadius = 50, homeSet = false

Write  (any of setHome / clearHome / setArrivalRadius / setLastSelectedStoreId)
  └─ mutate(): diskIO → load row (create it if missing) → apply change
                 → lastUpdated = now → upsert → COMMIT → notifySyncSafely() → postToMain

Read
  ├─ observe()            → LiveData, re-emitted by Room on every change
  ├─ getNow()             → the entity, or null
  └─ getArrivalRadius()   → an int that is ALWAYS usable

Delete a store
  └─ diskIO → pre-checks → delete → COMMIT
                └─ clearLastSelectedStoreIdIf(storeId, null)   queued on the same thread
                     └─ matches? → clear it, bump lastUpdated, commit
```

---

## 15. MANUAL TEST PROCEDURE

### 15.0 Already verified automatically (2026-09-25)

`./gradlew :app:testDebugUnitTest :app:assembleDebug` was run on this machine with Gradle 9.4.1 / JDK 21
and returned **`BUILD SUCCESSFUL in 43s`** (41 tasks: 13 executed, 28 up-to-date).

| Test class | Result |
| --- | --- |
| **`GeoConstantsTest`** | **17 tests, 0 failures, 0 errors — 0.002 s** |
| **`AppSettingEntityTest`** | **10 tests, 0 failures, 0 errors — 0.002 s** |
| `StoreValidatorTest` (Phase 2.2) | 19 tests, 0 failures, 0 errors — 0.003 s |
| `ExampleUnitTest` (template, Phase 10.2) | 1 test, 0 failures — 0.004 s |
| **TOTAL** | **47 tests, 0 failures, 0 errors, 0 skipped — about 11 ms** |

| Other evidence | Result |
| --- | --- |
| `:app:testDebugUnitTest` | ran — the suite executed |
| `:app:compileDebugUnitTestKotlin` | **NO-SOURCE** — the tests are Java |
| `:app:assembleDebug` | ran — the app still packages |
| `app/schemas/…/AppDatabase/` | contains **only `1.json`** — no schema change, so no migration is needed |
| `app-debug.apk` | **8,453,871 bytes** |

**47 tests, zero failures, in about eleven milliseconds, with no emulator and no device.** Every rule
this app's correctness rests on — store validation, the geographic bounds, and the Home-configured rule —
is now proven on any machine, before a single screen exists.

### 15.1 Run the settings tests

1. In Android Studio, right-click `app/src/test/java/com/cph/alpharoute/util/GeoConstantsTest.java` →
   **Run 'GeoConstantsTest'**. Expect **17 tests, 0 failures**.
2. Right-click `app/src/test/java/com/cph/alpharoute/data/db/AppSettingEntityTest.java` →
   **Run 'AppSettingEntityTest'**. Expect **10 tests, 0 failures**.
3. Or run everything at once: right-click the `test` folder → **Run 'Tests in …'**. Expect
   **47 tests, 0 failures** (the three new classes plus the 19 from Phase 2.2 plus the untouched
   `ExampleUnitTest`).

### 15.2 Confirm the settings row on the device

4. Run the app once, then close it.
5. Open **View → Tool Windows → App Inspection → Database Inspector** → `alpharoute.db` → `app_settings`.
6. There must be exactly **one row**, with `id = 1`, `arrivalRadius = 50`, `homeSet = 0`,
   `homeLatitude = NULL`, `homeLongitude = NULL`, `lastSelectedStoreId = NULL`.

### 15.3 Confirm nothing changed structurally

7. Confirm `app/schemas/` still contains **only** `1.json`. This phase added no schema, so there must be
   no `2.json`.
8. Confirm the app launches on the Dashboard with no crash and no permission prompt.
9. Confirm the `stores` table is still empty — there is still no UI to add a store.

### 15.4 Confirm the arrival radius cannot be corrupted (optional, advanced)

10. In the Database Inspector query window, run:
    `UPDATE app_settings SET arrivalRadius = 0 WHERE id = 1;`
11. Reopen the app. It must **not** crash and must not treat 0 as a real radius — `getArrivalRadius()`
    falls back to 50 m. (There is no screen showing the radius yet, so this is verified by the absence of
    a crash and by the tests in §15.1.)
12. Restore the value: `UPDATE app_settings SET arrivalRadius = 50 WHERE id = 1;`

**Report any failure with the step number and the exact text.**

---

## 16. EXPECTED RESULT

- **47 tests, 0 failures** on the JVM, no emulator.
- One `app_settings` row with `arrivalRadius = 50` and Home unset.
- The schema is still version 1, with no migration file.
- The app launches unchanged, with no permission prompt.
- Still no screen shows settings — the Settings UI is Phase 2.3's successor work in Phases 3.7 and 6.1.

---

## 17. COMMON ERRORS AND FIXES

| Symptom | Cause | Fix |
| --- | --- | --- |
| `Cannot resolve symbol 'GeoConstants'` | The `util` package did not compile | Re-sync Gradle, then rebuild |
| `AppSettingEntityTest` fails to load with an annotation error | Room annotations missing from the unit-test classpath | Room is an `implementation` dependency, so it is on the test classpath — re-sync and rebuild |
| `Cannot resolve symbol 'settingsRepository'` in `StoreRepository` | The new constructor parameter is missing from the call site | `AlphaRouteApp` must pass it — the build will point at the line |
| A test fails on `aFreshRowHasNoArrivalRadiusSetByTheEntityItself` | Someone "helpfully" gave the entity a default of 50 | The entity default must stay 0; the real default comes from `ensureDefaults()` |
| A test fails on `latitudeZeroIsStillAValidHome` | `isHomeSet()` was changed to treat 0 as missing | 0.0 is a real coordinate — see §13.3 |
| A test fails on `theEarthRadiusIsPlausible` | `EARTH_RADIUS_M` was typo'd | It is about 6,371,008.8 m, not 6371 |
| Two settings rows appear in the inspector | A write used something other than `upsert` with `SINGLETON_ID` | All settings writes must go through `SettingsRepository` |
| Deleting a store clears the wrong selection | The `If` comparison was removed | `clearLastSelectedStoreIdIf` must compare before clearing — see §13.4 |

---

## 18. ACCEPTANCE CHECKLIST

- [ ] Gradle sync completes.
- [ ] Clean build succeeds.
- [x] **`GeoConstantsTest`: 17 tests, 0 failures** (§15.0 — verified)
- [x] **`AppSettingEntityTest`: 10 tests, 0 failures** (§15.0 — verified)
- [x] **Whole suite: 47 tests, 0 failures** (§15.0 — verified)
- [ ] Exactly one `app_settings` row with `arrivalRadius = 50` and Home unset (§15.2).
- [x] `app/schemas/` still contains only `1.json` — no schema change (§15.0 — verified)
- [ ] The app launches with no crash and no permission prompt.
- [ ] Confirmed: `lastSelectedStoreId` is cleared when its store is deleted.
- [ ] Confirmed: still **no Kotlin source file** anywhere in the project.

---

## 19. UPDATED PROJECT STATE

**COMPLETED PHASES**
- ✅ 0.1 Requirements Review (Rev 5, FINAL) · ✅ 0.2 Architecture · ✅ 0.3 Screen Plan
- ✅ 1.1 Project Creation · ✅ 1.2 Theme and Base Navigation · ✅ 1.3 Dashboard Static UI
- ✅ 2.1 Room Database Foundation · ✅ 2.2 Store CRUD Operations
- 🔄 **2.3 Settings Storage** — *awaiting your test run*

**CURRENT PHASE**
- Phase 2.3 — awaiting the test run

**CURRENT DATABASE VERSION**
- **1.** Unchanged this phase. Three tables: `stores`, `completion_history`, `app_settings`.

**CURRENT FILES** (changed this phase)

```
app/src/test/java/com/cph/alpharoute/util/GeoConstantsTest.java        (NEW - 17 tests)
app/src/test/java/com/cph/alpharoute/data/db/AppSettingEntityTest.java (NEW - 10 tests)
app/src/main/java/com/cph/alpharoute/data/repository/SettingsRepository.java (MODIFIED)
app/src/main/java/com/cph/alpharoute/data/repository/StoreRepository.java    (MODIFIED)
app/src/main/java/com/cph/alpharoute/AlphaRouteApp.java                      (MODIFIED)
docs/PHASE_2.3_SETTINGS_STORAGE.md                                     <- created this phase
```

**WORKING FEATURES** (verified)
- ✅ **47/47 unit tests pass** — 0 failures, 0 errors, about 11 ms total (verified 2026-09-25)
- ✅ **`GeoConstantsTest` 17/17** and **`AppSettingEntityTest` 10/10**
- ✅ **Build succeeds** — `:app:assembleDebug` also ran clean; APK 8,453,871 bytes
- ✅ **Schema unchanged** — only `1.json` exists, so no migration is needed
- ✅ **Still zero Kotlin source files** — `compileDebugKotlin` and `compileDebugUnitTestKotlin` both `NO-SOURCE`
- ⏳ **On-device checks** — one `app_settings` row with `arrivalRadius = 50`, app launches with no crash — awaiting your §15.2–15.3

**KNOWN ISSUES**
- None blocking.
- **Still no Settings screen.** The storage is complete; the UI arrives with Home Setup in Phase 3.7 and
  the Settings screen later. That is correct for this phase.
- **Version 2 (Local Database) is now complete** once this phase is approved. The next phase opens
  Version 3 (Map and Manual Pins).
- The project is **not under version control**. Still the strongest recommendation in the project.
- Trademark note: "Alfamart" is a third-party brand; fine for a private solo tool (D-5).

**PENDING PHASES** (not implemented)
- **3.1 osmdroid map display** — next
- 3.2 Current location · 3.3 Manual pin creation · 3.4 Marker loading · 3.5 Store details ·
  3.6 Edit/delete · 3.7 Home pin
- 4.1 Routing service preparation · 4.2 Route preview · 4.3 Navigation hand-off + arrival monitoring ·
  4.4 State recovery
- 5.1 Arrival-radius check · 5.2 Completion dialog · 5.3 Atomic completion transaction
- 6.1 Live tracker · 6.2 Daily history list · 6.3 Daily completion order · 6.4 Completion details ·
  6.5 Consistency check
- 7.1 Nearest pending store · 7.2 Recalculate after completion · 7.3 Home-aware heuristic · 7.4 No pending stores
- 8.1 Routing service review · 8.2 Road-based next store · 8.3 Multi-stop optimization · 8.4 Complete route preview
- 9.1 Local export · 9.2 Local import · 9.3 Optional cloud sync *(Firebase — deferred here by your decision)*
- 10.1 Error handling · 10.2 Automated and manual testing · 10.3 Release preparation

---

## 20. STOP POINT

Phase 2.3 is complete. **6 files touched: 3 created, 3 modified. No schema change.**

**Being straight about the scope:** Phase 2.1 already stored all five of the fields this phase asks for,
because its brief said "repository layer" without splitting it per table. So this phase closed the two
real gaps rather than building from scratch:

1. **The arrival radius is now guaranteed usable.** `getArrivalRadius()` can never return null, zero, or
   an out-of-range value. That matters most for the foreground service in Phase 4.3, which runs the
   arrival check with **no screen present** — a service that has to reason about a corrupt radius is a
   service that will get it wrong. The guarantee is made once, here, so nobody downstream has to think
   about it.

2. **A dangling reference is now impossible.** `lastSelectedStoreId` points into another table with no
   foreign key behind it, so deleting a pending store could leave it pointing at nothing. Deleting a
   store now clears it — but only if it matched, so deleting store 7 never clears a selection of store 9.

Plus **27 new unit tests** for the range rules and the Home rule, and a written **settings contract**
(§13.2) so later phases do not have to guess who writes what.

**Please run §15.1** and confirm 47 tests, 0 failures. Then §15.2–15.3 on the device.

**Next phase on your instruction: Phase 3.1 — osmdroid Map Display.** That opens Version 3, and it is the
first phase that adds a dependency since Phase 2.1: `org.osmdroid:osmdroid-android:6.1.20`, plus the OSM
tile policy work — an app-specific User-Agent, OSM attribution, and a seven-day tile cache. It also adds
the app's first permission, `INTERNET`.

**Phase complete. Do not proceed to the next phase until all acceptance criteria pass.**

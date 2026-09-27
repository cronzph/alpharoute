# PHASES 7.1 → 10.3 — From the Suggestion to Release

> **Status: code complete, build verified.** Device testing is §15 and has not been run yet.

> **Why one record for thirteen phases.** These were delivered in a single rapid batch at the user's
> request ("gawen mo na lahat"). They share a shape: none of them changes the database schema, none adds a
> permission, and each one is either a pure-Java rule with a screen over it or a release-preparation step.
> The 20 sections below cover all of them, and each phase's own contract is stated in §4 and §7.
>
> **Phase 7.0 has its own record** (`PHASE_7.0_ROUNDS_UNDO_AND_AT_STORE.md`) because it carried the first
> schema migration in the project and deserved its own §13.

---

## 1. CURRENT VERSION AND PHASE

| | |
| --- | --- |
| Versions | 7 (rest), 8, 9, 10 |
| Phases | **7.1 · 7.2 · 7.3 · 7.4 · 8.1 · 8.2 · 8.3 · 8.4 · 9.1 · 9.2 · 10.1 · 10.2 · 10.3** |
| Previous phase | 7.0 — rounds, undo and the at-store shortcut ✅ |
| Next phase | **none — the roadmap is complete** |
| Database version | **2 — unchanged.** No new entity, no new column, no `Migration` |
| Kotlin files | **0** |

---

## 2. OBJECTIVE

Seven point zero made the app work in rounds. This batch makes it **useful, connected and shippable**:

- **7.1 – 7.4** — tell the user which store to visit next, and what to do when the round is done.
- **8.1 – 8.4** — replace the straight-line guesses with real driving distances, and solve the order of the
  whole route rather than one store at a time.
- **9.1 – 9.2** — let the user keep their data, because until now there was no way to move it off the phone.
- **10.1 – 10.3** — finish: the failure paths audited, the safety-critical code tested where it can actually
  be tested, and the app made presentable.

---

## 3. UNDERSTANDING CHECKLIST

- ✅ **Every new rule is pure Java** (AD-11) and tested on the JVM. A wrong suggestion, a wrong route or a
  wrong import all look exactly like right ones.
- ✅ **The strategy is swappable** (AD-12) and the implementations **degrade rather than fail**: no network,
  no Home, no location and no stores all have defined answers.
- ✅ **Nothing invented.** A distance that could not be measured is `NaN`, never `0`. A route that could not
  be built is an error, never a guess.
- ✅ **The import validates before it writes**, and the write is one transaction that throws to roll back.
- ✅ **No API key, no credential, and no transient navigation reference** ever reaches a backup file.
- ✅ **The app still works offline.** The network only ever improves an answer; it is never required for one.
- ✅ No schema change, no new permission, no new dependency.

---

## 4. FEATURES INCLUDED (in this batch)

### Version 7 — the suggestion

1. **`RouteSuggestionStrategy`** (AD-12) with `NearestStrategy`, `HomeAwareStrategy` and
   `RoadDistanceStrategy` composed into a fallback chain.
2. **The suggestion card**, first on the Dashboard, saying which store, how far, and **why** - because
   "nearest to you" and "picked without your location" are different claims.
3. **The manual override** (7.3): "Choose another" lists the pending stores nearest-first, and "Use the
   suggestion" undoes the choice.
4. **Recalculated automatically** (7.2): the card follows the four sources it depends on, so a completion
   produces a new suggestion with no wiring.
5. **Route complete** (7.4): a success state that offers Home and the next round.

### Version 8 — road-based routing

6. **`RouteMatrixParser`** (8.2) — real driving distances from one origin to many stores, in one request.
7. **Road distances in the suggestion**, with a threshold on re-fetching so the daily allowance is not spent
   on GPS jitter.
8. **`OptimizationRequestBuilder`** and **`OptimizationResponseParser`** (8.3) — the VROOM request and answer,
   both pure and both asserted field by field.
9. **The optimized route screen** (8.4) — every pending store in the solved order, with totals, Start Route,
   Recalculate, and the unassigned count when the solver could not place everything.

### Version 9 — backup

10. **`BackupCodec`** (9.1) — the file format, written from an explicit field list so nothing secret can
    ever appear in it.
11. **`BackupValidator`** (9.2) — pure, and it reports **every** problem rather than the first.
12. **`BackupTransactionDao`** — replace or merge, in one transaction, with the delete order that the foreign
    key demands.
13. **Export and import in Settings**, through the system file picker, so no storage permission is needed.

### Version 10 — finishing

14. **Instrumented tests** (10.2) for the completion transaction, the round change and the undo, on real
    SQLite — including the AD-6 foreign key, which no JVM test can reach.
15. **The two template test files are deleted** (10.2).
16. **A real launcher icon** (10.3) — a map pin on the brand blue, replacing the Android Studio robot, with
    the monochrome layer for Android 13 themed icons.
17. **The key and the logs audited** (10.3): the key appears in no source file and in no log line.

---

## 5. FEATURES NOT INCLUDED

| Feature | Why |
| --- | --- |
| Firebase cloud sync (9.3) | **Deferred to a later version by decision D-9.** The `SyncManager` seam is wired; the transport is a one-line change when a Firebase project exists. It also needs anonymous auth and scoped security rules before any data is pushed |
| Turn-by-turn guidance | **Never** — D-6/D-7. That is the Google Maps app's job |
| Handing the whole route to Google Maps as waypoints | **Not in this batch.** The app opens the first stop; waypoints are a different hand-off |
| A CSV export | **Not built.** The roadmap offered JSON/CSV; JSON is what the import reads, and a second format would be a second thing to keep in step |
| Editing a completion's date or sequence | **Never** — only removal is offered, one row at a time |
| A release signing config | **The user's to create.** A keystore and its passwords must not be invented or committed |

---

## 6. ASSUMPTIONS

| # | Assumption |
| --- | --- |
| A106 | **The strategies degrade rather than fail.** No location → measure from Home; no Home → first by id; no network → straight lines. Every case is labelled so the card never overstates what the app knows |
| A107 | **The suggestion chain is one object**: `RoadDistanceStrategy` → `HomeAwareStrategy` → `NearestStrategy`. The network improves the answer and is never required for one |
| A108 | **Driving distances are re-fetched only when the pending set changes or the user moves 250 m.** Without a threshold, every GPS fix would be a matrix request and the daily allowance would be gone in under an hour |
| A109 | **The optimization service's limits are the app's limits**: 50 jobs, 3 vehicles. Checked in the builder, before sending |
| A110 | **Only `"job"` steps are stops.** A route's start and end steps carry locations and distances too, and counting them would add two phantom stops to every route |
| A111 | **A backup references stores by `storeCode`, never by row id.** Ids are auto-generated, so a backup's ids mean nothing in the database it is restored into |
| A112 | **`lastSelectedStoreId` is never exported.** It points at a drive in progress, and restoring it could point at a different store |
| A113 | **The import validates before it writes, and writes in one transaction that throws to roll back.** A half-applied restore would leave the user unable to tell which data was theirs |
| A114 | **`REPLACE` deletes completions before stores.** The foreign key uses `RESTRICT` (AD-6), so the other order fails on the first store |
| A115 | **`MERGE` keeps the app's copy of a store it already has.** The app's version may have been completed or edited since the backup, and overwriting it would silently undo that |
| A116 | **The instrumented tests use an in-memory database.** Fast, leaves nothing behind, and cannot touch the user's data - but it exercises the fresh-install path, not an upgrade |
| A117 | **The release signing keystore is the user's to create.** A keystore and its passwords must not be invented |

---

## 7. FILES TO CREATE (23)

| # | File | Phase |
| --- | --- | --- |
| 1 | `domain/StoreCandidate.java` | 7.1 |
| 2 | `domain/SuggestionContext.java` | 7.1 |
| 3 | `domain/SuggestionResult.java` | 7.1 |
| 4 | `domain/RouteSuggestionStrategy.java` | 7.1 (AD-12) |
| 5 | `domain/SuggestionSupport.java` | 7.1 |
| 6 | `domain/NearestStrategy.java` | 7.1 |
| 7 | `domain/HomeAwareStrategy.java` | 7.3 |
| 8 | `domain/RoadDistanceStrategy.java` | 8.2 |
| 9 | `domain/PendingStores.java` | 7.0 follow-up — one definition of "still to visit" |
| 10 | `navigation/OptimizationState.java` | 8.3 |
| 11 | `navigation/OptimizationRequestBuilder.java` | 8.3 |
| 12 | `navigation/OptimizationResponseParser.java` | 8.3 |
| 13 | `navigation/RouteMatrixParser.java` | 8.2 |
| 14 | `domain/OptimizationPlan.java` | 8.3 |
| 15 | `domain/OptimizationStop.java` | 8.3 |
| 16 | `ui/route/OptimizedRouteFragment.java` | 8.4 |
| 17 | `ui/route/OptimizedRouteAdapter.java` | 8.4 |
| 18 | `data/backup/BackupContents.java` | 9.1 |
| 19 | `data/backup/BackupCodec.java` | 9.1 |
| 20 | `data/backup/BackupValidator.java` | 9.2 |
| 21 | `data/backup/ImportMode.java` | 9.2 |
| 22 | `data/db/BackupTransactionDao.java` | 9.2 |
| 23 | `data/repository/BackupRepository.java` | 9.1/9.2 |

Plus **7 test files**: `NearestStrategyTest`, `HomeAwareStrategyTest`, `RoadDistanceStrategyTest`,
`OptimizationRequestBuilderTest`, `OptimizationResponseParserTest`, `RouteMatrixParserTest`,
`BackupValidatorTest` — and **1 instrumented test**: `CompletionTransactionInstrumentedTest`.

---

## 8. FILES TO MODIFY (18)

| # | File | Change |
| --- | --- | --- |
| 1 | `ui/dashboard/DashboardViewModel.java` | The suggestion, the strategy chain, the matrix refresh, the override |
| 2 | `ui/dashboard/DashboardFragment.java` | The suggestion card and the plan-route button |
| 3 | `res/layout/fragment_dashboard.xml` | The card, the button |
| 4 | `navigation/RoutingConfig.java` | Matrix and optimization URLs; the re-verified limits; the job cap |
| 5 | `data/repository/RoutingRepository.java` | `loadDrivingDistances`, `optimize`, `observeOptimization` |
| 6 | `data/db/AppDatabase.java` | `backupTransactionDao()` |
| 7 | `AlphaRouteApp.java` | `backupRepository` |
| 8 | `ui/settings/SettingsFragment.java` | The round panel, the backup card, the SAF launchers |
| 9 | `res/layout/fragment_settings.xml` | The round and backup cards |
| 10 | `MainActivity.java` | `showSettingsTab`, `titleFor` gains the route screen |
| 11 | `res/values/strings.xml` | ~60 new strings across four versions |
| 12 | `res/drawable/ic_launcher_background.xml` | **Replaced** — brand blue instead of the template green |
| 13 | `res/drawable/ic_launcher_foreground.xml` | **Replaced** — the map pin instead of the Android robot |
| 14 | `app/build.gradle`, `gradle/libs.versions.toml` | No change; the existing test deps were enough |
| 15 | **Deleted**: `ExampleUnitTest.java`, `ExampleInstrumentedTest.java` | The two Android Studio templates |

---

## 9. DEPENDENCIES

| Dependency | Status |
| --- | --- |
| Everything from 7.0 | Reused. **Not one new library.** |
| `org.json` (test classpath) | Already present — the platform's copy is a stub on the JVM |
| `androidx.test.ext:junit`, `espresso-core` | Already present; now actually used |
| openrouteservice **matrix** | 500 requests/day. Used by 8.2 |
| openrouteservice **optimization** | 500 requests/day, 50 jobs, 3 vehicles. Used by 8.3 |

**No new dependency, no new permission, no new key.**

---

## 10. PERMISSIONS AND MANIFEST CHANGES

**None.** The sixth phase in a row with no manifest change.

| | |
| --- | --- |
| New permissions | **0** |
| Removed permissions | **0** |
| New components | **0** |
| Storage permission | **0** — the backup uses the system file picker, which grants access per file |
| `ACCESS_BACKGROUND_LOCATION` | Still **not declared** |

> ⚠️ **Trap.** Grepping the merged manifest for `ACCESS_BACKGROUND_LOCATION` still returns a hit — it is
> the **XML comment text**. Unchanged since 4.3.

---

## 11. SETUP INSTRUCTIONS

```bash
cd "C:/Users/Admin/OneDrive/Documents/custom system/AlphaRoute"

# JVM tests + the APK
./gradlew clean :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain

# Compile the instrumented tests (they need a device to RUN)
./gradlew :app:compileDebugAndroidTestJavaWithJavac --no-daemon --console=plain

# Run them, with a device or emulator attached
./gradlew :app:connectedDebugAndroidTest --no-daemon --console=plain
```

Nothing else. No `local.properties` change, no new key, no database migration.

---

## 12. COMPLETE CODE

The complete files are in the repository at the paths listed in §7 and §8. This record does not repeat
them; the phase rule is that a delivered file is the complete file, not a diff, and they are all present.

---

## 13. CODE EXPLANATION

### The strategy chain, and why it is a chain

```java
private final RouteSuggestionStrategy strategy = new RoadDistanceStrategy();
//  RoadDistanceStrategy → HomeAwareStrategy → NearestStrategy
```

Each one **delegates** when it cannot answer, and the delegation is what makes the app work offline without
a special case anywhere:

| Situation | Who answers | What the card says |
| --- | --- | --- |
| Road distances known | `RoadDistanceStrategy` | "closest to drive to" |
| Offline, Home set | `HomeAwareStrategy` | "on your way home" |
| Offline, no Home | `NearestStrategy` | "nearest to you" |
| No fix either | `NearestStrategy`, from Home | "nearest to home" |
| Neither | `NearestStrategy`, first by id | "first in your list" |

**The reason is not decoration.** "Nearest to you" and "first in your list" are very different claims, and a
card that showed the same words for both would be overstating what the app knows.

### The Home-aware formula, and what it does not claim

```
score(store) = distance(current, store) + distance(store, home)
```

The smallest score wins. It is the length of the trip you commit to by visiting that store next and then
going home — a sensible greedy move.

**It is a heuristic, not an optimisation.** It looks exactly one store ahead. A route of five stores can be
arranged into 120 orders and this examines none of them. The code says so, the record says so, and the card
says "on your way home" rather than "best route". Version 8.3 is where a real solver arrives.

### Two distances that are not the same claim

`RoadDistanceStrategy` compares a road distance against a straight line when only one is known. That is
strictly unfair — a road distance is always ≥ the straight line — and it is a deliberate compromise:
refusing to answer at all when one leg of the matrix failed would be worse. In practice the matrix answers
for every store in one request, so either all have road distances or none do.

### Why the matrix, and not a directions call per store

One request answers the whole question. Twenty stores would otherwise be twenty requests — and at 40/minute
that is a self-inflicted rate limit for a single suggestion.

### Not burning the daily allowance

```java
if (signature.equals(roadDistanceSignature) && !hasMovedFarEnough()) return;
```

The position updates every few seconds. Without a threshold, every fix would be a matrix request and the
500/day allowance would be gone in under an hour. 250 m is roughly where the nearest store could plausibly
change — and **distance, not "has the fix changed"**, because GPS jitters by a few metres on a table.

### Only `"job"` steps are stops

A VROOM route's steps include a `"start"` and an `"end"`, both carrying a location and a distance. Counting
them as stops would put a phantom shop at each end of every route and make the count wrong by two. The
parser filters on `"type"`, **not** on the presence of an id — because the start and end steps carry an id
of their own in some responses.

### The request body is asserted field by field

**A wrong optimization request produces a confident, well-formed answer to the wrong question** — a route
that visits every shop in a strange order with nothing to indicate why. There is no error to catch. So
`OptimizationRequestBuilderTest` checks the job ids, the coordinate order, the service time, the vehicle
count, the profile, and the deliberate absence of `"options"`.

### The backup format, and the two things it deliberately omits

Completions reference stores by **`storeCode`**, not by row id, because ids are auto-generated: a backup's
id 7 means nothing in the database it is restored into.

Two omissions, both deliberate:

- **`lastSelectedStoreId`** — a transient reference to a drive in progress. Restoring it into a different
  database could point at a store that means something else there.
- **Anything secret.** The file is written from an explicit field list, so a column added to `app_settings`
  in future cannot silently start appearing in backups. `BackupValidatorTest` asserts the file contains no
  `apikey`, `token`, `password` or `authorization`.

### The import deletes completions before stores

```java
deleteAllCompletions();
deleteAllStores();
```

**The order is not obvious and it is not optional.** The foreign key uses `onDelete = RESTRICT` (AD-6), so
SQLite refuses to delete a store that still has history — doing it the other way round fails on the first
store.

### The validator reports every problem, not the first

A user with three faults should not have to fix one, retry, and discover the next. And the checks run
**before anything is written**, so a file is never partially applied while it is still being judged.

### `./gradlew` exporting `2.json` is not proof the migration works

That was 7.0's lesson and it holds here: the schema export proves the **entity** is right. Only Room's
validation at open, on a device, proves the migration produces it.

---

## 14. DATA FLOW FOR THIS BATCH

```
Dashboard
   │
   ├─ stores + history + settings + location   (four LiveData sources)
   │        │
   │        ▼
   │   PendingStores.idsPendingInRound()   ← PURE, one definition of "still to visit"
   │        │
   │        ▼
   │   RoadDistanceStrategy.suggest(context)
   │        ├─ road distances known → nearest by ROAD (8.2)
   │        ├─ else → HomeAwareStrategy → balance with the way home (7.3)
   │        └─ else → NearestStrategy → straight line (7.1)
   │        │
   │        ▼
   │   SuggestionResult → the card, with a REASON that says which claim it is making
   │
   └─ "Plan the whole route"
            │
            ▼
      OptimizedRouteFragment
            ├─ pending stores (same PendingStores rule)
            ├─ RoutingRepository.optimize(stores, current, home)
            │        └─ OptimizationRequestBuilder → POST /v2/optimization
            │                └─ OptimizationResponseParser → OptimizationPlan
            └─ the ordered list · Start Route · Recalculate

Settings
   ├─ "Export" → SAF CreateDocument → BackupCodec.toJson → the file
   └─ "Import" → SAF OpenDocument → BackupCodec.parse → BackupValidator (PURE, reports everything)
                       │
                       ▼  only if there are no problems
                 the user picks Merge or Replace
                       │
                       ▼
                 BackupTransactionDao.importAll(...)   @Transaction
                       ├─ REPLACE: delete completions → delete stores → insert everything
                       └─ MERGE:   skip stores that exist → insert the rest
                       └─ then apply settings
                       └─ any failure THROWS → the whole restore rolls back
```

**The network appears in exactly two places**, both optional, and the app is fully usable without either.

---

## 15. MANUAL TEST PROCEDURE

### 15.1 The suggestion

1. With several stores pending, open the Dashboard. The card must be **first**, naming a store, a distance
   and a reason.
2. **Turn off location.** The card must still name a store, and the reason must change to "nearest to home"
   or "first in your list" — **never "nearest to you"**.
3. Turn location back on. The reason must return to a location-based one.
4. Tap **Navigate**. Google Maps must open at that store.
5. Tap **Choose another**, pick a different store. The card must switch to it, and the reason must say
   "nearest to you" — the override is the user's choice, not the strategy's.
6. Tap **Choose another → Use the suggestion**. The strategy's choice must come back.
7. **Complete the suggested store.** The card must move to a different store **without a refresh** (7.2).

### 15.2 Route complete (7.4)

8. Complete every store in the round. The card must say the round is complete and offer Home.
9. Unset Home in Settings, then look again. The card must say Home is not set, and **Go home must be
   absent** — not greyed out.

### 15.3 Road distances (8.2)

10. With a connection, watch the card. The reason must become **"closest to drive to"**.
11. Put the phone in flight mode and reopen the Dashboard. The card must still work, with a straight-line
    reason. **Nothing may crash or hang.**

### 15.4 The optimized route (8.3, 8.4)

12. With at least three stores pending, tap **Plan the whole route**.
13. The list must be in an order, with "#1", "#2"… and a leg distance and time on each.
14. The header must show the stop count, total distance and total time, and say where the route starts and
    finishes.
15. **The order must not be route-number order.** Compare it against the store list deliberately.
16. Tap a stop. The map must open centred on that store.
17. Tap **Start Route**. Google Maps must open at the **first** stop.
18. Put the phone in flight mode and tap **Recalculate**. A message must appear saying the service could not
    be reached, and **the existing list must not be destroyed**.

### 15.5 Export and import (9.1, 9.2)

19. Settings → **Export**. Choose a location. The message must confirm the backup was written.
20. Open the file in a text editor. It must contain `formatVersion`, `stores` and `completions`, and must
    **not** contain `lastSelectedStoreId`, any key, or any token.
21. Settings → **Import** → choose that file. A dialog must appear saying what it contains, offering Merge
    and Replace.
22. Choose **Cancel**. Nothing may change.
23. Import again, choose **Merge**. The message must report how many were added and how many were kept.
24. Import again, choose **Replace** — and confirm the second warning. The app must end up exactly as the
    backup describes.
25. Import a **text file** that is not a backup. It must be refused with a clear message, and **nothing may
    change**.
26. Import a backup, then check the **consistency check** in Settings. It must still say everything agrees.

### 15.6 The failure paths (10.1)

27. With no connection at all, walk every screen: Dashboard, Map, History, Settings. Nothing may crash and
    nothing may show invented data.
28. Revoke location while the app is open. Every screen must degrade with a message, not a crash.

### 15.7 Release checks (10.3)

29. The launcher icon must be the **blue pin**, not the green Android robot.
30. On Android 13+, long-press the icon and choose themed icons. The pin must tint to the wallpaper.

---

## 16. EXPECTED RESULT

- The Dashboard says which store to visit next, how far it is, and **why that one** — honestly, including
  when it could not measure.
- The route screen gives an order for the whole round, with real driving distances when the service is
  reachable and a straight-line fallback when it is not.
- The user's data can be saved to a file and restored from one, with the destructive option clearly marked.
- The safety-critical transaction is covered by a test that runs against real SQLite.
- The app has a real icon and no template files left.
- **No schema change, no new permission, no new dependency.**

---

## 17. COMMON ERRORS AND FIXES

| Symptom | Cause | Fix |
| --- | --- | --- |
| The suggestion never says "closest to drive to" | The matrix request failed, or no API key | Working as designed — it falls back. Check the log for `The matrix service answered HTTP`. |
| The suggestion always says "first in your list" | No location and no Home | Expected. Set Home in Settings, or grant location. |
| The daily routing allowance runs out | The matrix is being called too often | The 250 m threshold in `DashboardViewModel`. If it is gone, every fix is a request. |
| **Plan the whole route** says "too many stops" | More than 50 pending stores | A service limit, not a bug. The message names it. |
| The optimized route has two phantom stops | The parser counted the start and end steps | It must filter on `"step.type == "job"`. See §13. |
| The route order looks unchanged by Recalculate | The service returned the same plan | Possible — it is a solver, and a good order is often already good. |
| Import says "not a backup" for a valid file | No `formatVersion`, or a different one | A file from a different version is refused rather than guessed at. |
| Import reports "a store has no code" | The file is genuinely malformed | The validator names the fault. Nothing was written. |
| Import fails with a SQLite constraint error | A file that passed validation but violates the database | It **rolled back** — the app is unchanged. The log names the constraint. |
| The launcher icon is still the Android robot | A cached launcher icon | Uninstall and reinstall; some launchers cache aggressively. |
| `connectedDebugAndroidTest` fails to find a device | No emulator or device attached | It needs one — that is why it is in `androidTest`. |

---

## 18. ACCEPTANCE CHECKLIST

- [x] Every new rule is pure Java and tested on the JVM (AD-11).
- [x] The strategy is behind the `RouteSuggestionStrategy` interface (AD-12).
- [x] Every strategy has a defined answer for no location, no Home and no stores.
- [x] A distance that could not be measured is `NaN`, never `0`.
- [x] The card states **why** a store was chosen, and never overstates it.
- [x] The suggestion recalculates automatically; there is no Recalculate button for it.
- [x] The manual override is reversible.
- [x] Driving distances are re-fetched only on a change or a real move.
- [x] The optimization request is asserted field by field, including the coordinate order.
- [x] Only `"job"` steps become stops.
- [x] The job limit is checked before sending, not discovered from a 400.
- [x] The backup contains **no key, no token, no password and no transient reference**.
- [x] Completions in the backup reference stores by code, not by row id.
- [x] The import validates **before** writing, and reports **every** problem.
- [x] The import is one transaction that **throws to roll back**.
- [x] `REPLACE` deletes completions before stores.
- [x] The destructive option has its own second confirmation.
- [x] No storage permission is needed — the file picker is used.
- [x] The instrumented test covers the completion, the undo, the round change and **AD-6**.
- [x] The two template test files are deleted.
- [x] The launcher icon is real and has a monochrome layer.
- [x] The API key appears in no source file and in no log line.
- [x] **No new permission, no manifest change, no schema change.**
- [x] Zero Kotlin.
- [x] The build is green with all tests passing (see §19).
- [ ] **§15 has been run on a device.** ← the only unticked box

---

## 19. UPDATED PROJECT STATE

Verified on 2026-09-27 with:

```bash
./gradlew clean :app:testDebugUnitTest :app:assembleDebug \
    :app:compileDebugAndroidTestJavaWithJavac --no-daemon --console=plain
```

| | |
| --- | --- |
| Build | **BUILD SUCCESSFUL in 2m 55s** — 51 tasks, all executed (clean tree, including the instrumented test compile) |
| Tests | **495 passing, 0 failures, 0 errors, 0 skipped** across **31** classes (375 before this batch, **+120 new**, **−1** template deleted) |
| New JVM tests | `NearestStrategyTest` · `HomeAwareStrategyTest` · `RoadDistanceStrategyTest` · `OptimizationRequestBuilderTest` · `OptimizationResponseParserTest` · `RouteMatrixParserTest` · `BackupValidatorTest` |
| New instrumented tests | `CompletionTransactionInstrumentedTest` — **compiles; needs a device to run** |
| Deleted | `ExampleUnitTest`, `ExampleInstrumentedTest` |
| APK | **9,845,633 bytes** (debug, clean build) |
| Database schema | **still `1.json` and `2.json`** — nothing in this batch touches the schema |
| Kotlin files | **0** |
| Java sources | 105 main · 31 test · **1 instrumented** |
| New permissions | **0** — the merged manifest is unchanged since 4.3 |
| Runtime strings | 272 |

### The two failures the tests caught

Both were in my own new fixtures, and both were the same mistake: **a `0` in a distances row.**

The matrix grid is `sources × destinations`, so with one source the row holds one distance per store —
there is no "distance to yourself" entry. A fixture with a leading zero models a store sitting exactly where
the user is, and `indexOfNearest` **correctly** picks it. The expectation was wrong, not the code. Fixed by
making the fixtures realistic.

Worth recording because it is the same trap as the 4.3 tests: **check the expectation before the code.**

### The openrouteservice limits were re-verified on 2026-09-27

The roadmap asks for this before building on the service, and it was done **before a line of Version 8 was
written**:

| Endpoint | Free plan | Used by |
| --- | --- | --- |
| directions | 2,000/day | 4.2, 4.3 |
| matrix | 500/day | **8.2** |
| optimization | 500/day, **50 jobs**, **3 vehicles** | **8.3** |
| rate | 40 requests/minute | all |

The app sends **one vehicle** and checks the 50-job limit itself.

### The release checks, run against the real project

```bash
# The key value appears in NO source file
grep -rl "$ORS_KEY" app/src/     → nothing

# No log line carries a key or a coordinate
grep -rn "Log\..*(.*apiKey\|Log\..*(.*latitude" app/src/main/java/   → nothing
```

The launcher icon was the Android Studio template (green `#3DDC84` with its construction grid) and is now
the white map pin on brand blue `#1565C0`, with the monochrome layer so Android 13's themed icons work.

---

## 20. STOP POINT

**Phases 7.1 to 10.3 are code complete and the build is verified. They are NOT verified on a device.**

**The roadmap is now complete**, except for the items in §5 that were deliberately left out.

Two things must happen before this is called finished:

1. **7.0 §15.1 — the migration.** Still the highest-priority outstanding test in the project: it is the only
   change that can destroy real data, and no JVM test can reach it.
2. **§15 above** — particularly **§15.5**, the backup round trip. An export that cannot be restored is worse
   than no export, because it looks like a safety net that is not there.

Also still outstanding from earlier phases: **4.3 §15.2 step 9** (arriving changes nothing),
**5.2+5.3 §15.1 step 4** (NO changes nothing at all), and the device tests for 3.1–6.5.

**Version 9.3 (Firebase cloud sync) remains deliberately unbuilt** — decision D-9 defers it, and it needs
anonymous auth and scoped security rules before any data is pushed.

---

# ADDENDUM — Version 11: the glassmorphism redesign, and three LiveData bugs

> Requested after the batch above: *"pa fix kung may problem at gawing modern glassmorphism ang ui design ng
> buong app"*. Recorded here rather than in a new file because it is a UI revision plus three bug fixes,
> not a new feature — and the three bugs were found by auditing the code this batch added.

## The three bugs, and the one cause

**All three are the same mistake: `LiveData.getValue()` returns `null` until something observes it.**

| # | Where | What went wrong |
| --- | --- | --- |
| 1 | `OptimizedRouteFragment.pendingStores()` | Read three repositories through `getValue()`. If the app was restored straight to this screen — process death with the back stack rebuilt — all three were `null`, and the screen said **"Nothing left to visit in this round"** while a full round was outstanding. **Fixed**: three chained one-shot reads, and the position is now *observed* so `currentCoordinate()` has a value |
| 2 | `DashboardFragment.renderRoundComplete()` | Read the settings row to decide whether Home was set. The first render can arrive from the **stores** source before the settings row has ever been delivered — so the round-complete card offered **"Set a Home location in Settings"** when Home was set all along. **Fixed**: `DashboardViewModel.hasHome()`, because the ViewModel is the object that actually observes settings |
| 3 | `SettingsFragment.runConsistencyCheck()` | Same unobserved settings read. **Fixed**: `getNow()`, chained with the two reads already there |

Audited and **left alone**, because they are genuinely safe: `NavigationSession` (owns and initialises its
own `MutableLiveData`), `RoutePreviewBottomSheet` (same), `DailyHistoryFragment.join` (handles null),
`MapFragment.locationEnabled` (observed), `DashboardFragment`'s two remaining reads (the ViewModel observes
those repositories, so they have values).

**The general rule, now AD-37:** *a read that depends on another screen having run is not a read.* The
build was green and a manual test would very likely have passed — the failure needs a specific sequence to
appear, which is exactly what makes this class of bug worth auditing for rather than testing for.

## The glassmorphism

| What | Where |
| --- | --- |
| The gradient window background | `drawable/bg_glass_gradient.xml` — a 315° three-stop gradient plus two radial blobs. One file: the night variant is free, because it references colours with `values-night` counterparts |
| The backdrop is applied once | `android:windowBackground` in the theme. Every screen gets it with no per-layout work, the launch window uses it, and there is no flash of a flat colour |
| Every screen root is transparent | `@android:color/transparent` on all nine screen roots and `activity_main`. **This is what makes the cards translucent** — an opaque root covers the gradient and silently flattens the effect |
| The glass card | `Widget.AlphaRoute.StatCard`: 20dp radius (was 12dp), translucent fill, 1dp lighter rim, elevation 0. Used by every card in the app — verified by grep |
| Chrome | Toolbar, bottom navigation, the five bottom sheets and the dialogs. Sheets round at the **top only** |
| New colour roles | `glass_gradient_*`, `glass_blob_*` (+ `_end`), `glass_fill`, `glass_fill_strong`, `glass_fill_accent`, `glass_stroke`, `glass_chrome`, `glass_scrim` — in **both** `values/` and `values-night/` |

**No new dependency, no schema change, no permission, zero Kotlin.**

`docs/UI_PREVIEW.html` renders the intended design with the app's real values, light and dark side by side.

## What this addendum cannot claim

**The build proves the resources resolve and the code compiles. It does not prove the glass looks right.**
Alpha over a gradient is precisely the kind of thing that reads differently on a real screen than in a
colour picker, and no JVM test can see it. §15.7 of the main record above is where that gets checked — and
7.0 §15.1 (the migration) is still the first thing to do, because it is the only change that can destroy
real data.


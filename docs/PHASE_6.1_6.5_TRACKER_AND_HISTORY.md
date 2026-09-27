# PHASE 6.1 – 6.5 — The Tracker and the History

> **Status: code complete, build verified.** Device testing is §15 and has not been run yet.

> **Why five phase numbers in one record.** The roadmap splits Version 6 into five phases, and all five are
> read-only display work over the same two tables. They share one shape — observe Room, derive a figure,
> render it — and none of them writes anything, changes the schema, or asks for a permission. Building them
> one at a time would have meant four rounds of the same verification for no extra safety.
>
> The one thing that is genuinely separate is **6.5, the consistency check**, and it is included because it
> is the natural counterpart to 5.3: 5.3 is the write that must never go wrong, and 6.5 is the check that
> would notice if it ever did.

---

## 1. CURRENT VERSION AND PHASE

| | |
| --- | --- |
| Version | 6 — Tracker and history |
| Phase | **6.1 – 6.5 — Live tracker, History, Daily History, Completion Detail, Consistency check** |
| Previous phase | 5.2 + 5.3 — the completion flow ✅ |
| Next phase | **7.1 — Nearest pending store** |
| Database version | **1 — unchanged.** No new entity, no new column, no `Migration` |
| Kotlin files | **0** |

---

## 2. OBJECTIVE

Phases 5.2 and 5.3 made it possible to record a completion. This version makes the record **visible**.

The Dashboard stops showing invented numbers and starts showing real ones. The History tab stops being a
placeholder and starts listing days. A day opens into its stores in the order they were completed, and a
store opens into its full record. And the consistency check gives the user a way to ask whether the two
tables still agree.

**Nothing in this version writes anything.** Every screen here is a view over data that 5.3 produced, and
that constraint is what made batching five phases reasonable.

---

## 3. UNDERSTANDING CHECKLIST

- ✅ **Room is the single source of truth** (AD-4). Every figure is derived from the rows on every change.
  Nothing is cached and **nothing is stored as a counter** — a stored total can drift from the rows it
  claims to summarise, and there would be no way to tell which was right.
- ✅ **The arithmetic is pure Java and tested on the JVM** (AD-11). A wrong count is invisible — it just
  looks like a count — so the counting must be verifiable without a device.
- ✅ **A Fragment never touches Room.** Screens observe a ViewModel or a repository.
- ✅ **The date keys are the single definition of a day.** "Today" and "yesterday" are read from
  `DateKeyHelper`, never derived from timestamps.
- ✅ **The per-day count comes from the rows**, never from a stored daily total.
- ✅ **The consistency check reports and never repairs.** Deleting an orphaned history row would erase the
  only evidence that a completion happened.
- ✅ Every screen's empty state is a designed state, not a blank view.

---

## 4. FEATURES INCLUDED (in this version)

1. **`TrackerCalculator` + `TrackerSnapshot`** — every Dashboard figure computed in one pass over both
   tables, pure Java, 19 tests.
2. **The Dashboard, live** — seven cards, real numbers, a loading bar until the first emission, and an
   empty state. The placeholder values and their notice card are **deleted**.
3. **The seventh card, Total stores** — the count the Phase 1.3 brief did not list.
4. **`HistoryGrouper` + `HistoryDay`** — completions grouped by day, newest first, pure Java, 12 tests.
5. **Screen 10 — History** — one card per day, "25 Sep 2026 · 3 stores", tapping opens the day.
6. **Screen 11 — Daily History** — the day's stores in `dailySequence` order, with the date and total in
   the header.
7. **Screen 11b — Completion Detail** — the full record, a non-interactive map preview with the store's own
   marker, and **View on Map**.
8. **`ConsistencyChecker`** — four faults detected, reported in plain language, pure Java, 15 tests.
9. **The consistency check in Settings** — a card that runs it on demand and prints the findings.
10. **Generic pushed screens** — `MainActivity.push(...)`, so Daily History and Completion Detail are steps
    on the back stack rather than tabs.

---

## 5. FEATURES NOT INCLUDED YET

| Feature | Phase |
| --- | --- |
| The next-store suggestion card | **7.1** |
| Recalculating the suggestion after a completion | **7.2** |
| The Home-aware heuristic | **7.3** |
| The "route complete" state | **7.4** |
| Road-based distances and multi-stop optimisation | **Version 8** |
| An interactive map on the Completion Detail screen | **not planned** — deliberately a picture, not a second map to get lost in |
| Editing or deleting a completion | **never** — a completion is a historical fact. `CompletionHistoryDao` has no update or delete |
| A "run the consistency check automatically" mode | **not planned** — it reads both whole tables, and the answer changes only when the data does |

---

## 6. ASSUMPTIONS

| # | Assumption |
| --- | --- |
| A87 | `domain/` must not import `androidx.room`. The tracker takes the `StoreLike` / `CompletionLike` seams, and the entities implement them — the same pattern `ArrivalEvaluator.LocationFixLike` established for the location fix. |
| A88 | Every figure on the Dashboard is computed in **one pass**, so the cards can never show a set of numbers that never existed together. |
| A89 | The percentage is **`0.0%` on an empty database**, not a division by zero. An empty database is the state the app starts in. |
| A90 | "Remaining" is **clamped at zero**. It cannot be negative through the app, but a negative card would be meaningless. |
| A91 | The History screen groups in memory rather than with SQL `GROUP BY`, because the Daily History screen needs the individual rows anyway — two queries would read the same data twice, at two moments. |
| A92 | The displayed date is formatted **from the stored key**, not from a timestamp in the group. If the device's timezone changed after a completion, a timestamp could render as the neighbouring day. |
| A93 | The Completion Detail's map is **not interactive**. It says "here"; the action button is how the user goes there. |
| A94 | The consistency check **never modifies data**. A checker that repairs what it finds can also destroy what it does not understand. |
| A95 | A marker-count mismatch is **skipped** (not guessed) when the map's count is unknown. A false alarm would teach the user to ignore the checker. |
| A96 | Pushed screens take their toolbar title **from the fragment instance**, not from a tag map held in a field — a field is empty after a process death while the back stack is restored. |

---

## 7. FILES TO CREATE (18)

| # | File | Purpose |
| --- | --- | --- |
| 1 | `domain/TrackerSnapshot.java` | The seven metrics, immutable, one per emission. |
| 2 | `domain/TrackerCalculator.java` | The arithmetic + the two `Like` seams. Pure. |
| 3 | `domain/HistoryDay.java` | One day's completion count. Pure. |
| 4 | `domain/HistoryGrouper.java` | Group by day, newest first. Pure. |
| 5 | `domain/ConsistencyChecker.java` | The four faults. Pure. |
| 6 | `ui/dashboard/DashboardViewModel.java` | `MediatorLiveData` joining the two tables. |
| 7 | `ui/history/HistoryAdapter.java` | Date-group rows. |
| 8 | `ui/history/DailyHistoryAdapter.java` | Daily rows + the `Row` join model. |
| 9 | `ui/history/DailyHistoryFragment.java` | Screen 11. |
| 10 | `ui/history/CompletionDetailFragment.java` | Screen 11b. |
| 11 | `res/layout/fragment_history.xml` | Screen 10. |
| 12 | `res/layout/item_history_date_group.xml` | One date group. |
| 13 | `res/layout/fragment_daily_history.xml` | Screen 11. |
| 14 | `res/layout/item_history_row.xml` | One completion row. |
| 15 | `res/layout/fragment_completion_detail.xml` | Screen 11b. |
| 16 | `app/src/test/.../TrackerCalculatorTest.java` | 20 tests. |
| 17 | `app/src/test/.../HistoryGrouperTest.java` | 11 tests. |
| 18 | `app/src/test/.../ConsistencyCheckerTest.java` | 17 tests. |

---

## 8. FILES TO MODIFY (13)

| # | File | Change |
| --- | --- | --- |
| 1 | `data/db/StoreEntity.java` | Implements `TrackerCalculator.StoreLike` + two accessors. |
| 2 | `data/db/CompletionHistoryEntity.java` | Implements `TrackerCalculator.CompletionLike` + three accessors. |
| 3 | `data/repository/StoreRepository.java` | `findAllNow()` — a one-shot read for the checker. |
| 4 | `data/repository/CompletionRepository.java` | `findAllNow()` — the same. |
| 5 | `data/repository/PlaceLinkRepository.java` | `showCoordinate()` — the map-jump channel. |
| 6 | `util/DateTimeHelper.java` | `dateTextFromKey()` — format a stored key, not a timestamp. |
| 7 | `ui/dashboard/DashboardFragment.java` | Full body: observe, render seven cards. |
| 8 | `ui/history/HistoryFragment.java` | Full body: replaces the Phase 1.2 placeholder. |
| 9 | `ui/settings/SettingsFragment.java` | The consistency-check card. |
| 10 | `MainActivity.java` | `push()`, `showMapAt()`, `titleFor()`; the chrome is now generic. |
| 11 | `res/layout/fragment_dashboard.xml` | Seven cards, no notice, an empty state. |
| 12 | `res/layout/fragment_settings.xml` | The consistency card. |
| 13 | `res/values/strings.xml` | **Deletes** `placeholder_history` and all `dashboard_value_*`; adds the History, detail and checker strings. |

---

## 9. DEPENDENCIES

| Dependency | Status |
| --- | --- |
| Everything from 5.3 | Reused. **Not one new library.** |
| `androidx.lifecycle.ViewModel` / `MediatorLiveData` | Already present transitively via `fragment`. |
| `RecyclerView` | Already present (used by the Store list since Version 3). |
| openrouteservice | **Not used.** No network call anywhere in this version. |

**No new dependency, no new permission, no new key, no schema change.**

---

## 10. PERMISSIONS AND MANIFEST CHANGES

**None.** The fourth phase in a row with no manifest change.

| | |
| --- | --- |
| New permissions | **0** |
| Removed permissions | **0** |
| New components | **0** |
| `ACCESS_BACKGROUND_LOCATION` | Still **not declared** |

> ⚠️ **Trap.** Grepping the merged manifest for `ACCESS_BACKGROUND_LOCATION` still returns a hit — it is
> the **XML comment text**. Unchanged since 4.3.

---

## 11. SETUP INSTRUCTIONS

```bash
cd "C:/Users/Admin/OneDrive/Documents/custom system/AlphaRoute"
./gradlew clean :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain
```

Nothing else. No `local.properties` change, no new key, no database migration.

---

## 12. COMPLETE CODE

The complete files are in the repository at the paths listed in §7 and §8. This record does not repeat
them; the phase rule is that a delivered file is the complete file, not a diff, and they are all present.

---

## 13. CODE EXPLANATION

### The seams, and why `domain/` cannot see Room

The tracker's real inputs are `StoreEntity` and `CompletionHistoryEntity` — Room entities carrying
`androidx.room` annotations. Importing them into `domain/` would break AD-11, which exists so this
arithmetic can be tested on a laptop with no emulator.

So the calculator declares two tiny interfaces:

```java
public interface StoreLike { long id(); String storeCode(); boolean isCompleted(); }
public interface CompletionLike { long storeId(); String completionDateKey(); long completionTimestamp(); }
```

and the entities implement them. The dependency points **data → domain**, never the other way.

`StoreLike` has three members, not eleven, and that is deliberate: it is obvious at a glance that nothing
in the tracker can read a coordinate, a name, or anything else it has no business with. This is the same
seam `ArrivalEvaluator.LocationFixLike` established in 4.3.

### One pass, not seven queries

Six of the seven figures could each have been a `COUNT` query. The reason they are not: the Dashboard would
then read them at six different moments, and could show "42 completed" beside a "18 remaining" that came
from a different instant — numbers that never coexisted. One pass over two lists means every figure on
screen describes the same moment.

The cost is that both whole tables are read into memory. At the scale this app operates — one user, a few
hundred stores — that is a few kilobytes.

### Why the count is never stored

There is no `completedCount` column anywhere, and the reason is not purity. A stored counter and the rows
it summarises can disagree, and when they do **there is no way to tell which is right**. Deriving every
figure from the rows means the question cannot arise: the rows *are* the answer.

That is also why `CompletionHistoryDao` has no `UPDATE` and no `DELETE`. A completion is a historical fact.

### The `MediatorLiveData` in `DashboardViewModel`

```java
tracker.addSource(app.storeRepository().observeAll(), emitted -> { stores = ...; recompute(); });
tracker.addSource(app.completionRepository().observeAllOrdered(), emitted -> { history = ...; recompute(); });
```

Two observers in the Fragment would recompute twice per change and, in between, render a set of numbers
from two different moments. The mediator joins the sources so the screen is written once, from one
consistent pair of inputs.

`setValue`, not `postValue`: Room's `LiveData` delivers on the main thread, and posting would defer every
update by a message-loop turn for nothing.

### The date is formatted from the key, not the timestamp

```java
String date = DateTimeHelper.dateTextFromKey(day.dateKey());
```

The History screen groups by the stored `completionDateKey`. Formatting a timestamp from one of the group's
rows would *usually* agree — but if the device's timezone changed after the completion was recorded, it
could render as the neighbouring day, and the group would then be labelled with a date that does not match
the day it shows. Formatting the key makes the label and the grouping the same fact.

### Sorting by string, and why that is correct

```java
Collections.sort(days, (left, right) -> right.dateKey().compareTo(left.dateKey()));
```

`YYYY-MM-DD` is the one common date format where plain text comparison is also chronological order. So the
sort needs no date parsing, no timezone, and cannot disagree with the grouping that produced the keys. The
tests assert it across a month boundary and a year boundary, which are the two places zero padding earns
its keep.

### `HistoryGrouper` rather than `GROUP BY`

A SQL `GROUP BY completionDateKey` would give the same counts. But the Daily History screen needs the
individual rows for a day anyway, so grouping in memory means one read instead of two — and the two screens
cannot disagree, because the day's count and the day's list come from the same list.

### The map jump reuses the pasted-link channel

"View on Map" needs the Map tab to centre on a coordinate. Rather than adding a second `LiveData` and a
second observer in `MapFragment`, `PlaceLinkRepository.showCoordinate()` publishes into the channel that
already exists. The channel is really "a coordinate the map should fly to"; parsing a link was only ever one
way to fill it. A second channel would mean two places deciding when to move the camera.

`showMapAt` pops every pushed screen first. Completion Detail is two screens deep, and a tab cannot be shown
underneath a pushed screen — the pushed screen hides it.

### The consistency check reports, and never repairs

Four faults, and they are genuinely different:

| Fault | What the user would see |
| --- | --- |
| `COMPLETED_WITHOUT_HISTORY` | Green pin on the map, but counted as remaining |
| `HISTORY_WITHOUT_COMPLETION` | Counted, but still offered as somewhere to visit |
| `ORPHANED_HISTORY` | A completion with no store behind it — the foreign key is not being enforced |
| `MARKER_COUNT_MISMATCH` | A pin missing from the map |

**It never deletes or rewrites.** A checker that fixes what it finds can also destroy what it does not
understand, and deleting an orphaned history row would erase the only evidence that a completion happened.
Every finding is reported with enough detail to act on, and the acting is left to a human.

The marker-count check is **skipped**, not guessed, when the count is unknown (`-1`). Settings has no access
to the Map tab's controller, and passing a zero would report a mismatch against every store — a false alarm
in a checker teaches the user to ignore it.

### Pushed screens take their title from the instance

```java
private int titleFor(@Nullable Fragment fragment) {
    if (fragment instanceof HomeSetupFragment) return R.string.title_home_setup;
    ...
}
```

A `Map<String, Integer>` field of tag → title would be **empty after a process death**, while the framework
restores the back stack — so the screen would come back with no title. Asking the fragment cannot go stale.

---

## 14. DATA FLOW FOR THIS VERSION

```
                    Room (the single source of truth, AD-4)
                     │                                    │
        stores table │                                    │ completion_history
                     ▼                                    ▼
        StoreRepository.observeAll()      CompletionRepository.observeAllOrdered()
                     │                                    │
                     └──────────────┬─────────────────────┘
                                    ▼
                        DashboardViewModel (MediatorLiveData)
                                    │  one pass, both lists
                                    ▼
                        TrackerCalculator.compute(...)        ← PURE, 19 tests
                                    │
                                    ▼
                        TrackerSnapshot (one immutable object)
                                    │
                                    ▼
                        DashboardFragment → seven cards

  History tab ── observeAllOrdered() ──► HistoryGrouper.group(...)  ← PURE, 12 tests
                                            │
                                            ▼
                                    List<HistoryDay> (newest first)
                                            │
                                    tap ────┘
                                            ▼
                              DailyHistoryFragment
                                  │  joins the day's rows with the store list
                                  │  (order comes from SQL: dailySequence ASC)
                                  ▼
                              tap a row
                                  │
                                  ▼
                              CompletionDetailFragment
                                  │  re-reads the store and its history from Room
                                  ├─ map preview (MapMarkerController picks the green pin)
                                  └─ View on Map ──► MainActivity.showMapAt()
                                                          │  pops to the tabs, switches to Map
                                                          ▼
                                            PlaceLinkRepository.showCoordinate()
                                                          │
                                                          ▼
                                            MapFragment moves its camera

  Settings ── "Run check" ──► ConsistencyChecker.check(stores, history, -1)   ← PURE, 15 tests
                                  │
                                  ▼
                          findings, in plain language. Nothing is changed.
```

**The network is nowhere in this flow.** Every screen reads the local database.

---

## 15. MANUAL TEST PROCEDURE

Needs a device, and most of it needs at least one completion — so run **5.2+5.3 §15 first**, or complete a
couple of stores by hand.

### 15.1 The Dashboard

1. Fresh install, no stores. The Dashboard must show **all zeros**, **0.0%**, **—** for latest, and the
   **"No stores yet"** card. It must **not** show a blank screen or a divide-by-zero artefact.
2. Add three stores. The Dashboard must show **Total 3**, **Remaining 3**, **0.0%**, and no empty-state
   card.
3. Complete one. **Completed today 1**, **Overall 1**, **Remaining 2**, **33.3%**, and the latest card must
   name the store's code and the time.
4. Complete a second. **Completed today 2**, **40.0%** with three stores.
5. **The counts must match the History tab.** If they do not, that is the whole point of the consistency
   check — run it.

### 15.2 The History tab

6. With no completions: **"No completions yet."** No list, no error.
7. With completions: one card per day, **newest first**, each reading "25 Sep 2026 · 3 stores".
8. The count on each card must equal the number of rows that day, not a total.
9. Complete a store today and a second one. The day's card must change to "· 2 stores" **without a
   refresh**.

### 15.3 Daily History

10. Tap a day. The header must name the day and its total.
11. Rows must be in **completion order** — "#1", "#2", "#3" — which is the order you completed them, **not
    route-number order**. Complete stores out of route order deliberately to check this.
12. Rename a store from the Map tab, then come back. The row must show the **new** name.
13. Delete a store that has no completion. Its rows elsewhere must be unaffected.

### 15.4 Completion Detail

14. Tap a row. The store's code, name, area, completion date, time, daily sequence and coordinates must all
    be shown.
15. **The map preview must show a green pin** at the store — not orange, not blank. A blank preview means
    the tile User-Agent is wrong.
16. Tap **View on Map**. The app must land on the **Map tab**, centred on that store, with the bottom
    navigation visible — not on a pushed screen with no way back.

### 15.5 The consistency check

17. With healthy data, run it in Settings. It must say **"Everything agrees. No problems found."**
18. The check must **never** change anything. Compare the store count and completion count before and after.
19. If you can provoke a fault — for example by deleting a store row directly with a SQLite browser — it
    must be reported in plain language, with the store named, and **nothing must be deleted**.

### 15.6 The screens' states

20. Rotate on each of the four screens. Nothing may crash, and the Dashboard's numbers must not reset.
21. Open Daily History, then press back. It must return to the **History tab**, not to whichever tab was
    selected before.
22. Open Completion Detail, press back twice, and confirm the bottom navigation comes back.

---

## 16. EXPECTED RESULT

- Every figure on the Dashboard is real, and matches what the History tab shows.
- Nothing on any screen is a placeholder or an invented number.
- Days are newest first; a day's stores are in the order they were completed.
- The Completion Detail shows the store's own marker, which is green because the store is completed.
- **View on Map** lands on the Map tab, centred, with the navigation bar back.
- The consistency check reports four kinds of disagreement in plain language and **changes nothing**.
- No screen writes to the database. This entire version is read-only.

---

## 17. COMMON ERRORS AND FIXES

| Symptom | Cause | Fix |
| --- | --- | --- |
| The Dashboard shows `NaN%` | The percentage was computed by dividing by zero | `TrackerCalculator` returns `0.0` when there are no stores. If it appears, that guard was removed. |
| "Latest completed" shows a dash when a completion exists | The completion's store was not found in the store list | Should be impossible (AD-6). Run the consistency check — this is `ORPHANED_HISTORY`. |
| The History card's date is one day off | The date was formatted from a timestamp instead of the stored key | `DateTimeHelper.dateTextFromKey` is the correct call. See §13. |
| Days appear oldest-first | The sort was dropped | `HistoryGrouper` sorts by `dateKey` descending. A `LinkedHashMap` alone preserves **input** order, which is a query detail. |
| Daily rows are in route-number order | The DAO's `ORDER BY dailySequence ASC` was changed | It is the single definition of that order. See `CompletionHistoryDao.observeByDateKey`. |
| The map preview is blank | The tile source or User-Agent is missing | `CompletionDetailFragment.configureMapPreview()` uses `MapConfig.tileSource()`. The OSM servers refuse a generic User-Agent, silently. |
| The app crashes leaving Completion Detail | `mapView.onDetach()` was not called | It is mandatory; without it the map view leaks on every visit. |
| **View on Map** lands on a blank screen | A pushed screen was not popped first | `showMapAt` pops the whole back stack. A tab cannot be shown underneath a pushed screen. |
| The toolbar title is missing on a pushed screen | The title came from a field that did not survive a process death | `titleFor(fragment)` reads the instance. See §13. |
| The consistency check always reports a marker mismatch | A zero was passed instead of `-1` | `UNKNOWN_MARKER_COUNT` is `-1`. Zero means "the map drew nothing", which is a real claim. |
| The Dashboard numbers do not add up | The two tables were read at different moments | Both go through one `MediatorLiveData`. Two observers in the Fragment would reintroduce this. |

---

## 18. ACCEPTANCE CHECKLIST

- [x] `domain/` has **zero `androidx.room` imports** in the new files (AD-11).
- [x] The tracker, the grouper and the checker are pure Java and unit-tested.
- [x] Every Dashboard figure comes from one pass over both tables.
- [x] No count is stored anywhere; every figure is derived from the rows.
- [x] The empty database produces `0` and `0.0%`, not a divide-by-zero.
- [x] "Remaining" is never negative.
- [x] The percentage is one decimal place, formatted with `Locale.US`.
- [x] Days are newest first, asserted across a month and a year boundary.
- [x] The displayed date is formatted from the stored key.
- [x] A Fragment never touches Room — the ViewModel and the repositories do.
- [x] The Completion Detail re-reads the store from Room rather than trusting a snapshot.
- [x] The consistency check **modifies nothing**.
- [x] An unknown marker count is skipped, not guessed.
- [x] The placeholder strings and the notice card are **deleted**, not hidden.
- [x] `placeholder_history` is **deleted**.
- [x] **No new permission, no manifest change, no schema change** — `1.json` is still the only schema.
- [x] Zero Kotlin.
- [x] The build is green with all tests passing (see §19).
- [ ] **§15 has been run on a device.** ← the only unticked box

---

## 19. UPDATED PROJECT STATE

Verified on 2026-09-27 with:

```bash
./gradlew clean :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain
```

| | |
| --- | --- |
| Build | **BUILD SUCCESSFUL in 4m 3s** — 42 tasks, all executed (clean tree) |
| Tests | **348 passing, 0 failures, 0 errors, 0 skipped** across 24 classes (300 before this version, **+48 new**) |
| New tests | `TrackerCalculatorTest` 20 · `ConsistencyCheckerTest` 17 · `HistoryGrouperTest` 11 |
| APK | **9,739,165 bytes** (debug, clean build) |
| Database schema | **still only `1.json`** — nothing here touches the schema |
| Kotlin files | **0** |
| Java sources | 78 main · 24 test |
| New permissions | **0** — the merged manifest is unchanged from 4.3 (8 entries) |
| Runtime strings | 182 (167 before, after deleting 8 and adding 23) |

### What was checked in the merged manifest

Unchanged since 4.3 — 8 entries, and `ACCESS_BACKGROUND_LOCATION` is still only the comment text:

```
ACCESS_COARSE_LOCATION · ACCESS_FINE_LOCATION · ACCESS_NETWORK_STATE
FOREGROUND_SERVICE · FOREGROUND_SERVICE_LOCATION · INTERNET
POST_NOTIFICATIONS · RECEIVE_BOOT_COMPLETED
```

### What the tests caught

Two tests failed on the first run, and **the code was wrong, not the expectations** — the opposite of the
4.3 experience, where the expectations were the problem. Both were the same defect: the totals were counted
with `list.size()` while every loop in the same method skipped `null` elements, so a single null would have
made the Dashboard's figures contradict each other and would have raised a false marker-count alarm in the
checker. Fixed by counting non-null entries by walking. See §8's trap table.

---

## 20. STOP POINT

**Version 6 is code complete and the build is verified. It is NOT verified on a device.**

This version is read-only, so nothing here can corrupt data — but it can **misreport** it, and a wrong
number is invisible. Do not begin Phase 7 until **§15 has been run**, and specifically:

1. **§15.1 step 5** — that the Dashboard and the History tab agree. Phase 7's suggestion is built on these
   figures, and a tracker that disagrees with itself would make the suggestion look wrong when it is not.
2. **§15.3 step 11** — that Daily History is in completion order, not route order. That ordering is the
   point of the screen and it is decided in SQL, where no JVM test can reach it.
3. **§15.5 step 18** — that the consistency check changed nothing. It is a diagnostic, and a diagnostic that
   alters what it inspects is worse than none.

Carried forward: **4.3 §15**, **4.4 §15** and **5.2+5.3 §15** are all still outstanding. The two that
matter most remain **4.3 §15.2 step 9** (arriving changes nothing) and **5.2+5.3 §15.1 step 4** (NO changes
nothing at all).

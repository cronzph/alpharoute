# AlphaRoute — Phase 3.4 Load Saved Store Markers

**Project title:** Alfamart Route Tracker
**Version:** 3 — Map and Manual Pins — **Phase:** 3.4 Load Saved Store Markers
**Repository root:** `C:/Users/Admin/OneDrive/Documents/custom system/AlphaRoute`
**Date:** 2026-09-25
**Prerequisites:** Phases 0.1–0.3, 1.1–1.3, 2.1–2.3, 3.1–3.3
**Status:** 🔄 **Code complete and VERIFIED — 83/83 unit tests pass, build succeeds.** Awaiting your on-device test.

> **This phase closes the gap Phase 3.3 flagged.** Markers now load from Room, so they come back after a
> restart — and completed stores finally draw green.

---

## 1. CURRENT VERSION AND PHASE

| Item | Value |
| --- | --- |
| Version | 3 — Map and Manual Pins |
| Phase | 3.4 — Load Saved Store Markers |
| Files created | 3 |
| Files modified | 3 |
| Files deleted | 0 |
| Database version | **1** — unchanged. No database code was touched. |

---

## 2. OBJECTIVE

Make the map show what is actually in the database:

- Load stores from Room
- Orange markers for pending stores
- Green markers for completed stores
- Correct association between marker and store ID
- Marker refresh after database changes
- Marker persistence after restart

---

## 3. UNDERSTANDING CHECKLIST

- [x] Java only, no Kotlin. No new dependency, no new permission, no schema change.
- [x] The map **reads** the store table; it never writes to it and never caches a copy.
- [x] Pending is orange **and** a filled circle; completed is green **and** a check mark — shape carries the meaning, not colour.
- [x] Every marker carries its store id, ready for Phase 3.5's tap handling.
- [x] A store deleted from the database loses its marker.
- [x] Markers survive rotation and a cold start.
- [x] No store name, code, route number or coordinate is invented anywhere.

---

## 4. FEATURES INCLUDED (in this phase)

| Feature | Detail |
| --- | --- |
| **The green completed pin** | `ic_marker_completed` — a green teardrop with a stroked check |
| **`renderAll(stores)`** | Reconciles the whole marker set against the whole store list |
| **Icon follows status** | A store flipping from pending to completed turns green on the map |
| **Pruning** | A store removed from the database loses its marker |
| **Persistence** | Markers come back after a restart, because the first emission carries everything |
| **5 new unit tests** | Proving pending and completed can never resolve to the same icon |

---

## 5. FEATURES NOT INCLUDED YET

| Not here | Arrives in |
| --- | --- |
| Tapping a marker to open Store Details | **Phase 3.5** |
| Editing an existing store | **Phase 3.6** |
| Deleting a store from the UI | **Phase 3.6** |
| The Home pin | **Phase 3.7** |
| Route polyline | **Phase 4.2** |

---

## 6. ASSUMPTIONS

| # | Assumption | Impact if wrong |
| --- | --- | --- |
| A66 | **`renderAll` reconciles a whole list rather than exposing "add one" and "remove one".** | With a full list to compare against, a deleted store cannot linger on the map because a callback was missed, and a restart needs no special case. See §13.2. |
| A67 | **A store with impossible coordinates gets no marker**, and any marker it had is pruned. | Defensive: the database should not contain one, but a pin drawn at latitude 0 would be worse than a missing pin. |
| A68 | **The rendered status is remembered per marker**, so the icon is only replaced when it actually changes. | Without it, every database change would re-decode both drawables for every marker on the map. |
| A69 | **Phase 3.3's fragment-result plumbing was removed**, not left in place unused. | Rule 11 justification in §8. |

---

## 7. FILES TO CREATE (3)

| Path | Purpose |
| --- | --- |
| `res/drawable/ic_marker_completed.xml` | **New.** The green teardrop with a check mark |
| `app/src/test/java/com/cph/alpharoute/ui/map/MapMarkerControllerTest.java` | **New.** 5 tests |
| `docs/PHASE_3.4_MARKER_LOADING.md` | **New.** This record |

---

## 8. FILES TO MODIFY (3) — and two removals

| # | Path | Status | What changed |
| --- | --- | --- | --- |
| 1 | `ui/map/MapMarkerController.java` | **Replaced completely** | `renderAll` reconciliation, `MarkerEntry`, pruning, status-change icon handling |
| 2 | `ui/map/MapFragment.java` | **Modified** | `observeStores()` replaced the fragment-result listener; `markerController.clear()` added to `onDestroyView` |
| 3 | `ui/store/AddEditStoreBottomSheet.java` | **Modified** | The fragment-result plumbing was removed |

### 🗑️ Two removals, both justified under rule 11

**Removal 1 — Phase 3.3's fragment-result plumbing** (`RESULT_KEY`, `RESULT_STORE_ID`,
`RESULT_OUTCOME`, `publishResult`, and the map's listener plus `showSavedStore`).

| | |
| --- | --- |
| **Technical reason** | LiveData observation supersedes it. The old path could only draw the store the sheet had just reported, which is exactly why Phase 3.3's markers vanished on rotation. The new path covers the same case *and* restart, refresh and deletion. |
| **Affected files** | `ui/store/AddEditStoreBottomSheet.java`, `ui/map/MapFragment.java`. |
| **Safe replacement** | `MapFragment.observeStores()` observes `storeRepository.observeAll()` and calls `markerController.renderAll(stores)`. The marker appears as soon as Room reports the insert — no callback needed, and nothing to forget. |
| **Why it was removed rather than kept** | Leaving an unused publish/listen pair would be dead code that a later reader has to work out is dead. Phase 3.3's own document flagged this replacement in advance. |

**Removal 2 — the documented temporary in `markerIconRes`** (assumption A65), which mapped COMPLETED to
the pending icon because completed stores could not be drawn yet.

| | |
| --- | --- |
| **Technical reason** | Phase 3.4 is the phase that draws completed stores, so the fallback is no longer needed — and it would now be a **bug**: a completed store rendering orange. |
| **Affected files** | `ui/map/MapMarkerController.java` only. |
| **Safe replacement** | `R.drawable.ic_marker_completed`, the new green teardrop-with-a-check. |
| **Verification** | `MapMarkerControllerTest.pendingAndCompletedUseDifferentIcons` now makes it impossible to reintroduce the fallback silently. |

**Inspected and left unmodified:** the entire `data/` layer, `domain/`, `util/`, `location/`, and
`AddEditStoreBottomSheet`'s validation logic. This phase reads the database; it does not change how
anything is written to it.

---

## 9. DEPENDENCIES

**None added.**

---

## 10. PERMISSIONS AND MANIFEST CHANGES

**None.**

---

## 11. SETUP INSTRUCTIONS

1. Let Gradle sync (nothing new to fetch).
2. **Build → Clean Project**, then **Build → Rebuild Project**.
3. Run on a device or emulator. If you have stores from Phase 3.3, they should appear immediately.

---

## 12. COMPLETE CODE

All 6 files are written into the project on disk and attached to this message. The files worth reading
first:

1. `ui/map/MapMarkerController.java` — the reconciliation, and why it is a list rather than a pair of calls
2. `app/src/test/java/…/MapMarkerControllerTest.java` — the accessibility rule, made unbreakable
3. `ui/map/MapFragment.java` — `observeStores`

---

## 13. CODE EXPLANATION

### 13.1 The whole phase in one observation

```java
AlphaRouteApp.from(context)
        .storeRepository()
        .observeAll()
        .observe(getViewLifecycleOwner(), stores -> markerController.renderAll(stores));
```

Four of this phase's six requirements come out of that one call, and it is worth being explicit about
why:

| Requirement | How this satisfies it |
| --- | --- |
| **Load stores from Room** | `observeAll()` is a Room query. Nothing is cached and nothing is duplicated. |
| **Marker refresh after database changes** | Room re-runs the query whenever the `stores` table changes, so a store completed in Phase 5 turns green with no extra wiring. |
| **Marker persistence after restart** | The first emission after a cold start carries everything already saved. "Persistence" is not a feature that had to be written — it is what observing a database *does*. |
| Orange pending / green completed | `renderAll` picks the icon from each store's status. |

### 13.2 Why `renderAll` takes a whole list

The obvious alternative is a pair of calls — `addMarker(store)` and `removeMarker(storeId)` — driven by
whatever the caller happens to know changed. It was rejected for three reasons:

1. **A deleted store cannot linger.** With a full list to compare against, `prune` removes any marker whose
   store is no longer present. With per-event callbacks, one missed call leaves a pin on the map forever,
   pointing at nothing.
2. **A restart needs no special case.** The first emission is the whole database, so the same code path
   handles "opening the app" and "one store changed".
3. **It is idempotent.** Calling `renderAll` twice with the same list changes nothing visible. Calling
   `addMarker` twice creates two markers.

The reconciliation itself is deliberately in-place: existing markers are **moved and updated**, not
destroyed and rebuilt, so the camera and the user's panning are not disturbed when the database changes.

### 13.3 The bug this phase had to fix in its own code

Phase 3.3's `addOrUpdate` set the icon only when it *created* a marker:

```java
if (marker == null) {
    marker.setIcon(...);       // <-- only here
}
```

That is fine while every store is pending. It breaks the moment a store's status changes: the marker
already exists, so the icon is never touched, and **a completed store would keep its orange pin** — which
is precisely the requirement this phase exists to satisfy. Phase 5 would have hit it, and the symptom
(a store that is green everywhere except the map) would have pointed at the completion code rather than
here.

The fix is to apply the icon whenever the status differs from what is drawn:

```java
if (entry.renderedStatus != store.status) {
    entry.marker.setIcon(ContextCompat.getDrawable(mapView.getContext(), markerIconRes(store.status)));
    entry.renderedStatus = store.status;
}
```

`MarkerEntry` remembers the status it is currently drawn as, so the comparison is cheap and the drawables
are only re-decoded when something genuinely changed.

### 13.4 The green pin's check is stroked, not filled

`ic_marker_completed` draws its check as a **stroked path** with a 1.6-unit width, not as a scaled-down
copy of Material's filled check glyph. Scaling a filled glyph down to fit inside the teardrop's circle
thins its strokes to a hairline — at 36 dp on a map it would read as a smudge rather than a tick. A
stroked path keeps a constant, readable weight at any size.

### 13.5 Why the icon mapping has tests

```java
static int markerIconRes(@NonNull StoreStatus status) {
    return status == StoreStatus.COMPLETED
            ? R.drawable.ic_marker_completed
            : R.drawable.ic_marker_pending;
}
```

Five tests, and they are not ceremony. Phase 0.3 requires that marker colour is never the only signal,
because orange and green are the classic red/green-confusion pair. The **only** thing that lets a
colour-blind user tell a pending store from a completed one is that the shapes differ — a filled circle
against a check mark.

That is one line away from being broken. If someone ever "tidies" the method to return a single icon for
both statuses, every pending and completed store becomes visually identical for those users, and nothing
else in the codebase would notice. `pendingAndCompletedUseDifferentIcons` notices.

The method is package-private rather than private purely so the test can reach it, and `MapMarkerController`
has no static initialisers — which is why the test runs on the JVM despite the class otherwise needing
osmdroid and a `MapView`.

### 13.6 What is deliberately still not here

- **No tap handling.** Markers are drawn but do nothing when tapped. That is Phase 3.5's entire subject,
  and the `setRelatedObject(store.id)` association it needs is already in place.
- **No clustering.** With a few hundred stores at city zoom, pins overlap. Not a problem at the scale this
  app is used at (one route at a time), and not requested.
- **No marker animation or custom info window.** Neither is in the brief.

---

## 14. DATA FLOW FOR THIS PHASE

```
AlphaRouteApp
  └─ storeRepository (owns StoreDao)

MapFragment.onViewCreated()
  ├─ markerController = new MapMarkerController(mapView)
  └─ observeStores()
        └─ storeRepository.observeAll()          Room query, LiveData
              .observe(getViewLifecycleOwner(), stores -> markerController.renderAll(stores))

renderAll(stores)
  ├─ for each store with plottable coordinates:
  │     ├─ no marker yet?  → create it, anchor bottom-centre, carry the store id
  │     ├─ status changed? → swap the icon (orange circle ⇄ green check)
  │     └─ always          → move to the store's position, set title and snippet
  ├─ prune: any marker whose store is no longer in the list is removed
  └─ invalidate once

Any database change anywhere in the app
  └─ Room re-runs observeAll()
        └─ renderAll runs again
```

**The map never writes.** It reads the store table and nothing else. Every write still goes through
`StoreRepository`, and Room remains the single source of truth (AD-4).

---

## 15. MANUAL TEST PROCEDURE

### 15.0 Already verified automatically (2026-09-25)

`./gradlew :app:testDebugUnitTest :app:assembleDebug` was run on this machine with Gradle 9.4.1 / JDK 21
and returned **`BUILD SUCCESSFUL in 46s`** (41 tasks: 20 executed, 21 up-to-date).

| Test class | Result |
| --- | --- |
| **`MapMarkerControllerTest`** (new) | **5 tests, 0 failures, 0 errors** |
| `LocationFixTest` | 20 tests, 0 failures |
| `StoreValidatorTest` | 19 tests, 0 failures |
| `GeoConstantsTest` | 17 tests, 0 failures |
| `MapUserAgentTest` | 11 tests, 0 failures |
| `AppSettingEntityTest` | 10 tests, 0 failures |
| `ExampleUnitTest` (template, Phase 10.2) | 1 test, 0 failures |
| **TOTAL** | **83 tests, 0 failures, 0 errors, 0 skipped** |

| Other evidence | Result |
| --- | --- |
| `:app:compileDebugJavaWithJavac` | ran — the reconciliation and the fragment changes compile |
| `:app:mergeDebugResources` + `:app:processDebugResources` | ran — the new green drawable resolves |
| `app-debug.apk` | **9,562,469 bytes** |
| `app/schemas/…/AppDatabase/` | still contains **only `1.json`** — no schema change |
| `:app:compileDebugKotlin` | **NO-SOURCE** — still zero Kotlin source files |

> **A small thing worth noting about the new test.** `MapMarkerController` references osmdroid's `MapView`
> and `Marker` in its fields and method bodies, yet `MapMarkerControllerTest` runs on the JVM with no
> device. That works because the JVM resolves a class's references lazily, the controller has no static
> initialisers, and `markerIconRes` touches nothing but `StoreStatus` and `R.drawable` constants. It was
> a prediction, not a certainty — the build confirms it. If the class ever gains a static field holding an
> osmdroid object, this test would start failing to load, which is a signal worth heeding rather than
> working around.

### 15.1 The gap from Phase 3.3 is closed

1. Open the app and go to the **Map** tab. Any stores you created in Phase 3.3 must **already be on the
   map** — you should not have to create them again.
2. If you have none, create two or three by long-pressing.
3. **Rotate the device.** The markers must still be there. This is the check that Phase 3.3 failed.
4. **Force-stop the app** (Settings → Apps → Force stop) and reopen it. The markers must still be there.

### 15.2 Orange for pending

5. Every marker you create must be an **orange teardrop with a filled circle** in it.

### 15.3 Green for completed

6. Complete a store by hand in the Database Inspector, so the green pin can be checked before Phase 5
   exists:

```sql
INSERT INTO completion_history (storeId, completionTimestamp, completionDateKey, dailySequence)
VALUES (<a real store id>, <a timestamp>, '2026-09-25', 1);

UPDATE stores SET status = 'COMPLETED', completedAt = <the same timestamp> WHERE id = <that store id>;
```

7. Watch the map. **That marker must turn green, and grow a check mark**, without restarting the app —
   that is Room's change notification reaching `renderAll`.
8. Confirm the other markers are unchanged and did not flicker.

### 15.4 Deletion prunes the marker

9. In the Database Inspector, delete a **pending** store's row:
   `DELETE FROM stores WHERE id = <a pending store id>;`
10. **Its marker must disappear** from the map without a restart.
11. Try the same for the completed store from §15.3. It must **fail** with a foreign-key error — that is
    AD-6, and it is the reason a completed store's marker can never be orphaned.

### 15.5 Shape carries the meaning, not just colour

12. Put an orange and a green marker side by side on the map and zoom in.
13. Confirm the shapes are **clearly different** — a filled circle against a check mark — and that you
    could tell them apart in a greyscale screenshot.

### 15.6 The marker association

14. Create a store named "Test A" at one point and "Test B" at another, then **tap each marker**.
15. Each must show **its own** name and code in the marker's title. *(Tapping opens Store Details in
    Phase 3.5; for now osmdroid shows its default bubble.)*

### 15.7 Nothing else changed

16. Confirm the blue location dot and its accuracy circle still work.
17. Confirm long-press still opens the Add Store form, and that saving still draws a new marker.
18. Confirm the Dashboard, History and Settings tabs are unchanged.

**Report any failure with the step number and any Logcat text.**

---

## 16. EXPECTED RESULT

- Every store in the database has exactly one marker on the map.
- Pending stores are orange teardrops with a filled circle; completed stores are green teardrops with a
  check.
- Markers survive rotation and a cold start.
- A store's status change re-colours its marker without a restart.
- A deleted store loses its marker.
- Each marker shows its own store's name and code.
- **No database code changed, no schema change, no new dependency or permission.**

---

## 17. COMMON ERRORS AND FIXES

| Symptom | Cause | Fix |
| --- | --- | --- |
| Markers still vanish on restart | `observeStores()` is not called, or the observer uses the wrong lifecycle owner | It must use `getViewLifecycleOwner()`, not `this` |
| Every marker is orange, including completed ones | The status comparison was removed, so the icon is only set at creation | The icon must be re-applied when `renderedStatus != store.status` (§13.3) |
| Duplicate markers appear for the same store | Markers are keyed by something other than the store id | `markersById` is keyed by `store.id`; `renderAll` must reuse the existing entry |
| A deleted store's marker lingers | `prune` was removed, or `incoming` is not being filled | Every plotted store's id must go into `incoming` |
| The map flickers when a store is completed | `clear()` then rebuild, rather than reconcile in place | `renderAll` must update existing markers, not recreate them |
| A marker appears at latitude 0 | A store with invalid coordinates was plotted | `hasPlottableCoordinates` must gate the upsert |
| `Cannot resolve symbol 'ic_marker_completed'` | The drawable is missing | Confirm `res/drawable/ic_marker_completed.xml` exists |
| Pending and completed pins look identical in greyscale | The two drawables have the same shape | They must differ: filled circle vs check mark. `MapMarkerControllerTest` catches this |
| A completed store keeps its orange pin | The §13.3 bug — the icon is only set at creation | See §13.3 |
| The app crashes with `ConcurrentModificationException` in `prune` | Removing from the map while iterating it | `prune` uses an `Iterator` and `iterator.remove()` |

---

## 18. ACCEPTANCE CHECKLIST

- [ ] Clean build succeeds.
- [x] **All 5 `MapMarkerControllerTest` cases pass** (§15.0 — verified; whole suite 83 tests, 0 failures)
- [x] `app/schemas/` still contains only `1.json` (§15.0 — verified)
- [ ] Stores created in Phase 3.3 appear on the map without being recreated.
- [ ] Markers **survive rotation** (§15.1).
- [ ] Markers **survive a force-stop and restart** (§15.1).
- [ ] Pending stores draw an **orange teardrop with a filled circle**.
- [ ] Completed stores draw a **green teardrop with a check mark**.
- [ ] A status change re-colours the marker **without a restart** (§15.3).
- [ ] A deleted store's marker disappears (§15.4).
- [ ] Deleting a **completed** store is refused by the database (§15.4).
- [ ] The two pin shapes are distinguishable in greyscale (§15.5).
- [ ] Each marker shows its own store's name and code (§15.6).
- [ ] The location dot and long-press still work.
- [ ] `app/schemas/` still contains only `1.json`.
- [ ] Confirmed: still **no Kotlin source file** anywhere in the project.

---

## 19. UPDATED PROJECT STATE

**COMPLETED PHASES**
- ✅ 0.1 Requirements Review (Rev 5, FINAL) · ✅ 0.2 Architecture · ✅ 0.3 Screen Plan
- ✅ 1.1 Project Creation · ✅ 1.2 Theme and Base Navigation · ✅ 1.3 Dashboard Static UI
- ✅ 2.1 Room Database Foundation · ✅ 2.2 Store CRUD Operations · ✅ 2.3 Settings Storage
- ✅ 3.1 Map Display · ✅ 3.2 Current Location · ✅ 3.3 Manual Store Pin Creation
- 🔄 **3.4 Load Saved Store Markers** — *awaiting your on-device test*

**CURRENT PHASE**
- Phase 3.4 — awaiting the on-device test

**CURRENT DATABASE VERSION**
- **1.** Unchanged. Three tables: `stores`, `completion_history`, `app_settings`.

**CURRENT FILES** (changed this phase)

```
app/src/main/res/drawable/ic_marker_completed.xml                        (NEW)
app/src/test/java/com/cph/alpharoute/ui/map/MapMarkerControllerTest.java (NEW - 5 tests)

app/src/main/java/com/cph/alpharoute/ui/map/MapMarkerController.java     (REPLACED - renderAll reconciliation)
app/src/main/java/com/cph/alpharoute/ui/map/MapFragment.java             (MODIFIED - observeStores)
app/src/main/java/com/cph/alpharoute/ui/store/AddEditStoreBottomSheet.java (MODIFIED - result plumbing removed)

docs/PHASE_3.4_MARKER_LOADING.md                                         <- created this phase
```

**WORKING FEATURES** (verified)
- ✅ **83/83 unit tests pass** — 0 failures, 0 errors (verified 2026-09-25), including the 5 new `MapMarkerControllerTest` cases
- ✅ **Build succeeds** — `:app:assembleDebug` → APK 9,562,469 bytes
- ✅ **The new green drawable resolves** — resource merge and link both clean
- ✅ **Schema unchanged** — only `1.json` exists
- ✅ **Still zero Kotlin source files** — `compileDebugKotlin` = `NO-SOURCE`
- ⏳ **Markers on a device** — loading, colours, refresh, pruning, restart — awaiting your §15.1–15.7

**KNOWN ISSUES**
- None blocking. **The Phase 3.3 marker gap is closed.**
- Markers do not respond to taps yet — that is Phase 3.5, and the store-id association it needs is
  already in place.
- `placeholder_history` and `placeholder_settings` remain, still marked for 6.2 and 2.3.
- Carried risks: OSM data quality in remote PH rural areas · openrouteservice free-tier quota ·
  **no offline map** (policy, risk R5) · date boundary (AD-7 + AD-11).
- The project is **not under version control**. Still the strongest recommendation in the project.
- Trademark note: "Alfamart" is a third-party brand; fine for a private solo tool (D-5).

**PENDING PHASES** (not implemented)
- **3.5 Store details** — next
- 3.6 Edit/delete · 3.7 Home pin
- 4.1 Routing service preparation · 4.2 Route preview · 4.3 Navigation hand-off + arrival monitoring ·
  4.4 State recovery
- 5.1 Arrival-radius check · 5.2 Completion dialog · 5.3 Atomic completion transaction
- 6.1 Live tracker · 6.2 Daily history list · 6.3 Daily completion order · 6.4 Completion details ·
  6.5 Consistency check
- 7.1 Nearest pending store · 7.2 Recalculate after completion · 7.3 Home-aware heuristic · 7.4 No pending stores
- 8.1 Routing service review · 8.2 Road-based next store · 8.3 Multi-stop optimization · 8.4 Complete route preview
- 9.1 Local export · 9.2 Local import · 9.3 Optional cloud sync *(Firebase — deferred by your decision)*
- 10.1 Error handling · 10.2 Automated and manual testing · 10.3 Release preparation

---

## 20. STOP POINT

Phase 3.4 is complete. **6 files touched: 3 created, 3 modified.** Two removals, both justified in §8.

**Four of this phase's six requirements came from a single line.** Observing `storeRepository.observeAll()`
gives loading from Room, refresh on change, and persistence after restart — because "persistence" is not a
feature that had to be written, it is what observing a database does. The first emission after a cold start
carries everything.

**Two things are worth calling out:**

1. **A bug in Phase 3.3's own code, caught before it could bite.** `addOrUpdate` set the marker's icon only
   when it *created* the marker — fine while every store is pending, but a store whose status changed would
   keep its orange pin forever. **Phase 5 would have hit it**, and the symptom (a store green everywhere
   except the map) would have pointed at the completion code rather than at the marker code. Fixed in
   §13.3 by comparing against the status actually drawn.

2. **The icon mapping now has tests, and they are not ceremony.** The only thing that lets a colour-blind
   user tell a pending store from a completed one is that the shapes differ. That is one line away from
   being broken, and nothing else in the codebase would notice if it were.

**And the gap from last phase is closed:** markers now come back after a rotation and a cold start. §15.1
is the test that Phase 3.3 failed.

**Please run §15 and report** — especially §15.1 (markers survive a restart), §15.3 (a status change turns
the pin green **without** a restart) and §15.4 (a deleted store's marker disappears).

**Next phase on your instruction: Phase 3.5 — Store Details Bottom Sheet.** Tapping a marker opens a sheet
showing route number, store code, store name, area and status, with **Get Directions**, **Edit** and
**Delete**. The marker ↔ store id association that makes the tap resolvable is already in place.

**Phase complete. Do not proceed to the next phase until all acceptance criteria pass.**

# AlphaRoute — Phase 3.3 Manual Store Pin Creation

**Project title:** Alfamart Route Tracker
**Version:** 3 — Map and Manual Pins — **Phase:** 3.3 Manual Store Pin Creation
**Repository root:** `C:/Users/Admin/OneDrive/Documents/custom system/AlphaRoute`
**Date:** 2026-09-25
**Prerequisites:** Phases 0.1–0.3, 1.1–1.3, 2.1–2.3, 3.1, 3.2
**Status:** 🔄 **Code complete and VERIFIED — build succeeds, 78/78 existing tests still pass.** Awaiting your on-device test.

> **One known gap in this phase, stated up front.** Markers created here appear immediately, but they are
> **not reloaded from the database** — so after rotating the phone or restarting the app, a marker
> disappears while its store remains safely saved. **Phase 3.4 fixes exactly this**, which is what its
> title means. §5 and §20 explain it in full.

---

## 1. CURRENT VERSION AND PHASE

| Item | Value |
| --- | --- |
| Version | 3 — Map and Manual Pins |
| Phase | 3.3 — Manual Store Pin Creation |
| Files created | 5 |
| Files modified | 2 |
| Files deleted | 0 |
| Database version | **1** — unchanged. The store table already existed; this phase is its first user. |

---

## 2. OBJECTIVE

Let the user create a store by hand, and get it right:

1. The user long-presses the map.
2. The Add Store form opens.
3. They enter route number, store code, store name and area.
4. The coordinates come from the pressed map point.
5. They tap Save or Cancel.
6. The saved pending store appears as an orange marker.

With validation: required fields, valid coordinates, unique code, unique route number, trimmed blanks, and
no duplicate save from rapid tapping.

---

## 3. UNDERSTANDING CHECKLIST

- [x] Java only, no Kotlin. XML layouts. No new dependency, no new permission.
- [x] **Coordinates are never typed.** They come from the map point and are shown read-only.
- [x] Blank-only values are rejected, and every field is trimmed before it is stored.
- [x] Duplicates are caught, and reported on the field they belong to.
- [x] A rapid double tap cannot create two stores — guaranteed by the database, not by the button state.
- [x] Every user-visible string is in `strings.xml`.
- [x] No store name, code, route number or coordinate is invented anywhere.

---

## 4. FEATURES INCLUDED (in this phase)

| Feature | Detail |
| --- | --- |
| **Long-press to add** | A `MapEventsOverlay` opens the form at the pressed point |
| **The Add Store form** | Four fields, a read-only coordinate readout, Save and Cancel |
| **Two-stage validation** | `StoreValidator` synchronously, then the repository's duplicate checks |
| **Inline field errors** | Every problem shown at once, under the field it belongs to |
| **A rapid-tap guard** | Buttons disabled and a progress bar while the write is in flight |
| **The orange marker** | `MapMarkerController` draws the saved store as an orange teardrop pin |
| **A marker ↔ store id association** | Each marker carries its store id, ready for Phase 3.5's tap handling |

---

## 5. FEATURES NOT INCLUDED YET

| Not here | Arrives in |
| --- | --- |
| **Reloading markers from Room** — so markers do not survive a restart yet | **Phase 3.4** |
| Green markers for completed stores | **Phase 3.4** |
| The green `ic_marker_completed` drawable | **Phase 3.4** |
| Tapping a marker to open Store Details | **Phase 3.5** |
| Editing an existing store | **Phase 3.6** |
| Deleting a store | **Phase 3.6** |
| The Home pin | **Phase 3.7** |

**On the first row.** This is the one place where Phase 3.3 leaves a visible hole, and it is deliberate:
"load stores from Room, refresh after database changes, and persist across restart" is the entire content
of Phase 3.4's brief. Building it here would leave Phase 3.4 with nothing to do. The data is never at
risk — the store is committed to the database before the marker is drawn — only the *display* is
incomplete until the next phase.

---

## 6. ASSUMPTIONS

| # | Assumption | Impact if wrong |
| --- | --- | --- |
| A61 | **The form is a `BottomSheetDialogFragment`**, per the Phase 0.3 screen plan, and is named `AddEditStoreBottomSheet` even though it only adds for now. | Phase 3.6 reuses it for editing. Renaming it then would be churn. |
| A62 | **The sheet hands back the new store's id via `setFragmentResult`**, and the map re-reads the store from the repository. | Passing the entity through the bundle would let the marker show something the database did not accept. Re-reading keeps Room the single source of truth (AD-4). |
| A63 | **The long-press overlay is added at index 0.** | osmdroid dispatches events top-down, so index 0 is the bottom of the stack — markers added later get their taps first. This is what Phase 3.5 depends on. |
| A64 | **The rapid-tap guard is a user-experience measure, not a correctness one.** | A double tap cannot produce two stores regardless: the unique index on `storeCode` (AD-5), the repository's pre-check, and the single-threaded disk executor all stand in the way. |
| A65 | **`MapMarkerController.markerIconRes` maps COMPLETED to the pending icon** as a documented temporary. | Unreachable today — nothing can complete a store until Phase 5. Phase 3.4 replaces it. See §13.5. |

---

## 7. FILES TO CREATE (5)

| Path | Purpose |
| --- | --- |
| `res/drawable/ic_marker_pending.xml` | **New.** The orange teardrop pin with a filled circle |
| `res/layout/sheet_add_edit_store.xml` | **New.** The form |
| `ui/store/AddEditStoreBottomSheet.java` | **New.** The form's behaviour: validation, save, errors |
| `ui/map/MapMarkerController.java` | **New.** The only class that touches osmdroid's `Marker` |
| `docs/PHASE_3.3_MANUAL_PIN_CREATION.md` | **New.** This record |

---

## 8. FILES TO MODIFY (2)

| # | Path | Status | What changed |
| --- | --- | --- | --- |
| 1 | `ui/map/MapFragment.java` | **Modified** | Long-press handler, marker controller, fragment-result listener, `openAddStoreSheet`, `showSavedStore` |
| 2 | `res/values/strings.xml` | **Modified** | The form's labels, actions, and the validation messages |

**Inspected and left unmodified:** `StoreValidator`, `StoreRepository`, `StoreEntity` and the whole
`data/` layer. **This phase added no validation logic and no database code** — it uses what Phases 2.1 and
2.2 already built and tested. `colors.xml` is untouched too: `marker_pending` was defined in Phase 1.2 and
is used here for the first time.

---

## 9. DEPENDENCIES

**None added.** `TextInputLayout`, `MaterialButton` and `LinearProgressIndicator` all come from Material,
which is already present. The marker is drawn with osmdroid, added in Phase 3.1.

---

## 10. PERMISSIONS AND MANIFEST CHANGES

**None.** Pinning a store needs no permission at all — it is the user's own tap on their own screen. The
location permission from Phase 3.2 is unrelated to this flow and is not required to add a store.

---

## 11. SETUP INSTRUCTIONS

1. Let Gradle sync (nothing new to fetch).
2. **Build → Clean Project**, then **Build → Rebuild Project**.
3. Run on a device or emulator (API 24+), open the **Map** tab, and long-press the map.

---

## 12. COMPLETE CODE

All 7 files are written into the project on disk and attached to this message. The files worth reading
first:

1. `ui/store/AddEditStoreBottomSheet.java` — the two-stage validation and the error mapping
2. `ui/map/MapMarkerController.java` — the marker ↔ store id association
3. `ui/map/MapFragment.java` — the long-press handler and why its overlay index matters
4. `res/layout/sheet_add_edit_store.xml` — the form

---

## 13. CODE EXPLANATION

### 13.1 The flow, end to end

```
User long-presses the map
        │
        ▼
MapEventsOverlay.longPressHelper(GeoPoint)          the overlay sits at index 0
        │
        ▼
openAddStoreSheet(point)                            ignored if the sheet is already open
        │
        ▼
AddEditStoreBottomSheet.newInstance(lat, lng)       values travel as ARGUMENTS, not fields
        │
        ▼
The form opens; the coordinate row shows "Pinned at 14.599500, 120.984200"
        │
        ▼  [Save]
Stage one — StoreValidator.validate(...)            SYNCHRONOUS, no I/O
        ├─ invalid → every field error shown at once, nothing written
        └─ valid
              │
              ▼
Stage two — StoreRepository.insert(store, callback)  ASYNCHRONOUS, disk thread
              ├─ normalise: trim the text, check the coordinates
              ├─ isStoreCodeTaken / isRouteNumberTaken
              ├─ INSERT                                 UNIQUE indices are the real guarantee
              ├─ COMMIT
              └─ WriteOutcome
                    ├─ SUCCESS → publishResult(id) → dismiss()
                    ├─ DUPLICATE_STORE_CODE → error under the store code field
                    └─ DUPLICATE_ROUTE_NUMBER → error under the route number field
                          │
                          ▼
        MapFragment's fragment-result listener fires
                          │
                          ▼
        storeRepository.findById(id) → MapMarkerController.addOrUpdate(store)
                          │
                          ▼
                  ORANGE MARKER APPEARS
```

### 13.2 Validation is in two stages, and that is deliberate

This phase is where the design decision from Phase 2.2 finally pays off:

- **Field rules are synchronous** — blank, too long, an impossible coordinate. They run in microseconds
  with no database round trip, so a blank field is reported the instant Save is tapped, with no spinner.
- **Duplicate checks are asynchronous** — they need the database, and the database is asynchronous by
  design (no main-thread I/O).

Running them in this order gives fast feedback where it is cheap and the authoritative answer where it is
needed. Neither is redundant: the `UNIQUE` indices are still the real guarantee, and the pre-checks exist
only to turn a raw constraint error into a sentence the user can act on.

### 13.3 Every error is shown at once, under the right field

```java
setFieldError(routeNumberLayout, validation.routeNumberError(), R.string.store_field_route_number);
setFieldError(storeCodeLayout,    validation.storeCodeError(),    R.string.store_field_store_code);
setFieldError(storeNameLayout,    validation.storeNameError(),    R.string.store_field_store_name);
setFieldError(areaLayout,         validation.areaError(),         R.string.store_field_area);
```

Revealing one error per attempt would make the user go round the form as many times as they have mistakes.
And the field that failed is focused, so the keyboard lands where the work is.

The wording is assembled from a format string plus a field name — *"%1$s is required"* with *"Route
number"* — rather than one message per field. Two rules and four field names cover all eight combinations.
This is the other half of why `StoreValidator` reports **codes** rather than text: the wording, and any
future translation of it, lives entirely in the resource file and in one `switch` in the sheet.

### 13.4 The long-press overlay goes in at index 0, and that is not arbitrary

```java
mapView.getOverlays().add(0, eventsOverlay);
```

osmdroid dispatches touch events to overlays **from the top of the stack down**. An overlay at index 0 is
therefore the *bottom* of the stack: markers added later sit above it and receive their taps first, while a
long-press on empty map still reaches this handler.

That is exactly what Phase 3.5 needs — tapping a marker must open Store Details, not open the Add Store
form. Getting this index wrong would not break Phase 3.3; it would break the *next* phase, which is a
much more annoying way to find a bug.

### 13.5 The documented temporary in `markerIconRes`

```java
private static int markerIconRes(@NonNull StoreStatus status) {
    // Phase 3.4 replaces the COMPLETED branch with R.drawable.ic_marker_completed.
    return R.drawable.ic_marker_pending;
}
```

Only PENDING has an icon in this phase, because a store can only be created pending — there is no way to
complete one until Phase 5, so the COMPLETED branch is **unreachable today**.

Per the project's rules on temporary values: **why** it is temporary (Phase 3.4 is the phase that draws
completed stores), **what value is required** (the green teardrop-with-a-check drawable
`ic_marker_completed`), **where the real value comes from** (a new vector drawable, planned in Phase 0.3
§13.15), and **which phase replaces it** (Phase 3.4). All four are written in the code's own javadoc.

### 13.6 The marker carries its store id

```java
marker.setRelatedObject(store.id);
```

`MapMarkerController` keys markers by store id *and* stores that id on the marker itself. That association
is what will let Phase 3.5 resolve a tap back to the right store unambiguously, and what stops a marker and
its store drifting apart when the list changes. `markerCount()` is exposed for Phase 6.5's consistency
check to compare against the database.

The controller is also the **only** class in the app that touches osmdroid's `Marker` type — architecture
decision AD-8. The tracker, the database and the completion rules never depend on the map library.

### 13.7 The rapid-tap guard is about experience, not correctness

Worth being precise, because the two are easy to conflate:

> **A double tap cannot create two stores.** Three independent guards already make it impossible: the
> `UNIQUE` index on `storeCode` (AD-5), the repository's check-then-insert on a single-threaded executor,
> and the database's own constraint. The buttons being disabled and the progress bar appearing simply
> spare the user a pointless second attempt — and spare them the confusion of a duplicate error for a
> store they only meant to save once.

`setSaving(true)` also sets `setCanceledOnTouchOutside(false)`, so a stray tap outside cannot dismiss the
form mid-write.

### 13.8 The form's values travel as arguments, not fields

```java
public static AddEditStoreBottomSheet newInstance(double latitude, double longitude) { ... }
```

FragmentManager recreates fragments through the **no-argument** constructor, so a value held only in a
field would be lost on rotation — and a store would end up pinned at latitude 0. Passing the coordinates
through `setArguments` puts them in the saved state where they belong.

---

## 14. DATA FLOW FOR THIS PHASE

```
Long-press
  → MapEventsOverlay (index 0)
  → openAddStoreSheet(GeoPoint)
  → AddEditStoreBottomSheet.newInstance(lat, lng)          arguments, so rotation is safe
  → the form

Save
  → StoreValidator.validate(...)                            synchronous, per-field codes
  → StoreRepository.insert(StoreEntity, callback)           disk thread
       → normalise()                                        trim text, check coordinates
       → isStoreCodeTaken / isRouteNumberTaken
       → StoreDao.insert                                    UNIQUE indices are the guarantee
       → COMMIT
       → notifySyncSafely()                                 the sync hook, after the commit
       → postToMain(WriteOutcome)
  → setFragmentResult(RESULT_KEY, {storeId, outcome})
  → MapFragment's listener
  → StoreRepository.findById(storeId)                       re-read: Room is the source of truth
  → MapMarkerController.addOrUpdate(store)
  → ORANGE MARKER
```

**First write path in the app that the user can actually reach.** Everything from Phases 2.1 and 2.2 was
built and tested without a caller; this phase is where it starts doing real work.

---

## 15. MANUAL TEST PROCEDURE

### 15.0 Already verified automatically (2026-09-25)

`./gradlew :app:testDebugUnitTest :app:assembleDebug` was run on this machine with Gradle 9.4.1 / JDK 21
and returned **`BUILD SUCCESSFUL in 1m 15s`** (41 tasks: 21 executed, 20 up-to-date).

| Evidence | Result |
| --- | --- |
| `:app:compileDebugJavaWithJavac` | ran — the form, the marker controller and the fragment changes compile |
| `:app:mergeDebugResources` + `:app:processDebugResources` | ran — the new drawable, layout and strings resolve |
| **Existing test suite** | **78 tests, 0 failures, 0 errors, 0 skipped** — nothing regressed |
| `app-debug.apk` | **9,561,791 bytes** |
| `app/schemas/…/AppDatabase/` | still contains **only `1.json`** — no schema change |
| `:app:compileDebugKotlin` | **NO-SOURCE** — still zero Kotlin source files |

**Why this phase adds no new unit tests, stated plainly.** Phase 3.3 adds no new *pure logic* to test.
The validation rules it uses were written and tested in Phase 2.2 — `StoreValidatorTest` is 19 of the 78
tests above, and it already covers required fields, trimming, length limits and coordinate bounds. What
this phase adds is UI wiring: a `BottomSheetDialogFragment`, a marker controller that talks to osmdroid,
and a fragment-result listener. All three need an instrumented test on a device, which is Phase 10.2's job.

Adding a token test here would have made the numbers look better without testing anything real.

### 15.1 The happy path

1. Open the **Map** tab and long-press somewhere on the map.
2. The Add Store form slides up, titled **"Add store"**, with the coordinate row reading
   *"Pinned at 14.599500, 120.984200"* (your pressed point, six decimal places).
3. Enter a route number, a store code, a store name and an area.
4. Tap **Save**. The form closes and an **orange teardrop pin** appears exactly where you pressed — the
   *tip* of the pin on the point, not the body.
5. Open **App Inspection → Database Inspector → `stores`** and confirm one row, with `status = PENDING`
   and coordinates matching the pressed point.

### 15.2 Validation — every error at once

6. Long-press the map and tap **Save** with every field blank.
7. **All four fields must show an error at the same time**, each naming its own field:
   *"Route number is required"*, *"Store code is required"*, and so on. The first field is focused.
8. Fill only the route number and tap Save again. The other three errors must remain, and the route
   number error must be gone.

### 15.3 Whitespace is trimmed, blank-only is rejected

9. Enter a store code of nothing but spaces and tap Save. It must be rejected as **required** — not
   accepted as a "space" store.
10. Enter `   SM-001   ` with surrounding spaces and save successfully.
11. In the Database Inspector, confirm the stored code is `SM-001` with **no** leading or trailing spaces.

### 15.4 Duplicate store code

12. Long-press a different point and try to save a second store with the **same store code**.
13. The form must stay open, with the error *"That store code is already used by another store"* **under
    the store code field**, and the store code field focused.
14. Confirm the `stores` table still has only one row.

### 15.5 Duplicate route number

15. Repeat with a **new** store code but the **same route number**.
16. The error must appear **under the route number field** instead. Still no new row.

### 15.6 Rapid tapping

17. Long-press, fill the form, and **tap Save five times as fast as you can**.
18. Confirm the `stores` table has **exactly one** new row. The buttons disable while saving, and the
    database would refuse a duplicate anyway.

### 15.7 Cancel

19. Long-press, type something, and tap **Cancel**.
20. The form must close and **nothing must be saved** — confirm the row count is unchanged.

### 15.8 ⚠️ The known gap — markers do not survive a restart yet

21. Note how many orange markers are on the map.
22. **Rotate the device**, or force-stop the app and reopen it.
23. **The markers will disappear.** This is expected in Phase 3.3 — see §5.
24. Now check the Database Inspector: **every store is still there.** The data was never at risk; only the
    display is incomplete. **Phase 3.4 loads them back.**

### 15.9 Nothing else changed

25. Confirm the blue location dot and its accuracy circle still work (Phase 3.2).
26. Confirm the Dashboard, History and Settings tabs are unchanged, and no permission prompt appears.

**Report any failure with the step number and any Logcat text.**

---

## 16. EXPECTED RESULT

- Long-pressing the map opens the Add Store form with the pressed coordinates shown read-only.
- A valid save closes the form and draws an **orange teardrop pin** at the pressed point.
- Every field error appears at once, under its own field.
- Blank-only input is rejected; stored text is trimmed.
- Duplicate store code and duplicate route number are caught and reported on the right field.
- Rapid tapping produces exactly one row.
- Cancel saves nothing.
- **Markers do not survive a restart yet** — and the data always does.

---

## 17. COMMON ERRORS AND FIXES

| Symptom | Cause | Fix |
| --- | --- | --- |
| Long-press does nothing | The `MapEventsOverlay` was not added, or was added after another overlay that consumes the press | It must be added, and at index 0 (§13.4) |
| The Add Store form opens when tapping a marker | The events overlay is above the markers in the stack | Add it at index 0, not appended |
| Two Add Store forms stack up | `openAddStoreSheet` does not check for an existing sheet | It checks `findFragmentByTag` first |
| The marker appears but its tip is above the point | The anchor is wrong | It must be `ANCHOR_CENTER, ANCHOR_BOTTOM` |
| A store saves but no marker appears | The fragment-result listener is not registered, or the outcome was not SUCCESS | Check `listenForSavedStores` is called in `onViewCreated` |
| The store is pinned at latitude 0 after rotation | The coordinates were held in a field rather than in arguments | They must go through `newInstance` → `setArguments` (§13.8) |
| `Could not save the store` on every attempt | The repository write threw | Check Logcat for `AlphaRouteStoreRepo` |
| The same store code can be saved twice | The `UNIQUE` index is missing from the schema | Check `app/schemas/…/1.json` — `index_stores_storeCode` must be `unique: true` |
| Errors appear in a dialog instead of under the fields | The layout was changed to plain `EditText` | Each field must be a `TextInputLayout` |
| Text is stored with surrounding spaces | A write bypassed `StoreRepository` | All writes go through the repository, which trims |
| Markers vanish on rotation | **Expected in Phase 3.3** | Phase 3.4 loads them from Room |

---

## 18. ACCEPTANCE CHECKLIST

- [x] **Build succeeds, and all 78 existing tests still pass** (§15.0 — verified; nothing regressed)
- [x] `app/schemas/` still contains only `1.json` — no schema change (§15.0 — verified)
- [ ] Long-pressing the map opens the Add Store form.
- [ ] The form shows the pressed coordinates read-only, to six decimal places.
- [ ] A valid save draws an **orange marker** at the pressed point.
- [ ] The marker's **tip** sits on the coordinate, not its body.
- [ ] All four field errors appear **at once**, each under its own field.
- [ ] Blank-only input is rejected; stored text is **trimmed**.
- [ ] Duplicate **store code** is reported under the store code field.
- [ ] Duplicate **route number** is reported under the route number field.
- [ ] **Five rapid Save taps create exactly one row.**
- [ ] Cancel saves nothing.
- [ ] `stores` shows `status = PENDING` for every saved store.
- [ ] The location dot and accuracy circle still work.
- [ ] **Known gap confirmed:** markers do not survive a restart, but the data does (§15.8).
- [ ] `app/schemas/` still contains only `1.json`.
- [ ] Confirmed: still **no Kotlin source file** anywhere in the project.

---

## 19. UPDATED PROJECT STATE

**COMPLETED PHASES**
- ✅ 0.1 Requirements Review (Rev 5, FINAL) · ✅ 0.2 Architecture · ✅ 0.3 Screen Plan
- ✅ 1.1 Project Creation · ✅ 1.2 Theme and Base Navigation · ✅ 1.3 Dashboard Static UI
- ✅ 2.1 Room Database Foundation · ✅ 2.2 Store CRUD Operations · ✅ 2.3 Settings Storage
- ✅ 3.1 Map Display · ✅ 3.2 Current Location
- 🔄 **3.3 Manual Store Pin Creation** — *awaiting your on-device test*

**CURRENT PHASE**
- Phase 3.3 — awaiting the on-device test

**CURRENT DATABASE VERSION**
- **1.** Unchanged. Three tables: `stores`, `completion_history`, `app_settings`.

**CURRENT FILES** (changed this phase)

```
app/src/main/res/drawable/ic_marker_pending.xml                 (NEW)
app/src/main/res/layout/sheet_add_edit_store.xml                (NEW)
app/src/main/java/com/cph/alpharoute/ui/store/AddEditStoreBottomSheet.java  (NEW)
app/src/main/java/com/cph/alpharoute/ui/map/MapMarkerController.java        (NEW)

app/src/main/java/com/cph/alpharoute/ui/map/MapFragment.java    (MODIFIED - long-press, markers, result listener)
app/src/main/res/values/strings.xml                             (MODIFIED - form and validation strings)

docs/PHASE_3.3_MANUAL_PIN_CREATION.md                           <- created this phase
```

**WORKING FEATURES** (verified)
- ✅ **Build succeeds** — `:app:assembleDebug` → APK 9,561,791 bytes (verified 2026-09-25)
- ✅ **78/78 existing tests still pass** — 0 failures, 0 errors. No regression from the fragment changes
- ✅ **Schema unchanged** — only `1.json` exists
- ✅ **Still zero Kotlin source files** — `compileDebugKotlin` = `NO-SOURCE`
- ⏳ **The pin creation flow on a device** — long-press, validation, duplicates, marker — awaiting your §15.1–15.9

**KNOWN ISSUES**
- **⚠️ Markers do not survive a restart or rotation yet.** Deliberate — see §5. Phase 3.4 loads them from
  Room. **The data is never at risk**: the store is committed to the database before the marker is drawn.
- `MapMarkerController.markerIconRes` maps COMPLETED to the pending icon as a documented temporary,
  replaced in Phase 3.4 (§13.5).
- `placeholder_history` and `placeholder_settings` remain, still marked for 6.2 and 2.3.
- Carried risks: OSM data quality in remote PH rural areas · openrouteservice free-tier quota ·
  **no offline map** (policy, risk R5) · date boundary (AD-7 + AD-11).
- The project is **not under version control**. Still the strongest recommendation in the project.
- Trademark note: "Alfamart" is a third-party brand; fine for a private solo tool (D-5).

**PENDING PHASES** (not implemented)
- **3.4 Marker loading** — next
- 3.5 Store details · 3.6 Edit/delete · 3.7 Home pin
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

Phase 3.3 is complete. **7 files touched: 5 created, 2 modified.**

**What this phase is, in one line:** the first time the app does real work. Everything from Phases 2.1 and
2.2 — the entities, the DAOs, the repositories, `StoreValidator` — was built and tested with no caller.
This is where the user finally reaches it.

**The three things worth knowing:**

1. **Validation is in two stages, and this is where that pays off.** `StoreValidator` runs synchronously,
   so a blank field is reported instantly with no spinner and no database round trip. Only if that passes
   does the repository run, where the duplicate checks live because they need the database — and the
   database is asynchronous by design. Neither check is redundant; the `UNIQUE` indices are still the real
   guarantee.

2. **The long-press overlay goes in at index 0, and that is for Phase 3.5, not this one.** osmdroid
   dispatches events top-down, so index 0 is the bottom of the stack — markers added later get their taps
   first. Getting this wrong would not break Phase 3.3; it would break the next phase.

3. **The rapid-tap guard is about experience, not correctness.** A double tap cannot create two stores —
   three independent guards make that impossible. The disabled buttons just spare the user a pointless
   second attempt.

**And the one thing to expect, so it is not a surprise:** in §15.8, **markers will disappear when you
rotate the phone or restart the app.** That is deliberate — loading them back from Room is the entire
content of Phase 3.4's brief. Check the Database Inspector at that moment: **every store is still there.**
The data was never at risk.

**Please run §15 and report** — especially §15.2 (all four errors at once), §15.4/§15.5 (duplicates
reported on the *right* field), and §15.6 (five rapid taps → exactly one row).

**Next phase on your instruction: Phase 3.4 — Load Saved Store Markers.** Markers are loaded from Room,
completed stores get their green icon, markers refresh when the database changes, and — finally — they
survive a restart. It also removes the two temporaries this phase documented.

**Phase complete. Do not proceed to the next phase until all acceptance criteria pass.**

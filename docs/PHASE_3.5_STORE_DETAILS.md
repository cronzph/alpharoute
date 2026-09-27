# AlphaRoute — Phase 3.5 Store Details Bottom Sheet

**Project title:** Alfamart Route Tracker
**Version:** 3 — Map and Manual Pins — **Phase:** 3.5 Store Details Bottom Sheet
**Repository root:** `C:/Users/Admin/OneDrive/Documents/custom system/AlphaRoute`
**Date:** 2026-09-25
**Prerequisites:** Phases 0.1–0.3, 1.1–1.3, 2.1–2.3, 3.1–3.4
**Status:** 🔄 **Code complete and VERIFIED — 99/99 unit tests pass, build succeeds.** Awaiting your on-device test.

> **This phase makes a marker mean something.** Tapping one now opens the store it stands for: its route
> number, code, name, area, status and coordinates — and, for a completed store, when it was completed.

---

## 1. CURRENT VERSION AND PHASE

| Item | Value |
| --- | --- |
| Version | 3 — Map and Manual Pins |
| Phase | 3.5 — Store Details Bottom Sheet |
| Files created | 6 |
| Files modified | 6 |
| Files deleted | 0 |
| Database version | **1** — unchanged. No database code was touched. |
| Unit tests | **99** (was 83 — 16 new) |

---

## 2. OBJECTIVE

Let the user tap a store marker and see what that store is:

- Tap a marker → the Store Details sheet opens for **that** store
- Show the status chip, route number, store code, store name, area and coordinates
- For a completed store, also show the completion date, time and daily order
- Handle a store that no longer exists by closing and saying so
- **Offer no way to mark a store complete from here**

---

## 3. UNDERSTANDING CHECKLIST

- [x] Java only, no Kotlin. No new dependency, no new permission, no schema change.
- [x] The sheet **reads** the store from Room by id; it never writes and never caches a copy.
- [x] The sheet is opened with a **store id**, so it survives rotation by re-reading (A62 / AD-4).
- [x] **A pending store offers no completion action anywhere on this screen** — or in the whole UI.
- [x] The status chip's **text** says "Pending" or "Completed", so colour is never the only signal.
- [x] A store deleted while its marker was on screen closes the sheet with a message, rather than
      showing a blank panel.
- [x] The Completion section appears only when a completion record was **actually found**.
- [x] No store name, code, route number or coordinate is invented anywhere.

---

## 4. FEATURES INCLUDED (in this phase)

| Feature | Detail |
| --- | --- |
| **Tap a marker → that store** | The marker's own store-id association is read back, so the sheet can only ever open the store that was tapped |
| **`StoreDetailBottomSheet`** | New sheet, opened with a store id and nothing else |
| **Status chip** | "Pending" on orange, "Completed" on green — light and dark variants |
| **Five stored fields** | Route number · Store code · Store name · Area · Coordinates |
| **Completion section** | Date, time and daily order — shown only for a store that has a completion record |
| **Loading state** | A progress row while the store is re-read from Room |
| **Store-not-found state** | The sheet closes with "That store no longer exists." |
| **`DateTimeHelper` + 16 tests** | A pure, timezone-testable date/time formatter |

---

## 5. FEATURES NOT INCLUDED YET

| Not here | Arrives in |
| --- | --- |
| **Get Directions** button | **Phase 4.2** (route preview) then 4.3 (the hand-off itself) |
| **Edit** button | **Phase 3.6** |
| **Delete** button | **Phase 3.6** |
| Editing a store from this sheet | **Phase 3.6** |
| The Home pin | **Phase 3.7** |
| Route polyline | **Phase 4.2** |
| Any completion action | **Phase 5.2** — and only through the arrival flow |

---

## 6. ASSUMPTIONS

| # | Assumption | Impact if wrong |
| --- | --- | --- |
| A70 | **The sheet is opened with a store id and re-reads the store from Room itself.** | Room stays the single source of truth (AD-4) and the sheet survives rotation with no extra code. Passing the store object instead would show a snapshot taken when the marker was drawn — possibly no longer true — and would be lost on rotation. |
| A71 | **The Get Directions, Edit and Delete buttons are NOT drawn in this phase.** | See §13.2 for the full rule-11 justification. A button that does nothing when tapped is worse than a button that is not there: it reads as a bug. Each arrives with the phase that can make it work. |
| A72 | **The status chip is a tinted `TextView`, not a Material `Chip`.** | A `Chip` paints its label through its own `ChipDrawable`, which takes the text colour at inflation time. Switching it between orange and green at runtime means fighting the component for control of its own label. A `TextView` is a surface this sheet owns outright. |
| A73 | **The Completion section is gated on a completion record being found, not on the store's status.** | A store marked COMPLETED with no history row is a data inconsistency. Showing nothing is honest; showing an invented date would violate "never invent data". |
| A74 | **The chip uses a new light/dark container+text colour pair, not the marker colours.** | White text on `marker_pending` (#F57C00) is about **2.6:1**, which is not legible at chip size. #E65100 on #FFE0B2 clears 4.5:1 while still reading as orange. |
| A75 | **The tap is consumed (`return true`), so osmdroid's own info-window bubble never appears.** | Otherwise the default bubble would pop up underneath our sheet. |
| A76 | **The store id is read back off the marker with `getRelatedObject()` rather than captured.** | If the association were ever lost, the tap falls through and does nothing — rather than opening the wrong store. A silently wrong store is far worse than a dead tap. |

---

## 7. FILES TO CREATE (6)

| # | Path | Purpose |
| --- | --- | --- |
| 1 | `app/src/main/java/com/cph/alpharoute/util/DateTimeHelper.java` | **New.** Pure Java. Epoch millis → the date and time text a person reads |
| 2 | `app/src/test/java/com/cph/alpharoute/util/DateTimeHelperTest.java` | **New.** 16 JVM tests, including the midnight boundaries |
| 3 | `app/src/main/res/layout/sheet_store_detail.xml` | **New.** The sheet: chip, five fields, Completion section, Close |
| 4 | `app/src/main/res/drawable/bg_store_status_chip.xml` | **New.** The rounded background the chip tints |
| 5 | `app/src/main/java/com/cph/alpharoute/ui/store/StoreDetailBottomSheet.java` | **New.** The sheet itself |
| 6 | `docs/PHASE_3.5_STORE_DETAILS.md` | **New.** This record |

---

## 8. FILES TO MODIFY (6)

| # | Path | Status | What changed |
| --- | --- | --- | --- |
| 1 | `ui/map/MapMarkerController.java` | **Modified** | Added the `OnStoreClickListener` seam and the marker click listener |
| 2 | `ui/map/MapFragment.java` | **Modified** | Wires the listener; added `openStoreDetailSheet(long)`; clears the listener in `onDestroyView` |
| 3 | `res/values/strings.xml` | **Modified** | The Store Details strings (14 new) |
| 4 | `res/values/colors.xml` | **Modified** | The light status-chip colour pair |
| 5 | `res/values-night/colors.xml` | **Modified** | The dark status-chip colour pair |
| 6 | `docs/HANDOFF.md` | **Modified** | Status, phase history, file inventory, test count |

**Inspected and left unmodified:** the entire `data/` layer, `domain/`, `location/`, `AddEditStoreBottomSheet`,
and the store/marker drawing logic. This phase reads the database and adds one screen; it does not change
how anything is written.

### No removals this phase

Phase 3.4 removed two things and justified both. This phase removes nothing. Two *comments* were corrected
(the map's long-press handler no longer says "tapping a marker is Phase 3.5", and `MapMarkerController`'s
class note now describes the tap path in the present tense), but no behaviour was taken away.

---

## 9. DEPENDENCIES

**None added.** `java.time` was already available through core library desugaring (AD-7), and
`DateTimeHelper` is the first thing in the project to use it.

---

## 10. PERMISSIONS AND MANIFEST CHANGES

**None.** Verified against the merged manifest after the build:

```
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="com.cph.alpharoute.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" />
```

The last one is added automatically by `androidx.core` — self-scoped, no prompt. Still **no background
location**, and no `<queries>` element yet (that is Phase 4.3's, for the Google Maps hand-off).

---

## 11. SETUP INSTRUCTIONS

1. Let Gradle sync (nothing new to fetch).
2. **Build → Clean Project**, then **Build → Rebuild Project**.
3. Run on a device or emulator. You need at least one store from Phase 3.3 or 3.4 on the map.

---

## 12. COMPLETE CODE

All 12 files are written into the project on disk. The files worth reading first:

1. `ui/store/StoreDetailBottomSheet.java` — why the sheet takes an id rather than a store
2. `ui/map/MapMarkerController.java` — the `OnStoreClickListener` seam that keeps osmdroid behind its boundary
3. `app/src/test/java/…/DateTimeHelperTest.java` — the midnight boundaries, pinned to a timezone
4. `res/layout/sheet_store_detail.xml` — the header comment explains what is deliberately missing

---

## 13. CODE EXPLANATION

### 13.1 Why the sheet takes a store id and not a store

The obvious alternative is for the map to hand over the `StoreEntity` it already has. It was rejected:

| | |
| --- | --- |
| **Room is the single source of truth (AD-4)** | The map's list came from a Room query, but it is still a snapshot. Between the marker being drawn and the user tapping it, the store could have been edited or deleted. Re-reading by id means the sheet can only ever display what is actually stored. |
| **Rotation** | FragmentManager recreates a fragment through its no-argument constructor. A store held in a field would be lost and the sheet would come back empty. An id in the arguments just gets looked up again. This is assumption A62 from Phase 3.3, applied again. |
| **It keeps the map out of the sheet's business** | The map passes a `long`. It does not know what the sheet shows, and the sheet does not know the map exists. |

The cost is one extra database read — on a single-threaded disk executor, for one row, behind a
progress bar that the screen plan asked for anyway.

### 13.2 Why three buttons the plan calls for are not on the sheet

Phase 0.3 §13.6 gives screen 5 three actions: **Get Directions** (primary), **Edit** and **Delete**. None
of them is on the sheet yet, and that is a deliberate decision under rule 11 rather than an oversight.

| Button | What it would have to do | Which phase builds that | Why it is not drawn yet |
| --- | --- | --- | --- |
| **Get Directions** | Open **Route Preview** — which needs a real driving route from openrouteservice, and that account does not exist yet (Phase 4.1) | **4.2** | Wiring it straight to the Google Maps app instead would contradict the approved screen plan and would then have to be undone in 4.2 |
| **Edit** | Reopen `AddEditStoreBottomSheet` in edit mode | **3.6** | Edit mode does not exist yet — the sheet is add-only |
| **Delete** | Confirm, then `StoreRepository.delete()` | **3.6** | The confirmation dialog and the AD-6 "protected by history" message are 3.6's subject |

**The rule that decides this:** rule 8 forbids placeholders, and a button that does nothing when tapped is
a placeholder the user can see. It is not a greyed-out button with a reason next to it — it is a control
that accepts a tap and then ignores it, which reads as a broken app. So each button is added by the phase
that can make it work, and until then the sheet simply does not have it.

**The one thing this sheet must never gain** is a completion action. A pending store offers no way to be
marked complete here — or anywhere else in the UI. Completion is reachable only through the arrival flow:
the app detects that the user is inside the arrival radius and asks "Store completed?" with NO and YES. A
button here would let a store be counted from anywhere in the country, which is the single thing the whole
design exists to prevent.

### 13.3 How a tap becomes a store

osmdroid is confined to `MapMarkerController` (AD-8). The map screen is therefore not allowed to register
an osmdroid click listener itself. So the controller takes the listener and translates:

```
osmdroid Marker.onSingleTapConfirmed
      │  hitTest → the tap landed on this marker's icon
      ▼
Marker.OnMarkerClickListener  (lambda, set once when the marker is created)
      │  marker.getRelatedObject()  → the store id put there in upsert()
      ▼
MapMarkerController.OnStoreClickListener.onStoreClicked(long storeId)
      │
      ▼
MapFragment.openStoreDetailSheet(storeId)  →  StoreDetailBottomSheet
```

Three details in that path are load-bearing:

- **The id comes off the marker, not from the loop variable.** `setRelatedObject(store.id)` was already
  there from Phase 3.4, put there precisely so this phase could resolve a tap. Reading it back means a
  marker that somehow lost its association produces a dead tap rather than opening the wrong store.
- **`return true` consumes the tap.** osmdroid's default behaviour, when no listener is set, is
  `Marker.onMarkerClickDefault` — which shows its own info-window bubble and pans the map. Left alone,
  that bubble would appear underneath the new sheet.
- **The listener is read from the field at tap time**, not captured when the marker is created. Markers
  are created by `renderAll`, which runs before the fragment wires its listener; capturing would have
  meant the very first markers on screen were the ones that could not be tapped.

The verified signature came from osmdroid's own sources, not from memory:

```java
public interface OnMarkerClickListener {
    abstract boolean onMarkerClick(Marker marker, MapView mapView);
}
```

### 13.4 Why `DateTimeHelper` is its own class with 16 tests

Formatting a timestamp looks like a one-liner. It is not, and every interesting case is an off-by-one-day
case — which is exactly the kind of bug that survives to the user:

- A store completed at **11:50 pm** must read as that day.
- A store completed at **00:10** must read as the next day.
- The **same instant is a different date in a different timezone**.

So the formatting lives in a pure class (AD-11) where a test can reach it, instead of inline in a Fragment
where nothing can. `dateText` and `timeText` both delegate to one private method, so the date and the time
on the sheet cannot disagree about which moment they describe.

**Both the zone and the locale are parameters.** The convenience overloads use the device's own; the
three-argument versions exist so the test can pin them. That is not ceremony — without it the test would
pass on this machine and fail on a build server in another timezone, and a test that depends on where it
runs is worse than no test. The suite pins `Asia/Manila`, which is UTC+8 with no daylight saving, so a
fixed instant always maps to the same wall clock.

Two of the 16 tests use independently checkable facts rather than round-trips, so a systematic error in the
epoch conversion cannot hide behind expectations produced by the same conversion:

- `dateText(0L, UTC) == "1 Jan 1970"`
- `dateText(1_000_000_000_000L, UTC) == "9 Sep 2001"` — the "Unix billennium"

### 13.5 Why the status chip is a `TextView`

The screen plan calls it a chip, and Material Components has a `Chip`. It was not used, for a concrete
reason: a `Chip` paints its label through a `ChipDrawable`, which reads the text colour when the view is
inflated. Changing that colour later means going through the drawable, and the two can silently disagree —
leaving dark text on a dark container with no compile error and no crash. A `TextView` with a tinted
rounded background has both colours set directly by the sheet, so what is drawn is what was asked for.

The colours themselves are a new pair rather than the marker colours, and that is a readability decision:

| | Container | Text | Contrast |
| --- | --- | --- | --- |
| Pending (light) | #FFE0B2 | #E65100 | comfortably past 4.5:1 |
| Completed (light) | #C8E6C9 | #1B5E20 | comfortably past 4.5:1 |
| Pending (dark) | #4E2A00 | #FFB74D | light text on a dark container |
| Completed (dark) | #1B3D1E | #81C784 | light text on a dark container |

White text on `marker_pending` (#F57C00) would have been about **2.6:1** — not legible at this size. Unlike
the marker colours, these *do* have a night variant, because the chip sits on the app surface rather than
on a map tile.

The chip is non-interactive, and the status word is in the chip itself. Colour only reinforces it, because
orange and green are the classic red/green-confusion pair.

### 13.6 Why the Completion section is gated on the history row

The sheet reveals the Completion section when `CompletionRepository.findForStore()` returns a row — **not**
when `store.isCompleted()` is true. Those normally agree: Phase 5.3's completion transaction will write both
in one transaction. But if they ever disagree, a store whose status says COMPLETED with no history row is a
data inconsistency, and the honest thing to show is nothing at all. Rendering `completedAt` in that case
would be inventing a completion record.

The timestamp displayed comes from the **history row**, not from `StoreEntity.completedAt`. The history
table is the tracker's record of truth, and the two fields should always describe the same moment.

### 13.7 A trap the build caught

`sheet_store_detail.xml` failed its very first resource parse:

```
[Fatal Error] sheet_store_detail.xml:99:20: The string "--" is not permitted within comments.
```

A comment used a row of dashes as a separator (`<!-- -------- the stored fields -------- -->`), and XML
forbids `--` anywhere inside a comment. It is the same shape of mistake as the Phase 3.4 javadoc deletion:
harmless-looking decoration in a comment, caught only because the build was actually run. The separator was
replaced with plain text.

---

## 14. DATA FLOW FOR THIS PHASE

```
MapMarkerController.renderAll(stores)
  └─ for each store: create the Marker, setRelatedObject(store.id),
                     setOnMarkerClickListener(→ read the id back, hand it over)

User taps a marker
  └─ Marker.onSingleTapConfirmed → hitTest passes → the lambda
        └─ MapFragment.openStoreDetailSheet(storeId)      [already showing? return]
              └─ StoreDetailBottomSheet.newInstance(storeId).show(...)
                    │  the id travels in the fragment ARGUMENTS, not a field
                    ▼
              onViewCreated → loading state
                    └─ StoreRepository.findById(storeId)          diskIO → Room → main thread
                          ├─ null  → Toast "That store no longer exists." → dismiss
                          └─ found → bindStore()  (chip + the five fields)
                                ├─ PENDING   → showContent()
                                └─ COMPLETED → CompletionRepository.findForStore(storeId)
                                      ├─ null    → showContent()   (section stays hidden)
                                      └─ found   → bindCompletion() → showContent()
```

**The sheet never writes.** It performs two reads and shows what it found. Room remains the single source of
truth (AD-4), and every write in the app still goes through `StoreRepository`.

---

## 15. MANUAL TEST PROCEDURE

### 15.0 Already verified automatically (2026-09-25)

`./gradlew :app:testDebugUnitTest :app:assembleDebug` was run on this machine with Gradle 9.4.1 / JDK 21
and returned **`BUILD SUCCESSFUL in 46s`** (41 tasks: 20 executed, 21 up-to-date).

| Test class | Result |
| --- | --- |
| **`DateTimeHelperTest`** (new) | **16 tests, 0 failures, 0 errors** |
| `LocationFixTest` | 20 tests, 0 failures |
| `StoreValidatorTest` | 19 tests, 0 failures |
| `GeoConstantsTest` | 17 tests, 0 failures |
| `MapUserAgentTest` | 11 tests, 0 failures |
| `AppSettingEntityTest` | 10 tests, 0 failures |
| `MapMarkerControllerTest` | 5 tests, 0 failures |
| `ExampleUnitTest` (template, Phase 10.2) | 1 test, 0 failures |
| **TOTAL** | **99 tests, 0 failures, 0 errors, 0 skipped** |

| Other evidence | Result |
| --- | --- |
| `:app:compileDebugJavaWithJavac` | ran — the sheet, the helper and the tap wiring all compile |
| `:app:mergeDebugResources` + `:app:processDebugResources` | ran — the new layout, drawable and strings resolve |
| `app-debug.apk` | **9,575,599 bytes** (was 9,562,469 — +13,130) |
| Merged manifest permissions | **unchanged** — the same four, plus androidx's auto-added one |
| `app/schemas/…/AppDatabase/` | still contains **only `1.json`** — no schema change |
| `:app:compileDebugKotlin` | **NO-SOURCE** — still zero Kotlin source files |

### 15.1 Tapping a marker opens that store

1. Go to the **Map** tab. Create two or three stores by long-pressing if you have none.
2. **Tap a marker.** The Store Details sheet must open.
3. Confirm the route number, store code, store name and area are **that marker's** store — not another
   one's. Tapping a different marker must show a different store.
4. Confirm **no osmdroid bubble** appears behind or on top of the sheet.

### 15.2 The status chip

5. A store you have never completed shows an **orange "Pending"** chip.
6. Complete a store by hand in the Database Inspector, so the completed state can be checked before
   Phase 5 exists:

```sql
INSERT INTO completion_history (storeId, completionTimestamp, completionDateKey, dailySequence)
VALUES (<a real store id>, <a timestamp>, '2026-09-25', 1);

UPDATE stores SET status = 'COMPLETED', completedAt = <the same timestamp> WHERE id = <that store id>;
```

7. Tap that marker. The chip must be **green and read "Completed"**.
8. **Switch the device to dark mode** and check both chips again — the text must stay clearly readable on
   both the orange and the green container.

### 15.3 The Completion section

9. On the completed store, a **Completion** section must appear showing the date, the time and the daily
   order (`#1` for the first store completed that day).
10. On a pending store, that section must **not** be visible at all.
11. Check the date and time against the timestamp you inserted. The date must be the local date — a store
    completed at 11:50 pm must not read as the next day.

### 15.4 Colour is never the only signal

12. Compare a pending and a completed store's chip side by side, and confirm you can tell them apart from
    the **words** alone, ignoring the colour.

### 15.5 A store that no longer exists

13. Open a details sheet for a **pending** store, then delete that store's row in the Database Inspector:
    `DELETE FROM stores WHERE id = <that pending store id>;`
14. Tap its marker (it may take a moment to disappear). The sheet must close and show
    **"That store no longer exists."** — not a blank panel, and not a crash.

### 15.6 Rotation

15. Open a details sheet and **rotate the device**. The sheet must still show the same store, fully
    populated — this is the check that the id travels in the arguments rather than a field.

### 15.7 Nothing else changed

16. Confirm markers still draw (orange pending, green completed) and still survive a restart.
17. Confirm the blue location dot and its accuracy circle still work.
18. Confirm long-press still opens the Add Store form, and that saving still draws a new marker.
19. Confirm the Dashboard, History and Settings tabs are unchanged.

**Report any failure with the step number and any Logcat text.**

---

## 16. EXPECTED RESULT

- Tapping a marker opens the Store Details sheet **for that store**.
- The sheet shows a status chip, route number, store code, store name, area and coordinates.
- A completed store additionally shows its completion date, time and daily order.
- A pending store shows no Completion section and **no way to mark itself complete**.
- A store deleted while its marker was on screen closes the sheet with a message.
- The sheet survives rotation.
- **No database code changed, no schema change, no new dependency or permission.**

---

## 17. COMMON ERRORS AND FIXES

| Symptom | Cause | Fix |
| --- | --- | --- |
| Tapping a marker does nothing | The click listener was not set, or `storeClickListener` is null | `MapFragment.onViewCreated` must call `markerController.setOnStoreClickListener(...)` |
| Tapping opens the **wrong** store | The id is captured from a loop variable instead of read from the marker | Read `marker.getRelatedObject()` at tap time (§13.3) |
| osmdroid's own bubble appears behind the sheet | The listener returned `false`, so the tap was not consumed | Return `true` once the tap has been handled |
| The first markers cannot be tapped, but later ones can | The listener was captured when the marker was created | Read the field at tap time, never capture it |
| `The string "--" is not permitted within comments` | A comment in an XML file contains `--` | Rewrite the separator as plain text (§13.7) |
| The sheet opens blank and stays blank | `findById` returned null — the store was deleted | Expected: the sheet closes with a message (§15.5) |
| The Completion section never appears on a completed store | There is no `completion_history` row for it | Insert one (§15.2 step 6). The section is gated on the row, not the status (A73) |
| The sheet is empty after rotating | The store was passed as a field rather than as an argument | The id must travel via `Bundle` arguments (§13.1) |
| `Cannot resolve symbol 'StoreDetailBottomSheet'` | The class is in `ui/store/`, not `ui/map/` | Check the import |
| The chip text is unreadable in one theme | Only one of the two colour pairs was added | Both `values/colors.xml` and `values-night/colors.xml` must define all four names |
| `DateTimeHelperTest` fails only on another machine | A test used `ZoneId.systemDefault()` | Every test must pin its zone (§13.4) |

---

## 18. ACCEPTANCE CHECKLIST

- [ ] Clean build succeeds.
- [x] **All 16 `DateTimeHelperTest` cases pass** (§15.0 — verified; whole suite 99 tests, 0 failures)
- [x] `app/schemas/` still contains only `1.json` (§15.0 — verified)
- [x] Merged manifest permissions unchanged (§15.0 — verified)
- [ ] Tapping a marker opens the details sheet (§15.1).
- [ ] Each marker opens **its own** store (§15.1).
- [ ] No osmdroid bubble appears (§15.1).
- [ ] A pending store shows an orange "Pending" chip (§15.2).
- [ ] A completed store shows a green "Completed" chip (§15.2).
- [ ] Both chips stay readable in dark mode (§15.2).
- [ ] A completed store shows date, time and daily order (§15.3).
- [ ] A pending store shows **no** Completion section (§15.3).
- [ ] **A pending store offers no completion action anywhere on the sheet** (§13.2).
- [ ] The status is readable from the chip's **text** alone (§15.4).
- [ ] A deleted store closes the sheet with a message (§15.5).
- [ ] The sheet survives rotation (§15.6).
- [ ] Markers, the location dot and long-press all still work (§15.7).
- [ ] Confirmed: still **no Kotlin source file** anywhere in the project.

---

## 19. UPDATED PROJECT STATE

**COMPLETED PHASES**
- ✅ 0.1 Requirements Review (Rev 5, FINAL) · ✅ 0.2 Architecture · ✅ 0.3 Screen Plan
- ✅ 1.1 Project Creation · ✅ 1.2 Theme and Base Navigation · ✅ 1.3 Dashboard Static UI
- ✅ 2.1 Room Database Foundation · ✅ 2.2 Store CRUD Operations · ✅ 2.3 Settings Storage
- ✅ 3.1 Map Display · ✅ 3.2 Current Location · ✅ 3.3 Manual Store Pin Creation
- ✅ 3.4 Load Saved Store Markers
- 🔄 **3.5 Store Details Bottom Sheet** — *awaiting your on-device test*

**CURRENT PHASE**
- Phase 3.5 — awaiting the on-device test

**CURRENT DATABASE VERSION**
- **1.** Unchanged. Three tables: `stores`, `completion_history`, `app_settings`.

**CURRENT FILES** (changed this phase)

```
app/src/main/java/com/cph/alpharoute/util/DateTimeHelper.java             (NEW)
app/src/test/java/com/cph/alpharoute/util/DateTimeHelperTest.java        (NEW - 16 tests)
app/src/main/res/layout/sheet_store_detail.xml                           (NEW)
app/src/main/res/drawable/bg_store_status_chip.xml                       (NEW)
app/src/main/java/com/cph/alpharoute/ui/store/StoreDetailBottomSheet.java (NEW)

app/src/main/java/com/cph/alpharoute/ui/map/MapMarkerController.java     (MODIFIED - tap seam)
app/src/main/java/com/cph/alpharoute/ui/map/MapFragment.java             (MODIFIED - opens the sheet)
app/src/main/res/values/strings.xml                                      (MODIFIED)
app/src/main/res/values/colors.xml                                       (MODIFIED)
app/src/main/res/values-night/colors.xml                                 (MODIFIED)

docs/PHASE_3.5_STORE_DETAILS.md                                          <- created this phase
docs/HANDOFF.md                                                          (MODIFIED)
```

**WORKING FEATURES** (verified)
- ✅ **99/99 unit tests pass** — 0 failures, 0 errors (verified 2026-09-25), including the 16 new `DateTimeHelperTest` cases
- ✅ **Build succeeds** — `:app:assembleDebug` → APK 9,575,599 bytes
- ✅ **The new layout, drawable and strings resolve** — resource merge and link both clean
- ✅ **Schema unchanged** — only `1.json` exists
- ✅ **Permissions unchanged** — the same four
- ✅ **Still zero Kotlin source files** — `compileDebugKotlin` = `NO-SOURCE`
- ⏳ **The sheet on a device** — tap, chip, completion section, not-found, rotation — awaiting your §15.1–15.7

**KNOWN ISSUES**
- None blocking.
- The sheet has **no Get Directions, Edit or Delete button yet** — see §13.2 for which phase adds each.
  This is a deliberate scope decision, not a defect.
- Markers still do not cluster at city zoom. Not a problem at the scale this app is used at (one route at a
  time) and not requested.
- `placeholder_history` and `placeholder_settings` remain, still marked for 6.2 and 2.3.
- Carried risks: OSM data quality in remote PH rural areas · openrouteservice free-tier quota ·
  **no offline map** (policy, risk R5) · date boundary (AD-7 + AD-11).
- The project is **not under version control**. Still the strongest recommendation in the project.
- Trademark note: "Alfamart" is a third-party brand; fine for a private solo tool (D-5).

**PENDING PHASES** (not implemented)
- **3.6 Edit and delete store** — next
- 3.7 Home pin
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

Phase 3.5 is complete. **12 files touched: 6 created, 6 modified. Nothing removed.**

**A marker now means something.** Tapping one opens the store it stands for, read fresh from Room, with the
completion details filled in when there is a completion record to fill them from. The tap is resolved from
the marker's own store-id association — put there in Phase 3.4 for exactly this — so a tap can only ever
open the store that was tapped.

**Two things are worth calling out:**

1. **Three buttons the screen plan calls for are deliberately absent, and that is the phase being
   disciplined rather than incomplete.** Get Directions belongs to Phase 4.2 (it opens Route Preview, which
   needs a routing account that does not exist yet), and Edit and Delete belong to Phase 3.6. Drawing them
   now would mean three controls that accept a tap and ignore it — which reads as a broken app, and which
   rule 8 exists to prevent. §13.2 records the decision and which phase adds each.

2. **The one thing this sheet must never gain is a completion action.** A pending store offers no way to be
   marked complete here, or anywhere else in the UI. Completion stays reachable only through the arrival
   flow and an explicit YES. A button on this sheet would let a store be counted from anywhere in the
   country — the single thing the whole design exists to prevent.

**And a small one the build caught:** an XML comment used a row of dashes as a separator, and XML forbids
`--` inside a comment. It failed the first resource parse, which is the argument for running the build
before reporting a phase complete rather than after.

**Please run §15 and report** — especially §15.1 (the right store opens), §15.2 (the chip, in **both**
light and dark mode), §15.3 (the Completion section appears only for a completed store) and §15.6 (the
sheet survives rotation).

**Next phase on your instruction: Phase 3.6 — Edit and Delete Store.** It reuses `AddEditStoreBottomSheet`
in edit mode, adds the confirmation step to Delete, and surfaces `WriteOutcome.PROTECTED_BY_HISTORY` as
"This store has completion history and cannot be deleted." It will also add the **Edit** and **Delete**
buttons to this sheet, in the action row the layout already reserves.

**Phase complete. Do not proceed to the next phase until all acceptance criteria pass.**

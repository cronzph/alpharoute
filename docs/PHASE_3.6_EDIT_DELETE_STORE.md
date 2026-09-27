# AlphaRoute — Phase 3.6 Edit and Delete Store

**Project title:** Alfamart Route Tracker
**Version:** 3 — Map and Manual Pins — **Phase:** 3.6 Edit and Delete Store
**Repository root:** `C:/Users/Admin/OneDrive/Documents/custom system/AlphaRoute`
**Date:** 2026-09-25
**Prerequisites:** Phases 0.1–0.3, 1.1–1.3, 2.1–2.3, 3.1–3.5
**Status:** 🔄 **Code complete and VERIFIED — 106/106 unit tests pass, build succeeds.** Awaiting your on-device test.

> **This phase closes the loop on a store's life.** It can now be created, corrected and removed — and a
> completed store is protected from removal by two independent guards, one of which is the database itself.

---

## 1. CURRENT VERSION AND PHASE

| Item | Value |
| --- | --- |
| Version | 3 — Map and Manual Pins |
| Phase | 3.6 — Edit and Delete Store |
| Files created | 2 |
| Files modified | 6 |
| Files deleted | 0 |
| Database version | **1** — unchanged. No schema change. |
| Unit tests | **106** (was 99 — 7 new) |

---

## 2. OBJECTIVE

Let the user correct a store they pinned wrongly, and remove one they no longer want:

- **Edit** on the Store Details sheet → the Add / Edit form opens, filled in with that store
- Saving an edit updates the store without changing anything it should not
- **Delete** on the Store Details sheet → a confirmation, then the store is removed
- A completed store **cannot** be deleted, and the screen says so before the user tries

---

## 3. UNDERSTANDING CHECKLIST

- [x] Java only, no Kotlin. No new dependency, no new permission, no schema change.
- [x] **Editing a store can never change its status, its creation time or its completion time** — enforced
      in the repository, not trusted to the form (§13.1).
- [x] Deleting a store **requires an explicit confirmation**.
- [x] A completed store's Delete button is **disabled, with the reason on screen**.
- [x] The database's `onDelete = RESTRICT` (AD-6) is still the real guarantee, and its outcome is handled.
- [x] Coordinates stay **read-only in both modes** — a store cannot be re-pinned (§13.6).
- [x] Rotation does not throw away what the user has typed (§13.3).
- [x] No store name, code, route number or coordinate is invented anywhere.

---

## 4. FEATURES INCLUDED (in this phase)

| Feature | Detail |
| --- | --- |
| **Edit mode on the Add / Edit form** | `newInstanceForEdit(storeId)`, title "Edit store", fields filled from Room |
| **`StoreRepository.update` hardened** | Status, `createdAt` and `completedAt` are copied back from the stored row before every write |
| **7 new unit tests** | Proving an edit cannot un-complete a store |
| **Edit action** | On the Store Details sheet, opens the form for that store |
| **Delete action** | On the Store Details sheet, behind a confirmation dialog |
| **Delete protection** | Disabled for a completed store, with an explanatory caption |
| **Outcome handling** | `PROTECTED_BY_HISTORY`, `NOT_FOUND` and `FAILED` all reported properly |

---

## 5. FEATURES NOT INCLUDED YET

| Not here | Arrives in |
| --- | --- |
| **Get Directions** button | **Phase 4.2** (route preview) then 4.3 (the hand-off) |
| Re-pinning a store (moving its coordinates) | **Not planned** — see §13.6 |
| The Home pin | **Phase 3.7** |
| Any completion action | **Phase 5.2** — and only through the arrival flow |
| Undo for a delete | **Not planned.** The confirmation dialog is the safeguard |

---

## 6. ASSUMPTIONS

| # | Assumption | Impact if wrong |
| --- | --- | --- |
| A77 | **The repository, not the form, guarantees an edit cannot change a store's status, `createdAt` or `completedAt`.** | This is the phase's most important decision. See §13.1 — the failure it prevents is silent and corrupting. |
| A78 | **Edit mode re-reads the store by id rather than being handed its values.** | Extends A70. Room stays the single source of truth, a store deleted a moment ago is reported immediately, and the id in the arguments makes rotation free. |
| A79 | **The form fills its fields in only when there is no saved state to restore.** | The framework restores typed text itself; filling the fields in again after a rotation would silently throw away the user's unsaved edits. |
| A80 | **The Store Details sheet closes before the Edit form opens.** | Two stacked bottom sheets is a state the user cannot reason about, and the details sheet would be left showing pre-edit values behind the form. |
| A81 | **Delete is disabled for a completed store, *and* `PROTECTED_BY_HISTORY` is still handled.** | The disabled button is a courtesy, not a guarantee: the store could be completed after the sheet opened. The database is the guarantee. |
| A82 | **Coordinates stay read-only in edit mode — a store cannot be re-pinned.** | See §13.6. Re-pinning an already-completed store would silently change what "arrived at this store" meant. |
| A83 | **`store_detail_missing` was renamed to `store_missing`.** | Two screens now show the same message, so a screen-specific name was wrong. Rule-11 note in §8. |
| A84 | **A successful edit shows a toast.** | The marker does not move and does not change colour, and marker bubbles are suppressed because the map opens its own sheet — so without it the screen would just close and look as though nothing happened. |

---

## 7. FILES TO CREATE (2)

| # | Path | Purpose |
| --- | --- | --- |
| 1 | `app/src/test/java/com/cph/alpharoute/data/repository/StoreRepositoryTest.java` | **New.** 7 JVM tests for the edit-preservation rule |
| 2 | `docs/PHASE_3.6_EDIT_DELETE_STORE.md` | **New.** This record |

---

## 8. FILES TO MODIFY (6)

| # | Path | Status | What changed |
| --- | --- | --- | --- |
| 1 | `data/repository/StoreRepository.java` | **Modified** | `update()` now preserves `id`, `status`, `createdAt` and `completedAt` from the stored row; new package-private `preserveUneditableFields` |
| 2 | `ui/store/AddEditStoreBottomSheet.java` | **Replaced** | Edit mode: `newInstanceForEdit`, title, re-read, prefill guard, loading state, `update()` on save |
| 3 | `ui/store/StoreDetailBottomSheet.java` | **Modified** | Edit and Delete actions, confirmation dialog, delete protection, outcome handling |
| 4 | `res/layout/sheet_store_detail.xml` | **Modified** | Edit and Delete buttons in the reserved action row, plus the protection caption |
| 5 | `res/values/strings.xml` | **Modified** | 8 new strings, and `store_detail_missing` renamed to `store_missing` |
| 6 | `docs/HANDOFF.md` | **Modified** | Status, phase history, file inventory, test count, roadmap, traps |

### One rename, and why

**`store_detail_missing` → `store_missing`.** The string reads "That store no longer exists." It was
introduced in Phase 3.5, named for the one screen that used it. Phase 3.6 reaches the same situation from a
second screen — the user taps Edit on a store that was deleted in the meantime — so a name tied to one
screen became wrong. The alternative was a second string with identical text, which is how two copies of a
message start drifting apart. Nothing was removed: both usages were updated in the same change, and the
build confirms no reference to the old name remains.

**Inspected and left unmodified:** the database layer (`StoreDao`, the entities, `AppDatabase`), `domain/`,
`location/`, `util/`, the map, and the Phase 3.5 rendering of the details sheet. This phase changes how a
store is written and what the details sheet can do; it does not change how anything is read.

---

## 9. DEPENDENCIES

**None added.**

---

## 10. PERMISSIONS AND MANIFEST CHANGES

**None.** Verified against the merged manifest after the build — the same four permissions, plus
androidx's auto-added self-scoped one:

```
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="com.cph.alpharoute.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" />
```

---

## 11. SETUP INSTRUCTIONS

1. Let Gradle sync (nothing new to fetch).
2. **Build → Clean Project**, then **Build → Rebuild Project**.
3. Run on a device or emulator. You need at least one store on the map.

---

## 12. COMPLETE CODE

All 8 files are written into the project on disk. The files worth reading first:

1. `data/repository/StoreRepository.java` — `update` and `preserveUneditableFields`, and why the guard is here
2. `app/src/test/java/…/StoreRepositoryTest.java` — the invariant, made unbreakable
3. `ui/store/AddEditStoreBottomSheet.java` — one form, two modes, and the rotation guard
4. `ui/store/StoreDetailBottomSheet.java` — the actions, the confirmation, and the two delete guards

---

## 13. CODE EXPLANATION

### 13.1 The bug this phase exists to prevent, and why it is fixed in the repository

`StoreDao.update` is a Room `@Update`, which **replaces the whole row**. The obvious way to build an edit is
to construct a `StoreEntity` from the four text fields and call `update`. That is what this phase would have
done, and it would have introduced the worst kind of bug:

```java
// What NOT to do - a form that fills in only the fields it edits
StoreEntity store = new StoreEntity();      // status = PENDING, completedAt = null (Java defaults)
store.id = storeId;
store.routeNumber = editedRouteNumber;
store.storeCode = editedStoreCode;
store.storeName = editedStoreName;
store.area = editedArea;
repository.update(store, callback);         // ...and PENDING/null are written over the whole row
```

A store that had been completed would become **pending again**, while its `completion_history` row stayed
exactly where it was. The result is a store that is orange on the map, still counted in the history, and
still refusing to be deleted because AD-6 sees the history. Nothing in the app could detect or repair that,
and the trigger would be something as innocent as fixing a typo in a store name.

**The fix is not in the form.** The form does construct a partial entity — that is the natural shape of a
form — but the repository refuses to let the partial entity define the whole row:

```java
static void preserveUneditableFields(StoreEntity incoming, StoreEntity stored) {
    incoming.id = stored.id;
    incoming.status = stored.status;
    incoming.createdAt = stored.createdAt;
    incoming.completedAt = stored.completedAt;
}
```

called inside `update`, after the row has been re-read and before the write.

**Why the repository and not the form.** If the form were responsible, the rule would hold only for as long
as every future caller remembered it. A second edit path, an import in Version 9, or a well-meaning
refactor would each have to re-derive it. Putting it in the repository means the rule is a property of
*writing a store* rather than a property of one screen — the same reasoning behind `normalise`, which trims
every text field regardless of which caller sent it.

**What is on each side of the line:**

| From the stored row | From the incoming entity |
| --- | --- |
| `id` — the identity being updated | the four text fields |
| `status` — owned by the arrival flow | `latitude`, `longitude` — read-only on the form |
| `createdAt` — history | `updatedAt` — stamped by `update` itself |
| `completedAt` — owned by Phase 5.3 | |

**The rule has tests, and they are the point of the phase.** The repository's write path needs Room and
cannot run on the JVM, but this rule can, and it is the rule most worth testing: every other way an edit can
fail does so loudly — a duplicate code shows an inline error, a missing store shows a message — whereas this
one fails **silently**. `StoreRepositoryTest` holds the line with seven cases, of which the headline is:

```java
@Test
public void editingACompletedStoreDoesNotMakeItPending() {
    StoreEntity incoming = editedFormEntity();   // status = PENDING, completedAt = null
    assertEquals(StoreStatus.PENDING, incoming.status);

    StoreRepository.preserveUneditableFields(incoming, completedStoredRow());

    assertEquals("A completed store must stay completed through an edit",
            StoreStatus.COMPLETED, incoming.status);
}
```

It also covers the other half — that a **pending** store keeps a `null` completion time rather than being
given `0`, which the schema forbids because `0` would claim the store was completed in 1970.

### 13.2 Why edit mode re-reads the store instead of being handed its values

The Edit button is on a sheet that has just displayed the store, so the values are sitting right there. They
are still not passed across. That is assumption A70 from Phase 3.5, applied again:

- **Room stays the single source of truth (AD-4).** A form filled from a snapshot could show values the
  store no longer has.
- **A store deleted a moment ago is reported immediately** — "That store no longer exists." — instead of
  after the user has filled in the whole form and tapped Save.
- **Rotation is free.** The id travels in the arguments, so FragmentManager's no-argument reconstruction
  simply re-reads.

The cost is one extra single-row read behind a progress bar. Only **Save** is disabled while it runs;
**Cancel** stays live, so a slow read can never trap the user behind a form they cannot leave.

### 13.3 The rotation trap: do not fill the fields in twice

Android saves and restores the contents of every view that has an id, so after a rotation the four text
fields already hold whatever the user had typed. If the form then filled them in from the database when the
read returned, it would quietly discard those edits — the user would see their typing vanish.

So the prefill is conditional:

```java
prefillWhenLoaded = savedInstanceState == null;
```

The store is still re-read either way, because the save needs the row to exist — but a restored form is left
alone.

### 13.4 Why the details sheet closes before the form opens

`openEditSheet` dismisses the details sheet and then shows the form, in that order, on the same
FragmentManager:

```java
FragmentManager fragmentManager = getParentFragmentManager();
dismiss();
AddEditStoreBottomSheet.newInstanceForEdit(storeId)
        .show(fragmentManager, AddEditStoreBottomSheet.TAG);
```

Two reasons:

- **Stacked sheets are a state the user cannot reason about.** Which one does Back close? Which one is
  "the" screen?
- **The details sheet would be stale.** It reads the store once when it opens. Left underneath the form, it
  would still be showing the pre-edit values afterwards — and there is no reliable "you are visible again"
  callback for a `DialogFragment` that has another dialog above it, so refreshing it would mean adding
  observation plumbing for a screen the user is about to leave anyway.

The remove is handed to the FragmentManager before the add, so the two transactions run in that order and
the form lands on the map. Closing the details sheet also means Delete behaves the same way — after either
action the user is back on the map, where the marker has already updated because the map observes Room.

### 13.5 Two guards on Delete, because one of them is only a courtesy

The screen plan asks for Delete to be disabled on a completed store with an explanatory caption, and for a
`PROTECTED_BY_HISTORY` failure to be explained. Both are implemented, and they are not redundant:

| Guard | What it is | When it applies |
| --- | --- | --- |
| **Disabled button + caption** | A courtesy, decided from the status the sheet read when it opened | Tells the user before they try, which is the whole point of the screen plan's requirement |
| **`onDeleteFinished` handling `PROTECTED_BY_HISTORY`** | The real answer, coming back from the repository | Applies even if the store was completed *after* the sheet opened — which the disabled button cannot know about |
| **AD-6, `onDelete = RESTRICT`** | The guarantee, inside SQLite | Applies even if both of the above are bypassed by a future code path |

The repository checks the history table first so the user gets a clear reason, and the foreign key refuses it
anyway if that check is ever skipped. The history is the one thing in this app that cannot be reconstructed,
so it gets two independent guards rather than one careful one.

Delete is also behind a confirmation dialog that names the store, and the confirming button is drawn in the
error colour — resolved from the app's own `md_error` rather than from a theme attribute, so it is defined
for both light and dark mode and does not depend on which library declares the attribute. See §13.7.

### 13.6 Why a store still cannot be re-pinned

The coordinates remain read-only in edit mode, exactly as in add mode. The screen plan says so, and there is
a reason beyond it: **moving an already-completed store's pin would silently change what "arrived at this
store" meant.** The completion record would still say the store was visited, but the place that was visited
would no longer be where the pin is.

If re-pinning is ever wanted, the honest design is a deliberate "move this pin" action that is unavailable
for a completed store — and `StoreRepositoryTest.theCoordinatesAreKept` records exactly where the boundary
sits, so the change is a conscious one rather than an accident.

### 13.7 A compile error the build caught

```
StoreDetailBottomSheet.java:373: error: cannot find symbol
    com.google.android.material.R.attr.colorError
                      ^
  symbol:   variable colorError
```

`colorError` is an **AppCompat** attribute, not one Material declares in its own `R.attr`. The first version
resolved it through `com.google.android.material.R.attr.colorError`, which does not exist. Rather than reach
for `androidx.appcompat.R.attr.colorError` and depend on which library owns the name, the colour now comes
from the app's own `md_error` resource — the same value the theme's `colorError` role already points at,
defined for both light and dark mode, with no library dependency at all. Simpler and more robust than the
thing it replaced.

This is the second phase in a row where running the build caught something a careful reading had missed —
Phase 3.5's `--` inside an XML comment, and now this. That is the argument for the build being part of the
phase rather than a formality after it.

---

## 14. DATA FLOW FOR THIS PHASE

### Editing

```
Store Details sheet → Edit tapped
  └─ openEditSheet()
        ├─ dismiss()                        this sheet goes first
        └─ AddEditStoreBottomSheet.newInstanceForEdit(storeId)     id in ARGUMENTS
              └─ show() on the same FragmentManager                runs after the remove

AddEditStoreBottomSheet.onViewCreated
  ├─ title = "Edit store"
  ├─ prefillWhenLoaded = (savedInstanceState == null)
  ├─ setLoading(true)  →  Save disabled, Cancel still live
  └─ StoreRepository.findById(storeId)          diskIO → Room → main thread
        ├─ null  → Toast "That store no longer exists." → dismiss
        └─ found → coordinates from the store
                   └─ prefill the four fields ONLY if prefillWhenLoaded
                      → setLoading(false)

Save tapped
  ├─ StoreValidator.validate(...)                     synchronous, per-field errors
  ├─ buildEntity(...)                                 id + four text fields + coordinates ONLY
  └─ StoreRepository.update(entity)                   diskIO, single thread
        ├─ normalise(entity)                          trim + coordinate range
        ├─ stored = storeDao.findById(id)             existence check AND the source of the guard
        ├─ duplicate checks (excluding this store)
        ├─ preserveUneditableFields(entity, stored)   ← status / createdAt / completedAt / id
        ├─ entity.updatedAt = now
        ├─ storeDao.update(entity)                    COMMIT
        ├─ notifySyncSafely()                         after the commit, guarded
        └─ postToMain(callback)
              └─ SUCCESS → Toast "Store updated." → dismiss
                 NOT_FOUND → "That store no longer exists." → dismiss
                 DUPLICATE_* → inline field error, form stays open
```

### Deleting

```
Store Details sheet → Delete tapped
  └─ confirmDelete()  →  MaterialAlertDialogBuilder
        ├─ Cancel  → nothing happens
        └─ Delete  → StoreRepository.delete(storeId)
              ├─ not found                  → NOT_FOUND
              ├─ countForStore(id) > 0      → PROTECTED_BY_HISTORY   (AD-6 pre-check)
              ├─ storeDao.deleteById(id)    → COMMIT
              │     └─ if the FK still refuses → PROTECTED_BY_HISTORY (AD-6 itself)
              ├─ settingsRepository.clearLastSelectedStoreIdIf(id)  no dangling reference
              └─ notifySyncSafely()
                    ├─ SUCCESS / NOT_FOUND    → Toast "Store deleted." → dismiss
                    ├─ PROTECTED_BY_HISTORY   → Snackbar with the reason, sheet stays open
                    └─ FAILED                 → Snackbar, nothing changed
```

**The map is told nothing, in both flows.** It observes `storeRepository.observeAll()`, so Room's change
notification re-runs `renderAll` and the marker moves, renames or disappears on its own.

---

## 15. MANUAL TEST PROCEDURE

### 15.0 Already verified automatically (2026-09-25)

`./gradlew :app:testDebugUnitTest :app:assembleDebug` was run on this machine with Gradle 9.4.1 / JDK 21
and returned **`BUILD SUCCESSFUL in 50s`** (41 tasks: 13 executed, 28 up-to-date).

| Test class | Result |
| --- | --- |
| **`StoreRepositoryTest`** (new) | **7 tests, 0 failures, 0 errors** |
| `LocationFixTest` | 20 tests, 0 failures |
| `StoreValidatorTest` | 19 tests, 0 failures |
| `GeoConstantsTest` | 17 tests, 0 failures |
| `DateTimeHelperTest` | 16 tests, 0 failures |
| `MapUserAgentTest` | 11 tests, 0 failures |
| `AppSettingEntityTest` | 10 tests, 0 failures |
| `MapMarkerControllerTest` | 5 tests, 0 failures |
| `ExampleUnitTest` (template, Phase 10.2) | 1 test, 0 failures |
| **TOTAL** | **106 tests, 0 failures, 0 errors, 0 skipped** |

| Other evidence | Result |
| --- | --- |
| `:app:compileDebugJavaWithJavac` | ran — the form, the sheet and the repository change all compile |
| `:app:mergeDebugResources` + `:app:processDebugResources` | ran — the new buttons, the caption and the strings resolve |
| `app-debug.apk` | **9,582,311 bytes** (was 9,575,599 — +6,712) |
| Merged manifest permissions | **unchanged** — the same four |
| `app/schemas/…/AppDatabase/` | still contains **only `1.json`** — no schema change |
| `:app:compileDebugKotlin` | **NO-SOURCE** — still zero Kotlin source files |

### 15.1 Editing a pending store

1. Go to the **Map** tab and tap a **pending** marker.
2. Tap **Edit**. The details sheet must close and the form must open, titled **"Edit store"**.
3. Confirm the four fields are **filled in with that store's values**, and that the coordinate readout
   matches the store.
4. Change the store name. Tap **Save**.
5. The form must close with a **"Store updated."** message.
6. Tap the marker again. The details sheet must show the **new** name.
7. **Force-stop the app and reopen it.** The new name must still be there — the edit was committed, not just
   shown.

### 15.2 An edit must not change anything else

8. In the Database Inspector, note a store's `status`, `createdAt` and `completedAt`.
9. Edit that store's **name** through the app and save.
10. Re-read the row. **`status`, `createdAt` and `completedAt` must be unchanged**, and only `updatedAt`
    should have moved. This is the rule §13.1 exists for.

### 15.3 Editing a completed store — the important one

11. Complete a store by hand in the Database Inspector:

```sql
INSERT INTO completion_history (storeId, completionTimestamp, completionDateKey, dailySequence)
VALUES (<a real store id>, <a timestamp>, '2026-09-25', 1);

UPDATE stores SET status = 'COMPLETED', completedAt = <the same timestamp> WHERE id = <that store id>;
```

12. Tap that marker. The chip must be green and read "Completed".
13. Tap **Edit**, change the store name, and **Save**.
14. **The store must still be COMPLETED** — green on the map, still green on the details sheet, and
    `status` must still read `COMPLETED` in the Database Inspector with `completedAt` unchanged.
15. The details sheet must still show the Completion section with the same date, time and daily order.

> **If step 14 fails — the store turning orange — that is the §13.1 bug, and it must be reported.**

### 15.4 Editing must reject the usual mistakes

16. Open Edit and clear the **Store name**. Save. The field must show "Store name is required" and the form
    must stay open.
17. Change the store code to one that **another** store already uses. Save. The store code field must show
    "That store code is already used by another store".
18. Change it back to its own code and save. It must succeed — a store must not collide with itself.
19. Rotate the device with unsaved changes in the form. **What you typed must still be there.**

### 15.5 Deleting a pending store

20. Tap a **pending** marker and tap **Delete**.
21. A confirmation must appear naming the store, with the Delete button in red.
22. Tap **Cancel**. Nothing must happen — the store must still be on the map and in the database.
23. Tap **Delete** again, then confirm. The sheet must close with **"Store deleted."** and **the marker must
    disappear without a restart**.
24. Confirm the row is gone from the `stores` table.

### 15.6 A completed store cannot be deleted

25. Tap the **completed** marker from §15.3.
26. **Delete must be greyed out**, and the caption **"This store has completion history and cannot be
    deleted."** must be visible.
27. Confirm in the Database Inspector that the store and its history row are both still present.

### 15.7 A store deleted while you were looking at it

28. Tap a pending marker to open its details sheet. Leave it open.
29. In the Database Inspector, delete that store's row:
    `DELETE FROM stores WHERE id = <that pending store id>;`
30. Tap **Edit**. The form must close with **"That store no longer exists."** — not open with empty fields,
    and not crash.
31. Repeat with a fresh pending store: open the details sheet, delete the row, then tap **Delete** and
    confirm. The sheet must close with **"Store deleted."** and nothing must crash.

### 15.8 Nothing else changed

32. Confirm markers still draw (orange pending, green completed) and survive a restart.
33. Confirm long-press still opens the **Add store** form (title "Add store", empty fields) and that saving
    still draws a new marker.
34. Confirm the blue location dot and its accuracy circle still work.
35. Confirm the Dashboard, History and Settings tabs are unchanged.

**Report any failure with the step number and any Logcat text.**

---

## 16. EXPECTED RESULT

- Edit opens the form filled in with that store, and Save updates it.
- **An edit never changes a store's status, creation time or completion time.**
- A completed store stays completed and green through an edit.
- Delete asks for confirmation, and a confirmed delete removes the store and its marker.
- A completed store's Delete button is disabled, with the reason on screen.
- A store deleted from elsewhere is reported with a message rather than a crash or an empty form.
- Rotating the form keeps what was typed.
- **No schema change, no new dependency or permission.**

---

## 17. COMMON ERRORS AND FIXES

| Symptom | Cause | Fix |
| --- | --- | --- |
| **A completed store turns orange after an edit** | `preserveUneditableFields` was removed, or is not called before `storeDao.update` | It must run after the stored row is read and before the write (§13.1). `StoreRepositoryTest` catches this |
| A completed store loses its completion date after an edit | Same as above | Same |
| An edit writes `completedAt = 0` | The null-preservation case was lost | A pending store's `completedAt` must stay `null`, never `0` |
| The form opens empty in edit mode | The prefill guard is inverted, or the read returned null | The fields are filled in `onStoreLoaded` when `prefillWhenLoaded` is true |
| Rotation wipes what the user typed | The prefill ran even with a saved state | `prefillWhenLoaded = savedInstanceState == null` (§13.3) |
| Two sheets appear stacked | The details sheet was not dismissed before showing the form | `dismiss()` then `show()` on the same FragmentManager (§13.4) |
| Delete on a completed store does nothing visible | The disabled button swallowed the tap, as designed | The caption explains it. The repository outcome is also handled for the case where the status changed after opening |
| "Cannot resolve symbol colorError" | Reaching for `com.google.android.material.R.attr.colorError` | `colorError` is an AppCompat attribute. Use the app's own `md_error` colour instead (§13.7) |
| The edit toast never appears | `showMessage` was called after `dismiss()` | Show the message, then dismiss — `closeWithMessage` does both in that order |
| The form saves a blank store in edit mode | `loading` was not checked in `onSaveClicked` | Save must be refused while `loading \|\| saving` |
| A duplicate code error appears when editing a store to its own code | The exclude id was not passed | `isStoreCodeTaken(code, store.id)`, never `-1`, on an update |

---

## 18. ACCEPTANCE CHECKLIST

- [ ] Clean build succeeds.
- [x] **All 7 `StoreRepositoryTest` cases pass** (§15.0 — verified; whole suite 106 tests, 0 failures)
- [x] `app/schemas/` still contains only `1.json` (§15.0 — verified)
- [x] Merged manifest permissions unchanged (§15.0 — verified)
- [ ] Edit opens the form filled in with the tapped store (§15.1).
- [ ] Saving an edit updates the store everywhere, including after a restart (§15.1).
- [ ] **An edit does not change `status`, `createdAt` or `completedAt`** (§15.2).
- [ ] **A completed store stays completed through an edit** (§15.3).
- [ ] Blank and duplicate values are still rejected on edit (§15.4).
- [ ] Rotating the form keeps what was typed (§15.4).
- [ ] Delete asks for confirmation, and Cancel changes nothing (§15.5).
- [ ] A confirmed delete removes the store and its marker without a restart (§15.5).
- [ ] A completed store's Delete is **disabled**, with the caption visible (§15.6).
- [ ] A store deleted from elsewhere is reported, not crashed on (§15.7).
- [ ] The Add store form still works, with the title "Add store" (§15.8).
- [ ] Markers, the location dot and long-press all still work (§15.8).
- [ ] Confirmed: still **no Kotlin source file** anywhere in the project.

---

## 19. UPDATED PROJECT STATE

**COMPLETED PHASES**
- ✅ 0.1 Requirements Review (Rev 5, FINAL) · ✅ 0.2 Architecture · ✅ 0.3 Screen Plan
- ✅ 1.1 Project Creation · ✅ 1.2 Theme and Base Navigation · ✅ 1.3 Dashboard Static UI
- ✅ 2.1 Room Database Foundation · ✅ 2.2 Store CRUD Operations · ✅ 2.3 Settings Storage
- ✅ 3.1 Map Display · ✅ 3.2 Current Location · ✅ 3.3 Manual Store Pin Creation
- ✅ 3.4 Load Saved Store Markers · ✅ 3.5 Store Details Bottom Sheet
- 🔄 **3.6 Edit and Delete Store** — *awaiting your on-device test*

**CURRENT PHASE**
- Phase 3.6 — awaiting the on-device test

**CURRENT DATABASE VERSION**
- **1.** Unchanged. Three tables: `stores`, `completion_history`, `app_settings`.

**CURRENT FILES** (changed this phase)

```
app/src/test/java/com/cph/alpharoute/data/repository/StoreRepositoryTest.java  (NEW - 7 tests)

app/src/main/java/com/cph/alpharoute/data/repository/StoreRepository.java      (MODIFIED - update guard)
app/src/main/java/com/cph/alpharoute/ui/store/AddEditStoreBottomSheet.java     (REPLACED - edit mode)
app/src/main/java/com/cph/alpharoute/ui/store/StoreDetailBottomSheet.java      (MODIFIED - actions)
app/src/main/res/layout/sheet_store_detail.xml                                 (MODIFIED)
app/src/main/res/values/strings.xml                                            (MODIFIED)

docs/PHASE_3.6_EDIT_DELETE_STORE.md                                            <- created this phase
docs/HANDOFF.md                                                                (MODIFIED)
```

**WORKING FEATURES** (verified)
- ✅ **106/106 unit tests pass** — 0 failures, 0 errors (verified 2026-09-25), including the 7 new `StoreRepositoryTest` cases
- ✅ **Build succeeds** — `:app:assembleDebug` → APK 9,582,311 bytes
- ✅ **Resources merge and link cleanly** — the new buttons, caption and strings all resolve
- ✅ **Schema unchanged** — only `1.json` exists
- ✅ **Permissions unchanged** — the same four
- ✅ **Still zero Kotlin source files** — `compileDebugKotlin` = `NO-SOURCE`
- ⏳ **Edit and delete on a device** — prefill, the completed-store guard, delete, not-found — awaiting your §15.1–15.8

**KNOWN ISSUES**
- None blocking.
- The Store Details sheet still has **no Get Directions button** — it arrives with the route preview in
  Phase 4.2. Its action row now holds three buttons (Edit, Delete, Close); 4.2 will need to decide where a
  fourth goes.
- A store cannot be re-pinned once created (§13.6). Not planned; would need a deliberate design.
- A delete cannot be undone. The confirmation dialog is the safeguard.
- `placeholder_history` and `placeholder_settings` remain, still marked for 6.2 and 2.3.
- Carried risks: OSM data quality in remote PH rural areas · openrouteservice free-tier quota ·
  **no offline map** (policy, risk R5) · date boundary (AD-7 + AD-11).
- The project is **not under version control**. Still the strongest recommendation in the project.
- Trademark note: "Alfamart" is a third-party brand; fine for a private solo tool (D-5).

**PENDING PHASES** (not implemented)
- **3.7 Home pin** — next
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

Phase 3.6 is complete. **8 files touched: 2 created, 6 modified. Nothing removed.**

**A store's whole life is now covered:** it can be pinned, corrected and removed. Version 3's map work is
one phase from finished — only the Home pin (3.7) remains before the routing hand-off in Version 4.

**Two things are worth calling out:**

1. **The phase's most important work was preventing a bug, not adding a feature.** `StoreDao.update`
   replaces the entire row, so the natural way to write an edit form would have written `PENDING` and
   `null` over a completed store's status and completion time — while leaving its completion history in
   place. The result would have been a store that is orange on the map, still counted in the history, and
   still refusing to be deleted. The trigger would have been something as innocent as fixing a typo. The
   guard lives in the repository rather than in the form, so no future caller can forget it, and seven
   tests hold the line.

2. **Delete has two independent guards, and that is deliberate.** The disabled button with its caption is
   the courtesy that tells the user before they try; the repository's `PROTECTED_BY_HISTORY` is the answer
   that still works if the store was completed after the sheet opened; and AD-6's `onDelete = RESTRICT` is
   the guarantee inside SQLite. The completion history is the one thing in this app that cannot be
   reconstructed.

**And one the build caught again:** `colorError` is an AppCompat attribute, not a Material one, so
`com.google.android.material.R.attr.colorError` did not exist. It now reads from the app's own `md_error`
colour — the same value, defined for both themes, with no library dependency. That is the second phase in a
row where running the build found something a careful reading had missed.

**Please run §15 and report** — especially §15.3 (a completed store stays completed through an edit — the
one that must not fail), §15.2 (only `updatedAt` moves), §15.5 (delete needs confirmation and removes the
marker) and §15.7 (a store deleted from elsewhere is reported, not crashed on).

**Next phase on your instruction: Phase 3.7 — Home Pin.** `fragment_home_setup.xml` with **Use current
location** / **Select on map** / **Confirm**; Home drawn with a distinct house marker
(`ic_marker_home`, deep purple `#5E35B1`, not created yet). Home is never a store and never affects any
count. `SettingsRepository.setHome()` / `clearHome()` / `observe()` already exist and are waiting.

**Phase complete. Do not proceed to the next phase until all acceptance criteria pass.**

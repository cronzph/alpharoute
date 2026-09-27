# PHASE 18 — SAVED ROUTE PLANS

> The order the user decided, kept. `route_plans` and `route_plan_stops` have existed since schema
> version 3, added ahead of the feature; this is what finally uses them.
>
> Read alongside `HANDOFF.md`, which is the source of truth for the whole project.

---

## 1. CURRENT VERSION AND PHASE

| | |
| --- | --- |
| **Version** | 18 — Saved route plans |
| **Phase** | 18.1 — full CRUD on named plans, with drag-to-reorder on the route screen |
| **Build baseline** | AGP 9.2.1 · Gradle 9.4.1 · compileSdk 36 · minSdk 24 · Java 11 |
| **Database** | **version 3 — UNCHANGED.** No new table, no new column, no new migration |
| **Tests** | 563 total, 0 failures (546 before this phase) |
| **Cost** | $0 — no new service, no new dependency |

---

## 2. OBJECTIVE

Let the user **decide the order themselves and keep it.**

Before this phase the route screen showed one thing: the order a planner worked out. Asked twice it could
answer differently, and there was nowhere to put "this is the order I actually want". The user asked for
this in their own words:

> "manual nalang sa plan route … basta sya na maglagay ng sunod sunod pagkaka sunod sunod ng route"
> then, on clarification: "sa route pedeng madameng plan ganun like default plan plan b ganun pede akong
> mag dagdag delete full crud"

So: **the order is data the user owns**, there can be **many named plans**, and they can be **created,
renamed and deleted**.

---

## 3. UNDERSTANDING CHECKLIST

Before writing code, these were established as true:

- [x] `route_plans` and `route_plan_stops` already exist in **schema version 3**, and `MIGRATION_2_3`
      creates them. **No migration is needed for this phase.**
- [x] `RoutePlanDao` already has `observePlans`, `findPlan`, `findStops`, `countPlansInRound`,
      `insertPlan`, `insertStops`, `deletePlan`, `renamePlan`, `savePlan` (transactional) and
      `deletePlanOrThrow` (transactional). **Nothing called any of it.**
- [x] The unique index on `route_plan_stops` is `(planId, position)` — **not** `(planId, storeId)`, so a
      plan *can* list the same store twice and the merge must handle it.
- [x] `route_plan_stops.storeId` cascades on store delete (opposite of `completion_history`'s RESTRICT),
      so deleting a store silently removes it from any plan.
- [x] `RouteOrderPlanner` measures a **given** order without reordering it — `measure()`.
- [x] The route screen already owns a list of pending stores and the round number.
- [x] `ItemTouchHelper` is available (RecyclerView is already a dependency).

---

## 4. FEATURES INCLUDED (in this phase)

1. **Drag to reorder.** Long-press a stop, or press its grip, and move it up or down the route.
2. **Live renumbering.** The number is the row's position, so `#1 … #5` is right the moment a row lands.
3. **Re-measure on drop.** Every leg distance is recalculated for the new order, with the same arithmetic
   the planner uses, so a hand-made route and a suggested one are comparable numbers.
4. **Plan picker.** A button naming the plan on screen; it opens a menu of every plan saved for this round,
   with the current one ticked.
5. **Create.** *New plan* gives a fresh suggestion with no name behind it; **Save** creates it, and the
   repository generates the name (`Plan 1`, `Plan 2`…).
6. **Update.** **Save** on an existing plan replaces its stops and totals in one transaction.
7. **Rename.** A dialog, prefilled, capped at 40 characters.
8. **Delete.** Confirmed, and the confirmation says what is *not* affected.
9. **Round-scoped.** A plan is stored against the round it was made for, so a round-1 plan is not offered
   for round 2.
10. **Dirty state.** Save is disabled until something has actually changed.
11. **Survives rotation.** The chosen plan is remembered; the order reloads from the database.

---

## 5. FEATURES NOT INCLUDED YET

| Not included | Why |
| --- | --- |
| **Sharing or exporting a plan** | Version 9's backup already covers the database; a per-plan export is a separate decision |
| **Reordering by typing a number** | Drag was chosen over it; typing adds duplicate handling for no gain |
| **Auto-applying a plan to the map as a polyline** | The map draws straight lines between stops; a road line is still the directions call |
| **Moving a stop between plans** | Select the plan, then drag — the same result with one control instead of two |
| **A plan history / undo of a delete** | Deleting a plan touches no store and no completion record, so the blast radius is small |
| **Unused `optimize()` service path removed** | Kept deliberately — see §17 |

---

## 6. ASSUMPTIONS

1. One user, one device (D-5). No conflict resolution, no concurrent editing.
2. A plan is a **list of store ids in an order**. It is not a snapshot of the stores themselves; a store
   renamed tomorrow shows its new name in a plan made today.
3. The round number is the unit of scope. Two plans for the same round are alternatives; a plan for another
   round is not offered at all.
4. Straight-line distances are the only distances available without a network call, and the screen already
   says so.
5. The user's order is never improved. Where a newly added store goes is the user's call; the end is the
   honest default.

---

## 7. FILES TO CREATE (5)

| File | What it is |
| --- | --- |
| `app/src/main/java/com/cph/alpharoute/domain/PlanMerge.java` | **PURE.** Saved order + pending stores → the order to drive |
| `app/src/main/java/com/cph/alpharoute/data/repository/PlanRepository.java` | Full CRUD, on `diskIO()`, results on the main thread |
| `app/src/test/java/com/cph/alpharoute/domain/PlanMergeTest.java` | 12 tests |
| `app/src/main/res/drawable/ic_drag_handle.xml` | The grip |
| `app/src/main/res/drawable/ic_arrow_drop_down.xml` | The caret on the plan button |

---

## 8. FILES TO MODIFY (8)

| File | Change |
| --- | --- |
| `data/db/RoutePlanDao.java` | `findPlansInRoundNow`, `deleteStops`, `updatePlanSummary`, `replaceStops` (transactional) |
| `domain/RouteOrderPlanner.java` | Extracted `measure()` — score a decided order without reordering it |
| `data/repository/RoutingRepository.java` | Added `measureRoute()` |
| `AlphaRouteApp.java` | Built and exposed `planRepository()` |
| `ui/route/OptimizedRouteFragment.java` | The plan bar, the menu, drag-to-reorder, the dirty state |
| `ui/route/OptimizedRouteAdapter.java` | `move()`, the drag listener, numbering from position, legs hidden while dragging |
| `res/layout/fragment_optimized_route.xml` | The plan button, Save, and the reorder hint |
| `res/layout/item_route_stop.xml` | The drag handle |
| `res/values/strings.xml` | 20 new strings |

---

## 9. DEPENDENCIES

**None added.** `ItemTouchHelper` ships with `androidx.recyclerview`, which the screen already used.
`PopupMenu` is `androidx.appcompat`. `MaterialAlertDialogBuilder` was already in use elsewhere.

---

## 10. PERMISSIONS AND MANIFEST CHANGES

**None.** No new permission, no manifest change, no new activity. This phase adds no network call and
touches no hardware.

---

## 11. SETUP INSTRUCTIONS

Nothing to set up. Install and open the app; the route screen has the new controls.

---

## 12. COMPLETE CODE

**The complete files are in the repository, not duplicated here.** They were written whole — no diffs, no
placeholders — and §7 and §8 name every one of them. Roughly 1,300 lines of Java and XML were added or
rewritten in this phase, and pasting them into this document would create a **second copy that can drift
from the first**; the copy in `app/src` is the one the compiler checks.

To read them:

```
app/src/main/java/com/cph/alpharoute/domain/PlanMerge.java
app/src/main/java/com/cph/alpharoute/data/repository/PlanRepository.java
app/src/main/java/com/cph/alpharoute/data/db/RoutePlanDao.java
app/src/main/java/com/cph/alpharoute/domain/RouteOrderPlanner.java
app/src/main/java/com/cph/alpharoute/ui/route/OptimizedRouteFragment.java
app/src/main/java/com/cph/alpharoute/ui/route/OptimizedRouteAdapter.java
app/src/main/res/layout/fragment_optimized_route.xml
app/src/main/res/layout/item_route_stop.xml
app/src/main/res/values/strings.xml
```

§13 explains the decisions in them, which is the part this document is for.

---

## 13. CODE EXPLANATION

### Why `PlanMerge` is its own class

Merging a saved order with what is pending has **three jobs, and all three fail silently**:

| If it goes wrong | What the user sees | What actually happened |
| --- | --- | --- |
| A completed store is left in | Nothing wrong | They are sent back to a shop they already did |
| A newly added store is dropped | Nothing wrong | They drive home believing the round is done |
| A store listed twice is not deduped | Nothing wrong | They drive to the same shop twice |

No exception, no log, no odd-looking number — just a wrong route. So it is a pure class with 12 tests
rather than six lines inside a Fragment, because a rule with no error signal has to be asserted.

### Why the number comes from the row position

`OptimizedRouteAdapter` renders `position + 1`, not a sequence carried in the data. A drag would otherwise
need every affected stop renumbered mid-gesture, and a list reading `#1, #2, #2` is worse than one reading
nothing. Position cannot be stale, because it *is* the order.

### Why the leg distances disappear while dragging

A leg describes the step **from the stop before it**. The moment a row is picked up, every leg under it
describes a journey that is no longer on screen. Showing a stale figure that looks current is exactly the
quiet wrong answer this project avoids, so they are hidden for the duration of the drag and recalculated
the instant the row lands.

### Why delete-then-insert, not update-per-row

`replaceStops` clears a plan's stops and rewrites them inside one transaction. Updating rows one at a time
would collide with the unique index on `(planId, position)` halfway through — moving stop 3 to position 1
is fine, but whatever is at position 1 has to get out of the way first. The transaction is also what stops
a crash landing in the window where the plan has no stops at all.

### Why the default name is generated on the disk thread

`PlanRepository.defaultName` counts the plans already in the round. Counting is a database read, so it
happens on `diskIO()` with the insert — which is also why two plans cannot take the same default name
however fast the button is pressed.

### Why nothing is written until Save

Dragging changes the screen, not the database. A screen that wrote on every drag would make an accidental
nudge permanent, and there would be no way to tell a deliberate arrangement from a mis-tap. Save is
disabled until the order actually differs from what is stored, so the button also *reports* whether the
screen is saved.

### Why the fragment owns the order

`orderedStops` is the single source of truth: the adapter renders it, dragging rearranges it, Save writes
it out. The adapter's own copy is swapped identically during a drag, and the whole list is re-submitted
from `orderedStops` on drop — so even a missed move cannot leave the two apart.

---

## 14. DATA FLOW FOR THIS PHASE

```
Open the screen
  └─ three one-shot reads: stores, completion history, settings
       └─ pending stores for the round  (PendingStores — pure, tested)
            └─ read the plans saved for this round
                 ├─ none  → RouteOrderPlanner.plan()  → an unsaved suggestion
                 └─ some  → read the chosen plan's stops
                              └─ PlanMerge.merge(savedOrder, pending)
                                   └─ RoutingRepository.measureRoute()
                                        └─ render

Drag a stop
  └─ swap in orderedStops AND in the adapter (both, the same way)
       └─ on drop: measureRoute(orderedStops)  → dirty = true  → render

Save
  └─ no plan yet  → PlanRepository.createPlan()  → name generated, plan id + name come back
  └─ a plan      → PlanRepository.replacePlanStops()  → stops + totals in ONE transaction
       └─ dirty = false → the button shows the plan's name, Save goes grey
```

---

## 15. MANUAL TEST PROCEDURE

1. Open the route screen from the Dashboard. The stops appear in an order the app worked out, and the
   button under the totals reads **"Not saved yet"**. Save is **enabled**.
2. Press and hold a stop, drag it to the top, release. The numbers renumber immediately, the distances
   update, and the button changes to **"… (unsaved)"**.
3. Press **Save**. A message names the plan (`Saved as "Plan 1".`). The button now reads **"Plan 1"** and
   Save is **greyed out**.
4. Go back, then open the route screen again. **Your order is still there**, under "Plan 1".
5. Open the plan menu (the button with the caret). "Plan 1" is ticked. Choose **New plan**. The stops
   return to the app's suggested order and the button reads **"Not saved yet"**.
6. Press **Save**. The new plan is called **"Plan 2"**.
7. Open the menu — **both plans are listed**, with Plan 2 ticked. Switch to Plan 1. Your first order is
   back.
8. **Rename** → type `Monday` → Save. The button reads "Monday".
9. **Delete** → confirm. The plan is gone and a fresh suggestion appears.
10. **Rotate the phone.** The plan you were looking at is still the one on screen.
11. Open the map, complete a store, come back. The completed store is **gone from the plan**, and the rest
    keep their order.
12. Add a new store from the map, come back. The new store is **at the end** of the plan.
13. **Start Route** — it should open Google Maps to the **first stop of your order**, not the app's.

---

## 16. EXPECTED RESULT

- The route screen shows an order that can be changed by hand and saved under a name.
- Many plans can exist per round, and can be created, renamed, deleted and switched between.
- Reopening the screen shows the saved order, not a recomputed one.
- A completed store drops out of a plan; a new store appears at the end; the rest keep their order.
- Nothing is written until Save is pressed.
- **No route screen failure state is reachable** except "nothing left to visit".

---

## 17. COMMON ERRORS AND FIXES

| Symptom | Cause | Fix |
| --- | --- | --- |
| **`method showSnack(int) is already defined`** | Two helpers with the same signature were written in the fragment | One pair: `showSnack(int)` delegating to `showSnack(int, Object)`. **This did happen and was caught by the compiler** |
| Plans from another round appear in the menu | `findPlansInRoundNow` called with the wrong round | The round comes from `settings.currentRound`, read in `planFrom` and stored in `currentRound` |
| A reorder is lost on rotation | Only the plan id is restored, not the order | **By design.** An unsaved arrangement is not real until Save. The saved order reloads correctly |
| `#1, #2, #2` after a drag | A sequence carried in the data was rendered | The number is `position + 1`. Never render a stored sequence |
| A stale leg distance during a drag | Legs not hidden while reordering | `adapter.setReordering(true)` in `onSelectedChanged`, `false` in `clearView` |
| A drag does nothing | `getBindingAdapterPosition()` returned `NO_POSITION` | Guarded in `onMove`; the row is being recycled mid-drag |
| `optimize()` is never called | **Deliberate.** No screen uses the service path | See the note on `RoutingRepository.optimize()` — re-enabling it means restoring a call in `planFrom` |

---

## 18. ACCEPTANCE CHECKLIST

- [x] Drag a stop → the order changes and renumbers
- [x] Leg distances recalculate after a drag
- [x] Save creates a plan with a generated name
- [x] Save updates an existing plan
- [x] Save is disabled when nothing has changed
- [x] The menu lists every plan for the round, with the current one ticked
- [x] Rename works, and refuses a blank or over-long name
- [x] Delete confirms, and says the stores are unaffected
- [x] A plan made for another round is not offered
- [x] A completed store drops out of a plan
- [x] A newly added store appears at the end
- [x] A store listed twice in a plan is visited once
- [x] The chosen plan survives a rotation
- [x] Start Route opens the **user's** first stop
- [x] No migration was needed; schema stays at version 3
- [x] `./gradlew :app:testDebugUnitTest :app:assembleDebug` → **BUILD SUCCESSFUL**
- [x] 563 tests, 0 failures
- [ ] **On-device test by the user** — see §20

---

## 19. UPDATED PROJECT STATE

| | |
| --- | --- |
| **Database** | version **3**, unchanged. `route_plans` and `route_plan_stops` are now read and written |
| **New repository** | `PlanRepository` — the only writer of plans |
| **New pure domain class** | `PlanMerge` |
| **Route screen** | now shows the user's order when there is one, and the app's when there is not |
| **Tests** | 563 (546 before): +12 `PlanMergeTest`, +5 `measure` tests |
| **Unused, kept deliberately** | `RoutingRepository.optimize()`, `OptimizationRequestBuilder`, `OptimizationResponseParser`, `RoutingConfig.optimizationUrl()` — the openrouteservice VROOM path, verified working and documented, but no screen calls it |

---

## 20. STOP POINT

**This phase is code-complete and building. It is NOT verified on a device.**

The JVM tests prove the merge rule, the ordering and the measuring. They cannot prove that a drag feels
right, that the menu appears where a thumb expects it, or that Room accepts the write on a real phone.

**Next action: the user installs the build and works through §15.** Report anything that looks wrong,
especially items 4, 10 and 11 — the ones where state has to survive something.

**Do not start another phase until §15 has been run.**

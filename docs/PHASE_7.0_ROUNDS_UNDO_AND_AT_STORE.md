# PHASE 7.0 — Rounds, Undo, and the At-Store Shortcut

> **Status: code complete, build verified.** Device testing is §15 and has not been run yet.

> **Why 7.0, and why it is not the roadmap's Version 7.** The roadmap's Version 7 is the next-store
> suggestion (7.1 – 7.4). This phase was requested directly by the user, before any of that, and it is
> numbered **7.0** so it sits in front of those without renumbering them: a phase number is never reused
> and never reordered, and the suggestion work keeps 7.1 – 7.4 exactly as planned.
>
> **It is also a prerequisite for them.** A suggestion strategy has to know which stores are still to
> visit *in the round in progress*. Before this phase there was no round, so "still to visit" had no
> meaning once a store had been visited once.

---

## 1. CURRENT VERSION AND PHASE

| | |
| --- | --- |
| Version | 7 — Rounds, undo, and the at-store shortcut |
| Phase | **7.0** |
| Previous phase | 6.1 – 6.5 — the tracker and the history ✅ |
| Next phase | **7.1 — Nearest pending store** |
| Database version | **2 — the first migration in the project.** See §10 and §13 |
| Kotlin files | **0** |

---

## 2. OBJECTIVE

Three things the user asked for, and they turn out to be one feature.

1. **Standing at a store should not require a journey through the app.** "Get directions" is useless when
   you are already there; the only thing left to do is confirm.
2. **A mis-tap must be recoverable.** For six versions a completion could not be removed, and the store
   could not even be deleted (AD-6) — so one wrong tap on YES permanently spoiled the record.
3. **The route has rounds.** Visiting every store once is one round; then you do it again. The app should
   count the rounds and show progress through the current one.

The third is what makes the first two a single piece of work: rounds are what give "still to visit" a
meaning again, and the at-store shortcut is how a store gets completed in the new round.

---

## 3. UNDERSTANDING CHECKLIST

- ✅ **A round is one complete pass of every store.** Confirmed with the user directly.
- ✅ A store is completed **once per round**, and starting a new round puts every store back to PENDING so
  the map shows what is left.
- ✅ **AD-5 is generalised, not weakened.** The unique index moves from `storeId` to
  `(storeId, roundNumber)`: a store still cannot be counted twice **in the same round**.
- ✅ **Past rounds are never deleted.** Starting a new round changes statuses; every completion row stays.
- ✅ The completion transaction is still the only writer of `completion_history`, and the undo is the only
  other thing that touches it — one row at a time, always behind a confirmation.
- ✅ **The undo is narrow on purpose.** No bulk delete, no editing, no "clear history".
- ✅ `domain/` stays pure Java (AD-11). The round rule is a pure function and is tested on the JVM.
- ✅ The migration is **written out in full**. There is still no `fallbackToDestructiveMigration()`.

---

## 4. FEATURES INCLUDED (in this phase)

1. **Rounds.** `completion_history.roundNumber` and `app_settings.currentRound`, with the unique index
   scoped to the pair.
2. **The migration.** `MIGRATION_1_2`, the first in the project, written statement by statement.
3. **`RoundCalculator` + `RoundProgress`** — the round rule, pure Java, 14 tests.
4. **Start next round.** Refused unless the round is complete; re-checked **inside** the transaction.
5. **Round-aware completion.** The transaction's guards and its insert are scoped to the current round.
6. **Undo a completion.** From Completion Detail, behind a confirmation, removing exactly one row.
7. **The at-store shortcut.** Inside the arrival radius, "Get directions" is replaced by "Store
   completed?", which opens the same dialog the navigation sheet opens.
8. **The Dashboard, round-aware.** Round, Rounds finished, Remaining-this-round and Round completion,
   plus a "round complete" message.
9. **Settings round panel.** The round in progress, how many stores are done, how many rounds are
   finished, and the button — disabled with a reason until the round is complete.
10. **A round-aware consistency check.** Scoped so that a store pending with history from an older round
    is correctly treated as **healthy**, not as a fault.

---

## 5. FEATURES NOT INCLUDED YET

| Feature | Phase |
| --- | --- |
| The next-store suggestion | **7.1** |
| Recalculating the suggestion after a completion | **7.2** |
| The Home-aware heuristic | **7.3** |
| Road-based distances and multi-stop optimisation | **Version 8** |
| Undo from the History list (rather than the detail screen) | **not planned** — one route to a destructive action is enough |
| Deleting a whole round | **never** — a round is a record of what happened |
| Editing a completion's date or sequence | **never** — only removal is offered, and only one row at a time |
| Per-round notes or a round label | **not planned** — not asked for, and the number is enough |

---

## 6. ASSUMPTIONS

| # | Assumption |
| --- | --- |
| A97 | **A round is complete when every store currently in the database has a completion in it.** A store added mid-round therefore re-opens the round — the honest reading, because the user added it precisely because they still have to visit it. |
| A98 | `currentRound` is **stored**, not derived from `MAX(roundNumber)`. A round with nothing completed yet has no rows, so a derived value could not tell "round 3, nothing done" from "round 2, everything done". |
| A99 | A new round may only start when the current one is complete. Starting early would discard a pass with nothing recording that it was abandoned. |
| A100 | The round is re-checked **inside the start-round transaction**. The button's enabled state came from an earlier read and a store could have been added since. |
| A101 | The undo resets the store to PENDING **only when the row belonged to the current round**. Undoing an old round's completion must not change whether the store is done now. |
| A102 | The undo deletes one row and never renumbers the survivors. The `dailySequence` records the order things happened in, and rewriting it would rewrite history. |
| A103 | A store cannot be deleted while it has **any** history, including from a past round (AD-6, unchanged). |
| A104 | The dashboard's "Remaining" and "Completion" are **round-scoped**; "Overall completed" is lifetime. A lifetime figure cannot go back up when a new round starts, which is exactly what the user needs to see. |
| A105 | The at-store shortcut reuses the completion dialog rather than completing anything itself. There is still exactly one gate for counting a store. |

---

## 7. FILES TO CREATE (5)

| # | File | Purpose |
| --- | --- | --- |
| 1 | `domain/RoundProgress.java` | One round's progress. Pure. |
| 2 | `domain/RoundCalculator.java` | The round rule. Pure, zero `androidx.room` imports. |
| 3 | `data/db/RoundOutcome.java` | `SUCCESS` / `ROUND_NOT_COMPLETE` / `NO_STORES` / `FAILED`. |
| 4 | `data/db/RoundTransactionDao.java` | **Starting a new round** — the only thing that resets a store's status. |
| 5 | `app/src/test/.../RoundCalculatorTest.java` | 14 tests. |

---

## 8. FILES TO MODIFY (23)

| # | File | Change |
| --- | --- | --- |
| 1 | `data/db/CompletionHistoryEntity.java` | `roundNumber`; the unique index becomes `(storeId, roundNumber)`. |
| 2 | `data/db/AppSettingEntity.java` | `currentRound`. |
| 3 | `data/db/AppDatabase.java` | **v2**, `MIGRATION_1_2`, `roundTransactionDao()`, `.addMigrations(...)`. |
| 4 | `data/db/CompletionTransactionDao.java` | Round-scoped guards and insert; **`undo()`**. |
| 5 | `data/db/CompletionOutcome.java` | `COMPLETION_NOT_FOUND`. |
| 6 | `data/db/CompletionHistoryDao.java` | `findById`; `findByStoreId` now returns the newest round's row. |
| 7 | `data/repository/CompletionRepository.java` | `undoCompletion`, `startNewRound`, `findByHistoryId`, `countOutstandingInCurrentRound`, `currentRound`; `completeStore` passes the round. |
| 8 | `AlphaRouteApp.java` | Wires the new DAO and the settings repository into `CompletionRepository`. |
| 9 | `domain/TrackerCalculator.java` | `roundNumber()` on the seam; `currentRound` parameter; round-scoped counts. |
| 10 | `domain/TrackerSnapshot.java` | `currentRound`, `roundsFinished`, `completedInRound`, `remainingInRound`, `roundPercentage`. |
| 11 | `domain/ConsistencyChecker.java` | The first two checks scoped to the current round. |
| 12 | `ui/dashboard/DashboardViewModel.java` | A third source: the settings row. |
| 13 | `ui/dashboard/DashboardFragment.java` | The round cards and the round-complete message. |
| 14 | `ui/store/StoreDetailBottomSheet.java` | The at-store swap. |
| 15 | `ui/history/CompletionDetailFragment.java` | Takes a **history row id**; the undo and its confirmation. |
| 16 | `ui/history/DailyHistoryFragment.java` | Passes the history row id. |
| 17 | `ui/settings/SettingsFragment.java` | The round panel and Start next round. |
| 18 | `res/layout/fragment_dashboard.xml` | Round cards, 9 cards in total. |
| 19 | `res/layout/fragment_settings.xml` | The round card. |
| 20 | `res/layout/sheet_store_detail.xml` | The complete button and the at-location note. |
| 21 | `res/layout/fragment_completion_detail.xml` | The undo button. |
| 22 | `res/values/strings.xml` | 25 new strings; two Dashboard labels changed meaning. |
| 23 | `TrackerCalculatorTest`, `ConsistencyCheckerTest`, `HistoryGrouperTest` | The new seam member, the new signatures, and round cases. |

---

## 9. DEPENDENCIES

| Dependency | Status |
| --- | --- |
| Everything from 6.1 – 6.5 | Reused. **Not one new library.** |
| `androidx.room.migration.Migration` | Already present. |
| openrouteservice | **Not used.** No network call anywhere in this phase. |

**No new dependency, no new permission, no new key.**

---

## 10. PERMISSIONS AND MANIFEST CHANGES

**None.** The fifth phase in a row with no manifest change.

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

Nothing else. No `local.properties` change, no new key.

**This is the first phase that migrates an existing database.** An app installed from an earlier build
upgrades in place on first open; no uninstall is needed, and no data is lost.

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 12. COMPLETE CODE

The complete files are in the repository at the paths listed in §7 and §8. This record does not repeat
them; the phase rule is that a delivered file is the complete file, not a diff, and they are all present.

---

## 13. CODE EXPLANATION

### AD-5 was generalised, not weakened

The project's central guarantee was a `UNIQUE` index on `completion_history.storeId`, which made a second
completion for a store **impossible, ever**. Rounds need that second completion, so the index became:

```
UNIQUE(storeId, roundNumber)
```

**This is the same guarantee, scoped.** A store still cannot be counted twice *in the same round* — which
is the thing that was ever meant. The old rule was really "do not double-count", and double-counting
within a round is still physically impossible.

The difference is that a store now has **one row per round** instead of one row for ever. That is what makes
rounds work at all, and it is why the change could not be avoided.

### The migration, statement by statement

```sql
ALTER TABLE completion_history ADD COLUMN roundNumber INTEGER NOT NULL DEFAULT 1;
DROP INDEX IF EXISTS index_completion_history_storeId;
CREATE UNIQUE INDEX IF NOT EXISTS index_completion_history_storeId_roundNumber
    ON completion_history (storeId, roundNumber);
CREATE INDEX IF NOT EXISTS index_completion_history_roundNumber
    ON completion_history (roundNumber);
ALTER TABLE app_settings ADD COLUMN currentRound INTEGER NOT NULL DEFAULT 1;
```

Three things make this safe on a table that already holds rows:

1. **`ADD COLUMN ... NOT NULL DEFAULT` is the one form SQLite allows without rewriting the table**, and it
   is why both new columns carry a default. For every existing completion the answer is 1, because none of
   them had a round — the default is not a placeholder, it is the true value.
2. **Dropping an index loses no data.** An index is rebuilt from the table; the table is untouched by
   either statement.
3. **The index names are the ones Room generated for version 1**, read out of the exported `1.json` rather
   than guessed. Room validates the resulting schema against the entities on open, so a wrong name would
   fail loudly — but it would still be a crash at first launch, so the names were checked.

### There is still no destructive fallback

```java
.addMigrations(MIGRATION_1_2)
```

and no `fallbackToDestructiveMigration()` anywhere. That call would have been a one-line way to make the
schema mismatch go away, by silently deleting every store and every completion the user has. The project's
rule against it has now been tested for the first time.

### Why the round number is stored

A round could have been derived as `MAX(roundNumber) + 1`. It is stored instead, and the difference is the
case that matters: **a round with nothing completed yet has no rows at all.** A derived value could not
tell "round 3, nothing done yet" from "round 2, everything done" — both have the same `MAX`. The number has
to exist independently of the rows it will collect.

### The round is re-checked inside the transaction

```java
// In RoundTransactionDao.startNewRound:
int outstanding = countStoresWithoutCompletionIn(current);
if (outstanding > 0) {
    return RoundOutcome.refusal(RoundOutcome.Status.ROUND_NOT_COMPLETE, outstanding);
}
```

The Settings button is enabled or disabled from the pure `RoundCalculator`, which read the database a
moment ago. A store could have been added since — so the decision that matters is made **inside** the
transaction, against the current rows. The button's state is a courtesy; this is the guard.

### `NOT EXISTS`, not a count comparison

```sql
SELECT COUNT(*) FROM stores s WHERE NOT EXISTS (
    SELECT 1 FROM completion_history h
    WHERE h.storeId = s.id AND h.roundNumber = :roundNumber)
```

Comparing "completions in this round" against "stores" would be satisfied by any combination that happened
to add up: one store with two rows in the round and another with none would look complete. This asks the
actual question, per store.

### Why starting a round is one transaction

Advancing the round number and resetting the stores must happen together, because both partial outcomes are
unrecoverable:

- **Number advanced, reset failed** → the app is in round 2 with every store reading COMPLETED. The map
  shows a finished route, and the round looks complete before it began.
- **Reset done, number not advanced** → every store is PENDING while its completion is filed under the old
  round. Confirming them again is refused by the unique index, and there is no way out through the UI.

So `reset != stores` **throws**, which makes Room roll the whole thing back.

### The undo is the only destructive operation, and it is narrow

```java
CompletionHistoryEntity row = findHistoryById(historyId);
if (row == null) return refusal(COMPLETION_NOT_FOUND);
deleteHistory(historyId);
if (row.roundNumber == currentRound) markPending(row.storeId, PENDING, now);
```

Three deliberate narrowings:

- **It takes a row id, not a store id.** With rounds, "the store's completion" is not a single thing, and a
  store id would be ambiguous about which record to remove.
- **It resets the store only for the current round.** A store is green because of its *current-round*
  completion; removing an old round's row must not turn it orange.
- **It cannot fail into a worse state.** If the store cannot be reset, the method throws and the delete is
  rolled back — so a store is never left COMPLETED with the history row that justified it already gone.

`dailySequence` is **not** renumbered afterwards. The sequence records the order things happened in, and
rewriting the survivors would rewrite history.

### The undo is reachable, and that is the point

For six versions the rule was "a completion is a historical fact", and the rule is right — but a mis-tap
was then unrecoverable. The store could not be completed again, and because the foreign key refuses to
delete a store that has history (AD-6) it could not even be removed from the list. **A guarantee with no
way out is a trap.** The undo is the way out, and it is deliberately the only one: no bulk delete, no
editing, no "clear history".

### The at-store shortcut, and why it reuses the dialog

```java
boolean atStore = state == ArrivalState.ARRIVED;
directionsButton.setVisibility(atStore ? View.GONE : View.VISIBLE);
completeButton.setVisibility(atStore ? View.VISIBLE : View.GONE);
```

- **The decision is `ArrivalEvaluator`'s, not the sheet's.** It already accounts for the radius, the fix's
  accuracy and the fix's age, and the navigation sheet and the monitoring service both use it. Asking the
  same question a second way would be a second definition of "arrived", free to disagree with the one that
  gates the completion.
- **Exactly one primary button is shown.** Both are full-width, so showing them together would be two
  primary actions and no way to tell which one the app expects.
- **It opens the completion dialog rather than completing anything.** There is still exactly one gate for
  counting a store — the YES. Standing in front of a shop should not mean a journey through Get directions,
  the route preview, Start navigation and the arrival wait, but it should still mean the same confirmation.
- **The note says why the button moved.** A button that simply disappears reads as a bug rather than a
  state.

### The consistency check had to be scoped, or it would cry wolf

After a new round starts, **every store is PENDING while its completion is filed under the old round.**
That is the normal state, and an unscoped check would report the entire route as inconsistent the moment
round 2 began — which is how a checker teaches the user to ignore it.

So the first two faults are scoped to the current round, while an orphan is still reported from **any**
round: a completion pointing at a store that does not exist is wrong whatever round it belongs to, and it
is the one fault the foreign key should make impossible.

### Two Dashboard labels changed meaning, and the labels changed with them

| Card | Before | Now |
| --- | --- | --- |
| Remaining | total minus every completion ever | **left in this round** |
| Completion | lifetime percentage | **how much of this round is done** |

The reason is the one thing a lifetime figure cannot do: **go back up**. Starting a new round has to show a
full route again, and a lifetime percentage would sit at 100% for ever. "Overall completed" keeps the
lifetime meaning, and the label says so.

---

## 14. DATA FLOW FOR THIS PHASE

```
Round 1 in progress  (app_settings.currentRound = 1)
      ↓
Confirm a store  →  CompletionTransactionDao.complete(storeId, now, zone, roundNumber = 1)
                        ├─ guards scoped to round 1
                        ├─ INSERT completion_history(storeId, …, roundNumber = 1)
                        └─ UPDATE stores SET status = COMPLETED
      ↓
RoundCalculator.compute(stores, history, 1)
      └─ every store has a row with roundNumber = 1  →  isComplete() == true
      ↓
Settings: "Start next round"  (enabled, because the round is complete)
      ↓
RoundTransactionDao.startNewRound(now)          @Transaction
      1. countStores() == 0                  → NO_STORES
      2. stores without a round-1 completion  → ROUND_NOT_COMPLETE
      3. app_settings.currentRound = 2
      4. UPDATE stores SET status = PENDING, completedAt = NULL   (every store)
      └─ returns SUCCESS(2, n)
      ↓
The map is orange again · every round-1 completion stays in the History
      ↓
Complete a store again  →  a SECOND row, roundNumber = 2
      ↓
Completion Detail → "Undo completion" → confirm
      ↓
CompletionTransactionDao.undo(historyId, currentRound = 2, now)   @Transaction
      1. find the row by id        → gone  → COMPLETION_NOT_FOUND
      2. DELETE the row
      3. row.roundNumber == 2 == currentRound → UPDATE stores SET status = PENDING
      └─ returns SUCCESS
```

**The network is nowhere in this flow.** Every operation is local.

---

## 15. MANUAL TEST PROCEDURE

Needs a device. **§15.1 must be done on a build that already has data**, because it is the migration.

### 15.1 The migration — do this first

1. Install the **previous** build, add three stores and complete one of them.
2. Install this build **over it** (`adb install -r`, do not uninstall).
3. Open the app. It must start normally — **no crash, no "database version" error**.
4. The three stores must still be there, and the completed one must still be green with its history.
5. The Dashboard must show **Round 1** and **Rounds finished 0**.

> If step 3 crashes, the migration did not match the entity definitions. The logcat message will name the
> expected column or index — see §17.

### 15.2 The at-store shortcut

6. Set the arrival radius to a large value (Settings, up to 1000 m) so you can test without moving.
7. Tap a **pending** store's marker. The sheet must show **"Get directions"**.
8. Move inside the radius (or set the radius large enough that you already are).
9. The sheet must swap: **Get directions disappears**, **"Store completed?"** appears, and the note
   "You are inside the arrival radius of this store." is shown.
10. Tap it. The **same** completion dialog must open, with the distance filled in.
11. Tap **NO**. Nothing changes. The sheet still offers "Store completed?".
12. Tap a **completed** store's marker. It must show **Get directions**, never the complete action — there
    is nothing left to confirm.

### 15.3 The undo

13. Complete a store. Then open **History → that day → the completion**.
14. The detail must show the round ("Round 1 · store #1 that day") and an **Undo completion** button.
15. Tap it. A confirmation must appear naming what will happen. Tap **Cancel** — nothing changes.
16. Tap it again and confirm. The message must say the completion was undone.
17. Go back to the Map. **The marker must be orange again.**
18. The Dashboard's **Remaining this round** must have gone **up** by one, and **Overall completed** must
    have gone **down** by one.
19. Complete that store again. It must work — the unique index allowed it because the row was deleted.

### 15.4 Rounds

20. With every store pending, check Settings: **Start next round** must be **disabled**, with a note saying
    how many stores are still to visit.
21. Complete every store. The Dashboard must show the **"This round is complete"** message, and the
    Settings button must become **enabled**.
22. Tap it. The confirmation must say the history is not deleted. Confirm.
23. **Every marker must be orange again.** The message must name the new round and how many stores were
    reset.
24. The Dashboard must show **Round 2**, **Rounds finished 1**, **Remaining this round = all of them**, and
    **Round completion 0.0%**.
25. The History tab must **still show round 1's completions.** Nothing was deleted.
26. Open one of them. Its detail must say **Round 1**.

### 15.5 The round scoping

27. In round 2, complete one store. The Dashboard must show **Remaining this round = all minus one**, and
    **Overall completed = round 1's total + 1**.
28. Run the **consistency check**. It must say **"Everything agrees"** — a store pending with history from
    round 1 is the normal state, not a fault.

### 15.6 Adding a store mid-round

29. Start a fresh round and complete everything except one store.
30. Add a new store on the map. The Dashboard must show **Remaining this round = 2**, and the Settings
    button must become **disabled** again.
31. Complete both. The button must re-enable.

### 15.7 Nothing here counts itself

32. After every step above, **no store may be counted without an explicit YES**, and the map and the
    Dashboard must agree at every point.

---

## 16. EXPECTED RESULT

- An existing database upgrades in place, losing nothing.
- A store can be completed **once per round**, and never twice in the same one.
- Standing at a store offers the confirmation directly, with no route in between.
- A wrong completion can be undone, and only that one, and only after a confirmation.
- Starting a new round turns every marker orange and **deletes nothing**.
- The Dashboard shows both the round in progress and the lifetime totals, and they never contradict each
  other.
- The consistency check stays silent through all of it.

---

## 17. COMMON ERRORS AND FIXES

| Symptom | Cause | Fix |
| --- | --- | --- |
| Crash at launch: `A migration from 1 to 2 was required but not found` | `MIGRATION_1_2` was not registered | `.addMigrations(MIGRATION_1_2)` in `AppDatabase.getInstance`. |
| Crash at launch: `Migration didn't properly handle: completion_history` | The migration's result does not match the entity | Compare against the exported `2.json`. The message names the table and the difference. |
| Crash: `Expected: … Found: …` on an index | The index name in the migration differs from Room's | The names must be Room's own: `index_completion_history_storeId_roundNumber`. Read them out of `1.json` and `2.json`. |
| `UNIQUE constraint failed: completion_history.storeId, completion_history.roundNumber` | A second completion in the same round | Correct behaviour — this is AD-5. It is caught and reported as "Already counted". |
| A second-round completion is refused as "Already counted" | The round was not passed to the transaction | `completeStore` reads `currentRound` from settings and passes it down. A `0` or a stale value will do this. |
| **Start next round** is always disabled | The round genuinely is not complete | The note under the button says how many stores are still to visit. If it says 0 and the button is still disabled, `canStartNewRound()` is being fed the wrong round. |
| Starting a round leaves some stores green | The reset did not cover every store | `startNewRound` **throws** if `reset != stores`, so this should be impossible — check the log for "Round rollback". |
| A store goes green but the round still says incomplete | The completion was filed under the wrong round number | The repository reads `currentRound` once and passes it to both the guard and the insert. Two readings could straddle a round change. |
| The undo leaves the store green | The row belonged to an older round | Working as designed: only a current-round row resets the store. See §13. |
| The consistency check reports every store after a new round | The check is not round-scoped | The first two faults must compare against the **current** round. See §13. |
| "Remaining" never goes down | The Dashboard is showing lifetime figures | `remainingInRound()`, not a total minus an overall count. |

---

## 18. ACCEPTANCE CHECKLIST

- [x] **The migration exists, is written out in full, and has no destructive fallback.**
- [x] Every migration statement is safe on a table that already has rows.
- [x] The index names came from the exported `1.json`, not from guesswork.
- [x] `DATABASE_VERSION` is 2 and `1.json` is preserved alongside `2.json`.
- [x] **AD-5 is scoped to `(storeId, roundNumber)`** — a generalisation, documented as such.
- [x] The completion transaction's guards and insert are both round-scoped.
- [x] The round is read **once** by the repository and passed down.
- [x] The round is re-checked **inside** the start-round transaction.
- [x] A failed store reset **throws**, so the round change rolls back.
- [x] The undo takes a **row id**, deletes one row, and resets the store only for the current round.
- [x] The undo is behind a confirmation and is the only destructive operation on the table.
- [x] Starting a round **deletes nothing**.
- [x] The consistency check is round-scoped and stays silent after a new round.
- [x] `domain/` has zero `androidx.room` imports in the new files (AD-11).
- [x] The round rule is pure Java and unit-tested, including the empty route and mid-round additions.
- [x] The at-store shortcut reuses the completion dialog — there is still one gate.
- [x] The Dashboard's two round-scoped labels were changed with their meaning.
- [x] **No new permission, no manifest change.**
- [x] Zero Kotlin.
- [x] The build is green with all tests passing (see §19).
- [ ] **§15 has been run on a device.** ← the only unticked box, and the migration is the part that most needs it

---

## 19. UPDATED PROJECT STATE

Verified on 2026-09-27 with:

```bash
./gradlew clean :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain
```

| | |
| --- | --- |
| Build | **BUILD SUCCESSFUL in 4m 2s** — 42 tasks, all executed (clean tree) |
| Tests | **375 passing, 0 failures, 0 errors, 0 skipped** across 25 classes (348 before, **+27 new**) |
| New tests | `RoundCalculatorTest` 14 · `TrackerCalculatorTest` 20 → **30** (+10 round cases) · `ConsistencyCheckerTest` 17 → **20** (+3) |
| APK | **9,769,245 bytes** (debug, clean build) |
| **Database schema** | **`1.json` AND `2.json`** — the first migration in the project |
| Kotlin files | **0** |
| Java sources | 82 main · 25 test |
| New permissions | **0** — the merged manifest is unchanged from 4.3 (8 entries) |
| Runtime strings | 206 (182 before, +25 new, −1 label replaced) |

### The migration was checked against the exported schema, statement by statement

`./gradlew` writes `2.json` from the **entities**. `MIGRATION_1_2` writes the database. They must produce
the same thing, and the only way to compare them without a device is to read both:

| What the migration does | What `2.json` says | |
| --- | --- | --- |
| `ADD COLUMN roundNumber INTEGER NOT NULL DEFAULT 1` | `roundNumber` INTEGER, `notNull=true`, `default=1` | ✅ |
| `DROP INDEX index_completion_history_storeId` | the index is absent from v2 | ✅ |
| `CREATE UNIQUE INDEX index_completion_history_storeId_roundNumber (storeId, roundNumber)` | `index_completion_history_storeId_roundNumber`, `unique=true`, `["storeId","roundNumber"]` | ✅ |
| `CREATE INDEX index_completion_history_roundNumber (roundNumber)` | `index_completion_history_roundNumber`, `unique=false`, `["roundNumber"]` | ✅ |
| `ADD COLUMN currentRound INTEGER NOT NULL DEFAULT 1` | `currentRound` INTEGER, `notNull=true`, `default=1` | ✅ |

**This is a strong check, but it is not the proof.** It confirms the migration produces the shape the
entities describe — it does not confirm SQLite accepts it on a populated database, which is what §15.1 is
for. Room's own validation happens at open, on the device.

### What was checked in the merged manifest

Unchanged since 4.3 — 8 entries, and `ACCESS_BACKGROUND_LOCATION` is still only the comment text:

```
ACCESS_COARSE_LOCATION · ACCESS_FINE_LOCATION · ACCESS_NETWORK_STATE
FOREGROUND_SERVICE · FOREGROUND_SERVICE_LOCATION · INTERNET
POST_NOTIFICATIONS · RECEIVE_BOOT_COMPLETED
```

---

## 20. STOP POINT

**Phase 7.0 is code complete and the build is verified. It is NOT verified on a device.**

**This phase changed the schema for the first time, so §15.1 is not optional.** Everything else can be
re-tried; a broken migration on a device with real data cannot.

Do not begin Phase 7.1 until **§15 has been run**, and specifically:

1. **§15.1** — that a database from the previous build upgrades in place, losing nothing. This is the only
   part of the phase that a JVM test cannot reach, and the only part that could destroy the user's data.
2. **§15.4** — that starting a new round turns every marker orange and **deletes nothing from the
   History**.
3. **§15.3** — that the undo puts the store back and lets it be completed again.

Carried forward: **4.3 §15**, **4.4 §15**, **5.2+5.3 §15** and **6.1–6.5 §15** are all still outstanding.
The two that matter most remain **4.3 §15.2 step 9** (arriving changes nothing) and **5.2+5.3 §15.1 step 4**
(NO changes nothing at all).

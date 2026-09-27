# PHASE 5.2 + 5.3 — The Completion Flow

> **Status: code complete, build verified.** Device testing is §15 and has not been run yet.

> **Why two phase numbers in one record.** The roadmap lists 5.1, 5.2 and 5.3. **5.1 was already
> delivered by Phase 4.3** — `ArrivalEvaluator` implements exactly the states 5.1 describes (outside /
> approaching / arrived / no fix / no destination) together with the accuracy thresholds. Re-implementing
> it would have created a second definition of the arrival rule, free to disagree with the first.
>
> 5.2 (the dialog) and 5.3 (the transaction) are then built together, because **a confirmation dialog with
> no write behind it is a dead button**. That is the same reasoning that put "Start navigation" into 4.2
> rather than leaving it for 4.3, and the project's own screen plan treats them as one flow (§13.10 and the
> data-flow diagram at §14.4).

---

## 1. CURRENT VERSION AND PHASE

| | |
| --- | --- |
| Version | 5 — Arrival and completion |
| Phase | **5.2 + 5.3 — Completion confirmation and the atomic completion transaction** |
| Previous phase | 4.4 — State recovery ✅ |
| Next phase | **6.1 — Live tracker** |
| Database version | **1 — unchanged.** No new entity, no new column, no `Migration` |
| Kotlin files | **0** |

---

## 2. OBJECTIVE

This is the phase the whole project has been building towards. Everything before it has been safe to get
wrong: a route line in the wrong place, a distance that lags, a prompt that should not have appeared. None
of it wrote anything permanent.

This phase **writes a completion**. It is the first and only write in the app that moves a counter, and it
records a fact that nothing downstream can detect as false. So the objective is not "add a dialog" — it is
to make double-counting structurally impossible and to make every refusal leave the database exactly as it
was.

---

## 3. UNDERSTANDING CHECKLIST

- ✅ Completion is **in-radius + explicit YES**. Never one without the other. The radius is decided by
  `ArrivalEvaluator` (4.3); the YES is decided here.
- ✅ **A store is never counted twice.** Enforced three independent ways — see §13.
- ✅ **Room is the single source of truth** (AD-4). The dialog passes an *id*; everything shown is read
  back from Room.
- ✅ `CompletionRepository` has been **read-only since Phase 2.1 on purpose**, so that no bypass of the
  transaction could exist. This phase gives it its write path, and still no plain insert.
- ✅ The transaction **re-reads the store inside itself**. A `StoreEntity` handed in by a screen was read
  at some earlier moment and may already be stale.
- ✅ The clock is read **once**, for both the timestamp and the calendar day.
- ✅ `domain/` and `util/` stay pure Java with zero Android imports (AD-11), so the date rule is testable
  on the JVM.
- ✅ Nothing about arrival changes here. 4.3's rule — **arriving changes nothing in the database** — is
  still true; only the YES writes.

---

## 4. FEATURES INCLUDED (in this phase)

1. **The completion transaction** — re-read, guard, date key, daily sequence, insert, flip the store, all
   in one `@Transaction`. The only writer of `completion_history`.
2. **`DateKeyHelper`** — the local calendar day as `YYYY-MM-DD`, pure Java, with the midnight boundary
   tested to the millisecond.
3. **`CompletionOutcome`** — `SUCCESS` / `ALREADY_COUNTED` / `STORE_NOT_FOUND` / `FAILED`, carrying the day
   and the daily sequence on success.
4. **Screen 9 — the completion dialog** — "Store completed?" with the store in full, the distance that
   triggered it, and NO / YES.
5. **The loading guard** — both buttons disable and a progress bar shows while the write runs.
6. **Rotation safety** — the dialog is a `DialogFragment`, its `submitting` flag is saved, and a rotation
   that lands mid-write re-checks the database instead of guessing.
7. **Two user-initiated entry points** — the confirm action on the Navigation Active sheet (only when the
   state is `ARRIVED`), and the arrival notification, which now carries the store id.
8. **The session ends on completion** — the drive it was for is counted, so the foreground service stops
   and the persisted reference is cleared.

---

## 5. FEATURES NOT INCLUDED YET

| Feature | Phase |
| --- | --- |
| The live tracker numbers reacting to the new completion | **6.1** |
| The History list and Daily History screens | **6.2**, **6.3** |
| Completion Detail with the green marker and a map thumbnail | **6.4** |
| The consistency check (orphaned history, marker count) | **6.5** |
| Next-store suggestion after a completion | **7.2** |
| An instrumented test of the transaction itself | **10.2** — Room needs a device, so §15 covers it by hand |
| Undo / un-complete a store | **never** — a completion is a historical fact. `CompletionHistoryDao` has no update or delete, deliberately |
| Re-implementing 5.1 | **not needed** — 4.3 built it. See the note at the top |

---

## 6. ASSUMPTIONS

| # | Assumption |
| --- | --- |
| A80 | **A dialog, not a bottom sheet.** The screen plan says so, and the reason is that a bottom sheet can be swiped away — a confirmation the user is still deciding about must not disappear by accident. |
| A81 | The transaction's clock is read **once**, by the repository, and passed down. Two readings would let a completion started at 23:59:59.999 be filed under the previous day while carrying the new day's timestamp. |
| A82 | **`@Update` is not used** to flip the store. A targeted `UPDATE` naming three columns cannot overwrite `createdAt`, `storeCode` or a coordinate — the write is constrained by its own shape. |
| A83 | The date key is **stored, not recomputed on read**. If the user later changes timezone, yesterday's completion must not silently move to another day. |
| A84 | A rotation mid-write is **resolved by re-reading the database**, not by trusting a saved flag. The callback belonged to the destroyed fragment; the database is the only surviving record (AD-4). |
| A85 | Only `FAILED` leaves the dialog open. The other three outcomes settle the question, so re-asking would be pointless. |
| A86 | Adding a DAO **does not change the schema**. Only entities do — so `1.json` is untouched and `DATABASE_VERSION` stays 1. |

---

## 7. FILES TO CREATE (6)

| # | File | Purpose |
| --- | --- | --- |
| 1 | `util/DateKeyHelper.java` | The local calendar day, pure Java. **Did not exist** — the roadmap assumed it. |
| 2 | `data/db/CompletionOutcome.java` | `SUCCESS` / `ALREADY_COUNTED` / `STORE_NOT_FOUND` / `FAILED`, with the day and sequence on success. |
| 3 | `data/db/CompletionTransactionDao.java` | **The transaction.** The only writer of `completion_history`. |
| 4 | `ui/completion/CompletionConfirmDialogFragment.java` | Screen 9. |
| 5 | `res/layout/dialog_completion_confirm.xml` | Screen 9's content view. |
| 6 | `app/src/test/.../util/DateKeyHelperTest.java` | The midnight boundary, timezone, padding, and calendar arithmetic. |

---

## 8. FILES TO MODIFY (7)

| # | File | Change |
| --- | --- | --- |
| 1 | `data/db/AppDatabase.java` | Registers `completionTransactionDao()`. **No schema change.** |
| 2 | `data/repository/CompletionRepository.java` | Gains `completeStore()` — its first and only write. |
| 3 | `AlphaRouteApp.java` | Passes the new DAO into the repository. |
| 4 | `ui/route/NavigationActiveBottomSheet.java` | `renderCompletion()` — the confirm action, gated on `ARRIVED`. |
| 5 | `res/layout/sheet_navigation_active.xml` | `btn_nav_confirm`. |
| 6 | `navigation/NavigationNotifications.java` | The arrival `PendingIntent` carries the store id, on its own request code. |
| 7 | `navigation/NavigationMonitorService.java` | Passes the store id to `arrival(...)`. |

Also modified: `MainActivity.java` — `EXTRA_CONFIRM_STORE_ID`, `onNewIntent`, `handleCompletionIntent()`.
And `res/values/strings.xml` — 10 new strings.

---

## 9. DEPENDENCIES

| Dependency | Status |
| --- | --- |
| Everything from 4.4 | Reused. **Not one new library.** |
| `java.time` | Already available on minSdk 24 via core library desugaring (AD-7). |
| Room `@Transaction` | Already present. No new dependency, no new plugin. |
| openrouteservice | **Not used.** No network call anywhere in this phase. |

**No new dependency, no new permission, no new key, no schema change.**

---

## 10. PERMISSIONS AND MANIFEST CHANGES

**None.** The third phase in a row with no manifest change.

| | |
| --- | --- |
| New permissions | **0** |
| Removed permissions | **0** |
| New components | **0** — the dialog is a Fragment, not an Activity or a Service |
| `ACCESS_BACKGROUND_LOCATION` | Still **not declared** |

> ⚠️ **Trap.** Grepping the merged manifest for `ACCESS_BACKGROUND_LOCATION` still returns a hit — it is
> the **XML comment text**. Unchanged from 4.3 and 4.4.

---

## 11. SETUP INSTRUCTIONS

```bash
cd "C:/Users/Admin/OneDrive/Documents/custom system/AlphaRoute"
./gradlew clean :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain
```

Nothing else. No `local.properties` change, no new key, no database migration.

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 12. COMPLETE CODE

The complete files are in the repository at the paths listed in §7 and §8. This record does not repeat
them; the phase rule is that a delivered file is the complete file, not a diff, and they are all present.

---

## 13. CODE EXPLANATION

### Three guards, and none of them is redundant

Double counting is the failure this project treats as unacceptable, so it is prevented three times over,
and each guard catches a different mistake:

| # | Guard | The mistake it catches |
| --- | --- | --- |
| 1 | The **re-read inside the transaction** | **Stale data.** A `StoreEntity` handed in by a screen was read at some earlier moment. A store flipped to COMPLETED since then would slip past a check made on that copy. |
| 2 | The **UNIQUE index on `storeId`** (AD-5) | **A logic bug.** Even if both checks above somehow passed, SQLite refuses the INSERT. This is what turns "we check first" into "it cannot happen". |
| 3 | The **single-threaded disk executor** (AD-3) | **A threading mistake.** Two completions cannot interleave, so the read-then-write sequence is never observed half-finished. |

Plus the disabled buttons in the dialog, which are the only guard the user ever sees — and the weakest,
since a rotation can re-enable them. That is precisely why they are not the only one.

### The dialog is a `DialogFragment`, and that is a functional choice

A bottom sheet can be swiped away. This dialog must not vanish while the user is deciding, and — more
importantly — a `DialogFragment` is rebuilt by the framework with its arguments intact after a rotation.
Losing the store id mid-decision would leave a dialog that cannot complete anything.

### Rotation mid-write, resolved honestly

The write runs on a background thread owned by the **Application**, not the fragment. So when a rotation
lands mid-write, the fragment is destroyed and rebuilt — but the write keeps going, and its callback is
addressed to a fragment that no longer exists. That callback is gone.

The fix is not to save the result, because there is no result to save yet. It is to **re-read the
database**: if the store now reads COMPLETED the write succeeded; if it is still PENDING it did not, and
the dialog hands itself back to the user. The database is the only surviving record of what happened,
which is exactly what AD-4 claims it is.

### The clock is read once

```java
long now = System.currentTimeMillis();   // in the repository
...
completionTransactionDao.complete(storeId, now, zone);
```

`now` is used for **both** the history row's `completionTimestamp` and its `completionDateKey`. Reading the
clock twice would let a completion that began at 23:59:59.999 be filed under the previous day while
carrying the new day's timestamp — the classic midnight bug, avoided by construction rather than by care.

### A targeted `UPDATE`, not `@Update`

```sql
UPDATE stores SET status = :status, completedAt = :completedAt, updatedAt = :updatedAt WHERE id = :storeId
```

`@Update` rewrites every column, so a bug anywhere in the method could silently overwrite `createdAt`,
`storeCode` or a coordinate. This statement is *incapable* of touching anything else.

And `status` is passed as the **enum**, not as a hardcoded `'COMPLETED'` string. A literal would be a
second definition of the status, free to disagree with `StoreStatus` the moment a constant was renamed —
and the symptom would be a store showing as pending while a history row for it existed.

### Throwing is what rolls back

If the store cannot be marked COMPLETED after the history row was inserted, the method **throws**:

```java
if (updated != 1) {
    throw new IllegalStateException("Completion rollback: store " + storeId + " could not be marked COMPLETED");
}
```

Room's generated wrapper calls `setTransactionSuccessful()` only when the method returns normally. Throwing
means it is never called, so `endTransaction()` rolls everything back — and the one corruption this class
exists to prevent, a history row for a store still marked pending, never reaches the database.

### `ALREADY_COUNTED` is caught, not thrown

```java
try {
    rowId = insertHistory(...);
} catch (SQLiteConstraintException e) {
    return CompletionOutcome.refusal(CompletionOutcome.Status.ALREADY_COUNTED);
}
```

The architecture contract is explicit that a completion attempt on an already-completed store "must be
handled as `ALREADY_COUNTED`, not a crash". The user sees **"Already counted"** — which is information,
not an error. They did nothing wrong.

### The date key is stored, never recomputed

The row keeps `completionDateKey` rather than deriving it from the timestamp on read. That is what makes
"completed today" stable: if the user later flies to another timezone, yesterday's completion must not
silently move to a different day. It is also what lets `ORDER BY completionDateKey DESC` work in SQL with
no conversion at all, because `YYYY-MM-DD` is the one common date format where plain text comparison gives
chronological order.

### `Locale.US` on the date pattern, and why

A formatter built from the device locale can produce non-ASCII digits, and some locales carry a different
calendar system — the Thai Buddhist calendar would render 2026 as 2569. A date key is an internal
identifier that is compared and sorted as a string, so it must always come out the same shape. Only the
**zone** follows the device, because only the zone is genuinely local.

### Two request codes on two notifications

A `PendingIntent` is identified by its intent's action, data and component — **not** by its extras. Both
notifications target `MainActivity` with no data, so with the same request code they would be the *same*
PendingIntent, and `FLAG_UPDATE_CURRENT` would have the last one posted overwrite the other's extras. The
ongoing notification is re-posted every few seconds while driving, so the store id would be wiped almost
immediately and the arrival tap would open a bare map. Distinct request codes keep them apart.

### The session ends on completion

On `SUCCESS`, the dialog calls `navigationSessionManager.stop(context)`. The drive the session was for is
counted, so watching for its arrival has no purpose left — and a session still pointing at a completed
store would offer to navigate to it again. Stopping also clears the persisted reference (AD-10), so a
restart will not offer to resume a drive that is over.

---

## 14. DATA FLOW FOR THIS PHASE

```
Arrival detected (4.3, unchanged) - NOTHING IS WRITTEN HERE
      ↓
ArrivalState == ARRIVED
      ├─→ the Navigation Active sheet shows "Store completed?"  (gated on ARRIVED alone)
      └─→ the arrival notification is posted, carrying the store id

Either entry point, both the user's own tap
      ↓
CompletionConfirmDialogFragment
      ├─ reads the store from Room (code, name, area)
      ├─ shows the distance that triggered the arrival
      └─ NO  → dismiss. Store stays PENDING · marker stays orange · no history · no counter moves
         YES → both buttons disable · progress bar shows
                    ↓
         CompletionRepository.completeStore(storeId)
                    │  reads the clock ONCE
                    ↓
         CompletionTransactionDao.complete(storeId, now, zone)   @Transaction
                    1. SELECT the store again        → null  → STORE_NOT_FOUND
                    2. status == COMPLETED           → ALREADY_COUNTED
                    3. a history row exists          → ALREADY_COUNTED
                    4. dateKey  = DateKeyHelper.keyOf(now, zone)
                    5. sequence = IFNULL(MAX(dailySequence),0)+1 WHERE dateKey = ?
                    6. INSERT completion_history     → unique index refuses → ALREADY_COUNTED
                    7. UPDATE stores SET status=COMPLETED, completedAt=now, updatedAt=now
                    8. return SUCCESS(dateKey, sequence)
                    ↓
            SUCCESS           → session ends · service stops · "Completed — store #3 today"
            ALREADY_COUNTED   → session ends · "Already counted"
            STORE_NOT_FOUND   → "That store no longer exists."
            FAILED            → rolled back · buttons re-enable · "…Nothing was changed. Try again."
```

**The network is nowhere in this flow.** The completion is written locally and the local database is the
source of truth (AD-4); `SyncManager` is notified afterwards and a failure there cannot undo it.

---

## 15. MANUAL TEST PROCEDURE

All of §15 needs a device. Steps 1–6 can be done sitting at a desk if the arrival radius is set large
enough (Settings → arrival radius, up to 1000 m).

### 15.1 The happy path — this is the phase

1. Pin a store, tap its marker, **Get directions**, **Start navigation**.
2. Get inside the arrival radius. The Navigation Active sheet must show **"Store completed?"**.
3. Tap it. The dialog must appear with the **store code, name, area and the arrival distance**.
4. Tap **NO**. The dialog closes. **Nothing must have changed** — the store is still PENDING, the marker is
   still orange, and the History tab is still empty. *(This is 4.3 §15.2 step 9 restated; it is the most
   important assertion in the project.)*
5. Tap the confirm action again → **YES**. Both buttons must disable and the progress bar appear.
6. A message must read **"Completed — store #1 today"**. The marker must turn **green** and the navigation
   bar and notification must both disappear.

### 15.2 The duplicate guard

7. Open the same store's details. There must be **no completion action** — the store is completed.
8. In Settings, confirm the store count is unchanged and the completion count is 1.
9. Delete the store. It must be **refused** ("This store has completion history and cannot be deleted") —
   AD-6 working.

### 15.3 The daily sequence

10. Complete a **second** store the same day. The message must read **"store #2 today"**.
11. Complete a third. **"#3"**.

### 15.4 The dialog's error states

12. Open a store's details, note its id, then delete it from another route if you have one — or simply
    leave a dialog open and delete the store from the Store Details sheet behind it. Tapping **YES** must
    produce **"That store no longer exists."**, and nothing may be written.
13. Confirm **"Already counted"** appears if you can force it: complete a store, then re-open the
    confirmation from the arrival notification before it is auto-cancelled.

### 15.5 Rotation

14. Open the confirmation dialog and **rotate the device**. The dialog must survive with the same store
    shown — not vanish, and not re-ask.
15. Tap **YES** and rotate **immediately**, while the progress bar is showing. The dialog must not come
    back with two enabled buttons offering a second write. It must either report the completion or hand
    the dialog back — and the store must be counted **exactly once** either way.

### 15.6 The notification entry point

16. With a session running, get inside the radius and **do not open the app**.
17. Pull down the shade and tap the arrival notification. The app must open **with the completion dialog
    already showing for that store** — not on a bare map.
18. Repeat with the app already running in the background behind Google Maps. It must still work (this is
    the `onNewIntent` path).

### 15.7 Nothing counts itself

19. Start navigation and arrive. **Do not tap anything.** Leave it for several minutes.
20. The store must still be PENDING. No history row may exist. **Arriving changes nothing** — only a YES
    does.

### 15.8 Midnight, if you can

21. Set the device clock to 23:57, complete a store, and confirm it is filed under that day.
22. Set the clock past midnight, complete another, and confirm it is **#1 of the new day**, not #2 of the
    old one.

---

## 16. EXPECTED RESULT

- A store is counted **only** after an explicit YES, and **never** twice.
- Every refusal — already counted, store gone, write failed — leaves the database **exactly as it was**.
- The dialog shows the evidence for the question: which store, and how far away the app thought you were.
- A rotation mid-write cannot produce a second completion.
- The completion is filed under the **local** calendar day, with the correct position within that day.
- The navigation session ends when the store is counted, so the same store is never offered again.

---

## 17. COMMON ERRORS AND FIXES

| Symptom | Cause | Fix |
| --- | --- | --- |
| `IllegalStateException: Completion rollback: store N could not be marked COMPLETED` in logcat | The store row vanished between the read and the write | Working as designed — the transaction rolled back and nothing was written. The dialog reports FAILED and the user can retry. |
| The dialog shows no distance line | Opened from the arrival notification, which does not carry a distance | Expected. The line is omitted rather than invented (A80). |
| "Already counted" on the first attempt | The store was already completed, or has a history row from an earlier build | Correct behaviour. Check the History tab; the guard is doing its job. |
| The confirm action never appears on the sheet | The arrival state is not `ARRIVED` | Check the accuracy on the Map tab — `ArrivalEvaluator` refuses a fix worse than 100 m or older than 30 s. |
| A second completion is offered after a rotation | `submitting` was not restored | It is saved in `onSaveInstanceState` and re-applied in `onCreateDialog`. If it is missing, that line was removed. |
| The dialog opens twice from the notification | The `findFragmentByTag` guard was bypassed, or `savedInstanceState` handling changed | `handleCompletionIntent` is skipped when `savedInstanceState != null`, and guarded by the tag. |
| Tapping the arrival notification opens a bare map | Both notifications share one `PendingIntent` | They must use different request codes — see §13. |
| The completion is filed under the wrong day | The clock was read twice, or the zone was wrong | The repository reads `System.currentTimeMillis()` **once** and passes it down with `ZoneId.systemDefault()`. |
| `Cannot access database on the main thread` | A DAO call made directly from a Fragment | All database work goes through a repository, which runs it on `diskOps()`. A Fragment never touches Room (AD-4). |

---

## 18. ACCEPTANCE CHECKLIST

- [x] `util/` has zero Android imports in the new file (AD-11).
- [x] The date rule is pure Java and unit-tested, including the midnight boundary to the millisecond.
- [x] The transaction re-reads the store **inside** itself (contract step 1).
- [x] An already-completed store is refused as `ALREADY_COUNTED`, not thrown.
- [x] A store with a history row but a PENDING status is also refused.
- [x] The UNIQUE index violation is caught and reported, not crashed.
- [x] The store is flipped with a **targeted UPDATE**, not `@Update`.
- [x] The clock is read **once** and used for both the timestamp and the date key.
- [x] A failed store update **throws**, so Room rolls the whole transaction back.
- [x] `CompletionRepository` still has **no plain insert** — `completeStore` is the only write.
- [x] The dialog passes an **id**, and re-reads the store from Room.
- [x] Both buttons disable while the write runs.
- [x] A rotation mid-write cannot produce a second completion.
- [x] The confirm action is gated on `ARRIVED` alone — never on a small distance.
- [x] Nothing completes a store without an explicit YES.
- [x] The navigation session ends on success, so the same store is not offered again.
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
| Build | **BUILD SUCCESSFUL in 4m** — 42 tasks, all executed (clean tree) |
| Tests | **300 passing, 0 failures, 0 errors, 0 skipped** across 21 classes (277 before this phase, **+23 new**) |
| New tests | `DateKeyHelperTest` 23 |
| APK | **9,703,485 bytes** (debug, clean build) |
| Database schema | **still only `1.json`** — a DAO does not change the schema |
| Kotlin files | **0** |
| Java sources | 68 main · 21 test |
| New permissions | **0** — the merged manifest is unchanged from 4.3 (8 entries) |
| New runtime strings | 10 (157 → **167** total) |

### What was checked in the merged manifest

Unchanged from 4.3 and 4.4 — 8 entries, and `ACCESS_BACKGROUND_LOCATION` is still only the comment text:

```
ACCESS_COARSE_LOCATION · ACCESS_FINE_LOCATION · ACCESS_NETWORK_STATE
FOREGROUND_SERVICE · FOREGROUND_SERVICE_LOCATION · INTERNET
POST_NOTIFICATIONS · RECEIVE_BOOT_COMPLETED
```

### What was checked about the schema

```bash
ls app/schemas/com.cph.alpharoute.data.db.AppDatabase/
# 1.json
```

One file, as before. **A DAO is not a schema change** — `CompletionTransactionDao` was registered in
`AppDatabase` and `DATABASE_VERSION` stayed at 1. No `Migration` was written because none was needed.

---

## 20. STOP POINT

**Phases 5.2 and 5.3 are code complete and the build is verified. They are NOT verified on a device.**

This is the phase that writes. Do not begin Phase 6 until **§15 above has been run**, and specifically:

1. **§15.1 step 4** — that NO changes nothing at all. This is the guarantee the entire project exists to
   provide, and it is the one thing a JVM test cannot prove.
2. **§15.2** — that a second completion is refused, and that a completed store cannot be deleted.
3. **§15.5 step 15** — that a rotation mid-write cannot produce two completions.

Phase 6 reads these numbers back and shows them. Building a tracker on top of an unverified write would
mean two layers of unverified behaviour with no way to tell which one is wrong.

# AlphaRoute — Phase 2.2 Store CRUD Operations

**Project title:** Alfamart Route Tracker
**Version:** 2 — Local Database — **Phase:** 2.2 Store CRUD Operations
**Repository root:** `C:/Users/Admin/OneDrive/Documents/custom system/AlphaRoute`
**Date:** 2026-09-25
**Prerequisites:** Phases 0.1–0.3, 1.1–1.3, 2.1 · Phase 0.2 AD-5/AD-6/AD-11
**Status:** 🔄 **Code complete and VERIFIED — 19/19 unit tests pass, build succeeds.** Awaiting your confirmation run.

---

## 1. CURRENT VERSION AND PHASE

| Item | Value |
| --- | --- |
| Version | 2 — Local Database |
| Phase | 2.2 — Store CRUD Operations |
| Files created | 5 |
| Files modified | 2 |
| Files deleted | 0 |
| Database version | **1** — unchanged. This phase adds no schema. |

---

## 2. OBJECTIVE

Make the store operations complete and safe:

- Create, read, update and delete a store
- Delete **pending** stores only — completed stores are protected
- Find by id, by code, and by route number
- List pending stores and completed stores
- Validate every field before anything is written

No UI. This phase completes the data layer; the screens that call it arrive in Phases 3.3, 3.5 and 3.6.

---

## 3. UNDERSTANDING CHECKLIST

- [x] Java only, no Kotlin. No coroutines.
- [x] All database work stays on the single disk thread.
- [x] Completed-store deletion is protected by **two independent guards**.
- [x] Blank-only values are invalid; every text field is trimmed before storage.
- [x] Coordinates outside the globe are rejected.
- [x] Duplicate store code and duplicate route number are reported as ordinary outcomes, never crashes.
- [x] No store data, coordinate, code or count is invented anywhere.
- [x] No UI, no permission, no new dependency, no schema change.

---

## 4. FEATURES INCLUDED (in this phase)

| Feature | Detail |
| --- | --- |
| **`StoreValidator`** | Pure-Java field rules: required, maximum length, coordinate range, and the single definition of "trimmed" |
| **`StoreFieldError`** | `REQUIRED` / `TOO_LONG` / `INVALID_COORDINATES` — codes, not message strings |
| **`StoreValidationResult`** | One error slot per field, so every problem is reported at once |
| **`findByCode` / `findByRouteNumber`** | Added to `StoreRepository`, with trimming applied to the input |
| **A trimming invariant** | `StoreRepository` trims every text field before writing, whatever the caller did |
| **A coordinate backstop** | The repository refuses an impossible coordinate with `WriteOutcome.INVALID_DATA` |
| **JVM unit tests** | 19 tests proving the validation rules — no emulator needed |

---

## 5. FEATURES NOT INCLUDED YET

- No UI. The Add/Edit sheet is Phase 3.3, Store Details is 3.5, Edit and Delete are 3.6.
- No ViewModel. `StoreViewModel` arrives with the screens, where it can also own the asynchronous half of the duplicate check.
- No completion write path — the atomic completion transaction is Phase 5.3.
- No tracker calculations, no map, no location, no routing, no navigation.
- No Firebase transport.

---

## 6. ASSUMPTIONS

| # | Assumption | Impact if wrong |
| --- | --- | --- |
| A3 | `area` is free text, required, at most 60 characters. | One constant in `StoreValidator`. |
| **A41** | **Maximum lengths: route number 20, store code 30, store name 100, area 60.** Your specification sets no limits except on `area`. | Unbounded text fields would let a pasted document into a store name and break the list layouts. All four limits are constants in one class. |
| A42 | **Validation is split into a synchronous half and an asynchronous half.** Field rules run instantly in `StoreValidator`; the duplicate check needs the database and is reported by `StoreRepository`. | This is why a blank field is reported without a database round trip, and a real duplicate is still caught. §13.3 explains it. |
| A43 | **`WriteOutcome.INVALID_DATA` was added** to the Phase 2.1 enum. | The repository needs a distinct result for its own backstop, rather than overloading `FAILED`. |
| A44 | **The validator reports error codes, not messages.** | `domain/` is pure Java with no Android imports (AD-11), so it cannot reach `R.string`. The screen maps a code to a message. |

---

## 7. FILES TO CREATE (5)

| Path | Purpose |
| --- | --- |
| `domain/StoreFieldError.java` | **New.** What is wrong with one field, as a code |
| `domain/StoreValidationResult.java` | **New.** One error slot per field |
| `domain/StoreValidator.java` | **New.** The rules, and the single definition of `trimToEmpty` |
| `app/src/test/java/com/cph/alpharoute/domain/StoreValidatorTest.java` | **New.** 19 JVM tests |
| `docs/PHASE_2.2_STORE_CRUD.md` | **New.** This record |

---

## 8. FILES TO MODIFY (2)

| # | Path | Status | What changed |
| --- | --- | --- | --- |
| 1 | `data/repository/WriteOutcome.java` | **Modified** | Added `INVALID_DATA` — the repository's own backstop for an impossible coordinate or blank text |
| 2 | `data/repository/StoreRepository.java` | **Modified** | Added `findByCode`, `findByRouteNumber`, and the private `normalise` helper that trims text and checks coordinates before any write |

**Inspected and left unmodified:** everything else. No schema change, so `AppDatabase.DATABASE_VERSION`
stays at 1 and the exported `1.json` is untouched.

---

## 9. DEPENDENCIES

**None added.** This phase is pure Java plus the Room layer that already exists.

---

## 10. PERMISSIONS AND MANIFEST CHANGES

**None.**

---

## 11. SETUP INSTRUCTIONS

1. Let Gradle sync (nothing new to fetch).
2. **Build → Clean Project**, then **Build → Rebuild Project**.
3. To run the unit tests: in Android Studio, right-click
   `app/src/test/java/com/cph/alpharoute/domain/StoreValidatorTest.java` → **Run**, or from a terminal run
   `./gradlew :app:testDebugUnitTest`.

---

## 12. COMPLETE CODE

All 7 files are written into the project on disk and attached to this message. The files worth reading
first:

1. `domain/StoreValidator.java` — the rules
2. `domain/StoreValidationResult.java` — why the error slots are package-private
3. `data/repository/StoreRepository.java` — the `normalise` helper and the two new lookups
4. `app/src/test/java/…/StoreValidatorTest.java` — the rules, proven

---

## 13. CODE EXPLANATION

### 13.1 Where each CRUD operation lives

| Your specification | Implementation | Added in |
| --- | --- | --- |
| Create store | `StoreRepository.insert()` + `StoreValidator` for field rules | 2.1 + **2.2** |
| Read stores | `StoreRepository.observeAll()` | 2.1 |
| Update store | `StoreRepository.update()` | 2.1 + **2.2** |
| Delete pending store | `StoreRepository.delete()` | 2.1 + **2.2** |
| Find store by ID | `StoreRepository.findById()` | 2.1 |
| **Find store by code** | `StoreRepository.findByCode()` | **2.2** |
| **Find store by route number** | `StoreRepository.findByRouteNumber()` | **2.2** |
| List pending stores | `StoreRepository.observePending()` | 2.1 |
| List completed stores | `StoreRepository.observeCompleted()` | 2.1 |
| Completed-store deletion protected | `delete()` pre-check **and** the AD-6 foreign key | 2.1 |
| **Field validation** | `StoreValidator` | **2.2** |

### 13.2 Completed-store deletion is protected twice, on purpose

```java
if (completionHistoryDao.countForStore(storeId) > 0) {
    outcome = WriteOutcome.PROTECTED_BY_HISTORY;      // guard 1: a clear reason
}
...
} catch (SQLiteConstraintException e) {
    outcome = WriteOutcome.PROTECTED_BY_HISTORY;      // guard 2: the database refused
}
```

The first guard exists so the user gets a sentence rather than a raw SQL error. The second exists
because the history is the one thing in this app that cannot be reconstructed: if the pre-check is ever
bypassed — a future refactor, a race, a direct DAO call — `onDelete = RESTRICT` (AD-6) still refuses.
Two guards that agree, deliberately, rather than one that has to be perfect.

### 13.3 Why validation is split in two

This is the design decision worth understanding in this phase.

```
User taps Save
      │
      ├─ 1. StoreValidator.validate(...)          SYNCHRONOUS - microseconds, no I/O
      │        blank? too long? coordinate off the globe?
      │        └─ any error → show it under the field. Done. No database touched.
      │
      └─ 2. StoreRepository.insert(...)           ASYNCHRONOUS - disk thread
               duplicate store code? duplicate route number?
               └─ DUPLICATE_STORE_CODE / DUPLICATE_ROUTE_NUMBER
                  → show it under the matching field
```

The split exists because the two checks have genuinely different costs. A blank field should be reported
the instant the user taps Save, with no spinner. Whether a code is already taken is a database question,
and the database is asynchronous by design (no main-thread I/O). Running them in this order gives the
fast feedback where it is cheap and the authoritative answer where it is needed.

Neither check is redundant: the database's `UNIQUE` indices are still the real guarantee, and the
pre-checks exist only to turn a raw constraint error into a sentence a user can act on.

### 13.4 Trimming happens in exactly one place, and is enforced twice

`StoreValidator.trimToEmpty` is the only definition of "trimmed" in the app. It is applied in two
places for two different reasons:

- **In the validator**, before measuring length — so `"  SM-001  "` counts as six characters and is
  accepted, rather than being rejected as too long or failing a blank check it should pass.
- **In `StoreRepository.normalise`**, before writing — so stored text is always clean even if a caller
  forgot to trim. This is an invariant of the data layer, not a courtesy.

The important part is that both call the *same* method. Two similar-but-separate trim implementations
would be free to drift, and the resulting bug ("the field looks blank in the app but the database
disagrees") is miserable to track down.

### 13.5 The validator reports codes, not messages

```java
public enum StoreFieldError { REQUIRED, TOO_LONG, INVALID_COORDINATES }
```

`domain/` is pure Java with zero Android imports (AD-11), so it cannot reach `R.string`. It reports
*what kind* of problem a field has and the screen decides how to word it. Two things follow, and both
are worth the small extra indirection:

- The rules are testable on the JVM in milliseconds — 19 tests, no emulator, no device.
- The wording can change, or be translated, without touching a single validation rule.

### 13.6 Why every field is validated even after one fails

`validate` checks all six inputs and returns all the problems, rather than stopping at the first. A form
that reveals one error at a time forces the user through as many round trips as it has mistakes. The
Phase 0.3 screen plan shows inline errors under each field, which is exactly what this supports.

### 13.7 The `normalise` backstop and `INVALID_DATA`

The repository refuses a coordinate outside the globe with `WriteOutcome.INVALID_DATA`. In normal use
this never fires, because `StoreValidator` already caught it and the user was told which field to fix.
It exists so that a caller which bypassed validation cannot write a marker to an impossible place — a
bug should surface as a rejected write, not as a pin floating in the ocean.

---

## 14. DATA FLOW FOR THIS PHASE

```
Add / Edit Store sheet  (Phase 3.3 - not built yet)
      │
      ▼
StoreValidator.validate(routeNumber, storeCode, storeName, area, lat, lng)
      │  pure, synchronous
      ├─ invalid → StoreValidationResult with per-field codes → inline errors, nothing written
      └─ valid
             │
             ▼
      StoreRepository.insert(store)  |  .update(store)  |  .delete(storeId)
             │  on AppExecutors.diskIO()
             ├─ normalise()                  trim text, check coordinates
             ├─ duplicate pre-checks         isStoreCodeTaken / isRouteNumberTaken
             ├─ DAO write                    UNIQUE indices are the real guarantee
             ├─ COMMIT
             ├─ notifySyncSafely()           sync hook, after the commit, failure-proof
             └─ postToMain(WriteOutcome)
                     │
                     ▼
              the sheet shows the outcome, or closes on SUCCESS
```

Reads take the other branch: `observePending()` / `observeCompleted()` return `LiveData` that Room
re-runs whenever the `stores` table changes.

---

## 15. MANUAL TEST PROCEDURE

### 15.0 Already verified automatically (2026-09-25)

`./gradlew :app:testDebugUnitTest :app:assembleDebug` was run on this machine with Gradle 9.4.1 / JDK 21
and returned **`BUILD SUCCESSFUL in 59s`** (41 tasks: 14 executed, 27 up-to-date).

| Evidence | Result |
| --- | --- |
| **`:app:testDebugUnitTest`** | ran — **the unit tests executed** |
| **`StoreValidatorTest`** | **19 tests, 0 failures, 0 errors — in 0.004 seconds** |
| `ExampleUnitTest` | 1 test, 0 failures (the Android Studio template, replaced in Phase 10.2) |
| **Total** | **20 tests, 0 failures, 0 errors, 0 skipped** |
| `:app:compileDebugUnitTestJavaWithJavac` | ran — the test source compiled |
| `:app:compileDebugUnitTestKotlin` | **NO-SOURCE** — the tests are Java, like the rest of the project |
| `:app:assembleDebug` | ran — the app still packages |
| `:app:compileDebugKotlin` | **NO-SOURCE** — still zero Kotlin source files |

**19 tests in four milliseconds, with no emulator and no device.** That is the entire payoff of keeping
`domain/` as pure Java (AD-11): the rules this app's correctness rests on are provable instantly, on any
machine, before a single screen exists. A validator bug found here costs seconds; the same bug found on
a phone in the field costs a corrupted route.

The test cases cover: the fully valid form · whitespace trimming and acceptance · each blank field ·
blank-only-whitespace (the rule the specification states explicitly) · null inputs not crashing · all
problems reported at once · each field's length limit, at the limit and one over · each coordinate bound
above and below · the boundary values being inclusive · and `trimToEmpty` on null, on padding, and on
inner whitespace.

### 15.1 Run the unit tests yourself

1. In Android Studio, open `app/src/test/java/com/cph/alpharoute/domain/StoreValidatorTest.java`.
2. Click the green arrow beside the class name → **Run 'StoreValidatorTest'**.
3. All tests must pass. The Run panel should show **19 tests, 0 failures**.
4. If any test fails, copy the failure text — a failing validator test means a store could be saved with
   bad data, which is exactly what this phase is for.

### 15.2 Confirm the app still runs

5. Run the app. It must open on the Dashboard with no crash.
6. The Dashboard still shows its Phase 1.3 placeholder cards — this phase added no UI, so nothing on
   screen changes. That is correct.

### 15.3 Confirm nothing was written

7. Open **App Inspection → Database Inspector** and check the `stores` table.
8. It must still be **empty** — there is no way to add a store from the UI yet.

### 15.4 Confirm the schema did not change

9. Confirm `app/schemas/com.cph.alpharoute.data.db.AppDatabase/1.json` is unchanged and still the only
   file in that folder. This phase added no schema, so there must be no `2.json`.

**Report any failure with the step number and the exact text.**

---

## 16. EXPECTED RESULT

- 19 unit tests pass on the JVM, with no emulator.
- The app builds and runs unchanged.
- The `stores` table stays empty — there is still no way to add a store from the UI.
- The database schema is still version 1, and no migration file was created.
- Still no permission, no new dependency, no Kotlin.

---

## 17. COMMON ERRORS AND FIXES

| Symptom | Cause | Fix |
| --- | --- | --- |
| `Cannot resolve symbol 'StoreFieldError'` | The domain package did not compile | Re-sync Gradle, then rebuild |
| Tests do not appear in the Run panel | The test is not under `src/test/`, or Gradle has not synced | It must be at `app/src/test/java/com/cph/alpharoute/domain/StoreValidatorTest.java` |
| `No tests found for given includes` | Running the wrong Gradle task | Use `:app:testDebugUnitTest`, not `:app:test` |
| A test fails on `coordinatesAtTheExactBoundariesAreAccepted` | The bounds in `GeoConstants` were changed to exclusive | The poles and antimeridian are real places — the bounds must be inclusive |
| A test fails on the length cases | A `MAX_*_LENGTH` constant was edited | The test reads the constants, so a failure means the boundary logic broke, not the number |
| `WriteOutcome.INVALID_DATA` appearing in a real flow | A caller bypassed `StoreValidator` | That is the backstop doing its job — find the caller that skipped validation |
| Store text is stored with leading spaces | A write went straight to the DAO | All writes must go through `StoreRepository`, which trims |

---

## 18. ACCEPTANCE CHECKLIST

- [ ] Gradle sync completes.
- [ ] Clean build succeeds.
- [x] **All 19 `StoreValidatorTest` cases pass** (§15.0 — verified: 0 failures, 0 errors, 0.004 s)
- [ ] The app launches on the Dashboard with no crash.
- [ ] The `stores` table is still empty (§15.3).
- [ ] `app/schemas/…` still contains only `1.json` — no schema change (§15.4).
- [ ] Confirmed: no new permission, no new dependency.
- [ ] Confirmed: still **no Kotlin source file** anywhere in the project.

---

## 19. UPDATED PROJECT STATE

**COMPLETED PHASES**
- ✅ 0.1 Requirements Review (Rev 5, FINAL) · ✅ 0.2 Architecture · ✅ 0.3 Screen Plan
- ✅ 1.1 Project Creation · ✅ 1.2 Theme and Base Navigation · ✅ 1.3 Dashboard Static UI
- ✅ 2.1 Room Database Foundation
- 🔄 **2.2 Store CRUD Operations** — *awaiting your test*

**CURRENT PHASE**
- Phase 2.2 — awaiting the unit-test run and the on-device check

**CURRENT DATABASE VERSION**
- **1.** Unchanged this phase. Three tables: `stores`, `completion_history`, `app_settings`.

**CURRENT FILES** (changed this phase)

```
app/src/main/java/com/cph/alpharoute/domain/StoreFieldError.java        (NEW)
app/src/main/java/com/cph/alpharoute/domain/StoreValidationResult.java  (NEW)
app/src/main/java/com/cph/alpharoute/domain/StoreValidator.java         (NEW)
app/src/test/java/com/cph/alpharoute/domain/StoreValidatorTest.java     (NEW - 19 tests)
app/src/main/java/com/cph/alpharoute/data/repository/WriteOutcome.java  (MODIFIED - INVALID_DATA)
app/src/main/java/com/cph/alpharoute/data/repository/StoreRepository.java (MODIFIED - lookups + normalise)
docs/PHASE_2.2_STORE_CRUD.md                                            <- created this phase
```

**WORKING FEATURES** (verified)
- ✅ **19/19 `StoreValidatorTest` cases pass** — 0 failures, 0 errors, in 0.004 s on the JVM (verified 2026-09-25)
- ✅ **Total suite: 20 tests, 0 failures, 0 errors, 0 skipped**
- ✅ **Build succeeds** — `:app:assembleDebug` also ran clean
- ✅ **Still zero Kotlin source files** — `compileDebugKotlin` and `compileDebugUnitTestKotlin` both `NO-SOURCE`
- ⏳ **On-device checks** — app launches unchanged, `stores` table still empty, schema still `1.json` — awaiting your §15.2–15.4

**KNOWN ISSUES**
- None blocking.
- **Still nothing on screen can create a store.** That is correct — the data layer is complete, the UI
  arrives in Phase 3.3.
- **The Firebase question from Phase 2.1 §21 is still open** (options A, B or C). Nothing in this phase
  depended on the answer.
- The project is **not under version control**. Still the strongest recommendation in the project.
- Trademark note: "Alfamart" is a third-party brand; fine for a private solo tool (D-5).

**PENDING PHASES** (not implemented)
- **2.3 Settings storage** — next
- 3.1 osmdroid map display · 3.2 Current location · 3.3 Manual pin creation · 3.4 Marker loading ·
  3.5 Store details · 3.6 Edit/delete · 3.7 Home pin
- 4.1 Routing service preparation · 4.2 Route preview · 4.3 Navigation hand-off + arrival monitoring ·
  4.4 State recovery
- 5.1 Arrival-radius check · 5.2 Completion dialog · 5.3 Atomic completion transaction
- 6.1 Live tracker · 6.2 Daily history list · 6.3 Daily completion order · 6.4 Completion details ·
  6.5 Consistency check
- 7.1 Nearest pending store · 7.2 Recalculate after completion · 7.3 Home-aware heuristic · 7.4 No pending stores
- 8.1 Routing service review · 8.2 Road-based next store · 8.3 Multi-stop optimization · 8.4 Complete route preview
- 9.1 Local export · 9.2 Local import · 9.3 Optional cloud sync
- 10.1 Error handling · 10.2 Automated and manual testing · 10.3 Release preparation

---

## 20. STOP POINT

Phase 2.2 is complete. **7 files touched: 5 created, 2 modified.**

**The three things worth knowing:**

1. **Completed-store deletion is protected twice, and they agree.** A pre-check gives the user a
   sentence; the AD-6 foreign key is the guarantee. If the pre-check is ever bypassed, the database
   still refuses.
2. **Validation is deliberately split.** Field rules are synchronous and instant (no spinner, no
   database); the duplicate check is asynchronous because the database is. The user gets fast feedback
   where it is cheap and the authoritative answer where it is needed.
3. **`StoreValidator` reports codes, not messages**, because `domain/` cannot reach `R.string`. That is
   what makes the rules testable on the JVM — **19 tests, no emulator**, which is the only real
   verification available before there is a UI.

**Please run §15.1** — the 19 unit tests — and report. A failing validator test means a store could be
saved with bad data, so it is worth running before we build the screens on top.

**Also still open from Phase 2.1:** the Firebase question (§21 there) — **A** wire Firestore up next,
**B** keep it roadmapped to Version 9, or **C** do the file export/import first. Nothing so far depended
on it, and nothing in Phase 2.3 will either.

**Next phase on your instruction: Phase 2.3 — Settings Storage** — Home coordinates, the Home
configured flag, the arrival radius, the last selected store and the last update timestamp, all stored
and read through `SettingsRepository`.

**Phase complete. Do not proceed to the next phase until all acceptance criteria pass.**

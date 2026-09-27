# PHASE 4.4 — State Recovery

> **Status: code complete, build verified.** Device testing is §15 and has not been run yet.

---

## 1. CURRENT VERSION AND PHASE

| | |
| --- | --- |
| Version | 4 — Routing and navigation |
| Phase | **4.4 — State recovery** |
| Previous phase | 4.3 — Navigation hand-off and arrival monitoring ✅ |
| Next phase | 5.1 — Arrival confirmation dialog |
| Database version | **1 — unchanged.** No schema change, no `Migration`, no new entity |
| Kotlin files | **0** |

---

## 2. OBJECTIVE

Phase 4.3 made navigation work **while everything goes right**. This phase is what happens when it does
not: the app is killed mid-drive, the user leaves and comes back, notification permission is refused,
location permission is revoked while driving, the service is reclaimed by the system, and the drive is
cancelled.

The deliverable is one promise: **a drive is never silently lost, and a dead drive is never offered.**
Nothing here changes a completion count — that is still Phase 5.

---

## 3. UNDERSTANDING CHECKLIST

- ✅ The app cannot know whether the user is still driving. `lastSelectedStoreId` (AD-10) is written when
  navigation starts and cleared when it ends, so a leftover value and a live session look identical.
- ✅ Therefore it must **ask**, never resume on its own — and never discard silently either.
- ✅ Android 12+ forbids starting a foreground service from the background, so **the resume must be a
  tap**. The check finds the session; only the tap starts the service (A74).
- ✅ "Missing position" has two different causes — permission gone vs. service stopped — and they need
  two different actions. Offering the wrong one is a button that fails.
- ✅ Recovery logic is a **decision**, so it is pure Java in `domain/` and tested on the JVM (AD-11).
- ✅ Ending and discarding are the same operation, and only the user performs it.

---

## 4. FEATURES INCLUDED (in this phase)

1. **A cold-start resume prompt** — "Resume navigation?" with the store named, and Resume / Discard.
2. **A freshness rule** — a recorded session is offered for **four hours**, then cleared without asking.
3. **Stale-reference cleanup** — a session too old to offer is removed from Room on the next launch and
   cannot be re-judged.
4. **A dangling-reference guard** — a recorded store id whose store was deleted clears itself rather than
   offering a drive to nowhere.
5. **Location-lost recovery** — the Navigation Active sheet offers "Grant permission" when the permission
   was revoked mid-drive.
6. **Monitoring-stopped recovery** — the sheet offers "Resume monitoring" when the service was reclaimed
   by the system, and restarting it does **not** re-record the reference.
7. **A clock-moved-backwards guard** — a future timestamp is treated as unusable, not as brand new.
8. **A boot path that clears rather than resumes** — carried over from 4.3 (A72), still a stated
   limitation.

---

## 5. FEATURES NOT INCLUDED YET

| Feature | Phase |
| --- | --- |
| The "Store completed?" dialog | **5.2** |
| The atomic completion transaction | **5.3** |
| Resuming monitoring after a **reboot** | **never** — illegal on Android 12+, see §6 (A72) |
| Auto-resuming after a low-memory kill without a tap | **never** — illegal, and not the app's decision |
| Restoring the in-flight route polyline after a restart | **not planned** — the line is a preview, not state |
| A "recently ended" list | **not planned** — nothing here needs it |

---

## 6. ASSUMPTIONS

| # | Assumption |
| --- | --- |
| A75 | A recorded session older than **four hours** is a forgotten leftover, not a live drive. Interrupted delivery runs are minutes to an hour. |
| A76 | The user's answer to "Resume navigation?" is the only thing that may start the service. The app never decides a drive is still happening. |
| A77 | `app_settings.lastUpdated` is a usable freshness clock, because the settings row is touched on every session start and end. No new field is added. |
| A78 | A repeated "Resume monitoring" tap must be harmless. `restartMonitoring()` starts the service only; it never rewrites the session or the reference. |
| A79 | Revoking location permission mid-drive **must not** end the session. The drive is still real; only the watching stopped. The session stays open so the user can re-grant. |

---

## 7. FILES TO CREATE (2)

| # | File | Purpose |
| --- | --- | --- |
| 1 | `domain/RecoveryState.java` | `NOTHING` / `OFFER_RESUME` / `STALE`. Pure Java. |
| 2 | `domain/NavigationRecovery.java` | The four-hour freshness rule and the refusal cases. Pure Java. |

Plus the test file `app/src/test/.../NavigationRecoveryTest.java`.

---

## 8. FILES TO MODIFY (7)

| # | File | Change |
| --- | --- | --- |
| 1 | `navigation/NavigationSessionManager.java` | `checkForResumableSession()`, `resume()`, `restartMonitoring()`; `startService()` now returns `boolean`. |
| 2 | `navigation/NavigationSession.java` | `monitoring` flag + `isMonitoring()` / `setMonitoring()`. |
| 3 | `navigation/NavigationMonitorService.java` | Sets the monitoring flag in `onCreate` / `onDestroy`. |
| 4 | `ui/route/NavigationActiveBottomSheet.java` | `currentDistance`, `renderRecovery()`, `requestLocationPermission()`, `resumeMonitoring()`. |
| 5 | `res/layout/sheet_navigation_active.xml` | `btn_nav_recover`. |
| 6 | `res/values/strings.xml` | 9 new strings. |
| 7 | `ui/map/MapFragment.java` | `checkForResumableSession()`, the resume dialog, `resumeSession()`, `discardSession()`. |

---

## 9. DEPENDENCIES

| Dependency | Status |
| --- | --- |
| Everything from 4.3 | Reused. Not one new library. |
| `MaterialAlertDialogBuilder` | Already present (Phase 3.6). |
| openrouteservice | **Not used by this phase.** No network call at all. |

**No new dependency, no new permission, no new API key, no schema change.**

---

## 10. PERMISSIONS AND MANIFEST CHANGES

**None.** This is the second phase in a row with no manifest change, and that is deliberate: every
recovery path here re-uses a permission that 4.3 already declared.

| | |
| --- | --- |
| New permissions | **0** |
| Removed permissions | **0** |
| New components | **0** |
| `ACCESS_BACKGROUND_LOCATION` | Still **not declared**. The resume happens in the foreground, from a tap. |

> ⚠️ **Trap.** Grepping the merged manifest for `ACCESS_BACKGROUND_LOCATION` still returns a hit — it is
> the **XML comment text**. Unchanged from 4.3.

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

### The problem is that two different situations look the same

`lastSelectedStoreId` is written when navigation starts and cleared when it ends (AD-10). When the app
opens, the value is either a **live drive** the user is in the middle of, or a **leftover** from a drive
that ended days ago when the process was killed. The database cannot tell them apart; only the clock can,
and only imperfectly.

So there are three answers, and `RecoveryState` is exactly those three:

| State | Meaning | What the app does |
| --- | --- | --- |
| `NOTHING` | No session recorded | Nothing |
| `OFFER_RESUME` | Recorded, and recent | Asks |
| `STALE` | Recorded, and too old | Clears it silently |

### Four hours, and why the number is a judgement

`NavigationRecovery.RESUME_WINDOW_MILLIS` is four hours. Interrupted delivery runs — the case this exists
for — are minutes to an hour. Four hours covers one comfortably while making "you were navigating
yesterday" impossible.

The trade is asymmetric on purpose. **Asking wrongly** costs the user a confusing prompt they must reason
about before dismissing. **Not asking** costs two taps. So the window is set short, and the test asserts
the boundary to the millisecond in both directions.

### One hour of code, four ways to refuse

`evaluate()` returns `STALE`, not a guess, for:

- a **missing** timestamp — cannot tell how old it is, so it is not offered;
- a **negative** timestamp — same reasoning;
- a **future** timestamp — the clock moved backwards (date change, mid-session time sync). Subtracting
  would give a negative age, which reads as "brand new" and would offer a resume for something arbitrarily
  old. This is the subtle one;
- a **non-positive** store id — Room's `autoGenerate` keys start at 1, so it cannot be a real store.

Every refusal points the same way: **clear it**. The worst case of clearing wrongly is starting navigation
again, which is two taps. The worst case of keeping wrongly is a prompt about nothing.

### The check finds; the tap starts

```java
checkForResumableSession(...)   // reads Room, reports a state, changes nothing else
resume(context, onResumed)      // ← only ever called from the dialog's Resume button
```

`checkForResumableSession()` **cannot** start the service. It runs as the app is opening, which may be a
cold start the user did not ask for, and Android 12+ forbids a background foreground-service start. Making
the check do it would throw on those devices. So the check is a question and the tap is the answer — which
is also the honest design, because "was I still driving?" is a question only the user can settle.

The Map tab calls it once per launch. `recoveryChecked` stops a rotation from re-asking, and is
deliberately **not** saved in `onSaveInstanceState`: a rotation is not a new launch, but a process restart
is, and that is exactly when the question is worth asking.

### `resume()` re-reads the store rather than trusting the id

The recorded id is a reference, not a fact. The store can have been deleted — Phase 3.6 can delete, and
AD-6 only protects *completed* stores. Navigating to a coordinate whose store is gone would leave the sheet
with nothing to name. So `resume()` reads the store back from Room, and a missing store clears the
reference and reports `null` rather than starting a headless session.

### Restarting monitoring is not the same as starting a session

```java
start(context, store, onRecorded)   // new session: rewrites it, re-records, clears the route
restartMonitoring(context)          // only the service; the session is already correct
```

Collapsing these would be wrong twice. It would **reset the recovery timestamp**, making an old session
look fresh — defeating the whole freshness rule. And it would fire a database write for a drive the user
has not changed. So `restartMonitoring()` starts the service and touches nothing else, which also makes a
repeated tap harmless (A78).

### The service announces that it is running

`NavigationSession.monitoring` is set by the service itself, in `onCreate` and — critically — cleared in
`onDestroy` **before** `super.onDestroy()`. It is the only way the sheet can tell "the service is gone"
from "a fix has not arrived yet", and those two need different words and different buttons.

### Two failures, two actions, decided by permission

`renderRecovery()` triggers on a missing distance, because a granted permission with a working provider
produces a fix within seconds — so no distance means something broke. Which action to offer is then
decided by **permission state, not by the distance**:

- **Permission gone** → *Grant permission*. Nothing else can work, so offering "Resume monitoring" would
  be a button that fails.
- **Permission fine, service not running** → *Resume monitoring*. This one a tap genuinely fixes.

If both are fine, the button is hidden. A missing fix then means the few seconds before the first reading,
which is patience, not a problem.

### The permission launcher is a field, again

```java
private final ActivityResultLauncher<String[]> permissionLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                granted -> renderRecovery());
```

`registerForActivityResult` must run before the fragment is created; a launcher built in a method would
throw. Same reason the Map tab holds its launcher this way, and the same trap the phase skill records.

---

## 14. DATA FLOW FOR THIS PHASE

```
App opens (Map tab, once per launch)
      ↓
MapFragment.checkForResumableSession()
      ↓
NavigationSessionManager.checkForResumableSession(cb)
      ├─ settingsRepository.getNow(settings)
      ├─ NavigationRecovery.evaluate(id, lastUpdated, now)
      │        ├─ NOTHING        → report NOTHING, write nothing
      │        ├─ OFFER_RESUME   → report OFFER_RESUME
      │        └─ STALE          → setLastSelectedStoreId(null), report NOTHING
      ↓
OFFER_RESUME → MaterialAlertDialogBuilder "Resume navigation?"
      ├─ Discard → NavigationSessionManager.stop()
      │              ├─ session.end()
      │              ├─ setLastSelectedStoreId(null)   ← the drive is gone for good
      │              └─ stopService()
      └─ Resume  → NavigationSessionManager.resume()
                     ├─ getNow → lastSelectedStoreId        (re-read, not cached)
                     ├─ storeRepository.findById(id)
                     │      ├─ null → setLastSelectedStoreId(null), report null
                     │      └─ store → start(context, store, null)
                     │                    ├─ session.begin(...)
                     │                    ├─ setLastSelectedStoreId(id)  ← timestamp refreshed
                     │                    └─ startForegroundService(...)  ← legal: user just tapped
                     └─ open the Navigation Active sheet

Mid-drive, location permission revoked
      ↓
NavigationMonitorService loses the fix → no distance in the session
      ↓
NavigationActiveBottomSheet.renderRecovery()
      ├─ !hasLocationPermission → "Grant permission" → permissionLauncher
      └─ hasPermission && !session.isMonitoring() → "Resume monitoring"
                                                    → restartMonitoring()  ← service only
```

**The network is nowhere in this flow, and nothing writes a completion.**

---

## 15. MANUAL TEST PROCEDURE

All of §15 needs a device. Steps 1–4 can be done at a desk; 5 onwards need movement.

### 15.1 The cold-start resume prompt

1. Pin a store, tap its marker, **Get directions**, **Start navigation**.
2. Open the notification shade, confirm the ongoing notification is there.
3. **Swipe the app from recents** — do not tap End navigation. This is the low-memory-kill case.
4. Open the app again. The Map tab **must** ask *"Resume navigation?"* and name the store.
5. Tap **Discard**. It must not ask again on the next launch, and no notification may remain.

### 15.2 Resuming

6. Start a session, kill the app from recents, reopen it.
7. Tap **Resume**. The Navigation Active sheet must open, the nav bar must reappear, and the ongoing
   notification must return. The distance must be live.

### 15.3 The freshness rule

8. Start a session, kill the app, then set the device clock forward by **more than four hours**.
9. Reopen the app. It must **not** ask to resume, and the recorded session must be gone for good — set the
   clock back and reopen to confirm it does not come back.

### 15.4 A deleted store

10. Start a session for a store, kill the app, reopen it and **Discard** so no prompt is pending.
11. Delete that store (Phase 3.6), then in a fresh launch after starting a session for it — reopen and tap
    **Resume** on a store that no longer exists. The app must say *"That store is no longer available."*
    and must not open an empty sheet.

### 15.5 Location revoked mid-drive

12. Start a session. In Settings → Apps → Alfaroute → Permissions, switch **Location** off while the drive
    is running.
13. Open the Navigation Active sheet. It must offer **Grant permission**, not "Resume monitoring".
14. Tap it, grant, and confirm the distance returns and the session was **not** ended.

### 15.6 Service reclaimed

15. Start a session, then kill it with
    `adb shell am stop-service com.cph.alpharoute/.navigation.NavigationMonitorService`.
16. Open the sheet. It must offer **Resume monitoring**. Tapping it must bring the notification back and
    **must not** change the recorded timestamp — reopen the app and confirm it still does not over-offer.

### 15.7 Nothing here counts

17. After every step above, **the store must still be PENDING** and no history row may exist. Recovery
    restores monitoring; it never records a completion.

---

## 16. EXPECTED RESULT

- A drive interrupted by the system is offered back on the next launch, named, with one tap to resume.
- A drive from yesterday is never mentioned.
- A recorded session whose store was deleted clears itself instead of offering a dead destination.
- Both mid-drive failures — lost permission and a reclaimed service — are recoverable from the sheet,
  with the correct action offered for each.
- **Nothing in this phase changes a completion count.** The database guard (AD-5, AD-6) is untouched, and
  no store is marked complete here.

---

## 17. COMMON ERRORS AND FIXES

| Symptom | Cause | Fix |
| --- | --- | --- |
| No prompt after killing the app mid-drive | More than four hours passed, or the session was ended | Working as designed (§5, A75). Set the clock back and retry to confirm the rule rather than a bug. |
| Prompt appears for a drive from yesterday | `lastUpdated` was not refreshed on start | Check `SettingsRepository.setLastSelectedStoreId` writes `lastUpdated`. It does — a missing write is the bug to look for. |
| The prompt reappears on every rotation | `recoveryChecked` was not honoured | It is checked at the top of `checkForResumableSession()`. A rotation must not re-ask. |
| Crash on Resume: `ForegroundServiceStartNotAllowedException` | Resume was reached without a tap (e.g. called from `onResume`) | Every path to `resume()` is the dialog's positive button. Do not add a lifecycle caller. |
| "Resume monitoring" does nothing, no error | There is no active session, so `restartMonitoring()` returns `false` | Expected. The button is only shown when `session.isActive()` — check the session was not ended. |
| A deleted store still offers a resume | The reference was not cleared on delete | `StoreRepository.delete` calls the settings cleanup. If a store is deleted by another route, clear the reference there too. |
| The distance never returns after re-granting permission | The service was stopped, not merely blocked | Tap **Resume monitoring** as well — re-granting restores the permission, the service still has to be restarted. |
| A negative age is read as fresh | Clock moved backwards | Handled: `evaluate()` returns `STALE` for a future timestamp. If it does not, the check was removed. |

---

## 18. ACCEPTANCE CHECKLIST

- [x] `domain/` has zero Android imports in the new files (AD-11).
- [x] The freshness rule is pure Java and unit-tested, boundary included.
- [x] A future timestamp is refused, not treated as fresh.
- [x] A missing or non-positive store id is refused.
- [x] A stale reference is cleared, and reported to the caller as `NOTHING`.
- [x] The check never starts a foreground service; only a tap does (A74, A76).
- [x] `resume()` re-reads the store and tolerates it having been deleted.
- [x] `restartMonitoring()` never re-records the reference or rewrites the session (A78).
- [x] Revoking permission does not end the session (A79).
- [x] The prompt is asked once per launch and not re-asked on rotation.
- [x] No new permission, no manifest change, no schema change.
- [x] Zero Kotlin.
- [x] The build is green with all tests passing (see §19).
- [ ] **§15 has been run on a device.** ← the only unticked box

---

## 19. UPDATED PROJECT STATE

Verified on 2026-09-26 with:

```bash
./gradlew clean :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain
```

| | |
| --- | --- |
| Build | **BUILD SUCCESSFUL in 4m 8s** — 42 tasks, all executed (clean tree) |
| Tests | **277 passing, 0 failures, 0 errors, 0 skipped** across 20 classes (262 before this phase, **+15 new**) |
| New tests | `NavigationRecoveryTest` 15 |
| APK | **9,685,372 bytes** (debug, clean build) |
| Database schema | **still only `1.json`** — no version bump, no `Migration` |
| Kotlin files | **0** |
| Java sources | 64 main · 20 test |
| New permissions | **0** — the merged manifest is unchanged from 4.3 (8 entries) |
| New runtime strings | 9 (148 → **157** total) |

### What was checked in the merged manifest

Unchanged from 4.3 — 8 entries, and `ACCESS_BACKGROUND_LOCATION` is still only the comment text:

```
ACCESS_COARSE_LOCATION · ACCESS_FINE_LOCATION · ACCESS_NETWORK_STATE
FOREGROUND_SERVICE · FOREGROUND_SERVICE_LOCATION · INTERNET
POST_NOTIFICATIONS · RECEIVE_BOOT_COMPLETED
```

---

## 20. STOP POINT

**Phase 4.4 is code complete and the build is verified. It is NOT verified on a device.**

Do not begin Phase 5 until **§15 of this record AND §15 of the 4.3 record** have been run. Two things
specifically must be confirmed by the user:

1. **4.3 §15.2 step 9** — that arriving changes nothing in the database. The whole design exists to
   guarantee it, and it is the one thing a JVM test cannot prove.
2. **4.4 §15.1** — that a drive survives being killed from recents. This is the recovery this phase was
   written for.

Phase 5 is where the only database write in the app happens, so it must not start on top of unverified
navigation and unverified recovery.

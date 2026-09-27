# PHASE 4.3 — Navigation Hand-off and Arrival Monitoring

> **Status: code complete, build verified.** Device testing is §15 and has not been run yet.

---

## 1. CURRENT VERSION AND PHASE

| | |
| --- | --- |
| Version | 4 — Routing and navigation |
| Phase | **4.3 — Navigation hand-off and arrival monitoring** |
| Previous phase | 4.2 — Route preview ✅ |
| Next phase | 5.1 — Arrival-radius check |
| Database version | **1 — unchanged.** No schema change, no `Migration`, no new entity |
| Kotlin files | **0** |

---

## 2. OBJECTIVE

Phase 4.2 handed the driver to Google Maps and stopped there. This phase is everything **after** the
hand-off: knowing which store is the active destination, watching the position while Google Maps is in
front, and telling the driver when they have crossed this app's own arrival radius.

Completion itself is **not** in this phase. 4.3 stops at "you have arrived" - the YES/NO dialog and the
atomic transaction are Phase 5.

---

## 3. UNDERSTANDING CHECKLIST

- ✅ The app never renders turn-by-turn guidance (D-6/D-7). It hands off, then watches.
- ✅ A **foreground service** is the only structure that keeps receiving location updates while another
  app is in front. That is a hard Android rule, not a preference.
- ✅ Completion is gated on the arrival radius, so the arrival decision is the most consequential
  calculation in the project and is pure Java, tested on the JVM (AD-11, AD-14).
- ✅ Room stays the single source of truth (AD-4). The session holds an **id and a coordinate**, never a
  `StoreEntity`.
- ✅ `lastSelectedStoreId` (AD-10) is finally written here. It was deliberately left alone by 4.2.
- ✅ Nothing auto-completes, and nothing auto-starts navigation. Both are still taps.

---

## 4. FEATURES INCLUDED (in this phase)

1. **The arrival calculation** — haversine distance, widened by the fix's own accuracy, with refused
   input rather than a plausible zero.
2. **A navigation session** — observable, screen-agnostic, shared by the service and the UI.
3. **A foreground monitoring service** — `foregroundServiceType="location"`, with the required ongoing
   notification and the runtime permission handling that goes with it.
4. **An arrival notification** — dismissible, a different id from the ongoing one, the entry point to
   Phase 5's dialog.
5. **Screen 8 — Navigation Active** — the store, the live straight-line distance, and the arrival
   indicator.
6. **A compact active-navigation bar** on the Map tab — because screen 8 is dismissable.
7. **End navigation** — a decision, not a side effect.
8. **A boot receiver** — clears a session that did not survive a reboot. See §6 and §17.

---

## 5. FEATURES NOT INCLUDED YET

| Feature | Phase |
| --- | --- |
| The "Store completed?" dialog | **5.2** |
| The atomic completion transaction | **5.3** |
| Marking the store COMPLETED, writing history | **5.3** |
| Resuming a session after a reboot | **not planned** — illegal on Android 12+, see §6 |
| ETA, turn-by-turn, live traffic | **never** — D-6/D-7, that is Google Maps' job |
| `ACCESS_BACKGROUND_LOCATION` | **never** — deliberately absent, see §10 |

---

## 6. ASSUMPTIONS

| # | Assumption |
| --- | --- |
| A70 | The user drives with Google Maps in front and this app in the background. Monitoring must therefore be a foreground service. |
| A71 | A `POST_NOTIFICATIONS` refusal degrades the **alert**, never the **monitoring**. An app that stopped watching because it could not post would be silently broken. |
| A72 | **A session cannot resume after a reboot.** Android 12+ forbids starting a foreground service from a `BOOT_COMPLETED` receiver, and there is no permission that lifts it. The boot receiver clears the stale reference rather than leaving it to confuse the next launch. **This is a stated limitation, not an oversight.** |
| A73 | The arrival radius is the same `GeoConstants` value the Settings screen already writes. No second definition exists. |
| A74 | The service is started from a tap, never from a fragment lifecycle callback. Tabs are hidden rather than destroyed (A27), so `onResume` is not a reliable "the user is here" signal, and Android 12+ forbids background starts anyway. |

---

## 7. FILES TO CREATE (14)

| # | File | Purpose |
| --- | --- | --- |
| 1 | `domain/DistanceCalculator.java` | Haversine. Pure Java. |
| 2 | `domain/ArrivalState.java` | The four outcomes. |
| 3 | `domain/ArrivalEvaluator.java` | The arrival decision + the `LocationFixLike` test seam. |
| 4 | `navigation/NavigationSession.java` | The observable session + `Snapshot`. |
| 5 | `navigation/NavigationSessionManager.java` | Start / stop a session. The only writer. |
| 6 | `navigation/NavigationMonitorService.java` | The foreground service. |
| 7 | `navigation/NavigationNotifications.java` | The channel and both notifications. |
| 8 | `navigation/NavigationBootReceiver.java` | Clears a stale session after a reboot. |
| 9 | `ui/route/NavigationActiveBottomSheet.java` | Screen 8. |
| 10 | `res/layout/sheet_navigation_active.xml` | Screen 8's layout. |
| 11 | `res/drawable/ic_navigation_notification.xml` | The status-bar icon. |
| 12 | `app/src/test/.../NavigationSessionTest.java` | JVM tests for the session. |
| 13 | `app/src/test/.../DistanceCalculatorTest.java` | Known-distance tests for the haversine. |
| 14 | `app/src/test/.../ArrivalEvaluatorTest.java` | The arrival rules, including the refusals. |

---

## 8. FILES TO MODIFY (7)

| # | File | Change |
| --- | --- | --- |
| 1 | `AndroidManifest.xml` | 4 permissions, the service, the receiver. |
| 2 | `AlphaRouteApp.java` | Owns the session and the manager. |
| 3 | `MapFragment.java` | The nav bar, the sheet, `currentStores`. |
| 4 | `res/layout/fragment_map.xml` | The nav bar card. |
| 5 | `RoutePreviewBottomSheet.java` | **Start navigation** now begins a session. |
| 6 | `res/values/strings.xml` | 26 new strings (24 `nav_*` + the notification channel pair's labels). |
| 7 | `app/build.gradle`, `gradle/libs.versions.toml` | `arch-core-testing`, test only. |

Also modified: `LocationFix.java` — now implements `ArrivalEvaluator.LocationFixLike`.

---

## 9. DEPENDENCIES

| Dependency | Status |
| --- | --- |
| `play-services-location` | Already present (Phase 3.2). The service reuses `LocationProvider`. |
| `lifecycle-livedata` | Already present. The session is LiveData (AD-2). |
| `androidx.arch.core:core-testing:2.2.0` | **NEW — test only.** Runs LiveData on the calling thread so the session is testable on the JVM. Not in the APK. |
| openrouteservice | **Not used by this phase.** Arrival is a straight-line check; no network call. |

**No new runtime dependency, and no new API key.**

---

## 10. PERMISSIONS AND MANIFEST CHANGES

Four new `uses-permission` entries and two new components.

| Permission | Why | Protection |
| --- | --- | --- |
| `FOREGROUND_SERVICE` | Required from Android 9 for any foreground service. | install-time |
| `FOREGROUND_SERVICE_LOCATION` | Required from **Android 14** for a service of type `location`. Without it `startForeground` throws. | install-time |
| `POST_NOTIFICATIONS` | Required from **Android 13** to show the arrival alert. Runtime permission. | **runtime** |
| `RECEIVE_BOOT_COMPLETED` | So the boot receiver can clear a stale session. | install-time |

Components:

- `NavigationMonitorService` — `exported="false"`, `foregroundServiceType="location"`.
- `NavigationBootReceiver` — `exported="true"` (the system sends the broadcast), for `BOOT_COMPLETED`
  and `MY_PACKAGE_REPLACED`.

**`ACCESS_BACKGROUND_LOCATION` is still not declared, and this phase did not change that.** The
monitoring runs in a foreground service with a visible, undismissable notification - the user's own
evidence that it is running. Asking for the background permission on top would be a real privacy cost for
no benefit.

> ⚠️ **Trap.** Grepping the merged manifest for `ACCESS_BACKGROUND_LOCATION` returns a hit. It is the
> **XML comment text** in the location permission block, not a permission element. Do not "fix" it.

---

## 11. SETUP INSTRUCTIONS

```bash
cd "C:/Users/Admin/OneDrive/Documents/custom system/AlphaRoute"
./gradlew clean :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain
```

Nothing else. No `local.properties` change, no new key, no database migration.

To install:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**After installing, grant notifications** if prompted (Android 13+). Denying them is safe — see §17.

---

## 12. COMPLETE CODE

The complete files are in the repository at the paths listed in §7 and §8. This record does not repeat
them; the phase rule is that a delivered file is the complete file, not a diff, and they are all present.

---

## 13. CODE EXPLANATION

### The arrival decision is pure Java, and that is the point

`DistanceCalculator` and `ArrivalEvaluator` live in `domain/` with **zero Android imports** (AD-11). Every
other correctness property in this app has a database guard behind it: a UNIQUE index stops a double count
(AD-5), a foreign key stops a completed store being deleted (AD-6). **Arrival has no such backstop.** If it
decides wrongly, the app offers to count a store the user is not standing in, and a yes to that dialog is a
false record nothing downstream can detect. So it is the one calculation that must be provable on a laptop.

### The accuracy circle widens the radius

This is the whole design of `ArrivalEvaluator`. A GPS fix is not a point; it is a point with a radius of
68% confidence. Comparing the bare distance against the bare radius would be reasoning about a precision
the device does not have. So the effective radius is `arrivalRadius + accuracy`, and arrival is claimed
only when the **worst plausible position** is still inside. The conservative direction is the correct one
when the cost of being wrong is a false completion.

A consequence worth stating: **a better fix is stricter, not looser.** At 59 m, a 10 m accuracy reads
`ARRIVED` (radius 60) while a 1 m accuracy reads `APPROACHING` (radius 51). `ArrivalEvaluatorTest`
asserts exactly that, because it looks backwards until you follow the reasoning.

### Four ways to refuse

`NO_FIX` is returned for a missing reading, an accuracy that is absent or zero, an accuracy above 100 m,
and a reading older than 30 seconds. Every one of these is "cannot tell", and "cannot tell" must never
collapse into "arrived" - which is why the outcome is an enum with four values and not a boolean.

### Why the session is an object and not a callback

The foreground service and the Navigation Active sheet cannot see each other: a service holding a Fragment
would leak it, and a Fragment binding to the service would rebind on every rotation and show a stale
distance in between. Both go through one observable `NavigationSession` instead - the same LiveData pattern
the map's blue dot and the route line already use (AD-2, AD-4). Held by `AlphaRouteApp`, so a session
survives a rotation, a tab switch and the screen being destroyed while Google Maps is in front.

### A `Snapshot` per change, not several mutable fields

A reader that called `isActive()` and then `destination()` as two calls could catch the session ending in
between, and render an active-looking sheet with no destination. One immutable snapshot per change makes
that unrepresentable.

### The service observes; it does not write

`NavigationMonitorService` never touches Room and never marks anything complete. It publishes fixes to the
session and posts a notification. That is AD-15, and it is what keeps the write path (Phase 5.3) in one
place.

### The two notifications have two ids, deliberately

The **ongoing** one is the foreground service's required visible evidence and cannot be swiped away. The
**arrival** one is a normal dismissible alert. One id for both would mean either a foreground service with
no visible notification - which the system kills - or an alert the user cannot clear.

### The observer is a field, not a method reference

```java
private final Observer<LocationFix> fixObserver = this::onFix;
```

`observeForever(this::onFix)` and `removeObserver(this::onFix)` are two **different lambda objects**.
Written that way, nothing is ever removed, the service keeps receiving fixes after it was told to stop, and
the LiveData holds a reference to a service that can never be collected. A service has no `LifecycleOwner`,
so `observeForever` is the only option and the removal must be explicit.

### The nav bar exists because screen 8 is dismissable

Without it, closing the sheet would leave a session running with nothing on screen to say so. It sits at the
**top** of the map and not the bottom, because the OpenStreetMap attribution is pinned to the bottom and the
tile policy forbids covering it.

---

## 14. DATA FLOW FOR THIS PHASE

```
Start navigation (tap, Route Preview)
      ↓
NavigationSessionManager.start(context, store, _)
      ├─→ NavigationSession.begin(storeId, destination)      ← in-memory, immediate
      ├─→ settingsRepository.setLastSelectedStoreId(storeId)  ← AD-10, diskIO
      └─→ context.startForegroundService(intent)              ← legal only from a visible screen

NavigationMonitorService
      ├─ startForeground(ONGOING_ID, notification, TYPE_LOCATION)
      ├─ reads the arrival radius + the store from Room (once)
      ├─ locationProvider.currentFix().observeForever(fixObserver)
      └─ on each fix →
            NavigationSession.update(fix, radius, now)
                  └─ ArrivalEvaluator.evaluate(...) → ArrivalState
                  └─ DistanceCalculator.distanceMetres(...) → metres
                        ↓
                  NavigationSession.Snapshot (one immutable object)
                        ↓                                   ↓
            Navigation Active sheet                 the nav bar on the Map tab
                        ↓
            NavigationMonitorService also: updates the ongoing notification,
            and on ARRIVED posts ARRIVAL_ID and stops watching (session stays open)

End navigation (tap)
      ↓
NavigationSessionManager.stop(context)
      ├─→ NavigationSession.end()                             ← snapshot becomes null
      ├─→ settingsRepository.setLastSelectedStoreId(null)
      └─→ context.stopService(...)
```

**The network is nowhere in this flow.** Arrival is a straight-line check; no routing request is made.

---

## 15. MANUAL TEST PROCEDURE

All of §15 requires a real device with Google Maps installed and a location the user can move between.

### 15.1 The happy path

1. Pin a store somewhere you can actually drive to.
2. Tap the store's marker → **Get directions** → wait for the route → **Start navigation**.
3. Google Maps should open at the destination.
4. **Pull down the notification shade.** There must be an ongoing "Navigating to your store" notification
   with a distance in it. It must **not** be swipeable.
5. Switch back to the app. The Map tab must show the compact navigation bar with the store's code.
6. Drive (or walk) towards the store. The distance in the notification and on screen must fall.

### 15.2 The arrival

7. Get within roughly 50 m of the pin. Two things must happen:
   - the ongoing notification switches to "You have arrived";
   - a **second, dismissible** notification appears.
8. Open the app. The Navigation Active sheet must show *Inside arrival radius — confirm when you arrive*
   in the completed colour.
9. **Nothing must have been counted.** The store must still be PENDING on the map, and no history row must
   exist. This is the most important assertion in the whole phase.

### 15.3 End navigation

10. Tap **End navigation**. The sheet must close, the bar must disappear, and **both** notifications must go.
11. Cold-start the app (swipe it from recents first). It must **not** offer to resume navigation.

### 15.4 Closing the sheet without ending

12. Start navigation again. Close the sheet by swiping down.
13. The navigation bar must still be on the map, and the notification must still be there. Reopening the
    sheet from the bar must show the same session.

### 15.5 The permission refusal path

14. Revoke notifications (Settings → Apps → Alfamart Route Tracker → Notifications → off).
15. Start navigation again. **The monitoring must still work**: the distance must still update on screen
    when you open the app, and the arrival must still be detected. Only the notification is missing.

### 15.6 The reboot path

16. With a session running, reboot the phone.
17. Open the app. It must **not** offer to resume, and the boot receiver's log line must appear in logcat:
    `Device restarted or app updated - clearing any recorded navigation session`.

### 15.7 No navigation app

18. On a device without Google Maps and without Waze, tap **Start navigation**. A message must appear and
    **no session must start** — in particular, no permanent notification.

---

## 16. EXPECTED RESULT

- A store can be navigated to, and the app keeps watching the position while Google Maps is in front.
- The distance falls as the driver approaches, on screen and in the notification.
- Crossing the arrival radius produces a notification and the arrival indicator — and **changes nothing in
  the database**.
- Nothing is ever counted without an explicit YES, which arrives in Phase 5.
- Ending navigation cleans up completely: sheet, bar, both notifications and the persisted reference.

---

## 17. COMMON ERRORS AND FIXES

| Symptom | Cause | Fix |
| --- | --- | --- |
| Crash on **Start navigation**: `SecurityException: Starting FGS with type location ... requires permissions` | Missing `FOREGROUND_SERVICE_LOCATION` (API 34+) | Already declared in this phase's manifest. Check the merged manifest. |
| Crash: `startForeground() not allowed` / `ForegroundServiceStartNotAllowedException` | The service was started from the background | Every entry point is a tap. If this appears in the log after a reboot, that is the boot path — it is **not started** there by design (A72). |
| Crash: `ForegroundServiceDidNotStartInTimeException` | `startForeground` was not called within ~5 s | `startForegroundNow()` is called **first** in `onStartCommand`, before any disk read. |
| Arrival never fires | Stale or imprecise fix | `ArrivalEvaluator` refuses fixes older than 30 s or less precise than 100 m. Check the accuracy shown on the Map tab. |
| Arrival fires too early | The accuracy circle widening the radius | Working as designed. At a 30 m accuracy the effective radius is 80 m, not 50. Raise the configured radius only if you want it **larger**. |
| No arrival notification, but arrival detected on screen | `POST_NOTIFICATIONS` refused | Expected and safe — see §4 and A71. The alert is degraded, the monitoring is not. |
| The service dies when the app is swiped away | Normal on some OEM builds | The foreground service should survive; if an aggressive battery manager kills it, the session still works while the app is open. |
| Notification appears but the distance never changes | Location permission was revoked mid-drive | The service stops and says why. Re-grant and start navigation again. |

---

## 18. ACCEPTANCE CHECKLIST

- [x] `domain/` has zero Android imports in the new files (AD-11).
- [x] The arrival decision is pure Java and unit-tested.
- [x] A fix of unknown quality never produces `ARRIVED`.
- [x] A stale fix never produces `ARRIVED`.
- [x] The accuracy circle widens the radius (the conservative rule).
- [x] The session survives a rotation, a tab switch and a screen teardown.
- [x] A late fix cannot resurrect an ended session.
- [x] `ACCESS_BACKGROUND_LOCATION` is **not** declared.
- [x] The database schema is unchanged; no `Migration` and no `fallbackToDestructiveMigration`.
- [x] Zero Kotlin.
- [x] `NavigationSessionManager` is the only writer of the session **and** the service.
- [x] Nothing completes a store. Nothing starts navigation automatically.
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
| Build | **BUILD SUCCESSFUL in 56s** — 41 tasks, all executed (clean tree) |
| Tests | **262 passing, 0 failures, 0 errors** across 19 classes (216 before this phase, **+46 new**) |
| New tests | `DistanceCalculatorTest` 13 · `ArrivalEvaluatorTest` 20 · `NavigationSessionTest` 13 |
| APK | **9,676,892 bytes** (debug, clean build) |
| Database schema | **still only `1.json`** — no version bump, no `Migration` |
| Kotlin files | **0** |
| Java sources | 62 main · 19 test |
| New permissions | 4 (§10) — verified in the **merged** manifest: 8 total, and `ACCESS_BACKGROUND_LOCATION` is absent |
| New runtime strings | 26 |

### What was checked in the merged manifest, not just the source

```bash
grep -o 'android:name="android.permission.[A-Z_]*"' \
  app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml | sort -u
```

```
ACCESS_COARSE_LOCATION · ACCESS_FINE_LOCATION · ACCESS_NETWORK_STATE
FOREGROUND_SERVICE · FOREGROUND_SERVICE_LOCATION · INTERNET
POST_NOTIFICATIONS · RECEIVE_BOOT_COMPLETED
```

8 entries, 4 of them new, and **no `ACCESS_BACKGROUND_LOCATION`**. The service is present with
`foregroundServiceType="location"`, and `NavigationBootReceiver` is present and exported.

---

## 20. STOP POINT

**Phase 4.3 is code complete and the build is verified. It is NOT verified on a device.**

Do not begin Phase 5 until §15 has been run. Two things specifically must be confirmed by the user before
5.1 is written:

1. **§15.2 step 9** — that arriving has changed nothing in the database. This is the guarantee the whole
   design exists to provide, and it is the one thing a JVM test cannot prove.
2. **§15.6** — that a reboot behaves as described. The "no resume after reboot" limitation (A72) is a
   deliberate restriction, and the user should see it happen rather than read about it.

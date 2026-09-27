# Phase 4.3 — Plan (written before any code)

> Written first, on the user's instruction *"do it first plan then execute it. make no mistake."*
> The 20-section phase record (`PHASE_4.3_NAVIGATION_MONITORING.md`) is written **after** the build is
> verified, and this plan is its input.

## 1. What 4.3 is

4.2 already built the Intent, the `<queries>` element, `NavigationLauncher`, and the **Start navigation**
button. What 4.3 adds is everything that happens **after** the driver is handed to Google Maps:

- the app knows which store is the active destination, and remembers it across a restart (AD-10);
- a **foreground service** keeps watching the driver's position while Google Maps is in front;
- the driver crosses the **arrival radius** and is told, by notification and on screen;
- the **Navigation Active** sheet shows the live distance and the arrival indicator;
- **End navigation** stops monitoring and clears the session.

Completion itself — the YES/NO dialog and the atomic transaction — is **Phase 5**. 4.3 stops at
"you have arrived, confirm when you are ready".

## 2. Files

### New — pure Java, `domain/` (AD-11, zero Android imports)

| File | Purpose |
| --- | --- |
| `domain/DistanceCalculator.java` | Haversine. One formula, one place. Distinct from any map library. |
| `domain/ArrivalEvaluator.java` | Decides `OUTSIDE / APPROACHING / ARRIVED` from a fix, a target and a radius. |
| `domain/ArrivalState.java` | The enum result, with the reasons it can be unavailable. |

### New — navigation package

| File | Purpose |
| --- | --- |
| `navigation/NavigationSession.java` | Observable, screen-agnostic: which store is active, the latest arrival state. |
| `navigation/NavigationSessionManager.java` | Start / stop a session from any context; owns the service Intent. |
| `navigation/NavigationMonitorService.java` | The foreground service. Watches position, posts the arrival notification. |
| `navigation/NavigationNotifications.java` | The notification channel, the ongoing notification and the arrival one. |
| `navigation/NavigationBootReceiver.java` | Clears a stale session after a reboot — see §6. |

### New — UI

| File | Purpose |
| --- | --- |
| `ui/route/NavigationActiveBottomSheet.java` | Screen 8. |
| `res/layout/sheet_navigation_active.xml` | Screen 8's layout. |

### Modified

| File | Change |
| --- | --- |
| `AndroidManifest.xml` | `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `POST_NOTIFICATIONS`; the service; the boot receiver. |
| `AlphaRouteApp.java` | Own `NavigationSession` + `NavigationSessionManager`. |
| `MapFragment.java` | The active-navigation bar; opens screen 8; resumes a session on launch. |
| `res/layout/fragment_map.xml` | The compact active-navigation bar. |
| `RoutePreviewBottomSheet.java` | **Start navigation** now also begins a session. |
| `res/values/strings.xml` | ~18 new strings. |

## 3. The decisions

- **AD-13 (new):** the arrival radius is the *same* `GeoConstants.DEFAULT_ARRIVAL_RADIUS_M` the Settings
  screen already writes. No second definition.
- **AD-14 (new):** the arrival decision is pure Java in `domain/`, so it is JVM-testable. This is the
  single most safety-critical calculation in the app — it is the only thing that can offer a completion —
  and it must be provable without driving around.
- **AD-15 (new):** the foreground service owns *observation*, not *writing*. It never touches Room. It
  publishes to `NavigationSession`; the UI reads Room. Room stays the single source of truth (AD-4).
- **AD-16 (new):** `POST_NOTIFICATIONS` is a runtime permission on Android 13+, but the *service* does not
  depend on it. The notification channel is created regardless, and a denied notification permission
  degrades the arrival *alert* — never the monitoring. An app that stops watching because it cannot post
  a notification would be silently broken.

## 4. The traps I expect

1. **`FOREGROUND_SERVICE_TYPE_LOCATION` must be declared in the manifest for API 34+.** A service started
   with `startForeground(id, notification, FOREGROUND_SERVICE_TYPE_LOCATION)` on Android 14 throws
   `SecurityException` if the manifest lacks `android:foregroundServiceType="location"`.
2. **Starting a foreground service from the background is forbidden on Android 12+.** The start must
   happen while an Activity is in the foreground — i.e. from the tap on **Start navigation**. Resuming
   after a reboot therefore cannot start the service; see §6.
3. **`ACCESS_BACKGROUND_LOCATION` is still not needed.** The service is foreground and visible. If it is
   ever found to be needed, that is a design failure, not a permission to add quietly.
4. **The ongoing notification must not be dismissible and must not be the arrival notification.** Two
   separate ids: one `ONGOING` and one `DEFAULT` that the driver can swipe away.
5. **`android:exported`** on the service must be `false`; on the boot receiver it must be `true` (the
   system sends the broadcast) — and `RECEIVE_BOOT_COMPLETED` is then required.
6. **A rotation must not restart the service.** The service is started from a tap and stopped from a tap,
   never from a fragment lifecycle callback.

## 5. What 4.3 deliberately does NOT do

- **It does not complete a store.** No YES/NO dialog, no history row, no counter. That is Phase 5.
- **It does not render turn-by-turn.** D-7. Google Maps does that.
- **It does not add `ACCESS_BACKGROUND_LOCATION`.** See §4.3.
- **It does not start the service from a Fragment lifecycle callback.** A tap starts it; a tap ends it.

## 6. The honest limitation, stated up front

A navigation session cannot survive a phone reboot in 4.3. Resuming it would mean starting a foreground
service from the background, which Android 12+ forbids — the service must be started while a screen is
visible. So on reboot the boot receiver **clears the stale session** and the app says "navigation ended"
rather than pretending to still be watching.

That is the correct behaviour and it is deliberate. It is also the reason `lastSelectedStoreId` is
cleared on boot rather than restored.

## 7. Acceptance criteria

- Build clean: `./gradlew clean :app:testDebugUnitTest :app:assembleDebug` → `BUILD SUCCESSFUL`.
- All pre-existing tests still pass, plus the new ones.
- Zero Kotlin, zero new schema, `app/schemas/.../1.json` still the only schema.
- On-device (the user's job, §15 of the phase record):
  - Start navigation → Google Maps opens → a persistent notification appears.
  - Drive inside the radius → the notification changes and the sheet says *Inside arrival radius*.
  - End navigation → the notification goes, the session clears, and a cold start does not resume it.

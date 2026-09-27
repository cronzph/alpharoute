# AlphaRoute — Phase 4.2 Route Preview

**Project title:** Alfamart Route Tracker
**Version:** 4 — In-App Navigation (hand-off) — **Phase:** 4.2 Route Preview
**Repository root:** `C:/Users/Admin/OneDrive/Documents/custom system/AlphaRoute`
**Date:** 2026-09-25
**Prerequisites:** Phases 0.1–0.3, 1.1–1.3, 2.1–2.3, 3.1–3.7, 4.1
**Status:** ✅ **COMPLETE AND VERIFIED — 188/188 unit tests pass, clean build succeeds.**

> **The route line is here.** Tap a store → Get directions → the app asks openrouteservice for the drive,
> draws it on the map, and shows the distance and the time. Then **Start navigation** hands off to Google
> Maps. This is the first phase that talks to the internet, and the first that draws something other than
> a pin.

---

## 1. CURRENT VERSION AND PHASE

| Item | Value |
| --- | --- |
| Version | 4 — In-App Navigation (hand-off) |
| Phase | 4.2 — Route Preview |
| Files created | **14** (9 source, 4 test, 1 doc) |
| Files modified | **12** |
| Files deleted | 0 |
| Database version | **1** — unchanged |
| Unit tests | **188** (was 144 — 44 new) |
| New dependencies | **1, test-only** (`org.json`, never in the APK) |
| New permissions | **None** |

---

## 2. OBJECTIVE

Show the drive before anything navigational happens:

- Fetch a real route from openrouteservice between the current location and a store
- Draw it as a line on the map
- Show the distance and the estimated time, with the service's attribution
- **Start navigation** hands off to Google Maps — the only $0 route to live traffic
- **Never** start navigation on its own, and **never** invent a route

---

## 3. UNDERSTANDING CHECKLIST

- [x] Java only, no Kotlin. XML layouts only.
- [x] **No new permission.** The hand-off uses an Intent, not a location or background permission.
- [x] The request runs on `AppExecutors.networkIO()`, never on the disk thread.
- [x] A screen never touches HTTP: `RoutePreviewBottomSheet` → `RoutingRepository` → the service.
- [x] osmdroid stays behind `MapMarkerController` (AD-8) — the polyline is drawn there, not in the sheet.
- [x] **Nothing is stored.** A route is transient; the database is untouched.
- [x] Navigation starts only on an explicit tap, and only when a real route exists.
- [x] **The line is not drawn until a real route arrives**, and a failed request clears it.
- [x] The key is never in a URL, and never in a source file.
- [x] Still $0 — the free plan, no card.

---

## 4. FEATURES INCLUDED (in this phase)

| Feature | Detail |
| --- | --- |
| **The live route** | `RoutingRepository` POSTs to openrouteservice and maps the HTTP status to a state |
| **The route line** | `MapMarkerController.renderRoute` draws an osmdroid `Polyline` in the planned `route_line` colour |
| **Camera fit** | The one time the camera moves without the user asking for the *camera* — because they asked for the route |
| **`RouteResponseParser`** | Pure Java, 18 tests, including the coordinate swap |
| **`RouteTextFormatter`** | "1.7 km" / "5 min" / "1 h 15 min", 15 tests, boundary cases included |
| **`NavigationLauncher`** | AD-9: the Google Maps Intent, a Waze fallback, 5 tests on the URI formatting |
| **The `<queries>` element** | Android 11+ package visibility — without it the hand-off fails silently |
| **Route Preview sheet** | Store, distance, time, attribution; Start navigation / Retry / Cancel |
| **Get directions** | The Store Details sheet's primary action, as the screen plan specifies |

---

## 5. FEATURES NOT INCLUDED YET

| Not here | Arrives in |
| --- | --- |
| Arrival monitoring (the foreground service) | **Phase 4.3** |
| The active-navigation session in `lastSelectedStoreId` | **Phase 4.3** |
| `sheet_navigation_active.xml` — the "navigating to…" panel | **Phase 4.3** |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_LOCATION` / `POST_NOTIFICATIONS` | **Phase 4.3** |
| State recovery (backgrounded, GPS loss, cancellation) | **Phase 4.4** |
| Turn-by-turn step text | **Not planned** — D-7 gives that to Google Maps |
| Offline maps | **Not planned** — the OSM policy forbids prefetch (risk R5) |

---

## 6. ASSUMPTIONS

| # | Assumption | Impact if wrong |
| --- | --- | --- |
| A101 | **The repository holds the current route as `LiveData`**, rather than handing a result back to the caller. | It is the only way the sheet that *asks* for a route and the map that *draws* it can stay in step without a reference to each other. Same shape as `LocationProvider.currentFix()`, and it avoids re-adding the fragment-result plumbing Phase 3.4 deliberately removed. |
| A102 | **Only `READY` draws a line; every other status clears it.** | Otherwise a failed request would leave the *previous* route on the map, presented as the answer to the request just made. |
| A103 | **The parser refuses rather than guesses.** Any malformed body returns `null`. | See §13.3. A half-read route draws a plausible line that is wrong. |
| A104 | **One bad coordinate rejects the whole route**, rather than being skipped. | Skipping it would join two points that are not joined, cutting a straight line across whatever lies between them. |
| A105 | **Coordinates are longitude-first in the request *and* the response.** | See §13.2. The two halves must stay symmetric. |
| A106 | **A missing key is a status, not a request.** | `NOT_CONFIGURED` is checked before sending, so a keyless build says so instead of getting a 401 that looks like a rejected key. |
| A107 | **Start navigation and the `<queries>` element moved from Phase 4.3's row into this phase.** | See §13.7. The screen plan (§13.8) and the 4.2 roadmap row both list Start Navigation; shipping the sheet without it would be a dead button. |
| A108 | **The route is cleared when the sheet leaves — but not on rotation.** | See §13.6. A rotation would otherwise throw away a good route and spend a second request from the 2,000-a-day allowance. |
| A109 | **The camera fits the route.** | The one case where the camera moves without the user asking for the camera — but they did ask for the route, and a line that runs off the screen answers nothing. |
| A110 | **`org.json` is a TEST-ONLY dependency.** | Android ships `org.json` on the platform, so the app needs no dependency for it; the platform copy is a stub on the JVM, so the tests need a real one. Verified: it is not in the APK. |
| A111 | **`RouteTextFormatter` holds the unit boundaries and formats with `Locale.US`.** | See §13.5. In a comma-decimal locale the default formatter turns 1.7 into "1,7". |
| A112 | **No turn-by-turn steps are shown.** | The API returns six of them for the probe route and they are ignored. D-7 gives guidance to Google Maps; rendering a second, worse set here would be a competing answer. |

---

## 7. FILES TO CREATE (14)

| # | Path | Purpose |
| --- | --- | --- |
| 1 | `domain/RoutePoint.java` | **New.** One point on the line. Pure. |
| 2 | `domain/RoutePlan.java` | **New.** Distance, duration and the line. Pure. |
| 3 | `navigation/RouteState.java` | **New.** The request's status plus the plan — one object, so loading and result cannot disagree |
| 4 | `navigation/RouteResponseParser.java` | **New.** GeoJSON → `RoutePlan`. Pure apart from `org.json`. |
| 5 | `navigation/NavigationLauncher.java` | **New.** AD-9: the Google Maps Intent and the Waze fallback |
| 6 | `data/repository/RoutingRepository.java` | **New.** The request, the status mapping, and the current route as `LiveData` |
| 7 | `ui/route/RoutePreviewBottomSheet.java` | **New.** Screen 7 |
| 8 | `res/layout/sheet_route_preview.xml` | **New.** Its layout |
| 9 | `util/RouteTextFormatter.java` | **New.** Pure. "1.7 km", "5 min", "1 h 15 min" |
| 10 | `test/.../navigation/RouteResponseParserTest.java` | **New.** 18 tests |
| 11 | `test/.../navigation/NavigationLauncherTest.java` | **New.** 5 tests |
| 12 | `test/.../util/RouteTextFormatterTest.java` | **New.** 15 tests |
| 13 | `test/.../data/repository/RoutingRepositoryTest.java` | **New.** 5 tests |
| 14 | `docs/PHASE_4.2_ROUTE_PREVIEW.md` | **New.** This record |

---

## 8. FILES TO MODIFY (12)

| # | Path | Status | What changed |
| --- | --- | --- | --- |
| 1 | `gradle/libs.versions.toml` | **Modified** | The test-only `org.json` version and library entry |
| 2 | `app/build.gradle` | **Modified** | `testImplementation libs.json` |
| 3 | `app/src/main/AndroidManifest.xml` | **Modified** | The `<queries>` element |
| 4 | `AlphaRouteApp.java` | **Modified** | Owns `RoutingRepository` |
| 5 | `ui/map/MapMarkerController.java` | **Modified** | `renderRoute`, `clearRoute`, the camera fit; `clear()` now removes the line too |
| 6 | `ui/map/MapFragment.java` | **Modified** | `observeRoute()` |
| 7 | `ui/store/StoreDetailBottomSheet.java` | **Modified** | The Get directions action |
| 8 | `navigation/RoutingConfig.java` | **Modified** | `attribution()` |
| 9 | `res/layout/sheet_store_detail.xml` | **Modified** | The full-width Get directions button |
| 10 | `res/values/strings.xml` | **Modified** | 16 new strings |
| 11 | `test/.../navigation/RoutingConfigTest.java` | **Modified** | +1 test for the attribution |
| 12 | `docs/HANDOFF.md` | **Modified** | Status, inventory, test count, roadmap, traps |

**No removals and no renames this phase.**

**Inspected and left unmodified:** the database layer, `domain/StoreValidator`, `location/`, and every
screen built before this one. This phase adds a feature beside them; it changes nothing about how stores or
settings work.

---

## 9. DEPENDENCIES

**One, and it is test-only.**

| Dependency | Scope | Why |
| --- | --- | --- |
| `org.json:json:20240303` | **`testImplementation`** | Android ships `org.json` on the platform, so the app needs no dependency for it — but that platform copy is a stub on the JVM where every method throws "not mocked". Without a real copy the route parser could not be unit-tested at all. |

**Verified it is not in the APK:** `unzip -Z1 app-debug.apk | grep -i "org/json"` finds nothing. The app
uses the platform's copy; only the tests use the Maven one.

---

## 10. PERMISSIONS AND MANIFEST CHANGES

**No new permissions.** Verified in the merged manifest — the same four, plus androidx's auto-added one.

**One manifest addition: the `<queries>` element**, which is not a permission:

```xml
<queries>
    <package android:name="com.google.android.apps.maps" />
    <package android:name="com.waze" />
</queries>
```

Android 11 (API 30) stopped letting an app see what else is installed unless it says so. Without this,
`resolveActivity()` returns null **even when Google Maps is installed**, and the hand-off silently does
nothing. It is the single most common way this feature breaks, and it produces no error to explain itself.

Only the two navigation apps are named. A broad `<queries>` element, or `QUERY_ALL_PACKAGES`, would be a
privacy cost with no benefit — these are the only packages the app ever looks for.

`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION` and `POST_NOTIFICATIONS` are **not** added here. They
belong to the arrival monitoring in Phase 4.3, which is the thing that needs them.

---

## 11. SETUP INSTRUCTIONS

1. Let Gradle sync — it will fetch `org.json` for the test classpath only.
2. **Build → Clean Project**, then **Build → Rebuild Project**.
3. Make sure `ORS_API_KEY` is in `local.properties` (Phase 4.1). Without it the preview says
   "Routing is not set up in this build." rather than failing obscurely.
4. Run on a device with **Google Maps installed** — or with Waze, which is the fallback.

> A clean build on this machine takes about 3 minutes because the project lives in a OneDrive folder, which
> is slow to delete the build tree. If `clean` fails with a file-lock error, run it again — it is OneDrive
> holding a file, not a problem with the project.

---

## 12. COMPLETE CODE

All 26 files are written into the project on disk. The files worth reading first:

1. `data/repository/RoutingRepository.java` — the request, and why the route is `LiveData`
2. `navigation/RouteResponseParser.java` — the coordinate swap, and why it refuses rather than guesses
3. `ui/map/MapMarkerController.java` — `renderRoute`, and why the line goes at index 0
4. `navigation/NavigationLauncher.java` — the hand-off, and the two formatting traps
5. `util/RouteTextFormatter.java` — the unit boundaries
6. `test/.../RouteResponseParserTest.java` — a trimmed copy of a real response

---

## 13. CODE EXPLANATION

### 13.1 Who owns the route line, and why the repository holds it

The awkward part of this phase is that the sheet that **asks** for a route and the screen that **draws** it
are not the same thing. The Route Preview is a bottom sheet opened from Store Details; the route line is
drawn on the Map tab, because the Map tab owns the `MapView` and osmdroid is confined to
`MapMarkerController` (AD-8).

Three ways to join them up, and why the middle one won:

| Approach | Why not |
| --- | --- |
| Pass the route between fragments | This is the fragment-result plumbing Phase 3.4 **deliberately removed** in favour of observation. Re-adding it would undo that decision |
| A shared ViewModel | Textbook, and AD-2 does name ViewModels — but the project has none yet, and the object would exist solely to hold one value |
| **`LiveData` on the repository** | **Chosen.** Same shape as `LocationProvider.currentFix()`, which the app already relies on. Zero new infrastructure, and it survives rotation for free because the repository lives in the application container |

So the flow is:

```
Store Details → Get directions
  └─ dismiss the details sheet, show RoutePreviewBottomSheet(storeId)
        └─ reads the store by id, reads the current fix
              └─ RoutingRepository.calculateRoute(origin, destination)
                    └─ LiveData<RouteState>  ──┬──► RoutePreviewBottomSheet renders it
                                               └──► MapFragment draws or clears the line
```

Neither observer knows the other exists.

### 13.2 The longitude-first trap, in both directions

This is the mistake that costs an afternoon, and it is silent. openrouteservice takes and returns
coordinates as **`[longitude, latitude]`** — the opposite of how a coordinate is spoken aloud. A swapped
pair is not an error: it is a perfectly valid request for a point in the wrong place, and the service
answers with a perfectly valid route to it. The map draws a line. Nothing fails.

There are **two** halves to getting it right, and they must stay symmetric:

| Direction | Where | What it does |
| --- | --- | --- |
| **Out** | `RoutingRepository.coordinatePair` | Puts `longitude` at index 0 of each pair |
| **Back** | `RouteResponseParser.parsePoints` | Reads index 0 as longitude, index 1 as latitude |

From `RoutePoint` onwards the rest of the app deals in ordinary `latitude()` / `longitude()`, so the swap
happens once at each edge and cannot leak inward.

Both halves have tests, including the case a shortcut would get wrong:
`aNegativeLongitudeIsStillSentFirst` uses a point in the Americas, where a "the bigger number must be the
longitude" heuristic would swap the pair. In the Philippines that heuristic happens to look correct — which
is exactly what makes it dangerous.

### 13.3 Refusing rather than guessing

The parser returns `null` for every malformed body: not JSON, no `features`, an empty feature list, no
geometry, fewer than two points, a truncated pair, a non-numeric coordinate, an out-of-globe coordinate, no
summary, or a non-numeric distance. The screen then says "Could not calculate a route. Try again."

**There is deliberately no best-effort parse.** A partially-read route would be drawn as a line that looks
plausible and is wrong, and a driver following it is worse off than one who was told to try again. This is
the same reasoning as the screen plan's *"We never fake a route"*.

The single most important case is **one bad coordinate rejects the whole route** rather than being skipped.
Skipping would leave a line joining two points that are not actually joined, and the map would draw a
straight cut across whatever lies between them.

### 13.4 The `<queries>` element, and a hand-off that fails silently

`NavigationLauncher` guards the hand-off with `resolveActivity()`, which is what stops an
`ActivityNotFoundException` on a phone with no navigation app. On Android 11 and later, though,
`resolveActivity()` can only see packages the app has declared — so without the `<queries>` element it
returns null **even when Google Maps is installed**.

The failure is invisible: no exception, no log, no error message. The button simply does nothing. It is
worth the five lines of XML to not spend an evening on that.

The launcher tries Google Maps first (the primary target under D-6), then Waze, and returns `false` when
neither is installed — which the sheet turns into "No navigation app found. Install Google Maps or Waze."
rather than dismissing and leaving the user guessing.

### 13.5 The same locale trap, twice

Two places in this phase format a decimal, and both would break on a normal European phone:

| Where | Without `Locale.US` | Consequence |
| --- | --- | --- |
| `NavigationLauncher.formatCoordinate` | `14.5995` → `14,599500` | The URI reads it as **two values** and opens navigation to the wrong place |
| `RouteTextFormatter.distanceText` | `1.7` → `1,7` | "1,7 km" — not wrong so much as confusing |

Both are tested by changing the default locale to `Locale.GERMANY` inside a `try`/`finally` and asserting
the output is unchanged. That is unusual for a test — it deliberately changes the environment rather than
depending on it — and it is the only way to prove the code is immune rather than merely lucky.

`NavigationLauncher` also formats with `%.6f` rather than `Double.toString`, because a small enough value
comes out as `1.0E-5`, which the URI cannot read.

### 13.6 Why the route is not cleared on rotation

`onDestroy` fires for a rotation as well as for a real close, so clearing the route there unconditionally
would throw away a perfectly good route and spend **another request from the free plan's 2,000-a-day
allowance** just to redraw it.

`isRemoving()` is what tells the two apart:

```java
boolean activityGone = getActivity() == null || getActivity().isFinishing();
if (isRemoving() || activityGone) {
    routingRepository.clearRoute();
}
```

The route itself survives rotation for free, because it lives in the repository in the application
container — the same property that lets the map and the sheet share it.

### 13.7 What moved from Phase 4.3 into this phase, and why

**Moved: the navigation hand-off (the Intent) and the `<queries>` manifest element.**

The roadmap's 4.3 row lists both. They are here instead, for two reasons:

1. **The screen plan's own specification of screen 7 lists Start Navigation as its primary button**
   (§13.8: *"Buttons: Start Navigation (primary) · Cancel"*), and the 4.2 roadmap row says the same. So
   building the sheet without it would contradict the authoritative screen spec, not just the roadmap.
2. **A preview with no way to act on it is a dead button**, which is the thing rule 8 exists to prevent.
   The whole point of showing the route is to then drive it.

**What Phase 4.3 still has — and it is a full phase:** the foreground location service,
`FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_LOCATION` + `POST_NOTIFICATIONS`, the arrival-radius
notification, `sheet_navigation_active.xml`, and `ActiveNavigationSession` persisted in
`lastSelectedStoreId`.

**`lastSelectedStoreId` is deliberately NOT set here.** Without the monitoring service there is no session
to resume and nothing to clear it, so setting it would leave an "active navigation" flag that nothing reads
and nothing ends. Phase 4.3 owns it.

### 13.8 What the route is *not*

- **Not a guidance screen.** No turn-by-turn text, no voice, no lane advice. D-7 gives that to Google Maps,
  which is also the only $0 source of live traffic.
- **Not stored.** A route is transient. Nothing about it reaches the database, so it cannot go stale and
  cannot be mistaken for a record.
- **Not a substitute for the map's markers.** The line is drawn *under* the store pins, at overlay index 0,
  and it consumes no taps — verified against osmdroid 6.1.20's sources, where `Polyline` overrides neither
  `onSingleTapConfirmed` nor `onLongPress`. A marker sitting on top of the line is still tappable.

---

## 14. DATA FLOW FOR THIS PHASE

```
Store Details → "Get directions"
  └─ dismiss()  →  RoutePreviewBottomSheet(storeId)   [id in ARGUMENTS, so rotation re-reads]
        ├─ StoreRepository.findById(storeId)          → the destination
        ├─ LocationProvider.currentFix()              → the origin
        └─ RoutingRepository.calculateRoute(origin, destination)
              ├─ origin null?         → NO_LOCATION       (nothing is sent)
              ├─ no key configured?   → NOT_CONFIGURED    (nothing is sent)
              ├─ publish(LOADING)
              └─ networkIO().execute(...)
                    ├─ POST https://api.openrouteservice.org/v2/directions/driving-car/geojson
                    │     Authorization: <key>          (a header, never the URL)
                    │     {"coordinates":[[lng,lat],[lng,lat]]}
                    ├─ HTTP 200 → RouteResponseParser.parse(body)
                    │     ├─ parsed  → READY(plan)
                    │     └─ null    → FAILED
                    ├─ HTTP 429 → QUOTA_EXHAUSTED
                    ├─ other    → FAILED                (401 / 403 / 5xx)
                    └─ IOException → OFFLINE
                          ↓ postToMain
                    LiveData<RouteState>
                          ├──► RoutePreviewBottomSheet.render(state)
                          │      loading / summary + distance + time / message + Retry
                          └──► MapFragment.observeRoute()
                                 READY   → markerController.renderRoute(points)   (Polyline + camera fit)
                                 anything else → markerController.clearRoute()
```

**Nothing is written.** The only table this phase touches is none at all — `stores` and `app_settings` are
read, never written. That is why the database version is unchanged and there is no migration.

---

## 15. MANUAL TEST PROCEDURE

### 15.0 Already verified automatically (2026-09-25)

`./gradlew clean :app:testDebugUnitTest :app:assembleDebug` was run on this machine with Gradle 9.4.1 /
JDK 21 and returned **`BUILD SUCCESSFUL`** with all 41 tasks executed.

| Test class | Result |
| --- | --- |
| **`RouteResponseParserTest`** (new) | **18 tests, 0 failures, 0 errors** |
| **`RouteTextFormatterTest`** (new) | **15 tests, 0 failures, 0 errors** |
| **`NavigationLauncherTest`** (new) | **5 tests, 0 failures, 0 errors** |
| **`RoutingRepositoryTest`** (new) | **5 tests, 0 failures, 0 errors** |
| `RoutingConfigTest` | 13 tests, 0 failures (+1 this phase) |
| `LocationFixTest` | 20 tests, 0 failures |
| `StoreValidatorTest` | 19 tests, 0 failures |
| `GeoConstantsTest` | 17 tests, 0 failures |
| `ArrivalRadiusValidatorTest` | 16 tests, 0 failures |
| `DateTimeHelperTest` | 16 tests, 0 failures |
| `MapMarkerControllerTest` | 15 tests, 0 failures |
| `MapUserAgentTest` | 11 tests, 0 failures |
| `AppSettingEntityTest` | 10 tests, 0 failures |
| `StoreRepositoryTest` | 7 tests, 0 failures |
| `ExampleUnitTest` (template, Phase 10.2) | 1 test, 0 failures |
| **TOTAL** | **188 tests, 0 failures, 0 errors, 0 skipped** |

| Other evidence | Result |
| --- | --- |
| `:app:compileDebugJavaWithJavac` | ran — the repository, parser, launcher, sheet and controller change all compile |
| `:app:mergeDebugResources` + `:app:processDebugResources` | ran — the new layout and 16 strings resolve |
| **`<queries>` in the merged manifest** | **present**, naming `com.google.android.apps.maps` and `com.waze` |
| **`org.json` in the APK** | **absent** — the test-only scope held; the platform copy is used at runtime |
| `app-debug.apk` (**clean build**) | **9,633,673 bytes** (was 9,608,318 — +25,355) |
| Merged manifest permissions | **unchanged** — the same four |
| `app/schemas/…/AppDatabase/` | still contains **only `1.json`** — no schema change |
| `:app:compileDebugKotlin` | **NO-SOURCE** — still zero Kotlin source files |

### 15.1 The route appears

1. Make sure a store exists on the map. Tap its marker.
2. Tap **Get directions**. The details sheet must close and the Route Preview must open.
3. Confirm **"Calculating route…"** appears with a progress bar, and that **no line is on the map yet**.
4. Within a few seconds the sheet must show the store, the **distance**, the **estimated time**, and the
   **openrouteservice attribution**.
5. **The route line must be drawn on the map**, and the camera must have moved so the whole of it fits.
6. Confirm the line goes from **where you are** to **the store** — not to a point in the sea, and not from
   a point in the sea.

### 15.2 The line does not block anything

7. With the route drawn, **tap a store marker**. The Store Details sheet must still open — the line must not
   be swallowing taps.
8. **Long-press the map.** The Add Store form must still open.
9. Confirm the store pins are drawn **on top of** the route line.

### 15.3 Start navigation

10. Tap **Start navigation**. Google Maps must open, already navigating to the store.
11. Go back to the app. The preview must be closed and **the route line gone**.
12. If Google Maps is not installed, confirm Waze opens instead.
13. If neither is installed, confirm the message **"No navigation app found. Install Google Maps or
    Waze."** appears and the sheet stays open.

### 15.4 Cancel leaves nothing behind

14. Open the preview, wait for the route, then tap **Cancel**.
15. **The line must disappear from the map.** Nothing must be left drawn that nothing is explaining.

### 15.5 The failures are honest

16. **Turn off mobile data and Wi-Fi**, then open the preview. It must say **"Routing unavailable — check
    your connection."** with **Retry** and **Cancel** — and **no line must be drawn**.
17. Turn the connection back on and tap **Retry**. The route must appear.
18. In the Database Inspector, temporarily break the key (put a wrong value in `local.properties`, rebuild)
    and open the preview. It must say **"Could not calculate a route. Try again."** — not crash.
19. Restore the correct key and rebuild.

### 15.6 No location

20. Turn **location services off** on the device, then open the preview. It must say **"Waiting for your
    location before routing."** and draw nothing.
21. Turn location back on. The route must appear **by itself**, without closing and reopening the sheet.

### 15.7 Rotation

22. Open the preview, wait for the route, and **rotate the device**.
23. The summary must still be there, the line must still be drawn, and — importantly — **no second request
    should have been made**. Watch Logcat for a second `POST`, or check that the answer appears instantly
    rather than after a pause.

### 15.8 Nothing else changed

24. Store markers still draw (orange pending, green completed) and survive a restart.
25. The Home pin still works; Settings, Home Setup, Edit and Delete all still work.
26. The blue location dot and its accuracy circle still work.

**Report any failure with the step number and any Logcat text.**

---

## 16. EXPECTED RESULT

- Get directions opens the Route Preview and shows a real route from openrouteservice.
- **A line is drawn on the map** between the current location and the store, and the camera fits it.
- Distance and estimated time are shown, with the service's attribution.
- **Start navigation** opens Google Maps (or Waze) already navigating to the store.
- Cancel and Start both leave the map clean.
- A failed request says so and **draws nothing** — never a leftover line.
- No location is reported plainly, and resolves itself when a fix arrives.
- Rotating does not re-request the route.
- **No new permission, no schema change, and `org.json` is not in the APK.**

---

## 17. COMMON ERRORS AND FIXES

| Symptom | Cause | Fix |
| --- | --- | --- |
| **The route goes to the wrong place, or into the sea** | The coordinate order was swapped somewhere | Longitude is index 0 in the request and in the response — see §13.2. Both halves have tests |
| **Start navigation does nothing, with no error** | The `<queries>` element is missing from the manifest | Android 11+ cannot see Google Maps without it (§13.4) |
| The line stays on the map after Cancel | `clearRoute()` is not called on leaving, or `MapFragment` does not observe | The sheet clears it in `releaseRouteIfLeaving()`; the map clears on any non-`READY` state |
| A stale line is shown while a new route is loading | The `LOADING` state is drawing the previous plan | Only `READY` draws; everything else clears (§13.1) |
| "1,7 km" or a broken navigation URI on a European phone | A format without `Locale.US` | Both formatters pin the locale (§13.5) |
| A second request every time the device rotates | The route is cleared on every `onDestroy` | Guard with `isRemoving()` (§13.6) |
| "Routing is not set up in this build." | No `ORS_API_KEY` in `local.properties` | Phase 4.1 — add the line and clean build |
| "Could not calculate a route." with a working key | The response could not be parsed | Check Logcat for the HTTP status; 401/403 mean the key, 5xx means the service |
| The sheet says "Waiting for your location" forever | Location permission is not granted | The Map tab has the full permission flow; the button there grants it |
| `org.json` appears in the APK | The dependency was added as `implementation` rather than `testImplementation` | It must be test-only — Android already provides it |
| `clean` fails with a file-lock error | OneDrive holding a file in the build tree | Run it again — it is the folder, not the project |

---

## 18. ACCEPTANCE CHECKLIST

- [x] Clean build succeeds with all 41 tasks executed (§15.0 — verified)
- [x] **All 44 new tests pass** (§15.0 — verified; whole suite 188 tests, 0 failures)
- [x] **`<queries>` is present in the merged manifest** (§15.0 — verified)
- [x] **`org.json` is not in the APK** (§15.0 — verified)
- [x] Merged manifest permissions unchanged (§15.0 — verified)
- [x] `app/schemas/` still contains only `1.json` (§15.0 — verified)
- [ ] Get directions opens the Route Preview (§15.1)
- [ ] A real route is drawn on the map, from the user to the store (§15.1)
- [ ] The camera fits the route (§15.1)
- [ ] **The line does not block marker taps or long-press** (§15.2)
- [ ] **Start navigation opens Google Maps already navigating** (§15.3)
- [ ] Cancel leaves no line behind (§15.4)
- [ ] A failed request draws **nothing** and offers Retry (§15.5)
- [ ] No location is reported, and resolves itself (§15.6)
- [ ] **Rotation does not make a second request** (§15.7)
- [ ] Everything built before still works (§15.8)
- [ ] Confirmed: still **no Kotlin source file** anywhere in the project

---

## 19. UPDATED PROJECT STATE

**COMPLETED PHASES**
- ✅ 0.1 · 0.2 · 0.3 · 1.1 · 1.2 · 1.3 · 2.1 · 2.2 · 2.3 · 3.1 · 3.2 · 3.3 · 3.4 · 3.5 · 3.6 · 3.7
  — **Version 3 complete**
- ✅ 4.1 Routing Service Preparation
- 🔄 **4.2 Route Preview** — *awaiting your on-device test*

**CURRENT PHASE**
- Phase 4.2 — awaiting the on-device test

**CURRENT DATABASE VERSION**
- **1.** Unchanged. Three tables: `stores`, `completion_history`, `app_settings`.

**CURRENT FILES** (changed this phase)

```
app/src/main/java/com/cph/alpharoute/domain/RoutePoint.java                   (NEW)
app/src/main/java/com/cph/alpharoute/domain/RoutePlan.java                    (NEW)
app/src/main/java/com/cph/alpharoute/navigation/RouteState.java               (NEW)
app/src/main/java/com/cph/alpharoute/navigation/RouteResponseParser.java      (NEW)
app/src/main/java/com/cph/alpharoute/navigation/NavigationLauncher.java       (NEW)
app/src/main/java/com/cph/alpharoute/data/repository/RoutingRepository.java   (NEW)
app/src/main/java/com/cph/alpharoute/ui/route/RoutePreviewBottomSheet.java    (NEW)
app/src/main/java/com/cph/alpharoute/util/RouteTextFormatter.java            (NEW)
app/src/main/res/layout/sheet_route_preview.xml                              (NEW)

app/src/test/java/com/cph/alpharoute/navigation/RouteResponseParserTest.java  (NEW - 18 tests)
app/src/test/java/com/cph/alpharoute/navigation/NavigationLauncherTest.java   (NEW - 5 tests)
app/src/test/java/com/cph/alpharoute/util/RouteTextFormatterTest.java        (NEW - 15 tests)
app/src/test/java/com/cph/alpharoute/data/repository/RoutingRepositoryTest.java (NEW - 5 tests)

app/src/main/AndroidManifest.xml                                             (MODIFIED - <queries>)
app/src/main/java/com/cph/alpharoute/AlphaRouteApp.java                      (MODIFIED)
app/src/main/java/com/cph/alpharoute/ui/map/MapMarkerController.java         (MODIFIED - the polyline)
app/src/main/java/com/cph/alpharoute/ui/map/MapFragment.java                 (MODIFIED - observeRoute)
app/src/main/java/com/cph/alpharoute/ui/store/StoreDetailBottomSheet.java    (MODIFIED - Get directions)
app/src/main/java/com/cph/alpharoute/navigation/RoutingConfig.java           (MODIFIED - attribution)
app/src/main/res/layout/sheet_store_detail.xml                               (MODIFIED)
app/src/main/res/values/strings.xml                                          (MODIFIED)
app/build.gradle                                                             (MODIFIED)
gradle/libs.versions.toml                                                    (MODIFIED)
app/src/test/java/com/cph/alpharoute/navigation/RoutingConfigTest.java       (MODIFIED - +1 test)

docs/PHASE_4.2_ROUTE_PREVIEW.md                                              <- created this phase
docs/HANDOFF.md                                                              (MODIFIED)
```

**WORKING FEATURES** (verified)
- ✅ **188/188 unit tests pass** — 0 failures, 0 errors (verified 2026-09-25), including 44 new cases
- ✅ **Clean build succeeds** — all 41 tasks executed → APK 9,633,673 bytes
- ✅ **`<queries>` is in the merged manifest** — the hand-off will be able to see Google Maps
- ✅ **`org.json` is absent from the APK** — the test-only scope held
- ✅ **Schema unchanged** — only `1.json` exists
- ✅ **Permissions unchanged** — the same four
- ✅ **Still zero Kotlin source files** — `compileDebugKotlin` = `NO-SOURCE`
- ⏳ **The route on a device** — drawing, the camera fit, Start navigation, the failures — awaiting your
  §15.1–15.8

**KNOWN ISSUES**
- None blocking.
- **Start navigation and the `<queries>` element were built here rather than in 4.3** (§13.7). Phase 4.3
  keeps the foreground service, the permissions, the arrival notification and the session persistence.
- `lastSelectedStoreId` is **not** set by Start navigation — there is no session to resume until 4.3 (§13.7).
- The route is not cached. Rotating does not re-request it, but leaving the preview and opening it again does.
  At 2,000 requests a day on the free plan that is not a practical limit.
- Carried risks: OSM data quality in remote PH rural areas · openrouteservice free-tier quota ·
  **no offline map** (policy, risk R5) · date boundary (AD-7 + AD-11).
- The project is **not under version control**. Still the strongest recommendation in the project.
- Trademark note: "Alfamart" is a third-party brand; fine for a private solo tool (D-5).

**PENDING PHASES** (not implemented)
- **4.3 Navigation hand-off + arrival monitoring** — next
- 4.4 State recovery
- 5.1 Arrival-radius check · 5.2 Completion dialog · 5.3 Atomic completion transaction
- 6.1 Live tracker · 6.2 Daily history list · 6.3 Daily completion order · 6.4 Completion details ·
  6.5 Consistency check
- 7.1 Nearest pending store · 7.2 Recalculate after completion · 7.3 Home-aware heuristic · 7.4 No pending stores
- 8.1 Routing service review · 8.2 Road-based next store · 8.3 Multi-stop optimization · 8.4 Complete route preview
- 9.1 Local export · 9.2 Local import · 9.3 Optional cloud sync *(Firebase — deferred by your decision)*
- 10.1 Error handling · 10.2 Automated and manual testing · 10.3 Release preparation

---

## 20. STOP POINT

Phase 4.2 is complete. **26 files touched: 14 created, 12 modified. No removals, no renames.**

**The app now draws the way to a store.** Get directions → a real route from openrouteservice, drawn on the
map with the distance and the time → Start navigation → Google Maps takes over. That is the thing you asked
about, and it is in.

**Three things are worth calling out:**

1. **The coordinate order is the whole game.** openrouteservice takes and returns `[longitude, latitude]` —
   the reverse of how a coordinate is spoken. A swap is not an error: it is a valid request for a point in
   the wrong place, and the service answers with a valid route to it. The map draws a line and nothing
   fails. It is now swapped at both edges, tested in both directions, including the case a "bigger number
   must be the longitude" shortcut would get wrong — which happens to look *correct* in the Philippines.

2. **The `<queries>` element is the quiet one.** Without it, `resolveActivity()` returns null on Android 11
   even when Google Maps is installed, so Start navigation does nothing at all — no exception, no log, no
   message. Five lines of XML stand between this feature and an evening of confusion.

3. **The parser refuses rather than guesses, and one bad coordinate rejects the whole route.** A
   partially-read route draws a plausible line that is wrong, and a driver following it is worse off than
   one who was told to try again. That is the screen plan's "we never fake a route", implemented rather
   than merely intended.

**Please run §15 and report** — especially §15.1 (a real line, from you to the store), §15.2 (the line does
not block marker taps), §15.3 (Start navigation opens Google Maps already navigating), §15.5 (a failed
request draws **nothing**) and §15.7 (rotation does not re-request).

**Next phase on your instruction: Phase 4.3 — Navigation hand-off and arrival monitoring.** The
foreground location service, `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_LOCATION` + `POST_NOTIFICATIONS`,
the arrival-radius notification, `sheet_navigation_active.xml`, and `ActiveNavigationSession` persisted in
`lastSelectedStoreId` — so a drive survives the phone being locked and the app being backgrounded.

**Phase complete. Do not proceed to the next phase until all acceptance criteria pass.**

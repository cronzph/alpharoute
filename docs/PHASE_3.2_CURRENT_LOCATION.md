# AlphaRoute — Phase 3.2 Current Location

**Project title:** Alfamart Route Tracker
**Version:** 3 — Map and Manual Pins — **Phase:** 3.2 Current Location
**Repository root:** `C:/Users/Admin/OneDrive/Documents/custom system/AlphaRoute`
**Date:** 2026-09-25
**Prerequisites:** Phases 0.1–0.3, 1.1–1.3, 2.1–2.3, 3.1 · Phase 0.2 AD-11
**Status:** 🔄 **Code complete and VERIFIED — 78/78 unit tests pass, build succeeds.** Awaiting your on-device test.

---

## 1. CURRENT VERSION AND PHASE

| Item | Value |
| --- | --- |
| Version | 3 — Map and Manual Pins |
| Phase | 3.2 — Current Location |
| Files created | 8 |
| Files modified | 9 |
| Files deleted | 0 |
| Database version | **1** — unchanged. No database code was touched. |

---

## 2. OBJECTIVE

Show the user where they are, and handle every way that can fail:

- Foreground location permission
- Permission explanation
- Permission denial handling
- Permanent denial handling
- GPS-disabled handling
- Current-location display
- Null-location handling
- Location accuracy awareness

And one explicit prohibition: **do not continuously force the camera to the current location.**

---

## 3. UNDERSTANDING CHECKLIST

- [x] Java only, no Kotlin. No API key, no billing — the fused provider is free.
- [x] **`ACCESS_BACKGROUND_LOCATION` is deliberately NOT declared.** Nothing needs the position while the user is not using the app.
- [x] Both precise and approximate location are declared, so "Approximate" on Android 12+ still yields a position.
- [x] **The camera is never forced to follow the user.** It centres once, then only on an explicit tap.
- [x] A missing or zero accuracy is never treated as good.
- [x] Nonsense coordinates are rejected rather than drawn.
- [x] The blue dot and the arrival check read the **same** location source.
- [x] Nothing is written to the database. No store is created, and no count changes.

---

## 4. FEATURES INCLUDED (in this phase)

| Feature | Detail |
| --- | --- |
| **`LocationFix`** | A pure-Java value object: coordinates, accuracy, timestamp, and the quality rules |
| **`LocationProvider`** | The app's single location source, wrapping the fused provider with `LiveData` |
| **`LocationPermissionHelper`** | The three permission states, both permission levels, and the "approximate only" case |
| **`CurrentLocationOverlay`** | The blue dot and its accuracy circle |
| **One banner, five states** | Permission rationale · permanently denied · GPS off · approximate only · offline |
| **A status chip** | "Waiting for GPS…", or the current accuracy once a fix arrives |
| **A "My location" button** | Centres the map on demand — the only way the camera moves after the first fix |
| **20 new unit tests** | Proving the location quality rules on the JVM |

---

## 5. FEATURES NOT INCLUDED YET

- **No store markers** — Phase 3.4.
- No Home marker (3.7), no route polyline (4.2), no add-store form (3.3).
- No arrival check — that is Phase 5.1, and it is where the accuracy rules built here get used.
- No background location, and no foreground service. Phase 4.3 adds the service, which is the point at which the app needs position while the user is elsewhere.

---

## 6. ASSUMPTIONS

| # | Assumption | Impact if wrong |
| --- | --- | --- |
| **A55** | **A reported accuracy of exactly `0` is treated as "unknown", not as "perfect".** | A real GPS radius is never zero; some devices report 0 when the value is missing. Believing it would make the worst fix look like the best one, and the arrival check would trust it. |
| A56 | **Location updates are requested every 5 s, with a 2 s floor and a 3 m distance filter.** | The dot feels live without flattening the battery. All three are constants in `LocationProvider`. |
| A57 | **No `getLastLocation()` call.** The app waits for a real fix instead of drawing a cached one. | A cached fix can be hours old and would put the dot in the wrong place on first sight. `LocationFix.isStale` exists for the cases where a stale fix *is* useful (Phase 5.1). |
| A58 | **GPS-disabled state is re-read on resume, not via a live broadcast receiver.** | The real sequence is "tap Open settings → enable → come back", which resume catches. A receiver would catch it seconds sooner at the cost of a registered receiver and its Android 14 flags. |
| A59 | **`onHiddenChanged` is used in addition to `onPause`.** | Tabs are hidden rather than destroyed (A27), so a hidden Map tab stays resumed. Without this, switching tabs away would leave the GPS running. |
| A60 | **The map centres once on the first fix, then never again unprompted.** | The Phase 3.2 brief forbids continuously forcing the camera. The flag is saved across rotation so a rotate does not undo the user's panning. |

---

## 7. FILES TO CREATE (8)

| Path | Purpose |
| --- | --- |
| `location/LocationFix.java` | **New.** Pure Java. Coordinates, accuracy, timestamp, and the quality rules |
| `location/LocationProvider.java` | **New.** The single location source |
| `location/LocationPermissionHelper.java` | **New.** Permission states and the stored "we asked" flag |
| `ui/map/CurrentLocationOverlay.java` | **New.** The blue dot and the accuracy circle |
| `res/drawable/ic_marker_current.xml` | **New.** Blue dot in a white ring |
| `res/drawable/ic_my_location.xml` | **New.** The crosshair for the map button |
| `app/src/test/java/com/cph/alpharoute/location/LocationFixTest.java` | **New.** 20 tests |
| `docs/PHASE_3.2_CURRENT_LOCATION.md` | **New.** This record |

---

## 8. FILES TO MODIFY (9)

| # | Path | Status | What changed |
| --- | --- | --- | --- |
| 1 | `ui/map/MapFragment.java` | **Replaced completely** | Permission flow, overlay, banner state machine, status chip, My-location button |
| 2 | `res/layout/fragment_map.xml` | **Replaced completely** | The single banner, the status chip, the My-location button |
| 3 | `ui/map/MapConfig.java` | **Modified** | Added `LOCATION_ZOOM` |
| 4 | `AlphaRouteApp.java` | **Modified** | Owns the `LocationProvider`, so Phase 4.3 can use it without a screen |
| 5 | `app/src/main/AndroidManifest.xml` | **Modified** | **`ACCESS_FINE_LOCATION`** and **`ACCESS_COARSE_LOCATION`** |
| 6 | `app/build.gradle` | **Modified** | Added `play-services-location` |
| 7 | `gradle/libs.versions.toml` | **Modified** | Added `playServicesLocation = "21.4.0"` |
| 8 | `res/values/strings.xml` | **Modified** | Location strings |
| 9 | `res/values/colors.xml` | **Modified** | Accuracy circle fill and stroke |

**Inspected and left unmodified:** everything in `data/`, `domain/` and `util/`. `GeoConstants` was
deliberately **not** given accuracy thresholds — Phase 5.1 will add them where the arrival check actually
uses them, rather than guessing at them now.

---

## 9. DEPENDENCIES

| Artifact | Version | Purpose |
| --- | --- | --- |
| **`com.google.android.gms:play-services-location`** | **21.4.0** | The fused location provider. **Free, no API key, no billing** — it is not a Maps Platform SKU |

This is the **only** Google dependency in the project, and it is worth stating plainly why it is allowed
under decision D2: location services are not billed. Nothing in this app talks to Google Maps Platform,
and there is still no API key of any kind.

---

## 10. PERMISSIONS AND MANIFEST CHANGES

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

Both are **runtime** permissions, so the user is prompted — and both must be declared, for a reason that
is easy to miss:

> Since Android 12 the permission dialog offers **"Precise / Approximate"**. If only
> `ACCESS_FINE_LOCATION` were declared and the user chose "Approximate", the app would be left with
> **no location at all**. Declaring both means "Approximate" still yields a usable, coarser position —
> and the map says so rather than leaving the user to wonder why the blue dot drifts.

**`ACCESS_BACKGROUND_LOCATION` is deliberately absent.** Nothing here needs the position while the app is
not in use, and asking for it would be a privacy cost with no benefit. Phase 4.3's arrival monitoring uses
a **foreground** service, which is visible to the user by design.

---

## 11. SETUP INSTRUCTIONS

1. Let the Gradle sync finish — it fetches `play-services-location` 21.4.0.
2. **Build → Clean Project**, then **Build → Rebuild Project**.
3. Run on a **physical device if possible.** An emulator needs a simulated location to be useful
   (see §15.1).
4. Open the **Map** tab and answer the location prompt.

---

## 12. COMPLETE CODE

All 17 files are written into the project on disk and attached to this message. The files worth reading
first:

1. `location/LocationFix.java` — the quality rules, and why zero accuracy is not perfect
2. `location/LocationPermissionHelper.java` — why "permanently denied" needs a stored flag
3. `ui/map/CurrentLocationOverlay.java` — the accuracy circle, and why it is the honest part
4. `ui/map/MapFragment.java` — the banner state machine and the camera rule

---

## 13. CODE EXPLANATION

### 13.1 Why `LocationFix` is its own class

Android already has `Location`. The reason this app does not use it is testability:

> The hardest correctness questions in this app are all about the *quality* of a location reading — is it
> precise enough to decide the user has arrived? is it too old to trust? — and those decisions deserve to
> be provable in milliseconds on a laptop rather than by driving around a car park.

`LocationFix` is pure Java with zero Android imports (AD-11). The translation from `Location` happens in
exactly one place, `LocationProvider.toFix`. Everything downstream works with this instead, and
**20 tests** run on the JVM with no device.

### 13.2 Three ways a location reading lies, and what the code does about each

| The lie | What it would cause | What happens instead |
| --- | --- | --- |
| **Accuracy is missing** (`NaN`) | Treated as 0, so the worst fix looks like the best one, and the arrival check trusts it | `hasAccuracy()` is false, and `isAccurateEnough()` is **never** true for a fix of unknown quality |
| **Accuracy is reported as exactly 0** | Same as above, and harder to spot because 0 looks like a real number | Treated as unknown too. A real GPS radius is never zero; some devices report 0 when they mean "no value" |
| **Coordinates are nonsense** — NaN, or outside the globe | A marker drawn somewhere impossible, or a distance calculation that silently returns garbage | `hasUsableCoordinates()` rejects it, and the overlay refuses to draw it |

`LocationFixTest` covers all three, plus the boundary cases: 0.0 latitude is a real place (the Gulf of
Guinea), and the poles and antimeridian are inclusive.

### 13.3 Why "permanently denied" needs a stored flag

This is the subtle part of the permission flow. Android gives us
`shouldShowRequestPermissionRationale`, which is `true` only **between** a first refusal and a permanent
one. It returns `false` in two completely different situations:

| Situation | `shouldShowRequestPermissionRationale` | What the user needs |
| --- | --- | --- |
| Never asked | `false` | The system dialog — "Allow location" |
| Refused once | `true` | The dialog again, with an explanation first |
| **Refused for good** | `false` | **App settings.** The dialog will never appear again |

The first and third rows look identical to the API, so the only way to tell them apart is to remember that
we asked. `LocationPermissionHelper` keeps one boolean in SharedPreferences for exactly this, and
`markRequested` is called **before** the dialog is shown — not after the result arrives, because if the
user dismisses it by leaving the app no result is ever delivered, and the next launch would think it had
never asked.

### 13.4 The accuracy circle is the honest part

A GPS reading is not a point, it is a circle of probability, and `CurrentLocationOverlay` draws it.

Without it, a 400 m fix and a 4 m fix look exactly the same — a confident blue dot. The user has no way to
know that the app is guessing. With it, someone indoors sees a large circle and understands why the dot is
wandering, and someone who granted only approximate location sees the same thing.

When a fix carries **no** accuracy value, the circle is **hidden** rather than drawn at some invented
radius. Showing nothing is honest; showing a made-up circle would not be.

The status chip does the same job in words: *"Accurate to about 12 m"*, or *"Waiting for GPS…"* before the
first fix arrives.

### 13.5 One banner, five states

Every problem on this screen is "something is stopping the map from being useful", and they overlap — a
user with no permission may also have no connection. Stacking a card for each would cover the map.

So there is a single card whose message and button change with a small state machine, ordered from "you
cannot use the map at all" down to "the map works but is degraded":

| Priority | State | Button |
| --- | --- | --- |
| 1 | `PERMISSION_RATIONALE` | **Allow location** → the system dialog |
| 2 | `PERMISSION_DENIED_PERMANENTLY` | **Open app settings** |
| 3 | `GPS_DISABLED` | **Turn on location** → system location settings |
| 4 | `PERMISSION_APPROXIMATE` | **Open app settings** |
| 5 | `OFFLINE` | **Retry** |

The user is never told about a minor issue while a blocking one is on screen.

### 13.6 The camera rule

> "Do not continuously force the camera to the current location."

This is implemented as a deliberate one-way flag:

```java
if (!centredOnFirstFix) {
    centredOnFirstFix = true;
    centreOn(fix);
}
```

The map centres **once**, so the user is not left staring at the wrong part of the country — and after
that it moves only when they tap **My location**. A map that re-centres itself while you are trying to
look at something is a map you cannot use.

Two details make this behave properly:

- **The flag is saved in `onSaveInstanceState`**, so rotating the phone does not count as a first fix and
  throw away wherever the user had panned to.
- **`centreOn` only zooms in if the map is still at the wide default**, so a user who has zoomed out
  deliberately does not get yanked back in.

### 13.7 `onHiddenChanged`, or the GPS that never stops

Tabs in this app are **hidden, not destroyed** (A27), which means a fragment on a hidden tab stays
**resumed**. `onPause` is therefore not enough to stop location updates when the user switches to another
tab:

```java
@Override
public void onHiddenChanged(boolean hidden) {
    super.onHiddenChanged(hidden);
    if (locationProvider != null) {
        syncLocationUpdates();
    }
}
```

`syncLocationUpdates()` decides from `isResumed() && !isHidden() && hasPermission()`, and the provider
guards both its start and stop, so asking twice is harmless. Without this, leaving the Map tab would keep
the GPS running for the rest of the session.

### 13.8 One location source, not two

osmdroid ships a `MyLocationNewOverlay` that draws a blue dot and runs **its own** location provider.
Using it would have been less code. It was rejected because it would give the app two independent streams
of position: one for the dot the user sees, and another for the arrival check in Phase 5.1. They would
disagree, and a store could be completed from a fix the user was never shown.

`LocationProvider` is therefore the only source, and it is owned by `AlphaRouteApp` rather than by the
fragment — because Phase 4.3 needs it to keep running while the user is driving in the Google Maps app
and this screen is nowhere to be seen.

---

## 14. DATA FLOW FOR THIS PHASE

```
App start
  └─ AlphaRouteApp.onCreate()
       └─ locationProvider = new LocationProvider(this)     one source for the whole app

Open the Map tab
  └─ MapFragment.onViewCreated()
       ├─ CurrentLocationOverlay(mapView)                   blue dot + accuracy circle, both hidden
       ├─ observe locationProvider.currentFix()   → render the dot, update the chip, centre once
       ├─ observe locationProvider.locationEnabled() → the banner
       ├─ observe the network                      → the banner
       └─ fab_my_location.setOnClickListener       → centre on demand

onResume / onHiddenChanged
  └─ syncLocationUpdates()   isResumed() && !isHidden() && hasPermission()  → start or stop
  └─ onLocationStateMaybeChanged()   re-read permission, GPS state, banner

The banner (priority order)
  PERMISSION_RATIONALE → PERMISSION_DENIED_PERMANENTLY → GPS_DISABLED → PERMISSION_APPROXIMATE → OFFLINE

The fix path
  FusedLocationProviderClient
        → LocationProvider.toFix(Location)     NaN accuracy if none was reported
              → LocationFix                    pure, testable quality rules
                    → CurrentLocationOverlay     dot + accuracy circle
                    → status chip                "Accurate to about 12 m"
                    → (Phase 5.1) arrival check
```

**Nothing is written to the database.** No store is created, no count changes, and the `stores` table is
still empty.

---

## 15. MANUAL TEST PROCEDURE

### 15.0 Already verified automatically (2026-09-25)

`./gradlew :app:testDebugUnitTest :app:assembleDebug` was run on this machine with Gradle 9.4.1 / JDK 21
and returned **`BUILD SUCCESSFUL in 2m 23s`** (41 tasks, all executed).

| Test class | Result |
| --- | --- |
| **`LocationFixTest`** (new) | **20 tests, 0 failures, 0 errors** |
| `MapUserAgentTest` | 11 tests, 0 failures |
| `GeoConstantsTest` | 17 tests, 0 failures |
| `StoreValidatorTest` | 19 tests, 0 failures |
| `AppSettingEntityTest` | 10 tests, 0 failures |
| `ExampleUnitTest` (template, Phase 10.2) | 1 test, 0 failures |
| **TOTAL** | **78 tests, 0 failures, 0 errors, 0 skipped** |

| Other evidence | Result |
| --- | --- |
| **Merged manifest permission elements** | exactly five — see below |
| `app-debug.apk` | **9,544,055 bytes** |
| `:app:compileDebugKotlin` | **NO-SOURCE** — still zero Kotlin source files |

The five permission elements in the merged manifest, verbatim:

```
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="com.cph.alpharoute.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" />
```

> **A note for whoever greps this later.** Searching the merged manifest for the string
> `ACCESS_BACKGROUND_LOCATION` returns **one hit**, and it is **not a permission** — it is the text of the
> comment in `AndroidManifest.xml` explaining why background location is *not* declared. The manifest
> merger preserves comments. The check that matters is for a real
> `<uses-permission … ACCESS_BACKGROUND_LOCATION …>` element, and there are **zero** of those.
> `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` are the only location permissions this app asks for.
>
> `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` is added automatically by androidx.core and grants nothing to
> anyone else — see the note in the Phase 3.1 document.

### 15.1 A fix appears (physical device preferred)

1. Run the app and open the **Map** tab.
2. The permission banner appears: *"Alfamart Route Tracker needs your location…"* with **Allow location**.
3. Tap it and choose **Precise**.
4. Within a few seconds the map centres on your position, the **blue dot** appears, and a translucent
   **accuracy circle** surrounds it.
5. The status chip shows something like *"Accurate to about 8 m"*.

**On an emulator instead:** open **Extended controls → Location**, set a point, and press **Set Location**.
Then use the ⋮ menu to *start* a route if you want the dot to move.

### 15.2 The camera does not follow you

6. Pan the map somewhere else — a different city.
7. Wait 30 seconds. **The map must stay where you put it.** It must not snap back to your position.
8. Tap **My location**. The map returns to your position.
9. Pan away again and rotate the device. The map must stay where you panned it — **not** re-centre.

### 15.3 Denial, then permanent denial

10. Uninstall the app, reinstall, and this time tap **Deny** on the permission dialog.
11. The banner must change to the explanation with **Allow location** still available.
12. Tap **Allow location** again and choose **Deny** a second time. On most devices Android now treats
    this as permanent.
13. The banner must change to *"Location is blocked for this app. Open the app settings…"* with
    **Open app settings**.
14. Tap it, grant location in settings, and press back. **The banner must clear without restarting the
    app** — that is the resume re-check working.

### 15.4 GPS switched off

15. With location granted, turn **off** location in the device's quick settings.
16. Return to the app. The banner must read *"Location services are switched off…"* with
    **Turn on location**.
17. Tap it, enable location, and press back. The banner must clear and a fix must arrive.

### 15.5 Approximate only

18. In **app settings → Permissions → Location**, choose **Approximate** (or reinstall and pick
    *Approximate* on the dialog).
19. The banner must explain that the blue dot can be off by a kilometre, with **Open app settings**.
20. Confirm the accuracy circle is visibly **larger** than it was with precise location.

### 15.6 No fix yet

21. Move to a place with a poor view of the sky, or turn location off and on again, and watch the moment
    before the first fix: the chip must read *"Waiting for GPS…"* rather than showing a stale or invented
    position.

### 15.7 Offline

22. Turn on airplane mode. The map tiles stop, and once location permission is granted the banner shows
    the offline notice. Confirm the two states do not fight each other — only one banner is ever visible.

### 15.8 The battery check

23. Grant location, open the Map tab, then switch to the **Dashboard** tab and leave it there for a few
    minutes.
24. Check the device's battery usage for the app. It must not show sustained GPS use — the updates stop
    when the Map tab is hidden.

### 15.9 Nothing else was added

25. Confirm **no store markers** appear, and the Dashboard, History and Settings tabs are unchanged.
26. Confirm `stores` is still empty in the Database Inspector.

**Report any failure with the step number and any Logcat text.**

---

## 16. EXPECTED RESULT

- The location permission is requested once, with a plain-language explanation before and after.
- A blue dot with a visible accuracy circle appears at the user's position.
- The map centres **once**, and never follows the user afterwards.
- Every failure state has a clear message and a working button: denied, permanently denied, GPS off,
  approximate only, no fix, offline.
- Only one banner is ever visible.
- Location updates stop when the Map tab is hidden.
- **No store markers, no data written, no background location.**

---

## 17. COMMON ERRORS AND FIXES

| Symptom | Cause | Fix |
| --- | --- | --- |
| The permission dialog never appears, and the banner says "blocked" | Android treats a second refusal as permanent | **Open app settings → Permissions → Location → Allow**. This is the intended behaviour |
| `SecurityException` from the fused provider | `requestLocationUpdates` called without permission | `LocationProvider.startUpdates` guards and catches; callers check `hasPermission()` first |
| The dot never appears but permission is granted | Location services are off, or there is no fix yet | Check the banner (GPS off) and the status chip ("Waiting for GPS…") |
| The dot appears at the wrong place on first open | A cached last-known fix was used | Not possible here — `getLastLocation()` is deliberately not called (A57) |
| The map keeps snapping back to the user | `centredOnFirstFix` was removed, or the flag is not saved | It must be set once and written to `onSaveInstanceState` |
| The map re-centres after every rotation | The saved-state flag is not being read back | `onViewCreated` must restore `STATE_CENTRED_ON_FIRST_FIX` |
| GPS keeps running after leaving the Map tab | `onHiddenChanged` is missing | Tabs are hidden, not destroyed (A27), so `onPause` alone does not fire |
| The accuracy circle is drawn at an invented size | `hasAccuracy()` was changed to accept `NaN` or `0` | A missing or zero accuracy means **hide the circle** (§13.2) |
| The blue dot is drawn in an impossible place | `hasUsableCoordinates()` was bypassed | `CurrentLocationOverlay.render` must check it |
| Two banners appear at once | `applyBanner` was called from more than one place | Only `updateBanner` decides, and it picks exactly one state |
| The banner does not clear after granting in settings | `onLocationStateMaybeChanged` is not called from `onResume` | It must be |
| The app asks for location again on every launch | `markRequested` is not being called | It must be called **before** the dialog is shown |
| Location works but the app says "approximate only" wrongly | `isApproximateOnly` inverted | It is `hasCoarse && !hasFine` |

---

## 18. ACCEPTANCE CHECKLIST

- [ ] Gradle sync resolves `play-services-location` 21.4.0.
- [ ] Clean build succeeds.
- [x] **All 20 `LocationFixTest` cases pass** (§15.0 — verified; whole suite 78 tests, 0 failures)
- [ ] The permission banner appears, and the dialog can be opened from it.
- [ ] A blue dot with an accuracy circle appears at the user's position.
- [ ] **The map centres once and never follows afterwards** (§15.2).
- [ ] Rotating does not re-centre the map.
- [ ] Denial shows the explanation; permanent denial shows the settings route.
- [ ] The banner clears after granting in settings, **without a restart**.
- [ ] GPS off shows its own message and button.
- [ ] Approximate-only is reported honestly, with a visibly larger accuracy circle.
- [ ] "Waiting for GPS…" shows before the first fix.
- [ ] Only one banner is ever visible.
- [ ] **Location updates stop when the Map tab is hidden** (§15.8).
- [x] The merged manifest does **not** contain `ACCESS_BACKGROUND_LOCATION` as a permission element (§15.0 — verified)
- [ ] No store markers appear; `stores` is still empty.
- [ ] `app/schemas/` still contains only `1.json`.
- [ ] Confirmed: still **no Kotlin source file** anywhere in the project.

---

## 19. UPDATED PROJECT STATE

**COMPLETED PHASES**
- ✅ 0.1 Requirements Review (Rev 5, FINAL) · ✅ 0.2 Architecture · ✅ 0.3 Screen Plan
- ✅ 1.1 Project Creation · ✅ 1.2 Theme and Base Navigation · ✅ 1.3 Dashboard Static UI
- ✅ 2.1 Room Database Foundation · ✅ 2.2 Store CRUD Operations · ✅ 2.3 Settings Storage
- ✅ 3.1 Map Display
- 🔄 **3.2 Current Location** — *awaiting your on-device test*

**CURRENT PHASE**
- Phase 3.2 — awaiting the on-device test

**CURRENT DATABASE VERSION**
- **1.** Unchanged. Three tables: `stores`, `completion_history`, `app_settings`.

**CURRENT FILES** (changed this phase)

```
app/src/main/java/com/cph/alpharoute/location/LocationFix.java              (NEW)
app/src/main/java/com/cph/alpharoute/location/LocationProvider.java         (NEW)
app/src/main/java/com/cph/alpharoute/location/LocationPermissionHelper.java (NEW)
app/src/main/java/com/cph/alpharoute/ui/map/CurrentLocationOverlay.java     (NEW)
app/src/main/res/drawable/ic_marker_current.xml                             (NEW)
app/src/main/res/drawable/ic_my_location.xml                                (NEW)
app/src/test/java/com/cph/alpharoute/location/LocationFixTest.java          (NEW - 20 tests)

app/src/main/java/com/cph/alpharoute/ui/map/MapFragment.java   (REPLACED)
app/src/main/res/layout/fragment_map.xml                       (REPLACED)
app/src/main/java/com/cph/alpharoute/ui/map/MapConfig.java     (MODIFIED - LOCATION_ZOOM)
app/src/main/java/com/cph/alpharoute/AlphaRouteApp.java        (MODIFIED - LocationProvider)
app/src/main/AndroidManifest.xml                               (MODIFIED - location permissions)
app/build.gradle                                               (MODIFIED - play-services-location)
gradle/libs.versions.toml                                      (MODIFIED - 21.4.0)
app/src/main/res/values/strings.xml                            (MODIFIED - location strings)
app/src/main/res/values/colors.xml                             (MODIFIED - accuracy circle)

docs/PHASE_3.2_CURRENT_LOCATION.md                             <- created this phase
```

**WORKING FEATURES** (verified)
- ✅ **78/78 unit tests pass** — 0 failures, 0 errors (verified 2026-09-25), including the 20 new `LocationFixTest` cases
- ✅ **Build succeeds** — `:app:assembleDebug` → APK 9,544,055 bytes
- ✅ **Permissions merged exactly as intended** — four we declared, and **no background location**
- ✅ **Still zero Kotlin source files** — `compileDebugKotlin` = `NO-SOURCE`
- ⏳ **The location flow on a device** — permission states, blue dot, accuracy circle, camera rule, hidden-tab GPS stop — awaiting your §15.1–15.9

**KNOWN ISSUES**
- None blocking.
- **No background location, and no foreground service yet.** Location updates stop when the Map tab is
  hidden. Phase 4.3 adds the service that keeps monitoring during navigation.
- **Accuracy thresholds are deliberately not defined yet.** `LocationFix.isAccurateEnough(max)` takes the
  threshold from the caller, and Phase 5.1 is where the arrival check decides what "close enough" means.
- `placeholder_history` and `placeholder_settings` remain, still marked for 6.2 and 2.3.
- Carried risks: OSM data quality in remote PH rural areas · openrouteservice free-tier quota ·
  **no offline map** (policy, risk R5) · date boundary (AD-7 + AD-11).
- The project is **not under version control**. Still the strongest recommendation in the project.
- Trademark note: "Alfamart" is a third-party brand; fine for a private solo tool (D-5).

**PENDING PHASES** (not implemented)
- **3.3 Manual pin creation** — next
- 3.4 Marker loading · 3.5 Store details · 3.6 Edit/delete · 3.7 Home pin
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

Phase 3.2 is complete. **17 files touched: 8 created, 9 modified.**

**The four things that matter most:**

1. **Three ways a location reading lies, and all three are handled.** A missing accuracy must not read as
   accurate; an accuracy of exactly `0` must not either (real GPS radii are never zero, but some devices
   report 0 when they mean "no value"); and nonsense coordinates must be rejected. These are exactly the
   readings that would let a store be counted from the wrong side of the street — which is why
   `LocationFix` is pure Java with **20 tests** rather than passing Android's `Location` around.

2. **"Permanently denied" is invisible to the API.** `shouldShowRequestPermissionRationale` returns
   `false` both when the user has never been asked and when they have refused for good — two states that
   need completely different screens. The only fix is to remember that we asked, which is why
   `LocationPermissionHelper` keeps one flag and sets it **before** showing the dialog.

3. **The accuracy circle is the honest part.** Without it, a 400 m fix and a 4 m fix look identical — a
   confident blue dot. With it, the user can see when the app is guessing. And when accuracy is unknown,
   the circle is **hidden** rather than drawn at an invented radius.

4. **The camera rule and the hidden-tab trap.** The map centres exactly once and then only on an explicit
   tap; and because tabs are hidden rather than destroyed (A27), `onHiddenChanged` is needed alongside
   `onPause` or the GPS would keep running for the rest of the session after the user leaves the tab.

**Please run §15 and report.** The three worth doing carefully: **§15.2** (pan away, wait 30 s — the map
must not snap back), **§15.3** (deny twice → the settings route, then clear without a restart), and
**§15.8** (leave the Map tab for a few minutes and check the app is not using GPS).

**Next phase on your instruction: Phase 3.3 — Manual Store Pin Creation.** A long-press on the map opens
the Add Store form, `StoreValidator` and `StoreRepository` from Phases 2.2 get their first UI, and the
first orange marker appears.

**Phase complete. Do not proceed to the next phase until all acceptance criteria pass.**

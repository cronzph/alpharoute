# AlphaRoute — Phase 3.1 Map Display

**Project title:** Alfamart Route Tracker
**Version:** 3 — Map and Manual Pins — **Phase:** 3.1 Map Display
**Repository root:** `C:/Users/Admin/OneDrive/Documents/custom system/AlphaRoute`
**Date:** 2026-09-25
**Prerequisites:** Phases 0.1–0.3, 1.1–1.3, 2.1–2.3 · Phase 0.1 **D1** (the frozen stack)
**Status:** 🔄 **Code complete and VERIFIED — 58/58 unit tests pass, build succeeds.** Awaiting your on-device test.

> **Two adaptations from the original brief, both already approved.**
>
> | Brief | Actual | Why |
> | --- | --- | --- |
> | "Google Map display" | **OpenStreetMap drawn by osmdroid** | Decision **D1**, approved 2026-09-25. Google Maps requires a billing account with a payment method, even for its free tier. |
> | "Secure API-key setup" | **Not applicable — there is no key at all** | osmdroid needs no key, no account and no billing. This is a security *improvement*: there is no credential in this app that could leak. |

---

## 1. CURRENT VERSION AND PHASE

| Item | Value |
| --- | --- |
| Version | **3 — Map and Manual Pins** (this phase opens it) |
| Phase | 3.1 — Map Display |
| Files created | 5 |
| Files modified | 7 |
| Files deleted | 0 |
| Database version | **1** — unchanged. This phase touches no database code. |

---

## 2. OBJECTIVE

Display a map inside the app, and satisfy every requirement that comes with the tile source:

- The map dependency
- Manifest configuration and the internet permission
- Map loading state
- Map error state
- A default camera position only when necessary

**No manual pins yet.** Phase 3.1 deliberately draws no markers of any kind.

---

## 3. UNDERSTANDING CHECKLIST

- [x] Java only, no Kotlin. No API key, no billing account, no card.
- [x] **The OpenStreetMap tile usage policy is fully implemented** — see §13.2.
- [x] The app identifies itself with a distinct, stable User-Agent.
- [x] OpenStreetMap attribution is visible on the map at all times.
- [x] Tiles are cached locally, with a bounded cache.
- [x] No bulk download, no offline prefetch, no HTTP URL, no generic User-Agent.
- [x] Only two permissions, both install-time: `INTERNET` and `ACCESS_NETWORK_STATE`.
- [x] No location permission yet — that is Phase 3.2.
- [x] No store data, coordinate or marker is created in this phase.

---

## 4. FEATURES INCLUDED (in this phase)

| Feature | Detail |
| --- | --- |
| **The map** | osmdroid `6.1.20` drawing OpenStreetMap raster tiles |
| **Tile policy compliance** | Explicit tile URL, app User-Agent, attribution, bounded disk cache |
| **Loading state** | A thin indeterminate bar until the map surface is laid out |
| **Error state** | A live offline banner that explains a blank map instead of leaving the user guessing |
| **Default camera** | A Philippines-wide view, used only on a cold start |
| **Correct lifecycle** | `onResume` / `onPause` / `onDetach`, so the map neither stalls nor leaks |
| **11 new unit tests** | Proving the User-Agent format, which silently breaks the map if wrong |

---

## 5. FEATURES NOT INCLUDED YET

- **No store markers.** Orange and green pins arrive in Phase 3.4.
- **No current-location dot.** Phase 3.2 — and it also replaces the default camera.
- No Home marker (3.7), no route polyline (4.2), no add-store form (3.3).
- No location permission.
- No tap or long-press handling on the map.

---

## 6. ASSUMPTIONS

| # | Assumption | Impact if wrong |
| --- | --- | --- |
| A49 | **`MapTileConfig` was renamed `MapConfig`.** The Phase 0.2 architecture used the narrower name, but the class also owns the default camera, and "TileConfig" holding a camera default would mislead. | Cosmetic. One name in the Phase 0.2 document is now superseded. |
| A50 | **The default camera is a Philippines-wide view** (12.8797 N, 121.7740 E, zoom 5.5). | A *view* default, not data. No store coordinate is invented. Phase 3.2 replaces it with the user's real location. |
| A51 | **The attribution is a `TextView`, not osmdroid's `CopyrightOverlay`.** | Guarantees it is on screen and never covered, which is what the policy requires. The overlay draws on the canvas and can be obscured by later UI. |
| A52 | **The attribution text lives in `MapConfig`, not `strings.xml`.** | The one deliberate exception to the project's string rule: it is provider metadata that must match the tile source, so the two are written side by side. |
| A53 | **The tile cache is capped at 200 MB, trimming to 150 MB.** | The policy requires caching but forbids unlimited growth. Both bounds are constants in `MapConfig`. |
| A54 | **The User-Agent has no contact URL yet.** | The policy asks for one so the tile operators can reach the author. It is left out rather than invented — **add your own before any public distribution.** |

---

## 7. FILES TO CREATE (5)

| Path | Purpose |
| --- | --- |
| `ui/map/MapConfig.java` | **New.** Tile source, User-Agent, cache bounds, default camera, one-time setup |
| `ui/map/MapUserAgent.java` | **New.** Builds the User-Agent. Pure Java, so it is testable |
| `res/layout/fragment_map.xml` | **New.** The map, the loading bar, the offline banner, the attribution |
| `app/src/test/java/com/cph/alpharoute/ui/map/MapUserAgentTest.java` | **New.** 11 tests |
| `docs/PHASE_3.1_MAP_DISPLAY.md` | **New.** This record |

---

## 8. FILES TO MODIFY (7)

| # | Path | Status | What changed |
| --- | --- | --- | --- |
| 1 | `ui/map/MapFragment.java` | **Replaced completely** | Was a placeholder; now hosts the `MapView` |
| 2 | `AlphaRouteApp.java` | **Modified** | Calls `MapConfig.initialise(this)` before anything else |
| 3 | `app/build.gradle` | **Modified** | Added `implementation libs.osmdroid.android` |
| 4 | `gradle/libs.versions.toml` | **Modified** | Added `osmdroid = "6.1.20"` and the library entry |
| 5 | `app/src/main/AndroidManifest.xml` | **Modified** | **`INTERNET`** and **`ACCESS_NETWORK_STATE`** — the app's first permissions |
| 6 | `res/values/strings.xml` | **Modified** | Added the offline notice; **removed `placeholder_map`** (the Map placeholder is gone) |
| 7 | `res/values/colors.xml` | **Modified** | Added the attribution scrim and text colours |

**Inspected and left unmodified:** everything in `data/`, `domain/` and `util/`. This phase adds a
presentation layer only — **no database, no schema, no repository, no test of the existing logic.**

---

## 9. DEPENDENCIES

| Artifact | Version | Purpose |
| --- | --- | --- |
| **`org.osmdroid:osmdroid-android`** | **6.1.20** | The map. Apache-2.0. Latest release (Aug 2024), verified on Maven Central. No key, no account, no billing |

**Not added:** `play-services-maps`, the Google Navigation SDK, the Google Routes API — all removed by
decision D2 and replaced by D1. Nothing Google Maps Platform appears anywhere in this project, and there
is **no API key of any kind**.

---

## 10. PERMISSIONS AND MANIFEST CHANGES

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

| Permission | Why | Prompt? |
| --- | --- | --- |
| `INTERNET` | Download OpenStreetMap tiles | **No** — install-time |
| `ACCESS_NETWORK_STATE` | So the Map screen can say *"map tiles unavailable"* rather than showing a blank grid and letting the user guess. It reports only whether a network exists; it reads nothing about the user | **No** — install-time |

**Neither permission prompts the user**, and no location permission is requested yet. That arrives in
Phase 3.2, and only then.

No storage permission is needed: osmdroid caches tiles in the app's own cache directory, which has
required no permission since API 19.

---

## 11. SETUP INSTRUCTIONS

1. Let the Gradle sync finish — it fetches osmdroid 6.1.20 on this sync.
2. **Build → Clean Project**, then **Build → Rebuild Project**.
3. Run on a device or emulator (API 24+).
4. Open the **Map** tab.

**On an emulator:** tiles download over the host's internet connection, which normally works. If the map
stays blank on an emulator but the offline banner is *not* shown, that is a tile-server problem rather
than a connection problem — check Logcat for the tag `AlphaRouteMap`.

---

## 12. COMPLETE CODE

All 12 files are written into the project on disk and attached to this message. The files worth reading
first:

1. `ui/map/MapConfig.java` — the whole tile policy, in one place
2. `ui/map/MapUserAgent.java` — the requirement that silently breaks the map if missed
3. `ui/map/MapFragment.java` — the lifecycle calls and the two states
4. `res/layout/fragment_map.xml` — the layout, and a list of what is deliberately not in it yet

---

## 13. CODE EXPLANATION

### 13.1 The tile source is written down explicitly, not inherited

```java
private static final String OSM_TILE_BASE_URL = "https://tile.openstreetmap.org/";

public static XYTileSource tileSource() {
    return new XYTileSource(TILE_SOURCE_NAME, MIN_ZOOM, MAX_ZOOM, TILE_SIZE_PX,
            ".png", new String[]{OSM_TILE_BASE_URL}, OSM_COPYRIGHT);
}
```

The obvious alternative is `TileSourceFactory.MAPNIK`, which is one line. It was rejected for two
reasons:

- **The policy names this exact URL** and warns that "other subdomains or hostnames may be slower or
  withdrawn without notice". Building the source here means the app uses the URL the policy specifies,
  rather than whatever a library default happens to be after an update.
- **The policy recommends not hard-coding the tile URL**, and it appears exactly once in this project —
  in a class whose entire job is map configuration. Swapping providers is a one-line change.

### 13.2 The four policy requirements, and where each is satisfied

| Policy requirement | Where | What happens if it is missed |
| --- | --- | --- |
| **A distinct, stable User-Agent** | `MapUserAgent.build(...)`, applied in `MapConfig.initialise()` | Traffic using a generic library default **"will be blocked"** — a blank map, with a cause invisible from the screen. §13.3 |
| **The correct HTTPS tile URL** | `MapConfig.OSM_TILE_BASE_URL` | Requests may be slow or withdrawn without notice |
| **Visible attribution** | `fragment_map.xml`, set from `MapConfig.attribution()` | A licence breach. The policy forbids hiding it "beneath UI, behind toggles, or off-screen" |
| **Local caching, bounded** | `setTileFileSystemCacheMaxBytes` / `...TrimBytes` | Re-downloading tiles on every view, which the policy explicitly requires you to avoid |

**And the four things this app must never do**, none of which it does: bulk-download or pre-seed tiles,
offer an "offline maps" download button, use `http://`, or masquerade as another app or a browser.

> **The one real limitation, stated plainly:** because the policy forbids prefetching on
> `tile.openstreetmap.org`, **there is no offline map**. With no signal, the map is blank while the
> stores, the tracker and the history all keep working from Room. That is risk **R5** from Phase 0.1,
> and it is inherent to a free tile source.

### 13.3 Why the User-Agent has 11 tests

This is the failure mode worth protecting against, because it is invisible:

```java
// MapConfig.initialise()
config.setUserAgentValue(MapUserAgent.build(
        BuildConfig.APPLICATION_ID, BuildConfig.VERSION_NAME));
```

osmdroid's default User-Agent is library-shaped. The tile servers detect exactly that pattern and stop
answering. Nothing throws, nothing logs, the connection is fine — the map is just blank. So:

- The builder is **pure Java**, so `MapUserAgentTest` proves the format on the JVM in milliseconds.
- The tests assert the header **names the application** and **carries the version**, that it **is not a
  generic library default** (`okhttp`, `Java/`, `curl`, `Dalvik`, `Android SDK` are all explicitly
  rejected), that it is **deterministic** — the policy requires stability across requests — and that a
  null or blank input still produces a usable header rather than an empty one.

It also means the one thing a future release is allowed to change (the version) is the one thing the
test does not pin.

### 13.4 Three lifecycle calls that are not optional

```java
@Override public void onResume()      { super.onResume(); mapView.onResume(); }
@Override public void onPause()       { mapView.onPause(); super.onPause(); }
@Override public void onDestroyView() { mapView.onDetach(); mapView = null; super.onDestroyView(); }
```

`MapView` is not a passive widget — it runs its own tile-download threads and holds a reference to the
view. Skipping these has consequences that are easy to misdiagnose:

| Omitted | What actually happens |
| --- | --- |
| `onResume` | Tile loading never restarts. The map works on first visit and then appears frozen |
| `onPause` | A hidden tab keeps downloading tiles, burning the user's mobile data |
| `onDetach` | **A whole map view leaks per visit.** And because tabs are hidden rather than destroyed (A27), that is once per app run, not once per rotation — so it accumulates silently |

### 13.5 The two states

**Loading.** A thin indeterminate bar, hidden as soon as the map view has been laid out:

```java
view.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
    @Override public void onGlobalLayout() {
        view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
        loadingIndicator.setVisibility(View.GONE);
    }
});
```

That deliberately means **"the map surface is ready"**, not "every tile has arrived". Tiles download in
the background and fill in progressively; waiting for them would give a spinner that lasts as long as the
connection is slow, for no benefit — the user can already pan and zoom. The listener removes itself
immediately, or it would fire on every subsequent layout pass.

**Error.** A banner driven by the live network state, via `registerDefaultNetworkCallback` (API 24, the
app's minimum, so no version branch is needed). It fires on a binder thread, so every update is posted
back to the view. Its wording matters: *"Map tiles unavailable — check your connection. Your saved stores
are not affected."* — it explains the blank map **and** reassures the user that their data is intact.

### 13.6 The default camera is a view default, not data

The brief says to use a default position "only when necessary". A cold start is exactly that case —
there is nothing else to look at yet — so the map opens on a Philippines-wide view. It is a **view**
default: no store coordinate is invented, nothing is written to the database, and Phase 3.2 replaces it
with the user's real location as soon as a fix arrives.

### 13.7 What is deliberately absent from the layout

`fragment_map.xml` carries a comment listing what is *not* there yet — no store markers (3.4), no
current-location dot (3.2), no Home marker (3.7), no route polyline (4.2), no add-store form (3.3). It
is there so that a later reader does not mistake the absence for an oversight.

### 13.8 A real API change that the build caught (worth knowing for later phases)

The first build of this phase **failed**, and the reason is worth recording because it is a genuine trap
in osmdroid 6.1.x:

```java
// WRONG - does not compile in osmdroid 6.1.x
Configuration config = Configuration.getInstance();
// error: incompatible types: IConfigurationProvider cannot be converted to Configuration
```

In osmdroid 6.1.x, `org.osmdroid.config.Configuration` is **only a holder class**. It has no settings
methods of its own:

```
public class org.osmdroid.config.Configuration {
  public static synchronized IConfigurationProvider getInstance();
  public static void setConfigurationProvider(IConfigurationProvider);
}
```

Every getter and setter — including `load`, `setUserAgentValue` and the cache bounds — lives on the
**`IConfigurationProvider`** interface, which is what `getInstance()` actually returns:

```java
// CORRECT
IConfigurationProvider config = Configuration.getInstance();
config.load(context, prefs);
config.setUserAgentValue(...);
```

**Why this is worth writing down:** osmdroid's own documentation and almost every tutorial on the
internet are written against older releases where `getInstance()` returned a `Configuration`. Anyone
following those instructions will hit exactly this compile error, and the message
(`IConfigurationProvider cannot be converted to Configuration`) does not make the fix obvious. Phase 3.2
and 3.4 will both touch osmdroid and should start from this fact rather than rediscovering it.

The build is what found it. Nothing else would have — the class compiles in isolation and the mistake
only appears at the call site.

---

## 14. DATA FLOW FOR THIS PHASE

```
App start
  └─ AlphaRouteApp.onCreate()
       └─ MapConfig.initialise(this)          BEFORE any MapView exists
            ├─ Configuration.load(...)        resolves the tile cache path
            ├─ setUserAgentValue(...)         the policy requirement
            └─ setTileFileSystemCacheMaxBytes / TrimBytes

Open the Map tab
  └─ MapFragment.onViewCreated()
       ├─ setTileSource(MapConfig.tileSource())      https://tile.openstreetmap.org/
       ├─ setMultiTouchControls(true), setTilesScaledToDpi(true)
       ├─ controller.setZoom / setCenter              the default camera
       ├─ attribution.setText(MapConfig.attribution())
       ├─ hide the loading bar on first layout
       └─ registerDefaultNetworkCallback → show / hide the offline banner

  └─ onResume / onPause   start and stop tile downloading
  └─ onDestroyView        unregister the callback, onDetach(), release the map
```

**No data is read or written in this phase.** The map draws tiles and nothing else — the `stores` table
is not touched, and there is still no UI that can add a store.

---

## 15. MANUAL TEST PROCEDURE

### 15.0 Already verified automatically (2026-09-25)

`./gradlew :app:testDebugUnitTest :app:assembleDebug` was run on this machine with Gradle 9.4.1 / JDK 21
and returned **`BUILD SUCCESSFUL in 2m 12s`** (41 tasks: 33 executed, 8 up-to-date).

| Test class | Result |
| --- | --- |
| **`MapUserAgentTest`** (new) | **11 tests, 0 failures, 0 errors** |
| `GeoConstantsTest` | 17 tests, 0 failures |
| `StoreValidatorTest` | 19 tests, 0 failures |
| `AppSettingEntityTest` | 10 tests, 0 failures |
| `ExampleUnitTest` (template, Phase 10.2) | 1 test, 0 failures |
| **TOTAL** | **58 tests, 0 failures, 0 errors, 0 skipped** |

| Other evidence | Result |
| --- | --- |
| `:app:compileDebugJavaWithJavac` | ran — the osmdroid integration compiles |
| **Merged manifest permissions** | `INTERNET` and `ACCESS_NETWORK_STATE` present, as intended |
| `app-debug.apk` | **9,031,024 bytes** (up from 8,453,871 — osmdroid and its tile machinery) |
| `:app:compileDebugKotlin` | **NO-SOURCE** — still zero Kotlin source files |

> **Note on the merged manifest.** The build output also shows
> `com.cph.alpharoute.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. That is **added automatically by
> androidx.core**, not by us, and it is a self-scoped signature permission that protects dynamically
> registered broadcast receivers on Android 13+. It grants nothing to anyone else and requires no user
> prompt. Expect to see it during the Phase 10.3 permission review.

### 15.1 The map loads

1. Run the app and tap the **Map** tab.
2. A map must appear, showing the Philippines, with **visible streets and labels** — not a grey grid.
3. Confirm the attribution **"© OpenStreetMap contributors"** is visible in the bottom-right corner.
4. Pinch to zoom in and out; drag to pan. Both must respond smoothly.

### 15.2 The User-Agent is actually being accepted

5. Zoom into a city until street-level detail appears. If tiles are being blocked, they never arrive and
   the map stays a blank grid at every zoom level.
6. If the map is blank, open **Logcat** and filter on `osmdroid`. A blocked request shows up as repeated
   tile failures — and the cause is the User-Agent, not the connection.

### 15.3 Loading state

7. Force-stop the app, then reopen it and tap **Map**. A thin progress bar appears briefly at the top and
   disappears once the map is drawn.
8. It must not stay on screen after the map is visible.

### 15.4 Error state

9. Turn on **airplane mode**, then open the Map tab.
10. The banner must appear: *"Map tiles unavailable — check your connection. Your saved stores are not
    affected."*
11. Turn airplane mode **off** while the app is open. The banner must disappear **without restarting the
    app** — that is the live network callback working.
12. Revisit the Map tab after panning elsewhere, and confirm already-seen tiles still draw in airplane
    mode. That is the disk cache doing its job.

### 15.5 Lifecycle — the leak check

13. Switch between the Map tab and the other three tabs **ten times**.
14. Then pan and zoom the map. It must still respond normally and the app must not slow down. If each
    visit leaked a map view, the app would become noticeably sluggish.
15. Rotate the device while on the Map tab. The map must redraw and still respond.

### 15.6 Nothing else was added

16. Confirm **no markers of any kind** appear — no pins, no dots, no Home icon. That is correct for this
    phase.
17. Confirm **no permission dialog** appears at any point.
18. Confirm the Dashboard still shows its placeholder cards and the History and Settings tabs are
    unchanged.

**Report any failure with the step number and any Logcat text.**

---

## 16. EXPECTED RESULT

- A working OpenStreetMap map on the Map tab, showing the Philippines on first open.
- Visible "© OpenStreetMap contributors" attribution.
- Smooth pan and zoom, with tiles filling in progressively.
- A brief loading bar that clears itself.
- An offline banner that appears and clears live, without a restart.
- No leak after repeated tab switching; rotation works.
- **No markers, no location, no permission prompt.**
- Still no data read or written — the `stores` table remains empty.

---

## 17. COMMON ERRORS AND FIXES

| Symptom | Cause | Fix |
| --- | --- | --- |
| **Map is a blank grey grid at every zoom** | The User-Agent was not applied, or was replaced with a library default — the tile servers block it | Confirm `MapConfig.initialise(this)` runs in `AlphaRouteApp.onCreate`, **before** any `MapView`. Check `MapUserAgent.build` still returns `AlphaRoute/…` |
| Map is blank but the offline banner is **not** shown | Tiles are being refused, not missing — a policy problem, not a connection problem | Check Logcat for `osmdroid` tile failures |
| `Configuration.getInstance()` throws, or tiles cache nowhere | `MapConfig.initialise` did not run before the `MapView` was built | It must be the first thing in `AlphaRouteApp.onCreate` |
| **`error: incompatible types: IConfigurationProvider cannot be converted to Configuration`** | osmdroid 6.1.x: `Configuration` is only a holder, and every setter lives on `IConfigurationProvider` | Type the variable as `IConfigurationProvider`. **This is the trap in §13.8** — most tutorials are written for older osmdroid |
| `ClassNotFoundException: org.osmdroid.views.MapView` | The osmdroid dependency is missing | Confirm `implementation libs.osmdroid.android` and re-sync |
| The layout editor shows the map as a grey box | osmdroid views do not render in the preview pane | Expected. Test on a device |
| Map works on first visit, then appears frozen | `mapView.onResume()` is missing | All three lifecycle calls are required — see §13.4 |
| App becomes sluggish after switching tabs repeatedly | `mapView.onDetach()` is missing, so each visit leaks a map view | See §13.4 |
| The offline banner stays after reconnecting | The network callback was not registered, or was unregistered early | It is registered in `onViewCreated` and unregistered in `onDestroyView` |
| Banner appears while online | `isOnline()` was changed to check something other than `NET_CAPABILITY_INTERNET` | It must test that specific capability |
| Attribution hidden behind the bottom navigation | The layout constraint was changed | It is pinned to the bottom-right **of the map area**, which sits above the bottom nav |
| A "storage permission" prompt appears | Something added `WRITE_EXTERNAL_STORAGE` | Not needed. osmdroid caches in the app's own cache directory since API 19 |
| Tiles look blurry or tiny | `setTilesScaledToDpi` was removed | It is set in `configureMap()` |

---

## 18. ACCEPTANCE CHECKLIST

- [ ] Gradle sync resolves osmdroid 6.1.20.
- [ ] Clean build succeeds.
- [x] **All 11 `MapUserAgentTest` cases pass** (§15.0 — verified; whole suite 58 tests, 0 failures)
- [ ] The map displays with real street detail — **not** a blank grey grid.
- [ ] The attribution is visible in the bottom-right.
- [ ] Pan and zoom respond smoothly.
- [ ] The loading bar appears briefly and clears itself.
- [ ] The offline banner appears in airplane mode and **clears on reconnect without a restart**.
- [ ] Cached tiles still draw in airplane mode after they have been viewed once.
- [ ] Ten tab switches do not slow the app down.
- [ ] Rotation works on the Map tab.
- [ ] **No markers, no location dot, no Home pin.**
- [ ] **No permission prompt appears.**
- [x] `app/schemas/` still contains only `1.json` — no schema change (§15.0 — verified)
- [ ] Confirmed: still **no Kotlin source file** anywhere in the project.

---

## 19. UPDATED PROJECT STATE

**COMPLETED PHASES**
- ✅ 0.1 Requirements Review (Rev 5, FINAL) · ✅ 0.2 Architecture · ✅ 0.3 Screen Plan
- ✅ 1.1 Project Creation · ✅ 1.2 Theme and Base Navigation · ✅ 1.3 Dashboard Static UI
- ✅ 2.1 Room Database Foundation · ✅ 2.2 Store CRUD Operations · ✅ 2.3 Settings Storage
- 🔄 **3.1 Map Display** — *awaiting your on-device test*

**CURRENT PHASE**
- Phase 3.1 — awaiting the on-device test

**CURRENT DATABASE VERSION**
- **1.** Unchanged. Three tables: `stores`, `completion_history`, `app_settings`.

**CURRENT FILES** (changed this phase)

```
gradle/libs.versions.toml                        (MODIFIED - osmdroid 6.1.20)
app/build.gradle                                 (MODIFIED - osmdroid dependency)
app/src/main/AndroidManifest.xml                 (MODIFIED - INTERNET + ACCESS_NETWORK_STATE)

app/src/main/java/com/cph/alpharoute/ui/map/MapConfig.java     (NEW)
app/src/main/java/com/cph/alpharoute/ui/map/MapUserAgent.java  (NEW)
app/src/main/java/com/cph/alpharoute/ui/map/MapFragment.java   (REPLACED)
app/src/main/java/com/cph/alpharoute/AlphaRouteApp.java        (MODIFIED - MapConfig.initialise)

app/src/main/res/layout/fragment_map.xml         (NEW)
app/src/main/res/values/strings.xml              (MODIFIED - removed placeholder_map)
app/src/main/res/values/colors.xml               (MODIFIED - attribution scrim)

app/src/test/java/com/cph/alpharoute/ui/map/MapUserAgentTest.java  (NEW - 11 tests)
docs/PHASE_3.1_MAP_DISPLAY.md                    <- created this phase
```

**WORKING FEATURES** (verified)
- ✅ **58/58 unit tests pass** — 0 failures, 0 errors (verified 2026-09-25), including the 11 new `MapUserAgentTest` cases
- ✅ **Build succeeds** — `:app:assembleDebug` → APK 9,031,024 bytes
- ✅ **osmdroid integration compiles** — after correcting the `IConfigurationProvider` API (§13.8)
- ✅ **Permissions merged correctly** — `INTERNET` + `ACCESS_NETWORK_STATE`, both install-time
- ✅ **Still zero Kotlin source files** — `compileDebugKotlin` = `NO-SOURCE`
- ⏳ **The map on a device** — tiles drawing, attribution, loading bar, offline banner, no leak — awaiting your §15.1–15.6

**KNOWN ISSUES**
- None blocking.
- **No offline map, by policy.** The OSM tile policy forbids prefetching, so a blank map with no signal
  is expected behaviour, not a bug. Everything else keeps working offline. This is risk **R5**.
- **`placeholder_history` and `placeholder_settings` remain** and are still marked with their deletion
  phases (6.2 and 2.3 respectively). `placeholder_map` was removed in this phase as planned.
- Carried risks: OSM data quality in remote PH rural areas · openrouteservice free-tier quota ·
  osmdroid release cadence (isolated behind `MapConfig` and `MapFragment`) · date boundary (mitigated by
  AD-7 + AD-11).
- The project is **not under version control**. Still the strongest recommendation in the project.
- Trademark note: "Alfamart" is a third-party brand; fine for a private solo tool (D-5).

**PENDING PHASES** (not implemented)
- **3.2 Current location** — next
- 3.3 Manual pin creation · 3.4 Marker loading · 3.5 Store details · 3.6 Edit/delete · 3.7 Home pin
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

Phase 3.1 is complete. **12 files touched: 5 created, 7 modified.** Version 3 is open.

**The three things that matter most:**

1. **The tile policy is fully implemented, and one part of it is a silent failure mode.** The
   OpenStreetMap servers block traffic that uses a generic library User-Agent — no error, no log, just a
   blank map. That is why `MapUserAgent` is a pure class with **11 tests** rather than a string built
   inline: the failure points nowhere near its cause.

2. **Three lifecycle calls are not optional.** `onResume`, `onPause` and `onDetach`. Omitting the last
   one leaks a whole map view per visit — and because tabs are hidden rather than destroyed (A27), that
   accumulates across a single app run and shows up as mysterious sluggishness.

3. **There is no API key, and that is the point.** Decision D1 replaced Google Maps with OpenStreetMap
   specifically because Google requires a billing account with a payment method even for its free tier.
   This app now has **no credential that could leak** — and no card.

**Please run §15 and report.** The two checks worth doing carefully are **§15.2** (real street detail
appears, which proves the User-Agent is accepted) and **§15.4** (the offline banner clears on reconnect
without a restart).

**Next phase on your instruction: Phase 3.2 — Current Location.** That adds the location permission, the
permission rationale and denial handling, the GPS-disabled case, the blue current-location overlay, and
replaces the default camera with the user's real position.

**Phase complete. Do not proceed to the next phase until all acceptance criteria pass.**

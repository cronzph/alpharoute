# AlphaRoute — Phase 3.7 Home Pin

**Project title:** Alfamart Route Tracker
**Version:** 3 — Map and Manual Pins — **Phase:** 3.7 Home Pin
**Repository root:** `C:/Users/Admin/OneDrive/Documents/custom system/AlphaRoute`
**Date:** 2026-09-25
**Prerequisites:** Phases 0.1–0.3, 1.1–1.3, 2.1–2.3, 3.1–3.6
**Status:** 🔄 **Code complete and VERIFIED — 132/132 unit tests pass, build succeeds.** Awaiting your on-device test.

> **This phase finishes Version 3.** Home can now be set, changed, and seen on the map as its own house
> pin — and the Settings tab stops being a placeholder. The next phase is the routing hand-off.

---

## 1. CURRENT VERSION AND PHASE

| Item | Value |
| --- | --- |
| Version | 3 — Map and Manual Pins |
| Phase | 3.7 — Home Pin |
| Files created | 7 |
| Files modified | 8 |
| Files deleted | 0 |
| Database version | **1** — unchanged. No schema change. |
| Unit tests | **132** (was 106 — 26 new) |

---

## 2. OBJECTIVE

Give the route a place to finish:

- A **Home Setup** screen: tap the map to choose Home, or use the current location, then confirm
- Home stored in the settings row — **never as a store, never in a count**
- A distinct **house pin** on the map, in deep purple, that is clearly not a store
- A real **Settings** screen, so Home Setup can actually be reached
- The arrival radius configurable, with its rules unit-tested

---

## 3. UNDERSTANDING CHECKLIST

- [x] Java only, no Kotlin. No new dependency, no new permission, no schema change.
- [x] **Home is never a store row and never affects a count** — it has its own pin outside the store map.
- [x] `markerCount()` still counts **stores only**; Home is deliberately excluded.
- [x] Home is drawn with a **house**, so it cannot be mistaken for a store.
- [x] The bottom navigation is hidden while Home Setup is open, and restored on the way out.
- [x] The chrome is restored **after a rotation** too, not only on the way out.
- [x] The Home pin is transparent to taps, so tapping it in Home Setup still places it.
- [x] The arrival radius cannot be saved as zero, negative, or out of range — the parser is unit-tested.
- [x] `placeholder_settings` is deleted, as its own comment said it would be in this phase.
- [x] No store name, code, route number or coordinate is invented anywhere.

---

## 4. FEATURES INCLUDED (in this phase)

| Feature | Detail |
| --- | --- |
| **`HomeSetupFragment`** | Pushed screen: tappable map, readout, Confirm / Use current location / Back |
| **The house pin** | `ic_marker_home` — deep purple with a white outline, 36 dp |
| **`renderHome`** | `MapMarkerController` draws or removes Home, outside the store markers |
| **Home on the map** | `MapFragment` observes the settings row, so setting Home shows the pin at once |
| **A real Settings screen** | Home status + action, the arrival radius, and the app version |
| **Push navigation** | `MainActivity.openHomeSetup()` with back-stack-driven chrome restoration |
| **`ArrivalRadiusValidator`** | Pure Java, 16 tests, including the integer-overflow case |
| **26 new unit tests** | 16 for the radius, 10 for which coordinates may be drawn |

---

## 5. FEATURES NOT INCLUDED YET

| Not here | Arrives in |
| --- | --- |
| **Get Directions** button | **Phase 4.2** (route preview) then 4.3 (the hand-off) |
| Store and completion counts on Settings | **Phase 6.1** — the Dashboard computes real figures properly |
| Clearing Home back to "not set" | **Not planned.** See §13.8 |
| Route polyline | **Phase 4.2** |
| Any completion action | **Phase 5.2** — and only through the arrival flow |
| The tile-source URL and routing-service status rows on Settings | **Phase 4.1** — the account does not exist yet |

---

## 6. ASSUMPTIONS

| # | Assumption | Impact if wrong |
| --- | --- | --- |
| A85 | **Home Setup is a pushed fragment, not a tab**, so the bottom navigation is hidden while it is open. | It is a step reached from Settings, not a destination. A tab bar offering four other places would contradict "the way out is back". |
| A86 | **There is no "Select on map" button.** Tapping the map *is* the selection. | The screen plan lists one beside "Use current location", but with the map embedded and always tappable, that button would have had nothing to do. §13.2 records the deviation; it is trivial to add back. |
| A87 | **The candidate starts from the saved Home when there is one.** | So "change home location" opens on where Home currently is rather than on nothing. The plan only describes the "Home not set" case. |
| A88 | **The candidate is held in a field and saved in the instance state**, not passed as arguments. | It is a value the user is producing, not one handed in - the opposite of the store id on the Store Details sheet. |
| A89 | **The Home pin is transparent to taps** (its listener returns `false`). | So a tap on the pin in Home Setup still reaches the map's tap handler and moves the pin, which is what you want when nudging it. |
| A90 | **`markerCount()` excludes Home.** | Phase 6.5 compares it against the store count; including Home would make that check report a permanent, false discrepancy of one. |
| A91 | **The radius error message substitutes its bounds from `GeoConstants`.** | The text cannot end up promising a range the validator would refuse. |
| A92 | **Settings does not show store or completion counts.** | See §13.8. The Dashboard computes real figures through `TrackerCalculator` in 6.1; a second, simpler way to count the same rows would be a second answer to a question that already has one. |
| A93 | **`store_detail_coordinates_format` was renamed to `coordinates_format`.** | Three screens now show a coordinate, so a name tied to one of them was wrong. Rule-11 note in §8. |
| A94 | **The radius field is prefilled once, and only when there is no saved state.** | The settings row emits on every write, including this screen's own, so setting the text every time would fight whatever the user is typing - and after a rotation it would throw it away. |

---

## 7. FILES TO CREATE (7)

| # | Path | Purpose |
| --- | --- | --- |
| 1 | `res/drawable/ic_marker_home.xml` | **New.** The deep purple house pin |
| 2 | `res/layout/fragment_home_setup.xml` | **New.** Map preview, readout, and the three actions |
| 3 | `ui/settings/HomeSetupFragment.java` | **New.** The Home Setup screen |
| 4 | `res/layout/fragment_settings.xml` | **New.** The real Settings screen |
| 5 | `domain/ArrivalRadiusValidator.java` | **New.** Pure Java. Text in, metres or `null` out |
| 6 | `app/src/test/java/com/cph/alpharoute/domain/ArrivalRadiusValidatorTest.java` | **New.** 16 JVM tests |
| 7 | `docs/PHASE_3.7_HOME_PIN.md` | **New.** This record |

---

## 8. FILES TO MODIFY (8)

| # | Path | Status | What changed |
| --- | --- | --- | --- |
| 1 | `ui/settings/SettingsFragment.java` | **Replaced** | Was the Phase 1.2 placeholder; now the real screen |
| 2 | `ui/map/MapMarkerController.java` | **Modified** | `renderHome`, `removeHomeMarker`, `hasPlottableCoordinates(Double, Double)`; the coordinate bounds now come from `GeoConstants` |
| 3 | `ui/map/MapFragment.java` | **Modified** | `observeHome()` |
| 4 | `MainActivity.java` | **Modified** | `openHomeSetup()`, the back-stack listener, and the chrome it drives |
| 5 | `res/values/strings.xml` | **Modified** | 25 new strings; `placeholder_settings` deleted; `store_detail_coordinates_format` renamed |
| 6 | `ui/store/StoreDetailBottomSheet.java` | **Modified** | Uses the renamed `coordinates_format` |
| 7 | `app/src/test/java/com/cph/alpharoute/ui/map/MapMarkerControllerTest.java` | **Modified** | 10 new tests for which coordinates may be drawn |
| 8 | `docs/HANDOFF.md` | **Modified** | Status, phase history, inventory, test count, roadmap, traps |

### One deletion, and one rename

**`placeholder_settings` was deleted.** Its own comment said it would go in "Phase 2.3/3.7", and this is
the phase that replaced the Settings placeholder with the real screen. Nothing else referenced it.

**`store_detail_coordinates_format` → `coordinates_format`.** The string is `%1$.6f, %2$.6f`. It was named
in Phase 3.5 for the one sheet that used it; Home Setup and Settings now show a coordinate too, so a name
tied to one screen became wrong. The alternative was two more copies of the same value, which is how three
formats start drifting apart. Both usages were updated in the same change and the build confirms no
reference to the old name remains.

**Inspected and left unmodified:** the database layer, `domain/StoreValidator`, `location/`,
`util/`, and the store editing and deletion paths. This phase adds a setting and a screen; it does not
change how stores are written.

---

## 9. DEPENDENCIES

**None added.**

---

## 10. PERMISSIONS AND MANIFEST CHANGES

**None.** Verified against the merged manifest after the build:

```
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="com.cph.alpharoute.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" />
```

Home Setup reuses the same location permission the Map tab already asks for, so there is nothing new to
grant. It also deliberately has **no background location**: Home Setup is on screen while it reads the
GPS.

---

## 11. SETUP INSTRUCTIONS

1. Let Gradle sync (nothing new to fetch).
2. **Build → Clean Project**, then **Build → Rebuild Project**.
3. Run on a device or emulator.

> **Do a clean build before judging the APK size.** An incremental debug build can leave stale dex shards
> in the APK - this phase's first build reported 11.1 MB against a true 9.6 MB. See §13.7.

---

## 12. COMPLETE CODE

All 15 files are written into the project on disk. The files worth reading first:

1. `ui/settings/HomeSetupFragment.java` — the screen, the candidate, and the permission states
2. `MainActivity.java` — `openHomeSetup` and the back-stack-driven chrome
3. `domain/ArrivalRadiusValidator.java` — why the radius rule is pure and tested
4. `ui/map/MapMarkerController.java` — `renderHome`, and why Home is not in the store map
5. `res/drawable/ic_marker_home.xml` — the shape that makes Home unmistakable

---

## 13. CODE EXPLANATION

### 13.1 A pushed screen, and how the chrome comes back

Home Setup is not a tab. It is reached from Settings, it configures one setting, and the way out is back
to where you came from. So it is pushed onto the back stack:

```java
Fragment covered = fm.findFragmentByTag(activeTag);
FragmentTransaction tx = fm.beginTransaction();
if (covered != null) {
    tx.hide(covered);                                    // the tab underneath stops running
}
tx.add(R.id.fragment_container, new HomeSetupFragment(), TAG_HOME_SETUP);
tx.addToBackStack(TAG_HOME_SETUP);
tx.commit();
```

Two things follow from that, and both are handled in one place:

- **The bottom navigation is hidden.** It offers four other destinations, which contradicts "the way out
  is back". Hiding it also means a tab can never be tapped while Home Setup is open, which is what keeps a
  pushed screen from being left on top of a tab the user has just chosen - so `showTab` needs no special
  case for it.
- **The toolbar is retitled to "Home".** A screen with no title at all reads as a glitch.

Restoring both is driven by the back stack rather than by the screen:

```java
getSupportFragmentManager().addOnBackStackChangedListener(this::syncChromeToBackStack);
syncChromeToBackStack();
```

One listener covers **both** ways out - the screen's own Back action and the system back button - because
both end in the same `popBackStack`. The immediate call after it is what handles **rotation**: by the time
`onCreate` runs, `super.onCreate` has already restored the fragment, so no event is ever going to fire for
it. Without that line, rotating with Home Setup open would leave the screen showing with the bottom
navigation visible underneath it and the toolbar saying "Settings".

### 13.2 The one place this phase deviates from the screen plan: no "Select on map" button

The Phase 0.3 plan gives screen 6 three actions: **Use current location**, **Select on map**, and
**Confirm home location**. This phase ships **two** - Use current location and Confirm - and the map is
simply tappable.

The reason is that the third button had nothing to do. "Select on map" means "choose a point by looking at
the map" - and the map is already embedded in this screen and already accepts taps. So the button would
either have been a mode toggle (annoying: why can't I just tap?) or an instruction dressed up as a
control, which is exactly the kind of button rule 8 exists to prevent. The instruction line under the
readout says what to do instead: *"Tap the map to choose Home, or use your current location."*

Every control on the screen now does something no other control does:

| Control | What it does |
| --- | --- |
| **The map** | Places or moves Home wherever you tap |
| **Use current location** | Puts Home where you are standing |
| **Confirm home location** | Saves it - disabled until a point exists |
| **Back** | Leaves without saving |

If the button is wanted back, the honest version is "put Home at the centre of the current view" (pan to
your house, then tap it) - which is a real action, distinct from tapping. It is about eight lines.

### 13.3 Why the Home pin is transparent to taps

osmdroid walks the overlay list **from the top down** and stops at the first overlay that returns `true`.
This was checked against osmdroid 6.1.20's own sources rather than assumed:

```java
// DefaultOverlayManager
public boolean onSingleTapConfirmed(final MotionEvent e, final MapView pMapView) {
    for (final Overlay overlay : this.overlaysReversed()) {
        if (overlay.onSingleTapConfirmed(e, pMapView)) {
            return true;
        }
    }
    return false;
}
```

The Home marker is added above the tap handler, and its click listener returns `false`:

```java
homeMarker.setOnMarkerClickListener((tapped, map) -> false);
```

So on the **Map tab** tapping Home does nothing - no osmdroid bubble appears over our own UI - and in
**Home Setup** a tap on the pin falls through to the tap handler below and moves the pin. That second
behaviour is the one that matters: nudging Home into place by tapping it is the natural gesture, and a pin
that swallowed its own taps would make the last few metres unadjustable.

### 13.4 Why the arrival radius rule is pure Java with 16 tests

The arrival radius decides whether the app ever offers to complete a store. It is also the only place in
the app where a **typed number** is trusted - every other value is chosen from a map or a fixed set. So the
parsing and range checking live in `ArrivalRadiusValidator`, in `domain/`, with no Android imports (AD-11),
where `ArrivalRadiusValidatorTest` can reach them.

**The rule that matters most is that zero is refused.** A radius of 0 means "you must be standing exactly
on the pin", which no GPS reading can satisfy. The app would look like it worked perfectly and never offer
a completion, and nothing in the logs would say why.

**It returns `null`, not `0` or `-1`.** Those are values a caller could accidentally use - `0` in
particular being a radius the arrival check must never be given. A null is a value the caller has to deal
with.

**The bounds come from `GeoConstants`**, the same constants `SettingsRepository.setArrivalRadius` enforces,
so there is one definition of "a usable radius" rather than a UI copy and a repository copy that can
disagree.

**The overflow case is the interesting one.** `Integer.parseInt` throws `NumberFormatException` for a value
too large to be an `int` - it does not silently wrap:

```java
try {
    int metres = Integer.parseInt(trimmed);
    return GeoConstants.isValidArrivalRadius(metres) ? metres : null;
} catch (NumberFormatException e) {
    return null;
}
```

A wrapping parse could have turned `"999999999999"` into a number that happened to land inside 10..1000,
and the user would have saved a radius they never typed. Two of the sixteen tests cover exactly this, one
from each side.

### 13.5 Why the radius field is filled in only once

The Settings screen observes the settings row. That row emits on **every** write - including the write this
screen has just made. So filling the field in on every emission would fight whatever the user is in the
middle of typing.

```java
radiusNeedsPrefill = savedInstanceState == null;   // in onViewCreated
...
if (radiusNeedsPrefill) {
    radiusNeedsPrefill = false;
    arrivalRadiusInput.setText(String.valueOf(settings.arrivalRadius));
}
```

The `savedInstanceState` part is the same guard the Add / Edit form uses (assumption A79): Android saves
and restores the contents of every text field itself, so after a rotation the field already holds what the
user typed - and filling it in again would silently discard it.

### 13.6 Why Home is not in the store markers map

`MapMarkerController` keeps its store markers in `markersById`, keyed by store id. Home has its own field
instead:

```java
private final Map<Long, MarkerEntry> markersById = new HashMap<>();   // stores only
@Nullable private Marker homeMarker;                                  // Home, separate on purpose
```

That separation is not tidiness - it is what keeps `markerCount()` a count of **stores**. Phase 6.5's
consistency check compares that number against the store count in the database, so a Home pin counted
among the stores would make every run of that check report a discrepancy of one, and a real discrepancy
would be lost in the noise.

It also means `renderAll` never prunes Home, so a store refresh cannot make Home disappear.

### 13.7 Two things the build caught

**First, the `--` trap. Again.**

```
[Fatal Error] fragment_settings.xml:36:16: The string "--" is not permitted within comments.
```

A comment used a row of dashes as a section separator (`<!-- ------- Home ------- -->`), and XML forbids
`--` anywhere inside a comment. This is the *same* trap that was recorded in the handoff's §8 after Phase
3.5 - and it was still repeated, which says something about how invisible the mistake is. The separators
are now plain words.

**Second, a misleading APK size.**

The first build of this phase reported an APK of **11,157,222 bytes** - a jump of 1.57 MB over Phase 3.6,
for a phase that added three small classes and two layouts. That was not plausible, so it was checked
rather than reported. A clean build produced **9,608,318 bytes**, a rise of 26 KB, which is what this
phase's code actually costs.

The cause is that an **incremental debug build can leave stale dex shards in the APK**: the intermediate
merge step appends new shards without clearing the old ones, so the reported size drifts upward over a
series of incremental builds. It is harmless for testing - the classes are still correct - but it makes the
size useless as evidence. **Do a clean build before judging APK size.**

### 13.8 What is deliberately not here

- **No way to clear Home back to "not set".** `SettingsRepository.clearHome()` exists and works, but the
  screen does not offer it. Home is the place the route finishes - a user with no Home has a route with no
  end - so "I do not want a Home" is not a state this app is designed around. Changing Home is offered
  instead. If a Clear action is ever wanted, `clearHome()` is already waiting.
- **No store or completion counts on Settings.** The screen plan lists them, but they are statistics rather
  than settings, they would need repository reads that nothing else uses, and Phase 6.1's Dashboard
  computes real figures properly through `TrackerCalculator`. A second, simpler way to count the same rows
  would be a second answer to a question that already has one.
- **No tile-source or routing-service rows.** The routing account does not exist until Phase 4.1, and a row
  reporting on something that is not there yet would be a row full of "not configured".

---

## 14. DATA FLOW FOR THIS PHASE

### Setting Home

```
Settings tab → "Set / Change home location"
  └─ SettingsFragment.openHomeSetup()  →  MainActivity.openHomeSetup()
        ├─ hide(Settings tab)
        ├─ add(HomeSetupFragment, TAG_HOME_SETUP) + addToBackStack
        ├─ bottomNav GONE, toolbar "Home"
        └─ HomeSetupFragment.onViewCreated
              ├─ map configured (OSM tiles, attribution visible)
              ├─ MapMarkerController created; tap overlay added at index 0
              ├─ restore the candidate from the instance state, if any
              ├─ else settingsRepository.getNow() → the saved Home becomes the starting candidate
              └─ observe locationProvider.currentFix()

Choosing a point
  ├─ tap the map  → singleTapConfirmedHelper → setCandidate(lat, lng, centre=false)
  ├─ "Use current location" → permission check → setCandidate(fix.lat, fix.lng, centre=true)
  └─ either way → markerController.renderHome(candidate) + updateActions()
        └─ Confirm enabled, readout shows the coordinates

Confirm
  └─ settingsRepository.setHome(lat, lng)     validates the range, then mutates the settings row
        ├─ SUCCESS → Toast → popBackStack()
        │     ├─ the back-stack listener restores the toolbar and the bottom navigation
        │     ├─ Settings observes the row → the Home row already shows the new coordinates
        │     └─ MapFragment observes the row → the house pin moves, with nothing told to it
        └─ FAILED  → Snackbar, Confirm re-enabled, nothing written
```

### Home is not a store, anywhere

```
app_settings (one row)                        stores (many rows)
  homeLatitude / homeLongitude / homeSet        id, routeNumber, storeCode, ...
        │                                              │
        ├─ MapMarkerController.homeMarker              └─ MapMarkerController.markersById
        │     (its own field)                                (markerCount() counts only these)
        └─ never in markerCount(), never in the Dashboard, never in completion history
```

---

## 15. MANUAL TEST PROCEDURE

### 15.0 Already verified automatically (2026-09-25)

`./gradlew clean :app:testDebugUnitTest :app:assembleDebug` was run on this machine with Gradle 9.4.1 /
JDK 21 and returned **`BUILD SUCCESSFUL in 2m 34s`** (42 tasks, all executed).

| Test class | Result |
| --- | --- |
| **`ArrivalRadiusValidatorTest`** (new) | **16 tests, 0 failures, 0 errors** |
| **`MapMarkerControllerTest`** | **15 tests, 0 failures** (was 5 — 10 new) |
| `LocationFixTest` | 20 tests, 0 failures |
| `StoreValidatorTest` | 19 tests, 0 failures |
| `GeoConstantsTest` | 17 tests, 0 failures |
| `DateTimeHelperTest` | 16 tests, 0 failures |
| `MapUserAgentTest` | 11 tests, 0 failures |
| `AppSettingEntityTest` | 10 tests, 0 failures |
| `StoreRepositoryTest` | 7 tests, 0 failures |
| `ExampleUnitTest` (template, Phase 10.2) | 1 test, 0 failures |
| **TOTAL** | **132 tests, 0 failures, 0 errors, 0 skipped** |

| Other evidence | Result |
| --- | --- |
| `:app:compileDebugJavaWithJavac` | ran — the fragment, the controller change and `MainActivity` all compile |
| `:app:mergeDebugResources` + `:app:processDebugResources` | ran — the two new layouts, the house drawable and the strings resolve |
| `app-debug.apk` (**clean build**) | **9,608,318 bytes** (was 9,582,311 — +26,007) |
| Merged manifest permissions | **unchanged** — the same four |
| `app/schemas/…/AppDatabase/` | still contains **only `1.json`** — no schema change |
| `res/values-night/` | still contains **only `colors.xml`** — no `themes.xml` |
| `:app:compileDebugKotlin` | **NO-SOURCE** — still zero Kotlin source files |

### 15.1 Settings is a real screen now

1. Open the **Settings** tab. The placeholder is gone.
2. Confirm three groups: **Home**, **Arrival radius**, **About**.
3. Confirm the Home row reads **"Home not set"** (first run) and the button reads **"Set home location"**.
4. Confirm **About** shows the app name and a version.

### 15.2 Setting Home

5. Tap **Set home location**. Home Setup must open, the **bottom navigation must disappear**, and the
   toolbar must read **"Home"**.
6. Confirm the caption, the readout (**"No point chosen yet"** or **"Locating…"**), and the **OSM
   attribution** are all visible on the map.
7. Confirm **Confirm home location** is **disabled** — no point has been chosen yet.
8. **Tap somewhere on the map.** A **deep purple house** must appear there, the readout must show its
   coordinates, and **Confirm must become enabled**.
9. Tap somewhere else. The house must **move**, not duplicate.
10. Tap **Confirm home location**. A **"Home saved."** message must appear and the screen must close back
    to Settings.
11. Confirm the Settings Home row now shows the coordinates and the button reads **"Change home
    location"**.
12. Go to the **Map** tab. The **house pin must be there**, in deep purple, clearly different from the
    orange and green teardrop store pins.

### 15.3 Home survives a restart, and is never a store

13. **Force-stop the app** and reopen it. Go to the Map tab. **The house pin must still be there.**
14. In the Database Inspector, check `app_settings`: `homeSet = 1`, and both coordinates set.
15. **Check `stores`: there must be no Home row in it.** Count the rows and compare with the number of
    store markers on the map — they must match, with Home **not** counted.

### 15.4 "Use current location"

16. Open Home Setup again and tap **Use current location**. The house must move to your position and the
    map must centre on it.
17. If the app has no location permission, the button must read **"Grant permission"** instead. Tap it,
    grant, and confirm the button changes to **"Use current location"**.
18. Confirm the permission dialog appears **once** and that denying it leaves the map tap working.

### 15.5 Back leaves without saving

19. Open Home Setup, **tap the map somewhere far away**, then tap **Back**.
20. Confirm you are back on Settings, the **bottom navigation has returned**, the toolbar says
    **"Settings"**, and the Home row still shows the **old** coordinates — the tap must not have saved
    anything.
21. Repeat using the **system back button** instead. Same result, same restored chrome.

### 15.6 Rotation

22. Open Home Setup and tap the map to place a point.
23. **Rotate the device.** The chosen point must still be there, the readout must still show its
    coordinates, and **Confirm must still be enabled**.
24. Confirm the **bottom navigation is still hidden** and the toolbar still says **"Home"**.
25. **Force-stop the app and reopen it.** The bottom navigation must be back.

### 15.7 The arrival radius

26. On Settings, clear the radius field and type **`0`**. Tap **Save**. An inline error must appear
    reading **"Enter a distance in metres between 10 and 1000"** — and nothing must be saved.
27. Try **`abc`**, **`-5`**, **`1001`** and **`999999999999`**. All must be refused the same way.
28. Type **`100`** and Save. A **"Settings saved."** message must appear.
29. Force-stop and reopen. The field must still show **`100`** — it was committed.
30. **Rotate the Settings tab with unsaved text in the radius field.** What you typed must still be there.

### 15.8 Nothing else changed

31. Confirm store markers still draw (orange pending, green completed) and survive a restart.
32. Confirm tapping a store marker still opens the Store Details sheet, and that Edit and Delete still work.
33. Confirm long-press on the map still opens the Add store form.
34. Confirm the blue location dot and its accuracy circle still work.
35. Confirm the Dashboard and History tabs are unchanged.

**Report any failure with the step number and any Logcat text.**

---

## 16. EXPECTED RESULT

- Settings is a real screen with Home, the arrival radius and the version.
- Home Setup opens as a pushed screen with the bottom navigation hidden.
- Tapping the map places a deep purple house; **Confirm** enables only once a point exists.
- Confirming saves Home, closes the screen, and the house appears on the Map tab.
- **Home survives a restart and is never counted as a store.**
- The arrival radius refuses zero, negatives, words, fractions and out-of-range values.
- Back — either kind — leaves without saving and restores the toolbar and the bottom navigation.
- **No schema change, no new dependency or permission.**

---

## 17. COMMON ERRORS AND FIXES

| Symptom | Cause | Fix |
| --- | --- | --- |
| `The string "--" is not permitted within comments` | A comment in an XML file contains `--` | Rewrite the separator as plain text (§13.7). This has now happened twice — check every new XML comment |
| **Confirm does nothing / the house never appears** | The tap overlay is not at index 0, or `renderHome` was given a coordinate it refuses | The overlay goes in with `getOverlays().add(0, …)`; `renderHome` removes the pin for a null, NaN or out-of-globe coordinate |
| **The house pin is counted as a store** | Home was put into `markersById` | Home must stay in its own field, or `markerCount()` stops meaning "stores" (§13.6) |
| **Tapping the house in Home Setup does nothing** | The Home marker's click listener returns `true` | It must return `false`, so the tap falls through to the map handler (§13.3) |
| **The bottom navigation stays hidden after leaving** | `syncChromeToBackStack` is not registered, or the screen was dismissed without popping | `leave()` must `popBackStack()`; the listener is registered in `MainActivity.onCreate` |
| **After a rotation the bottom navigation reappears over Home Setup** | The one-off `syncChromeToBackStack()` in `onCreate` was removed | It is what covers the restore case, where no back-stack event fires (§13.1) |
| **The radius field is wiped while typing** | The prefill runs on every emission | It must run once, and only when `savedInstanceState == null` (§13.5) |
| **A radius of 0 saves** | `isValidArrivalRadius` was bypassed, or the parser returned `0` instead of `null` | Zero must be refused in both the parser and the repository |
| **Home Setup shows "Locating…" forever with no permission** | `isWaitingForFix()` does not check the permission | It must return false when permission is missing — the app is blocked, not waiting |
| **The OSM attribution is missing on Home Setup** | The attribution `TextView` was hidden or covered | The tile policy requires it to be visible wherever tiles are drawn |
| **The APK looks 1.5 MB bigger than it is** | An incremental debug build left stale dex shards | Do a clean build (§13.7) |

---

## 18. ACCEPTANCE CHECKLIST

- [ ] Clean build succeeds.
- [x] **All 16 `ArrivalRadiusValidatorTest` cases pass** (§15.0 — verified; whole suite 132 tests, 0 failures)
- [x] **All 10 new `MapMarkerControllerTest` cases pass** (§15.0 — verified)
- [x] `app/schemas/` still contains only `1.json` (§15.0 — verified)
- [x] Merged manifest permissions unchanged (§15.0 — verified)
- [x] `values-night/` still contains only `colors.xml` (§15.0 — verified)
- [ ] Settings shows Home, the arrival radius and the version (§15.1).
- [ ] Home Setup opens with the bottom navigation hidden (§15.2).
- [ ] Confirm is disabled until a point is chosen (§15.2).
- [ ] Tapping the map places a **deep purple house**, and tapping again **moves** it (§15.2).
- [ ] Confirming saves Home and the house appears on the Map tab (§15.2).
- [ ] Home **survives a restart** (§15.3).
- [ ] **Home is not a row in `stores` and is not counted as a store** (§15.3).
- [ ] **Use current location** works, and offers **Grant permission** when it has none (§15.4).
- [ ] Back leaves **without saving** and restores the toolbar and the bottom navigation (§15.5).
- [ ] The chosen point and the hidden navigation **survive rotation** (§15.6).
- [ ] The radius refuses **0**, words, negatives and out-of-range values (§15.7).
- [ ] A valid radius saves and survives a restart (§15.7).
- [ ] Store markers, store details, edit/delete and long-press all still work (§15.8).
- [ ] Confirmed: still **no Kotlin source file** anywhere in the project.

---

## 19. UPDATED PROJECT STATE

**COMPLETED PHASES**
- ✅ 0.1 Requirements Review (Rev 5, FINAL) · ✅ 0.2 Architecture · ✅ 0.3 Screen Plan
- ✅ 1.1 Project Creation · ✅ 1.2 Theme and Base Navigation · ✅ 1.3 Dashboard Static UI
- ✅ 2.1 Room Database Foundation · ✅ 2.2 Store CRUD Operations · ✅ 2.3 Settings Storage
- ✅ 3.1 Map Display · ✅ 3.2 Current Location · ✅ 3.3 Manual Store Pin Creation
- ✅ 3.4 Load Saved Store Markers · ✅ 3.5 Store Details Bottom Sheet · ✅ 3.6 Edit and Delete Store
- 🔄 **3.7 Home Pin** — *awaiting your on-device test*

**CURRENT PHASE**
- Phase 3.7 — awaiting the on-device test. **Version 3 is code complete.**

**CURRENT DATABASE VERSION**
- **1.** Unchanged. Three tables: `stores`, `completion_history`, `app_settings`.

**CURRENT FILES** (changed this phase)

```
app/src/main/res/drawable/ic_marker_home.xml                                    (NEW)
app/src/main/res/layout/fragment_home_setup.xml                                 (NEW)
app/src/main/res/layout/fragment_settings.xml                                   (NEW)
app/src/main/java/com/cph/alpharoute/ui/settings/HomeSetupFragment.java         (NEW)
app/src/main/java/com/cph/alpharoute/domain/ArrivalRadiusValidator.java         (NEW)
app/src/test/java/com/cph/alpharoute/domain/ArrivalRadiusValidatorTest.java     (NEW - 16 tests)

app/src/main/java/com/cph/alpharoute/ui/settings/SettingsFragment.java          (REPLACED - was a placeholder)
app/src/main/java/com/cph/alpharoute/ui/map/MapMarkerController.java            (MODIFIED - renderHome)
app/src/main/java/com/cph/alpharoute/ui/map/MapFragment.java                    (MODIFIED - observeHome)
app/src/main/java/com/cph/alpharoute/MainActivity.java                          (MODIFIED - pushed screen)
app/src/main/java/com/cph/alpharoute/ui/store/StoreDetailBottomSheet.java       (MODIFIED - renamed string)
app/src/main/res/values/strings.xml                                             (MODIFIED)
app/src/test/java/com/cph/alpharoute/ui/map/MapMarkerControllerTest.java        (MODIFIED - +10 tests)

docs/PHASE_3.7_HOME_PIN.md                                                      <- created this phase
docs/HANDOFF.md                                                                 (MODIFIED)
```

**WORKING FEATURES** (verified)
- ✅ **132/132 unit tests pass** — 0 failures, 0 errors (verified 2026-09-25), including 26 new cases
- ✅ **Clean build succeeds** — `:app:assembleDebug` → APK 9,608,318 bytes
- ✅ **Resources merge and link cleanly** — both new layouts, the house drawable and the strings resolve
- ✅ **Schema unchanged** — only `1.json` exists
- ✅ **Permissions unchanged** — the same four
- ✅ **`values-night/` still has no `themes.xml`** — dark mode is still safe
- ✅ **Still zero Kotlin source files** — `compileDebugKotlin` = `NO-SOURCE`
- ⏳ **Home Setup on a device** — tap, confirm, restart, rotation, back — awaiting your §15.1–15.8

**KNOWN ISSUES**
- None blocking. **Version 3 is code complete.**
- **There is no "Select on map" button** on Home Setup — see §13.2. The map is tappable instead. This is
  the one deliberate deviation from the screen plan in this phase.
- Home cannot be cleared back to "not set" (§13.8). `clearHome()` exists if that changes.
- Settings shows no store or completion counts (§13.8) — they belong to the Dashboard in 6.1.
- `placeholder_history` remains, still marked for 6.2.
- Carried risks: OSM data quality in remote PH rural areas · openrouteservice free-tier quota ·
  **no offline map** (policy, risk R5) · date boundary (AD-7 + AD-11).
- The project is **not under version control**. Still the strongest recommendation in the project.
- Trademark note: "Alfamart" is a third-party brand; fine for a private solo tool (D-5).

**PENDING PHASES** (not implemented)
- **4.1 Routing service preparation** — next
- 4.2 Route preview · 4.3 Navigation hand-off + arrival monitoring · 4.4 State recovery
- 5.1 Arrival-radius check · 5.2 Completion dialog · 5.3 Atomic completion transaction
- 6.1 Live tracker · 6.2 Daily history list · 6.3 Daily completion order · 6.4 Completion details ·
  6.5 Consistency check
- 7.1 Nearest pending store · 7.2 Recalculate after completion · 7.3 Home-aware heuristic · 7.4 No pending stores
- 8.1 Routing service review · 8.2 Road-based next store · 8.3 Multi-stop optimization · 8.4 Complete route preview
- 9.1 Local export · 9.2 Local import · 9.3 Optional cloud sync *(Firebase — deferred by your decision)*
- 10.1 Error handling · 10.2 Automated and manual testing · 10.3 Release preparation

---

## 20. STOP POINT

Phase 3.7 is complete. **15 files touched: 7 created, 8 modified. One string deleted, one renamed.**

**Version 3 — "Map and Manual Pins" — is code complete.** A store can be pinned, corrected and removed;
Home can be set and seen; the map, the details sheet and Settings are all real screens. What is left in
Version 4 is turning a chosen store into a route.

**Three things are worth calling out:**

1. **The one place this phase deviates from the screen plan.** Home Setup ships two actions rather than
   three: there is no "Select on map" button, because the map is embedded and a tap *is* the selection —
   the button would have had nothing to do but tell the user to do the obvious thing. §13.2 records it,
   and the alternative reading ("put Home at the centre of the view") is about eight lines if it is wanted.

2. **The arrival radius is the only typed number in the app, so its rule is pure Java with 16 tests.** A
   radius that came out as **zero** would mean "stand exactly on the pin" — the app would look like it
   worked and never offer to complete a store, and nothing in the logs would explain why. The parser
   returns `null` rather than a sentinel, and two of the tests cover the integer-overflow case where a
   wrapping parse could have produced a radius the user never typed.

3. **Two things the build caught, and one of them was my own repeated mistake.** The `--`-inside-an-XML-
   comment error from Phase 3.5 happened *again* in a new layout — it is in the handoff's trap table and
   was still repeated, which says how invisible it is. And the first build reported an APK 1.57 MB larger
   than the code could possibly explain; a clean build showed the true figure was 26 KB. **An incremental
   debug build leaves stale dex shards in the APK**, so its size is not evidence until you clean.

**Please run §15 and report** — especially §15.2 (the house pin, and Confirm disabled until a point is
chosen), §15.3 (Home survives a restart and is **not** a store row), §15.5 (Back leaves without saving and
brings the navigation back), §15.6 (rotation) and §15.7 (the radius refuses 0).

**Next phase on your instruction: Phase 4.1 — Routing Service Preparation.** Creating the free
openrouteservice account, verifying the API, and deciding where the key lives. **The key goes in
`local.properties` → `BuildConfig.ORS_API_KEY`, never in a Java file.** That is the one step in Version 4
that needs something from you before any code can be written.

**Phase complete. Do not proceed to the next phase until all acceptance criteria pass.**

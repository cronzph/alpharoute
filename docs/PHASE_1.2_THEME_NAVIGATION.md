# AlphaRoute — Phase 1.2 Theme and Base Navigation

**Project title:** Alfamart Route Tracker
**Version:** 1 — Project Foundation — **Phase:** 1.2 Application Theme and Base Navigation
**Repository root:** `C:/Users/Admin/OneDrive/Documents/custom system/AlphaRoute`
**Date:** 2026-09-25
**Prerequisites:** Phase 0.1 Rev 5 · Phase 0.2 (AD-1 single Activity, AD-7 desugaring) · Phase 0.3 (A17 bottom navigation only) · Phase 1.1 (build-verified)
**Status:** 🔄 **Code complete and BUILD-VERIFIED.** Automated `:app:assembleDebug` → **BUILD SUCCESSFUL**. Awaiting your on-device test.

> **A note on how the code is delivered in this phase.** Phase 1.1 changed 5 small files, so its record
> listed every line. This phase touches **24 files**, so the code is *in the files themselves* — written
> directly into the project and attached to this message — and this document explains what each one is for
> and why it is shaped the way it is. There is deliberately no second copy of the code inside this
> document, because two copies of the truth drift apart.

---

## 1. CURRENT VERSION AND PHASE

| Item | Value |
| --- | --- |
| Version | 1 — Project Foundation |
| Phase | 1.2 — Application Theme and Base Navigation |
| Files created | 16 |
| Files modified | 8 |
| Files deleted | **1** — `app/src/main/res/values-night/themes.xml` (reason in §8) |
| Database version | None. Room arrives in Phase 2.1 and will start at **version 1** |

---

## 2. OBJECTIVE

Give the app its own visual identity and its screen navigation, with no live data:

- Application colours (light **and** dark) and typography
- A toolbar style
- Bottom navigation with four tabs
- Placeholder Dashboard, Map, History and Settings screens

Acceptance: the app opens on the Dashboard, all four tabs switch correctly, each tab survives rotation, and
dark mode looks right.

---

## 3. UNDERSTANDING CHECKLIST

- [x] Java only, XML layouts, AndroidX, Material Design Components. No Kotlin, no Compose.
- [x] **No live data** in this phase — no Room, no location, no network.
- [x] Single Activity, Fragments, manual `FragmentManager` navigation (**AD-1**). No Navigation Component.
- [x] Bottom navigation is the primary navigation (**A17**): Dashboard · Map · History · Settings.
- [x] Every user-visible string is in `strings.xml` — nothing hardcoded in a layout.
- [x] Colours are defined once and never repeated as literals in layouts.
- [x] Marker colours are reserved now but **not used** until Phase 3.4.
- [x] Nothing is invented: no store, coordinate, code, count or API key appears anywhere.

---

## 4. FEATURES INCLUDED (in this phase)

| Feature | Detail |
| --- | --- |
| **Light + dark colour scheme** | 15 Material 3 colour roles plus 5 surface-container roles, in `values/colors.xml` and `values-night/colors.xml` under identical names |
| **Marker palette reserved** | `marker_pending`, `marker_completed`, `marker_home`, `marker_current`, `route_line` — defined once, deliberately with **no** night variant |
| **Typography** | Six named text roles (`ScreenTitle`, `CardLabel`, `CardValue`, `ListTitle`, `ListSubtitle`, `Caption`) mapped onto the Material 3 type scale |
| **Toolbar style** | `Widget.AlphaRoute.Toolbar` — flat surface, `ScreenTitle` text role |
| **Splash screen** | `Theme.AlphaRoute.Splash` via the Android SplashScreen API — brand-blue window with a white pin, **no extra Activity** |
| **Bottom navigation** | `BottomNavigationView` with four tabs and four vector icons |
| **Application shell** | Toolbar + `FragmentContainerView` + bottom navigation, driven by `MainActivity` |
| **Four placeholder screens** | `DashboardFragment`, `MapFragment`, `HistoryFragment`, `SettingsFragment` |

---

## 5. FEATURES NOT INCLUDED YET

- No stat cards — Phase 1.3.
- No Room, no `Application` class, no repositories, no ViewModels — Phase 2.1 onwards.
- No map, no location, no markers — Phase 3.x.
- No routing, no navigation hand-off, no arrival detection — Phase 4.x and 5.x.
- No tracker data, no history list, no settings storage — Phase 6.x and 2.3.
- No custom app icon — Phase 10.3.
- No toolbar menu — nothing needs one yet.

---

## 6. ASSUMPTIONS

| # | Assumption | Impact if wrong |
| --- | --- | --- |
| A18 | The colour values are **proposals**. Your brief names the colours (orange / green / Home / blue) but not the hex values. | Every value lives in one file per mode; changing them is a one-line edit. |
| A26 | **One shared placeholder layout** (`fragment_placeholder.xml`) serves all four tabs, rather than four near-identical files. | Each of the four layouts is replaced in its own later phase anyway, so four copies would be four files to delete. If you would rather see four separate files, that is a small change. |
| A27 | Tab fragments are **hidden, not destroyed**, when you switch tabs. | Tabs keep their scroll position and state. If memory ever became a concern we would switch to `replace()`, losing that. |
| A28 | The toolbar is a **plain `MaterialToolbar`**, not a support action bar. | No menu exists yet, so `setSupportActionBar` would add AppCompat title handling that fights the per-tab titles. |
| A29 | `app_tagline` and `app_version_format` are kept even though the Phase 1.1 start screen that used them is gone. | They are reserved for a Settings → About area. Two unused strings, no functional impact. |
| A30 | Splash background is the brand blue `#1565C0` with a white pin. | One colour and one drawable; trivial to change. |

---

## 7. FILES TO CREATE (16)

### Resources — colours and type

| Path | Purpose |
| --- | --- |
| `app/src/main/res/values-night/colors.xml` | **New.** The dark-mode values for the same 20 `md_*` names defined in the light file |
| `app/src/main/res/values/type.xml` | **New.** The six named text roles |
| `app/src/main/res/values/styles.xml` | **New.** `Widget.AlphaRoute.Toolbar` |

### Resources — navigation and graphics

| Path | Purpose |
| --- | --- |
| `app/src/main/res/menu/menu_bottom_nav.xml` | **New.** The four tab items |
| `app/src/main/res/drawable/ic_nav_dashboard.xml` | **New.** 2×2 tile grid |
| `app/src/main/res/drawable/ic_nav_map.xml` | **New.** Folded map sheet |
| `app/src/main/res/drawable/ic_nav_history.xml` | **New.** Clock face |
| `app/src/main/res/drawable/ic_nav_settings.xml` | **New.** Slider controls |
| `app/src/main/res/drawable/ic_splash_logo.xml` | **New.** White map pin for the splash window |
| `app/src/main/res/layout/fragment_placeholder.xml` | **New.** The shared placeholder layout |

All five drawables are **vector** XML — no bitmaps, crisp at every density, and reviewable as text. The four
navigation icons are single-colour (`#FF000000`) because `BottomNavigationView` applies its own tint for the
selected and unselected states; giving them their own colour would fight that.

### Java — the four tab fragments

| Path | Purpose |
| --- | --- |
| `app/src/main/java/com/cph/alpharoute/ui/dashboard/DashboardFragment.java` | **New.** Placeholder; stat cards arrive in Phase 1.3, live data in Phase 6.1 |
| `app/src/main/java/com/cph/alpharoute/ui/map/MapFragment.java` | **New.** Placeholder; osmdroid map arrives in Phase 3.1 |
| `app/src/main/java/com/cph/alpharoute/ui/history/HistoryFragment.java` | **New.** Placeholder; the history list arrives in Phase 6.2 |
| `app/src/main/java/com/cph/alpharoute/ui/settings/SettingsFragment.java` | **New.** Placeholder; settings storage arrives in Phase 2.3 |

### Documentation

| Path | Purpose |
| --- | --- |
| `docs/PHASE_1.2_THEME_NAVIGATION.md` | **New.** This record |

---

## 8. FILES TO MODIFY (8) — and one deletion

| # | Path | Status | What changed |
| --- | --- | --- | --- |
| 1 | `app/src/main/res/values/colors.xml` | **Replaced completely** | 15 Material 3 roles + 5 surface-container roles + 5 marker colours + splash background. The old `black` / `white` entries were removed — verified unused anywhere in `res/` |
| 2 | `app/src/main/res/values/themes.xml` | **Replaced completely** | `Base.Theme.AlphaRoute` now carries all 20 colour items; added the `Theme.AlphaRoute.Splash` style |
| 3 | `app/src/main/res/values/strings.xml` | **Modified** | Added four tab titles and four temporary placeholder strings |
| 4 | `app/src/main/res/layout/activity_main.xml` | **Replaced completely** | The Phase 1.1 branding screen becomes the real shell: `MaterialToolbar` + `FragmentContainerView` + `BottomNavigationView` |
| 5 | `app/src/main/java/com/cph/alpharoute/MainActivity.java` | **Replaced completely** | Splash install, tab switching, state restore, toolbar titles |
| 6 | `app/src/main/AndroidManifest.xml` | **Modified** | `android:theme` on `<application>` → `@style/Theme.AlphaRoute.Splash`. **No permissions added** |
| 7 | `gradle/libs.versions.toml` | **Modified** | Added `coreSplashscreen = "1.2.0"` and the `core-splashscreen` library entry |
| 8 | `app/build.gradle` | **Modified** | Added `implementation libs.core.splashscreen` |

### 🗑️ DELETED: `app/src/main/res/values-night/themes.xml`

**This is a removal, so per rule 11 here is the full justification.**

| | |
| --- | --- |
| **What was removed** | `app/src/main/res/values-night/themes.xml`, which contained a second declaration of `Base.Theme.AlphaRoute` (parent `Theme.Material3.DayNight.NoActionBar`, no items). |
| **Technical reason** | It was **actively dangerous**, not merely redundant. Resource resolution means a `values-night` declaration of the same style **wins** in dark mode. That file did not carry the colour items, so leaving it in place would have made the whole app silently revert to Material's default purple whenever the device was in dark mode — while looking perfectly correct in light mode. A bug that only appears at night, on someone else's phone. |
| **Affected files** | Only that one file. |
| **Safe replacement** | Dark mode is now handled by the **correct** mechanism: one theme definition in `values/themes.xml` referencing `md_*` colour names, and `values-night/colors.xml` redefining those same names with dark values. The theme never has to know which mode it is in, and there is no second style declaration to drift. |
| **Verification** | The build resolves `values-night/colors.xml` alongside `values/colors.xml` with no duplicate-resource error. Test step §15.3 explicitly checks dark mode. |

**Inspected and left unmodified:** `settings.gradle`, root `build.gradle`, `gradle.properties`, `local.properties`, `gradle/wrapper/gradle-wrapper.properties`, `app/proguard-rules.pro`, `app/src/main/res/xml/backup_rules.xml`, `app/src/main/res/xml/data_extraction_rules.xml`, the launcher icon resources, both test files.

---

## 9. DEPENDENCIES

| Artifact | Version | Status | Purpose |
| --- | --- | --- | --- |
| `androidx.core:core-splashscreen` | **1.2.0** | **Added** | The Android SplashScreen API backported to API 23+. Latest stable, verified against Google's Maven metadata |
| `com.android.tools:desugar_jdk_libs` | 2.1.5 | Existing (Phase 1.1) | `java.time` on minSdk 24 |
| `androidx.activity:activity-ktx` | 1.13.0 | Existing | `EdgeToEdge` |
| `androidx.appcompat:appcompat` | 1.7.1 | Existing | `AppCompatActivity`, `FragmentContainerView` |
| `androidx.constraintlayout:constraintlayout` | 2.2.1 | Existing | Shell and placeholder layouts |
| `com.google.android.material:material` | 1.14.0 | Existing | `MaterialToolbar`, `BottomNavigationView`, Material 3 themes |
| test libraries | — | Existing | Unchanged |

**Not added:** Room, Lifecycle, osmdroid, play-services-location, RecyclerView. Each arrives in its own phase.

---

## 10. PERMISSIONS AND MANIFEST CHANGES

**No permission is added.** The manifest change is a single attribute:

```xml
<application
    ...
    android:theme="@style/Theme.AlphaRoute.Splash">
```

**Why the *splash* theme is the application theme, not the normal one:** the system draws this window while the
process is starting. Making it the splash theme means the very first frame is already the brand-blue launch
screen instead of a blank white or black window. `MainActivity` calls
`SplashScreen.installSplashScreen(this)` and the library swaps the activity to `postSplashScreenTheme`
(`Theme.AlphaRoute`) once the first frame is ready.

---

## 11. SETUP INSTRUCTIONS

1. In Android Studio, let the Gradle sync finish — it will fetch `core-splashscreen:1.2.0` on this sync.
2. **Build → Clean Project**, then **Build → Rebuild Project**.
3. Run on a device or emulator (API 24+).
4. If Android Studio shows stale resources after the theme change: **File → Invalidate Caches / Restart →
   Invalidate and Restart**. The IDE caches merged resource tables aggressively.

---

## 12. COMPLETE CODE

**All 24 files are written into the project on disk** and attached to this message — see §7 and §8 for the
complete list and each file's role. This phase does not duplicate them here (see the note at the top of this
document).

The files worth reading first, in order:

1. `app/src/main/res/values/themes.xml` — the single theme definition and the splash style
2. `app/src/main/res/values/colors.xml` and `values-night/colors.xml` — the two colour schemes
3. `app/src/main/res/layout/activity_main.xml` — the shell
4. `app/src/main/java/com/cph/alpharoute/MainActivity.java` — tab switching and state restore
5. `app/src/main/res/values/type.xml` — the six text roles
6. `app/src/main/res/menu/menu_bottom_nav.xml` — the four tabs

---

## 13. CODE EXPLANATION

### 13.1 Why the theme is defined only once

`Base.Theme.AlphaRoute` lives in `values/themes.xml` and carries all 20 colour items pointing at `md_*` names.
The `DayNight` parent switches Material's own resources, and `values-night/colors.xml` redefines those same
`md_*` names. So the theme is mode-agnostic, there is exactly one place to change a colour role, and the
dangerous duplicate in `values-night/themes.xml` is gone (see §8).

### 13.2 Why the marker colours have no night variant

`marker_pending`, `marker_completed`, `marker_home`, `marker_current` and `route_line` are defined **only** in
the light file, deliberately. They are drawn on map tiles, not on the app surface, so they must look identical
in light and dark. Defining them twice would invite someone to "fix" them later and break the marker scheme.

### 13.3 Why the status bar is not set in the theme

`MainActivity` calls `EdgeToEdge.enable(this)`. androidx.activity sets both system bars transparent **and**
chooses the correct light/dark icon appearance from the current UI mode. Setting `android:statusBarColor` or
`android:windowLightStatusBar` in the theme would duplicate that, would need a separate value per mode, and
`android:statusBarColor` is deprecated and ignored from API 35 — which this app targets (36).

### 13.4 How tab switching works, and the two restore traps it avoids

```
BottomNavigationView item tapped
        │
        ▼
MainActivity.tagForMenuItem(itemId)  ──►  "tag_map" / "tag_history" / …
        │
        ▼
showTab(tag)
   ├─ for every OTHER tag: if its fragment exists and is visible → hide()
   ├─ if this tag's fragment does not exist yet → add(container, new …, tag)
   ├─ else if it is hidden → show()
   └─ commit()
        │
        ▼
updateToolbarTitle(tag)  ──►  reads the title from the menu item itself
```

Two traps this code deliberately avoids:

1. **The listener is attached *after* the state restore.** `BottomNavigationView` restores its selected item
   itself. If the listener were attached first, that restore would fire it and `showTab` would `add` a second
   copy of a fragment that `FragmentManager` had already recreated — giving you two overlapping screens after
   every rotation.
2. **The toolbar title is read from the menu item, not from a second list of strings.**
   `updateToolbarTitle` looks the title up in the menu, so the tab label and the toolbar label cannot drift
   apart when a string is edited.

Fragments are **hidden, not replaced**, so each tab keeps its state (A27). And because fragments are added with
a **tag**, `findFragmentByTag` is how we find them again — no fragment ids to track.

### 13.5 Why the fragments take the layout id in their constructor

```java
public DashboardFragment() {
    super(R.layout.fragment_placeholder);
}
```

`FragmentManager` recreates fragments through a **no-argument constructor** when the system restores state
after rotation or process death. Passing the layout id up to `super(...)` from that same no-arg constructor
satisfies both requirements at once, so there is no separate `newInstance` factory to forget. A class with
*only* a `Fragment(int)` constructor and no no-arg constructor would crash on rotation.

### 13.6 Why the placeholder strings name their phases

`placeholder_dashboard` says "built in Phase 1.3 and connected to live data in Phase 6.1" rather than
something generic. During development this makes it immediately obvious which screen is still a stub, and each
string is deleted in the phase that fills in its screen. They are marked **TEMPORARY** in `strings.xml` with
the exact deletion phase listed.

---

## 14. DATA FLOW FOR THIS PHASE

```
App process starts
        │
        ▼
Theme.AlphaRoute.Splash window drawn       brand blue + white pin (no Activity yet)
        │
        ▼
MainActivity.onCreate()
        ├─ SplashScreen.installSplashScreen(this)   takes over the splash theme
        ├─ EdgeToEdge.enable(this)                  bars transparent, icons match UI mode
        ├─ setContentView(activity_main)            toolbar + container + bottom nav
        ├─ apply system-bar insets to root_main
        ├─ savedInstanceState == null ?
        │      yes → activeTag = dashboard; showTab(dashboard)   adds DashboardFragment
        │      no  → activeTag restored from the bundle          fragments already restored
        ├─ updateToolbarTitle(activeTag)
        └─ bottomNav.setOnItemSelectedListener(...)  attached LAST, on purpose
                        │
                        │  user taps a tab
                        ▼
                 hide the previous tab fragment → show or add the new one
                 → toolbar title updates from the menu item
```

**There is no data in this phase.** Nothing reads or writes a database, a network, or a sensor. The only state
is which tab is active, and that lives in the saved-instance-state bundle.

---

## 15. MANUAL TEST PROCEDURE

### 15.0 Already verified automatically (2026-09-25)

`:app:assembleDebug` was run on this machine with Gradle 9.4.1 / JDK 21 and returned **`BUILD SUCCESSFUL in 2m`**,
35 actionable tasks executed.

| Evidence | Result |
| --- | --- |
| `:app:mergeDebugResources` | ran — **no duplicate-resource error**, so `values-night/colors.xml` coexists correctly with `values/colors.xml` after the `values-night/themes.xml` deletion |
| `:app:processDebugResources` | ran — **resource linking succeeded**, so `Theme.SplashScreen`, `Widget.Material3.Toolbar`, the six `TextAppearance.Material3.*` parents, the menu, all five drawables and every `@color/` / `@style/` / `@string/` reference resolved |
| `:app:compileDebugJavaWithJavac` | ran — `MainActivity` and all four fragments compiled |
| `:app:l8DexDesugarLibDebug` | ran — desugaring (AD-7) still active |
| `:app:compileDebugKotlin` | **NO-SOURCE** — still zero Kotlin source files |
| Merged manifest | `android:theme="@style/Theme.AlphaRoute.Splash"` — the splash theme is applied |
| `app/build/outputs/apk/debug/app-debug.apk` | **7,846,975 bytes** (up from 7,778,347 — the new `core-splashscreen` dependency and resources) |

So the whole build half is proven, including the risky part: **the resource merge and link both pass**, which is
what confirms the night-colour mechanism and the deleted night theme are correct. What remains is the runtime
half: tabs, rotation, dark mode, splash.

### 15.1 Basic run

1. Open the project, let Gradle sync, then **Clean → Rebuild → Run** on API 24+.
2. The app opens on the **Dashboard** tab: the toolbar reads "Dashboard" and the placeholder shows the grid
   icon, the title "Dashboard" and its note.
3. Tap **Map** — toolbar reads "Map", the map placeholder appears, the Map icon is highlighted.
4. Tap **History**, then **Settings** — same behaviour each time.
5. Tap back to **Dashboard** — it reappears with no flicker and no duplicate content.

### 15.2 Rotation and process death

6. On the **History** tab, rotate the device. The app must come back on **History** with the toolbar still
   reading "History" — **and only one copy of the screen must be visible.** This is the trap described in
   §13.4; a double screen here means the listener was attached too early.
7. Enable **Developer options → Don't keep activities**, then background and reopen the app. It must restore
   cleanly rather than crash or show a blank container.

### 15.3 Dark mode

8. **Settings → Display → Dark theme** → turn it on while the app is open.
9. Confirm: the background goes dark, the toolbar and bottom navigation go dark, text stays legible, and the
   accent colour is the **light blue** (`#A9C7FF`), not the light-mode blue and **not** Material's default
   purple. This is the check that proves the deleted `values-night/themes.xml` was replaced correctly.
10. Turn dark mode off and confirm it returns to the light scheme.

### 15.4 Splash

11. Force-stop the app (**Settings → Apps → Alfamart Route Tracker → Force stop**), then launch it again.
    A brief brand-blue window with the white pin should appear before the Dashboard — **not** a blank white
    window, and not the Android robot icon.

### 15.5 Edge-to-edge

12. Confirm the toolbar is not hidden under the status bar and the bottom navigation is not hidden under the
    gesture/navigation bar.

**Report any failure with the exact screen, the step number, and any Logcat text.**

---

## 16. EXPECTED RESULT

- Four working tabs, each with its own icon, toolbar title and placeholder.
- The active tab is preserved across rotation and process death, with no duplicated screens.
- Light and dark modes both use the AlphaRoute palette, not Material's default purple.
- A brand-blue splash window on launch, with no extra Activity.
- Nothing is hidden behind the system bars.
- Still **no live data**: no database, no network, no location.

---

## 17. COMMON ERRORS AND FIXES

| Symptom | Cause | Fix |
| --- | --- | --- |
| Two screens overlap after rotating | The nav listener was attached before the state restore, so a fragment was added twice | `setOnItemSelectedListener` must come **after** the `savedInstanceState` block in `onCreate` |
| App opens on Dashboard every time, even after being on Settings | `activeTag` not saved | Confirm `onSaveInstanceState` writes `STATE_ACTIVE_TAG` and `onCreate` reads it |
| Dark mode shows Material purple | A duplicate `Base.Theme.AlphaRoute` exists in `values-night/` and wins in dark mode | It was deleted in this phase — confirm `values-night/` contains only `colors.xml` |
| `Duplicate resources` build error | A style or colour is declared twice in the same folder | Check `values/` and `values-night/` for repeated names |
| `resource style/Theme.SplashScreen not found` | `core-splashscreen` not synced | Re-sync Gradle; confirm the dependency is in `app/build.gradle` |
| Blank white screen on launch instead of the splash | `android:theme` on `<application>` still points at `Theme.AlphaRoute` | It must be `@style/Theme.AlphaRoute.Splash` |
| `SplashScreen.installSplashScreen` crashes or does nothing | Called after `super.onCreate` | It must be the **first** statement in `onCreate` |
| Bottom nav labels truncated | Long titles with `labelVisibilityMode="labeled"` | Keep tab titles short ("Dashboard", "Map", "History", "Settings") |
| Icons invisible in the nav bar | The drawable carries its own colour and fights the tint | Navigation icons must use `#FF000000` and let the nav view tint them |
| `R.menu.menu_bottom_nav` not found | The `menu/` folder is new in this phase | Re-sync Gradle |
| Stale colours after editing the theme | Android Studio's merged-resource cache | **File → Invalidate Caches / Restart** |

---

## 18. ACCEPTANCE CHECKLIST

- [ ] Gradle sync completes with no error.
- [ ] Clean build succeeds.
- [ ] The app opens on the **Dashboard** tab.
- [ ] All four tabs switch correctly and the toolbar title follows.
- [ ] Rotating on a non-default tab returns to that tab with **one** screen visible.
- [ ] Process death (Don't keep activities) restores cleanly.
- [ ] **Dark mode** uses the AlphaRoute palette, not Material's default purple.
- [ ] The launch splash is brand blue with the white pin.
- [ ] Nothing is hidden behind the status or navigation bars.
- [ ] No permission prompt appears.
- [ ] Confirmed: still **no Kotlin source file** anywhere in the project.

---

## 19. UPDATED PROJECT STATE

**COMPLETED PHASES**
- ✅ Phase 0.1 — Requirements Review (Rev 5, FINAL) — approved 2026-09-25
- ✅ Phase 0.2 — Architecture and Data Flow — approved 2026-09-25
- ✅ Phase 0.3 — UI and Screen Plan — approved 2026-09-25
- ✅ Phase 1.1 — Create Android Java Project — approved 2026-09-25 (build-verified)
- 🔄 Phase 1.2 — Theme and Base Navigation — *awaiting your on-device test*

**CURRENT PHASE**
- Phase 1.2 — awaiting the on-device test

**CURRENT DATABASE VERSION**
- None. Room has not been added. `AppDatabase` will start at **version 1** in Phase 2.1.

**CURRENT FILES** (new and changed this phase)

```
gradle/libs.versions.toml                                   (MODIFIED - core-splashscreen 1.2.0)
app/build.gradle                                            (MODIFIED - core-splashscreen dep)
app/src/main/AndroidManifest.xml                            (MODIFIED - splash theme; no permissions)

app/src/main/res/values/colors.xml                          (REPLACED - 25 colours, light)
app/src/main/res/values-night/colors.xml                    (NEW - 20 colour roles, dark)
app/src/main/res/values-night/themes.xml                    (DELETED - dangerous duplicate, see s8)
app/src/main/res/values/themes.xml                          (REPLACED - colour roles + splash style)
app/src/main/res/values/type.xml                            (NEW - six text roles)
app/src/main/res/values/styles.xml                          (NEW - toolbar widget style)
app/src/main/res/values/strings.xml                         (MODIFIED - tab titles + placeholders)

app/src/main/res/menu/menu_bottom_nav.xml                   (NEW)
app/src/main/res/drawable/ic_nav_dashboard.xml              (NEW)
app/src/main/res/drawable/ic_nav_map.xml                    (NEW)
app/src/main/res/drawable/ic_nav_history.xml                (NEW)
app/src/main/res/drawable/ic_nav_settings.xml               (NEW)
app/src/main/res/drawable/ic_splash_logo.xml                (NEW)

app/src/main/res/layout/activity_main.xml                   (REPLACED - toolbar + container + nav)
app/src/main/res/layout/fragment_placeholder.xml            (NEW - shared placeholder)

app/src/main/java/com/cph/alpharoute/MainActivity.java      (REPLACED - tab switching + state restore)
app/src/main/java/com/cph/alpharoute/ui/dashboard/DashboardFragment.java  (NEW)
app/src/main/java/com/cph/alpharoute/ui/map/MapFragment.java              (NEW)
app/src/main/java/com/cph/alpharoute/ui/history/HistoryFragment.java      (NEW)
app/src/main/java/com/cph/alpharoute/ui/settings/SettingsFragment.java    (NEW)

docs/PHASE_1.2_THEME_NAVIGATION.md                          <- created this phase
```

**WORKING FEATURES** (verified)
- ✅ **Build succeeds** — `:app:assembleDebug` → `BUILD SUCCESSFUL in 2m`, 35 tasks (verified 2026-09-25)
- ✅ **Resources merge and link cleanly** — night colours + deleted night theme are correct
- ✅ **All five Java classes compile** — `MainActivity` + the four tab fragments
- ✅ **Splash theme applied** — merged manifest reads `android:theme="@style/Theme.AlphaRoute.Splash"`
- ✅ **APK produced** — 7,846,975 bytes
- ✅ **Core library desugaring still active** — `:app:l8DexDesugarLibDebug` executed
- ✅ **Still zero Kotlin source files** — `:app:compileDebugKotlin` = `NO-SOURCE`
- ⏳ **Four-tab navigation, rotation restore, dark mode, splash, edge-to-edge** — awaiting your §15.1–15.5 test

**KNOWN ISSUES**
- None blocking.
- **Temporary content:** four `placeholder_*` strings are intentionally stub text; each is deleted in the
  phase that fills in its screen (1.3 / 3.1 / 6.2 / 2.3). `app_tagline` and `app_version_format` are currently
  unused (reserved for a Settings → About area) — Android Studio may flag them as unused resources; that is
  expected, not a fault.
- Carried risks: OSM data quality in remote PH areas · OSM tile policy compliance (Phase 3.1) ·
  openrouteservice free-tier quota · no offline map download · osmdroid cadence (AD-8) · date boundary (AD-7 + AD-11).
- The project is **not under version control**. `git init` is now genuinely worth doing — there are 24 changed
  files in this phase alone. I will not do it without your go-ahead.
- Trademark note: "Alfamart" is a third-party brand; fine for a private solo tool (D-5), may need permission
  if distributed.

**PENDING PHASES** (not implemented)
- **1.3 Dashboard static UI** — next
- 2.1 Room foundation · 2.2 Store CRUD · 2.3 Settings storage
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

Phase 1.2 is complete. **24 files touched: 16 created, 8 modified, 1 deleted.**

**The three decisions worth knowing about:**

1. **The theme is defined exactly once.** `Base.Theme.AlphaRoute` carries 20 colour items pointing at `md_*`
   names; `values-night/colors.xml` redefines those names. The theme is mode-agnostic, so there is one place
   to change a colour.

2. **`values-night/themes.xml` was deleted, and that was a bug fix, not tidying.** A `values-night` declaration
   of the same style *wins* in dark mode. That file did not carry the colour items, so leaving it would have
   made the whole app silently fall back to Material's default purple at night — correct in daylight, wrong
   after dark. Dark mode is now handled by the proper mechanism. §15.3 tests it explicitly.

3. **Two restore traps are avoided in `MainActivity`.** The nav listener is attached *after* the saved-state
   restore (otherwise rotation adds a duplicate fragment), and the toolbar title is read from the menu item
   itself (so the tab label and the toolbar can never disagree). §15.2 tests rotation.

**Please run §15 and report the result** — especially §15.2 (rotation shows only one screen) and §15.3 (dark
mode is blue, not purple). If either fails, paste what you saw and I will diagnose before starting Phase 1.3.

**Next phase on your instruction: Phase 1.3 — Dashboard static UI** — the six stat cards (Today, Yesterday,
Overall, Remaining, Percentage, Latest Completed) with clearly labelled placeholder values for visual testing
only, plus the Version-7 suggestion card slot.

**Phase complete. Do not proceed to the next phase until all acceptance criteria pass.**

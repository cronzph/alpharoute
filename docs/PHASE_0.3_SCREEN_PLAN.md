# AlphaRoute — Phase 0.3 UI and Screen Plan

**Project title:** Alfamart Route Tracker
**Version:** 0 (Planning) — **Phase:** 0.3 UI and Screen Plan
**Repository root:** `C:/Users/Admin/OneDrive/Documents/custom system/AlphaRoute`
**Date:** 2026-09-25
**Prerequisites:** Phase 0.1 Rev 5 (FINAL, approved) — stack frozen as **Option D1**. Phase 0.2 (approved) — AD-1 single Activity, AD-7 desugaring.
**Status:** ✅ **PHASE 0.3 COMPLETE AND APPROVED.** A17 resolved: **bottom navigation is the primary navigation**; the three Dashboard buttons are dropped.
**No production code is generated in this phase.**

---

## 1. CURRENT VERSION AND PHASE

| Item | Value |
| --- | --- |
| Version | 0 — Planning |
| Phase | 0.3 — UI and Screen Plan |
| Production code written | **None** (forbidden in this phase) |
| Files created | `docs/PHASE_0.3_SCREEN_PLAN.md` (this document) |
| Files modified | **None** |
| Source files touched | **None** |
| Database version | None yet — Room starts at v1 in Phase 2.1 |

### Adaptation note — two screens changed from the brief
The brief lists an "In-App Navigation Screen" and a "Splash or Launch Screen". Under the approved stack these
are adapted. **Both adaptations are proposals requiring your approval** (rules 9 and 11) — I will not change an
approved requirement silently.

| Brief | Actual under Option D1 | Why |
| --- | --- | --- |
| **8. In-App Navigation Screen** with turn-by-turn, next-turn distance and ETA | **Navigation hand-off / monitoring panel** — shows the destination, distance to destination, arrival status, and *Open Google Maps* / *End Navigation*. Turn-by-turn, traffic and road rules live in the Google Maps app. | D-6/D-7: the Google Maps app does the driving. We cannot and must not render Google's guidance inside our app. |
| **1. Splash or Launch Screen** | **The Android SplashScreen API** — a themed launch window (icon on a background colour) shown by the system while `MainActivity` starts. No separate Activity. | AD-1 keeps a single Activity. The SplashScreen API is the modern, zero-extra-Activity way to get a launch screen. |

---

## 2. OBJECTIVE

Plan every screen before any layout XML is written. For each screen: **purpose, inputs, outputs, buttons, empty
state, loading state, error state**. Plus the shared UI conventions (naming, strings, theming, marker assets,
accessibility) that keep the layouts consistent.

No production code.

---

## 3. UNDERSTANDING CHECKLIST

- [x] Java + XML layouts only. Material Design Components. AndroidX. No Compose.
- [x] Single Activity (`MainActivity`) + Fragments, manual `FragmentManager` navigation (AD-1).
- [x] Bottom navigation with four tabs: **Dashboard · Map · History · Settings**.
- [x] Every user-visible string lives in `strings.xml` — no hardcoded text in layouts.
- [x] Markers differ by **icon and shape**, not colour alone.
- [x] Every destructive or irreversible action needs confirmation.
- [x] The app must remain usable with no network: every screen needs a sane offline state.
- [x] Completion is never automatic and never triggered by tapping a marker.

---

## 4. FEATURES INCLUDED (in this phase)

The screen inventory, per-screen specifications, layout/resource file plan, marker asset plan, colour proposals,
and the UI conventions (naming, strings, accessibility, theming).

---

## 5. FEATURES NOT INCLUDED YET

Every executable artefact: no layouts, no drawables, no strings, no Fragments, no Activities.

---

## 6. ASSUMPTIONS

Inherited A1–A16, plus:

| # | Assumption | Impact if wrong |
| --- | --- | --- |
| A17 | ✅ **RESOLVED 2026-09-25 — bottom navigation is the primary navigation; the Dashboard's three buttons (Open Map / View History / Settings) are dropped.** | Approved deviation from the original Phase 1.3 text. Reversible in one line if you change your mind. |
| A18 | Marker colours are proposals (orange / green / home / blue) — your spec names the colours but not the hex values. | Values can be changed in one place (`colors.xml`). |
| A19 | Portrait-only orientation is acceptable for v1. | Landscape would need alternate layouts for the Map and Dashboard. |
| A20 | English only, single locale, for v1. | Additional locales would need `values-xx/strings.xml`. |

---

## 7. FILES TO CREATE (this phase)

| Path | Type | Purpose |
| --- | --- | --- |
| `docs/PHASE_0.3_SCREEN_PLAN.md` | **New** | This screen plan. |

---

## 8. FILES TO MODIFY (this phase)

**None.**

---

## 9. DEPENDENCIES

Nothing added now. One dependency appears because of the splash adaptation:

| Phase | Dependency | Why |
| --- | --- | --- |
| 1.2 | `androidx.core:core-splashscreen` | The Android SplashScreen API backported to API 23+ — gives a launch screen with no extra Activity (AD-1). |

Everything else is already planned in Phase 0.2 §9. No new map, routing or navigation dependency.

---

## 10. PERMISSIONS AND MANIFEST CHANGES

Nothing added in Phase 0.3. One **manifest declaration** is worth restating because it is easy to miss and it
silently breaks screen 8:

```xml
<queries>
    <package android:name="com.google.android.apps.maps" />
    <package android:name="com.waze" />
</queries>
```

Without this, `resolveActivity()` returns `null` on Android 11+ and the *Start Navigation* button appears broken
even though Google Maps is installed.

Phase 1.2 will also add the splash theme:

```xml
<style name="Theme.AlphaRoute.Splash" parent="Theme.SplashScreen">
    <item name="windowSplashScreenBackground">@color/splash_background</item>
    <item name="windowSplashScreenAnimatedIcon">@mipmap/ic_launcher_foreground</item>
    <item name="postSplashScreenTheme">@style/Theme.AlphaRoute</item>
</style>
```

---

## 11. SETUP INSTRUCTIONS

Nothing to set up. Outstanding from Phase 0.1: run the baseline build check (Clean → Rebuild → Run) and report
the result.

---

## 12. COMPLETE CODE

**None.** Phase 0.3 forbids production code.

---

## 13. UI AND SCREEN EXPLANATION

### 13.1 Screen inventory

| # | Screen | Type | Layout file | Class |
| --- | --- | --- | --- | --- |
| 1 | Splash / launch | System window | *(splash theme, no layout)* | *(none — SplashScreen API)* |
| 2 | Dashboard | Tab fragment | `fragment_dashboard.xml` | `DashboardFragment` |
| 3 | Map | Tab fragment | `fragment_map.xml` | `MapFragment` |
| 4 | Add / Edit Store | Bottom sheet | `sheet_add_edit_store.xml` | `AddEditStoreBottomSheet` |
| 5 | Store Details | Bottom sheet | `sheet_store_detail.xml` | `StoreDetailBottomSheet` |
| 6 | Home Setup | Pushed fragment | `fragment_home_setup.xml` | `HomeSetupFragment` |
| 7 | Route Preview | Bottom sheet | `sheet_route_preview.xml` | `RoutePreviewBottomSheet` |
| 8 | Navigation hand-off / monitoring | Bottom sheet on the Map tab | `sheet_navigation_active.xml` | `NavigationActiveSheet` |
| 9 | Completion confirmation | Dialog fragment | `dialog_completion_confirm.xml` | `CompletionConfirmDialogFragment` |
| 10 | History | Tab fragment | `fragment_history.xml` | `HistoryFragment` |
| 11 | Daily History | Pushed fragment | `fragment_daily_history.xml` | `DailyHistoryFragment` |
| 11b | Completion Detail | Pushed fragment | `fragment_completion_detail.xml` | `CompletionDetailFragment` |
| 12 | Settings | Tab fragment | `fragment_settings.xml` | `SettingsFragment` |

Reusable list rows: `item_stat_card.xml`, `item_history_date_group.xml`, `item_history_row.xml`,
`item_pending_store_row.xml`.

> **Note on 11b:** your brief lists 11 screens and names only "Daily History Details". Phase 6.4 separately
> requires a *completion detail* view (store details, completion date, time, daily sequence, coordinates,
> *View on Map*). I have planned it as **11b** rather than merging it into 11, so each screen stays small.

### 13.2 Screen 1 — Splash / launch

| Aspect | Detail |
| --- | --- |
| **Purpose** | Show the app identity while `MainActivity` starts; hide cold-start work (Room open, osmdroid `Configuration`, tile cache path). |
| **Layout** | None. A themed window via the SplashScreen API. |
| **Inputs** | None. |
| **Outputs** | None. It never collects or shows data. |
| **Buttons** | None. |
| **Empty / loading / error** | Not applicable — it is a system window, always "loaded". |
| **Notes** | Keep it brief. If it ever needs a visible progress indicator, we add a lightweight splash **Fragment** inside `MainActivity` instead of a second Activity, preserving AD-1. |

### 13.3 Screen 2 — Dashboard

| Aspect | Detail |
| --- | --- |
| **Purpose** | Show the whole picture at a glance: today, yesterday, overall, remaining, total, percentage, latest completed — and the suggested next store. |
| **Layout** | `fragment_dashboard.xml` — a scrolling `NestedScrollView` containing a 2-column grid of stat cards, a next-store suggestion card, and three action buttons. |
| **Inputs** | None from the user (read-only screen). |
| **Outputs** | Navigation intents only: open Map, open History, open Settings, open the suggested store. |
| **Stat cards (6)** | **Completed today** · **Completed yesterday** · **Overall completed** · **Remaining** · **Completion %** · **Latest completed** (store code + time, or "—"). Each card: label + value. Value uses Material `headlineSmall`. |
| **Buttons** | **None.** Movement between Dashboard, Map, History and Settings is done entirely with the **bottom navigation** (A17, resolved). From Version 7 the suggestion card adds **Navigate** and **Choose Another Store** — those are actions on a specific store, not screen navigation, so they stay. |
| **Empty state** | Zero stores: all counters show `0`, percentage shows `0.0%`, and the suggestion card is replaced by a prompt — "No stores yet. Open the **Map** tab and long-press the map to add your first store." (No action button — the bottom navigation is right there.) No divide-by-zero. |
| **Loading state** | Skeleton/placeholder values with a small progress indicator while the first Room emission arrives. In practice this is a fraction of a second. |
| **Error state** | If the database read fails: the cards show `—` and a non-blocking message bar appears — "Could not load your progress. Pull to retry." with a **Retry** action. The screen never shows stale invented numbers. |
| **Notes** | Phase 1.3 builds this screen with **clearly labelled placeholder values** for visual testing only (per your brief); Phase 6.1 wires it to Room. The percentage is always shown to **one decimal place**. |

### 13.4 Screen 3 — Map

| Aspect | Detail |
| --- | --- |
| **Purpose** | The working surface: see your position, Home, and every store; tap a store; long-press to add one. |
| **Layout** | `fragment_map.xml` — a full-bleed `org.osmdroid.views.MapView` with floating controls on top. |
| **Inputs** | Tap on a marker · long-press on empty map (adds a store) · the floating buttons. |
| **Outputs** | Opens Store Details (tap), opens Add Store prefilled with the pressed coordinates (long-press), pans/zooms the camera. |
| **Markers** | Blue = current location (+ accuracy ring) · **Orange circle pin** = pending · **Green check pin** = completed · **House pin** = Home. Colour **and** shape differ, per your accessibility rule. |
| **Floating controls** | **My location** (FAB) · **Home** (FAB — set up, or fly to it) · **Suggest next store** (extended FAB / card, Version 7) |
| **Empty state** | No stores yet: the map still renders with the current location and Home (if set), and a dismissible hint chip appears — "Long-press the map to add a store." |
| **Loading state** | A thin indeterminate progress bar while tiles and the first store list load. The map itself shows tiles as they arrive — we do not cover it with a spinner. |
| **Error state** | *Tiles fail* → a small banner: "Map tiles unavailable — check your connection. Your pins are safe." (pins stay drawn from Room). *Location denied* → a banner with **Grant permission** / **Why?**. *Location unavailable* → "Waiting for GPS…" chip, and the camera is **not** forced. |
| **Notes** | The camera is never continuously forced to the current location. Long-press coordinates come from the pressed map point — never invented. |

### 13.5 Screen 4 — Add / Edit Store

| Aspect | Detail |
| --- | --- |
| **Purpose** | Create a store at a long-pressed map point, or edit an existing one. |
| **Layout** | `sheet_add_edit_store.xml` — a `BottomSheetDialogFragment` with four text fields, a read-only coordinate row, and two buttons. |
| **Inputs** | **Route number** (required) · **Store code** (required) · **Store name** (required) · **Area** (required, free text, max 60) · coordinates (read-only, shown as lat/lng to 6 dp with a "from the map" caption). |
| **Outputs** | A new `PENDING` store, or an updated store. Closes the sheet and refreshes the marker on success. |
| **Buttons** | **Save** (primary) · **Cancel** (text button). In edit mode the title reads "Edit store". |
| **Empty state** | In add mode the four fields are blank; the coordinate row is already filled from the map point. |
| **Loading state** | On **Save**: both buttons disable, the primary button shows a small progress indicator, and the sheet cannot be dismissed by a swipe — this is what prevents a duplicate save from rapid tapping. |
| **Error state** | Per-field inline errors: *"Route number is required"*, *"Store code is required"*, *"Store name is required"*, *"Area is required"*, *"Store code already exists"*, *"Route number already exists"*, *"Coordinates are outside the valid range"*. The sheet stays open, the offending field is focused, and the Save button re-enables. |
| **Notes** | Validation runs in the pure `StoreValidator` (AD-11), so the rules are unit-testable. Values are trimmed before validation — blank-only input is rejected. The database `UNIQUE` indices are the final guard (AD-5). |

### 13.6 Screen 5 — Store Details

| Aspect | Detail |
| --- | --- |
| **Purpose** | Show one store and offer the actions available for its status. |
| **Layout** | `sheet_store_detail.xml` — a `BottomSheetDialogFragment` with a status chip, a detail list, and an action row. |
| **Inputs** | The tapped marker's store id. |
| **Outputs** | Opens Route Preview, opens Add/Edit in edit mode, or deletes the store. |
| **Displayed** | **Status chip** ("Pending" orange / "Completed" green) · Route number · Store code · Store name · Area · Coordinates · *(completed only)* completion date, time and daily sequence. |
| **Buttons** | **Get Directions** (primary) · **Edit** · **Delete**. |
| **Empty state** | Not applicable — it is only opened from an existing marker. If the store vanished (deleted elsewhere), the sheet closes with a toast: "That store no longer exists." |
| **Loading state** | A brief placeholder row while the store is re-read from Room by id. |
| **Error state** | Store not found → close + message. Delete failure (protected by AD-6) → "This store has completion history and cannot be deleted." |
| **Notes** | **A pending store outside the arrival radius shows no completion action at all.** Completion is never offered here — it only appears through the arrival flow (screen 9). For a completed store the Delete action is disabled with an explanatory caption. |

### 13.7 Screen 6 — Home Setup

| Aspect | Detail |
| --- | --- |
| **Purpose** | Set or change Home — the permanent final destination. |
| **Layout** | `fragment_home_setup.xml` — a pushed fragment containing an embedded map preview, a coordinate readout, and three buttons. |
| **Inputs** | A map tap (to place the Home pin) or the current GPS position. |
| **Outputs** | Persisted `homeLatitude`, `homeLongitude`, `homeSet = true` in `AppSettingEntity`. |
| **Buttons** | **Use current location** · **Select on map** · **Confirm home location** (primary, enabled only once a point is chosen) · plus a back action. |
| **Empty state** | Home not set: the map opens centred on the current location (if available) with no Home pin, and a caption — "Home is where your route finishes. It is not counted as a store." |
| **Loading state** | While the first GPS fix is being acquired: the coordinate readout shows "Locating…" and **Use current location** is disabled. |
| **Error state** | *No location permission* → the button offers **Grant permission** with an explanation. *No GPS fix* → "Could not get your location. You can still select Home on the map." *Save failed* → "Could not save Home. Try again." |
| **Notes** | Home is never stored as a store record, never affects any count, and is visually distinct (house pin). Changing Home never alters completion history. |

### 13.8 Screen 7 — Route Preview

| Aspect | Detail |
| --- | --- |
| **Purpose** | Show the route to the selected store **before** anything navigational happens. |
| **Layout** | `sheet_route_preview.xml` — a bottom sheet over the map, with a summary block and a two-button action row. |
| **Inputs** | The selected store; the current location as origin. |
| **Outputs** | A drawn route polyline on the map; a navigation hand-off on **Start Navigation**. |
| **Displayed** | Selected store (route number, code, name, area) · **Distance** · **Estimated duration** (when the routing service returns it) · a caption that the route comes from openrouteservice and may differ from Google's. |
| **Buttons** | **Start Navigation** (primary) · **Cancel**. |
| **Empty state** | Not applicable — always opened with a destination. |
| **Loading state** | "Calculating route…" with an indeterminate bar while the request is in flight. The polyline is not drawn until a real route arrives. |
| **Error state** | *Offline / request failed* → "Routing unavailable — check your connection." with **Retry** and **Cancel**. We **never fake a route**. *Quota exhausted* → "Daily routing limit reached. Try again tomorrow." *No location yet* → "Waiting for your location before routing." |
| **Notes** | Origin is always the current location; the destination is always the selected store. Navigation **never** starts from this screen automatically — it requires an explicit tap. |

### 13.9 Screen 8 — Navigation hand-off / monitoring  *(adapted)*

| Aspect | Detail |
| --- | --- |
| **Purpose** | Confirm that guidance is running in the Google Maps app, show how far away the destination is, and watch for arrival — while the actual turn-by-turn, live traffic and road rules happen in Google Maps. |
| **Layout** | `sheet_navigation_active.xml` — a compact, persistent sheet on the Map tab (also reachable from the Dashboard's suggestion card). |
| **Inputs** | The active destination (from `AppSettingEntity.lastSelectedStoreId`); live location updates. |
| **Outputs** | **Open Google Maps** (re-launches the hand-off Intent) · **End Navigation** (stops monitoring, clears the active session). |
| **Displayed** | "Navigating to **<store code> — <store name>**" · distance remaining to the destination · an arrival indicator: *Outside arrival radius* / *Inside arrival radius — confirm when you arrive* · a note that turn-by-turn, traffic and road rules are handled by Google Maps. |
| **Buttons** | **Open Google Maps** (primary) · **End Navigation** (text button). |
| **Empty state** | When no session is active the sheet is hidden entirely — it only appears once **Start Navigation** has been tapped. |
| **Loading state** | "Starting navigation…" for the moment between the tap and Google Maps coming to the foreground. |
| **Error state** | *Google Maps not installed* → "Google Maps is not installed. Install it, or use Waze." with **Open Waze** / **Install Google Maps**. *Location permission lost during navigation* → "Location access stopped — arrival cannot be detected." with **Grant permission** and **End Navigation**. *Monitoring service stopped* → "Arrival monitoring stopped. Reopen navigation?" with **Resume**. |
| **Notes** | This sheet is where the brief's "In-App Navigation Screen" lands. We deliberately do **not** render turn-by-turn guidance, because that is the Google Maps app's job under D-6/D-7. The arrival notification (screen 9's entry point) is posted from the foreground service, not from this sheet. |

### 13.10 Screen 9 — Completion confirmation dialog

| Aspect | Detail |
| --- | --- |
| **Purpose** | The single gate that turns a visit into a completion. Nothing is counted without an explicit **YES**. |
| **Layout** | `dialog_completion_confirm.xml` — a `DialogFragment` (not a bottom sheet, so it survives rotation and cannot be swiped away). |
| **Inputs** | The store awaiting confirmation, plus the arrival check that produced it. |
| **Outputs** | On **YES**: one completion history row and the store flipped to `COMPLETED`, in one transaction. On **NO**: nothing at all changes. |
| **Displayed** | The question **"Store completed?"** · route number · store code · store name · area · the distance at which arrival was detected. |
| **Buttons** | **NO** · **YES**. |
| **Empty state** | Not applicable. |
| **Loading state** | On **YES**: both buttons disable and a progress indicator shows while the transaction runs — this is the first of the three duplicate-completion guards. |
| **Error state** | *Already counted* → the dialog closes with a message: **"Already counted"**, and no counter changes. *Transaction failed* → "Could not save the completion. Nothing was changed. Try again." *Store deleted meanwhile* → "That store no longer exists." |
| **Notes** | The dialog only ever appears when the arrival requirement passed. If the user selects **NO**, the store stays pending, the marker stays orange, no history row is created and no counter moves. This screen is reachable from the arrival notification as well as from the app. |

### 13.11 Screen 10 — History

| Aspect | Detail |
| --- | --- |
| **Purpose** | Show completions grouped by day, newest day first. |
| **Layout** | `fragment_history.xml` — a `RecyclerView` of date-group cards using `item_history_date_group.xml`. |
| **Inputs** | Tap a date group. |
| **Outputs** | Opens Daily History for that date. |
| **Displayed** | Per group: the **date** and the **number completed that day** (e.g. "25 Sep 2026 · 3 stores"). |
| **Empty state** | No completions yet: an illustration-free centred message — "No completions yet. Completed stores will appear here." (No action button — use the bottom navigation.) |
| **Loading state** | Three grey placeholder group cards while the first query returns. |
| **Error state** | "Could not load your history." with **Retry**. |
| **Notes** | Dates sort **newest first**. The count shown per day comes from the actual history rows, never from a stored counter. |

### 13.12 Screen 11 — Daily History

| Aspect | Detail |
| --- | --- |
| **Purpose** | Show exactly which stores were completed on one day, in the real order they were completed. |
| **Layout** | `fragment_daily_history.xml` — a `RecyclerView` using `item_history_row.xml`, with a summary header. |
| **Inputs** | Tap a row. |
| **Outputs** | Opens Completion Detail (11b). |
| **Displayed** | Header: the date and total for that day. Each row: **daily sequence** ("#1", "#2"…) · route number · store code · store name · area · completion time. |
| **Empty state** | Only reachable from a date that has rows, so the realistic empty case is "this day's records were removed" — show "Nothing recorded for this day." and a back action. |
| **Loading state** | Placeholder rows while the query runs. |
| **Error state** | "Could not load this day." with **Retry**. |
| **Notes** | Rows sort by **`dailySequence` ascending** — earliest completion first, which is the real chronological order, not route-number order. |

### 13.13 Screen 11b — Completion Detail

| Aspect | Detail |
| --- | --- |
| **Purpose** | The full record of one completion. |
| **Layout** | `fragment_completion_detail.xml` — a detail list plus a map thumbnail and an action button. |
| **Inputs** | A history row id (or store id). |
| **Outputs** | Jumps to the Map tab, centred on that store, with its marker shown as green. |
| **Displayed** | Store details (route number, code, name, area) · **completion date** · **completion time** · **daily sequence** · **coordinates** · a small map preview showing the green marker. |
| **Buttons** | **View on Map** (primary) · a back action. |
| **Empty state** | Not applicable. If the store row is missing (data inconsistency), show "This store record is missing." with a **Run consistency check** hint (Phase 6.5). |
| **Loading state** | Brief placeholder while the joined query returns. |
| **Error state** | "Could not load this completion." with **Retry**. |
| **Notes** | The marker shown here **must be green**. If the store's status is not `COMPLETED`, that is a consistency problem and Phase 6.5's checker is what surfaces it — this screen does not silently "fix" it. |

### 13.14 Screen 12 — Settings

| Aspect | Detail |
| --- | --- |
| **Purpose** | Configure Home, the arrival radius, and see the app's data facts. |
| **Layout** | `fragment_settings.xml` — a grouped `Preference`-style list built from Material cards (not the deprecated `PreferenceFragment`), so it matches the rest of the UI. |
| **Inputs** | **Arrival radius** (numeric, metres) · **Home location** (opens screen 6) · optionally the tile-source URL and routing-service status (read-only). |
| **Outputs** | Persisted settings in `AppSettingEntity`; navigation to Home Setup. |
| **Displayed** | Current Home coordinates (or "Not set") · current arrival radius · a short note that exact coordinate equality is never required · app version · store count · completion count. |
| **Buttons** | **Set / change home location** · **Save** for the radius. From Version 9: **Export data** / **Import data**. |
| **Empty state** | Home not set: the row reads "Home not set" with a **Set home** action. |
| **Loading state** | A brief placeholder while the single settings row loads. |
| **Error state** | Invalid radius (not a number, ≤ 0, or absurdly large) → inline error: "Enter a distance in metres between 10 and 1000." Save failure → "Could not save settings." |
| **Notes** | The default arrival radius is **50 m** and it stays configurable. Changing it never rewrites history. |

### 13.15 Marker assets — vector drawables, not bitmaps

All four markers are planned as **vector drawables** (plain XML, reviewable, no binary assets, crisp at any
density):

| Drawable | Shape (the accessibility differentiator) | Proposed colour |
| --- | --- | --- |
| `ic_marker_pending.xml` | Teardrop pin containing a **filled circle** | Orange `#F57C00` |
| `ic_marker_completed.xml` | Teardrop pin containing a **check mark** | Green `#2E7D32` |
| `ic_marker_home.xml` | **House silhouette** (not a teardrop at all) | Deep purple `#5E35B1` |
| `ic_marker_current.xml` | **Blue dot with a ring** (supplied to osmdroid's location overlay) | Blue `#1E88E5` |

Proposed route polyline colour: `#1565C0`. Proposed accuracy circle: blue at low alpha.

> **Colour accessibility:** orange and green are the classic red/green-confusion pair. The shape difference above
> is what carries the meaning, and each marker also gets a `contentDescription` so TalkBack announces
> "Pending store", "Completed store" or "Home". Colour is never the only signal.

### 13.16 Shared UI conventions

**Layout file naming**

| Prefix | Used for |
| --- | --- |
| `fragment_*` | Tab fragments and pushed fragments |
| `sheet_*` | `BottomSheetDialogFragment` layouts |
| `dialog_*` | `DialogFragment` layouts |
| `item_*` | RecyclerView row layouts |
| `view_*` | Reusable included view groups |

**View ID prefixes** — so a Java reference is never ambiguous:

| Prefix | Widget |
| --- | --- |
| `tv_` | `TextView` |
| `et_` | `TextInputEditText` |
| `til_` | `TextInputLayout` |
| `btn_` | `MaterialButton` |
| `card_` | `MaterialCardView` |
| `rv_` | `RecyclerView` |
| `map_` | `MapView` |
| `fab_` | `FloatingActionButton` |
| `pb_` | `ProgressBar` / `CircularProgressIndicator` |
| `chip_` | `Chip` |
| `group_` | A `ViewGroup` wrapper |

**Strings** — every user-visible string goes into `res/values/strings.xml` with a descriptive key
(`dashboard_card_today_label`, `completion_dialog_title`, `error_routing_unavailable`, …). No hardcoded text in
any layout, so Phase 10.3's review and any future locale work stay trivial.

**Theming** — `Theme.Material3.DayNight.NoActionBar` (already in the project). Every colour is defined once in
`values/colors.xml` with a `values-night/colors.xml` counterpart for dark mode. Stat cards and sheets use
Material surfaces, so dark mode is inherited rather than hand-painted.

**Accessibility**

- Minimum 48 dp touch targets for every button, FAB and list row.
- Icon-only controls (the two map FABs) carry `contentDescription`.
- Stat cards expose a single TalkBack sentence each, e.g. "Completed today: 3 stores".
- Store names longer than one line use `ellipsize="end"` and `maxLines="1"` in lists, but **wrap fully** in
  Store Details.
- Colour is never the sole carrier of meaning (see §13.15).

**Universal states** — every screen that reads Room implements all four: loading, content, empty, error. The
`UiState` type from Phase 0.2 §13.2 (`LOADING | CONTENT | EMPTY | ERROR`) is what each ViewModel emits, so the
pattern is identical everywhere.

---

## 14. DATA FLOW FOR THIS PHASE

Phase 0.3 defines no runtime flow of its own. What it fixes is the **screen-to-data mapping**, so no later phase
has to guess:

| Screen | Reads from | Writes through |
| --- | --- | --- |
| Dashboard | `StoreDao.observeAll()` + `CompletionDao.observeAll()` → `TrackerCalculator` | nothing (read-only) |
| Map | `StoreDao.observeAll()`, `AppSettingDao.observe()`, `LocationProvider` | nothing (read-only) |
| Add / Edit Store | — | `StoreRepository.insert()` / `update()` |
| Store Details | `StoreDao.findById()` (+ history for completed) | `StoreRepository.delete()` (pending only) |
| Home Setup | `AppSettingDao.observe()` | `SettingsRepository.setHome()` |
| Route Preview | selected store + `LocationProvider` | `RoutingRepository.directions()` (read-only) |
| Navigation hand-off | `AppSettingEntity.lastSelectedStoreId` + `LocationProvider` | `NavigationLauncher` (Intent), `ActiveNavigationSession` |
| Completion dialog | the store under confirmation | `CompletionRepository.completeStore()` (the §13.7 transaction) |
| History | `CompletionDao.observeAllOrderedByDate()` | nothing |
| Daily History | `CompletionDao.observeByDateKey()` | nothing |
| Completion Detail | `CompletionDao` joined with `StoreDao` | nothing |
| Settings | `AppSettingDao.observe()` | `SettingsRepository` |

---

## 15. MANUAL TEST PROCEDURE

Phase 0.3 produces no code, so there is nothing to run. The baseline check from Phase 0.1 still stands:

1. Android Studio → **File → Open** → `C:/Users/Admin/OneDrive/Documents/custom system/AlphaRoute`.
2. Let Gradle sync finish.
3. **Build → Clean Project**, then **Build → Rebuild Project** → confirm `BUILD SUCCESSFUL`.
4. Run on an emulator or device (API 24+) → confirm "Hello World!" with no crash.
5. Report the result, or the exact error text.

**Design review instead** — please confirm:

6. Do the 12 screens (plus 11b) cover your workflow with nothing missing?
7. Do you approve the **screen 8 adaptation** (no in-app turn-by-turn; a hand-off/monitoring panel instead)?
8. Do you approve the **splash adaptation** (SplashScreen API, no extra Activity)?
9. Are the proposed marker colours in §13.15 acceptable?
10. Do you want the Dashboard's three buttons kept, given the bottom navigation does the same job (A17)?

---

## 16. EXPECTED RESULT

- A complete, approved screen plan: 12 screens plus one sub-screen, each with purpose, inputs, outputs, buttons,
  and empty / loading / error states.
- A concrete resource plan: layout file names, row layouts, vector marker drawables, colour proposals.
- Shared UI conventions for naming, strings, theming and accessibility.
- Still zero production code.

---

## 17. COMMON ERRORS AND FIXES

| Pitfall | Why it happens | How this plan prevents it |
| --- | --- | --- |
| *Start Navigation* does nothing on Android 11+ | Missing `<queries>` for Google Maps | Declared in §10 |
| Duplicate store saved by double-tapping **Save** | The sheet stays interactive during the write | Screen 4's loading state disables both buttons and blocks swipe-dismiss |
| Duplicate completion from double-tapping **YES** | The dialog stays interactive | Screen 9's loading state, plus the AD-5 unique index |
| A pending store offers a completion action | The action was placed on Store Details | §13.6 explicitly states no completion action exists outside the arrival flow |
| Percentage shows `NaN` with zero stores | Dividing by zero | Dashboard empty state pins `0.0%`, and `TrackerCalculator` guards it |
| History sorted by route number instead of time | Sorting the wrong column | §13.12 pins the sort to `dailySequence` ascending |
| Colours alone distinguish markers | Colour chosen as the only signal | §13.15 uses four distinct shapes plus `contentDescription` |
| Splash hangs the app | Keeping the splash on screen while work runs | Screen 1 keeps the window brief and does no blocking work |
| Markers disappear when tiles fail | Markers tied to tile loading | Screen 3's error state draws pins from Room regardless of tile state |

---

## 18. ACCEPTANCE CHECKLIST

- [ ] The screen inventory in §13.1 is complete for your workflow.
- [ ] Every screen's **purpose, inputs, outputs and buttons** are correct.
- [ ] Every screen's **empty, loading and error** states are acceptable.
- [ ] The **screen 8 adaptation** (no in-app turn-by-turn) is approved.
- [ ] The **splash adaptation** (SplashScreen API, no extra Activity) is approved.
- [ ] The **11b** Completion Detail screen is approved as a separate screen.
- [ ] The marker assets and proposed colours in §13.15 are approved.
- [ ] The UI conventions in §13.16 are accepted (naming, strings, theming, accessibility).
- [x] **A17 RESOLVED: bottom navigation is the primary navigation; the Dashboard's three buttons are dropped.**
- [ ] The baseline build check (§15) passes.

---

## 19. UPDATED PROJECT STATE

**COMPLETED PHASES**
- ✅ Phase 0.1 — Requirements Review (Rev 5, FINAL) — approved 2026-09-25
- ✅ Phase 0.2 — Architecture and Data Flow — approved 2026-09-25 (AD-7: desugaring enabled)
- ✅ Phase 0.3 — UI and Screen Plan — approved 2026-09-25 (A17: bottom navigation only)

**CURRENT PHASE**
- None open. **Version 0 (Planning) is complete.** **Phase 1.1 — Create the Android Java project** is next and
  awaits your go-ahead.

**CURRENT DATABASE VERSION**
- None. Room has not been added. `AppDatabase` will start at **version 1** in Phase 2.1.

**CURRENT FILES**

```
settings.gradle · build.gradle · gradle.properties · local.properties
gradle/libs.versions.toml · gradle/wrapper/gradle-wrapper.properties
app/build.gradle
app/src/main/AndroidManifest.xml
app/src/main/java/com/cph/alpharoute/MainActivity.java
app/src/main/res/layout/activity_main.xml
app/src/main/res/values/{colors,strings,themes}.xml
app/src/main/res/values-night/themes.xml
app/src/test/java/com/cph/alpharoute/ExampleUnitTest.java
app/src/androidTest/java/com/cph/alpharoute/ExampleInstrumentedTest.java
docs/PHASE_0.1_REQUIREMENTS_REVIEW.md      (Rev 5, approved)
docs/MAP_NAVIGATION_OPTIONS.md             (decision record — D1)
docs/PHASE_0.2_ARCHITECTURE.md             (approved)
docs/PHASE_0.3_SCREEN_PLAN.md              <- created this phase
```

**WORKING FEATURES** (tested)
- None. Still the Android Studio scaffold.

**KNOWN ISSUES**
- No blockers. Two adaptations (screens 1 and 8) and one minor redundancy (A17) need your confirmation.
- Carried risks: OSM data quality in remote PH areas · OSM tile policy compliance · openrouteservice quota ·
  no offline map download · osmdroid cadence (isolated by AD-8) · date boundary (mitigated by AD-11 + AD-7).
- The project is **not under version control**. Recommend `git init` before Phase 1.1 — I will not do it without
  your go-ahead.

**PENDING PHASES** (not implemented)
- **1.1 Create Android Java project** — next
- 1.2 Theme and base navigation · 1.3 Dashboard static UI
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

Phase 0.3 is complete and **approved**. **No production code has been written.**

**A17 is resolved: bottom navigation is the primary navigation.** The Dashboard's three buttons
(Open Map / View History / Settings) are **dropped**, and the empty states that referenced them now simply point
to the relevant tab. This is a deliberate deviation from your original Phase 1.3 text — recorded here so it is
never a surprise, and reversible in one line if you change your mind.

**Two adaptations are carried forward** as consequences of decisions you already approved, not as new choices:

| Adaptation | Follows from | Effect |
| --- | --- | --- |
| Screen 8 is a **navigation hand-off / monitoring panel**, not an in-app turn-by-turn screen | D-6 / D-7 (Google Maps app does the driving) | We never render Google's guidance inside our app |
| Screen 1 uses the **SplashScreen API**, no extra Activity | AD-1 (single Activity) | A themed launch window with zero extra classes |

If either of those is not what you want, say so and I will revise the plan — but both follow directly from the
navigation decision you approved in Phase 0.1.

**Version 0 (Planning) is now finished.** Three planning documents exist, all approved:

```
docs/PHASE_0.1_REQUIREMENTS_REVIEW.md   — requirements, frozen D1 stack, risks
docs/PHASE_0.2_ARCHITECTURE.md          — five layers, packages, 12 ADRs, completion contract
docs/PHASE_0.3_SCREEN_PLAN.md           — 13 screens, resources, UI conventions
docs/MAP_NAVIGATION_OPTIONS.md          — the D1/D2 decision record
```

**Still outstanding before code:** run the baseline build check (Clean → Rebuild → Run, confirm "Hello World!"
with no crash) and report the result. Nothing in Phases 0.1–0.3 changed a single source file, so if it fails now
it is environmental — a JDK or SDK setting — and I will diagnose that first.

**Next phase on your instruction: Phase 1.1 — Create the Android Java project.** This is the first phase that
writes code. It will:

- finalise `app/build.gradle` — add core library desugaring per **AD-7**, and the `buildConfig` flag
- extend `gradle/libs.versions.toml` with the dependencies Phase 1.2–2.1 need
- replace `MainActivity` with the single-Activity shell and its layout
- add `AlphaRouteApp` (Application class) and the package skeleton
- then **stop** for your build test

**Phase complete. Do not proceed to the next phase until all acceptance criteria pass.**

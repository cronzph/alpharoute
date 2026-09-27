# AlphaRoute — Phase 0.2 Architecture and Data Flow

**Project title:** Alfamart Route Tracker
**Version:** 0 (Planning) — **Phase:** 0.2 Architecture and Data Flow
**Repository root:** `C:/Users/Admin/OneDrive/Documents/custom system/AlphaRoute`
**Date:** 2026-09-25
**Prerequisite:** Phase 0.1 Revision 5 (FINAL, APPROVED) — stack frozen as **Option D1**
**Status:** ✅ **PHASE 0.2 COMPLETE AND APPROVED.** AD-7 decided: **core library desugaring is enabled** (minSdk stays 24).
**No production code is generated in this phase.**

---

## 1. CURRENT VERSION AND PHASE

| Item | Value |
| --- | --- |
| Version | 0 — Planning |
| Phase | 0.2 — Architecture and Data Flow |
| Production code written | **None** (forbidden in this phase) |
| Files created | `docs/PHASE_0.2_ARCHITECTURE.md` (this document) |
| Files modified | **None** |
| Source files touched | **None** |
| Database version | None yet — Room arrives in Phase 2.1 and will start at **version 1** |

### Adaptation note
The original Phase 0.2 brief asked for "Google Map integration" and "Navigation SDK integration". Under the
approved **Option D1** these become:

| Brief wording | Actual under D1 |
| --- | --- |
| Google Map integration | **osmdroid + OpenStreetMap** map integration |
| Navigation SDK integration | **Google Maps app hand-off** (free Intent) + a **foreground location service** that watches for arrival |

---

## 2. OBJECTIVE

Define how the application is structured and how data moves through it, **before** any production code is
written:

- Activities and Fragments
- ViewModel / controller strategy
- Repository layer
- Room database
- Map integration (osmdroid)
- Navigation integration (Google Maps hand-off + arrival monitoring)
- Location provider
- Tracker calculations
- Route-suggestion service
- History flow
- Package structure, screen navigation flow, and the responsibility of every component

No full production code.

---

## 3. UNDERSTANDING CHECKLIST

- [x] Java only, XML layouts, AndroidX, Material Components. No Kotlin, no Compose.
- [x] Single-user, single-device, solo app (D-5). No accounts, no sync, no multi-user concerns.
- [x] Local-first: Room is the single source of truth for everything the tracker shows.
- [x] The Store table and Completion History table are **authoritative**; no manually maintained counters.
- [x] Completion = valid store + inside arrival radius + explicit **YES**. Never automatic.
- [x] One completion event per store, enforced at the **database level**, not only in UI code.
- [x] Home is not a store, never affects counts, and is always the final destination.
- [x] Navigation is handed to the Google Maps app; our app never drives turn-by-turn itself.
- [x] Everything the tracker does must keep working with **no network at all**.

---

## 4. FEATURES INCLUDED (in this phase)

Architecture design only: layering, package structure, component responsibilities, data flow, screen navigation
flow, threading model, and the architecture decision records (ADRs) that the later phases must follow.

---

## 5. FEATURES NOT INCLUDED YET

Everything executable. No Room, no map, no location, no pins, no routing, no navigation, no tracker, no
history/settings screens, no key.

---

## 6. ASSUMPTIONS

Inherited from Phase 0.1 (A1–A12) and added in this phase:

| # | Assumption | Impact if wrong |
| --- | --- | --- |
| A13 | The dataset is small — realistically **tens to a few hundred stores**, one route at a time. | If it grows to thousands, some "load all and compute in Java" paths would need SQL aggregates instead. Documented as AD-4. |
| A14 | Only **one navigation session at a time**. | Multiple simultaneous targets would need a session table. |
| A15 | `AppSettingEntity.lastSelectedStoreId` doubles as "the store currently being navigated to". | If a separate flag is wanted, that is a schema addition — I will not add a column silently. |
| A16 | The user returns to our app after driving (or taps the arrival notification). | If we ever needed live trip telemetry from Google Maps, that requires the paid Navigation Connect API — explicitly out of scope. |

---

## 7. FILES TO CREATE (this phase)

| Path | Type | Purpose |
| --- | --- | --- |
| `docs/PHASE_0.2_ARCHITECTURE.md` | **New** | This architecture and data-flow design. |

---

## 8. FILES TO MODIFY (this phase)

**None.** No file is created or modified in the project source tree.

---

## 9. DEPENDENCIES

No dependency is added in Phase 0.2. This is the **planned** dependency set, to be pinned in
`gradle/libs.versions.toml` in the phase that introduces each one.

| Phase | Dependency | Why |
| --- | --- | --- |
| 2.1 | `androidx.room:room-runtime` | Local database |
| 2.1 | `androidx.room:room-compiler` (as `annotationProcessor`) | Room code generation |
| 2.1 | `androidx.lifecycle:lifecycle-viewmodel` | ViewModels |
| 2.1 | `androidx.lifecycle:lifecycle-livedata` | Observable data for Java (no coroutines) |
| 2.1 | `androidx.lifecycle:lifecycle-runtime` | Lifecycle awareness |
| 3.1 | `org.osmdroid:osmdroid-android:6.1.20` | Map display (verified version) |
| 3.2 | `com.google.android.gms:play-services-location` | Fused location provider |
| 6.2 | `androidx.recyclerview:recyclerview` | History lists |
| 1.1 | `com.android.tools:desugar_jdk_libs` (core library desugaring) | `java.time` on minSdk 24 — see **AD-7** |
| 10.2 | `androidx.room:room-testing`, `androidx.arch.core:core-testing` | Database and LiveData tests |

**Already present:** AGP 9.2.1 · Gradle 9.4.1 · appcompat 1.7.1 · material 1.14.0 · constraintlayout 2.2.1 ·
activity-ktx 1.13.0 · junit 4.13.2 · ext-junit 1.3.0 · espresso-core 3.7.0.

---

## 10. PERMISSIONS AND MANIFEST CHANGES

Nothing is added in Phase 0.2. This is the architecture's permission plan.

| Permission | Phase | Component that needs it |
| --- | --- | --- |
| `INTERNET` | 3.1 | osmdroid tile downloads; openrouteservice calls |
| `ACCESS_NETWORK_STATE` | 3.1 | Offline detection so we show a clear message instead of faking a route |
| `ACCESS_FINE_LOCATION` | 3.2 | `LocationProvider`; `NavigationMonitorService` |
| `ACCESS_COARSE_LOCATION` | 3.2 | Fallback when precise is denied |
| `FOREGROUND_SERVICE` | 4.3 | `NavigationMonitorService` |
| `FOREGROUND_SERVICE_LOCATION` | 4.3 | Required on API 34+ for a location foreground service |
| `POST_NOTIFICATIONS` | 4.3 | The arrival notification (runtime permission on API 33+) |
| `ACCESS_BACKGROUND_LOCATION` | **Not planned** | Not needed — our service is a *foreground* service |

Manifest entries added in their phases:

- `<service android:name=".navigation.NavigationMonitorService" android:foregroundServiceType="location" />`
- `android.permission.QUERY_ALL_PACKAGES` is **not** needed; instead declare a `<queries>` element for
  `com.google.android.apps.maps` and `com.waze` so `resolveActivity()` can see them on Android 11+.
  **This is easy to miss and would silently break the hand-off.**

---

## 11. SETUP INSTRUCTIONS

Nothing to set up in Phase 0.2 — it is a design phase. The only setup actions already outstanding are from
Phase 0.1:

1. Run the baseline build check (Phase 0.1 §15.1) and report the result.
2. Optionally create the free openrouteservice account (email only, no card) — not needed until Phase 4.2.

---

## 12. COMPLETE CODE

**None.** Phase 0.2 forbids production code.

---

## 13. ARCHITECTURE EXPLANATION

### 13.1 High-level architecture — five layers

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  UI LAYER            MainActivity · Fragments · BottomSheets · DialogFragments│
│                      XML layouts · Material Components · no business logic    │
└───────────────────────────────┬──────────────────────────────────────────────┘
                                │ observes LiveData / calls ViewModel methods
┌───────────────────────────────▼──────────────────────────────────────────────┐
│  PRESENTATION LAYER  DashboardViewModel · MapViewModel · StoreViewModel       │
│                      RouteViewModel · HistoryViewModel · SettingsViewModel    │
│                      Converts domain data into screen state; survives rotation│
└───────────────────────────────┬──────────────────────────────────────────────┘
                                │ repository calls (never Room or HTTP directly)
┌───────────────────────────────▼──────────────────────────────────────────────┐
│  DATA LAYER          StoreRepository · CompletionRepository                   │
│                      SettingsRepository · RoutingRepository                  │
│                      Single source of truth · owns threading · owns transactions│
└───────────┬───────────────────────────────────┬──────────────────────────────┘
            │                                   │
┌───────────▼───────────────┐     ┌─────────────▼──────────────────────────────┐
│  LOCAL SOURCE             │     │  REMOTE SOURCE (optional, never required)  │
│  Room: AppDatabase v1     │     │  openrouteservice Directions + Optimization│
│  StoreEntity              │     │  HTTPS + JSON                              │
│  CompletionHistoryEntity  │     └────────────────────────────────────────────┘
│  AppSettingEntity         │
└───────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────────┐
│  PLATFORM LAYER (sideways services, not part of the data flow above)          │
│  LocationProvider      – fused location → LiveData<LocationFix>               │
│  MapMarkerController   – osmdroid wrapper: entities → orange/green/Home pins   │
│  NavigationLauncher    – builds the Google Maps / Waze Intent                 │
│  NavigationMonitorService – foreground service: watches the arrival radius     │
└──────────────────────────────────────────────────────────────────────────────┘
```

**The one rule that keeps this clean:** a Fragment never talks to Room and never makes an HTTP call. It talks to
its ViewModel; the ViewModel talks to a Repository; the Repository talks to Room and the network.

### 13.2 Package structure

```
com.cph.alpharoute
├── AlphaRouteApp.java                  Application: builds AppDatabase + repositories,
│                                       configures osmdroid once
│
├── ui/
│   ├── MainActivity.java               THE single activity: bottom nav + fragment container
│   ├── common/
│   │   ├── UiState.java                LOADING | CONTENT | EMPTY | ERROR  (+ message)
│   │   ├── BaseFragment.java           shared LiveData observation + error/toast helpers
│   │   └── ConfirmDialogFragment.java  reusable NO / YES dialog (used by completion + delete)
│   ├── dashboard/
│   │   ├── DashboardFragment.java
│   │   └── DashboardViewModel.java
│   ├── map/
│   │   ├── MapFragment.java
│   │   ├── MapViewModel.java
│   │   ├── MapMarkerController.java    osmdroid wrapper — the ONLY class that touches Marker/Polyline
│   │   ├── MapIcons.java               orange / green / home / current-location icon selection
│   │   └── MapTileConfig.java          User-Agent, tile cache, attribution (OSM policy compliance)
│   ├── store/
│   │   ├── StoreDetailBottomSheet.java
│   │   ├── AddEditStoreBottomSheet.java
│   │   ├── StoreViewModel.java
│   │   └── StoreValidator.java         pure validation → returns field errors, no Android types
│   ├── route/
│   │   ├── RoutePreviewBottomSheet.java
│   │   ├── NextStoreSuggestionSheet.java
│   │   └── RouteViewModel.java
│   ├── history/
│   │   ├── HistoryFragment.java
│   │   ├── DailyHistoryFragment.java
│   │   ├── CompletionDetailFragment.java
│   │   ├── HistoryViewModel.java
│   │   └── DailyHistoryAdapter.java
│   └── settings/
│       ├── SettingsFragment.java
│       ├── HomeSetupFragment.java
│       └── SettingsViewModel.java
│
├── data/
│   ├── db/
│   │   ├── AppDatabase.java            RoomDatabase v1, singleton
│   │   ├── StoreEntity.java
│   │   ├── CompletionHistoryEntity.java
│   │   ├── AppSettingEntity.java
│   │   ├── StoreDao.java
│   │   ├── CompletionDao.java          includes the @Transaction completion method
│   │   ├── AppSettingDao.java
│   │   ├── Converters.java             StoreStatus enum <-> String
│   │   └── CompletionOutcome.java      SUCCESS | ALREADY_COUNTED | STORE_NOT_FOUND
│   └── repository/
│       ├── StoreRepository.java
│       ├── CompletionRepository.java   the ONLY writer of completion history
│       ├── SettingsRepository.java
│       └── RoutingRepository.java      openrouteservice client (directions + optimization)
│
├── domain/                             pure Java, zero Android imports — fully unit-testable
│   ├── StoreStatus.java                PENDING | COMPLETED
│   ├── TrackerCalculator.java          the 7 tracker metrics
│   ├── TrackerSnapshot.java            value object
│   ├── DistanceCalculator.java         haversine
│   ├── ArrivalChecker.java             inside-radius + accuracy gate
│   ├── DateKeyHelper.java              YYYY-MM-DD keys, today / yesterday
│   ├── RouteSuggestionEngine.java      chooses the next store
│   ├── RouteSuggestionStrategy.java    interface
│   └── strategies/
│       ├── NearestStrategy.java        V7.1 — straight-line nearest
│       ├── HomeAwareStrategy.java      V7.3 — Home-aware heuristic
│       └── OptimizedStrategy.java      V8.3 — openrouteservice Optimization
│
├── location/
│   ├── LocationProvider.java           fused client -> LiveData<LocationFix>
│   ├── LocationFix.java                lat, lng, accuracy, timestamp
│   └── LocationPermissionHelper.java   rationale, denial, permanent-denial handling
│
├── navigation/
│   ├── NavigationLauncher.java         Google Maps / Waze Intent builder + resolveActivity guard
│   ├── NavigationMonitorService.java   foreground service: arrival detection + notification
│   ├── ActiveNavigationSession.java    which store is being navigated to, persisted
│   └── NotificationHelper.java         channel creation + arrival notification
│
└── util/
    ├── AppExecutors.java               diskIO (single thread) + mainThread
    ├── Formatters.java                 distance / duration / date display
    └── GeoConstants.java               EARTH_RADIUS_M etc.
```

### 13.3 Component responsibilities

| Component | Responsibility | Must NOT do |
| --- | --- | --- |
| `MainActivity` | Own the single fragment container + `BottomNavigationView`; route arrival-notification intents into the completion flow | Contain business logic |
| `MapFragment` | Host the osmdroid `MapView`; forward taps and long-presses to `MapViewModel` | Touch `Marker` objects directly — that is `MapMarkerController`'s job |
| `MapMarkerController` | Own all osmdroid overlays; render entities as orange/green/Home/current-location markers; draw and clear the route polyline; diff instead of full redraws | Query the database |
| `MapTileConfig` | Set osmdroid `Configuration` once: app-specific **User-Agent**, tile cache path and size, attribution | Be called from more than one place |
| `DashboardViewModel` | Combine the store list + history list into a `LiveData<TrackerSnapshot>` | Do date maths itself — delegates to `TrackerCalculator` |
| `StoreViewModel` | Add / edit / delete stores; surface validation errors as UI state | Write SQL |
| `RouteViewModel` | Ask `RouteSuggestionEngine` for the next store; ask `RoutingRepository` for a route; hold the preview state | Start navigation — the Fragment does that after a user tap |
| `HistoryViewModel` | Group history by date, then order within a date | Format dates for display — that is `Formatters` |
| `StoreRepository` | Store CRUD; expose `LiveData` lists for pending / completed / all | Know about HTTP |
| `CompletionRepository` | **The only writer of completion history.** Re-reads the store, runs the atomic transaction, returns `CompletionOutcome` | Trust a cached store object |
| `SettingsRepository` | Read/write the single `AppSettingEntity` row; expose Home and arrival radius | Store preferences outside Room |
| `RoutingRepository` | Call openrouteservice; map JSON to domain objects; handle network/quota/API errors | Be required for the app to function |
| `CompletionDao` | The `@Transaction` method: verify PENDING → compute date key → compute next `dailySequence` → insert history → update store → return outcome | Be bypassed by any other writer |
| `LocationProvider` | Wrap `FusedLocationProviderClient`; expose `LiveData<LocationFix>` with accuracy; start/stop updates | Request permissions itself — the Fragment does that |
| `ArrivalChecker` | Pure: is this fix inside the radius, and is accuracy good enough? | Know about services or UI |
| `NavigationLauncher` | Build the Google Maps Intent (`google.navigation:q=…`), guard with `resolveActivity()`, fall back to Waze | Start navigation without a user tap |
| `NavigationMonitorService` | Foreground service: keep location updates alive while the user is in Google Maps; post the arrival notification once per store | Complete a store. It only *notifies* |
| `ActiveNavigationSession` | Persist which store is being navigated to (survives restart) | Duplicate `AppSettingEntity` data |
| `TrackerCalculator` | Pure: Today, Yesterday, Overall, Remaining, Total, Percentage (1 dp), Latest Completed; guarantee `Completed + Remaining = Total`; guard divide-by-zero | Touch the database |
| `DateKeyHelper` | Pure: `YYYY-MM-DD` keys, today, yesterday — from an injectable `Clock` | Use the default clock directly (breaks testability) |
| `RouteSuggestionEngine` | Given location + pending stores + Home, choose the next store via a `RouteSuggestionStrategy` | Call the network directly |
| `AppExecutors` | The app's threading policy in one place | Be duplicated per class |

### 13.4 Threading model

Java, no coroutines. The policy lives in `AppExecutors`:

| Thread | Used for |
| --- | --- |
| `diskIO` — a **single-threaded** executor | All Room reads and writes, all transactions |
| `networkIO` — a small fixed pool | openrouteservice HTTP calls |
| `mainThread` — a `Handler(Looper.getMainLooper())` | Delivering results back to LiveData / UI |

**Why a *single-threaded* disk executor matters:** it serialises every write, which is a second layer of defence
for the duplicate-completion rule on top of the database transaction and the unique index (AD-5).

### 13.5 Screen navigation flow

```
                       ┌─────────────────────────┐
                       │   MainActivity           │
                       │   (the only Activity)    │
                       │                          │
                       │  BottomNavigationView    │
                       │  FragmentContainerView   │
                       └────────────┬─────────────┘
                                    │ manual FragmentManager transactions
        ┌───────────────┬───────────┼────────────┬────────────────┐
        ▼               ▼           ▼            ▼                │
  ┌───────────┐   ┌───────────┐ ┌───────────┐ ┌───────────┐      │
  │ DASHBOARD │   │    MAP    │ │  HISTORY  │ │ SETTINGS  │      │
  └─────┬─────┘   └─────┬─────┘ └─────┬─────┘ └─────┬─────┘      │
        │               │             │             │             │
   "Open Map"      tap a marker   tap a date    "Home location"   │
        │               │             │             │             │
        │               ▼             ▼             ▼             │
        │      ┌──────────────────┐ ┌────────────┐ ┌───────────┐  │
        │      │ StoreDetail      │ │ DailyHistory│ │ HomeSetup │  │
        │      │ BottomSheet      │ │ (pushed)   │ │ (pushed)  │  │
        │      └────────┬─────────┘ └──────┬─────┘ └───────────┘  │
        │               │                  │                      │
        │        Get Directions      tap a record                 │
        │               ▼                  ▼                      │
        │      ┌──────────────────┐ ┌──────────────────┐          │
        │      │ RoutePreview     │ │ CompletionDetail │          │
        │      │ BottomSheet      │ │ (green marker)   │          │
        │      └────────┬─────────┘ └──────────────────┘          │
        │               │                                          │
        │      [Start Navigation]  ──► EXTERNAL: Google Maps app ──┼──► (user drives)
        │                                          │               │
        │                             NavigationMonitorService     │
        │                             detects arrival               │
        │                                          │               │
        │                             arrival notification ─────────┘
        │                                          │
        │                                          ▼
        │                              ┌───────────────────────┐
        └─────────────────────────────►│ CompletionConfirm     │
                                       │ DialogFragment        │
                                       │   Store completed?    │
                                       │     [ NO ]  [ YES ]   │
                                       └───────────────────────┘
```

**Navigation mechanism:** manual `FragmentManager` transactions with `add`/`show`/`hide` for the four bottom-nav
tabs (state preserved per tab) and `add` + `addToBackStack` for pushed screens. Bottom sheets are
`BottomSheetDialogFragment`; the completion dialog is a `DialogFragment` so it survives rotation.

### 13.6 Architecture Decision Records

| ID | Decision | Why | Consequence |
| --- | --- | --- | --- |
| **AD-1** | **Single Activity + Fragments**, manual `FragmentManager` navigation. **No Navigation Component.** | AGP 9.2.1 is very new; adding the Safe Args Gradle plugin is a compatibility risk. Manual transactions keep everything explicit and visible in Java, which matters for a learning project. | A small amount of transaction boilerplate; a later refactor to Navigation Component is contained in `MainActivity`. |
| **AD-2** | **ViewModel + LiveData**, not `StateFlow`. | LiveData is fully usable from Java with no coroutines; Room returns `LiveData` natively. | Fine at this scale; `StateFlow` would require Kotlin. |
| **AD-3** | **Repository layer owns threading** via `AppExecutors`; no coroutines, no `AsyncTask`. | `AsyncTask` is deprecated; coroutines are Kotlin. A single-threaded disk executor also serialises writes. | Explicit, debuggable threading. |
| **AD-4** | **Room is the single source of truth.** UI observes `LiveData` from DAOs; the network never writes to the UI directly. | Guarantees the spec's rule that the tables are authoritative, not counters. | Slightly more `LiveData` plumbing. |
| **AD-5** | **Duplicate completion is enforced at the database level:** `UNIQUE` index on `CompletionHistoryEntity.storeId`. | Makes double-counting *impossible*, not merely unlikely — the insert fails. | A completion attempt on an already-completed store must be handled as `ALREADY_COUNTED`, not a crash. |
| **AD-6** | **Foreign key `CompletionHistory.storeId → Store.id` with `onDelete = RESTRICT`.** | The database itself refuses to delete a store that has completion history — protecting completed stores and history even if UI code has a bug. | Deleting a completed store must be blocked in the UI *and* will fail in the DB. Both layers agree. |
| **AD-7** | ✅ **APPROVED — Enable core library desugaring** so `java.time` (`LocalDate`, `Clock`) works on minSdk 24. | `java.time` needs API 26. An injectable `Clock` is what makes the midnight-boundary tests (risk R7) actually possible. Keeps minSdk 24 as approved. | One build flag plus the `desugar_jdk_libs` dependency. Applied in Phase 1.1. **minSdk stays 24; the approved build baseline is unchanged.** |
| **AD-8** | The map layer is isolated behind **`MapMarkerController` + `MapTileConfig`**. | osmdroid is the one component most likely to be swapped (risk R6). Keeping it behind two classes means the tracker, database and completion logic never change if we do. | One extra indirection. |
| **AD-9** | The navigation hand-off is isolated behind **`NavigationLauncher`**; arrival monitoring lives in **`NavigationMonitorService`**. | Keeps the free-Intent approach and the foreground service in one place, so adding Waze or an offline navigator later is a one-class change. | A foreground service to declare and manage. |
| **AD-10** | The active navigation target is persisted in **`AppSettingEntity.lastSelectedStoreId`**. | The approved AppSetting model already has this field; no schema addition is needed. | If a dedicated flag is ever wanted, that is a schema change requiring your authorisation. |
| **AD-11** | **`domain/` contains pure Java with zero Android imports.** | `TrackerCalculator`, `DistanceCalculator`, `ArrivalChecker`, `DateKeyHelper`, `StoreValidator` and the suggestion strategies become ordinary JUnit tests in Phase 10.2 — no emulator needed. | Discipline: no `Context` leaks into domain classes. |
| **AD-12** | Route suggestion sits behind a **`RouteSuggestionStrategy` interface** with three implementations across V7.1 / V7.3 / V8.3. | The spec evolves the suggestion logic three times; a strategy interface means each version is an addition, not a rewrite. | Slight over-engineering at V7.1, pays off at V7.3 and V8.3. |

### 13.6.1 AD-7 implementation detail — core library desugaring (approved)

Applied in **Phase 1.1** when `app/build.gradle` is finalised. Two additions, nothing else changes:

```groovy
android {
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_11
        targetCompatibility JavaVersion.VERSION_11
        coreLibraryDesugaringEnabled true          // ADDED (AD-7)
    }
}
dependencies {
    coreLibraryDesugaring 'com.android.tools:desugar_jdk_libs:<version>'
}
```

`<version>` is pinned in Phase 1.1 after checking the current release on Maven Central. The documented
minimum for the `java.time` support below is **1.1.8**. **minSdk stays 24.**

**What is actually available (from Google's desugaring support table):**

| Class | Support |
| --- | --- |
| `java.time.Clock` | **Fully implemented** — this is what makes time injectable and testable |
| `LocalDate` | Partially implemented (a few uncommon methods missing) |
| `LocalDateTime`, `Instant`, `DayOfWeek`, `Month`, `MonthDay`, `OffsetDateTime` | Fully implemented |
| `Duration`, `LocalTime` | Partially implemented |

**Design consequence — a simplification worth knowing:** `LocalDate.toString()` already emits ISO-8601
`YYYY-MM-DD`, which is **exactly** the required `completionDateKey` format. So `DateKeyHelper` needs no
`DateTimeFormatter` at all, and the whole date-key path stays trivial:

| `DateKeyHelper` member | Contract |
| --- | --- |
| `keyOf(long epochMillis, ZoneId zone)` | The `YYYY-MM-DD` key for an instant, in the given zone |
| `today(Clock clock)` | Today's key, derived from the injected clock |
| `yesterday(Clock clock)` | `today(clock).minusDays(1)` |

Production passes `Clock.systemDefaultZone()`. Phase 10.2's date-boundary tests pass
`Clock.fixed(instant, zone)` and can therefore pin the clock to 23:59:59 and to 00:00:01 on the same night —
which is the only reliable way to test "complete near midnight, reopen the next day" (risk R7).

**Constraint adopted:** `DateKeyHelper` uses only the common, fully-supported `LocalDate` methods —
`now(Clock)`, `toString()`, `minusDays(int)`, `equals`, `isBefore`, `isAfter`. Exotic `LocalDate` methods that the
table marks as unsupported at some minSdk levels will be avoided.

### 13.7 The completion transaction (design contract)

The single most safety-critical operation in the app. Phase 5.3 implements it; here is the contract it must meet.

```
CompletionRepository.completeStore(storeId)
  │
  └─► CompletionDao  @Transaction
        1. SELECT the store again, inside the transaction        (never trust a cached object)
        2. If not found                -> STORE_NOT_FOUND, rollback
        3. If status == COMPLETED      -> ALREADY_COUNTED, rollback, change nothing
        4. If a history row exists     -> ALREADY_COUNTED, rollback, change nothing
        5. completionDateKey = DateKeyHelper.keyOf(now, localZone)
        6. dailySequence = MAX(dailySequence) + 1 WHERE completionDateKey = ?
                          (0 rows -> 1)
        7. INSERT CompletionHistoryEntity(storeId, now, completionDateKey, dailySequence)
        8. UPDATE StoreEntity SET status=COMPLETED, completedAt=now, updatedAt=now WHERE id=?
        9. commit
       10. return SUCCESS(completionDateKey, dailySequence)
```

Three independent guards make double counting impossible: the in-transaction re-read (step 1), the `UNIQUE` index
on `storeId` (AD-5), and the single-threaded disk executor (AD-3). A double tap cannot produce two history rows.

---

## 14. DATA FLOW FOR THIS PHASE

### 14.1 Dashboard / tracker

```
StoreDao.observeAll() ─────────┐
                               ├─► MediatorLiveData ─► TrackerCalculator.compute(stores, history)
CompletionDao.observeAll() ────┘                              │
                                                              ▼
                                            TrackerSnapshot (Today, Yesterday, Overall,
                                                             Remaining, Total, Percentage,
                                                             Latest Completed)
                                                              │
                                                     DashboardViewModel
                                                              │ LiveData
                                                              ▼
                                                     DashboardFragment (XML cards)
```

`TrackerCalculator` guarantees `Completed + Remaining = Total` and returns `0.0%` for an empty database instead of
dividing by zero.

### 14.2 Map and markers

```
AppSettingDao.observe()  ──► Home coordinates ──┐
StoreDao.observeAll()    ──► all stores ────────┼─► MapViewModel ─► MapMarkerController
LocationProvider         ──► LiveData<LocationFix> ┘                        │
                                                                            ▼
                                              osmdroid overlays:
                                                blue   = current location (accuracy circle)
                                                orange = PENDING   (circle pin)
                                                green  = COMPLETED (check pin)
                                                house  = Home      (never orange/green)
                                                            +
                                                Polyline = active route
```

Markers are differentiated by **icon and shape as well as colour**, per the accessibility requirement. Each
`Marker` carries the store id so a tap maps back to a store unambiguously.

### 14.3 Pin creation

```
User long-presses the map
   │
   ▼
MapFragment onMapLongClick(lat, lng)
   │
   ▼
AddEditStoreBottomSheet  (route number, store code, store name, area; coordinates shown read-only)
   │
   ▼
StoreValidator.validate(...)  ── pure ──► field errors or OK
   │
   ▼
StoreViewModel.save()
   │  duplicate check: find by code, find by route number
   ▼
StoreRepository.insert()  ──► AppExecutors.diskIO ──► StoreDao.insert()  (UNIQUE index is the final guard)
   │
   ▼
StoreDao.observeAll() emits  ──► MapViewModel  ──► new ORANGE marker appears
```

### 14.4 Route preview and the navigation hand-off

```
Tap an ORANGE marker
   │
   ▼
StoreDetailBottomSheet  (route no. / code / name / area / status + Get Directions · Edit · Delete)
   │
   ▼  [Get Directions]     — user-initiated, never automatic
RouteViewModel.requestRoute(currentFix, store)
   │
   ▼
RoutingRepository.directions(origin, destination)     ← openrouteservice, free tier
   │  success → geometry + distance + duration + steps
   │  failure → we show "Routing unavailable — retry". We never invent a route.
   ▼
RoutePreviewBottomSheet  (selected store · distance · duration · route line · [Start Navigation] [Cancel])
   │
   ▼  [Start Navigation]   — user-initiated, never automatic
NavigationLauncher.launch(store)
   │  Intent ACTION_VIEW, google.navigation:q=<lat>,<lng>&mode=d
   │  setPackage("com.google.android.apps.maps"); guarded by resolveActivity()
   │  not installed? → try Waze → otherwise show a clear message
   ▼
EXTERNAL: Google Maps app drives the user
   (real-time turn-by-turn · live traffic · road rules · voice · lane guidance)
   │
   │  meanwhile, in our app:
   ▼
ActiveNavigationSession.set(store.id)          → persisted in AppSettingEntity.lastSelectedStoreId
NavigationMonitorService.start(store.id)       → foreground, foregroundServiceType="location"
   │
   ▼
each LocationFix → ArrivalChecker.isInside(fix, store, arrivalRadius)
   │  NO  → keep watching
   │  YES → NotificationHelper.postArrival(store)     (once per store only)
   ▼
User taps the notification → MainActivity → CompletionConfirmDialogFragment
   │
   ├── NO  → store stays PENDING · marker stays orange · no history · no counter change
   └── YES → CompletionRepository.completeStore(storeId)  → the §13.7 transaction
                │
                ├─ SUCCESS        → green marker · tracker refreshes · next-store suggestion recalculated
                └─ ALREADY_COUNTED → show "Already counted" · counters unchanged
```

### 14.5 History

```
CompletionDao.observeAllOrderedByDate()   newest date first
   │
   ▼
HistoryFragment      : date groups → "2026-09-25 · 3 stores"
   │  tap a date
   ▼
DailyHistoryFragment : rows ordered by dailySequence ASC (earliest completion first)
   │                   shows sequence · route number · store code · store name · area · completion time
   │  tap a row
   ▼
CompletionDetailFragment : store details · completion date · time · daily sequence · coordinates
                           + [View on Map]  → Map tab, pans to the store, marker is GREEN
```

### 14.6 Offline behaviour (by design, not by accident)

| Capability | Offline |
| --- | --- |
| Store list, pins, orange/green/Home markers | ✅ works (Room) |
| Tracker, history, daily sequence | ✅ works (Room) |
| Manual pinning, edit, delete | ✅ works (Room) |
| Cached map tiles already viewed | ✅ works (osmdroid tile cache) |
| **New** map areas | ❌ blank — we say so, we do not fake it |
| Route preview | ❌ show "Routing unavailable — retry" |
| Turn-by-turn | ✅ the Google Maps app has its own offline behaviour |

### 14.7 Route suggestion (Version 7/8)

```
RouteSuggestionEngine.suggest(currentFix, pendingStores, home)
   │
   ├─ V7.1 NearestStrategy        : sort by haversine distance, pick the nearest
   ├─ V7.3 HomeAwareStrategy      : score = f(current→candidate, candidate→remaining, candidate→home)
   └─ V8.3 OptimizedStrategy      : openrouteservice Optimization (VROOM), Home forced last
   │
   ▼
SuggestionCard: route number · code · name · area · approximate distance
                [ Navigate ]  [ Choose Another Store ]
   │
   └─ "Approximate" is labelled as approximate; V7.1 is explicitly NOT route optimisation.
```

---

## 15. MANUAL TEST PROCEDURE

Phase 0.2 produces no code, so there is nothing new to run. The only executable check is still the baseline.

1. Open Android Studio → **File → Open** → `C:/Users/Admin/OneDrive/Documents/custom system/AlphaRoute`.
2. Let Gradle sync finish.
3. **Build → Clean Project**, then **Build → Rebuild Project** → confirm `BUILD SUCCESSFUL`.
4. Run on an emulator or device (API 24+); confirm "Hello World!" with no crash.
5. Report the result, or the exact error text.

**Design review instead of a runtime test** — please check these before approving:

6. Does the package structure in §13.2 look like something you can navigate?
7. Are you happy with **AD-1** (single Activity, manual navigation, no Navigation Component)?
8. Do you approve **AD-7** (enable core library desugaring for `java.time` on minSdk 24), or would you rather
   raise minSdk to 26?
9. Is the completion contract in §13.7 what you expect from "prevent one store being counted twice"?

---

## 16. EXPECTED RESULT

- A complete, approved blueprint: five layers, the package structure, every component's responsibility, the
  threading policy, the screen navigation flow, and the data flows for tracker / map / pinning / navigation /
  history / offline / suggestion.
- Twelve ADRs that every later phase must follow.
- The completion transaction contract that Phases 5.2 and 5.3 must satisfy.
- Still zero production code.

---

## 17. COMMON ERRORS AND FIXES

These are the pitfalls this architecture is specifically designed to avoid — recorded now so they are not
rediscovered later.

| Pitfall | Why it happens | How this architecture prevents it |
| --- | --- | --- |
| `resolveActivity()` returns null on Android 11+ | Package visibility: without a `<queries>` entry, the app cannot see Google Maps | A `<queries>` element for `com.google.android.apps.maps` and `com.waze` is declared in §10 |
| Duplicate history rows on a double tap | Two writes race | AD-5 unique index + AD-3 single-threaded executor + in-transaction re-read |
| A completed store gets deleted | UI bug | AD-6 `onDelete = RESTRICT` makes the database refuse |
| `java.time` crashes on API 24 | `LocalDate` needs API 26 | AD-7 core library desugaring |
| OSM blocks our tile requests | osmdroid's generic default User-Agent | `MapTileConfig` sets an app-specific User-Agent once |
| LiveData fires before the Fragment view exists | Observing in `onCreate` | Observe in `onViewCreated` / `onStart` |
| Markers go stale after a database change | Manual marker bookkeeping | AD-4: the map observes `LiveData` and `MapMarkerController` diffs |
| Foreground service crashes on Android 14+ | Missing `foregroundServiceType` or `FOREGROUND_SERVICE_LOCATION` | Both declared in §10 |
| Date-key off-by-one near midnight | Reading the clock in several places | AD-11 + `DateKeyHelper` with an injectable `Clock`, computed once inside the transaction |

---

## 18. ACCEPTANCE CHECKLIST

- [ ] The five-layer architecture in §13.1 is approved.
- [ ] The package structure in §13.2 is approved.
- [ ] Every component's responsibility in §13.3 is approved — in particular that Fragments never touch Room or
      HTTP directly.
- [ ] The threading model in §13.4 is approved.
- [ ] The screen navigation flow in §13.5 is approved.
- [ ] **AD-1** (single Activity, manual navigation) is approved.
- [ ] **AD-5 / AD-6** (database-level duplicate and delete protection) are approved.
- [x] **AD-7 DECIDED: core library desugaring is enabled.** minSdk stays 24. Applied in Phase 1.1. See §13.6.1.
- [ ] The completion transaction contract in §13.7 matches your expectations.
- [ ] The offline behaviour in §14.6 is accepted.
- [ ] The baseline build check (§15) passes.

---

## 19. UPDATED PROJECT STATE

**COMPLETED PHASES**
- ✅ Phase 0.1 — Requirements Review (Revision 5, FINAL) — approved 2026-09-25
- ✅ Phase 0.2 — Architecture and Data Flow — approved 2026-09-25 (AD-7 decided: core library desugaring)

**CURRENT PHASE**
- None open. **Phase 0.3 — UI and Screen Plan** is next and awaits your go-ahead.

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
docs/PHASE_0.2_ARCHITECTURE.md             <- created this phase
```

**WORKING FEATURES** (tested)
- None. Still the Android Studio scaffold.

**KNOWN ISSUES**
- No blocking issues. AD-7 is the only decision this phase raises.
- Carried risks: OSM data quality in remote PH areas · OSM tile policy compliance · openrouteservice free-tier
  quota (2,000 routes/day, far above solo use) · no offline map download · osmdroid release cadence (isolated by
  AD-8) · date-boundary correctness (mitigated by AD-11).
- The project is **not under version control**. Recommend `git init` before Phase 1.1 — I will not do it without
  your go-ahead.

**PENDING PHASES** (not implemented)
- **0.3 UI and screen plan** — next
- 1.1 Project creation · 1.2 Theme and base navigation · 1.3 Dashboard static UI
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

Phase 0.2 is complete and **approved**. **No production code has been written.**

**AD-7 is resolved: core library desugaring is enabled.** minSdk stays 24, so the approved build baseline from
Phase 0.1 is untouched. §13.6.1 now carries the exact Gradle additions and the verified support table.

**A useful side-effect of the decision:** `LocalDate.toString()` already produces `YYYY-MM-DD`, which is exactly
the `completionDateKey` format your specification requires. So `DateKeyHelper` needs no `DateTimeFormatter` at
all — and because `Clock` is injectable, Phase 10.2 can pin the clock to 23:59:59 and 00:00:01 on the same night
and actually *test* the midnight boundary instead of hoping.

**The architecture is now frozen:**

- Five layers, with `domain/` kept as pure Java so the tracker, distance, arrival, validation and suggestion
  logic is unit-testable without a device.
- **AD-5** and **AD-6** make your hardest rule structural: a unique index on `history.storeId` means a store
  *cannot* be counted twice, and `onDelete = RESTRICT` means the database *refuses* to delete a completed store
  or its history — even if UI code has a bug.
- The completion transaction contract in §13.7 is what Phases 5.2 and 5.3 must satisfy.

**Still outstanding from Phase 0.1:** run the baseline build check (Clean → Rebuild → Run, confirm "Hello World!"
with no crash) and report the result. Nothing in Phase 0.1 or 0.2 changed any source file, so if it fails now it
is environmental — a JDK or SDK setting — and I will diagnose that first.

**Next phase on your instruction: Phase 0.3 — UI and Screen Plan** — all 12 screens, each with its purpose,
inputs, outputs, buttons, and empty / loading / error states.

Per your rule 6, I will not start it automatically.

**Phase complete. Do not proceed to the next phase until all acceptance criteria pass.**

# AlphaRoute — Project Memory (condensed index)

> **READ `docs/HANDOFF.md` FIRST.** It is the source of truth: full state, frozen stack, all ADRs, the
> database schema, every accumulated trap, the remaining roadmap, and a copy-paste quick-start.
> This file is only a short index. Phase records live in `docs/PHASE_<n>_<NAME>.md` (20-section format).

## Project
- Product name (user spec): **Alfamart Route Tracker**. Root project name: `AlphaRoute`.
- Root: `C:/Users/Admin/OneDrive/Documents/custom system/AlphaRoute` · package `com.cph.alpharoute`
- Build baseline: AGP 9.2.1 · Gradle 9.4.1 · compileSdk 36 · minSdk 24 · targetSdk 36 · Java 11 · Material 3.
- Purpose: pin Alfamart stores on a map, hand navigation to the Google Maps app, GPS-validate arrival,
  track daily/overall progress, keep completion history, suggest the next store, end at Home.
- **NOT under version control** (strongest open recommendation). `app/schemas/` must never be gitignored.

## User working agreement (MANDATORY)
- **Java ONLY** (zero Kotlin), **XML layouts only**, no Compose, no ViewBinding — use `findViewById`.
- **One phase at a time.** Complete ONLY the requested phase, then STOP and wait for the test result.
- **Never invent data** — no store names/codes, route numbers, coordinates, counts, history, credentials.
- **Never auto-complete a store, never auto-start navigation.** Completion = in-radius **+** explicit YES.
- **A store is never counted twice.**
- State the exact file path and mark each file **New / Existing / Modified / Replaced**; give the
  **complete** file, not a diff. **No placeholders** (if unavoidable: why, where the real value comes
  from, which phase replaces it). Never put a private API key in a shared Java file.
- **No destructive Room migration** once real data exists. Do not remove a working feature without a
  technical reason, the reason explained, the affected files listed, and a safe replacement.
- **At most ONE clarifying question**, only when critical.
- Response format: the **20-section phase format** (HANDOFF §10). Each phase also writes its own
  `docs/PHASE_<n>_<NAME>.md` with the same 20 sections.
- Communication: plain language first, **always a clear recommendation**, English (Taglish is fine).
  The user **values verification** — build before claiming a phase complete.

## Frozen stack — Option D1, approved 2026-09-25. TOTAL COST $0.
- **In-app map:** osmdroid 6.1.20 + OSM tiles. App-specific User-Agent mandatory, OSM attribution visible,
  no offline prefetch (tile policy). **No offline map — risk R5.**
- **Turn-by-turn + live traffic:** the **Google Maps app** via a plain Intent (`google.navigation:q=lat,lng`),
  guarded by `resolveActivity()`. Needs a `<queries>` manifest element on Android 11+ (Phase 4.3).
  **Do NOT use Navigation Connect** — that is paid.
- **Route geometry:** openrouteservice free plan (account NOT created yet; needed from Phase 4.2).
- **Location:** `play-services-location`. **Local data:** Room 2.8.5.
- Region **Philippines**; app is **solo** (one user, one device, never distributed).
- **Firebase: deferred to Version 9** (D-9). The `SyncManager` seam is already wired.

## Architecture (AD-1…AD-12 — see HANDOFF §4)
- Single Activity + Fragments, manual `FragmentManager` nav. Tabs are **hidden, not destroyed** (A27).
- ViewModel + LiveData (no StateFlow). `AppExecutors.diskIO()` is **single-threaded** → serialises writes.
- **Room is the single source of truth.** A Fragment never touches Room or HTTP — ViewModel → Repository.
- **AD-5** UNIQUE index on `completion_history.storeId` → double-counting is physically impossible.
- **AD-6** FK `completion_history.storeId → stores.id` `onDelete = RESTRICT` → DB refuses to delete a
  completed store. **`PRAGMA foreign_keys = ON`** is issued from `AppDatabase`'s `Callback.onOpen` —
  SQLite has FK enforcement OFF by default; remove that line and AD-6 silently stops working.
- **AD-7** core library desugaring (`desugar_jdk_libs` 2.1.5) gives `java.time` on minSdk 24.
- **AD-8** osmdroid isolated behind `MapConfig` / `MapMarkerController` / `CurrentLocationOverlay`.
- **AD-11** `domain/` is PURE Java, zero Android imports → JVM-testable. `util/` helpers are pure too.
- **LOCAL-FIRST WRITE:** diskIO → normalise → duplicate pre-checks → DAO write → **COMMIT** →
  `notifySyncSafely()` (try/catch) → `postToMain(callback)`. **The network is never in the write path.**

## Database — version 1, 3 tables
`stores` (unique storeCode, unique routeNumber, indexed status) · `completion_history` (unique storeId,
indexed completionDateKey) · `app_settings` (single row, `SINGLETON_ID = 1`).
- Store status is stored as the **enum name**, not the ordinal.
- `completedAt` is a **nullable `Long`**, never `0`. `0.0` is a REAL coordinate — never conflate with null.
- `completionDateKey` = `YYYY-MM-DD`, device local timezone; `dailySequence` = real order that day.
- **Never add `fallbackToDestructiveMigration()`.** Bump `DATABASE_VERSION` only with a written `Migration`.
- `Home` is NOT a store row and never affects any count.
- `CompletionRepository` is **READ-ONLY until Phase 5.3** — its write path is the atomic transaction.

## Status
- ✅ 0.1 · 0.2 · 0.3 · 1.1 · 1.2 · 1.3 · 2.1 · 2.2 · 2.3 · 3.1 · 3.2 · 3.3 · 3.4 (all builds verified;
  **on-device tests for 3.1–3.4 never reported by the user**).
- 🔄 **Phase 3.5 — Store Details bottom sheet** (tap a marker → details).
- Next after that: 3.6 edit/delete · 3.7 Home pin · then Version 4 (routing hand-off).
- **Database version 1 · 83 JVM tests passing (before 3.5) · zero Kotlin · $0 stack.**

## Highest-value traps (full list in HANDOFF §8)
- **osmdroid 6.1.x:** `Configuration` is only a holder; `Configuration.getInstance()` returns
  **`IConfigurationProvider`** — every setter lives on that interface. `Configuration config =
  Configuration.getInstance();` does **not compile**. Most online tutorials are for older releases.
- **The OSM tile servers block generic User-Agents** — blank map, no error, no log.
- `mapView.onResume()` / `onPause()` / **`onDetach()`** are all mandatory; a missing `onDetach` leaks a
  map view per visit and accumulates because tabs are hidden not destroyed.
- Long-press `MapEventsOverlay` goes in at **index 0** (bottom of the stack) so markers added later get
  their taps first.
- Marker anchor = `ANCHOR_CENTER, ANCHOR_BOTTOM` so a teardrop's tip sits on the coordinate.
- **A `@ForeignKey` is only a promise** — see AD-6 above.
- **`values-night/themes.xml` must NOT exist** — a values-night duplicate wins in dark mode and silently
  reverts the app to Material purple at night. Dark mode = `values-night/colors.xml` only.
- Observe LiveData with **`getViewLifecycleOwner()`**, not `this`.
- Fragment values travel as **arguments, not fields** (FragmentManager recreates via the no-arg ctor).
- Fragments need a public **no-arg constructor** passing the layout id to `super(...)`.
- **Accuracy `0` means "unknown", not "perfect"**; `shouldShowRequestPermissionRationale` cannot tell
  "never asked" from "refused for good" (hence the stored flag, set BEFORE the dialog).
- **BOTH location levels must stay declared** (Android 12+ "Approximate" would otherwise mean no location).
- **Never `getLastLocation()`** as the current position.
- ⚠️ Grepping the merged manifest for `ACCESS_BACKGROUND_LOCATION` returns a hit — it is the **XML
  comment text**, not a permission element.
- ⚠️ Editing a javadoc can delete the line of live code below it — never include live code in `old_string`.

## Build and verify
```bash
cd "C:/Users/Admin/OneDrive/Documents/custom system/AlphaRoute"
./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain
```
- JDK 21 at `C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot\`; SDK at
  `C:\Users\Admin\AppData\Local\Android\Sdk`. Build ≈ 2 min.
- `app/build.gradle`: `buildFeatures { buildConfig true }`, core library desugaring on.
- Non-blocking warning *"Deprecated Gradle features … incompatible with Gradle 10"* comes from
  AGP 9.2.1 / Gradle 9.4.1, not from our files. Revisit at Phase 10.3.
- **Always run the build before reporting a phase complete.**

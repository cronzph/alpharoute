# AlphaRoute — Phase 0.1 Requirements Review (Revision 5 — FINAL, APPROVED)

**Project title:** Alfamart Route Tracker
**Version:** 0 (Planning) — **Phase:** 0.1 Requirements Review
**Repository root:** `C:/Users/Admin/OneDrive/Documents/custom system/AlphaRoute`
**Date:** 2026-09-25 · **Revision 5 (final)** — **Option D1 selected and approved by the user.**
**Status:** ✅ **PHASE 0.1 COMPLETE AND APPROVED.** Phase 0.2 may begin on request. No production code written.

> **Revision 5 change summary.** The user selected **Option D1** — the fully free stack: **osmdroid +
> OpenStreetMap** for the in-app map, **Google Maps app via a free Intent** for turn-by-turn navigation with live
> traffic and road rules, **openrouteservice free plan** for route geometry, **Room** for local data. No API key,
> no billing account, no card, no quota anywhere in the project. Decision log entries D-6 and D-7 are now
> **Active/Approved**. The Phase 0.1 stack is frozen.

---

## 0. DECISION LOG

| ID | Decision | By | Status | Consequence |
| --- | --- | --- | --- | --- |
| D-1 | Operating region = **Philippines**. | User | **Active** | Resolves CONFLICT-0. |
| D-2 | Free stack only, no billing account. | User | **SUPERSEDED by D-4** | Was incompatible with any Google map layer. Retained only as Plan B (§9.5). |
| D-3 | In-app turn-by-turn navigation is still required and will be **our own UI** on top of a routing service. | Derived | **Active** | Phase 4.3 is our code, not a vendor SDK. |
| D-4 | Use the **Google Maps SDK for Android free tier**. | User | **Active** | Google map layer reinstated. See the condition in §0.2 — it cannot be waived. |
| D-5 | The app is **solo**: one personal user, one device, never distributed. | User | **Active** | Makes the free tiers effectively unreachable by normal use, simplifies key handling, and removes any need for multi-user auth or sync. |
| D-6 | **Option D: hand turn-by-turn off to the free Google Maps app (or Waze) via a plain Android Intent.** No API key, no billing account, no card, no quota. | Research → **User approved** | **✅ Active** | Delivers real-time navigation **with live traffic, lane guidance, speed limits and road rules** at $0 — the only free way to get traffic. See `docs/MAP_NAVIGATION_OPTIONS.md`. |
| D-7 | Navigation will **no longer happen inside our app**. | Research → **User approved** | **✅ Active** | Replaces the original specification rule "Navigation must remain inside the Alfamart Route Tracker application". Our app keeps a foreground location service and detects arrival. |
| D-8 | **Variant D1 chosen** (not D2). | User | **✅ Active** | In-app map = **osmdroid + OpenStreetMap**. No Google Cloud account, no billing account, no card, and no API key anywhere in the project. |

### 0.1 Good news: your map display is genuinely free — and unlimited

Verified verbatim from Google's official *Maps SDK for Android — Usage and Billing* page (2026-09-25):

> **"All mobile usage of the Maps SDK for Android is unlimited."**

And from the official Google Maps Platform pricing list, the **Maps SDK** SKU (ID `6DE1-4D9C-5B67`) has a
**Free Usage Cap of `Unlimited`**. So displaying the map in your app costs **$0 with no usage ceiling** — for a
solo user this is not a "tier" you can realistically exhaust.

### 0.2 The one unavoidable condition — please read before approving

The same official page states, verbatim:

> **"To use the Maps SDK for Android, you must enable billing on each of your projects and include an API key or
> OAuth token with all API or SDK requests."**

And Google Cloud's billing documentation states:

> **"You must have a valid Cloud Billing account even if you are in your free trial period or if you only use
> Google Cloud free tier services."**

**Therefore:** you can pay **$0**, but you **cannot avoid attaching a billing account with a payment method
(card)** in order to obtain the API key. There is no "Google Maps key without a card" path — this is a Google
policy, not a technical limitation we can code around. Revision 2's "no billing at all" and this revision's
"Google Maps SDK" are mutually exclusive; one of them has to give.

**How we make $0 a guarantee rather than a hope** (§10.2): hard **daily quota caps** on every enabled API, so the
API simply stops responding instead of ever billing you; budget alerts; and an API key locked to your app's
package name and signing certificate. A solo app with those three controls in place cannot generate a charge.

---

## 1. CURRENT VERSION AND PHASE

| Item | Value |
| --- | --- |
| Version | 0 — Planning |
| Phase | 0.1 — Requirements Review (Revision 3) |
| Production code written | **None** (forbidden in this phase) |
| Files created | `docs/PHASE_0.1_REQUIREMENTS_REVIEW.md` |
| Files modified | `docs/PHASE_0.1_REQUIREMENTS_REVIEW.md` (Rev 1 → 2 → 3) |
| Source files touched | **None** |

---

## 2. OBJECTIVE

Organise the requirements before coding, and — given D-1, D-3, D-4 and D-5 — define a stack that uses the real
**Google Maps SDK for Android**, keeps a **solo** user at a **guaranteed $0**, and still satisfies every core
requirement of the specification.

---

## 3. UNDERSTANDING CHECKLIST

All still confirmed:

- [x] Android Studio, **Java only**, XML layouts, AndroidX, Material Design Components, Gradle.
- [x] Room local database, local-first storage.
- [x] Manual store pinning; no store data is invented by me.
- [x] Markers: orange = pending, green = completed, distinct Home marker, blue current location, plus icon/shape
      differences for accessibility.
- [x] Completion requires: valid store record + user inside the arrival radius + explicit **YES**.
- [x] Navigation never starts automatically; the user taps **Start Navigation**.
- [x] Duplicate completion prevented; one valid completion event per store.
- [x] Completion history preserves real chronological order via `dailySequence`.
- [x] Home is the permanent final destination, is not a store, never affects counts.
- [x] Tracker: Today, Yesterday, Overall, Remaining, Total, Percentage (1 decimal), Latest Completed.
- [x] Store table + Completion History table are authoritative, not stored counters.
- [x] Route Cache deferred.
- [x] Phases delivered one at a time, then stopped for testing.
- [x] **D-1:** the app operates in the Philippines.
- [x] **D-4:** the map layer is the Google Maps SDK for Android, free tier.
- [x] **D-5:** solo, single user, single device, never distributed.

---

## 4. FEATURES INCLUDED (in this phase)

Planning artefacts only: requirements organisation, verified cost model, conflict resolution, the revised stack,
risk register, and the phase checklist.

---

## 5. FEATURES NOT INCLUDED YET

Everything executable. No Room, no map, no location, no pins, no routing, no navigation, no tracker, no
history/settings screens, no API key.

---

## 6. ASSUMPTIONS

| # | Assumption | Why | Impact if wrong |
| --- | --- | --- | --- |
| A1 | Target market is the **Philippines** (D-1). | User decision. | — |
| A2 | **Single-user, personal, solo app** (D-5) — no login, no accounts, no sync. | User decision. | If it is ever distributed, the key must be locked down and quotas re-reviewed. |
| A3 | `area` is free text (required, trimmed, max 60 chars). | No format specified. | A controlled list would replace the EditText with a spinner. |
| A4 | `completionDateKey` uses the **device default timezone** (Asia/Manila). | Spec says "local date". | Fixed-timezone reporting would need an explicit setting. |
| A5 | `dailySequence` resets to 1 at each local midnight. | Implied by the spec's per-day example. | — |
| A6 | One global arrival radius (default 50 m), not per store. | Spec models it in App Settings. | Per-store radius changes the settings model. |
| A7 | Package stays `com.cph.alpharoute`; the visible app name becomes "Alfamart Route Tracker". | Renaming the package rewrites every file for no functional gain. | — |
| A8 | The existing "Hello World" scaffold is throwaway and may be replaced in its phase. | Android Studio default. | — |
| A9 | **NEW:** you accept attaching a **billing account with a payment method** to a Google Cloud project, with quota caps guaranteeing $0 spend (§0.2). | Google requires it; there is no alternative. | Without it, we fall back to Plan B (§9.5). |
| A10 | **NEW:** the app will **not** use a Map ID. | Keeps map loads on the unlimited **Maps SDK** SKU instead of the 10,000/month **Dynamic Maps** SKU. | Using a Map ID later (for cloud styling) moves map loads to the 10,000/month cap — still fine for solo use, but no longer unlimited. |
| A11 | **NEW:** the device has Google Play services (standard in the Philippines). | Needed for the Maps SDK and fused location. | If absent, neither the Google map nor the fused provider works at all. |
| A12 | **NEW:** turn-by-turn is built by us from the routing API's step data, **not** the Navigation SDK. | The Navigation SDK is an Enterprise SKU with only 1,000 free destinations/month and it cannot coexist with the Maps SDK. | If you later want the real Google turn-by-turn overlay, it costs $25 per 1,000 destinations beyond 1,000/month. Decision deferred to Phase 4.1. |

---

## 7. FILES TO CREATE (this phase)

| Path | Type | Purpose |
| --- | --- | --- |
| `docs/PHASE_0.1_REQUIREMENTS_REVIEW.md` | New | This requirements review / project baseline. |

---

## 8. FILES TO MODIFY (this phase)

| Path | Status | Note |
| --- | --- | --- |
| `docs/PHASE_0.1_REQUIREMENTS_REVIEW.md` | **Modified** (Rev 2 → Rev 3) | D-4/D-5 integrated; Google Maps SDK reinstated; verified cost model and cost-safety controls added. |

**No application source file has been created or modified.** Baseline inspected read-only:

| Path | Status | Note |
| --- | --- | --- |
| `settings.gradle` | Existing, unmodified | `rootProject.name = "AlphaRoute"` |
| `build.gradle` | Existing, unmodified | AGP plugin alias only |
| `gradle/libs.versions.toml` | Existing, unmodified | Version catalog |
| `app/build.gradle` | Existing, unmodified | namespace `com.cph.alpharoute`, AGP 9.2.1, compileSdk 36, minSdk 24, targetSdk 36, Java 11 |
| `app/src/main/AndroidManifest.xml` | Existing, unmodified | No permissions |
| `app/src/main/java/com/cph/alpharoute/MainActivity.java` | Existing, unmodified | "Hello World" scaffold |
| `app/src/main/res/values/themes.xml` | Existing, unmodified | `Theme.AlphaRoute` |

---

## 9. DEPENDENCIES

### 9.1 Already present (verified)

AGP 9.2.1 · Gradle wrapper 9.4.1 · `androidx.appcompat:appcompat` 1.7.1 ·
`com.google.android.material:material` 1.14.0 · `androidx.constraintlayout:constraintlayout` 2.2.1 ·
`androidx.activity:activity-ktx` 1.13.0 · `junit:junit` 4.13.2 · `androidx.test.ext:junit` 1.3.0 ·
`androidx.test.espresso:espresso-core` 3.7.0

### 9.2 Cost model for a SOLO user (official figures, verified 2026-09-25)

| What | SKU | Free cap | Realistic solo usage | Cost |
| --- | --- | --- | --- | --- |
| **Map display** | **Maps SDK** `6DE1-4D9C-5B67` (Android, **no Map ID**) | **Unlimited** | thousands of map loads/month | **$0** |
| Map display *if a Map ID were used* | Dynamic Maps `FAF4-3B2D-51B2` | 10,000 / month | thousands | **$0** |
| **Route + turn-by-turn data** | **Routes: Compute Routes Essentials** `9EFF-679A-9B16` | 10,000 / month | ~1,000–3,000 / month | **$0** |
| Route data (legacy alternative) | Directions `28A8-3EB4-4595` | 10,000 / month | ~1,000–3,000 / month | **$0** |
| Route data, if a Pro-tier field is required | Routes: Compute Routes Pro `02F7-1B55-DC90` | 5,000 / month | ~1,000–3,000 / month | **$0** |
| Geocoding (optional — not needed; pinning is manual) | Geocoding `BAC8-4E68-E261` | 10,000 / month | 0 | **$0** |
| Location updates | *Not a Maps Platform SKU* | Unlimited | — | **$0** |
| *Navigation SDK — NOT chosen* | Navigation Request `AF6D-A7B0-31CF` (**Enterprise**) | 1,000 / month | 30–60 destinations/day can exceed this | **$25 per 1,000 after the cap** |

**Conclusion:** the chosen stack touches **only** SKUs with free caps that a solo user cannot realistically
exhaust. No paid SKU is used at all.

### 9.3 THE FROZEN STACK (Revision 5) — Option D1

| Layer | Component | Key needed? | Billing / card? | Cost |
| --- | --- | --- | --- | --- |
| **In-app map** | `org.osmdroid:osmdroid-android:6.1.20` + `https://tile.openstreetmap.org/{z}/{x}/{y}.png` | **No** | **No** | **$0** |
| **Map tiles policy** | App-specific `User-Agent`, OSM attribution, ≥7-day caching, no bulk/offline fetch | — | — | **$0** |
| **Route geometry + steps** | openrouteservice free Standard plan, `Directions V2` with `instructions=true` | Free key (email only) | **No** | **$0** (2,000/day) |
| **Multi-stop optimization (V8)** | openrouteservice `Optimization` (VROOM) | Same free key | **No** | **$0** (500/day, 50 stops) |
| **Turn-by-turn, live traffic, road rules** | **Google Maps app** via `google.navigation:q=lat,lng&mode=d` (Intent, package `com.google.android.apps.maps`) | **No** | **No** | **$0, unlimited** |
| **Alternative navigator** | Waze via `https://waze.com/ul?ll=lat%2Clng&navigate=yes` (package `com.waze`) | **No** | **No** | **$0** |
| **Offline fallback navigator** | Organic Maps (free, open source, offline) — optional, later phase | **No** | **No** | **$0** |
| **Route drawing** | osmdroid `Polyline` + `Marker` overlays | No | No | **$0** |
| **Current location** | `com.google.android.gms:play-services-location` (FusedLocationProviderClient) | **No** | **No** | **$0** |
| **Local data** | Room | No | No | **$0** |

**Total project cost: $0, with no billing account, no payment method and no API key of any kind.**

> **Do not use** the Navigation Connect API variant (`action_token` / `external_trip_token`) — that one is a paid
> Google Maps Platform product. Plain intents only.

The Revision 3 Google Maps SDK table below is retained only as the **D2 alternative** and is **not** part of the
frozen stack:

| Layer | Component | Key | Cost |
| --- | --- | --- | --- |
| **Map display** | **Google Maps SDK for Android** (`com.google.android.gms:play-services-maps`), loaded **without a Map ID** | Maps key | **$0, unlimited** |
| **Route geometry + step data** | **Google Routes API** (REST, `computeRoutes`) — current service; Directions API is the legacy fallback | Same key | **$0** within 10,000/month |
| **Turn-by-turn guidance UI** | **Built by us** (D-3): turn card, next-turn distance, ETA, from the routing response's navigation instructions | — | **$0** |
| **Route drawing** | Maps SDK `Polyline` + `Marker` overlays | — | **$0** |
| **Current location** | `com.google.android.gms:play-services-location` (FusedLocationProviderClient) | No extra key | **$0** |
| **Local data** | Room | — | **$0** |
| **Multi-stop optimization (V8)** | RouteOptimization **SingleVehicleRouting** `2020-AA6E-7D49` — 5,000/month free | Same key | **$0** within cap |

### 9.4 Planned additions, by phase

| Phase | Artifact(s) | Purpose |
| --- | --- | --- |
| 1.2 | `com.google.android.material:material` (present) | Bottom navigation, toolbar, cards |
| 2.1 | `androidx.room:room-runtime`, `androidx.room:room-compiler` (annotationProcessor) | Local database |
| 2.1 | `androidx.lifecycle:lifecycle-viewmodel`, `androidx.lifecycle:lifecycle-livedata` | ViewModel + observable data |
| **3.1** | **`com.google.android.gms:play-services-maps`** | **Map display (Google Maps SDK)** |
| 3.2 | `com.google.android.gms:play-services-location` | Fused location provider |
| **4.2–4.3** | *(no new dependency)* — HTTPS + JSON against the Routes API | **Route preview + turn-by-turn** |
| 6.x | `androidx.recyclerview:recyclerview` | History lists |
| 10.2 | `androidx.room:room-testing`, `androidx.arch.core:core-testing` | Database/unit tests |

**Version-pinning policy:** exact versions are resolved and pinned in `gradle/libs.versions.toml` **in the phase
that introduces each dependency**, after re-checking the current documentation. I will not guess version numbers.

### 9.5 Plan B (documented, not used) — if you decide against the card

If A9 turns out to be unacceptable, the Revision 2 stack remains fully viable and costs nothing at all with no
account of any kind:

- **Map:** `org.osmdroid:osmdroid-android:6.1.20` + `https://tile.openstreetmap.org/{z}/{x}/{y}.png`
  (requires an app-specific `User-Agent`, OSM attribution, ≥7-day tile caching, no bulk/offline fetch).
- **Routing:** openrouteservice free Standard plan (€0, email signup, no card) — 2,000 routes/day,
  turn-by-turn instructions, plus 500 optimizations/day.

Both plans keep the exact same Room schema, tracker logic, completion rules, and screens — only the map and
routing layer differ, and that layer is isolated behind our own wrapper so it can be swapped.

---

## 10. PERMISSIONS AND MANIFEST CHANGES

Nothing is added in Phase 0.1.

| Permission | Introduced in | Why | Runtime prompt? |
| --- | --- | --- | --- |
| `android.permission.INTERNET` | 3.1 | Google map tiles + Routes API calls | No |
| `android.permission.ACCESS_NETWORK_STATE` | 3.1 | Detect offline state and say so clearly instead of faking routes | No |
| `android.permission.ACCESS_FINE_LOCATION` | 3.2 | GPS position: current-location marker, arrival detection | **Yes** |
| `android.permission.ACCESS_COARSE_LOCATION` | 3.2 | Approximate fallback when precise is denied | **Yes** |
| `android.permission.FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_LOCATION` | 4.4 | Keep guidance alive while backgrounded | No (typed service required) |
| `android.permission.POST_NOTIFICATIONS` | 4.4 | Notification for the location foreground service (API 33+) | **Yes** |
| `ACCESS_BACKGROUND_LOCATION` | **Not planned** | Avoid unless continuous background tracking becomes a hard requirement | — |

### 10.1 Manifest / build entries (added in their phases, not now)

- **Phase 3.1 — API key, injected safely:**
  `<meta-data android:name="com.google.android.geo.API_KEY" android:value="${MAPS_API_KEY}" />`
  with `manifestPlaceholders = [MAPS_API_KEY: <value from local.properties>]` in `app/build.gradle`.
  The key lives in `local.properties` (already git-ignored) and **never** in a Java file or in version control
  (rules 24 + privacy rule 5).
- **Phase 4.4:** `<service>` with `android:foregroundServiceType="location"`.

`targetSdk 36` implications: runtime notification permission on API 33+, mandatory `foregroundServiceType` on
API 34+.

### 10.2 MANDATORY cost-safety configuration (Phase 3.1, before the map is ever run)

These four controls are what turn "$0 in theory" into "$0 guaranteed". They are part of the phase's acceptance
criteria, not optional advice.

1. **Enable only what we use** in the Google Cloud project: *Maps SDK for Android* and *Routes API*. Nothing else.
   (Never enable Navigation SDK, Places, or anything Enterprise.)
2. **Do not use a Map ID.** Loading the map without a Map ID keeps map loads on the **unlimited Maps SDK SKU**.
   A Map ID would move them to the 10,000/month Dynamic Maps SKU (A10).
3. **Set hard daily quota caps** on every enabled API in *Google Maps Platform → Quotas*. When the cap is hit the
   API stops responding — it does **not** bill. Suggested starting caps: map loads 1,000/day, route requests
   300/day. Both are far above solo needs and far below anything chargeable.
4. **Budget alerts + API key restrictions:** create a Cloud Billing budget alert at a low threshold, and restrict
   the API key to your app's **package name + SHA-1 signing certificate** so nobody else can spend your quota.

**Honest limitation:** the key ships inside the APK and a determined person could extract it. With key
restrictions (package + SHA-1) an extracted key is useless to anyone else, and with quota caps it cannot cost
money even if abused. This is restated in Phase 4.1.

---

## 11. TECHNICAL RISK REGISTER (Revision 3)

### CONFLICT-0 — **RESOLVED by D-1.** Operating region = Philippines.

### CONFLICT-1 — **RESOLVED.** The Maps SDK and Navigation SDK cannot coexist, so we use the **Maps SDK only**
and build the turn-by-turn UI ourselves (D-3). The Navigation SDK is deliberately not used, which also keeps us
off a paid Enterprise SKU.

### CONFLICT-2 — **RESOLVED by D-4.** "No billing at all" and "Google Maps SDK" cannot both hold. D-4 wins; the
consequence is a billing account with a payment method, mitigated to $0 by §10.2.

### R1 — MEDIUM: an unattended billing account can still charge if a cap is missed
**Mitigation:** §10.2 is mandatory and part of Phase 3.1 acceptance. Caps are set *before* the first map run.

### R2 — MEDIUM: quota caps stop the service rather than bill it
If a cap is ever hit, the map or routing stops until the next day. **Mitigation:** caps set well above solo usage;
the tracker, Room data, markers, and history keep working with no network at all.

### R3 — MEDIUM: turn-by-turn quality is ours, not Google's
The Navigation SDK is not used, so the turn card, next-turn distance, and ETA are our code driven by the routing
response. Expect a simpler guidance UI than Google Maps' own. **Mitigation:** Phase 0.3 plans the navigation
screen explicitly; Phase 4.3 is scoped larger than originally written.

### R4 — MEDIUM: toolchain novelty
AGP 9.2.1 / Gradle 9.4.1 / compileSdk 36 is newer than the Maps SDK guides are validated against.
**Mitigation:** verify at Phase 3.1; the Maps SDK is a mature AAR and the risk is low, but it is not zero.

### R5 — LOW: `activity-ktx` pulls in the Kotlin standard library
No Kotlin *source* files exist (rule 2 respected), but the dependency chain is not Kotlin-free.
**Mitigation:** in Phase 1.1, either keep it (no Kotlin is written) or drop it and use `WindowCompat`. I will tell
you which, and why, before changing anything.

### R6 — LOW: date-boundary correctness
"Today"/"Yesterday" and `dailySequence` depend on the local date. Phase 10.2's "complete near midnight, reopen the
next day" scenario is the test that catches off-by-one-day errors.

### R7 — LOW: duplicate completion under rapid taps
Mitigated by design in Phases 5.2/5.3: re-read status inside a Room transaction, unique index on `storeId` in the
history table, and UI-level de-bouncing.

---

## 12. COMPLETE CODE

**None.** Phase 0.1 forbids production code.

---

## 13. CODE EXPLANATION

Not applicable. Existing scaffold for reference only: `MainActivity.java` extends `AppCompatActivity`, enables
edge-to-edge, inflates `activity_main.xml` (a single "Hello World!" `TextView`); `Theme.AlphaRoute` extends
`Theme.Material3.DayNight.NoActionBar`; the manifest declares one launcher activity with **no permissions**.

---

## 14. DATA FLOW FOR THIS PHASE (Revision 3)

```
Android Location Services
        |
        v
LocationProvider (FusedLocationProviderClient)
        |
        +--> Google Map (Maps SDK): blue current-location + orange/green/Home markers + route Polyline
        |
        v
Room (Store, CompletionHistory, AppSetting)  <--  Repository  <--  ViewModels
        |                                                            |
        |                                                            v
        |          Tracker calculations (Today/Yesterday/Overall/Remaining/%) --> Dashboard
        |
        +--> RouteSuggestionService
        |        |-- shortlist by straight-line distance (offline, always available)
        |        +-- RouteOptimization SingleVehicleRouting (free, 5,000/mo) for the Home-last order
        |
        +--> RoutingService (Routes API computeRoutes, free, 10,000/mo, HTTPS + JSON)
        |        |-- route polyline --> drawn on the map (Phase 4.2 preview)
        |        +-- navigation steps --> our own turn card / next-turn distance / ETA (Phase 4.3)
        |
        +--> Arrival check (distance vs. arrivalRadius) --> Completion dialog --> Atomic Room transaction
```

**Key property:** markers, tracker counts, history, and manual pinning are fully functional **offline**. Only map
tile downloads and route generation need connectivity — matching the spec's offline rules.

---

## 15. MANUAL TEST PROCEDURE

### 15.1 Baseline build check (Phase 0.1 acceptance)

1. Android Studio → **File → Open** → `C:/Users/Admin/OneDrive/Documents/custom system/AlphaRoute`.
2. Let Gradle sync finish. `local.properties` correctly contains
   `sdk.dir=C:\Users\Admin\AppData\Local\Android\Sdk`.
3. **Build → Clean Project**, then **Build → Rebuild Project** → confirm `BUILD SUCCESSFUL`.
4. Start an emulator or device (API 24+). For map work later, prefer a **physical device** (real GPS, real tiles).
5. **Run ▶** → confirm the app opens showing "Hello World!" with no crash.
6. Report the result, or the exact error text.

### 15.2 Cost-safety dry run (recommended now, no code needed)

Do this **before** we write any map code, so the billing side is proven safe first:

7. Create (or reuse) a Google Cloud project and enable billing.
8. Enable **only** *Maps SDK for Android* and *Routes API*.
9. Open **Google Maps Platform → Quotas** and set the daily caps from §10.2.
10. Create a **budget alert** at a low threshold.
11. Create an API key and restrict it to **Android apps → your package name + SHA-1**.
12. Confirm the Quotas page shows your caps as active.
13. Tell me the outcome. If any step is unclear, stop and ask — do not proceed on an assumption.

---

## 16. EXPECTED RESULT

- The baseline builds and launches with no crash; no source file changed.
- You have a verified, official cost model showing the chosen stack is **$0 for solo use**.
- You understand the one condition: **billing must be enabled**, and exactly how §10.2 guarantees $0.
- You know Plan B exists if you reject the card.

---

## 17. COMMON ERRORS AND FIXES

| Symptom | Likely cause | Fix |
| --- | --- | --- |
| `SDK location not found` | `local.properties` missing/wrong | It points at `C:\Users\Admin\AppData\Local\Android\Sdk`; re-point if the SDK moved |
| `Unsupported class file major version` | Gradle daemon on JDK < 17 | **Settings → Build → Gradle → Gradle JDK** → bundled JDK 17+ |
| `Could not resolve com.android.application:9.2.1` | Network / repository | `settings.gradle` already declares `google()` and `mavenCentral()` |
| *(Phase 3.1)* Map shows a grey grid with the Google logo | API key missing, wrong, or not restricted to your SHA-1 | Re-check `local.properties`, the manifest placeholder, and the key restriction |
| *(Phase 3.1)* `Authorization failure` in Logcat | Maps SDK for Android not enabled on the project | Enable exactly that API in the Cloud console |
| *(Phase 3.1)* `API key not authorized` after a new signing key | Debug SHA-1 changed (new machine / new keystore) | Add the new SHA-1 to the key's Android restrictions |
| *(Phase 4.x)* `REQUEST_DENIED` from Routes API | Routes API not enabled, or key restriction blocks it | Enable Routes API; ensure the restriction allows it |

---

## 18. ACCEPTANCE CHECKLIST

- [ ] App purpose agreed.
- [ ] The 12 screens are identified.
- [ ] The 3 (+1 deferred) entities are identified.
- [ ] The permission plan is accepted.
- [ ] **The stack in §9.3 is approved** — Google Maps SDK for Android (no Map ID) + Routes API + our own
      turn-by-turn UI + Room.
- [ ] **The condition in §0.2 is accepted:** a billing account with a payment method is required, and §10.2's
      quota caps will be configured before any map code runs.
- [ ] It is accepted that turn-by-turn will be our own UI, simpler than Google Maps' guidance.
- [ ] It is accepted that the Navigation SDK is **not** used (Enterprise, 1,000 free/month, cannot coexist with
      the Maps SDK).
- [ ] The risk register is reviewed.
- [ ] The phase checklist is accepted.
- [ ] The baseline build check (§15.1) passes.

---

## 19. UPDATED PROJECT STATE

**COMPLETED PHASES**
- ✅ **Phase 0.1 — Requirements Review (Revision 5, FINAL) — APPROVED 2026-09-25**

**CURRENT PHASE**
- None open. **Phase 0.2 — Architecture and Data Flow** is next and awaits your go-ahead.

**CURRENT DATABASE VERSION**
- None. Room has not been added. No database exists.

**CURRENT FILES** (pre-existing baseline, unmodified, plus the planning document)

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
docs/PHASE_0.1_REQUIREMENTS_REVIEW.md          <- created this phase (Rev 3)
```

**WORKING FEATURES** (tested)
- None. The scaffold shows static text; no feature is built or tested yet.

**KNOWN ISSUES**
- **One open condition:** §0.2 — the billing account with a payment method is unavoidable for the Google Maps
  SDK. Everything else is resolved.
- Open risks: R1 unattended-billing (mitigated by §10.2), R2 caps stop service (accepted), R3 our own
  turn-by-turn (accepted), R4 toolchain novelty, R5 `activity-ktx` Kotlin transitive, R6 date boundary,
  R7 duplicate completion.
- The project is **not under version control**. Recommendation: `git init` before Phase 1.1 so each phase is a
  reviewable commit. I will not do this without your go-ahead.

**PENDING PHASES** (not implemented)
- 0.2 Architecture and data flow · 0.3 UI and screen plan
- 1.1 Project creation · 1.2 Theme and base navigation · 1.3 Dashboard static UI
- 2.1 Room foundation · 2.2 Store CRUD · 2.3 Settings storage
- 3.1 Google Map display + cost-safety setup · 3.2 Current location · 3.3 Manual pin creation ·
  3.4 Marker loading · 3.5 Store details · 3.6 Edit/delete · 3.7 Home pin
- 4.1 Routing service preparation (Routes API) · 4.2 Route preview · 4.3 Turn-by-turn UI (ours) · 4.4 State recovery
- 5.1 Arrival radius · 5.2 Completion dialog · 5.3 Atomic completion transaction
- 6.1 Live tracker · 6.2 Daily history list · 6.3 Daily completion order · 6.4 Completion details ·
  6.5 Consistency check
- 7.1 Nearest pending store · 7.2 Recalculate after completion · 7.3 Home-aware heuristic · 7.4 No pending stores
- 8.1 Routing service review · 8.2 Road-based next store · 8.3 Multi-stop optimization (Home last) ·
  8.4 Complete route preview
- 9.1 Local export · 9.2 Local import · 9.3 Optional cloud sync
- 10.1 Error handling · 10.2 Automated and manual testing · 10.3 Release preparation

---

## 20. STOP POINT

Phase 0.1 (Revision 5, FINAL) is complete and **approved**. **No production code has been written.**

**How we got here:**

1. **Your map is free and unlimited.** Google's own docs: *"All mobile usage of the Maps SDK for Android is
   unlimited"*, and the Maps SDK SKU's free cap is literally `Unlimited`. For a solo user, map display will never
   cost anything — provided we **don't use a Map ID**.
2. **But billing must be enabled.** *"To use the Maps SDK for Android, you must enable billing on each of your
   projects and include an API key."* There is no way to get a key without a billing account and a payment
   method. That is the one thing I cannot engineer around.
3. **$0 is still guaranteed**, by four mandatory controls (§10.2): enable only Maps SDK + Routes API, no Map ID,
   hard daily quota caps so the API stops instead of billing, and a key locked to your package name + SHA-1.
4. **Routing stays free for solo use:** Routes API gives 10,000 requests/month free; you would use roughly
   1,000–3,000.
5. **The Navigation SDK is not used** — it is an Enterprise SKU with only 1,000 free destinations/month and it
   cannot coexist with the Maps SDK. So the turn-by-turn overlay is our own UI. Everything else in your
   specification is unchanged.
6. **Plan B is preserved:** if you decide the card is unacceptable after all, the osmdroid + openrouteservice
   stack from Revision 2 costs nothing with no account of any kind, and swaps in without touching the database,
   tracker, or screens.
7. **NEW — Option D answers your latest request.** A fully free way that behaves like Google Maps: launch the
   **Google Maps app** (or Waze) via a plain Android Intent. Google's own docs for these intents specify **no API
   key, no billing, no account and no quota** — they just hand a destination to an app already on the phone. You
   therefore get **real-time turn-by-turn, live traffic, lane guidance, speed limits and road rules for $0**, with
   no card anywhere in the project. Full detail, code, and the side-by-side comparison are in
   `docs/MAP_NAVIGATION_OPTIONS.md`.

---

### ✅ FINAL DECISION — Option D1 approved

> **Selected stack:** osmdroid + OpenStreetMap for the in-app map · **Google Maps app via free Intent** for
> turn-by-turn navigation with live traffic and road rules · openrouteservice free plan for route geometry and
> step data · Room for local data.
>
> **Cost: $0. No API key. No billing account. No payment method. No quota.** Nothing to sign up for.

**Locked requirements (unchanged from your specification):**

- Manual store pinning; no store data is invented by me.
- Orange = pending, green = completed, distinct Home marker, blue current location — differentiated by icon and
  shape as well as colour.
- Completion requires: valid store record **+** user inside the arrival radius **+** explicit **YES**.
- Navigation never starts automatically; the user taps **Start Navigation**.
- One store is never counted twice; one completion-history row per store.
- `dailySequence` reflects the real order of completion, per local date.
- Home is the final destination, is not a store, and never affects counts.
- Tracker derived from the tables: Today, Yesterday, Overall, Remaining, Total, Percentage (1 decimal),
  Latest Completed.
- Route Cache deferred to Version 8.

**Changed requirements (your explicit approval recorded as D-6 / D-7 / D-8):**

- "Google Maps SDK for Android" → **osmdroid + OpenStreetMap** (in-app map).
- "Google Navigation SDK for Android" → **Google Maps app via a free Intent**.
- "Navigation must remain inside the Alfamart Route Tracker application" → **navigation happens in the Google
  Maps app**, while our app keeps a foreground location service and detects arrival.

**Open risks carried into Phase 0.2 (all with documented mitigations):** OSM data quality in remote PH areas ·
OSM tile policy compliance (User-Agent, attribution, ≥7-day cache, no bulk fetch) · openrouteservice free-tier
quotas (2,000 routes/day — far above solo use) · no offline map download · turn-by-turn UI is Google's app, so
our own guidance UI is no longer needed · osmdroid release cadence (isolated behind our own map wrapper).

**Before Phase 0.2 — please run the baseline build check (§15.1)** so we know the starting point is healthy, and
report the result. Nothing in Phase 0.1 changed any source file.

**Next phase on your instruction: Phase 0.2 — Architecture and Data Flow.**
Per your rule 6, I will not start it automatically.

**Phase complete. Do not proceed to the next phase until all acceptance criteria pass.**

# AlphaRoute — HANDOFF / CONTINUATION PLAN

**Read this file first in any new conversation.** It is the single source of truth for where the project
stands, what has been decided, what must not be broken, and what comes next.

**Last updated:** 2026-09-27
**Project root:** `C:/Users/Admin/OneDrive/Documents/custom system/AlphaRoute`
**Last completed phase:** 7.1 – 10.3 (the suggestion, road routing, backup, and release prep) — **build verified; device test outstanding**
**Next phase:** **none — the roadmap is complete** (except 9.3 Firebase, deferred by D-9)

---

## 1. PROJECT AT A GLANCE

| Item | Value |
| --- | --- |
| Product name | **Alfamart Route Tracker** (visible app label) |
| Repo / root project name | `AlphaRoute` |
| Package / namespace / applicationId | `com.cph.alpharoute` |
| Purpose | Manually pin Alfamart stores on a map, hand navigation to Google Maps, GPS-validate arrival, track daily/overall progress, keep completion history, suggest the next store, end at Home |
| Region | **Philippines** |
| Users | **Solo** — one user, one device, never distributed |
| Language | **Java only.** Zero Kotlin source files (verified) |
| UI | **XML layouts only.** No Compose, no ViewBinding |
| Architecture | Single Activity + Fragments, manual `FragmentManager` navigation |
| Database | Room, **version 2**, 3 tables (migrated in 7.0) |
| Look | **Glassmorphism** since Version 11 — gradient window background, translucent cards with a 1dp rim |
| Tests | **512 unit tests, 0 failures** — all run on the JVM, no emulator |
| APK size | ~9.85 MB (debug, **clean build** — see §8 on incremental sizes) |
| Total cost of the stack | **$0.** One free API key (openrouteservice), no billing account, no card anywhere |
| Version control | **NOT under git.** Strongly recommended — see §14 |

---

## 2. THE WORKING AGREEMENT (MANDATORY — DO NOT VIOLATE)

These are the user's own rules. Breaking any of them breaks the project.

1. **Java only.** No Kotlin, Compose, Flutter, React Native, Xamarin, JS mobile frameworks. XML layouts.
2. **One phase at a time.** Complete ONLY the phase explicitly requested, then **STOP** and wait for the
   user's test result. Never auto-continue to the next phase.
3. **Never invent data** — no store names, codes, route numbers, coordinates, counts, history, credentials.
4. **Never auto-complete a store. Never auto-start navigation.** Nothing is ever counted without an explicit
   **YES** in the confirmation dialog — **that dialog is the single gate.**
   ⚠️ **Revised 2026-09-27 at the user's request:** the arrival radius is **no longer a gate on the
   button.** "Store completed?" is offered for any pending store, wherever the driver is, because a weak
   fix or having driven on before tapping withheld it from exactly the people who needed it — the user's
   words were *"kahit di na malapit … minsan humina pa signal ehh wala den"*. The radius still decides what
   the arrival **banner** says (arrived / approaching / outside / no fix); it no longer decides whether the
   question can be asked.
5. **One store is never counted twice.**
6. **Always give the exact file path** and mark each file **New / Existing / Modified / Replaced**.
7. **Provide the complete updated file**, not a diff, unless the user asks for a section.
8. **No placeholders** like `// Add your code here`. If a temporary is unavoidable, state: why it is
   temporary, what value is required, where the real value comes from, **and which phase replaces it**.
9. **Never put a private API key in a shared Java file.**
10. **No destructive Room migration** once real data exists.
11. **Do not remove or replace a working feature** without: a technical reason, the reason explained, the
    affected files identified, and a safe replacement provided.
12. **Clarification: at most ONE question**, only when critical (missing / conflicting / ambiguous / unsafe).
13. **Response format:** the 20-section phase format — see §10.

### How the user likes to be communicated with

- **Plain language, not jargon.** Lead with the simple version: what the problem is, what the choices are,
  what you recommend. Detail afterwards. A wall of options without a recommendation loses them.
- **Always give a clear recommendation**, not just a list of choices.
- English, though they sometimes write in **Taglish** (e.g. *"di ko gets yan"* = "I don't get that",
  *"sige b muna"* = "okay, B for now").
- They **value verification** — they appreciate work that is actually built and tested, not just claimed.

---

## 3. THE FROZEN STACK (decision D1 — approved 2026-09-25)

**Option D1: fully free — no billing account, no payment method, and one free API key (openrouteservice).**

The map, the turn-by-turn hand-off and location all need no key at all. The routing service does, and it
is the only one: free Standard plan, email signup, no card. It lives in `local.properties` →
`BuildConfig.ORS_API_KEY`, read by `RoutingConfig` and nothing else — see Phase 4.1.

| Layer | Component | Key? | Cost |
| --- | --- | --- | --- |
| **In-app map** | `org.osmdroid:osmdroid-android:6.1.20` + OpenStreetMap tiles | **None** | **$0** |
| **Turn-by-turn, live traffic, road rules** | **The Google Maps app**, launched by a plain Intent | **None** | **$0** |
| **Route geometry + steps** | openrouteservice free Standard plan (2,000 routes/day) — `POST https://api.openrouteservice.org/v2/directions/driving-car/geojson`, key in the **`Authorization` header** | Free email key | **$0** |
| **Multi-stop optimization (V8)** | openrouteservice Optimization (500/day, 50 stops) | Same key | **$0** |
| **Current location** | `com.google.android.gms:play-services-location:21.4.0` | **None** | **$0** |
| **Local data** | Room 2.8.5 | None | **$0** |
| **JVM tests only** | `org.json:json` — **`testImplementation`**, so the platform's JVM stub is replaced where the tests run. **Never in the APK** | None | **$0** |

### Decision log

| ID | Decision |
| --- | --- |
| D-1 | Operating region = **Philippines** |
| D-2 | **Free stack only** — no billing account, no card, no paid tier |
| D-3 | Superseded — we do not build our own turn-by-turn UI |
| D-4 / D-5 | Google Maps SDK free tier was considered; app is **solo** |
| **D-6** | **Hand turn-by-turn off to the free Google Maps app via a plain Intent.** No key, no billing, no quota |
| **D-7** | **Navigation no longer happens inside our app** (replaces the original spec rule) |
| **D-8** | **Variant D1 chosen** — osmdroid + OpenStreetMap for the in-app map |
| D-9 | **Firebase deferred to Version 9** (*"sige b muna"*). The `SyncManager` seam is already wired |

### Why not Google Maps

Google Maps Platform **requires an active Cloud Billing account with a payment method even for free-tier
usage**, and the Navigation SDK is a paid Enterprise SKU. There is no free Google Maps path. Verified
against Google's own docs, 2026-09-25.

### The navigation hand-off (for Phases 4.2/4.3)

```java
Uri uri = Uri.parse("google.navigation:q=" + lat + "," + lng + "&mode=d");
Intent intent = new Intent(Intent.ACTION_VIEW, uri);
intent.setPackage("com.google.android.apps.maps");
if (intent.resolveActivity(getPackageManager()) != null) { startActivity(intent); }
```

- Waze alternative: `https://waze.com/ul?ll=<lat>%2C<lng>&navigate=yes`, package `com.waze`.
- **DO NOT use the Navigation Connect API** variant (`action_token` / `external_trip_token`) — that one is a
  **paid** Google Maps Platform product.
- **Android 11+ package visibility:** the manifest **must** declare a `<queries>` element for
  `com.google.android.apps.maps` and `com.waze`, or `resolveActivity()` returns null and the hand-off
  silently breaks. Not yet added — it is Phase 4.3's job.

---

## 4. ARCHITECTURE DECISIONS (AD-1 … AD-12)

| ID | Decision |
| --- | --- |
| **AD-1** | **Single Activity + Fragments**, manual `FragmentManager` navigation. **No Navigation Component** (AGP 9 novelty risk). Tabs are **hidden, not destroyed** |
| **AD-2** | **ViewModel + LiveData**, not `StateFlow` — Java has no coroutines |
| **AD-3** | `AppExecutors` owns threading. **`diskIO()` is a SINGLE-threaded executor** — it serialises writes, which is the third guard against double-counting |
| **AD-4** | **Room is the single source of truth.** UI observes LiveData; the network never writes to the UI |
| **AD-5** | **`UNIQUE` index on `CompletionHistoryEntity.(storeId, roundNumber)`** — a store physically cannot be counted twice **in the same round**. **Revised in Phase 7.0** from a bare `storeId`: the original made a second visit impossible ever, which rounds need. This is a **generalisation, not a weakening** — the guarantee was always "do not double-count", and it still is |
| **AD-6** | **FK `completion_history.storeId → stores.id` with `onDelete = RESTRICT`** — the database refuses to delete a completed store |
| **AD-7** | **Core library desugaring enabled** for `java.time` on minSdk 24 (`desugar_jdk_libs` **2.1.5**) |
| **AD-8** | Map layer isolated behind `MapConfig` / `MapMarkerController` / `CurrentLocationOverlay` — osmdroid is swappable |
| **AD-9** | Navigation hand-off isolated behind a future `NavigationLauncher` |
| **AD-10** | Active navigation target persisted in `AppSettingEntity.lastSelectedStoreId` |
| **AD-11** | **`domain/` is pure Java with zero Android imports** → unit-testable on the JVM |
| **AD-12** | Route suggestion behind a `RouteSuggestionStrategy` interface (V7.1 / V7.3 / V8.3) |
| **AD-13** | The **arrival radius** is the single `GeoConstants` value the Settings screen already writes. There is no second definition anywhere |
| **AD-14** | The **arrival decision is pure Java** in `domain/` (AD-11) — it is the most safety-critical calculation in the project, because it is the only thing that can offer a completion, and it has no database guard behind it |
| **AD-15** | The foreground service **observes; it does not write.** It publishes to `NavigationSession` and never touches Room. The write path stays in one place (5.3) |
| **AD-16** | A refused `POST_NOTIFICATIONS` degrades the **alert**, never the **monitoring**. An app that stopped watching because it could not post a notification would be silently broken |
| **AD-17** | A recorded session is **offered, never resumed**. `checkForResumableSession()` reports a state and cannot start a service; only the user's tap on Resume does (Android 12+ forbids a background FGS start). The check is a question because "was I still driving?" is not answerable from the database |
| **AD-18** | A recorded session older than **`NavigationRecovery.RESUME_WINDOW_MILLIS` (4 h)** is `STALE` and is **cleared without asking**. A session of unknown age (missing/negative/future timestamp) is also `STALE` — every ambiguous case resolves toward clearing |
| **AD-19** | Recovering a session is **not** restarting it. `restartMonitoring()` starts only the service: it never rewrites the session and never re-records `lastSelectedStoreId`, because re-recording would reset the recovery timestamp and make a stale session look fresh |
| **AD-20** | **The completion transaction is the only writer of `completion_history`**, and it spans two tables, so it lives in its own DAO (`CompletionTransactionDao`) — Room's `@Transaction` covers one DAO only. `CompletionRepository.completeStore()` is the only way in, and there is still no plain insert anywhere |
| **AD-21** | **The clock is read once**, by the repository, and passed into the transaction. It is used for *both* the `completionTimestamp` and the `completionDateKey` — reading it twice would let a completion begun at 23:59:59.999 be filed under the previous day while carrying the new day's timestamp |
| **AD-22** | **A refusal writes nothing.** `ALREADY_COUNTED`, `STORE_NOT_FOUND` and `FAILED` all leave the database exactly as it was. Only `SUCCESS` means anything changed, and only `FAILED` re-opens the dialog for a retry — the other two settle the question |
| **AD-23** | **No count is ever stored.** Every figure on every screen is derived from the rows on each change. A stored counter can drift from the records it summarises, and when it does there is no way to tell which is right |
| **AD-24** | **`domain/` never imports `androidx.room`.** The tracker takes the `StoreLike` / `CompletionLike` seams and the entities implement them — the same pattern `ArrivalEvaluator.LocationFixLike` uses. The dependency points data → domain, never the reverse |
| **AD-25** | **The consistency check reports and never repairs.** A checker that fixes what it finds can also destroy what it does not understand — deleting an orphaned history row would erase the only evidence a completion happened. It is also the only place a `MARKER_COUNT_MISMATCH` is looked for |
| **AD-26** | **A pushed screen takes its toolbar title from the fragment instance**, never from a tag→title map held in a field. A field is empty after a process death while the framework restores the back stack, so the screen would return with no title |
| **AD-27** | **One round is one complete pass of every store.** A store is completed once per round. Starting a new round puts every store back to PENDING and **deletes nothing** — every completion row stays, which is what makes the History able to show past rounds |
| **AD-28** | **A round is complete when every store currently in the database has a completion in it.** A store added mid-round therefore re-opens the round — the honest reading, because the user added it precisely because they still have to visit it |
| **AD-29** | **`currentRound` is stored, not derived from `MAX(roundNumber)`.** A round with nothing completed yet has no rows, so a derived value could not tell "round 3, nothing done" from "round 2, everything done" |
| **AD-30** | **The undo removes exactly one named row and is the only destructive operation on `completion_history`.** It takes a row id (not a store id), it resets the store only when the row belonged to the current round, and it always asks first. There is no bulk delete and no editing |
| **AD-31** | **The Dashboard's "Remaining" and "Completion" are round-scoped; "Overall completed" is lifetime.** A lifetime figure cannot go back up when a new round starts, and going back up is exactly what the user needs to see |
| **AD-32** | **A suggestion strategy must have a defined answer for every sparse context** — no location, no Home, no stores, no network. It **degrades rather than fails**, and it **labels which claim it is making**: "nearest to you" and "first in your list" are different statements, and the card never confuses them |
| **AD-33** | **`RoadDistanceStrategy` → `HomeAwareStrategy` → `NearestStrategy` is one fallback chain.** The network only ever improves an answer; it is never required for one. Driving distances are re-fetched only when the pending set changes or the user moves **250 m**, or the 500/day allowance would be spent on GPS jitter |
| **AD-34** | **A backup references stores by `storeCode`, never by row id** — ids are auto-generated, so a backup's ids mean nothing in the database it is restored into. The file is written from an **explicit field list**, so nothing secret can ever appear in it, and `lastSelectedStoreId` is deliberately excluded |
| **AD-35** | **The import validates before it writes, and writes in one transaction that throws to roll back.** `REPLACE` deletes **completions before stores** (AD-6 refuses the other order); `MERGE` keeps the app's copy of any store it already has. Every problem is reported, not just the first |
| **AD-36** | **The optimization service's limits are the app's limits** — 50 jobs, 3 vehicles — checked in `OptimizationRequestBuilder` **before sending**, not discovered from a 400. Only `"job"` steps are stops; the start and end steps carry locations and distances too |
| **AD-37** | **`LiveData.getValue()` is `null` until something observes it.** A screen must never read a repository's LiveData that it does not itself observe — use a one-shot read (`findAllNow`, `getNow`) or ask the ViewModel that does the observing. Three real bugs came from breaking this in Version 11; see §8 |
| **AD-38** | **The glass backdrop lives on `android:windowBackground`, and every screen root must be transparent.** This is what gives every screen the gradient with no per-layout work, keeps the launch window consistent, and lets the cards be translucent. An opaque root silently flattens the whole effect |
| **AD-39** | **A round's "days taken" counts the days WORKED, not the calendar span.** A round worked on the 1st and then the 10th is **two** days of work with nine days in between, and "10 days" would be a misleading answer to "how long did it take" |
| **AD-40** | **The round report lists rounds newest-first and days oldest-first**, deliberately differently: the round in progress is what the user is living in, and "day 1, day 2, day 3" is the order the story makes sense in. `dayNumber` is 1-based **within the round** — never the day of the month |
| **AD-41** | **A round is complete if the user moved past it (`roundNumber < currentRound`) OR it covered the whole route.** The count alone reads a round closed early and later added to as open for ever; the round number alone reads the round in progress as open even after its last store is done |
| **AD-43** | **A POPUP IS NOT GLASS.** Glassmorphism belongs to the surfaces you read **through** on the way to something else — the cards on a screen. A popup (dialog, bottom sheet) is a surface you **read**, and anything showing through it competes with the text the user is being asked to act on. **Dialogs and sheets are opaque** (`md_surface_container_high`); only screen cards are translucent |
| **AD-44** | **A card and an app bar are not the same surface either** — a card has only the gradient behind it, a bar has the whole scrolling app. Bars are 97% opaque (`glass_chrome`) and have **no** fade, because at 85% the scrolled content stayed legible and rendered *through* the toolbar title |
| **AD-45** | **`push()` hides everything currently visible, asked of the FragmentManager — never one recorded tag.** Hiding `activeTag` was correct only for a tab→screen push; in a chain (tab → day → detail) it hid the tab and left the middle screen visible. This was latent for six versions, masked by opaque screen roots, and **exposed** by glassmorphism making them transparent |
| **AD-42** | **A completed store CAN be deleted (Version 13), and its completion history goes with it — but AD-6's `RESTRICT` foreign key is UNCHANGED.** `StoreTransactionDao.deleteWithHistory()` deletes the history rows first, then the store, in one transaction that throws to roll back. Nothing is ever orphaned, and no schema change or migration was needed. An orphaning delete is still refused by the database, and `StoreDeleteInstrumentedTest` proves it |

### The five layers

```
UI (Fragments / XML)  →  ViewModels + LiveData  →  Repositories  →  Room v1 / openrouteservice
Platform services alongside: LocationProvider · MapMarkerController · CurrentLocationOverlay
                             NavigationLauncher (4.3) · NavigationMonitorService (4.3)
                             NavigationRecovery (4.4, pure) · NavigationSessionManager (4.4)
                             CompletionTransactionDao (5.3 — THE ONLY WRITER of completions)
                             TrackerCalculator (6.1, pure) · ConsistencyChecker (6.5, pure)
```

**The rule:** a Fragment never touches Room or HTTP. It talks to its ViewModel, which talks to a
Repository, which talks to Room or the network.

---

## 5. CURRENT DATABASE SCHEMA — version 2

Schema files: `app/schemas/com.cph.alpharoute.data.db.AppDatabase/1.json` **and** `2.json` — **keep both, never gitignore them.**
`1.json` is not history: it is the starting point `MIGRATION_1_2` is verified against.

> **Version 2 arrived in Phase 7.0** (rounds). It is the **first migration in the project** — see §8 for the
> traps, and `docs/PHASE_7.0_ROUNDS_UNDO_AND_AT_STORE.md` §13 for the reasoning.

### `stores`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | INTEGER PK AUTOINCREMENT | |
| `routeNumber` | TEXT NOT NULL | **UNIQUE index** |
| `storeCode` | TEXT NOT NULL | **UNIQUE index** |
| `storeName` | TEXT NOT NULL | |
| `area` | TEXT NOT NULL | max 60 chars |
| `latitude`, `longitude` | REAL NOT NULL | |
| `status` | TEXT NOT NULL | `PENDING` \| `COMPLETED` (stored as the **name**, not the ordinal) — indexed. Means "completed **in the current round**" |
| `createdAt`, `updatedAt` | INTEGER NOT NULL | epoch millis |
| `completedAt` | INTEGER NULL | **nullable `Long`**, not `0`. Reset to `NULL` by a new round |

### `completion_history`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | INTEGER PK AUTOINCREMENT | |
| `storeId` | INTEGER NOT NULL | **part of the UNIQUE index → AD-5 (revised)** |
| `roundNumber` | INTEGER NOT NULL DEFAULT 1 | **NEW in v2** — the other half of the UNIQUE index; also indexed alone |
| `completionTimestamp` | INTEGER NOT NULL | |
| `completionDateKey` | TEXT NOT NULL | `YYYY-MM-DD`, local timezone — indexed |
| `dailySequence` | INTEGER NOT NULL | 1, 2, 3… in real completion order **within that day**, across all rounds |

**UNIQUE index: `(storeId, roundNumber)`** → a store can be completed **once per round**, never twice in
the same one. This replaced the v1 `UNIQUE(storeId)`, which made a second visit impossible ever.

**FK:** `storeId → stores.id`, `onDelete = RESTRICT` → **AD-6** (unchanged: a store with **any** history,
from any round, cannot be deleted)

### `app_settings` — exactly one row, `id = 1`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | INTEGER PK | always `SINGLETON_ID = 1` |
| `homeLatitude`, `homeLongitude` | REAL NULL | null until Home is set |
| `homeSet` | INTEGER NOT NULL | separate flag — `isHomeSet()` requires all three to agree |
| `arrivalRadius` | INTEGER NOT NULL | default **50 m**, bounds 10–1000 |
| `currentRound` | INTEGER NOT NULL DEFAULT 1 | **NEW in v2** — the round in progress |
| `lastSelectedStoreId` | INTEGER NULL | **AD-10**, no FK |
| `lastUpdated` | INTEGER NOT NULL | set automatically on every write |

### Rules that must not be broken

- **`PRAGMA foreign_keys = ON`** is issued from a Room `Callback.onOpen` in `AppDatabase.getInstance`.
  SQLite has FK enforcement **off by default** — remove that line and **AD-6 silently stops working**.
- **Never add `fallbackToDestructiveMigration()`.**
- **Bump `DATABASE_VERSION` only together with a written `Migration`** — and register it with
  `.addMigrations(...)`. A missing registration is a crash at first launch, not a silent fallback.
- **A migration must be safe on a table that already has rows.** `ADD COLUMN ... NOT NULL DEFAULT` is the
  one form SQLite allows without rewriting the table. An index name in a migration must be **Room's own**,
  read out of the exported JSON rather than guessed.

---

## 6. COMPLETE FILE INVENTORY

### Root / build

```
settings.gradle · build.gradle · gradle.properties · local.properties (git-ignored)
gradle/libs.versions.toml            version catalog
gradle/wrapper/gradle-wrapper.properties
app/build.gradle                     desugaring, buildConfig, Room schema export, all deps
app/proguard-rules.pro
app/src/main/AndroidManifest.xml     app class, splash theme, 4 permissions
app/schemas/.../AppDatabase/1.json   EXPORTED SCHEMA — keep it
```

### Java — 105 source files

```
AlphaRouteApp.java                    DI container: executors, database, sync, location, 3 repositories
MainActivity.java                     the single Activity: splash, bottom nav, manual tab switching

data/db/
  AppDatabase.java                    Room v1, singleton, PRAGMA foreign_keys = ON
  StoreEntity.java                    3 indices (unique code, unique route, status)
  CompletionHistoryEntity.java        AD-5 unique storeId + AD-6 RESTRICT FK
  AppSettingEntity.java               single settings row
  Converters.java                     StoreStatus ↔ String (name, not ordinal)
  CompletionTransactionDao.java       ⚠️ THE COMPLETION TRANSACTION (5.3). The ONLY writer of
                                      completion_history. @Transaction spanning stores + history.
                                      Round-scoped since 7.0. Also holds undo() — the only other
                                      thing that touches the table, and it removes one named row
  CompletionOutcome.java              SUCCESS | ALREADY_COUNTED | STORE_NOT_FOUND | COMPLETION_NOT_FOUND | FAILED
  RoundTransactionDao.java            ⚠️ STARTING A NEW ROUND (7.0). The ONLY thing that resets a store's
                                      status. @Transaction spanning settings + stores
  RoundOutcome.java                   SUCCESS | ROUND_NOT_COMPLETE | NO_STORES | FAILED (7.0)
  StoreDao.java                       CRUD + observeAll/Pending/Completed + isCodeTaken/isRouteTaken
  CompletionHistoryDao.java           read-only; no update, no delete (by design)
  AppSettingDao.java                  observe / getNow / upsert

data/repository/
  StoreRepository.java                THE only route to the store table. 5-arg ctor
  CompletionRepository.java           READ-ONLY until Phase 5.3
  SettingsRepository.java             getArrivalRadius (always usable), clearLastSelectedStoreIdIf
  RepositoryCallback.java             always called on the main thread
  WriteOutcome.java                   SUCCESS / DUPLICATE_* / NOT_FOUND / INVALID_DATA /
                                      PROTECTED_BY_HISTORY / FAILED
  RoutingRepository.java              the ONLY class that makes a network request. Holds the current
                                      route as LiveData; stores nothing. 5 tests on the request body.
                                      measureRoute() scores an order the user decided (18.1)
  PlaceLinkRepository.java            reads a location out of a pasted link; follows a short one by hand
  PlanRepository.java                 saved route plans — full CRUD. The ONLY writer of route_plans and
                                      route_plan_stops. Generates the default name on the disk thread
                                      because counting plans is a database read (18.1)

data/sync/
  SyncManager.java                    the seam; documents the local-first ordering guarantee
  NoOpSyncManager.java                the Null Object still in force — see §12 for why that is now a
                                      decision, not a gap. There is no automatic transport
  CloudSyncRepository.java            MANUAL two-way sync (19.1). One Firestore document per account.
                                      Refuses without a signed-in uid; never deletes locally on a push

domain/                               PURE JAVA, zero Android imports (AD-11)
  StoreStatus.java                    PENDING | COMPLETED
  StoreFieldError.java                REQUIRED | TOO_LONG | INVALID_COORDINATES  (codes, not messages)
  StoreValidationResult.java          one error slot per field
  StoreValidator.java                 the rules + trimToEmpty (the ONE definition of "trimmed")
  ArrivalRadiusValidator.java         parseMetres: the only typed number in the app (16 tests)
  GeoCoordinate.java                  a latitude and a longitude. THE one coordinate type. PURE.
  RoutePlan.java                      distance + duration + the line. PURE.
  GoogleMapsLinkParser.java           reads a location out of a Google Maps link. PURE. 28 tests
  PlaceLinkState.java                 status + coordinate in ONE object. PURE.
  DistanceCalculator.java             haversine. PURE. NaN rather than a plausible zero. 13 tests
  ArrivalState.java                   NO_DESTINATION | NO_FIX | OUTSIDE | APPROACHING | ARRIVED. PURE
  ArrivalEvaluator.java               THE arrival decision + the LocationFixLike test seam. PURE. 20 tests
  RecoveryState.java                  NOTHING | OFFER_RESUME | STALE. PURE
  NavigationRecovery.java             the 4 h freshness window + 4 refusal cases. PURE. 15 tests
  TrackerSnapshot.java                the 7 Dashboard metrics, one immutable object. PURE
  TrackerCalculator.java              the Dashboard arithmetic + the StoreLike/CompletionLike seams.
                                      PURE — zero androidx.room imports (AD-24). 19 tests
  HistoryDay.java                     one day's completion count. PURE
  HistoryGrouper.java                 group by day, newest first. PURE. 12 tests
  ConsistencyChecker.java             the 4 data faults. PURE. Reports, never repairs (AD-25). 20 tests
  RoundProgress.java                  one round's progress. PURE
  RoundCalculator.java                THE round rule: complete when every store has a completion in it.
                                      PURE. 14 tests
  RoundDay.java                       one day inside a round: dayNumber (1-based within the ROUND, never
                                      the day of the month), dateKey, store ids. PURE
  RoundReport.java                    one round: total stores, days taken, range, isComplete. PURE
  RoundGrouper.java                   groups history into rounds, and each round into its days.
                                      Rounds newest-first, days oldest-first. PURE. 17 tests
  PendingStores.java                  ONE definition of "still to visit this round". PURE. Shared by the
                                      suggestion, the route planner and anything else that needs it
  StoreCandidate.java                 a store as the strategies see it: id, code, name, coordinates. PURE
  SuggestionContext.java              everything a strategy may look at, and nothing else. PURE
  SuggestionResult.java               status + store + distance + REASON. PURE
  RouteSuggestionStrategy.java        AD-12: the swappable rule
  SuggestionSupport.java              shared "nearest" arithmetic + the terminal states. PURE
  NearestStrategy.java                straight-line nearest (7.1). PURE
  HomeAwareStrategy.java              distance-to-you + distance-to-home (7.3). PURE. Heuristic, NOT optimal
  RoadDistanceStrategy.java           real driving distances, falling back down the chain (8.2). PURE
  RouteOrderPlanner.java              THE route order: nearest neighbour + 2-opt refinement, on the device.
                                      No network, no key, no quota. PURE. 28 tests. measure() scores an
                                      order WITHOUT reordering it — that is what a hand-made plan needs
  PlanMerge.java                      saved order + pending stores → the order to drive. PURE. 12 tests.
                                      Three jobs, all of them silent when wrong — see §8
  CloudSyncSummary.java               what a cloud restore WOULD add, worked out before it happens. PURE.
                                      13 tests. Mirrors BackupTransactionDao.importAll's merge rule, so the
                                      preview cannot promise one number and deliver another
  OptimizationPlan.java               the ordered route: ordered stops + totals. PURE
  OptimizationStop.java               one stop. PURE

location/
  LocationFix.java                    PURE JAVA. The location quality rules
  LocationProvider.java               the app's SINGLE location source (fused client)
  LocationPermissionHelper.java       3 permission states + the stored "we asked" flag

navigation/
  RoutingConfig.java                  the ONLY reader of BuildConfig.ORS_API_KEY: base URL, profile,
                                      directions URL, isConfigured(), attribution(). 13 tests
  RouteResponseParser.java            GeoJSON → RoutePlan. PURE apart from org.json. 18 tests
  RouteState.java                     status + plan in ONE object, so they cannot disagree
  NavigationLauncher.java             AD-9: the Google Maps Intent + the Waze fallback. 5 tests
  OptimizationState.java              IDLE | LOADING | READY | NOT_CONFIGURED | TOO_MANY_STOPS | OFFLINE |
                                      QUOTA_EXHAUSTED | FAILED (8.3)
  OptimizationRequestBuilder.java     the VROOM request body. PURE apart from org.json. 15 tests
  OptimizationResponseParser.java     the VROOM answer → OptimizationPlan. Only "job" steps are stops
  RouteMatrixParser.java              the matrix answer → driving distances. Refuses a row of the wrong
                                      width rather than pairing distances with the wrong stores
  NavigationSession.java              the active session: one immutable Snapshot per change (AD-15)
  NavigationSessionManager.java       start/stop/resume a session. The ONLY writer of the session + the service
  NavigationMonitorService.java       the foreground service. Observes; never writes. See §8 on fixObserver
  NavigationNotifications.java        the channel + the two notification ids (ongoing vs arrival)
  NavigationBootReceiver.java         clears a stale session after a reboot — it cannot resume (AD-72)

ui/common/StatCardView.java           compound view: label + value, one TalkBack sentence
ui/dashboard/DashboardFragment.java   constructor only — cards take content from XML
ui/dashboard/DashboardFragment.java   the Dashboard tab: 7 REAL cards from TrackerSnapshot (6.1)
ui/dashboard/DashboardViewModel.java  MediatorLiveData joining stores + history, so one emission = one
                                      consistent set of figures
ui/history/HistoryFragment.java       screen 10 (6.2): date groups, newest first, from HistoryGrouper
ui/history/HistoryAdapter.java        one card per day
ui/history/DailyHistoryFragment.java  screen 11 (6.3): a day's stores, joined with the store list
ui/history/DailyHistoryAdapter.java   one row per completion + the joined Row model
ui/history/CompletionDetailFragment.java screen 11b (6.4): the full record, a non-interactive map preview,
                                      and View on Map
ui/settings/SettingsFragment.java     the Settings tab: Home, arrival radius, version
ui/map/
  MapConfig.java                      tile source, User-Agent, cache bounds, default camera, initialise()
  MapUserAgent.java                   PURE JAVA. Builds the OSM User-Agent (11 tests)
  MapFragment.java                    the Map tab: map, location, banner state machine, long-press
  MapMarkerController.java            the ONLY class touching osmdroid Marker (AD-8)
  CurrentLocationOverlay.java         blue dot + accuracy circle
ui/store/AddEditStoreBottomSheet.java the Add / Edit form: one screen, two modes
ui/store/StoreDetailBottomSheet.java  the Store Details sheet: store id in, details + Edit/Delete out
ui/settings/HomeSetupFragment.java    Home Setup: a PUSHED screen, not a tab. Tap the map to choose Home
ui/route/RoutePreviewBottomSheet.java screen 7: distance, duration, attribution, Start navigation
ui/route/NavigationActiveBottomSheet.java screen 8: the live distance + the arrival indicator
ui/completion/CompletionConfirmDialogFragment.java screen 9 (5.2/5.3): THE ONLY YES. A DialogFragment,
                                      so it survives rotation. Rotation mid-write re-reads Room
ui/store/AddFromLinkBottomSheet.java  paste a Google Maps link → hands off to the Add Store form, unchanged

util/
  AppExecutors.java                   diskIO (single thread), networkIO, mainHandler
  GeoConstants.java                   earth radius, arrival-radius bounds, coordinate validity
  DateTimeHelper.java                 PURE JAVA. Epoch millis → readable date and time (16 tests)
  DateKeyHelper.java                  PURE JAVA. Epoch millis → the local YYYY-MM-DD day key (23 tests).
                                      Stored, never recomputed — see §8 on the midnight trap
  RouteTextFormatter.java             PURE JAVA. "1.7 km", "5 min", "1 h 15 min" (15 tests)
```

### Tests — 512 cases, all on the JVM

```
test/.../domain/GoogleMapsLinkParserTest.java        28   every link shape + the 164 m camera trap
test/.../util/DateKeyHelperTest.java                 23   the midnight boundary, zones, leap day, padding
test/.../domain/ArrivalEvaluatorTest.java            20   the arrival rules + the four refusals
test/.../location/LocationFixTest.java               20   accuracy/coordinate/staleness rules
test/.../domain/StoreValidatorTest.java              19   field rules, trimming, lengths, coordinates
test/.../domain/TrackerCalculatorTest.java           30   the 7 metrics, the empty DB, the divide-by-zero
test/.../navigation/RouteResponseParserTest.java     18   GeoJSON → RoutePlan, incl. the coordinate swap
test/.../util/GeoConstantsTest.java                  17   radius + coordinate bounds, earth radius
test/.../domain/ArrivalRadiusValidatorTest.java      16   the typed radius: bounds, overflow, not-a-number
test/.../util/DateTimeHelperTest.java                16   date/time text, zone + midnight boundaries
test/.../domain/ConsistencyCheckerTest.java          20   the 4 faults + the healthy cases (no false alarms)
test/.../ui/map/MapMarkerControllerTest.java         15   icon mapping + which coordinates may be drawn
test/.../util/RouteTextFormatterTest.java            15   distance/duration units + the locale trap
test/.../domain/NavigationRecoveryTest.java          15   the 4 h window to the millisecond + 4 refusals
test/.../domain/DistanceCalculatorTest.java          13   haversine against known distances + refusals
test/.../navigation/NavigationSessionTest.java       13   session lifetime, the late-fix race, the radius
test/.../navigation/RoutingConfigTest.java           13   key states + the HTTPS requirement
test/.../domain/HistoryGrouperTest.java              11   grouping, newest-first, month + year boundaries
test/.../domain/RoundCalculatorTest.java              14   the round rule, the empty route, mid-round adds
test/.../domain/RoundGrouperTest.java                 17   days worked vs calendar span, ordering, completeness
test/.../ui/map/MapUserAgentTest.java                11   the OSM User-Agent format
test/.../data/db/AppSettingEntityTest.java           10   the Home-configured rule
test/.../data/repository/StoreRepositoryTest.java     7   an edit cannot change status/createdAt/completedAt
test/.../navigation/NavigationLauncherTest.java       5   the URI coordinate format + the locale trap
test/.../data/repository/RoutingRepositoryTest.java   5   the request body: longitude first
test/.../ExampleUnitTest.java                         1   Android Studio template (Phase 10.2)
androidTest/.../ExampleInstrumentedTest.java              template (Phase 10.2)
```

### Resources

```
layout/  activity_main · fragment_map · fragment_dashboard · fragment_settings ·
         fragment_home_setup · fragment_placeholder · sheet_add_edit_store · sheet_store_detail ·
         sheet_route_preview · sheet_add_from_link · view_stat_card
menu/    menu_bottom_nav
drawable/ ic_marker_pending (36dp orange + circle) · ic_marker_completed (36dp green + check) ·
          ic_marker_home (36dp deep purple house, white outline) · ic_marker_current (blue dot) ·
          ic_my_location · ic_add_link · bg_store_status_chip ·
          ic_nav_{dashboard,map,history,settings} · ic_splash_logo · ic_launcher_*
values/  colors · colors-night (only colour roles, NO themes.xml) · themes · styles · type ·
         attrs · strings
xml/     backup_rules · data_extraction_rules
```

---

## 7. PHASE HISTORY — what each completed phase delivered

| Phase | Delivered | Notes |
| --- | --- | --- |
| **0.1** | Requirements review, risk register, decision log | Established D1 |
| **0.2** | Five-layer architecture, package structure, 12 ADRs, completion transaction contract | Design only |
| **0.3** | 13 screens specified (12 + 11b), resource plan, marker drawables, UI conventions | Design only |
| **1.1** | Project foundation: AGP 9.2.1 / Gradle 9.4.1 / compileSdk 36 / minSdk 24 / targetSdk 36 / Java 11 | 5 files |
| **1.2** | Theme (light + dark), typography, toolbar, splash, bottom nav, 4 placeholder tabs | 24 files. Deleted `values-night/themes.xml` |
| **1.3** | Dashboard: 6 stat cards via the `StatCardView` compound view | 8 files. A17: no nav buttons |
| **2.1** | Room v1, 3 entities, 3 DAOs, converters, 4 repositories, `AppExecutors`, `AlphaRouteApp`, sync seam | 23 files |
| **2.2** | `StoreValidator` + `StoreValidationResult` + `StoreFieldError`, `findByCode`/`findByRouteNumber`, trimming invariant | 7 files |
| **2.3** | Guaranteed-usable `getArrivalRadius()`, dangling-reference guard, settings contract | 6 files |
| **3.1** | osmdroid map, OSM tile-policy compliance, loading + offline states, default camera | 12 files |
| **3.2** | Location permission flow (4 states), blue dot + accuracy circle, camera rule | 17 files |
| **3.3** | Long-press → Add Store form, two-stage validation, orange marker | 7 files |
| **3.4** | `renderAll` reconciliation, green completed pin, marker persistence | 6 files |
| **3.5** | Marker tap → Store Details sheet; status chip, five fields, completion date/time/order; `DateTimeHelper` + 16 tests | 12 files |
| **3.6** | Edit mode on the Add/Edit form; Delete behind a confirmation; completed stores protected; `StoreRepository.update` hardened + 7 tests | 8 files. `update` now preserves `status`/`createdAt`/`completedAt` — see §8 |
| **3.7** | Home Setup (a pushed screen), the house pin, `renderHome`, a real Settings screen, `ArrivalRadiusValidator` + 16 tests | 15 files. One deviation: no "Select on map" button — see the phase record §13.2 |
| **3.8** | Pin from a shared Google Maps link: `GoogleMapsLinkParser` (pure, 28 tests), `PlaceLinkRepository`, `AddFromLinkBottomSheet`, a Map tab button, and the map centring on the imported point. `RoutePoint` renamed `GeoCoordinate` | 22 files. **No new permission, no dependency, no schema change** — and **the Add Store form did not change at all**. The 164 m camera trap is in §8 |
| **4.1** | `local.properties` → `BuildConfig.ORS_API_KEY` → `RoutingConfig`; the API's endpoint and auth verified against the live service; 12 tests | 5 files. No dependency, no permission, no schema change |
| **4.2** | The live route: `RoutingRepository` + `RouteResponseParser` + the polyline + the Route Preview sheet + **Start navigation** and the `<queries>` element | 26 files. **First network call in the project.** 44 new tests. No new permission |
| **4.4** | State recovery: `RecoveryState` + `NavigationRecovery` (pure, 15 tests — the 4 h window), the cold-start resume prompt, stale + dangling-reference cleanup, and both mid-drive recoveries in the Navigation Active sheet ("Grant permission" / "Resume monitoring") | 10 files, **15 new tests** (277 total). **No new permission, no manifest change, no schema change, zero Kotlin** |
| **5.2 + 5.3** | **The completion flow.** `CompletionTransactionDao` (the §13.7 contract — re-read, three guards, date key, daily sequence, insert, targeted UPDATE, throw-to-rollback), `CompletionOutcome`, `DateKeyHelper` (pure, 23 tests — the midnight boundary), screen 9 `CompletionConfirmDialogFragment`, the loading guard, rotation recovery by re-reading Room, and both entry points (the sheet's confirm action gated on `ARRIVED`, and the arrival notification carrying the store id) | 15 files, **23 new tests** (300 total). **The first and only write that moves a counter.** No new permission, no manifest change, **no schema change** — a DAO is not a schema change. ⚠️ **Device test outstanding** |
| **6.1 – 6.5** | **The tracker and the history — all read-only.** `TrackerCalculator` + `TrackerSnapshot` (pure, 20 tests, the `StoreLike`/`CompletionLike` seams so `domain/` never imports Room), the Dashboard wired to Room with 7 real cards and the placeholders **deleted**, `HistoryGrouper` + `HistoryDay` (pure, 11 tests), screen 10 History, screen 11 Daily History, screen 11b Completion Detail with a non-interactive map preview and **View on Map**, `ConsistencyChecker` (pure, 17 tests — reports, never repairs) surfaced in Settings, and generic `MainActivity.push()` | 31 files, **48 new tests**. **No new permission, no manifest change, no schema change, zero Kotlin.** Deleted `placeholder_history` and every `dashboard_value_*` string |
| **7.0** | **Rounds, undo, and the at-store shortcut.** **The first schema migration in the project** (v1 → v2): `completion_history.roundNumber` + `app_settings.currentRound`, with **AD-5 revised** to `UNIQUE(storeId, roundNumber)`. `RoundCalculator` + `RoundProgress` (pure, 14 tests), `RoundTransactionDao.startNewRound()` (re-checks completeness **inside** the transaction, throws to roll back), `CompletionTransactionDao.undo()` (one named row, current-round-aware), the at-store swap in Store Details, the round-aware Dashboard and Settings panel, and a round-scoped consistency check | **28 files**, round cases across three test suites. **No new permission, no manifest change.** ⚠️ **The migration is the one thing that must be device-tested first** — see §8 |
| **7.1 – 10.3** | **The suggestion, road routing, backup, and release — the rest of the roadmap.** `RouteSuggestionStrategy` (AD-12) with `NearestStrategy` → `HomeAwareStrategy` → `RoadDistanceStrategy` as **one fallback chain**, so the app works offline and the network only ever improves an answer. The Dashboard suggestion card stating **why** a store was chosen; the manual override; route-complete offering Home (7.1–7.4). Real driving distances via the **matrix** endpoint with a 250 m refresh threshold (8.2), and the **VROOM** request/answer parsers plus the optimized route screen (8.3, 8.4). JSON **export and import** through the system file picker, with a pure validator and an atomic restore (9.1, 9.2). **Instrumented tests** for the completion transaction, the round change and AD-6 (10.2), the two template tests deleted, and a **real launcher icon** (10.3) | **41 files, +120 tests** (495 total). **No new permission, no manifest change, no schema change, zero Kotlin.** The openrouteservice limits were **re-verified before a line of Version 8 was written** |
| **11** | **Glassmorphism.** The gradient backdrop on `android:windowBackground`, transparent screen roots, and a glass card style (20dp radius, translucent fill, 1dp lighter rim, elevation 0) used by every card in the app. Toolbar, bottom navigation, the five sheets and the dialogs all follow. **Plus three `LiveData.getValue()` bugs found by auditing** — all the same cause (a read that depended on another screen having observed the row first), and all invisible to a manual test | 2 new drawables, 1 style, 17 colour roles in both modes, 3 files with real fixes. **No new dependency, no schema change, zero Kotlin** |
| **12** | **The round report.** `RoundDay` / `RoundReport` / `RoundGrouper` (pure, 17 tests) group the history into rounds, and each round into the days it was worked on. `RoundReportFragment` + adapter show **"65 stores · 4 days"** with **"Day 1 · 1 Sep — 23 stores"** beneath it, and tapping a day reveals its store codes. The History tab gained a header card showing the round in progress, and is the way in | **12 files, +17 tests** (512 total). **No schema change, no permission, no new dependency, zero Kotlin** |
| **13** | **A completed store can now be deleted.** Until now AD-6's `RESTRICT` made that impossible, and there was no way to remove a store completed by mistake or one that had closed down. `StoreTransactionDao.deleteWithHistory()` deletes the history first then the store, in one transaction — **AD-6 is unchanged and no migration was needed.** The confirmation names what goes: *"…and 3 completion records will be deleted too"* | **9 files, 6 new instrumented tests.** **No schema change, no permission, no new dependency, zero Kotlin** |
| **4.3** | Arrival monitoring: `DistanceCalculator` + `ArrivalEvaluator` (pure, AD-14), `NavigationSession` + `Manager`, `NavigationMonitorService` (foreground), `NavigationNotifications`, `NavigationBootReceiver`, screen 8 + the map nav bar. `lastSelectedStoreId` finally written | 21 files. **46 new tests.** 4 new permissions, **no schema change**. ⚠️ **Device test outstanding** |

**Permissions currently declared:** `INTERNET` · `ACCESS_NETWORK_STATE` · `ACCESS_FINE_LOCATION` ·
`ACCESS_COARSE_LOCATION`. **No background location.**

---

## 8. ⚠️ ACCUMULATED TRAPS — READ THIS BEFORE WRITING CODE

Every one of these was discovered the hard way. They are the most valuable part of this document.

### osmdroid

| Trap | The rule |
| --- | --- |
| **`Configuration` is only a holder class** | In 6.1.x, `getInstance()` returns **`IConfigurationProvider`**. Every setter (`load`, `setUserAgentValue`, cache bounds) lives on that interface. `Configuration config = Configuration.getInstance();` **does not compile.** Most tutorials online are for older releases |
| **The OSM tile servers block generic User-Agents** | No error, no log — just a **blank map**. `MapUserAgent.build()` must stay in `MapConfig.initialise()`, called **first** in `AlphaRouteApp.onCreate` |
| **Three lifecycle calls are mandatory** | `mapView.onResume()` / `onPause()` / **`onDetach()`**. Missing `onDetach` leaks a map view per visit — and because tabs are hidden not destroyed, it accumulates across one app run |
| **Long-press overlay goes at index 0** | `mapView.getOverlays().add(0, eventsOverlay)`. osmdroid dispatches events top-down, so index 0 is the bottom of the stack and markers added later get their taps first |
| **Marker anchor** | `ANCHOR_CENTER, ANCHOR_BOTTOM` for a teardrop, so the tip sits on the coordinate |
| **Marker icons must update on status change** | Setting the icon only at marker creation leaves a completed store orange forever |
| **No offline map** | The OSM policy forbids prefetching. A blank map with no signal is **expected**, not a bug |
| **A marker tap needs a listener, or osmdroid shows its own bubble** | With no `OnMarkerClickListener` set, `Marker.onMarkerClickDefault` shows an info-window bubble **and pans the map**. Set one and **return `true`** to consume the tap, or that bubble appears behind your own UI. The signature is `boolean onMarkerClick(Marker marker, MapView mapView)` — a single-method interface, so a lambda works. Verified against osmdroid 6.1.20's own sources, not from memory |
| **A listener set after the markers are drawn must still fire** | `renderAll` creates markers before the fragment can wire anything up. Read the listener from a **field at tap time** — capturing it in the lambda when the marker is created leaves the first markers on screen dead |

### Room / database

| Trap | The rule |
| --- | --- |
| **A `@ForeignKey` is only a promise** | SQLite has FK enforcement **off by default**. `PRAGMA foreign_keys = ON` is issued from `AppDatabase`'s `Callback.onOpen` — **do not remove it**, or AD-6 silently does nothing |
| **Never destructive-migrate** | No `fallbackToDestructiveMigration()`. Bump `DATABASE_VERSION` only with a written `Migration` |
| **Keep `app/schemas/`** | The exported JSON is what makes a migration testable. **Never gitignore it** |
| **Store enum as its name, not ordinal** | An ordinal changes meaning silently if the constants are ever reordered |
| **`@Update` replaces the WHOLE row** | A Room `@Update` writes every column. An edit form that builds a partial entity leaves the untouched fields at their **Java defaults**, so editing a completed store would write `PENDING` and `completedAt = null` over it — silently, while its completion history stayed in place, leaving a store that is orange on the map, still counted in the history, and still refusing to be deleted. `StoreRepository.update` copies `id` / `status` / `createdAt` / `completedAt` back from the stored row before every write; `StoreRepositoryTest` (7 tests) holds that line |

### Android / Fragments

| Trap | The rule |
| --- | --- |
| **Tabs are hidden, not destroyed (A27)** | A hidden tab stays **RESUMED**, so `onPause` alone does not stop GPS or tile loading. Use **`onHiddenChanged`** too |
| **Fragments need a no-arg constructor** | Pass the layout id to `super(...)` from a public no-arg constructor, or FragmentManager cannot recreate the fragment on rotation |
| **`values-night/themes.xml` must NOT exist** | A `values-night` declaration of the same style **wins** in dark mode and does not carry the colour items — it silently reverts the app to Material purple **at night only**. Dark mode = `values-night/colors.xml` only |
| **`getViewLifecycleOwner()`, not `this`** | Observing LiveData with the fragment as owner causes duplicate observers on view recreation |
| **Coordinates travel as arguments** | Not fields — FragmentManager recreates via the no-arg constructor, so a field-held coordinate would be lost and a store pinned at latitude 0 |
| **Status/nav bar colours are NOT in the theme** | `EdgeToEdge.enable()` handles them; `android:statusBarColor` is deprecated and ignored from API 35 |
| **A Material `Chip` owns its own label colour** | A `Chip` paints its text through a `ChipDrawable` that reads the colour at inflation time, so switching it at runtime can silently do nothing — dark text on a dark container, with no error. For a chip whose colours change, use a tinted `TextView` instead |
| **XML comments cannot contain `--`** | A row of dashes used as a comment separator fails the resource parse: *"The string `--` is not permitted within comments."* **This has now happened TWICE** (Phase 3.5 and again in Phase 3.7, after being written into this very table). It is invisible because the separators look like decoration. Never write `--` inside an XML comment |
| **An incremental debug build inflates the APK size** | `mergeProjectDexDebug` appends new dex shards without clearing the old ones, so the reported size drifts upward across incremental builds. Phase 3.7's first build claimed 11.1 MB against a true 9.6 MB. **Run `./gradlew clean` before quoting an APK size as evidence** |
| **`colorError` is an AppCompat attribute, not a Material one** | `com.google.android.material.R.attr.colorError` **does not exist** and fails to compile (*cannot find symbol*). Caught by the build in Phase 3.6. Read the app's own `md_error` colour instead — the same value, defined for both light and dark mode, with no library dependency |
| **openrouteservice wants the key in a HEADER, not a query parameter** | Verified against the live API 2026-09-25: no header → **401** `"Authorization field missing"`; bad key → **403** `"Access to this API has been disallowed"`. Older versions accepted `?api_key=…` and most examples online still show it — using it gives a 401 that looks like an expired key, and puts the key into URLs and logs. Endpoint: `POST /v2/directions/driving-car/geojson` |
| **A Google Maps place link carries TWO coordinate pairs, 164 m apart** | `…/place/Name/@lat,lng,17z/data=…!3d<lat>!4d<lng>`. The **`@` pair is the camera**, the **`!3d`/`!4d` pair is the place**. Measured 164 m apart in the test fixture — and the arrival radius is **50 m**, so reading the wrong one pins the store on the wrong side of the building and the app then **never offers to complete it, and never says why**. `GoogleMapsLinkParser` reads `!3d`/`!4d` first and falls back to `@` only when they are absent; two tests hold that order |
| **A short link has to be followed, and one redirect is not enough** | `maps.app.goo.gl/…` carries no coordinates. Follow it **by hand** (`setInstanceFollowRedirects(false)`) so the `Location` header can be read without downloading a page — then parse each hop, and read the body (bounded) as a last resort, because a JS redirect has no `Location` header at all |
| **A GeoJSON route's coordinates are `[longitude, latitude]`** | The opposite of how a coordinate is usually spoken. Reading them the other way round puts every route in the sea off the coast of Africa, and nothing fails — the map just draws a line in the wrong place. **It applies in BOTH directions**: the request body must send longitude first too. `RoutingRepository.coordinatePair` and `RouteResponseParser.parsePoints` are the two halves, and both have tests |
| **Android 11+ hides other installed apps unless you declare them** | Without a `<queries>` element naming `com.google.android.apps.maps` and `com.waze`, `resolveActivity()` returns **null even when Google Maps is installed**, so the hand-off silently does nothing — no exception, no log, no message. Five lines of XML between the feature and an evening of confusion |
| **`String.format` uses the device locale** | On a phone set to a comma-decimal locale (German, French), `%.6f` of 14.5995 gives `14,599500` — which the `google.navigation:` URI reads as **two values**, sending the driver elsewhere. Same class of bug turns "1.7 km" into "1,7 km". Always pass `Locale.US` for machine-facing and numeric formats; there are tests that change the default locale to prove it |
| **`Double.toString` can produce scientific notation** | A small enough coordinate comes out as `1.0E-5`, which a navigation URI cannot read. Format coordinates with `%.6f`, never `toString()` |
| **`org.json` is a stub on the JVM** | Android provides it on the platform, so the app needs no dependency — but every method throws "not mocked" in a unit test. A `testImplementation 'org.json:json'` gives the tests a real copy without putting anything in the APK. Verified absent from the built APK |
| **A `buildConfigField` ends up inside the APK** | It is a Java compile-time constant, so javac inlines it into the bytecode. The key is therefore extractable from any APK built with it. Fine for a solo app (D-5) — but **the debug APK must never be shared**. Also: because of the inlining, a changed key needs the dependent class recompiled; tested as working incrementally, but a clean build removes all doubt |
| **A `DialogFragment` has no reliable "visible again" callback** | When a second sheet sits above it, the one underneath stays STARTED/RESUMED — only the window changes. So a sheet cannot refresh itself when uncovered. Close it instead of leaving it stale |

### Location

| Trap | The rule |
| --- | --- |
| **`shouldShowRequestPermissionRationale` is ambiguous** | It returns `false` both for "never asked" and "refused for good" — two states needing different screens. That is why `LocationPermissionHelper` stores a flag, set **BEFORE** the dialog is shown |
| **Both location levels must be declared** | On Android 12+, declaring only `ACCESS_FINE_LOCATION` means choosing "Approximate" leaves the app with **no location at all** |
| **Accuracy `0` means "unknown", not "perfect"** | A real GPS radius is never zero. `LocationFix.hasAccuracy()` is false for `NaN` **and** for `<= 0` |
| **Never `getLastLocation()` as the current position** | A cached fix can be hours old. `LocationProvider` deliberately does not call it |

### Build / Gradle

| Trap | The rule |
| --- | --- |
| **`java.time` needs desugaring on minSdk 24** | AD-7. `coreLibraryDesugaringEnabled true` + `desugar_jdk_libs` |
| **AGP 8+ has `BuildConfig` off by default** | `buildFeatures { buildConfig true }` |
| **Use JDK 21** | AGP 9.2.1 needs a modern JDK. It is at `C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot\` |
| **Non-blocking warning** | *"Deprecated Gradle features … incompatible with Gradle 10"* — comes from AGP 9.2.1 / Gradle 9.4.1, **not from our files**. Revisit at Phase 10.3 |

### Process traps (mine, worth avoiding again)

| Trap | The rule |
| --- | --- |
| **Editing a javadoc can delete the code below it** | When removing a comment line, never include the following line of live code in `old_string`. This actually happened in Phase 3.4 and deleted a `setTileSource` call |
| **`ACCESS_BACKGROUND_LOCATION` grep false positive** | Searching the merged manifest for that string returns a hit — it is the **XML comment text**, because the manifest merger preserves comments. Check for a `<uses-permission …>` **element** |
| **`removeObserver(this::onFix)` removes nothing** | `observeForever(this::onFix)` and `removeObserver(this::onFix)` are two **different lambda objects**. The observer is never removed, the component keeps receiving updates, and the LiveData holds it forever. Always store the observer in a field. Cost a real bug in Phase 4.3 — see `NavigationMonitorService.fixObserver` |
| **A foreground service may only be started from a visible screen** | Android 12+ throws `ForegroundServiceStartNotAllowedException` for a background start. A `BOOT_COMPLETED` receiver is a background context, so a session **cannot** be resumed after a reboot. Never design a resume that starts the service from a receiver |
| **`foregroundServiceType` must match in two places** | The manifest's `android:foregroundServiceType="location"` and the type passed to `startForeground()` must agree, and `FOREGROUND_SERVICE_LOCATION` must be declared from API 34. Missing either throws `SecurityException` **at start on the drive**, not at build time |
| **A missing accuracy must never widen the arrival radius as if it were good** | `ArrivalEvaluator` refuses a fix with no accuracy rather than treating the accuracy as 0. The effective radius is `radius + accuracy`, so a spurious zero is the most flattering possible lie — it would make the worst fix look like the best one |
| **Write tests against independently-known values, not against the code** | Three 4.3 tests failed on first run because the *expectations* were wrong, not the code — a 0.1° span compared as if it were 1°, and an `APPROACHING` band assumed to be wider than it is. Verify an expected number some other way before asserting it |
| **A future timestamp must be refused, not clamped** | If the device clock moves backwards mid-session, `now - lastUpdated` is negative — which reads as "brand new" and would offer a resume for something arbitrarily old. `NavigationRecovery` returns `STALE` for `lastUpdated > now`. Any "how old is this" check needs the same guard |
| **"Restart the service" is not "restart the session"** | Restarting a session rewrites the reference and **resets the timestamp the freshness rule depends on**, so a stale session starts looking fresh. Keep a service-only restart separate (`restartMonitoring()`) and never let it touch the database |
| **Two writers of one clock field is a bug waiting** | `lastUpdated` in `app_settings` is advanced by every settings mutation. A recovery check that also wrote to that row would refresh its own freshness input. The check is read-only apart from the deliberate clear |
| **A `registerForActivityResult` launcher must be a field** | It must be registered before the fragment is created; a launcher built inside a method throws. Both `MapFragment` and `NavigationActiveBottomSheet` hold theirs this way |
| **Two `PendingIntent`s with the same request code are the SAME intent** | Identity is action + data + component — **not** the extras. Two notifications both targeting `MainActivity` with no data will share one PendingIntent, and `FLAG_UPDATE_CURRENT` has the last one posted overwrite the other's extras. Give them distinct request codes (Phase 5.3: the ongoing one is 0, the arrival one is 4102) |
| **A DAO does not change the Room schema** | Only entities do. Adding `CompletionTransactionDao` left `1.json` untouched and `DATABASE_VERSION` at 1 — no `Migration`, no version bump. Do not bump the version "because a DAO was added" |
| **`@Transaction` only covers ONE DAO** | An operation spanning two tables (insert history + flip the store) must live in a single DAO, or it is not atomic at all. That is why `CompletionTransactionDao` exists instead of a method on `CompletionHistoryDao` |
| **Throwing is what rolls a Room transaction back** | Room calls `setTransactionSuccessful()` only when the `@Transaction` method returns normally. A half-applied completion must therefore **throw**, not return an error value — returning would commit it |
| **Never let the transaction read the clock itself** | Read it once, in the repository, and pass it down. Two readings let a completion begun at 23:59:59.999 be filed under the previous day while carrying the new day's timestamp |
| **A formatter built from the device locale is not stable** | `Locale.US` must be pinned on the `YYYY-MM-DD` pattern. Some locales use non-ASCII digits, and a Buddhist-calendar locale would render 2026 as 2569 — a key that is compared and sorted as a string must always be the same shape |
| **A rotation mid-write loses the callback** | The write runs on a thread owned by the Application, so its callback is addressed to the fragment that was just destroyed. Do not save a result — **re-read the database**, which is the only surviving record (AD-4) |
| **`list.size()` and a null-skipping loop are two different definitions of "a row"** | If a loop skips nulls, every count of the same list must skip them too — otherwise a single null makes the total disagree with the counters it is supposed to summarise. This was a **real bug caught by the tests in Version 6**, in both `TrackerCalculator` and `ConsistencyChecker`. Count by walking, not by `size()`, whenever the loop skips |
| **A `YYYY-MM-DD` key must be formatted as a key, not as a timestamp** | The History screen groups by the stored key, so it must display that key. Formatting a timestamp from one of the group's rows usually agrees — but after a timezone change it can render as the neighbouring day, and the group would be labelled with a date it does not represent. `DateTimeHelper.dateTextFromKey` |
| **`Collections.sort` needs `compareTo`, and `YYYY-MM-DD` is the format where text order is date order** | That is the whole reason for zero padding. Without it `2026-9-30` sorts after `2026-10-01` and the History screen shows days in a nonsense order |
| **A pushed screen's toolbar title must come from the fragment instance** | A tag→title map held in a field is **empty after a process death**, while the framework restores the back stack — so the screen returns with no title. `MainActivity.titleFor(fragment)` cannot go stale |
| **A tab cannot be shown underneath a pushed screen** | The pushed screen *hides* the tab. Any "jump to a tab" action must pop the whole back stack first, or the user lands on a hidden tab and sees a blank screen |

### Migration traps (learned the hard way in Phase 7.0 — the first migration)

| Trap | The rule |
| --- | --- |
| **`ADD COLUMN` on a table with rows needs a DEFAULT** | SQLite allows `ADD COLUMN x NOT NULL DEFAULT v` without rewriting the table, and that is the only form it allows. A `NOT NULL` column with no default cannot be added to a non-empty table at all. Both v2 columns carry one, and for the existing rows the default is the **true** value, not a placeholder |
| **A migration must be REGISTERED** | `.addMigrations(MIGRATION_1_2)` in `AppDatabase.getInstance`. Writing the `Migration` object and forgetting to register it is a crash at first launch: *"A migration from 1 to 2 was required but not found"* |
| **An index name in a migration must be Room's own** | Room generates `index_<table>_<col>` and `index_<table>_<col1>_<col2>`. Guessing gives a migration that runs and then fails schema validation. **Read the names out of the exported JSON** — that is what `app/schemas/.../1.json` and `2.json` are for |
| **Dropping an index loses no data** | An index is rebuilt from the table. Replacing the v1 unique index with the v2 one is a schema change, not a data change |
| **Never reach for `fallbackToDestructiveMigration()`** | It makes the mismatch go away by deleting every store and every completion. The project has now had its first real opportunity to use it and did not |
| **A changed unique index changes the ENTITY too** | The migration and `@Entity(indices = ...)` must agree, or Room's schema validation fails at open. Both were changed together |
| **A migration can only be truly verified on a device** | Room validates the result at open, which a JVM test cannot reach. `./gradlew` exporting `2.json` proves the **entity** is right, not that the migration produces it. Compare the two files, then test §15.1 |

### Suggestion and routing traps (Versions 7.1 – 8.4)

| Trap | The rule |
| --- | --- |
| **A matrix row has no "distance to yourself" entry** | The grid is `sources × destinations`, so with one source the row is one distance **per store**. A fixture with a leading zero models a store sitting where the user is, and `indexOfNearest` will correctly pick it. Two tests failed on this in Version 8 — the expectation was wrong, not the code |
| **A wrong optimization request produces a confident answer to the wrong question** | There is no error to catch. Assert the body **field by field**: the job ids, the coordinate order, the service time, the vehicle count, the profile, and `"options"` (see below — `{"g": true}` is required, not forbidden) |
| **Only `"job"` steps are stops** | A VROOM route's steps include a `"start"` and an `"end"`, both carrying a location and a distance. Counting them adds **two phantom stops** to every route. Filter on `"type"`, **not** on the presence of an id — the start and end steps have ids too. A job step carries **both** `"id"` and `"job"` with the same value |
| **The optimization endpoint is NOT under `/v2/`** | `POST /v2/optimization` answers **404** — a plain nginx page, not a JSON error. `POST /optimization` answers 200. Directions and matrix **are** under `/v2/`, so pattern-matching on them produces exactly this bug. This shipped in 8.3 and made **every** optimised route report "Could not build a route" until 2026-09-27. `RoutingConfigTest.theOptimizationUrlIsNotUnderV2` pins it |
| **The optimization totals live at the TOP level of the response, not inside the route** | `{"summary": {...}, "routes": [ ... ]}`. Reading `routes[0].summary` returns `null` and both totals become **0**, so a real route renders as "0 m". Each route does carry its own `distance`/`duration`, and an older shape nests a summary inside the route — read all three in order |
| **The service returns NO distance unless `"options": {"g": true}` is sent** | Not in the summary, not on the steps — only `duration` and `cost`. Measured on the live service 2026-09-27, 50 stops: **8,058 bytes without it and 13,304 with it** (≈ +5 KB, *not* the "roughly triples" the original note claimed). The `geometry` field it also returns is ignored by the parser — the option is requested for the distance, not the line |
| **`sources` / `destinations` in a matrix request are INDICES, not coordinates** | Sending coordinate pairs answers **400** `"Parameter 'sources' has incorrect value or format."`. It is an easy mistake because the directions endpoint takes bare pairs. `locations` = `[origin, ...destinations]`, so `sources` is `[0]` and `destinations` is `[1..n]`. **The failure was silent**: any non-200 made `requestMatrix` return `null`, which the caller reads as "no answer" and falls back to straight-line distance — so a permanently broken request looked like a working-but-approximate feature |
| **A failed response's body is on `getErrorStream()`, not `getInputStream()`** | Reading the wrong one returns an empty string, which looks like the service said nothing. The body carries the real reason — a 500 from the optimization endpoint means the solver found **no road near one of the coordinates**, and the message names which one. Surface it (capped in length) or every distinct failure reads as "HTTP 500" |
| **A screen whose ONE job is to show a route must not depend on the network** | The route screen asked a web service for the order, so it failed whenever the network, the key, the quota or a single unroutable coordinate went wrong — and it reported "could not build a route" instead of a route. That is a screen failing at its only job. The order is now computed on the device (`RouteOrderPlanner`) and the service is opt-in dead code |
| **Nearest neighbour alone is 12.5% worse than the solver — refine it** | Measured over real road distances on six fixtures: greedy **+12.5%**, greedy + 2-opt **+1.8%**, and 2-opt won outright on 3 of 6. 2-opt is ~40 lines, still instant, still offline. Shipping raw greedy would have been a route that looks sensible and quietly costs a fifth more driving |
| **In a 2-opt comparison, "no reference" and "unusable coordinate" are different things** | Both produce a leg that cannot be measured, and conflating them is a real bug. A **null** reference (no fix / no Home) means the leg does not exist → **0**, so the edge is invisible and the reversal is judged on the edges that can be measured; infinity there would silently disable every refinement of the first segment. An **unusable coordinate** gives `NaN` → **infinity**, so no reversal may pull it into a measurable slot; zero there makes a broken pin look free to move and the refinement shuffles the route around it. A test caught exactly this |
| **Zero duration means "not known", never "no time"** | A locally planned route has no clock behind it, because a time needs a speed the app has never measured. The summary drops the third part and the screen says the distances are straight-line estimates. Rendering the zero as "0 s" — or inventing a speed to fill it — would be stating something never measured |
| **Never compare a road distance against a straight line as if they were the same measure** | A road distance is always ≥ the straight line. `RoadDistanceStrategy` mixes them deliberately when only one is known, and documents why: refusing to answer when one matrix leg failed would be worse |
| **A suggestion card must say WHY** | "Nearest to you" and "first in your list" are different claims. A card that shows the same words for both is overstating what the app knows. Every `SuggestionResult.Reason` maps to its own string |
| **Never let a network call run on every GPS fix** | The position updates every few seconds. Without a threshold the matrix allowance (500/day) is gone in under an hour. 250 m, and **distance not "has the fix changed"** — GPS jitters by a few metres on a table |
| **Check a service's limits BEFORE building on it** | The roadmap asks for this, and it was done before a line of Version 8: directions 2,000/day, matrix 500/day, optimization 500/day with **50 jobs and 3 vehicles**, 40 requests/minute |

### Completion traps (Versions 5 – 18)

| Trap | The rule |
| --- | --- |
| **The arrival radius must never be a gate on the completion button** | It was, twice, and it failed the same way both times: the button vanished — sometimes for good — for exactly the drivers who needed it. No fix under a roof, a signal that drops, a radius GPS jitter alone can explain, or simply having driven on before tapping. **"No delay, no signal, no button" is worse than the false completion the gate was built to prevent.** Store Details was fixed first (Version 16); the Navigation Active sheet was fixed on 2026-09-27. The user asked for it in their own words: *"kahit di na malapit … minsan humina pa signal ehh wala den"* |
| **The ONE gate is the confirmation dialog's YES** | Nothing in the app is counted without it. `CompletionConfirmDialogFragment` re-reads the store and re-checks its status before it writes, and `CompletionRepository.completeStore` is the only writer. Any button that opens that dialog is safe wherever it appears; a button that completes anything directly would not be |
| **The arrival banner reports; it does not gate** | It says arrived / approaching / outside / no fix. That is information for the driver, and the app never claims an arrival it cannot see. Do not let a banner's wording become a condition on what the user is allowed to do |
| **`NavigationMonitorService.arrivalNotified` is about the NOTIFICATION, not the gate** | It latches once so "You have arrived" is posted a single time, and `stopWatching()` then stops monitoring. It must not be read as "the store can no longer be completed" — it never meant that |
| **A stale comment is worse than no comment** | `StoreDetailBottomSheet`'s class javadoc said *"There is no completion action anywhere on this sheet, and that is a rule, not an omission"* for two versions after the button had been added. Anyone reading it would have concluded the feature did not exist. **When a rule is reversed, the prose that states it must be reversed in the same commit** |

### Saved plan traps (Version 18)

| Trap | The rule |
| --- | --- |
| **The plan tables already existed — do not write a migration** | `route_plans` and `route_plan_stops` were added in **schema version 3**, ahead of the feature, and `MIGRATION_2_3` creates them. Nothing about this feature changes the schema, so `DATABASE_VERSION` stays 3 and `app/schemas/3.json` is untouched |
| **A merge has three jobs and every one of them fails silently** | Leaving in a completed store sends the user back to a shop they already did. Dropping a newly added store makes them drive home believing the round is done. Not deduping a plan that lists a store twice drives to it twice. **No exception, no log, nothing odd on screen** — which is why `PlanMerge` is a pure class with 12 tests rather than six lines in a Fragment |
| **The unique index is `(planId, position)`, NOT `(planId, storeId)`** | A plan **can** list the same store twice. Do not assume the database prevents it — `PlanMerge` dedupes, first mention wins |
| **`replaceStops` must be delete-then-insert, in ONE transaction** | Updating rows one at a time collides with the unique `(planId, position)` index halfway through: moving stop 3 to position 1 is fine, but whatever is at position 1 must get out of the way first. And between the delete and the insert the plan has **no stops** — a crash landing there leaves a plan that describes nothing |
| **`route_plan_stops.storeId` CASCADEs; `completion_history.storeId` RESTRICTs** | Opposite choices, both deliberate. A completion is a record of something that happened and must never be lost; a stop in a plan is an instruction about the future and stops being true the moment the store is gone |
| **Never render a stored sequence number** | The number is the row's **position**. A sequence carried in the data has to be rewritten on every move, and a list reading `#1, #2, #2` after a drag is worse than one reading nothing |
| **Hide the leg distances while a row is being dragged** | A leg describes the step from the stop before it. The moment a row is picked up, every leg under it describes a journey that is no longer on screen. Showing a stale figure that looks current is the quiet wrong answer this project avoids |
| **A default name must be generated on the disk thread** | `Plan 1`, `Plan 2`… needs `countPlansInRound`, which is a database read. Counting on the main thread is a read in a place that must never do one, and generating the name in the UI lets two plans take the same default |
| **Two `showSnack(int)` helpers in one class do not compile** | It happened in 18.1 and the compiler caught it. One pair: `showSnack(int)` delegating to `showSnack(int, Object)` |
| **`OptimizationState.IDLE` is not a failure** | The route screen is reached before its first request finishes, so an observer firing on `IDLE` and rendering `messageFor(IDLE)` would flash **"could not build a route" on every single open** — on a screen that always has a route to show. Treat `IDLE` and `LOADING` as "nothing to report yet" |
| **Restore which plan was chosen, not the order** | A rotation must not silently switch the user to the newest plan. Only the plan id is saved in the instance state; the order reloads from the database, and an arrangement that was never saved is correctly gone — Save is the only thing that makes it real |

### Honesty traps (learned 2026-09-27)

| Trap | The rule |
| --- | --- |
| **A working sign-in button is not a working sync** | Firebase **Auth** has been built since Version 14; Firestore **sync** has not, and `AlphaRouteApp` still constructs `NoOpSyncManager`. The two sit next to each other on the Settings screen, so the visible, working half made the user believe the invisible half existed. **They asked why their deny-all Firestore rules were not blocking their saves — the rules were not blocking anything, because nothing was being sent.** If two features are adjacent in the UI and only one is built, the UI has to say so |
| **Never let a string promise a capability the code does not have** | The account card said *"Sign in to sync your stores and history to your Google account"* and the sign-out confirmation said *"You will stop syncing to your Google account"* — both false, for versions. A user reading them would conclude their data was in the cloud. **Grep the user-facing strings for verbs the code cannot honour**, and fix the words in the same change that removes the capability |
| **"Where did my data go?" is a question the app must never make the user ask** | The account card now carries a permanent **sync status line** — *"Sync: not set up yet. Everything is saved on this phone."* It is the honest answer today, and it is where a real transport reports last-pushed / last-pulled / the reason it failed. **State where the data is, in the place the user looks for it** |
| **A deny-all Firestore rule is the correct default, not a mistake to fix** | `allow read, write: if false` is right while nothing writes. Loosening it to `users/{uid}` would change nothing (no code writes) and would imply a feature that does not exist. Leave it until the transport is built |

### Bottom sheet traps (Version 19)

| Trap | The rule |
| --- | --- |
| ⚠️ **Inside a bottom sheet, an unstyled `MaterialButton` is a TEXT BUTTON** | `bottomSheetDialogTheme` → `ThemeOverlay.Material3.BottomSheetDialog` → `Base.V14...` → `Base.ThemeOverlay.Material3.Dialog`, and **that last one sets `materialButtonStyle = Widget.Material3.Button.TextButton.Dialog.Flush`** — Material's own value, for dialogs whose actions are text. So a bare `<MaterialButton>` that was meant to be the sheet's primary action renders as plain text with no container. **Five sheets had it**: `btn_store_save`, `btn_place_link_continue`, `btn_nav_confirm`, `btn_nav_open_maps`, `btn_route_preview_start`. Every button works; it just does not look like a button, which is why nobody noticed. **Every `MaterialButton` in a `sheet_*.xml` must name its style.** Verified by reading `material-1.14.0.aar`, not inferred from a screenshot |
| **A label that cannot fit its half of a row will wrap, and a wrapped button looks broken** | "Save to cloud" and "Retrieve from cloud" were content-sized in a `gravity="end"` row. On a 360dp screen the two labels plus padding need more than the ~312dp available, so the longer one was squeezed and broke across two lines. Fixed three ways at once, because any one alone leaves it reachable: **`layout_weight="1"`** on both so neither can exceed its half, **one-word labels** so a half is enough, and **an icon** to carry the word that was dropped. `maxLines="1"` as the backstop |
| **Do not print the same string on two lines** | A Google account with no display name makes `AuthUser.label()` fall back to the email — and the account card drew the label *and* the email, so the address appeared twice, one line under the other. Two identical lines read as a rendering bug, not as a missing name. **When a fallback value equals the value it is standing in for, draw one of them** |
| **A stack of label-above-value pairs is not a detail view** | Five fields, each a small grey caption over a line of value with 12dp between, came to roughly half a screen of very sparse text. The key-value list — **label left, value right, hairline between rows** — says the same five things in about half the height, and lets the eye scan one column of values instead of hunting for where each one starts. The label column needs a `minWidth` so the values line up |

### The trap that cost three bugs in one afternoon (Version 11)

| Trap | The rule |
| --- | --- |
| **`LiveData.getValue()` is `null` until something observes it** | **Never read a repository's LiveData from a screen that does not observe it.** It *appears* to work, because another screen usually observed it first — so it fails only in the case that is hardest to notice (process death with the back stack rebuilt, or a first render arriving from a different source). Three real bugs came from this: the route screen claiming "nothing left to visit" with a full round outstanding, and the Dashboard offering to set a Home that was already set. **Use a one-shot read** (`findAllNow`, `getNow`) or ask the object that does the observing (a ViewModel). |
| **A read that depends on another screen having run is not a read** | If correctness relies on the Dashboard having been opened first, the code is wrong even when it passes a manual test. |
| ⚠️ **A Room `LiveData` query returns a NEW LiveData on EVERY call — so `getValue()` through the accessor is null even when the screen DOES observe** | **The fourth instance of the trap above, found 2026-09-27, and the worst-behaved.** Room generates every `LiveData`-returning DAO method as `getInvalidationTracker().createLiveData(...)`, which **builds a new object each time it is called**. So `repo.observeAll().observe(...)` and `repo.observeAll().getValue()` are **two different LiveData**, and the second has never been observed. `SettingsFragment.refreshRound()` did exactly that with all three of its queries, so `RoundCalculator.compute(null, null, 1)` returned `RoundProgress(1, 0, 0)` — and the Settings Round panel read **"Round 1 in progress · 0 of 0 stores visited"** with "Start next round" permanently disabled, **for ever, with any number of stores**. **The fix: capture the instance once into a field, then observe *and* read that same field.** Note `LocationProvider.currentFix()` already does this correctly (it returns a field), which is why the location reads that look identical are fine — check which of the two shapes an accessor is before trusting a `getValue()` on it |

### Glassmorphism traps (Version 11)

| Trap | The rule |
| --- | --- |
| **A screen root must be TRANSPARENT for glass to work** | The gradient is `android:windowBackground`. An opaque root covers it and every translucent card silently becomes a flat panel with a faint tint — nothing looks broken enough to explain why |
| **Fade a blob to a zero-alpha version of its own hue** | Not to `@android:color/transparent`, which is transparent **black**: fading blue to it passes through grey and leaves a dirty smudge around the edge |
| **A bottom sheet rounds at the TOP only** | Its bottom edge is off-screen; rounding it leaves two slivers of the screen visible through the gap, which reads as a rendering fault |
| **Elevation 0 under a translucent pane** | A hard drop shadow under frosted glass looks wrong, and Material's elevation tint would grey the fill |
| **Never put a second declaration in `values-night/themes.xml`** | Unchanged since Phase 2.1, and it now also applies to the new sheet and dialog styles — a duplicate there wins in dark mode and does not carry the colour items |

### Backup and import traps (Versions 9.1 – 9.2)

| Trap | The rule |
| --- | --- |
| **A backup must reference stores by `storeCode`, never by row id** | Ids are auto-generated, so a backup's id 7 means nothing in the database it is restored into |
| **Write a backup from an explicit field list** | Serialising "whatever is in memory" means a column added later silently starts appearing in backups. `BackupValidatorTest` asserts the file contains no `apikey`, `token`, `password` or `authorization` |
| **`lastSelectedStoreId` must never be exported** | It is a transient reference to a drive in progress; restoring it into a different database could point at a different store |
| **`REPLACE` must delete completions BEFORE stores** | The foreign key uses `RESTRICT` (AD-6), so SQLite refuses to delete a store that still has history — the other order fails on the first store |
| **Validate BEFORE writing, and report EVERY problem** | A user with three faults should not have to fix one, retry, and discover the next. And a file must never be partially applied while it is still being judged |
| **A `MERGE` keeps the app's copy of a store it already has** | The app's version may have been completed or edited since the backup; overwriting it with the older version would silently undo that |

---

## 9. HOW TO BUILD AND VERIFY

```bash
cd "C:/Users/Admin/OneDrive/Documents/custom system/AlphaRoute"

# Full verification: JVM unit tests + APK
./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain
```

Expected: `BUILD SUCCESSFUL`, **512 tests, 0 failures**.

Useful checks after a build:

```bash
# Test results, per class
python -c "
import glob, xml.etree.ElementTree as ET
for f in sorted(glob.glob('app/build/test-results/testDebugUnitTest/*.xml')):
    r = ET.parse(f).getroot()
    print(r.get('name'), r.get('tests'), 'tests,', r.get('failures'), 'failures')
"

# Permissions actually merged into the manifest
grep -o '<uses-permission[^>]*>' \
  app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml

# The schema export must contain BOTH 1.json and 2.json
ls app/schemas/com.cph.alpharoute.data.db.AppDatabase/

# Kotlin must be zero
find . -name "*.kt" -not -path "./build/*" -not -path "./.gradle/*" | wc -l
```

**Always run the build before reporting a phase complete.** Two phases were caught this way — Phase 3.1's
`IConfigurationProvider` error, and Phase 3.4's accidental deletion of a code line.

---

## 10. THE 20-SECTION RESPONSE FORMAT (required for every phase)

1. CURRENT VERSION AND PHASE
2. OBJECTIVE
3. UNDERSTANDING CHECKLIST
4. FEATURES INCLUDED
5. FEATURES NOT INCLUDED YET
6. ASSUMPTIONS
7. FILES TO CREATE
8. FILES TO MODIFY
9. DEPENDENCIES
10. PERMISSIONS AND MANIFEST CHANGES
11. SETUP INSTRUCTIONS
12. COMPLETE CODE
13. CODE EXPLANATION
14. DATA FLOW FOR THIS PHASE
15. MANUAL TEST PROCEDURE
16. EXPECTED RESULT
17. COMMON ERRORS AND FIXES
18. ACCEPTANCE CHECKLIST
19. UPDATED PROJECT STATE
20. STOP POINT

Every phase must end with:

> **Phase complete. Do not proceed to the next phase until all acceptance criteria pass.**

And each phase writes its own record to `docs/PHASE_<n>_<NAME>.md` containing the same 20 sections.

---

## 11. REMAINING ROADMAP

### Version 3 — Map and Manual Pins ✅ **COMPLETE** (code complete 2026-09-25, awaiting on-device tests)

| Phase | What it must deliver | Key facts to reuse |
| --- | --- | --- |
| **3.5 Store details bottom sheet** ✅ **DONE** | Delivered: marker tap → `sheet_store_detail.xml` with a status chip, route number, store code, store name, area and coordinates, plus completion date, time and daily order for a completed store. Edit and Delete were added in 3.6 | The tap is resolved from `marker.getRelatedObject()`; the store is re-read with `StoreRepository.findById` |
| **3.6 Edit and delete store** ✅ **DONE** | Delivered: edit mode on `AddEditStoreBottomSheet` (re-reads the store by id, prefills only when there is no saved state), Delete behind a confirmation dialog, Delete disabled with a caption for a completed store, and `StoreRepository.update` hardened so an edit can never change `status` / `createdAt` / `completedAt` | 8 files. **The `@Update` trap is in §8** — read it before touching `update` |
| **3.7 Home pin** ✅ **DONE** | Delivered: `HomeSetupFragment` (a **pushed** screen — the bottom nav is hidden while it is open), `ic_marker_home`, `MapMarkerController.renderHome`, Home drawn from the settings row on the Map tab, a **real Settings screen** (Home + arrival radius + version), and `ArrivalRadiusValidator` + 16 tests. `placeholder_settings` deleted | 15 files. **One deviation: there is no "Select on map" button** — the map is tappable instead. Phase record §13.2 |
| **3.8 Pin from a shared link** ✅ **DONE** | Delivered: `GoogleMapsLinkParser` (pure, 28 tests — **prefers `!3d`/`!4d` over `@`**), `PlaceLinkRepository` (follows a short link by hand), `AddFromLinkBottomSheet`, a Map tab link button, and the map centring on the imported point. `RoutePoint` renamed `GeoCoordinate` | 22 files. **The Add Store form did not change** — it already takes a lat/lng. **⚠️ The short-link path is reasoned, not proven** — it needs a real share link to confirm |

### Version 4 — In-App Navigation (hand-off) — **in progress**

| Phase | What it must deliver |
| --- | --- |
| **4.1 Routing service preparation** ✅ **DONE** | Delivered: `local.properties` → `BuildConfig.ORS_API_KEY` → `RoutingConfig` (the only reader), 12 tests, and the API's endpoint + auth verified against the live service. **The key appears nowhere under `app/src`.** The real key is configured and a live route was fetched — **HTTP 200, 1734 m, 31 geometry points** | `RoutingConfig.directionsUrl()` and `AUTHORIZATION_HEADER` are ready for 4.2. The key is **120 characters, not hex** |
| **4.2 Route preview** ✅ **DONE** | Delivered: `RoutingRepository` (the first network call in the project), `RouteResponseParser`, `RouteState`, the polyline in `MapMarkerController`, `RouteTextFormatter`, `sheet_route_preview.xml` + `RoutePreviewBottomSheet`, and **Get directions** on the Store Details sheet. **Start navigation and the `<queries>` element were built here rather than in 4.3** — the screen plan's own §13.8 lists Start Navigation as screen 7's primary button, and a preview with no way to act on it is a dead button | 26 files, 44 new tests. **No new permission, no schema change.** The coordinate-order and `<queries>` traps are in §8 |
| **4.3 Navigation hand-off + arrival monitoring** ✅ **DONE** (build verified; **device test outstanding**) | Delivered: `DistanceCalculator` + `ArrivalEvaluator` + `ArrivalState` (pure Java, AD-14 — **the accuracy circle widens the radius**, so arrival is only claimed when the worst plausible position is inside), `NavigationSession` + `Snapshot`, `NavigationSessionManager` (the only writer of the session **and** the service), `NavigationMonitorService` (foreground, `foregroundServiceType="location"`), `NavigationNotifications` (two ids — an undismissable ongoing one and a dismissible arrival one), `NavigationBootReceiver`, `NavigationActiveBottomSheet` + `sheet_navigation_active.xml` (screen 8), the compact nav bar on the Map tab, and `lastSelectedStoreId` finally written and cleared | 21 files, **46 new tests** (262 total). **4 new permissions, no schema change, zero Kotlin.** **⚠️ A session cannot resume after a reboot** — Android 12+ forbids a background foreground-service start, so the boot receiver *clears* it. `ACCESS_BACKGROUND_LOCATION` is still deliberately absent |
| **4.4 State recovery** | Backgrounded, rotation, GPS loss, network loss, cancellation, reopen |

### Version 5 — Arrival and Completion — **COMPLETE** (code complete 2026-09-27, awaiting on-device tests)

| Phase | What it must deliver |
| --- | --- |
| **5.1 Arrival-radius check** ✅ **DELIVERED BY 4.3 — DO NOT RE-IMPLEMENT** | `ArrivalEvaluator` (pure, AD-14) already implements every state this row asks for: `OUTSIDE` / `APPROACHING` / `ARRIVED` / `NO_FIX` / `NO_DESTINATION`, with the accuracy and staleness thresholds. `SettingsRepository.getArrivalRadius()` is already the single reader of the configured radius. **Building a second `ArrivalChecker` would create a second definition of the arrival rule, free to disagree with the first.** 4.3 also delivered the equivalent of `LocationFix.isAccurateEnough` / `isStale` inside the evaluator's refusals |
| **5.2 Completion dialog** ✅ **DONE** | Delivered: `dialog_completion_confirm.xml` + `CompletionConfirmDialogFragment` (a **`DialogFragment`**, not a bottom sheet — a sheet can be swiped away and this one must not be), showing "Store completed?" with the route number, code, name, area and the arrival distance, NO / YES, both buttons disabled with a progress bar while the write runs, and a rotation mid-write resolved by **re-reading Room** rather than by trusting a saved flag | 15 files with 5.3. **The three error states are exactly the screen plan's**: *Already counted* · *Could not save the completion. Nothing was changed. Try again.* · *That store no longer exists.* |
| **5.3 Atomic completion transaction** ✅ **DONE** | Delivered: `CompletionTransactionDao` implementing the `PHASE_0.2_ARCHITECTURE.md` §13.7 contract step for step — re-read the store inside the transaction, refuse COMPLETED, refuse an existing history row, `DateKeyHelper.keyOf(now, zone)`, `IFNULL(MAX(dailySequence),0)+1`, insert, targeted `UPDATE`, throw-to-rollback. `CompletionOutcome` reports `SUCCESS` / `ALREADY_COUNTED` / `STORE_NOT_FOUND` / `FAILED`. **`CompletionRepository` gains its write path here — and still has no plain insert** | **The only write in the app that moves a counter.** `DateKeyHelper` is new (the roadmap assumed it existed) + 23 tests. **No new permission, no manifest change, no schema change** — a DAO is not a schema change. See §8 on `@Transaction` covering one DAO only |

### Version 6 — Tracker and History — **COMPLETE** (code complete 2026-09-27, awaiting on-device tests)

| Phase | What it must deliver |
| --- | --- |
| **6.1 Live tracker** ✅ **DONE** | Delivered: `TrackerCalculator` + `TrackerSnapshot` (pure, 19 tests, one pass over both tables, the `StoreLike`/`CompletionLike` seams so `domain/` never imports Room), `DashboardViewModel` with `MediatorLiveData`, the Dashboard wired to Room, the **7th card (Total stores)**. **The placeholder values, the notice card and all six `dashboard_value_*` strings were DELETED**, not hidden | The percentage is `0.0%` on an empty database, "remaining" is clamped at zero, and both are asserted. **No count is stored anywhere** (AD-23) |
| **6.2 Daily history list** ✅ **DONE** | Delivered: `fragment_history.xml`, `item_history_date_group.xml`, `HistoryAdapter`, `HistoryFragment` — one card per day, newest first, "25 Sep 2026 · 3 stores". `HistoryGrouper` is pure with 12 tests. **`placeholder_history` is deleted** | The count comes from the rows, never a stored total. Days sort **newest first** — asserted across a month and a year boundary |
| **6.3 Daily completion order** ✅ **DONE** | Delivered: `fragment_daily_history.xml`, `item_history_row.xml`, `DailyHistoryAdapter`, `DailyHistoryFragment` — the day's stores with a summary header, joined with the store list through a `MediatorLiveData` | Rows sort by **`dailySequence` ASC** — the DAO's `ORDER BY` is the single definition of that order. The header date is formatted from the stored key |
| **6.4 Completion details** ✅ **DONE** | Delivered: `fragment_completion_detail.xml`, `CompletionDetailFragment` — store details, completion date, time, daily sequence, coordinates, a **non-interactive map preview** with the store's own marker, and **View on Map** | The marker is green because `MapMarkerController` picks the icon from the store's status. **View on Map** pops to the tabs and reuses the pasted-link channel to move the camera |
| **6.5 Consistency check** ✅ **DONE** | Delivered: `ConsistencyChecker` (pure, 15 tests) + a card in Settings that runs it on demand. Four faults: a store COMPLETED with no history, a history row for a PENDING store, an orphaned history row, and a marker-count mismatch | **It reports and never repairs** (AD-25). An unknown marker count is **skipped**, not guessed — a false alarm in a checker teaches the user to ignore it |

### Version 7 — Rounds, undo, and the next-store suggestion

| Phase | What it must deliver |
| --- | --- |
| **7.0 Rounds, undo, at-store shortcut** ✅ **DONE** (build verified; **device test outstanding**) | Delivered: **the first schema migration in the project** (v1 → v2) — `completion_history.roundNumber` + `app_settings.currentRound`, with **AD-5 revised** to `UNIQUE(storeId, roundNumber)`. `RoundCalculator` + `RoundProgress` (pure, 14 tests), `RoundTransactionDao.startNewRound()` (re-checks completeness **inside** the transaction, throws to roll back), `CompletionTransactionDao.undo()` (one named row, current-round-aware), the at-store swap in Store Details (Get directions → "Store completed?" inside the arrival radius), the round-aware Dashboard (Round, Rounds finished, round-scoped Remaining and Completion), the Settings round panel, and a round-scoped consistency check | **28 files, +27 tests** (375 total). **No new permission, no manifest change.** ⚠️ **The migration must be device-tested first** — see §8's migration traps |
| **7.1 Simple nearest pending store** ✅ **DONE** | `RouteSuggestionStrategy` + `NearestStrategy` using **haversine**. Label it clearly as **approximate, not route optimisation** |
| **7.2 Recalculate after completion** ✅ **DONE** | Refresh DB, tracker, location, pending list; new suggestion; **wait for confirmation** |
| **7.3 Home-aware heuristic** ✅ **DONE** | `HomeAwareStrategy`. **Document the scoring formula.** Do not call it globally optimal. Allow manual override |
| **7.4 No pending stores** ✅ **DONE** | Remaining = 0, Percentage = 100%, **Route Complete**, daily summary, suggest Home |

### Version 8 — Full Road-Based Route Optimization

| Phase | What it must deliver |
| --- | --- |
| **8.1 Routing service review** ✅ **DONE** | Re-verify pricing and limits before implementing |
| **8.2 Road-based next store** ✅ **DONE** | Real driving distance; **fall back to straight-line** when the service fails. ⚠️ **Fixed 2026-09-27:** the matrix body sent `sources`/`destinations` as coordinates, so it answered 400 on every call and the fallback was the *only* path ever taken |
| **8.3 Multi-stop optimization** ✅ **DONE** | openrouteservice **Optimization (VROOM)**, 500/day, up to 50 stops. Origin = current location, final = **Home**. Every pending store exactly once. ⚠️ **Fixed 2026-09-27:** the URL had a spurious `/v2/` (404 on every request) and the totals were read from the wrong object. See the routing traps above |
| **8.3b Route order on the device** ✅ **DONE 2026-09-27** | **The service is no longer called by any screen.** `RouteOrderPlanner` orders the stops locally: nearest neighbour, then 2-opt refinement. Always answers, instantly, offline, free. Measured over real road distances on six fixtures of 11–16 stores: greedy alone **+12.5%** versus VROOM, greedy + refinement **+1.8%** — and it beat the solver on 3 of the 6. The `optimize()` service path is kept and documented, not deleted |
| **8.4 Complete route preview** ✅ **DONE** | Route line, ordered stores, all marker types, totals, **Start Route**, **Recalculate**, manual selection |

### Version 9 — Backup and Synchronization

| Phase | What it must deliver |
| --- | --- |
| **9.1 Local export** ✅ **DONE** | JSON/CSV of stores, coordinates, status, history, Home, settings. **No API keys, no credentials** |
| **9.2 Local import** ✅ **DONE** | Validate, preview, detect duplicates, confirm replace or merge, **transaction, roll back on failure** |
| **9.3 Optional cloud sync** | **Firebase was deferred here (D-9).** See §12 for what it requires |

### Version 10 — Testing and Release

| Phase | What it must deliver |
| --- | --- |
| **10.1 Error handling** ✅ **DONE** | 16 scenarios: no internet, no GPS, permission denied/permanently denied, map failure, navigation failure, empty DB, invalid coordinates, duplicate store, duplicate completion, DB failure, Home not set, no pending stores, restart during navigation, low accuracy, API quota |
| **10.2 Automated and manual testing** ✅ **DONE** | **Instrumented tests** for the DAOs, the completion transaction, and `MapMarkerController`. Plus the 14 critical field scenarios. **Replace `ExampleUnitTest` and `ExampleInstrumentedTest`** |
| **10.3 Release preparation** ✅ **DONE** | Remove test records, remove unsafe logs, **protect the API key**, review permissions (expect androidx's auto-added `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`), release build, **create a real app icon**, signed APK/AAB, user guide. Revisit the Gradle 10 deprecation warning |

### Version 18 — Saved Route Plans

> The tables have existed since **schema version 3**, added ahead of the feature. This is what uses them.
> **No migration was needed.** Full document: `docs/PHASE_18_ROUTE_PLANS.md`.

| Phase | What it must deliver |
| --- | --- |
| **18.1 Saved plans, full CRUD** ✅ **DONE 2026-09-27** | Drag-to-reorder on the route screen, a plan picker, and create / rename / delete of **named plans scoped to a round**. `PlanRepository` is the only writer; `PlanMerge` (pure, 12 tests) rejoins a saved order with what is still pending. Nothing is written until **Save** is pressed. 563 tests |
| **18.2 …** | Not started. See §5 of the phase document for what was deliberately left out |

### Version 19 — Manual Cloud Sync

> Two buttons, both manual, and the destructive one shows its numbers first. Full detail: §12.

| Phase | What it must deliver |
| --- | --- |
| **19.1 Save to cloud / Retrieve from cloud** ✅ **DONE 2026-09-27** | `CloudSyncRepository` + the Settings **Cloud** card. A save writes **one** Firestore document (`users/{uid}/cloud/backup`) built by `BackupRepository.buildBackupText`; a retrieve reads it and reuses the **same** validation, merge rule, transaction and rollback as the file import. `CloudSyncSummary` (pure, 13 tests) works out what a merge would add **before** anything is written. Sign-in required; empty pushes refused; one operation at a time. 576 tests |
| **19.2 Route plans in the backup** | The v1 format covers stores, completions and settings — **not plans**. Extending it to v2 touches `BackupCodec`, `BackupValidator`, `BackupContents` and `BackupTransactionDao.importAll`, which is the most dangerous code in the app. Wants its own phase |
| **19.3 Automatic sync** | Deliberately not built. See §12 for why the `SyncManager` seam is the wrong shape for this feature |

---

## 12. FIREBASE — Auth and MANUAL cloud sync are built. Automatic sync is not.

> ⚠️ **Updated 2026-09-27 (Version 19).** Two earlier versions of this section were wrong in opposite
> directions: first "nothing is built", then "Auth is built but sync is not". Read the three paragraphs
> below and nothing else.

**What is built.**
- **Auth (Version 14/15).** `firebase-auth`, a real `google-services.json`, `AuthRepository`, and a working
  Sign in with Google / Sign out card on Settings.
- **Manual cloud sync (Version 19).** `CloudSyncRepository` + the Settings **Cloud** card. Two buttons:
  **Save to cloud** and **Retrieve from cloud**. Nothing automatic.

**What is still NOT built.** The `SyncManager` seam is still `NoOpSyncManager`, and that is now a decision
rather than a gap — see below. There is no background transport and no automatic push.

### THE RULES — the file is `firestore.rules` in the project root

**Publish it.** Firebase console → Firestore Database → Rules → paste → Publish. The user's console
currently holds `allow read, write: if false` (a correct default while nothing wrote) and **that now
blocks both buttons with a permission error**.

The file is the single copy — do not paste a second version into a document, it will drift. Its shape:

```
match /users/{uid} {
  allow read: if isOwner(uid);
  allow create, update: if isOwner(uid);          // no delete: orphaning the subcollection
  match /{document=**} {
    allow read, create, update, delete: if isOwner(uid);
  }
}
```

**Version 14 wrote those rules and they were already correct** — the recursive `{document=**}` covers
`users/{uid}/cloud/backup`, so Version 19's sync needed **no permission change at all**. What Version 19
fixed was the *description*: the comments listed `stores/{storeId}`, `completions/{id}` and
`meta/settings`, a data shape that was never built. **A rules file that describes the wrong data is worse
than no comment**, because the next person reads it and believes it.

**Two facts about Firestore rules that the file states and that are easy to get wrong:**
1. **Rules are OR'd, not hierarchical.** A request is allowed if **any** matching `allow` is true — so a
   catch-all `allow read, write: if false;` **cannot veto** a more specific grant. It is documentation,
   not a safety net. The console starts you with one; it proves nothing.
2. **`function` declarations live inside `match /databases/{database}/documents`**, and are scoped to
   where they are defined. Both verified against Firebase's own docs on 2026-09-27.

### Where the data lives

```
users/{uid}/cloud/backup
  json              the whole backup document, exactly as the file export writes it
  formatVersion     1
  exportedAt        when the phone built it
  storeCount        \
  completionCount    > readable in the console without parsing the json
  updatedAt         /
  deviceLabel       which phone wrote it
```

The counts and timestamps are duplicated out of the json **on purpose** — they are what makes the document
legible in the console, which is how the user confirms a save actually happened. The `json` is the only
thing ever restored from.

### Why it is manual, and why it is not a SyncManager

The `SyncManager` seam fires on **every local write**, which is the right shape for a background transport
and the wrong shape for this one:

- A transport that runs while the user is driving can push a **half-finished round**, and nothing on screen
  says so.
- Saving and retrieving are **different intentions**. One "Sync" button would have to guess which was
  meant, and would guess wrong exactly when it mattered.
- The destructive direction has to be **visible** as destructive. Retrieve shows its numbers first.

### The safety features, in one list

| | |
| --- | --- |
| **Sign-in required** | Every operation refuses without a uid, and the buttons are disabled with the reason on screen |
| **A cloud save IS a backup** | It reuses `BackupRepository.buildBackupText` — there is no second definition of the app's data |
| **A cloud retrieve IS a restore** | It reuses `readAndValidate` + `BackupTransactionDao.importAll` — the same validation, merge rule, single transaction and rollback the file import already had |
| **Preview before writing** | Retrieve shows what the cloud holds and **what a merge would add**, worked out by the pure `CloudSyncSummary`, before anything changes |
| **Merge is the default, Replace is separate** | Merge keeps everything already here. Replace deletes first, sits behind a second confirmation, and is on a different button |
| **Push never deletes locally** | It only writes |
| **An empty database cannot be pushed** | `EMPTY` is refused rather than replacing a good cloud copy with nothing |
| **One operation at a time** | Both buttons go dead while one is in flight |
| **The status line tells the truth** | What the cloud holds and when it was written, or why it could not be read |

**Verified facts (2026-09-25):**
- Firebase **Spark plan needs no payment method** — compatible with D-2.
- Firestore free tier: 1 GiB storage, 50k reads/day, 20k writes/day, 20k deletes/day, 10 GiB/month egress.
  One save is **one write**; one retrieve is **one read**.

**Before the first push, confirm with the user:** syncing uploads store records, coordinates, completion
history, Home and route plans to Google's servers.

**Known gap:** the backup format (v1) covers **stores, completions and settings — NOT route plans**. A
cloud retrieve on a new phone therefore brings the round back but not the saved plans. Extending the format
to v2 with plans is the next step, and it touches `BackupCodec`, `BackupValidator`, `BackupContents` and
`BackupTransactionDao.importAll` — the most dangerous code in the app, so it wants its own phase.

---

## 13. THE LOCAL-FIRST WRITE PATTERN (do not change this)

```
Fragment / ViewModel
      │  repository.insert(store, callback)
      ▼
executors.diskIO().execute(...)          SINGLE thread
      │
      ├─ normalise (trim + coordinate check) → duplicate pre-checks → DAO write → COMMIT
      │
      ├─ notifySyncSafely()              sync hook, AFTER the commit, wrapped in try/catch
      │
      └─ postToMain(() -> callback.onResult(outcome))
```

**Four properties that must be preserved:**

1. **The network is not in the write path.** A write returns as soon as the local transaction commits.
2. **The sync hook fires after the commit**, never before.
3. **The sync hook is wrapped in a guard** — a sync bug must never turn a successful save into a visible
   failure.
4. **No screen observes sync state.** Every screen reads Room.

With no signal, the app is fully functional. That is the user's explicit requirement, and it is satisfied by
structure rather than by care.

---

## 14. OPEN ITEMS

| Item | Status |
| --- | --- |
| **Phases 3.1–3.8, 4.1–4.4, 5.2–5.3, 6.1–6.5, 7.0–10.3 on-device tests** | **Not reported by the user.** The builds are verified; the runtime behaviour is not. **Every phase from 3.1 onwards is code complete but has never been run on a device.** The four that matter most: **7.0 §15.1** (the migration), **7.1–10.3 §15.5** (the backup round trip), **4.3 §15.2 step 9** (arriving changes nothing) and **5.2+5.3 §15.1 step 4** (NO changes nothing at all) |
| **Store Details action row** | Get directions is now a full-width primary button above Edit · Delete · Close. The row is settled |
| **Route caching** | A route survives rotation (it lives in the repository) but not closing and reopening the preview. At 2,000 requests a day that is not a practical limit — worth revisiting only if the free tier is ever reduced |
| **The paste flow is not a share target yet** | Phase 3.8 built the parser and the paste screen; appearing in Google Maps' Share sheet is the same parser with intent plumbing, and deserves its own phase (cold start, warm start, rotation, and a tab that may not exist yet) |
| **The store name is not pre-filled from the share text** | A Google Maps share puts the place name on the first line — it is sitting right there and could fill the name field. Needs the Add Store form's constructor widened, which 3.8 deliberately did not touch |
| **⚠️ The short-link path is reasoned, not proven** | `maps.app.goo.gl` success could not be tested without a real share link. A bogus code returns 404, confirming the mechanism. **Test §15.4 of the 3.8 record first** |
| **Home Setup has no link import** | The parser would work there unchanged; the screen does not offer it |
| **`lastSelectedStoreId`** | ✅ **Now written.** Phase 4.3 sets it when navigation starts and clears it when navigation ends, on reboot, and when the store is deleted. **`ACTIVE_NAVIGATION_SESSION` in the original plan was never a column** — AD-10 maps it onto this one field, so no schema change was ever needed |
| **⚠️ A navigation session cannot resume after a reboot** | **Deliberate, not an oversight.** Android 12+ forbids starting a foreground service from a `BOOT_COMPLETED` receiver, so there is no legal way to restart the monitoring. `NavigationBootReceiver` **clears** the stale reference. Resuming after an ordinary process kill **is** possible, and Phase 4.4 built it |
| **`lastSelectedStoreId` is now offered back, with a 4 h window** | ✅ Phase 4.4. A leftover from more than four hours ago is cleared silently; anything fresh asks first. **The resume is always a tap** — the check cannot start a service (AD-17) |
| **`git init`** | **Still not done.** Strongly recommended — there are **two database schemas**, 105 Java sources and 512 tests with no version control. **`app/schemas/` must be committed, never ignored** — and now that a migration exists, `1.json` is as important as `2.json`: it is the starting point the migration is verified against |
| **The migration has never run on a device** | **The single highest-risk untested thing in the project.** Room validates the result at open, which no JVM test can reach. `2.json` matching the migration's SQL is strong evidence, not proof. **7.0 §15.1 is the first thing to test** |
| **`AD-5` is now `UNIQUE(storeId, roundNumber)`** | Revised in 7.0. A store can be completed once per round. **Anything that asks "is this store completed?" must be round-scoped** — a store with history from an old round and a PENDING status is the NORMAL state after a new round |
| **A new round deletes nothing** | It changes store statuses. Every completion row stays, which is what lets the History show past rounds. Do not "clean up" old rounds |
| **The undo is the only destructive operation on `completion_history`** | One row at a time, by row id, behind a confirmation. **Do not add a bulk delete or a "clear history"** — that would remove the only evidence of what happened, which is the thing the table exists for |
| **No placeholder screens or strings remain** | ✅ Version 6 deleted the last of them: `placeholder_history` and all six `dashboard_value_*` strings, plus the Dashboard's notice card. **Every screen now shows real data.** The `placeholder_settings` string went in Phase 3.7 |
| **`dashboard_value_*` are gone — do not re-add them** | If a card ever needs a "not loaded yet" state, it is a **loading state**, not an invented number. The screen shows a progress bar until Room's first emission, because "0 completed" is a claim about the data and would be false before the first read |
| **A null-skipping loop and `list.size()` disagree** | Caught by the tests in Version 6, in both `TrackerCalculator` and `ConsistencyChecker`: the totals used `size()` while the loops skipped nulls, so one null would have made the Dashboard's own figures contradict each other. **Count by walking whenever the loop skips** — see §8 |
| **The consistency check has no automatic run** | It is a button in Settings, deliberately: it reads both whole tables, and the answer changes only when the data does. The `MARKER_COUNT_MISMATCH` check is **skipped** there because Settings cannot see the Map tab's marker count — passing a zero would raise a false alarm on every run |
| **`DateKeyHelper` did not exist before 5.3** | The roadmap referenced it from Phase 2.1 onwards, but no such class was ever written — the date key had no implementation until the completion transaction needed one. It now exists, pure Java, 23 tests. **Anything that needs a local calendar day uses it; do not write a second one** |
| **`CompletionOutcome` carries the day and sequence** | So the success message can say "store #3 today" without a second query. The screen plan's wording depends on it — a bare boolean could not produce it |
| **The completion transaction has no instrumented test yet** | Room needs a device, so the §13.7 contract is verified by hand in 5.2+5.3 §15 until Phase 10.2 adds the instrumented suite. **That is the single largest remaining test gap in the project** |
| **Firebase** | Deferred to Version 9 (D-9) |
| **openrouteservice account** | ✅ **Created 2026-09-25.** The key is in `local.properties` and verified live — HTTP 200, a 1734 m route. **Nothing further is needed from the user for Version 4** |
| **App icon** | Still the Android Studio default. Phase 10.3 |
| **Trademark** | "Alfamart" is a third-party brand — fine for a private solo tool (D-5), may need permission if distributed |
| **Unused strings** | None. `app_tagline` and `app_version_format` were put to use in Phase 3.7's Settings → About |
| **Placeholder strings remaining** | Only `placeholder_history` (delete in 6.2). `placeholder_settings` was deleted in Phase 3.7 |
| **Home Setup has no "Select on map" button** | Deliberate — the map is tappable, so the button had nothing to do. Phase record §13.2. The alternative reading ("put Home at the centre of the view") is ~8 lines if wanted |
| **Home cannot be cleared** | `SettingsRepository.clearHome()` exists and works but nothing calls it. Home is where the route finishes, so "no Home" is not a state this app designs around |

---

## 15. QUICK START FOR THE NEW CONVERSATION

Paste this:

> Continue the AlphaRoute project. Read `docs/HANDOFF.md` first — it has the full state, the frozen stack,
> all architecture decisions, the schema, every accumulated trap, and the remaining roadmap. Then read
> `docs/PHASE_7.1_TO_10.3_SUGGESTION_TO_RELEASE.md` for the last completed batch, and
> `docs/PHASE_7.0_ROUNDS_UNDO_AND_AT_STORE.md` for the schema migration. **Both §20 sections say the device
> tests have not been run yet.**
>
> **The roadmap is complete.** The only unbuilt item is **9.3, Firebase cloud sync**, deferred by decision
> D-9 — see §12 for what it needs before any data is pushed.
>
> Project root: `C:/Users/Admin/OneDrive/Documents/custom system/AlphaRoute`
> **Next: nothing to build. The work left is testing.** In priority order:
> 1. **7.0 §15.1** — the migration, on a build that already has data. **The only change that can destroy
>    real data, and the only one no JVM test can reach.**
> 2. **7.1–10.3 §15.5** — the backup round trip. An export that cannot be restored is worse than none,
>    because it looks like a safety net that is not there.
> 3. **4.3 §15.2 step 9** and **5.2+5.3 §15.1 step 4** — arriving changes nothing; NO changes nothing.
> 4. The device tests for 3.1–6.5.
>
> Follow my rules: Java only, XML layouts, one phase at a time, stop after each phase for testing, never
> invent data, never auto-complete a store, never auto-start navigation, and use the 20-section response
> format. Run the build before reporting a phase complete.

### The one-line status

> **Every version is complete. The app pins Alfamart stores on an OSM map, hands navigation to Google Maps,
> watches the position in a foreground service until the driver crosses the arrival radius, records a
> completion on an explicit YES, works in ROUNDS, shows a live Dashboard and a day-by-day History, suggests
> the next store with an honest reason, plans the whole route with real driving distances, and can export
> and restore its own data. Database v2 (one migration), 512 JVM tests plus an instrumented suite for the
> completion transaction, zero Kotlin, $0 stack. **What is left is testing, not building** — and 7.0 §15.1
> (the migration) is the first thing to do.**

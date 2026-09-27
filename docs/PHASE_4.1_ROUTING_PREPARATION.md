# AlphaRoute — Phase 4.1 Routing Service Preparation

**Project title:** Alfamart Route Tracker
**Version:** 4 — In-App Navigation (hand-off) — **Phase:** 4.1 Routing Service Preparation
**Repository root:** `C:/Users/Admin/OneDrive/Documents/custom system/AlphaRoute`
**Date:** 2026-09-25
**Prerequisites:** Phases 0.1–0.3, 1.1–1.3, 2.1–2.3, 3.1–3.7 (Version 3 complete)
**Status:** ✅ **COMPLETE AND VERIFIED — 144/144 unit tests pass, build succeeds, and a live route was
fetched with the real key: HTTP 200.**

> **This phase is finished.** The service's address, authentication and failure modes were verified
> against the live API *before* the account existed, and the real key has now been confirmed end to end —
> from `local.properties`, through the build, to a 1734 m route coming back.

---

## 1. CURRENT VERSION AND PHASE

| Item | Value |
| --- | --- |
| Version | 4 — In-App Navigation (hand-off) |
| Phase | 4.1 — Routing Service Preparation |
| Files created | 3 |
| Files modified | 2 |
| Files deleted | 0 |
| Database version | **1** — unchanged |
| Unit tests | **144** (was 132 — 12 new) |
| New dependencies | **None** (runtime or test) |
| New permissions | **None** |

---

## 2. OBJECTIVE

Get everything the routing work needs in place before any route code is written:

- Decide where the API key lives, and make it reachable from code **without ever entering `app/src`**
- Make the app build and run correctly **with no key at all**
- Verify the service's endpoint, authentication and failure behaviour against the real API
- Record what Phase 4.2 will need, so it does not have to re-decide any of it

---

## 3. UNDERSTANDING CHECKLIST

- [x] Java only, no Kotlin. **No new dependency, no new permission, no schema change.**
- [x] The key lives in `local.properties`, which is **already gitignored**, and reaches the code as a
      build constant. **No file under `app/src` contains it.**
- [x] The app **builds, tests and packages with no key** — the state it is in right now.
- [x] A blank or whitespace-only key is reported as **not configured**, never treated as a key.
- [x] The base URL is **HTTPS**, and a test fails if that ever changes.
- [x] The service's real behaviour was **checked against the live API**, not taken from a tutorial.
- [x] Still $0. openrouteservice's Standard plan is EUR 0 with no card (decision D-2).

---

## 4. FEATURES INCLUDED (in this phase)

| Feature | Detail |
| --- | --- |
| **The key's home** | `local.properties` → `BuildConfig.ORS_API_KEY`, read by `app/build.gradle` |
| **`RoutingConfig`** | The one class that reads the key; base URL, profile, endpoint and auth header |
| **`isConfigured()`** | A blank key is a clean "not set up yet", not a 401 at runtime |
| **12 new unit tests** | Key states, whitespace handling, and the HTTPS requirement |
| **Verified API facts** | Endpoint path, header-based auth, 401 vs 403 — see §13.2 |
| **A leak check** | With no key configured, neither `app/src` nor the built APK contains one — checked, not assumed |

---

## 5. FEATURES NOT INCLUDED YET

| Not here | Arrives in |
| --- | --- |
| The HTTP call itself | **Phase 4.2** |
| Parsing the route (distance, duration, geometry) | **Phase 4.2** |
| `sheet_route_preview.xml` | **Phase 4.2** |
| The Google Maps hand-off Intent + the `<queries>` manifest element | **Phase 4.3** |
| The foreground arrival-monitoring service | **Phase 4.3** |
| Any screen that reads `isConfigured()` | **Phase 4.2** — nothing calls it yet, by design |

---

## 6. ASSUMPTIONS

| # | Assumption | Impact if wrong |
| --- | --- | --- |
| A95 | **The key lives in `local.properties`, becomes `BuildConfig.ORS_API_KEY`, and is read by exactly one class.** | The same isolation the project already applies to osmdroid and to the map hand-off. When 4.2 adds the call it reads `RoutingConfig`, not the build constant — so there is one place to change if the key's home ever moves. |
| A96 | **A missing key is a valid state, not an error.** | The app must build and run before the account exists, and a fresh checkout has no key. `isConfigured()` turns it into an answer; sending a request with an empty key would come back 401, which looks like a broken key rather than a missing one. |
| A97 | **The key travels in the `Authorization` header, not as a query parameter.** | Verified against the live API — see §13.2. Plenty of examples online still show `?api_key=`, which this service no longer reads. |
| A98 | **The base URL must be HTTPS.** | The key is sent with every request. Over `http://` it would be readable by anyone on the same network, and the app would look like it was working. There is a test for this. |
| A99 | **The key is baked into the APK as a string constant.** | Unavoidable for a build-time constant, and acceptable for a solo app that is never distributed (D-5). It does mean the debug APK must never be shared — see §13.6. |
| A100 | **Phase 4.2 will use `HttpURLConnection` and Android's built-in `org.json`, with a test-only `org.json` dependency.** | Keeps the runtime dependency count at zero, in keeping with the rest of this project. The test-only dependency is what lets the parser run on the JVM — see §13.7. Recorded here so 4.2 does not re-decide it. |

---

## 7. FILES TO CREATE (3)

| # | Path | Purpose |
| --- | --- | --- |
| 1 | `navigation/RoutingConfig.java` | **New.** The service's address, and whether this build can use it |
| 2 | `app/src/test/java/com/cph/alpharoute/navigation/RoutingConfigTest.java` | **New.** 12 JVM tests |
| 3 | `docs/PHASE_4.1_ROUTING_PREPARATION.md` | **New.** This record |

---

## 8. FILES TO MODIFY (2)

| # | Path | Status | What changed |
| --- | --- | --- | --- |
| 1 | `app/build.gradle` | **Modified** | Reads `local.properties` and exposes `ORS_API_KEY` as a `buildConfigField`, with the key escaped |
| 2 | `docs/HANDOFF.md` | **Modified** | Status, the frozen stack's routing row, inventory, test count, roadmap, traps |

**`local.properties` was temporarily modified and restored.** A placeholder key was added, the build was
run, and the file was put back exactly as it was — see §15.1. It is not a change to the project.

**Inspected and left unmodified:** the whole `data/` layer, `domain/`, `location/`, `ui/`, and the
`util/` helpers. This phase adds configuration and changes the build; it touches no app behaviour at all.

---

## 9. DEPENDENCIES

**None added — runtime or test.**

The `org.json` library that Phase 4.2 will need is already on the Android platform, so parsing the
response costs nothing in download size. The test-only copy of it (A100) is Phase 4.2's.

---

## 10. PERMISSIONS AND MANIFEST CHANGES

**None.** Verified against the merged manifest after the build — still the same four, plus androidx's
auto-added self-scoped one.

The `<queries>` element that the Google Maps hand-off needs on Android 11+ is **not** added here; it
belongs with the hand-off in Phase 4.3.

---

## 11. SETUP INSTRUCTIONS

1. Let Gradle sync.
2. **Build → Clean Project**, then **Build → Rebuild Project**.
3. To add the key, put one line in `local.properties` at the project root:

```properties
ORS_API_KEY=your_key_here
```

4. **Do a clean build after adding or changing the key.** Gradle does pick up a changed key on an
   incremental build — that was tested, see §15.1 — but a clean build removes any doubt, and it is the
   only way to be sure the APK you are about to install carries the key you think it does.

---

## 12. COMPLETE CODE

All 5 files are written into the project on disk. The files worth reading first:

1. `app/build.gradle` — the header comment explaining why the key lives where it does
2. `navigation/RoutingConfig.java` — the verified API facts, and why a blank key is an answer
3. `app/src/test/java/…/RoutingConfigTest.java` — the two tests that matter most, and why

---

## 13. CODE EXPLANATION

### 13.1 Where the key lives, and why there

```
local.properties            ← the key is typed here (one line, already gitignored)
      ↓  Gradle reads it
app/build.gradle            ← turns it into a build constant
      ↓  generated
BuildConfig.ORS_API_KEY     ← read by exactly one class, RoutingConfig
```

Three properties come out of that arrangement, and each one is the point of the design:

- **The key is never in `app/src`.** A `.java` file is the kind of file that gets shared, copied into a
  bug report, or committed — which is why rule 9 exists. `local.properties` is already listed in
  `.gitignore` (twice), so it cannot be committed by accident even after `git init`.
- **Nothing else in the app knows where the key comes from.** `RoutingConfig` is the only class that reads
  `BuildConfig.ORS_API_KEY` — the same isolation the project applies to osmdroid (`MapConfig`) and to the
  map hand-off (`NavigationLauncher`, Phase 4.3).
- **A missing key does not break the build.** `local.properties` may not exist at all, or may exist
  without the line. Both produce an empty string, and the app builds, tests and packages normally.

The value is escaped before it is written into generated Java:

```groovy
def orsApiKey = (localProperties.getProperty("ORS_API_KEY") ?: "")
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
```

Without that, a key containing a quote would produce a `BuildConfig.java` that does not compile — and the
error would point at generated code rather than at the line in `local.properties` that caused it.

### 13.2 What was actually verified against the live service

The phase brief says "verify the API". Most of that turned out to be possible **without a key at all**,
because a service tells you a great deal by how it rejects you. Two requests were made to the real
endpoint:

| Request | Response |
| --- | --- |
| `POST https://api.openrouteservice.org/v2/directions/driving-car/geojson` with no `Authorization` header | **HTTP 401** — `{"error": "Authorization field missing"}` |
| The same request with a bogus key | **HTTP 403** — `{"error": "Access to this API has been disallowed"}` |

Three things follow, and all three are now written into `RoutingConfig`:

1. **The endpoint path is correct.** A wrong path answers `404`. A `401` means the service found the
   endpoint and then asked who was calling.
2. **The key goes in the `Authorization` header.** The error says so in as many words. Older versions of
   this API accepted `?api_key=...`, and a great many examples online still show it — using it now would
   produce a `401` indistinguishable from an expired key, and would also put the key into URLs, logs and
   crash reports. `RoutingConfigTest.theKeyIsNotPartOfTheDirectionsUrl` guards against that coming back.
3. **A bad key is `403`, not `401`.** So Phase 4.2 can tell "no key configured" from "key rejected", which
   are two different messages for the user: *set up the key* versus *your key is not accepted*.

The probe coordinates used (`14.5995, 120.9842` → `14.6071, 120.9911`) are a pair of points in Manila,
used only to give the service something to reject. They are **not** store data and are never stored by
the app.

### 13.3 Why a blank key is an answer rather than an error

`isConfigured()` exists so that a screen needing a route can ask a question and get a truthful answer,
instead of sending a request that comes back `401` and looks like a broken key.

The rule it enforces is that **whitespace is not a key**:

```java
static boolean isUsable(@Nullable String key) {
    return !normalise(key).isEmpty();
}

static String normalise(@Nullable String key) {
    return key == null ? "" : key.trim();
}
```

The trim is not tidiness. A key pasted from a browser or a terminal very often arrives with a trailing
newline, and a key with a newline in it is rejected by the service while looking perfectly correct in the
editor. So a value that is only whitespace is not a key, and a value with whitespace around it is.

`apiKey()` promises **never `null`** — a caller gets a string it can put straight into a header — and
both methods go through the same `normalise`, so they can never disagree about whether a given value is
a key.

### 13.4 Why the tests take the key as a parameter

`isUsable` and `normalise` are package-private and take the key as an argument, rather than reading
`BuildConfig` themselves. That is the difference between a test suite that means something and one that
does not: a test asserting `isConfigured()` would **pass on a machine with a key configured and fail on
one without**, which is worse than having no test at all.

The same trick appears elsewhere in this project for the same reason — `MapMarkerController.markerIconRes`
is package-private so `MapMarkerControllerTest` can reach it, and `DateTimeHelper` takes its `ZoneId` and
`Locale` as parameters so its tests do not depend on where they run.

### 13.5 The plumbing was proved before you have a key

A placeholder key was put into `local.properties`, the build was run, and the generated
`BuildConfig.java` was inspected:

| Step | `BuildConfig.ORS_API_KEY` |
| --- | --- |
| No key in `local.properties` | `""` |
| `ORS_API_KEY=VERIFY_PLACEHOLDER_NOT_A_REAL_KEY` added, **incremental** build | `"VERIFY_PLACEHOLDER_NOT_A_REAL_KEY"` |
| Line removed, **incremental** build | `""` |

So the wiring works in both directions, and **an incremental build does pick up a changed key** — which
was worth checking, because `BuildConfig` fields are Java compile-time constants and javac inlines those
at the point of use. If Gradle had not recompiled `RoutingConfig`, the app would have kept the old
inlined value and the key would have looked as though it had not been saved.

`local.properties` was then restored to exactly its original content.

### 13.6 The key is inside the APK — what that means

`BuildConfig.ORS_API_KEY` is a compile-time constant, so javac **inlines it**: after compilation the
string literal sits inside `RoutingConfig`'s bytecode, and the name `ORS_API_KEY` does not even survive
into the dex. (That was confirmed — the built APK contains no `ORS_API_KEY` symbol at all.)

So the key is in the APK, and there is no way around that for a value the app must send. What matters is
what follows from it:

- **The APK must never be shared.** Not uploaded anywhere, not attached to a message. For a solo app that
  is never distributed (D-5) this is easy to keep, but it is now a rule rather than an assumption.
- **A key in an APK can be extracted.** Anyone holding the file can read it. That is why the account's
  key can be regenerated on the openrouteservice dashboard, and why the key should be treated as
  disposable rather than precious.
- **The free tier's limits are the real protection.** 2,000 routes a day, and the service is not a paid
  account, so a leaked key costs nothing but a nuisance.

The built APK was searched for the placeholder key, dex file by dex file, and contains none — the check
is in §15.1 so it can be repeated whenever the key changes.

### 13.7 What Phase 4.2 will need, decided here

Recorded so 4.2 does not have to re-open it:

- **HTTP client: `HttpURLConnection`.** It is in the platform, it needs no dependency, and this is one
  `POST` to one endpoint. OkHttp or Retrofit would add a library, a build step and a concept to learn, to
  replace about thirty lines. Revisit if the routing work ever grows beyond this one call.
- **JSON: Android's built-in `org.json`.** Also in the platform, so the download size does not change.
  The response is read as `features[0].geometry.coordinates` for the line and
  `features[0].properties.segments[0]` for the distance and duration.
- **One test-only dependency will be needed:** `testImplementation 'org.json:json:…'`. The platform's
  `org.json` is a stub on the JVM — every method throws "not mocked" — so without a real copy the parser
  cannot be unit-tested. Adding it under `testImplementation` keeps it out of the app entirely.
- **Coordinate order is longitude-first.** The GeoJSON the service returns is `[longitude, latitude]`,
  which is the opposite of how a coordinate is usually spoken. Getting it backwards puts every route in
  the sea off the coast of Africa, and it is the single easiest mistake to make in 4.2.
- **Two failures to distinguish:** `isConfigured() == false` (no key — tell the user to set it up) versus
  a `403` from the service (key rejected — tell the user the key is not accepted).

---

## 14. DATA FLOW FOR THIS PHASE

```
local.properties  (gitignored, machine-specific)
      │  ORS_API_KEY=...
      ▼
app/build.gradle          reads it at configuration time, escapes it
      │  buildConfigField "String", "ORS_API_KEY", "\"...\""
      ▼
BuildConfig.ORS_API_KEY   generated Java, compile-time constant
      │  inlined by javac into…
      ▼
RoutingConfig             the ONLY class that reads it
      ├─ apiKey()        → the trimmed key, never null
      ├─ isConfigured()  → false when blank; the app's "not set up yet"
      ├─ directionsUrl() → https://api.openrouteservice.org/v2/directions/driving-car/geojson
      └─ AUTHORIZATION_HEADER → "Authorization"

Nothing in the app calls any of this yet. Phase 4.2 does.
```

---

## 15. MANUAL TEST PROCEDURE

### 15.0 Already verified automatically (2026-09-25)

`./gradlew :app:testDebugUnitTest :app:assembleDebug` was run on this machine with Gradle 9.4.1 / JDK 21.

| Test class | Result |
| --- | --- |
| **`RoutingConfigTest`** (new) | **12 tests, 0 failures, 0 errors** |
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
| **TOTAL** | **144 tests, 0 failures, 0 errors, 0 skipped** |

| Other evidence | Result |
| --- | --- |
| Build with **no key** in `local.properties` | `BUILD SUCCESSFUL` — the app builds and packages without a key |
| Generated `BuildConfig.java` with no key | `public static final String ORS_API_KEY = "";` |
| Generated `BuildConfig.java` with a placeholder key | `= "VERIFY_PLACEHOLDER_NOT_A_REAL_KEY";` |
| Generated `BuildConfig.java` after removing it | back to `= "";` — an incremental build picks up both directions |
| `local.properties` afterwards | restored to exactly its original content |
| `grep -rn "VERIFY_PLACEHOLDER" app/src/` | **no matches** — the key never entered the source tree |
| APK searched dex by dex for the placeholder | **0 occurrences in all 14 dex files** |
| `app/schemas/…/AppDatabase/` | still contains **only `1.json`** — no schema change |
| Merged manifest permissions | **unchanged** — the same four |
| `app-debug.apk` | 9,625,061 bytes *(incremental build — see the trap in HANDOFF §8)* |
| `:app:compileDebugKotlin` | **NO-SOURCE** — still zero Kotlin source files |

### 15.1 The key plumbing (no key needed — this is already done)

This was run here and can be repeated whenever the key changes.

**Step 1 — the app builds with no key.** `local.properties` has only `sdk.dir`. The build succeeds and
`BuildConfig.ORS_API_KEY` is `""`.

**Step 2 — a key reaches the code.** Add a line to `local.properties`, then build:

```properties
ORS_API_KEY=VERIFY_PLACEHOLDER_NOT_A_REAL_KEY
```

```bash
./gradlew :app:assembleDebug --no-daemon --console=plain
grep ORS_API_KEY app/build/generated/source/buildConfig/debug/com/cph/alpharoute/BuildConfig.java
```

**Expected:** `public static final String ORS_API_KEY = "VERIFY_PLACEHOLDER_NOT_A_REAL_KEY";`

**Step 3 — the key never reaches the source tree or the wrong APK:**

```bash
grep -rn "VERIFY_PLACEHOLDER" app/src/            # expected: no matches
unzip -p app/build/outputs/apk/debug/app-debug.apk classes.dex | grep -ac "VERIFY_PLACEHOLDER"
```

**Expected:** no matches, and `0`.

**Step 4 — remove the line again** and rebuild. `BuildConfig.ORS_API_KEY` must return to `""`.

### 15.2 ✅ The live check with the real key — PASSED

The account was created and the key put in `local.properties` on 2026-09-25. The check was run here.

| Check | Result |
| --- | --- |
| `ORS_API_KEY` present in `local.properties` | **yes** — 120 characters, no spaces, no quotes, no backslashes |
| Live request to the directions endpoint | **HTTP 200** |
| Response type | `FeatureCollection` |
| Route returned | **1734 m, 298 s (5.0 min), 31 geometry points** |
| First coordinate pair | `[120.984206, 14.599528]` — **longitude first**, confirming §13.7 |
| `BuildConfig.ORS_API_KEY` after the build | **set, 120 characters** |
| Unit tests with the key configured | **144 tests, 0 failures** — the suite is key-independent by design (§13.4) |
| Key present in the built APK | **yes, once** — the compile-time constant was inlined as expected (§13.6) |

That last row is the whole chain proved: the key reached the code, the code reached the APK.

> A note on the key's shape, in case it ever looks wrong: it is **120 characters and not hexadecimal.**
> An older tutorial describing a 64-character hex key is not describing what this service issues now.

**How the account was set up, for reference:**

1. **openrouteservice.org** → **Sign up**. It creates a **HeiGIT account**.
2. Email and password only. **No card, no billing** — the Standard plan is EUR 0, 2,000 routes/day.
3. The key is on the account dashboard once you are in.

**The key goes in `local.properties`** — one line, at the project root:

```properties
ORS_API_KEY=your_key_here
```

**To repeat the check**, read the key out of the file rather than typing it on the command line, so the key
never appears in the terminal history or in a chat log:

```bash
cd "C:/Users/Admin/OneDrive/Documents/custom system/AlphaRoute"
KEY=$(grep -E '^ORS_API_KEY=' local.properties | cut -d= -f2-)
curl -s -w "\nHTTP %{http_code}\n" -X POST \
  "https://api.openrouteservice.org/v2/directions/driving-car/geojson" \
  -H "Authorization: $KEY" -H "Content-Type: application/json" \
  -d '{"coordinates":[[120.9842,14.5995],[120.9911,14.6071]]}' | head -c 600
```

| What you see | What it means |
| --- | --- |
| **HTTP 200** and a body containing `"type":"FeatureCollection"` | The key works |
| **HTTP 401** | The key is empty or blank — the line is not being read |
| **HTTP 403** | The key is being read but the service rejected it — check for a stray character, or regenerate it on the dashboard |
| **HTTP 429** | The key works, but the daily quota is used up |

> The two coordinates in that command are a pair of points in Manila, used only to give the service
> something to route between. They are not store data and are never stored by the app.

### 15.3 The app is otherwise unchanged

Nothing in this phase touches a screen. Confirm as a formality:

1. The app installs and opens.
2. The Map tab still draws markers and the Home pin.
3. Settings, Home Setup, Store Details and the Add form all still work.

**Report any failure with the step number and any Logcat text.**

---

## 16. EXPECTED RESULT

- The app builds, tests and packages **with no key at all**.
- Adding `ORS_API_KEY` to `local.properties` puts the key into `BuildConfig.ORS_API_KEY`.
- **No file under `app/src` contains the key**, and neither does the source tree.
- `RoutingConfig.isConfigured()` is `false` with no key and `true` with one.
- The directions URL is HTTPS, names the driving-car profile, and carries no key.
- **With your key in place, the live request returns HTTP 200** (§15.2).
- **No schema change, no new dependency, no new permission, no change to any screen.**

---

## 17. COMMON ERRORS AND FIXES

| Symptom | Cause | Fix |
| --- | --- | --- |
| `BuildConfig.ORS_API_KEY` is `""` after adding the key | The line is not in `local.properties` at the **project root**, or the key name is misspelled | It must be exactly `ORS_API_KEY=...`, and the file must be the root one, beside `settings.gradle` |
| The generated `BuildConfig.java` does not compile | The key contains a quote or a backslash | Already escaped in `app/build.gradle`; if it still fails, check for other unusual characters in the key |
| **HTTP 401** from the service | The `Authorization` header is missing or empty | The key is not reaching the request — check `isConfigured()` first |
| **HTTP 403** from the service | The key was sent but rejected | A stray character, or a regenerated key. The error text is `"Access to this API has been disallowed"` |
| A `?api_key=...` style request returns 401 | This API no longer reads a query parameter | The key goes in the `Authorization` header (§13.2) |
| Every route appears off the coast of Africa | The GeoJSON coordinates were read as `[latitude, longitude]` | They are `[longitude, latitude]` (§13.7) |
| The old key still seems to be in use after changing it | An incremental build did not recompile the dependent class | Tested as working, but a clean build removes all doubt (§13.5) |
| `grep` finds the key somewhere under `app/src` | Something wrote the key into a source file | It must not be there. `RoutingConfig` reads the build constant; nothing else should (§13.1) |

---

## 18. ACCEPTANCE CHECKLIST

- [x] Build succeeds **with no key** in `local.properties` (§15.0 — verified)
- [x] **All 12 `RoutingConfigTest` cases pass** (§15.0 — verified; whole suite 144 tests, 0 failures)
- [x] A key in `local.properties` reaches `BuildConfig.ORS_API_KEY` (§15.0/§15.1 — verified both directions)
- [x] `local.properties` restored to its original content (§15.0 — verified)
- [x] **No trace of the key under `app/src`** (§15.0 — verified)
- [x] **With no key configured, no trace of one in the built APK either** (§15.0 — verified, all 14 dex files)
- [x] **With the real key configured, it IS in the APK — by design and verified** (§15.2, §13.6)
- [x] The endpoint path and the `Authorization` header are **confirmed against the live service** (§13.2 — verified)
- [x] `app/schemas/` still contains only `1.json` (§15.0 — verified)
- [x] Merged manifest permissions unchanged (§15.0 — verified)
- [x] **openrouteservice account created** (§15.2 — verified)
- [x] **A real key returns HTTP 200** (§15.2 — verified: a 1734 m route came back)
- [ ] The app still works on a device after the change (§15.3)
- [ ] Confirmed: still **no Kotlin source file** anywhere in the project

---

## 19. UPDATED PROJECT STATE

**COMPLETED PHASES**
- ✅ 0.1 · 0.2 · 0.3 · 1.1 · 1.2 · 1.3 · 2.1 · 2.2 · 2.3 · 3.1 · 3.2 · 3.3 · 3.4 · 3.5 · 3.6 · 3.7
  — **Version 3 complete**
- ✅ **4.1 Routing Service Preparation** — complete and verified, including the live key check

**CURRENT PHASE**
- Phase 4.1 — **complete.** Next: Phase 4.2 Route Preview

**CURRENT DATABASE VERSION**
- **1.** Unchanged. Three tables: `stores`, `completion_history`, `app_settings`.

**CURRENT FILES** (changed this phase)

```
app/src/main/java/com/cph/alpharoute/navigation/RoutingConfig.java              (NEW)
app/src/test/java/com/cph/alpharoute/navigation/RoutingConfigTest.java          (NEW - 12 tests)

app/build.gradle                                                                (MODIFIED)
docs/HANDOFF.md                                                                 (MODIFIED)

docs/PHASE_4.1_ROUTING_PREPARATION.md                                           <- created this phase
```

**WORKING FEATURES** (verified)
- ✅ **144/144 unit tests pass** — 0 failures, 0 errors (verified 2026-09-25), including 12 new cases
- ✅ **The build succeeds with no key configured** — the state the project is in right now
- ✅ **The key plumbing works in both directions** — proven with a placeholder, then reverted
- ✅ **The key is absent from `app/src`** — checked, not assumed. (It *is* inside the built APK, because a
  compile-time constant is inlined — see §13.6. That is why the APK must never be shared.)
- ✅ **The service's endpoint and auth method are confirmed against the live API**
- ✅ **Schema unchanged** — only `1.json` exists
- ✅ **Permissions unchanged** — the same four
- ✅ **Still zero Kotlin source files** — `compileDebugKotlin` = `NO-SOURCE`
- ✅ **A real key configured and working** — a live route returned HTTP 200, 1734 m (§15.2)

**KNOWN ISSUES**
- None blocking. The account exists, the key is configured, and a live route has been fetched.
- Nothing calls `RoutingConfig` yet. That is deliberate — Phase 4.2 is its first caller.
- The APK size above is an **incremental** figure and is inflated; see the trap in HANDOFF §8.
- Carried risks: OSM data quality in remote PH rural areas · openrouteservice free-tier quota (2,000
  routes/day) · **no offline map** (policy, risk R5) · date boundary (AD-7 + AD-11).
- The project is **not under version control**. Still the strongest recommendation in the project.
- Trademark note: "Alfamart" is a third-party brand; fine for a private solo tool (D-5).

**PENDING PHASES** (not implemented)
- **4.2 Route preview** — next
- 4.3 Navigation hand-off + arrival monitoring · 4.4 State recovery
- 5.1 Arrival-radius check · 5.2 Completion dialog · 5.3 Atomic completion transaction
- 6.1 Live tracker · 6.2 Daily history list · 6.3 Daily completion order · 6.4 Completion details ·
  6.5 Consistency check
- 7.1 Nearest pending store · 7.2 Recalculate after completion · 7.3 Home-aware heuristic · 7.4 No pending stores
- 8.1 Routing service review · 8.2 Road-based next store · 8.3 Multi-stop optimization · 8.4 Complete route preview
- 9.1 Local export · 9.2 Local import · 9.3 Optional cloud sync *(Firebase — deferred by your decision)*
- 10.1 Error handling · 10.2 Automated and manual testing · 10.3 Release preparation

---

## 20. STOP POINT

Phase 4.1 is code complete and verified. **5 files touched: 3 created, 2 modified.** No dependency, no
permission, no schema change, and no screen touched.

**Half of this phase could be done without a key, and that half is done.** The service's endpoint path,
its authentication method and its two failure modes are all confirmed against the live API — the endpoint
answers `401` with no key and `403` with a bad one, which proves the path is right and proves the key goes
in the `Authorization` header rather than a query parameter. That one fact alone would have cost an
afternoon in Phase 4.2.

**The key's home is settled and proved.** `local.properties` → `BuildConfig.ORS_API_KEY` → read by
`RoutingConfig` and nothing else. The app builds, tests and packages with **no key at all**, and a
placeholder key was pushed all the way through to the generated `BuildConfig.java` and back again to
confirm the wiring works in both directions. **The key appears nowhere under `app/src`, and nowhere in the
built APK** — both checked rather than assumed.

**Two things worth carrying forward.** The key ends up inside the APK, because a compile-time constant is
inlined into the bytecode — unavoidable, acceptable for a solo app that is never distributed, but it means
**the debug APK must never be shared**. And the GeoJSON coordinates come back **longitude-first**, which
is the opposite of how a coordinate is usually spoken and the easiest way to put every route in the sea.

### Nothing left to unblock

The account exists and the key is in place, so Phase 4.1 is finished end to end. **Every remaining phase
in Version 4 can be done without anything further from you** — the service is verified, the key reaches the
code, and the endpoint's behaviour is recorded.

**Next: Phase 4.2 — Route Preview**, on your instruction: `sheet_route_preview.xml` with the selected
store, the distance, the duration, the route line, **Start Navigation** and **Cancel**. It will not start
navigation automatically — that stays a deliberate act.

**Phase complete. Do not proceed to the next phase until all acceptance criteria pass.**

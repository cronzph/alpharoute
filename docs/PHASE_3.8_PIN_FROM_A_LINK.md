# AlphaRoute — Phase 3.8 Pin from a Shared Google Maps Link

**Project title:** Alfamart Route Tracker
**Version:** 3 — Map and Manual Pins — **Phase:** 3.8 Pin from a Shared Google Maps Link
**Repository root:** `C:/Users/Admin/OneDrive/Documents/custom system/AlphaRoute`
**Date:** 2026-09-25
**Prerequisites:** Phases 0.1–0.3, 1.1–1.3, 2.1–2.3, 3.1–3.7, 4.1, 4.2
**Status:** ✅ **COMPLETE AND VERIFIED — 216/216 unit tests pass, clean build succeeds.**

> **Google Maps already knows where every Alfamart is.** Now you can copy one and paste it in: the store is
> pinned exactly where Google Maps has it, instead of by eye on our own map. That matters more than it
> sounds — with a 50 m arrival radius, a pin a few hundred metres out means the app never offers to
> complete that store, and never explains why.

---

## 1. CURRENT VERSION AND PHASE

| Item | Value |
| --- | --- |
| Version | 3 — Map and Manual Pins |
| Phase | 3.8 — Pin from a Shared Google Maps Link |
| Files created | **9** |
| Files modified | **12** |
| Files deleted | **1** (`RoutePoint.java` — renamed, see §13.6) |
| Database version | **1** — unchanged |
| Unit tests | **216** (was 188 — 28 new) |
| New dependencies | **None** |
| New permissions | **None** |

### Why "3.8", when 4.2 is already done

It is a **pinning** feature, and pinning is Version 3's subject — so it belongs beside 3.3 and 3.7 rather
than in the navigation version. The number was free, so nothing had to be renumbered. It was built out of
roadmap order because you asked for it, and that is recorded here rather than hidden.

---

## 2. OBJECTIVE

Let a store's location come from Google Maps instead of from the user's eye:

- Paste a Google Maps link (or plain coordinates) into the app
- Read the location out of it — **the place, not the camera**
- Hand it to the existing Add Store form, so the pin is exact
- Move the map to it, so the new store can actually be found afterwards
- **Never guess.** If the link cannot be read, say so

---

## 3. UNDERSTANDING CHECKLIST

- [x] Java only, no Kotlin. XML layouts only.
- [x] **No new permission** and no new dependency.
- [x] **Nothing is stored by this phase** — no schema change, no migration.
- [x] The link parser is **pure Java** (AD-11) and fully unit-tested.
- [x] The one network request runs on `AppExecutors.networkIO()`, never the disk thread.
- [x] A screen never touches HTTP: the sheet → `PlaceLinkRepository` → the link.
- [x] **The Add Store form is not modified at all** — its constructor already takes a latitude and a longitude.
- [x] **A location is never invented.** An unreadable link produces a message, not a pin at 0, 0.
- [x] Navigation is still only ever started by an explicit tap.

---

## 4. FEATURES INCLUDED (in this phase)

| Feature | Detail |
| --- | --- |
| **`GoogleMapsLinkParser`** | Pure Java. Seven link shapes, **28 tests** |
| **The 164 m fix** | Prefers `!3d`/`!4d` (the place) over `@` (the camera) — §13.1 |
| **`PlaceLinkRepository`** | Reads a full link with no network; follows a short link when it has to |
| **The clipboard** | Read on open, so the common case is one tap |
| **`AddFromLinkBottomSheet`** | The paste screen, with a live result and honest errors |
| **A Map tab button** | `fab_add_from_link` — the second way to add a store |
| **The map moves to it** | Otherwise a store imported from elsewhere would be saved and never seen |
| **A rename** | `RoutePoint` → `GeoCoordinate`, done first and verified on its own |

---

## 5. FEATURES NOT INCLUDED YET

| Not here | Why |
| --- | --- |
| **Being a share target** (appearing in Google Maps' Share sheet) | Deliberate — see §13.7. The intent plumbing has to survive a cold start, a warm start and rotation, and it deserves its own phase rather than being bolted on |
| **Pre-filling the store name** from the share blob's first line | The share text *does* contain it ("Alfamart San Roque"), and it is a genuine free win — but it needs the Add Store form's constructor widened, which this phase deliberately left alone. About fifteen minutes of work whenever it is wanted |
| Pasting a link for **Home** | Home Setup has its own map. Same parser, different screen — worth doing, not now |
| Reading a **Plus Code** or a what3words link | Neither is in the frozen stack, and neither appears in a Google Maps share |

---

## 6. ASSUMPTIONS

| # | Assumption | Impact if wrong |
| --- | --- | --- |
| A113 | **The paste flow is its own sheet, which hands off to the existing Add Store form.** | That form is unchanged: it already takes a latitude and a longitude and has no business knowing where they came from. A separate sheet also keeps the "no coordinates yet" state out of a form that always had them. |
| A114 | **`!3d`/`!4d` is read before `@`.** | See §13.1. This is the phase's whole reason for existing. |
| A115 | **The bare-coordinate pattern is anchored to the whole text.** | So it cannot match numbers buried inside a URL and win the precedence race. The cost is that `"Location: 14.5, 120.9"` is not read — a documented, deliberate limit (§13.3). |
| A116 | **The parser refuses rather than guesses.** No coordinate, no result. | A store pinned in the wrong place is worse than one that was not pinned, because nothing reports it. |
| A117 | **A short link is followed by hand, up to three hops, then its body is read.** | See §13.4. One redirect is not enough — a link may redirect twice, or redirect in JavaScript with no `Location` header at all. |
| A118 | **The state is `LiveData`, like `RoutingRepository` and `LocationProvider`.** | It is what lets the sheet that collects the link and the map that must move to the result observe the same thing without a reference to each other. |
| A119 | **The clipboard is read when the sheet opens, and only on a fresh view.** | The user has just tapped Copy, so this makes the common case one tap. On a rotation the field is already restored, and reading again would overwrite whatever they had typed. |
| A120 | **The map centres on the imported point.** | A store imported from a link is usually somewhere else entirely — that is why it was imported. Without this the user saves it and sees nothing move. |
| A121 | **Paste only; not a share target yet.** | §13.7. |
| A122 | **Pin first, then navigate.** Never auto-navigate. | Beyond the standing rule: a store has to exist for the arrival flow to complete. Navigating to an unsaved pin would arrive somewhere with nothing to attach a completion to. |
| A123 | **`RoutePoint` was renamed `GeoCoordinate`.** | One coordinate type for routes, pins and links, and a name that stops lying — a store's location is not "a point along a route". §13.6. |
| A124 | **The share blob's first line (the place name) is ignored.** | Deliberate, and recorded above as a follow-on rather than forgotten. |

---

## 7. FILES TO CREATE (9)

| # | Path | Purpose |
| --- | --- | --- |
| 1 | `domain/GeoCoordinate.java` | **New** (renamed from `RoutePoint`). A latitude and a longitude. Pure. |
| 2 | `domain/GoogleMapsLinkParser.java` | **New.** The core. Pure, 28 tests. |
| 3 | `domain/PlaceLinkState.java` | **New.** Status + coordinate in one object. Pure. |
| 4 | `data/repository/PlaceLinkRepository.java` | **New.** Reads a link; follows a short one. |
| 5 | `ui/store/AddFromLinkBottomSheet.java` | **New.** The paste screen. |
| 6 | `res/layout/sheet_add_from_link.xml` | **New.** Its layout. |
| 7 | `res/drawable/ic_add_link.xml` | **New.** The Map tab button's icon. |
| 8 | `test/.../domain/GoogleMapsLinkParserTest.java` | **New.** 28 tests. |
| 9 | `docs/PHASE_3.8_PIN_FROM_A_LINK.md` | **New.** This record. |

---

## 8. FILES TO MODIFY (12) AND DELETE (1)

| # | Path | Status | What changed |
| --- | --- | --- | --- |
| 1 | `domain/RoutePoint.java` | **Deleted** | Renamed to `GeoCoordinate` |
| 2 | `domain/RoutePlan.java` | Modified | Uses `GeoCoordinate` |
| 3 | `navigation/RouteResponseParser.java` | Modified | Uses `GeoCoordinate` |
| 4 | `data/repository/RoutingRepository.java` | Modified | Uses `GeoCoordinate` |
| 5 | `ui/map/MapMarkerController.java` | Modified | Uses `GeoCoordinate` |
| 6 | `ui/route/RoutePreviewBottomSheet.java` | Modified | Uses `GeoCoordinate` |
| 7 | `test/.../navigation/RouteResponseParserTest.java` | Modified | Uses `GeoCoordinate` |
| 8 | `test/.../data/repository/RoutingRepositoryTest.java` | Modified | Uses `GeoCoordinate` |
| 9 | `ui/map/MapFragment.java` | Modified | The new button, and `observePlaceLink()` |
| 10 | `res/layout/fragment_map.xml` | Modified | `fab_add_from_link`, stacked above the My location button |
| 11 | `AlphaRouteApp.java` | Modified | Owns `PlaceLinkRepository` |
| 12 | `res/values/strings.xml` | Modified | 13 new strings |
| 13 | `docs/HANDOFF.md` | Modified | Status, inventory, test count, roadmap, traps |

Rows 2–8 are the rename, and nothing else.

**Inspected and left unmodified:** the Add Store form, `StoreRepository`, `StoreValidator`, the database
layer, and every other screen. **The Add Store form is the one worth stating: this phase adds a way to
produce coordinates, and the form that consumes them did not change a single line.**

---

## 9. DEPENDENCIES

**None added.** The whole feature is the platform: a regular expression, `HttpURLConnection`, and the
clipboard.

---

## 10. PERMISSIONS AND MANIFEST CHANGES

**None.** Verified in the merged manifest — still the same four permissions, and the `<queries>` element
from Phase 4.2 is untouched.

Reading the clipboard needs no permission: `ClipboardManager` is a system service available to the
foreground app, and Android shows its own notice when an app reads it.

---

## 11. SETUP INSTRUCTIONS

1. Let Gradle sync (nothing new to fetch).
2. **Build → Clean Project**, then **Build → Rebuild Project**.
3. On the device, make sure Google Maps is installed — this feature reads *its* links.

> A clean build on this machine takes around four minutes, because the project lives in a OneDrive folder
> that is slow to delete the build tree. If `clean` fails with a file-lock error, run it again.

---

## 12. COMPLETE CODE

All 22 files are written into the project on disk. The files worth reading first:

1. `domain/GoogleMapsLinkParser.java` — the precedence order, and the 164 m trap
2. `test/.../GoogleMapsLinkParserTest.java` — the two tests that pin that trap down
3. `data/repository/PlaceLinkRepository.java` — the three-step short-link follow
4. `ui/store/AddFromLinkBottomSheet.java` — the paste screen, and the hand-off
5. `ui/map/MapFragment.java` — `observePlaceLink()`, and why the map cares

---

## 13. CODE EXPLANATION

### 13.1 The trap this phase exists to avoid: two coordinate pairs, 164 metres apart

A Google Maps place link contains **two** coordinate pairs, and they are not the same point:

```
…/maps/place/Alfamart/@14.6010,120.9900,17z/data=…!3d14.599528!4d120.984206…
                   └─ the CAMERA ─┘            └─── the PLACE ───┘
```

The `@` pair is where the map was looking. The `!3d`/`!4d` pair is the shop itself. In the test fixture
they are **164 metres apart** — and this app's arrival radius defaults to **50 metres**.

So the obvious implementation, reading the `@` pair (which is what most examples online do, and the first
thing that matches if you write the regex in the natural order), would produce an app that:

- pins the store on the wrong side of the building,
- **never once offers to complete it**, because the user is never inside 50 m of that pin,
- and reports **nothing at all** — no error, no log, no warning.

It would look like it worked perfectly. It would just never say "you have arrived". That is the worst kind
of bug, and it is why the precedence in `parse()` puts `!3d`/`!4d` before `@` and why two tests exist
purely to hold that order:

```java
@Test
public void aPlaceLinkReadsThePlaceNotTheCamera() { … }

@Test
public void theCameraPairIsNotUsedWhenAPlacePairExists() { … }
```

The second one is the regression guard: if anyone ever reorders those patterns, it fails immediately.

### 13.2 Why the paste flow is its own sheet, and why the Add Store form did not change

The obvious place to put a "paste a link" button is on the Add Store form, next to the coordinates. It was
rejected, for a specific reason: **that form always has coordinates already.** It is opened by a long-press
on the map, and the whole point of the long-press is that it supplies the location. Adding a second way to
set them would mean adding a "no coordinates yet" state to a form that has never needed one, and gating its
Save button on it.

A separate sheet keeps that state where it belongs — in the flow that does not have a location yet:

```
Map tab → the link button → AddFromLinkBottomSheet
                               ├─ reads the clipboard, resolves it
                               └─ Continue → dismiss, then:
                                     AddEditStoreBottomSheet.newInstance(lat, lng)
```

That constructor already exists, unchanged since Phase 3.3. **The form that consumes the coordinates did
not change a single line**, and it has no idea whether they came from a long-press or a pasted link.

### 13.3 The precedence order, and why the bare pattern is anchored

Five patterns, tried in this order:

| Order | What | Why here |
| --- | --- | --- |
| 1 | Bare `14.5995, 120.9842` | Unambiguous, and it must come before anything that *searches*, or the numbers inside a URL would match it |
| 2 | `geo:14.5995,120.9842` | Android's own location-sharing form |
| 3 | `!3d` / `!4d` | **The place.** See §13.1 |
| 4 | `?q=` / `?query=` / `?ll=` / `?daddr=` | What a "search for these coordinates" link uses |
| 5 | `@lat,lng` | The camera. Last, because it is the least accurate |

The bare pattern is **anchored** (`^…$`) on purpose. An unanchored one would match the numbers buried
inside a URL — and since it is tried first, it would win, and every link would resolve to whichever numbers
happened to appear first in it.

The cost of anchoring is documented as a test rather than left to be discovered: `"Location: 14.5, 120.9"`
is **not** read. That is a deliberate trade. Google Maps never produces that shape, and a wrong pin is
worse than no pin.

### 13.4 Following a short link — the part that could not be verified

`maps.app.goo.gl/…` — what the Share sheet actually gives you — carries no coordinates. It is a redirect,
and the coordinates are at the far end. So the repository follows it, **by hand**:

```java
connection.setInstanceFollowRedirects(false);   // we want the Location header, not the page
```

Letting the library follow would download a Google Maps page on every hop. Reading the header is free.

**Three steps, because one is not enough:**

1. **The `Location` header** of the first redirect. Usually enough.
2. **Follow up to three hops**, parsing each `Location` — a real chain is one or two, but a link can
   redirect more than once.
3. **The response body**, bounded to 64 KB. This is the case where the redirect happens in JavaScript and
   there is no `Location` header at all — the coordinates are only in the page.

Every step runs the same tested parser over whatever text it found, so the only untested part is the
fetching.

> **⚠️ Honest limitation, and the one thing to check on a device.** A bogus short code returns **404** —
> that much was verified — which confirms the mechanism is a plain redirector. But **I could not verify the
> success path without a real share link**, because short links are generated by Google and not by hand.
> The direct-parse path (full links, `geo:`, bare coordinates) is fully tested; the short-link path is
> reasoned and defensive, not proven. **Please test §15.4 with a real link and tell me what happens.**

### 13.5 Why the state is `LiveData`, and why the map cares about a store that does not exist yet

`PlaceLinkRepository` holds a `LiveData<PlaceLinkState>`, the same shape as `RoutingRepository` and
`LocationProvider`. Two observers need it and neither knows about the other:

- **The sheet**, which collects the link and shows the result.
- **The map**, which has to move to the point.

The map one is not a nicety. A store imported from a link is usually **somewhere else entirely** — that is
the entire reason to import it rather than long-press. Without the camera move, the user would paste a
location, save the store, and see absolutely nothing happen, with no obvious way to find the pin they had
just added.

The camera is otherwise only ever moved when the user asks for it. This is them asking: they pasted a
location.

### 13.6 The rename, and why it was done first

`RoutePoint` became `GeoCoordinate`, and it was done as the **first step of this phase, on its own, and
verified before any new code was written** — 8 files, 35 references, no remaining references afterwards,
build green.

The reason it was needed: the parser has to return a coordinate, and `RoutePoint`'s own javadoc says *"one
point along a route"*. A store's location is not that. Reusing the name would have made it lie, and
creating a second identical class would have left the app with two coordinate types free to drift apart —
including in how they validate.

So there is now **one** coordinate type, and it is named for what it is. Renaming when a name stops being
accurate is something this project already does (`store_detail_missing` → `store_missing`,
`MapTileConfig` → `MapConfig`), and doing it in isolation first meant the compiler and 188 tests confirmed
it before anything else changed.

### 13.7 What is deliberately not here

- **Not a share target yet.** Appearing in Google Maps' Share sheet is the better experience — no manual
  copy-paste at all — and it shares every line of the parser. It is not here because the *intent plumbing*
  is not a small job: an incoming share has to be handled on a cold start, a warm start and after a
  rotation, and it has to open a sheet on a tab that may not exist yet. That is its own phase, and the
  parser this phase built is the expensive half of it.
- **The store name is not pre-filled.** A Google Maps share puts the place's name on the first line — the
  test fixture uses `"Alfamart San Roque\nhttps://…"` — so it is sitting right there and could fill the
  name field for free. It needs the Add Store form's constructor widened, which this phase chose not to
  touch. Fifteen minutes, whenever it is wanted.
- **No Plus Code or what3words support.** Neither appears in a Google Maps share, and neither is in the
  frozen stack.
- **Pasting a link for Home is not wired up.** Home Setup has its own map and its own flow; the parser
  would work there unchanged, but the screen does not offer it.

---

## 14. DATA FLOW FOR THIS PHASE

```
Map tab → the link button (fab_add_from_link)
  └─ AddFromLinkBottomSheet
        ├─ reads the clipboard on open (fresh view only)
        └─ PlaceLinkRepository.resolve(text)
              ├─ GoogleMapsLinkParser.parse(text)
              │     ├─ coordinates found → FOUND, immediately, NO network
              │     └─ not found, and not a short link → NOT_A_LINK
              └─ is a short link → RESOLVING, then on networkIO():
                    follow it by hand, up to 3 hops:
                      ├─ Location header parses → FOUND
                      ├─ redirect with no coordinates → follow it and look again
                      ├─ no redirect → read ≤64 KB of the body and parse that
                      ├─ nothing readable → UNRESOLVED
                      └─ IOException → OFFLINE
                            ↓ postToMain
                      LiveData<PlaceLinkState>
                            ├──► AddFromLinkBottomSheet.render()
                            │      spinner / "Found: 14.599528, 120.984206" / the reason there is none
                            │      Continue enabled only when FOUND
                            └──► MapFragment.observePlaceLink()
                                   FOUND → camera moves to the point
                                   anything else → nothing

Continue → dismiss → AddEditStoreBottomSheet.newInstance(lat, lng)   ← UNCHANGED since Phase 3.3
              └─ the ordinary form: four fields, Save
                    └─ StoreRepository.insert()  → the pin appears on the map
```

**Nothing is written until Save**, and this phase adds no table, no column and no migration.

---

## 15. MANUAL TEST PROCEDURE

### 15.0 Already verified automatically (2026-09-25)

`./gradlew clean :app:testDebugUnitTest :app:assembleDebug` was run on this machine with Gradle 9.4.1 /
JDK 21 and returned **`BUILD SUCCESSFUL in 4m 19s`** (42 tasks, all executed).

| Test class | Result |
| --- | --- |
| **`GoogleMapsLinkParserTest`** (new) | **28 tests, 0 failures, 0 errors** |
| `LocationFixTest` | 20 tests, 0 failures |
| `StoreValidatorTest` | 19 tests, 0 failures |
| `RouteResponseParserTest` | 18 tests, 0 failures |
| `GeoConstantsTest` | 17 tests, 0 failures |
| `ArrivalRadiusValidatorTest` | 16 tests, 0 failures |
| `DateTimeHelperTest` | 16 tests, 0 failures |
| `MapMarkerControllerTest` | 15 tests, 0 failures |
| `RouteTextFormatterTest` | 15 tests, 0 failures |
| `RoutingConfigTest` | 13 tests, 0 failures |
| `MapUserAgentTest` | 11 tests, 0 failures |
| `AppSettingEntityTest` | 10 tests, 0 failures |
| `StoreRepositoryTest` | 7 tests, 0 failures |
| `RoutingRepositoryTest` | 5 tests, 0 failures |
| `NavigationLauncherTest` | 5 tests, 0 failures |
| `ExampleUnitTest` (template, Phase 10.2) | 1 test, 0 failures |
| **TOTAL** | **216 tests, 0 failures, 0 errors, 0 skipped** |

| Other evidence | Result |
| --- | --- |
| **The rename, verified on its own first** | no remaining `RoutePoint` references; build green before any new code |
| `:app:compileDebugJavaWithJavac` | ran — the parser, repository, sheet and map change all compile |
| `:app:mergeDebugResources` + `:app:processDebugResources` | ran — the new layout, drawable and 13 strings resolve |
| `fab_add_from_link` in the packaged layout | present |
| `app-debug.apk` (**clean build**) | **9,649,815 bytes** (was 9,633,673 — +16,142) |
| Merged manifest permissions | **unchanged** — the same four |
| `app/schemas/…/AppDatabase/` | still contains **only `1.json`** — no schema change |
| `:app:compileDebugKotlin` | **NO-SOURCE** — still zero Kotlin source files |

### 15.1 The Map tab has a second way to add a store

1. Open the **Map** tab. Confirm a **link button** above the My location button, and that neither covers the
   OSM attribution.
2. Tap it. The **Add from a link** sheet must open.

### 15.2 A full link works with no network

3. Copy a **full** Google Maps link — open the place in a browser and copy the address bar, or use
   **Share → Copy link** if your version gives the long form.
4. Open the sheet. If the clipboard held it, the field fills itself and the result shows the coordinates.
   If not, tap **Paste**.
5. **The result line must read `Found: <lat>, <lng>`** and **Continue must be enabled**.
6. **Turn mobile data and Wi-Fi off** and repeat. A full link must still resolve — it needs no network.

### 15.3 The coordinates are the SHOP, not the camera

7. Open the same place in Google Maps and note its coordinates.
8. Compare with what the app found. **They must match.**
9. **The check that matters:** if the app's coordinate differs from Google Maps' by a few hundred metres —
   roughly `0.001` in latitude — it has read the **camera** pair instead of the place pair. Report it
   immediately; that is the §13.1 bug and it would silently break arrival detection.

### 15.4 A short link (the one path I could not verify) ⚠️

10. In Google Maps, find an Alfamart, tap **Share**, and **Copy link**. It will look like
    `https://maps.app.goo.gl/…`.
11. Open the sheet. It must show **"Reading the link…"**, then either `Found: …` or a clear message.
12. **Please report exactly what happens here**, whichever way it goes — this is the one path that could
    not be tested without a real link, and it is the path you will use most.

### 15.5 Bare coordinates and the failure cases

13. Type or paste just **`14.5995, 120.9842`**. It must resolve.
14. Paste **`Alfamart San Roque`** (no link). It must say **"That is not a Google Maps link…"** and
    **Continue must stay disabled**.
15. Paste **`https://www.alfamart.com.ph/`**. Same message.
16. Clear the field and tap **Paste** with nothing useful on the clipboard. It must say
    **"Copy a link in Google Maps first…"**.
17. With mobile data **off**, paste a short link. It must say **"Could not reach Google Maps…"**, not
    crash.

### 15.6 The hand-off, and the pin

18. With a location found, tap **Continue**. The sheet must close and the **Add store** form must open with
    its coordinates readout showing the imported point.
19. **The map behind must have moved to that location.** Confirm the area under the sheet is the store's
    area, not where the map was before.
20. Fill in the route number, code, name and area, and **Save**.
21. Confirm the new marker appears **at the imported point**, and that tapping it shows the coordinates you
    imported.

### 15.7 Cancel leaves nothing behind

22. Open the sheet, resolve a location, then tap **Cancel**.
23. Confirm nothing was saved, and that reopening the sheet does **not** still show the previous result.

### 15.8 Rotation and the other ways in

24. Open the sheet and rotate. The field and the result must survive, and the clipboard must **not** be read
    again over anything you typed.
25. Confirm **long-press on the map still opens the Add store form** with the pressed coordinates — the old
    way still works.
26. Confirm store markers, the Home pin, Get directions, Edit and Delete all still work.

**Report any failure with the step number and any Logcat text.**

---

## 16. EXPECTED RESULT

- The Map tab has a second way to add a store, from a link.
- A full Google Maps link resolves **with no network**; a short link resolves after one request.
- **The coordinates are the shop's, not the map's camera.**
- Continue is disabled until a real location has been read.
- Continue opens the ordinary Add Store form, pre-filled, and **the map has moved there**.
- An unreadable link produces a clear message and **no pin**.
- **No new permission, no new dependency, no schema change, and the Add Store form is untouched.**

---

## 17. COMMON ERRORS AND FIXES

| Symptom | Cause | Fix |
| --- | --- | --- |
| **The pin is a few hundred metres from the shop** | The `@` camera pair was read instead of `!3d`/`!4d` | §13.1. Reorder the patterns in `parse()` — and `theCameraPairIsNotUsedWhenAPlacePairExists` will fail until it is right |
| **"Could not read that link"** on a short link | The redirect chain gave nothing readable | §13.4. Try pasting the **full** link instead — open it in a browser and copy the address bar |
| "That is not a Google Maps link" on something that looks like one | The shape is not one of the five handled | Paste the full link, or the coordinates |
| Continue stays disabled | No location was read — see the result line | The line always says why |
| The map did not move after Continue | `observePlaceLink()` is not observing, or the camera was moved back | `MapFragment` observes the repository; nothing else should move the camera at that moment |
| The sheet still shows the last result when reopened | `clear()` is not called on leaving | `onDestroy` clears when `isRemoving()` — §13.6 of the Phase 4.2 record has the same pattern |
| The clipboard is read again after a rotation | The `savedInstanceState == null` guard was removed | It must read the clipboard only on a fresh view |
| `clean` fails with a file-lock error | OneDrive holding a file in the build tree | Run it again |

---

## 18. ACCEPTANCE CHECKLIST

- [x] Clean build succeeds with all 42 tasks executed (§15.0 — verified)
- [x] **All 28 new tests pass** (§15.0 — verified; whole suite 216 tests, 0 failures)
- [x] **The rename verified on its own first** (§15.0 — verified: no remaining references, build green)
- [x] `fab_add_from_link` is in the packaged layout (§15.0 — verified)
- [x] Merged manifest permissions unchanged (§15.0 — verified)
- [x] `app/schemas/` still contains only `1.json` (§15.0 — verified)
- [ ] The Map tab has the second add-a-store button (§15.1)
- [ ] A full link resolves **offline** (§15.2)
- [ ] **The coordinates are the shop's, not the camera's** (§15.3) — the one that must not fail
- [ ] **A short link resolves** (§15.4) — *the path I could not verify*
- [ ] An unreadable link produces a message and **no pin** (§15.5)
- [ ] Continue opens the Add Store form pre-filled, **and the map has moved there** (§15.6)
- [ ] Cancel leaves nothing behind (§15.7)
- [ ] Rotation keeps what was typed and does not re-read the clipboard (§15.8)
- [ ] Long-press still works, and everything built before still works (§15.8)
- [ ] Confirmed: still **no Kotlin source file** anywhere in the project

---

## 19. UPDATED PROJECT STATE

**COMPLETED PHASES**
- ✅ 0.1 · 0.2 · 0.3 · 1.1 · 1.2 · 1.3 · 2.1 · 2.2 · 2.3
- ✅ 3.1 · 3.2 · 3.3 · 3.4 · 3.5 · 3.6 · 3.7 · **3.8**
- ✅ 4.1 · 4.2
- 🔄 **3.8** — *awaiting your on-device test*

**CURRENT PHASE**
- Phase 3.8 — awaiting the on-device test

**CURRENT DATABASE VERSION**
- **1.** Unchanged. Three tables: `stores`, `completion_history`, `app_settings`.

**CURRENT FILES** (changed this phase)

```
app/src/main/java/com/cph/alpharoute/domain/GeoCoordinate.java               (NEW - renamed from RoutePoint)
app/src/main/java/com/cph/alpharoute/domain/GoogleMapsLinkParser.java        (NEW)
app/src/main/java/com/cph/alpharoute/domain/PlaceLinkState.java              (NEW)
app/src/main/java/com/cph/alpharoute/data/repository/PlaceLinkRepository.java (NEW)
app/src/main/java/com/cph/alpharoute/ui/store/AddFromLinkBottomSheet.java    (NEW)
app/src/main/res/layout/sheet_add_from_link.xml                             (NEW)
app/src/main/res/drawable/ic_add_link.xml                                   (NEW)
app/src/test/java/com/cph/alpharoute/domain/GoogleMapsLinkParserTest.java    (NEW - 28 tests)

app/src/main/java/com/cph/alpharoute/domain/RoutePoint.java                  (DELETED - renamed)
app/src/main/java/com/cph/alpharoute/domain/RoutePlan.java                   (MODIFIED)
app/src/main/java/com/cph/alpharoute/navigation/RouteResponseParser.java     (MODIFIED)
app/src/main/java/com/cph/alpharoute/data/repository/RoutingRepository.java  (MODIFIED)
app/src/main/java/com/cph/alpharoute/ui/map/MapMarkerController.java         (MODIFIED)
app/src/main/java/com/cph/alpharoute/ui/route/RoutePreviewBottomSheet.java   (MODIFIED)
app/src/test/java/com/cph/alpharoute/navigation/RouteResponseParserTest.java (MODIFIED)
app/src/test/java/com/cph/alpharoute/data/repository/RoutingRepositoryTest.java (MODIFIED)
app/src/main/java/com/cph/alpharoute/ui/map/MapFragment.java                 (MODIFIED - the button + centring)
app/src/main/res/layout/fragment_map.xml                                     (MODIFIED - the button)
app/src/main/java/com/cph/alpharoute/AlphaRouteApp.java                      (MODIFIED)
app/src/main/res/values/strings.xml                                          (MODIFIED)

docs/PHASE_3.8_PIN_FROM_A_LINK.md                                           <- created this phase
docs/HANDOFF.md                                                              (MODIFIED)
```

**WORKING FEATURES** (verified)
- ✅ **216/216 unit tests pass** — 0 failures, 0 errors (verified 2026-09-25), including 28 new cases
- ✅ **Clean build succeeds** — 42 tasks executed → APK 9,649,815 bytes
- ✅ **The rename was verified in isolation** before any new code was written
- ✅ **Schema unchanged** — only `1.json` exists
- ✅ **Permissions unchanged** — the same four
- ✅ **Still zero Kotlin source files** — `compileDebugKotlin` = `NO-SOURCE`
- ⏳ **The paste flow on a device** — §15.1–15.8, and **especially §15.4**, the short-link path

**KNOWN ISSUES**
- ⚠️ **The short-link path is reasoned, not proven.** A bogus short code returns 404 (verified), which
  confirms the mechanism, but the success path needs a real share link. **This is the one thing to test
  first** (§15.4).
- **Not a share target yet** (§13.7) — pasting is manual for now. The parser is the expensive half and it
  is done.
- **The store name is not pre-filled** from the share text's first line (§13.7) — fifteen minutes whenever
  it is wanted.
- Home Setup does not offer a link import (§13.7).
- Carried risks: OSM data quality in remote PH rural areas · openrouteservice free-tier quota ·
  **no offline map** (policy, risk R5) · date boundary (AD-7 + AD-11).
- The project is **not under version control**. Still the strongest recommendation in the project.
- Trademark note: "Alfamart" is a third-party brand; fine for a private solo tool (D-5).

**PENDING PHASES** (not implemented)
- **4.3 Navigation hand-off + arrival monitoring** — the next *planned* phase
- 4.4 State recovery
- 5.1 Arrival-radius check · 5.2 Completion dialog · 5.3 Atomic completion transaction
- 6.1 Live tracker · 6.2 Daily history list · 6.3 Daily completion order · 6.4 Completion details ·
  6.5 Consistency check
- 7.1 Nearest pending store · 7.2 Recalculate after completion · 7.3 Home-aware heuristic · 7.4 No pending stores
- 8.1 Routing service review · 8.2 Road-based next store · 8.3 Multi-stop optimization · 8.4 Complete route preview
- 9.1 Local export · 9.2 Local import · 9.3 Optional cloud sync *(Firebase — deferred by your decision)*
- 10.1 Error handling · 10.2 Automated and manual testing · 10.3 Release preparation
- *(Not on the roadmap, candidates if wanted: being a share target; pre-filling the name; a link import for
  Home)*

---

## 20. STOP POINT

Phase 3.8 is complete. **22 files touched: 9 created, 12 modified, 1 deleted (renamed).**

**The pin is now exact instead of approximate.** Google Maps knows where every Alfamart is; the app can now
read that instead of asking you to guess from memory. With a 50 m arrival radius, that is the difference
between the arrival check working and never firing at all.

**Three things are worth calling out:**

1. **The 164-metre trap is why this phase exists.** A place link carries two coordinate pairs — the camera's
   and the shop's — and reading the wrong one pins the store on the wrong side of the building. The app
   would look like it worked and would simply never offer to complete that store, with nothing anywhere to
   explain why. Two tests exist purely to hold the right order, and one of them is a regression guard.

2. **The Add Store form did not change.** It already takes a latitude and a longitude, so the paste flow
   hands off to it rather than growing a "no coordinates yet" state inside it. That is why this phase needed
   no change to any existing screen's contract — and why the new flow could not break the old one.

3. **One path is reasoned, not proven, and I am flagging it rather than burying it.** Following a short
   link (`maps.app.goo.gl/…`, which is what Share actually gives you) needs a real link to test, and I
   could not make one. The mechanism is confirmed — a bogus code returns 404, so it is a plain redirector —
   and the code tries the header, then further hops, then the page body. **Please run §15.4 first and tell
   me exactly what happens**, whichever way it goes.

**Please run §15 and report** — especially **§15.4** (the short link), **§15.3** (the coordinates are the
shop's, not the camera's) and **§15.6** (Continue opens the form and the map has moved).

**Next on your instruction: Phase 4.3 — Navigation hand-off and arrival monitoring** — the foreground
service, the notification, and the active session, so a drive survives the phone being locked. Or, if you
would rather, the share-target version of this feature, which is the same parser with better plumbing.

**Phase complete. Do not proceed to the next phase until all acceptance criteria pass.**

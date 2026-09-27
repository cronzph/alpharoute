---
name: alpharoute-phase
description: Delivering one phase of the AlphaRoute / Alfamart Route Tracker Android project. Use when the user says "go <phase>" or "gawen naten ito" for this project, when adding a phase that is not on the roadmap, or when picking the work back up. Covers the working agreement, the build/verify loop, the recurring traps, and the document set every phase must update.
agent_created: true
---

# AlphaRoute — delivering one phase

**Read `docs/HANDOFF.md` FIRST.** It is the project's source of truth: frozen stack, all ADRs, the schema,
every accumulated trap, and the roadmap. This skill is the *checklist*; HANDOFF is the *content*. Do not
duplicate HANDOFF here — read it.

Root: `C:/Users/Admin/OneDrive/Documents/custom system/AlphaRoute` · package `com.cph.alpharoute`

## The working agreement (non-negotiable)

- **Java only**, zero Kotlin. **XML layouts only**, no Compose, no ViewBinding — use `findViewById`.
- **One phase at a time.** Complete only the requested phase, then STOP and wait for the device test.
- **Never invent data** — no store names, codes, route numbers, coordinates, counts, credentials.
- **Never auto-complete a store, never auto-start navigation.** Completion = in-radius **+** explicit YES.
- A store is never counted twice.
- **At most ONE clarifying question**, and only when critical. Decide and document otherwise.
- State each file's path and mark it **New / Existing / Modified / Replaced**. Give the **complete** file.
- No destructive Room migration once real data exists. Never `fallbackToDestructiveMigration()`.
- Response format: the **20-section phase format** (HANDOFF §10). Plain language first, always a clear
  recommendation. English; the user writes Taglish sometimes.
- The user **values verification** — never claim a phase complete without a build.

## The loop

1. **Plan first when asked.** The user may say "plan then execute" — produce the plan, then do it in the
   same turn. Do not stall waiting for approval when they have said go.
2. **Read the spec, not your memory of it.** Grep `docs/PHASE_0.3_SCREEN_PLAN.md` for the screen, and
   `docs/PHASE_0.1_REQUIREMENTS_REVIEW.md` for the requirement. The plan is authoritative over instinct.
   **Then check whether an earlier phase already delivered the roadmap row.** The roadmap is written ahead
   of the code and drifts: Phase 5.1 ("arrival-radius check") was fully implemented by 4.3's
   `ArrivalEvaluator`, and re-building it would have created a second definition of the arrival rule. If a
   row is already satisfied, **mark it delivered in HANDOFF and say so in the record** rather than
   implementing it twice.
2b. **If a phase's deliverable is a dead button on its own, fold in the phase that makes it work** — and
   say so explicitly in the record. 4.2 absorbed "Start navigation" from 4.3; 5.2 (the dialog) was built
   with 5.3 (the transaction) because a confirmation dialog with nothing behind it cannot be tested.
3. **Check the library sources, not the internet.** Extracted osmdroid 6.1.20 sources live at
   `/tmp/osmdsrc` (unzip the `-sources.jar` from the Gradle cache). Tutorials are for older releases and
   have been wrong twice already.
4. **Do risky/mechanical changes first, in isolation, and verify.** (Phase 3.8's rename: 8 files, build
   green, *then* the feature.) This is how "make no mistake" is satisfied.
5. **Put pure logic in `domain/` or `util/` and test it on the JVM.** Anything with a boundary or a parse
   belongs there. Package-private statics are the project's idiom for reaching into a class the JVM cannot
   fully load. **A `LiveData` needs `androidx.arch.core:core-testing` (`InstantTaskExecutorRule`) plus
   `testImplementation`, or `postValue` never delivers off a device.**
6. **Build and verify:** `./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain`
   Expect `BUILD SUCCESSFUL` and read the per-class results out of
   `app/build/test-results/testDebugUnitTest/*.xml`.
7. **When a test fails, check the expectation before the code.** In Phase 4.3 three tests failed because the
   *expected number* was wrong, not the logic — an order-of-magnitude slip on a degree span, and a band
   assumed wider than it is. Probe the real value independently (a throwaway `java Foo.java`) before
   asserting anything numeric.
8. **Report honestly.** If a path could not be verified, say so and give the exact test step.

## Before quoting evidence

- **Run `./gradlew clean` before quoting an APK size** — an incremental debug build leaves stale dex
  shards and inflates it (Phase 3.7 reported 11.1 MB against a true 9.6 MB).
- Check the four invariants every phase: `app/schemas/` still only `1.json` · merged-manifest permissions
  unchanged · `values-night/` has no `themes.xml` · `find . -name "*.kt" | wc -l` is **0**.
- `clean` takes ~3-4 minutes here (OneDrive). If it fails with a file lock, run it again.

## Recurring traps — check for these every time

- **`--` inside an XML comment** fails the resource parse. It has happened twice. Never use dash separators.
- **`[longitude, latitude]`** — openrouteservice, in the request *and* the response. Silent: the map draws
  a line in the wrong place and nothing errors.
- **A Google Maps place link has TWO coordinate pairs.** `@` is the camera, `!3d`/`!4d` is the place —
  measured 164 m apart, against a 50 m arrival radius. Prefer `!3d`/`!4d`.
- **`String.format` uses the device locale** — always pass `Locale.US` for numbers and URIs.
- **`<queries>`** is required for `resolveActivity()` to see Google Maps on Android 11+.
- **`@Update` in Room replaces the WHOLE row** — never write a partial entity without preserving
  `status`/`createdAt`/`completedAt`.
- **Observe LiveData with `getViewLifecycleOwner()`**, never `this`.
- **`removeObserver(this::onFix)` removes nothing** — two different lambda objects. Store the observer in a
  field. This was a live bug in Phase 4.3.
- **A foreground service may only be started from a visible screen.** Android 12+ throws
  `ForegroundServiceStartNotAllowedException` for a background start, and a `BOOT_COMPLETED` receiver is a
  background context — so **nothing can resume a session after a reboot**. Never design one.
- **`foregroundServiceType` is declared in TWO places** — the manifest and `startForeground()`. Both, plus
  `FOREGROUND_SERVICE_LOCATION` from API 34. A mismatch throws at start on the drive, not at build time.
- **A "how old is this?" check must refuse a future timestamp, not clamp it.** If the clock moved
  backwards, `now - lastUpdated` is negative and reads as *brand new* — the opposite of the truth. Return
  the "too old" answer for `lastUpdated > now`. (`NavigationRecovery.evaluate`.)
- **"Restart the service" is not "restart the session."** Restarting a session rewrites the persisted
  reference and **resets the timestamp the freshness rule itself reads**, so a stale session starts looking
  fresh. Keep a service-only path that touches nothing. (`restartMonitoring()` vs `start()`.)
- **A recovery/repair check must not write to the row it is judging.** `app_settings.lastUpdated` advances
  on every mutation, so writing during the check would refresh its own input. Read, then clear only
  deliberately.
- **One failure, two causes, two buttons.** "No position" means *permission gone* **or** *service stopped*;
  decide which button to offer by permission state, never by the distance. The wrong button is one that
  cannot work.
- **`registerForActivityResult` must be called on a field**, before the fragment is created. A launcher
  built inside a method throws.
- **`@Transaction` covers ONE DAO only.** An operation spanning two tables (insert history + flip the
  store) must live in a single DAO or it is not atomic at all. Phase 5.3's `CompletionTransactionDao`
  exists for exactly this.
- **Throwing is what rolls a Room transaction back.** `setTransactionSuccessful()` is called only when the
  `@Transaction` method returns normally, so a half-applied write must **throw** — returning an error value
  would commit it.
- **A DAO does not change the schema.** Only entities do. Registering a new DAO leaves `1.json` untouched
  and `DATABASE_VERSION` where it was. Do not bump the version "because a DAO was added".
- **Two `PendingIntent`s with the same request code are the SAME intent.** Identity is action + data +
  component — **not** the extras. Two notifications both targeting `MainActivity` share one PendingIntent,
  and `FLAG_UPDATE_CURRENT` lets the last one posted overwrite the other's extras. Use distinct request
  codes (ongoing 0, arrival 4102).
- **Never let a transaction read the clock itself.** Read it once in the repository and pass it down; two
  readings let a write begun at 23:59:59.999 be filed under the previous day.
- **Pin `Locale.US` on any `yyyy-MM-dd` pattern.** Some locales use non-ASCII digits and some carry a
  different calendar — a Buddhist-calendar locale renders 2026 as 2569. A key compared and sorted as a
  string must always be the same shape.
- **A rotation mid-write loses the callback** — the write runs on an Application-owned thread, so its
  callback is addressed to the destroyed fragment. Do not save a result; **re-read the database**, which is
  the only surviving record (AD-4).
- **`@Update` rewrites every column.** To flip one field, use a targeted `UPDATE` naming only that field —
  then the statement is incapable of clobbering `createdAt` or a coordinate.

### Migration traps (Phase 7.0 was the first migration — these cost real thought)

- **`ADD COLUMN x NOT NULL DEFAULT v` is the only form SQLite allows** on a table that already has rows.
  No default means the migration cannot run at all. And for existing rows the default must be the **true**
  value, not a placeholder.
- **A migration must be REGISTERED** — `.addMigrations(MIGRATION_1_2)` in `AppDatabase.getInstance`.
  Writing the object and forgetting to register it is a crash at first launch.
- **An index name in a migration must be Room's own**: `index_<table>_<col>`, and
  `index_<table>_<col1>_<col2>` for a composite. **Read the names out of `app/schemas/.../N.json`** — never
  guess them.
- **Changing a unique index means changing the ENTITY too.** The migration and `@Entity(indices = ...)`
  must agree or Room's schema validation fails at open.
- **`./gradlew` exporting `N.json` proves the ENTITY, not the migration.** Two artefacts, one shape. Read
  both and compare — it is the strongest check available without a device, and it is still not proof.
- **A migration can only be truly verified on a device.** Room validates at open. Flag it as the
  highest-priority test in the record.
- **Never reach for `fallbackToDestructiveMigration()`** — it makes the mismatch go away by deleting the
  user's data.

### Changing a guarantee (AD-5, Phase 7.0)

When a feature needs an invariant to change, **scope it rather than remove it**, and say so in the record.
`UNIQUE(storeId)` → `UNIQUE(storeId, roundNumber)` kept the guarantee ("no double counting") and moved its
boundary (per round instead of per lifetime). Anything that *asked* the old question — a checker, a
count, a screen — must be re-scoped in the same phase, or it will start reporting the new normal state as
a fault.

### Suggestion, routing and backup traps (Versions 7.1 – 9.2)

- **A strategy must answer for every sparse context, and must LABEL which claim it is making.** "Nearest to
  you" and "first in your list" are different statements; a card that shows the same words for both is
  overstating what the app knows. One `Reason` per case, one string per reason.
- **A matrix row has no "distance to yourself" entry.** The grid is `sources × destinations`, so with one
  source the row is one distance per store. A test fixture with a leading zero models a store where the user
  is standing, and `indexOfNearest` will correctly pick it — **two tests failed on this in Version 8 and the
  expectation was wrong, not the code.**
- **A wrong optimization request is a confident answer to the wrong question.** There is no error to catch.
  Assert the body **field by field**: job ids, coordinate order, service time, vehicle count, profile, and
  the absence of `"options"`.
- **Only `"job"` steps are stops.** A VROOM route's `"start"` and `"end"` carry a location and a distance
  too. Counting them adds **two phantom stops** to every route. Filter on `"type"`, **not** on the presence
  of an id — the start and end steps have ids of their own.
- **Never compare a road distance against a straight line as if they were the same measure.** A road
  distance is always ≥ the straight line. Mixing them when only one is known is a **documented compromise**,
  not an oversight — refusing to answer when one leg failed would be worse.
- **Never let a network call run on every GPS fix.** The position updates every few seconds; without a
  threshold a 500/day allowance is gone in under an hour. Use a **distance** threshold (250 m), not "has the
  fix changed" — GPS jitters by a few metres on a table.
- **Check a service's limits BEFORE building on it.** The roadmap asks for it and it paid off: re-verified
  2026-09-27 as directions 2,000/day, matrix 500/day, optimization 500/day with **50 jobs / 3 vehicles**.
- **A backup must reference stores by `storeCode`, never by row id** — ids are auto-generated, so a backup's
  ids mean nothing in the database it is restored into.
- **Write a backup from an explicit field list**, so a column added later cannot silently start appearing in
  backups. Assert the file contains no `apikey`, `token`, `password` or `authorization`.
- **`REPLACE` must delete completions BEFORE stores** — AD-6 `RESTRICT` refuses the other order, and it fails
  on the first store.
- **Validate before writing, and report EVERY problem** — a user with three faults should not have to fix
  one, retry, and discover the next.
- **Use the system file picker for import/export.** No storage permission, no guessed paths, and it is the
  only thing that works on Android 10+.

### The LiveData trap that cost three bugs in one afternoon (Version 11)

- **`LiveData.getValue()` is `null` until something observes it.** A screen must **never** read a
  repository's LiveData that it does not itself observe. It *appears* to work, because another screen
  usually observed it first — so it fails only in the case hardest to notice: process death with the back
  stack rebuilt, or a first render arriving from a *different* source than the one being read.
  - **The three real bugs**: the route screen claiming "nothing left to visit in this round" with a full
    round outstanding; the Dashboard offering "Set a Home location" when Home was already set; the
    consistency check reading the round from an unobserved row.
  - **The fix**: a one-shot read (`findAllNow`, `getNow`), or ask the **ViewModel**, which is the object
    that actually does the observing. `DashboardViewModel.hasHome()` exists for exactly this reason.
  - **Audit rule**: grep for `.getValue()` and justify every hit. Safe only when the LiveData is a
    `MutableLiveData` the class owns and initialises, or when the class observes it itself.
- **A read that depends on another screen having run is not a read.** If correctness relies on the
  Dashboard having been opened first, the code is wrong even when a manual test passes.

### Glassmorphism rules (Version 11)

- **Put the backdrop on `android:windowBackground`, not on each screen's root.** Every screen gets it with
  no per-layout work, the launch window uses it (no flash of a flat colour), and `drawable-night/` picks the
  dark variant automatically.
- **Every screen root must then be `@android:color/transparent`.** An opaque root covers the gradient and
  every translucent card silently becomes a flat panel with a faint tint. Nothing looks broken enough to
  explain why, which is what makes this the most dangerous mistake in the whole effect.
- **A glass card is four values**: a translucent fill, a 1dp rim **lighter** than the fill, a large corner
  radius (20dp), and **elevation 0**. A hard drop shadow under frosted glass looks wrong, and Material's
  elevation tint would grey the fill.
- **Fade a radial blob to a zero-alpha version of its own hue**, never to `@android:color/transparent` —
  that is transparent **black**, so a blue blob fades through grey and leaves a dirty smudge.
- **A bottom sheet rounds at the TOP only.** Its bottom edge is off-screen; rounding it leaves two slivers
  of the screen visible through the gap.
- **Keep dialogs the most opaque surface.** A dialog is a question the user must read and answer; one they
  have to squint at is a worse trade than a slightly less glassy corner.
- **Changing what a card *is* means changing its colour roles.** The accent cards moved off
  `colorSecondaryContainer`/`colorOnSecondaryContainer` when they stopped being that container — leaving
  them would have been a role that no longer describes the thing.
- **A card and an app bar are NOT the same surface, even though they share a palette.** A card has nothing
  behind it but the gradient, so it can be properly translucent (72%). A bar has **the whole scrolling app
  behind it**, so it cannot — at anything less than near-opaque the scrolled content is still legible and
  renders *through* the bar's own title. This was a real bug: a toolbar fading to 100% transparent at its
  bottom edge made a scrolled list collide with the title. **Bars are 97% opaque; only cards are glass.**
- **The HTML mockup shows colours; it cannot show what scrolls behind what.** Two separate changes looked
  right in the mockup and were wrong on a device. Anything about layering, scrolling or z-order must be
  judged on the phone, not in the preview.
- **`list.size()` and a null-skipping loop are two definitions of "a row".** If a loop skips nulls, every
  count of that list must skip them too — or one null makes the total disagree with the counters it
  summarises. This was a **real bug in Version 6**, in both `TrackerCalculator` and `ConsistencyChecker`.
  Count by walking whenever the loop skips.
- **Format a `YYYY-MM-DD` key as a key, not as a timestamp.** The History screen groups by the stored key,
  so it must display that key; formatting a timestamp from the group can render as the neighbouring day
  after a timezone change. `DateTimeHelper.dateTextFromKey`.
- **A tab cannot be shown underneath a pushed screen** — the pushed screen *hides* it. Any "jump to a tab"
  action must pop the whole back stack first, or the user lands on a hidden tab and sees a blank screen.
- **A pushed screen's toolbar title must come from the fragment instance**, never from a tag→title map in a
  field: the field is empty after a process death while the framework restores the back stack, so the
  screen returns with no title.
- **Batch read-only phases, but verify as usual.** Version 6's five phases were built in one pass because
  none of them writes, changes the schema, or needs a permission — they share one shape (observe Room,
  derive, render). Write phases are never batched this way. Still run the clean build, and still write a
  record: §20's unticked box is what stops the next phase starting on unverified ground.
- **`mapView.onResume/onPause/onDetach`** are all mandatory; a missing `onDetach` leaks a map per visit.
- **A javadoc edit can delete the live code below it** — never put live code in `old_string`.

## Every phase must update

| File | What changes |
| --- | --- |
| `docs/PHASE_<n>_<NAME>.md` | New. The 20-section record. |
| `docs/HANDOFF.md` | Last/next phase · test count · APK size · Java file count · the file inventory · the phase-history row · the roadmap row · any new trap · §14 open items · the quick-start block |
| `.workbuddy-ai/memory/YYYY-MM-DD.md` | Append a session note: what was built, the decisions, what was verified, new traps, next step. |

**Adding a phase that is not on the roadmap:** pick the number by **version subject**, not by date order —
a pinning feature belongs in Version 3 even if built after 4.2 — and say so explicitly in the record so it
does not look like an accident. Prefer a free number over renumbering.

## Where things live

`domain/` pure Java, no Android imports · `util/` pure helpers · `data/db` Room · `data/repository` the
only route to data (and the only place HTTP happens) · `navigation/` routing config, the route parser, the
hand-off launcher · `location/` · `ui/{map,store,route,settings,dashboard,history,common}`

**A Fragment never touches Room or HTTP directly.** A route, a location and a pasted link are each held as
`LiveData` on a repository in the application container, so two screens can observe the same thing without
a reference to each other — and it survives rotation.

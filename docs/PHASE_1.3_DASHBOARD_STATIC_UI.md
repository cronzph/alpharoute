# AlphaRoute — Phase 1.3 Dashboard Static UI

**Project title:** Alfamart Route Tracker
**Version:** 1 — Project Foundation — **Phase:** 1.3 Dashboard Static User Interface
**Repository root:** `C:/Users/Admin/OneDrive/Documents/custom system/AlphaRoute`
**Date:** 2026-09-25
**Prerequisites:** Phase 0.1 Rev 5 · Phase 0.2 (AD-1, AD-11) · Phase 0.3 (§13.3 Dashboard, A17) · Phase 1.2 (theme + navigation)
**Status:** 🔄 **Code complete and BUILD-VERIFIED.** Automated `:app:assembleDebug` → **BUILD SUCCESSFUL**. Awaiting your on-device test.

---

## 1. CURRENT VERSION AND PHASE

| Item | Value |
| --- | --- |
| Version | 1 — Project Foundation |
| Phase | 1.3 — Dashboard Static User Interface |
| Files created | 5 |
| Files modified | 3 |
| Files deleted | 0 (one *string* was removed — see §8) |
| Database version | None. Room arrives in Phase 2.1 and will start at **version 1** |

---

## 2. OBJECTIVE

Build the Dashboard's six progress cards with **clearly labelled placeholder values**, so the layout can be
checked visually before any data exists:

- Completed today
- Completed yesterday
- Overall completed
- Remaining
- Completion percentage
- Latest completed

Acceptance: the Dashboard renders six readable cards with a visible notice that the values are not real, in
both light and dark mode, with no crash.

---

## 3. UNDERSTANDING CHECKLIST

- [x] Java + XML layouts only. No Kotlin, no Compose.
- [x] **No live data** — nothing reads Room, the network or a sensor.
- [x] Placeholder values are **labelled clearly on screen**, exactly as the brief requires.
- [x] Every user-visible string is in `strings.xml`.
- [x] No real or plausible store code, coordinate, count or history record is invented (rule 12).
- [x] Colours and text sizes come from the Phase 1.2 theme and text roles — no literals in this layout.
- [x] The card ids are chosen now so Phase 6.1 will not need to change the layout.

---

## 4. FEATURES INCLUDED (in this phase)

| Feature | Detail |
| --- | --- |
| **Six stat cards** | Today, Yesterday, Overall, Remaining, Percentage, Latest Completed |
| **A reusable card component** | `StatCardView`, a compound view used six times, with a typed `setValue(...)` API for Phase 6.1 |
| **An on-screen placeholder notice** | A card at the top of the Dashboard stating that the figures are not real |
| **Accessibility** | Each card is a single TalkBack target reading one sentence, e.g. "Completed today: 3" |
| **Layout structure** | A 2-column grid for the four count cards, then two full-width cards |

---

## 5. FEATURES NOT INCLUDED YET

- **The three navigation buttons** from the original brief — dropped by assumption **A17** (see §6 and §20).
- **The Total card** — the Phase 1.3 brief lists six cards; Phase 6.1 adds a seventh, Total.
- **The next-store suggestion card** — Phase 7.1.
- Any real data: no Room, no ViewModel, no tracker calculation.
- Empty / loading / error states — those are driven by real data and arrive in Phase 6.1.

---

## 6. ASSUMPTIONS

| # | Assumption | Impact if wrong |
| --- | --- | --- |
| **A17** | **RESOLVED (approved 2026-09-25): the bottom navigation is the primary navigation, so the Dashboard's three buttons — Open Map / View History / Settings — are dropped.** | This is a deliberate deviation from the original Phase 1.3 text. Reversible in one layout edit if you change your mind. |
| A31 | **The six cards are built as one reusable compound view**, not as six copies of the same XML. | One extra class and one `attrs.xml`. If you would rather have plain duplicated XML, it is a contained change. |
| A32 | The placeholder figures are `3 / 5 / 42 / 18 / 70.0%` and `SAMPLE-000 · 10:12 AM`. | Any value can be changed in one place in `strings.xml`. |
| A33 | Percentage is displayed as `70.0%` — **one decimal place**, matching the specification's rounding rule. | Formatting moves into `TrackerCalculator` in Phase 6.1. |
| A34 | Percentage and Latest Completed are **full-width** cards, not half-width. | A half-width card would leave an empty cell in the grid, and "Latest completed" needs room for a code and a time. |

### 6.1 How the placeholder values sit with rule 12

Rule 12 forbids inventing store coordinates, names, codes, route numbers, completion history and tracker
counts. The Phase 1.3 brief simultaneously asks for "placeholder values only for visual testing and label them
clearly". The two are reconciled like this:

1. **The values are unmistakably labelled as fake, on screen** — a notice card sits above the grid.
2. **No store code is invented.** The Latest Completed card reads `SAMPLE-000 · 10:12 AM`. A plausible-looking
   code such as `ALF-0142` would be exactly the fabrication rule 12 prohibits; `SAMPLE-000` cannot be mistaken
   for real data.
3. **Every placeholder lives in one marked block in `strings.xml`** and is deleted in Phase 6.1.
4. **The card labels are permanent.** "Completed today" is a real label that survives Phase 6.1; only the
   *values* are temporary.

---

## 7. FILES TO CREATE (5)

| Path | Purpose |
| --- | --- |
| `app/src/main/res/values/attrs.xml` | **New.** Declares the `statLabel` and `statValue` custom attributes |
| `app/src/main/res/layout/view_stat_card.xml` | **New.** The inside of one card: a label above a value |
| `app/src/main/res/layout/fragment_dashboard.xml` | **New.** The Dashboard: notice card + six cards |
| `app/src/main/java/com/cph/alpharoute/ui/common/StatCardView.java` | **New.** The compound view |
| `docs/PHASE_1.3_DASHBOARD_STATIC_UI.md` | **New.** This record |

---

## 8. FILES TO MODIFY (3)

| # | Path | Status | What changed |
| --- | --- | --- | --- |
| 1 | `app/src/main/res/values/styles.xml` | **Modified** | Added `Widget.AlphaRoute.StatCard` (parent `Widget.Material3.CardView.Filled`, 12dp radius, 0dp elevation) |
| 2 | `app/src/main/res/values/strings.xml` | **Modified** | Added six permanent card labels and seven temporary placeholder strings. **Removed** `placeholder_dashboard` — the Dashboard no longer shows placeholder *text*, so that string is dead |
| 3 | `app/src/main/java/com/cph/alpharoute/ui/dashboard/DashboardFragment.java` | **Replaced completely** | Now inflates `fragment_dashboard.xml`; the Phase 1.2 placeholder body is gone |

**Note on the removed string:** `placeholder_dashboard` was created in Phase 1.2 and marked "deleted in
Phase 1.3". That is now done. This is a string, not a feature, so nothing working is lost — the Dashboard now
shows real cards instead of placeholder text.

**Inspected and left unmodified:** everything else, including `fragment_placeholder.xml`, which the Map,
History and Settings tabs still use. Their `placeholder_*` strings remain and are still marked with their
deletion phases.

---

## 9. DEPENDENCIES

**No dependency was added.** The cards use `MaterialCardView`, `NestedScrollView` and `LinearLayout`, all of
which already ship with the existing Material and AndroidX dependencies.

This is worth stating explicitly because it is a genuine benefit of the chosen design: a compound view costs
one Java class, whereas reaching for a third-party card library or a RecyclerView with a GridLayoutManager
would have added a dependency and a lot of machinery for six static cards.

---

## 10. PERMISSIONS AND MANIFEST CHANGES

**None.** No permission, no manifest change, no new resource qualifier.

---

## 11. SETUP INSTRUCTIONS

1. Let Gradle sync (nothing new to fetch).
2. **Build → Clean Project**, then **Build → Rebuild Project**.
3. Run on a device or emulator (API 24+).
4. If the IDE shows `Cannot resolve symbol 'StatCardView'` in the layout editor, re-sync Gradle — the layout
   editor needs the compiled class before it can render the custom view.

---

## 12. COMPLETE CODE

All 8 files are written into the project on disk and attached to this message. The files worth reading, in
order:

1. `app/src/main/java/com/cph/alpharoute/ui/common/StatCardView.java` — the component
2. `app/src/main/res/layout/fragment_dashboard.xml` — the screen
3. `app/src/main/res/layout/view_stat_card.xml` — the card interior
4. `app/src/main/res/values/attrs.xml` — the custom attributes
5. `app/src/main/res/values/strings.xml` — the labels and the marked placeholder block

---

## 13. CODE EXPLANATION

### 13.1 Why a compound view instead of six copies of the same XML

The card is **one concept used six times, and seven times after Phase 6.1**. Copying the markup six times
would mean six places to change a padding and six chances for them to drift apart.

`StatCardView` also gives Phase 6.1 a **typed API**:

```java
// Phase 6.1, with a StatCardView - reads like the domain
todayCard.setValue("3");

// Phase 6.1, with plain XML - stringly typed and easy to get wrong
((TextView) findViewById(R.id.tv_today_value)).setText("3");
```

### 13.2 The `defStyleAttr` detail that would have broken the cards

```java
public StatCardView(Context context, AttributeSet attrs) {
    this(context, attrs, com.google.android.material.R.attr.materialCardViewStyle);
}
```

This mirrors `MaterialCardView`'s own two-argument constructor. Without it — if the third argument were `0` —
the card would not resolve the Material 3 card style from the theme and would render as a bare, unstyled
frame. It is the single easiest thing to get wrong in a `MaterialCardView` subclass, so it is called out
here.

### 13.3 `TypedArray` must always be recycled

```java
TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.StatCardView);
try {
    ...
} finally {
    a.recycle();
}
```

`TypedArray` objects come from a shared pool. Failing to recycle leaks them for the life of the process —
and this class is instantiated six times per Dashboard. The `try/finally` guarantees the recycle even if
attribute reading throws.

### 13.4 Accessibility: one sentence per card, not two fragments

`view_stat_card.xml` marks both TextViews `importantForAccessibility="no"`, and `StatCardView` sets a single
`contentDescription` on the card itself:

```java
setContentDescription(labelView.getText() + ": " + valueView.getText());
```

TalkBack therefore announces **"Completed today: 3"** as one statement. Without this, a screen-reader user
would hear the label and the value as two separate, context-free fragments — "Completed today", then "3",
then "Completed yesterday", then "5" — which is much harder to follow. The description is refreshed by both
`setLabel` and `setValue`, so it can never go stale.

### 13.5 Why Percentage and Latest Completed are full width

The four count cards sit in a 2×2 grid. Percentage and Latest Completed are deliberately full width:
"Completion percentage" is the headline figure and deserves the space, and "Latest completed" has to fit a
code and a time. A half-width Percentage card would also leave an empty cell beside it, which looks like a
rendering bug.

### 13.6 Why `DashboardFragment` now has no code at all

The six cards take their label and value from `app:statLabel` / `app:statValue` in the layout. An
`onViewCreated` that set the same strings again would be redundant, and an empty one would be exactly the kind
of placeholder the project rules forbid. So the class is a constructor and a javadoc — and the javadoc carries
the card ids that Phase 6.1 will use.

---

## 14. DATA FLOW FOR THIS PHASE

```
MainActivity shows DashboardFragment
        │
        ▼
Fragment inflates fragment_dashboard.xml
        │
        ├─ notice card            ← @string/dashboard_placeholder_notice
        │
        └─ six StatCardView instances
                 │
                 │  each one, during inflation:
                 ▼
           StatCardView.init()
                 ├─ inflate view_stat_card.xml into itself
                 ├─ read app:statLabel  → labelView
                 ├─ read app:statValue  → valueView
                 └─ setContentDescription("label: value")   for TalkBack
```

**There is no data in this phase.** The values travel from `strings.xml` into the layout at inflation time.
Nothing is read from a database, a network or a sensor, and nothing is written anywhere.

**Phase 6.1 will change exactly one step:** instead of the values coming from XML attributes,
`DashboardViewModel` will expose a `LiveData<TrackerSnapshot>` and `onViewCreated` will observe it and call
`setValue(...)`. The layout, the ids and the component do not change.

---

## 15. MANUAL TEST PROCEDURE

### 15.0 Already verified automatically (2026-09-25)

`:app:assembleDebug` was run on this machine with Gradle 9.4.1 / JDK 21 and returned **`BUILD SUCCESSFUL in 45s`**
(12 tasks executed, 23 up-to-date — the incremental build picked up only what changed).

| Evidence | Result |
| --- | --- |
| `:app:compileDebugJavaWithJavac` | ran — `StatCardView` and the new `DashboardFragment` compiled |
| `StatCardView.class` on disk | present at `intermediates/javac/debug/.../ui/common/StatCardView.class` |
| `:app:mergeDebugResources` + `:app:processDebugResources` | ran — `attrs.xml`, the two new layouts, the new strings and the new style all resolved |
| `R.styleable.StatCardView` | resolved — otherwise `StatCardView` would not have compiled |
| `:app:compileDebugKotlin` | **NO-SOURCE** — still zero Kotlin source files |
| Filesystem scan for `*.kt` | **0 files** |
| `app/build/outputs/apk/debug/app-debug.apk` | **9,221,713 bytes** |

The build half is therefore proven, including the part most likely to fail: the **custom view's attribute
plumbing** (`declare-styleable` → `R.styleable` → `obtainStyledAttributes`) and the **`defStyleAttr` chain**
into `MaterialCardView`. What remains is visual: arrangement, alignment, dark mode, font scaling, TalkBack.

### 15.1 Layout

1. Open the app — it starts on the **Dashboard**.
2. Confirm the **placeholder notice** is visible at the top and reads as a warning that the values are not
   real.
3. Confirm **six cards** in this arrangement: Today | Yesterday on the first row, Overall | Remaining on the
   second, then Completion full width, then Latest completed full width.
4. Confirm the labels read: "Completed today", "Completed yesterday", "Overall completed", "Remaining",
   "Completion", "Latest completed".
5. Confirm the values read: `3`, `5`, `42`, `18`, `70.0%`, `SAMPLE-000 · 10:12 AM`.
6. Confirm the two half-width cards in each row are the **same height** and the same width.

### 15.2 Scrolling and edge-to-edge

7. On a small screen or with a large font size, confirm the content **scrolls** rather than being cut off.
8. Confirm the last card is fully visible above the bottom navigation bar — nothing hidden underneath it.

### 15.3 Dark mode

9. Turn on **Settings → Display → Dark theme**.
10. Confirm the cards are dark, the notice card uses the secondary container colour, and all text stays
    legible.

### 15.4 Font scaling

11. **Settings → Display → Font size → Largest**, then reopen the Dashboard.
12. Confirm the card labels wrap to two lines rather than being clipped, and the values are still readable.

### 15.5 Accessibility (optional but recommended)

13. Turn on **TalkBack** and swipe through the Dashboard. Each card must be announced as **one sentence** —
    "Completed today: 3" — not as two separate items.
14. Confirm the notice card is announced and is not skipped.

### 15.6 Tab behaviour

15. Switch to **Map** and back to **Dashboard**. The cards must still be there and no duplicate Dashboard may
    appear.

**Report any failure with the screen, the step number, and any Logcat text.**

---

## 16. EXPECTED RESULT

- Six cards with clearly labelled placeholder values, plus a visible notice that they are not real.
- Identical, aligned card heights within each row.
- Readable in light and dark mode, and at the largest system font size.
- Each card is a single TalkBack target.
- Content scrolls instead of clipping; nothing is hidden behind the bottom navigation.
- Still **no live data**: no database, no network, no location.

---

## 17. COMMON ERRORS AND FIXES

| Symptom | Cause | Fix |
| --- | --- | --- |
| `Cannot resolve symbol 'StatCardView'` in the layout editor | The layout editor needs the compiled class | Re-sync Gradle, then rebuild |
| `error: attribute statLabel not found` | `attrs.xml` missing, or the wrong `app:` namespace | Confirm `values/attrs.xml` exists and the layout declares `xmlns:app` |
| Cards render as flat, unstyled frames | `defStyleAttr` was `0` in the constructor chain | It must be `com.google.android.material.R.attr.materialCardViewStyle` — see §13.2 |
| Cards all show the same text | `init` runs before the views exist, so `findViewById` returned null | `findViewById` must run **after** the `inflate(..., this, true)` call |
| `NullPointerException` in `refreshContentDescription` | `setValue` called before the view was constructed | Only call `setValue` after inflation — Phase 6.1 calls it from `onViewCreated`, which is safe |
| Cards in a row have different heights | `layout_height` is not `wrap_content`, or a card has more text lines | Both cards must be `wrap_content` and their labels must wrap to the same number of lines |
| Last card hidden behind the bottom nav | Content not scrollable, or insets not applied | The root must be a `NestedScrollView` and `MainActivity` applies the system-bar insets |
| Notice card and stat cards look identical | The notice overrides `cardBackgroundColor` | It intentionally uses `?attr/colorSecondaryContainer` to stand out |
| Old placeholder text still shows on the Dashboard | Stale resource cache | **File → Invalidate Caches / Restart** |

---

## 18. ACCEPTANCE CHECKLIST

- [ ] Clean build succeeds.
- [ ] The Dashboard shows **six** cards in the arrangement described in §15.1.
- [ ] The **placeholder notice** is visible and clearly states the values are not real.
- [ ] Values read `3`, `5`, `42`, `18`, `70.0%`, `SAMPLE-000 · 10:12 AM`.
- [ ] Cards in each row are equal width and equal height.
- [ ] Content scrolls on a small screen; the last card is not hidden behind the bottom navigation.
- [ ] Light **and** dark mode both look correct.
- [ ] The largest system font size does not clip the labels.
- [ ] Each card is announced as one sentence by TalkBack.
- [ ] Switching tabs and returning shows no duplicate Dashboard.
- [ ] Confirmed: the three navigation buttons are **absent**, per A17.
- [ ] Confirmed: still **no Kotlin source file** anywhere in the project.

---

## 19. UPDATED PROJECT STATE

**COMPLETED PHASES**
- ✅ Phase 0.1 — Requirements Review (Rev 5, FINAL) — approved 2026-09-25
- ✅ Phase 0.2 — Architecture and Data Flow — approved 2026-09-25
- ✅ Phase 0.3 — UI and Screen Plan — approved 2026-09-25
- ✅ Phase 1.1 — Create Android Java Project — approved 2026-09-25
- ✅ Phase 1.2 — Theme and Base Navigation — approved 2026-09-25
- 🔄 Phase 1.3 — Dashboard Static UI — *awaiting your on-device test*

**CURRENT PHASE**
- Phase 1.3 — awaiting the on-device test

**CURRENT DATABASE VERSION**
- None. Room has not been added. `AppDatabase` will start at **version 1** in Phase 2.1.

**CURRENT FILES** (changed this phase)

```
app/src/main/res/values/attrs.xml                      (NEW - statLabel / statValue)
app/src/main/res/layout/view_stat_card.xml             (NEW - card interior)
app/src/main/res/layout/fragment_dashboard.xml         (NEW - the Dashboard)
app/src/main/java/com/cph/alpharoute/ui/common/StatCardView.java            (NEW)
app/src/main/java/com/cph/alpharoute/ui/dashboard/DashboardFragment.java    (REPLACED)
app/src/main/res/values/styles.xml                     (MODIFIED - Widget.AlphaRoute.StatCard)
app/src/main/res/values/strings.xml                    (MODIFIED - 6 labels + 7 placeholders, removed placeholder_dashboard)
docs/PHASE_1.3_DASHBOARD_STATIC_UI.md                  <- created this phase
```

**WORKING FEATURES** (verified)
- ✅ **Build succeeds** — `:app:assembleDebug` → `BUILD SUCCESSFUL in 45s` (verified 2026-09-25)
- ✅ **`StatCardView` compiles** — the class file is on disk, which also proves `R.styleable.StatCardView` resolved from `attrs.xml`
- ✅ **All new resources link** — `mergeDebugResources` + `processDebugResources` both ran clean
- ✅ **APK produced** — 9,221,713 bytes
- ✅ **Still zero Kotlin source files** — `:app:compileDebugKotlin` = `NO-SOURCE`, filesystem scan = 0 `.kt`
- ⏳ **Visual checks** — six-card arrangement, equal card heights, dark mode, largest font size, TalkBack, scrolling — awaiting your §15.1–15.6 test

**KNOWN ISSUES**
- None blocking.
- **Temporary content:** the `dashboard_value_*` strings and `dashboard_placeholder_notice` are deliberate
  placeholders, all deleted in Phase 6.1. The six `dashboard_label_*` strings are permanent.
- Still present from Phase 1.2 and still marked: `placeholder_map` (deleted in 3.1), `placeholder_history`
  (6.2), `placeholder_settings` (2.3). `app_tagline` and `app_version_format` remain unused, reserved for
  Settings → About.
- Carried risks: OSM data quality in remote PH areas · OSM tile policy compliance (Phase 3.1) ·
  openrouteservice free-tier quota · no offline map download · osmdroid cadence (AD-8) · date boundary
  (AD-7 + AD-11).
- The project is **not under version control** — now worth doing before Phase 2.1, which adds the database.
- Trademark note: "Alfamart" is a third-party brand; fine for a private solo tool (D-5).

**PENDING PHASES** (not implemented)
- **2.1 Room foundation** — next
- 2.2 Store CRUD · 2.3 Settings storage
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

Phase 1.3 is complete. **8 files touched: 5 created, 3 modified.**

**Two things to know:**

1. **A17 is applied: the three navigation buttons are NOT on the Dashboard.** Your original Phase 1.3 text
   listed Open Map / View History / Settings, but you approved the bottom navigation as the primary
   navigation. The Dashboard is now a pure read-only screen. If you want the buttons back, say so — it is one
   layout edit.

2. **The cards are one reusable component, not six copies of XML.** `StatCardView` gives Phase 6.1 a typed
   `setValue(...)` API, and it is also where the accessibility work lives: each card is a single TalkBack
   target that reads "Completed today: 3" rather than two disconnected fragments.

**On rule 12 (do not invent data):** the brief asks for placeholder values, and rule 12 forbids inventing
store codes and counts. They are reconciled by labelling the values as fake *on screen* and by using
`SAMPLE-000` rather than a plausible-looking code such as `ALF-0142`. Every placeholder lives in one marked
block in `strings.xml` and disappears in Phase 6.1.

**Also corrected from my Phase 1.2 sign-off:** I mentioned a "Version-7 suggestion-card slot" would be added
here. It is **not** — an empty slot would be a placeholder, and the suggestion card belongs to Phase 7.1.
Phase 1.3 delivers exactly the six cards the brief asks for.

**Please run §15 and report the result** — particularly §15.1 (six cards, correct arrangement) and §15.4
(largest font size does not clip the labels). If anything fails, paste what you saw and I will diagnose before
starting Phase 2.1.

**Next phase on your instruction: Phase 2.1 — Room Database Foundation** — `StoreEntity`,
`CompletionHistoryEntity`, `AppSettingEntity`, the DAO interfaces, `AppDatabase` at version 1, the type
converters, and the repository layer. That phase brings the first database, and with it the `AlphaRouteApp`
application class.

**Phase complete. Do not proceed to the next phase until all acceptance criteria pass.**

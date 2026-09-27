# AlphaRoute — Map & Navigation Options

**Purpose:** decide how the Alfamart Route Tracker displays maps and delivers turn-by-turn navigation,
given the user's constraints: **Philippines**, **solo user**, **fully free** (no billing account, no card),
and a wish for **real-time navigation with live traffic and road rules**.
**Date:** 2026-09-25 · All figures verified against official documentation on this date.

---

## 1. The hard truth about live traffic

Live traffic data is proprietary and expensive. There is **no free routing API that provides real-time traffic**.
Verified:

| Service | Live traffic? | Card required? |
| --- | --- | --- |
| openrouteservice (free Standard plan) | **No** | No |
| OSRM demo server | **No** | No |
| Organic Maps (offline, open source) | **No** | No |
| Google Routes API (traffic-aware) | Yes | **Yes — billing account required** |
| Waze / HERE / TomTom commercial APIs | Yes | **Yes** |
| FreeRoute (third-party, claims "live traffic optional") | Claimed, **unverifiable** | Claimed no |
| **Google Maps app / Waze app (via Intent)** | **Yes** | **No** |

**Conclusion:** the only way to get genuine real-time traffic for **$0 with no card** is to hand the actual
driving over to a **free consumer navigation app** — Google Maps or Waze — which is already installed on the
phone. That is Option D below.

> **Note on FreeRoute:** it advertises a free tier (5,000 requests/day, no credit card) and "live traffic
> optional". I could not verify its traffic source, terms, longevity, or data quality, its paid plan is listed as
> "coming soon", and building a core dependency on an unvetted vendor is a real risk. **Not recommended.** It is
> listed only for completeness.

---

## 2. OPTION D — Free Google Navigation hand-off  ← RECOMMENDED

### 2.1 The key finding

Google's plain Android **Maps Intents** launch the Google Maps app for turn-by-turn navigation.
The official Android documentation for these intents **specifies no API key, no billing, no Google Maps Platform
account, and no quota**. They simply hand a destination to an app that is already on the device.

Verbatim from the official docs:

> "Use this intent URI to launch Google Maps navigation with turn-by-turn directions to the address or coordinate
> specified. **Directions are always given from the user's current location.**"

URI format (official):

```
google.navigation:q=latitude,longitude
```

Optional parameters: `mode=d` driving (default), `b` bicycling, `l` two-wheeler, `w` walking;
`avoid=t` tolls, `h` highways, `f` ferries.

The only stated prerequisite is that a receiving app is installed — hence the documented `resolveActivity()` check.

### 2.2 What the user gets — for $0

Because the Google Maps **app** does the navigating, the user gets the full consumer experience:

- **Real-time turn-by-turn** with automatic rerouting
- **Live traffic** (and traffic-aware ETA)
- **Road rules:** one-way streets, turn restrictions, no-left-turn rules, tolls, ferries, motorway rules
- **Voice guidance**, **lane guidance**, **speed limits**, step-by-step distance
- Continuous updates with no code from us

**Cost: $0. No API key. No billing account. No card. No quota. No rate limit. Unlimited.**

### 2.3 How the flow works in our app

```
User taps an orange (pending) store marker
        |
        v
Store details bottom sheet  (route no. / code / name / area / status)
        |
        v
"Get Directions"  -> in-app route preview (free routing service or straight line)
        |
        v
"Start Navigation"  -> Intent(ACTION_VIEW, "google.navigation:q=<lat>,<lng>&mode=d")
        |                setPackage("com.google.android.apps.maps")
        |                guard with resolveActivity()
        v
Google Maps app takes over: turn-by-turn + live traffic + road rules + voice
        |
        |   (our app keeps a FOREGROUND LOCATION SERVICE running)
        v
Distance to store <= arrivalRadius ?
        |-- NO  -> keep watching
        +-- YES -> post a notification: "Arrived at <store>. Store completed?"
                        |
                        v  (user taps the notification, returns to our app)
                   Completion dialog:  Store completed?   [ NO ]  [ YES ]
                        |
                        |-- NO  -> store stays PENDING, marker stays orange, no history, no counter change
                        +-- YES -> atomic Room transaction (Phase 5.3) -> marker turns green -> tracker refreshes
```

Nothing about the completion rules changes: in-radius + explicit **YES**, duplicate-protected, one history row
per store.

### 2.4 Exact intent code (verified against the official docs)

```java
// Launch Google Maps turn-by-turn to a coordinate. No API key, no billing, no account.
Uri gmmUri = Uri.parse("google.navigation:q=" + lat + "," + lng + "&mode=d");
Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmUri);
mapIntent.setPackage("com.google.android.apps.maps");

if (mapIntent.resolveActivity(getPackageManager()) != null) {
    startActivity(mapIntent);
} else {
    // Google Maps is not installed - show a clear message and offer the fallback navigator.
}
```

**Waze alternative** (also free, no key — excellent crowdsourced traffic and hazard reports):

```java
Uri wazeUri = Uri.parse("https://waze.com/ul?ll=" + lat + "%2C" + lng + "&navigate=yes");
Intent wazeIntent = new Intent(Intent.ACTION_VIEW, wazeUri);
wazeIntent.setPackage("com.waze");

if (wazeIntent.resolveActivity(getPackageManager()) != null) {
    startActivity(wazeIntent);
}
```

### 2.5 IMPORTANT — do not confuse this with the Navigation Connect API

Google also documents a variant that adds `dir_action=navigate&action_token=TRIP_TOKEN` (Google Maps) or
`navigate=yes&external_trip_token=TRIP_TOKEN` (Waze). That is the **Navigation Connect API**, which is a Google
Maps Platform product requiring an account and trip tokens. **We do not use it.**

We use the **plain** intent / plain deep link only — the destination and nothing else. That is the version that
costs nothing and needs no account.

### 2.6 Two variants — pick one

| | **D1 — fully free, zero account** | **D2 — free tier, card required** |
| --- | --- | --- |
| In-app map | **osmdroid** + OpenStreetMap tiles | **Google Maps SDK for Android** |
| In-app map cost | $0, no key, no account | $0 (unlimited map loads), but a **billing account with a payment method is mandatory** |
| Navigation | Google Maps app via Intent | Google Maps app via Intent |
| Navigation cost | **$0, no key, no quota** | **$0, no key, no quota** |
| Card needed anywhere | **No** | Yes, for the map key only |

Both variants deliver identical navigation, traffic, and road-rule quality — the only difference is **whose map
tiles you see inside your own app.**

### 2.7 What Option D gives up

1. **Navigation is no longer inside our app** (this contradicts a rule in the original specification — it needs
   your explicit approval).
2. Our app must run a **foreground location service** to keep watching for arrival while the user is in Google
   Maps. This requires `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, and `POST_NOTIFICATIONS`.
3. It requires Google Maps (or Waze) to be installed. In the Philippines this is effectively always true, but we
   must still guard with `resolveActivity()` and offer a fallback.
4. **Privacy note:** handing off means Google handles that trip. That is exactly the same as using Google Maps
   normally, and only the destination coordinate is passed — never your store database, tracker, or history.
   Our own data never leaves the device.

---

## 3. All options side by side

| Capability | **A** — Google Maps SDK + Routes API | **B** — osmdroid + openrouteservice, our own UI | **D1** — osmdroid + Google Maps hand-off | **D2** — Google Maps SDK + hand-off |
| --- | --- | --- | --- | --- |
| **Total cost** | $0 within free caps | **$0** | **$0** | $0 within caps |
| **Card / billing account** | **Required** | **None** | **None** | **Required (for the map key)** |
| Quota risk | Low (10,000/mo routes) | None | **None** | Low |
| Map inside your app | Google | OSM | OSM | Google |
| Turn-by-turn | our own UI | our own UI | **Google Maps' own** | **Google Maps' own** |
| **Live traffic** | ❌ | ❌ | ✅ | ✅ |
| Road rules (one-ways, turn restrictions) | ✅ | ✅ | ✅ | ✅ |
| Lane guidance / speed limits | ❌ | ❌ | ✅ | ✅ |
| Voice guidance | we build TTS | we build TTS | ✅ built-in | ✅ built-in |
| Automatic rerouting | we build it | we build it | ✅ built-in | ✅ built-in |
| Works offline | partial | partial | map yes, routing no | map cache only |
| Navigation stays inside your app | ✅ | ✅ | **❌** | **❌** |
| Build effort for turn-by-turn | **High** | **High** | **Very low** | **Very low** |

**Option D wins on every axis except "navigation stays inside your app."**

---

## 4. Offline fallback navigator (optional)

**Organic Maps** — free, open source, offline OSM maps, turn-by-turn navigation, no ads, no tracking, no account.
Verified: published on F-Droid and the Play Store; works with no internet at all.

Useful when there is no mobile signal: the map and navigation still work, but there is **no live traffic**.
It can be launched by Intent as a secondary "Navigate offline" button. Not required for Phase 1–7.

---

## 5. Recommendation — ✅ ADOPTED: Option D1

**Adopt Option D, variant D1** — osmdroid for the in-app map, Google Maps app via Intent for navigation.

Why: it is the only combination that is **fully free with no card at all**, and it is also the only free option
that delivers **live traffic, lane guidance, speed limits and road rules** — because Google's own navigation app
does that work. It is also by far the least code for us to write and maintain, and it removes every quota and
billing risk from the project.

If you would rather see **Google's map tiles inside your own app**, choose **D2** and accept the card — the
navigation experience is identical either way.

---

## 6. What this changes in the specification

| Original requirement | Status under Option D |
| --- | --- |
| "Google Maps SDK for Android" (map display) | **Kept in D2.** In D1 replaced by osmdroid + OpenStreetMap. |
| "Google Navigation SDK for Android" (in-app navigation) | **Replaced** by the Google Maps app launched via Intent. Reason: the Navigation SDK is a paid Enterprise SKU (1,000 free destinations/month, then $25 per 1,000) and requires a billing account. |
| "Navigation must remain inside the Alfamart Route Tracker application" | **Replaced.** Navigation happens in Google Maps. Requires your approval. |
| "The user must tap Start Navigation" | **Unchanged.** |
| "Always allow the user to cancel navigation or reject a suggested route" | **Unchanged.** |
| "Do not start navigation automatically" | **Unchanged.** |
| Arrival radius check, explicit YES, duplicate protection, daily sequence, tracker, history, Home last | **All unchanged.** |

---

## 7. Decision — ✅ DECIDED: Option D1

> **Decided 2026-09-25.** The user selected **Option D1**. Recorded as decisions **D-6, D-7 and D-8** in
> `docs/PHASE_0.1_REQUIREMENTS_REVIEW.md`.
>
> **Approved:** in-app map = **osmdroid + OpenStreetMap**; turn-by-turn = **Google Maps app via a free Intent**
> (with Waze as an alternative and Organic Maps as an optional offline fallback); route geometry =
> **openrouteservice free plan**; local data = **Room**.
>
> **Approved change:** "navigation must remain inside the app" is replaced by the Google Maps hand-off. Our app
> keeps a foreground location service running and detects arrival, then asks **NO / YES** as before.
>
> **Cost: $0. No API key, no billing account, no payment method, no quota.**
>
> D2 remains documented above as the only alternative if Google map tiles are ever wanted inside the app.

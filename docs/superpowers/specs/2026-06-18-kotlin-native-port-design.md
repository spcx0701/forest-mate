# Kotlin Native Android And Wear Port Design

## Status

Approved scope from the user on 2026-06-18:

> Port both the phone app and watch app to Kotlin native apps, considering the
> IzzyOnDroid feedback that a web app should be installable as a PWA instead of
> being distributed primarily as a wrapper APK.

## Goal

Replace the Android phone TWA wrapper with a Kotlin native Android app that
talks directly to the ForestMate FastAPI backend, and convert the existing Wear
OS companion from Java to Kotlin while preserving the existing server-based
pairing and sensor upload behavior.

The first native milestone must be useful enough to justify an APK independent
of the PWA: native screens, native permissions, native hiking state, native SOS,
and watch sensor integration. It does not need to reproduce every public
marketing or dashboard page from the web app.

## Current State

- `packaging/android/app` is a Bubblewrap-generated Trusted Web Activity. It
  depends on `com.google.androidbrowserhelper:androidbrowserhelper:2.7.1` and
  opens `https://forestmate.onrender.com/index.html`.
- `packaging/android/wear` is a native Wear OS module, but its application,
  API client, custom watch view, and sensor service are Java.
- The backend already exposes native-friendly endpoints for conditions,
  courses, recommendations, hiking, SOS, auth, chat, and watch pairing.
- The web app remains the PWA and public web experience. The native phone APK
  should no longer be described as a TWA wrapper after the port lands.

## Non-Goals For The First Milestone

- Do not embed a `WebView` or TWA fallback for the main phone experience.
- Do not port the B2G dashboard or marketing home page into native screens.
- Do not add Google Play Services, Firebase, or proprietary Wear Data Layer
  dependencies for the first milestone. Izzy/F-Droid compatibility matters more
  than automatic paired-phone handoff in this phase.
- Do not implement real 119 dispatch or real emergency-contact fan-out beyond
  the existing backend SOS endpoint and current documented simulation boundary.
- Do not expand LLM behavior. The native app should call the existing `/chat`
  endpoint and surface whether the backend returned `rules` or `claude`.

## Architecture

The Android project remains under `packaging/android` with two installable
modules:

- `:app` becomes a Kotlin native phone app using AndroidX Activity and Compose.
- `:wear` becomes a Kotlin Wear OS app using Android platform APIs and Kotlin.

A new `:core` Android library module should own shared Kotlin code that both
apps can consume:

- API endpoint constants and URL construction.
- DTOs for public conditions, courses, hikes, SOS, chat, and watch pairing.
- A small HTTP client abstraction backed by `HttpURLConnection` for the first
  milestone, avoiding larger dependencies while disk and F-Droid constraints are
  tight.
- JSON parsing helpers built on `org.json`, matching the current watch module
  style.
- Repository classes that expose suspend-friendly APIs through plain Kotlin
  interfaces.

The phone app owns user-facing hiking state: registered device token, selected
course, active hike id, current progress, latest watch status, and SOS state.
It stores this state in `SharedPreferences` for the first milestone. The watch
app stores only watch-scoped state: API base, watch token, paired hike/course
summary, streaming state, and the latest uploaded sensor snapshot.

## Phone App Screens

The phone app uses a bottom navigation surface with five native destinations:

1. Home
   - Shows hiking index from `/api/v1/index`.
   - Shows recommended courses from `/api/v1/recommend`.
   - Allows course selection and opens course details from `/api/v1/courses`.

2. Hike
   - Shows selected course, progress, distance, estimated time, and hazards.
   - Registers a device through `/api/v1/devices` if no token exists.
   - Starts and ends hikes through `/api/v1/hikes` and `/api/v1/hikes/{id}/end`.
   - Sends progress updates to `/api/v1/hikes/{id}/track`.
   - Starts watch pairing through `/api/v1/watch/pair/start` and polls
     `/api/v1/watch/latest`.

3. SOS
   - Uses a native hold-to-confirm interaction.
   - Sends `/api/v1/sos` with the active hike id when available.
   - Displays returned grid number, GPS text, station, status, and ETA.

4. AI
   - Provides a simple chat transcript.
   - Calls `/api/v1/chat`.
   - Displays backend sources and engine type from the response.
   - Uses native text input only, not an embedded web chat.

5. My
   - Displays `/api/v1/hikes/summary` and `/api/v1/hikes`.
   - Shows account/session state only if existing auth endpoints are easy to
     consume. Otherwise it stays device-token based for the first milestone.

## Wear App Behavior

The Wear OS app keeps the current product role:

- Pair to a phone-started hike using the existing six-digit backup code through
  `/api/v1/watch/pair/claim`.
- Show a compact hiking face with route name, progress, heart rate, GPS state,
  battery, and distress level.
- Run a foreground sensor service for heart rate, GPS, accelerometer,
  orientation-derived heading, and battery.
- Upload samples to `/api/v1/watch/track`.
- Persist last known values locally so the face remains useful between uploads.

The conversion should preserve behavior while improving boundaries:

- `WatchApi.kt` owns request/response parsing.
- `WatchStateStore.kt` owns `SharedPreferences`.
- `WatchSensorService.kt` owns sensors, foreground notification, and upload
  scheduling.
- `MainActivity.kt` owns UI and permission prompts.

## Data Flow

Phone startup:

1. Load `apiBase` and saved device token.
2. Fetch `/healthz` opportunistically for API availability.
3. Fetch `/index` and `/recommend`.
4. Render cached fallback state if the network is unavailable.

Hike flow:

1. User selects course.
2. Phone registers a device if needed.
3. Phone starts hike and saves `hikeId`.
4. Phone tracks progress locally and posts updates.
5. Phone can start watch pairing and show the code.
6. Watch claims code, uploads samples, and server merges watch data into the
   active hike.

SOS flow:

1. User holds the native SOS control until confirmation completes.
2. Phone posts `/sos`.
3. UI shows server response and logs the event locally.

Chat flow:

1. User sends a text prompt.
2. Phone posts `/chat` with current language, selected course id, and progress.
3. UI shows the reply, source labels, and backend engine.

## Error Handling

- Network calls return typed success or typed failure results. UI code must not
  parse exception strings directly.
- API base defaults to `https://forestmate.onrender.com/api/v1`, with a debug
  override stored locally.
- Missing device token should trigger automatic device registration for hike,
  SOS, watch pairing, and My summary flows.
- If a hike endpoint returns `401`, the phone clears only the local device token
  and asks the user to retry. It must not clear watch tokens.
- If watch upload fails, the foreground notification changes to a waiting state
  and the service retries on the next interval.
- If Java/Python local tooling is missing, that is a setup failure, not an app
  failure. The plan must keep setup and app behavior verification separate.

## Testing Strategy

The implementation must use test-first changes for new Kotlin behavior:

- Unit tests in `:core` for URL normalization, JSON DTO parsing, failure
  mapping, and repository behavior using fake HTTP transports.
- Unit tests in `:app` for state reducers and hold-to-confirm SOS logic.
- Unit tests in `:wear` for watch JSON parsing, state persistence, and sensor
  upload request construction.
- Existing Python server tests remain the backend contract guard.
- Gradle build commands verify Android compilation once a JDK is available.

Baseline blockers observed before writing this spec:

- `python3 -m pytest server/tests/test_services.py -q` fails in a fresh
  worktree because local Python dependencies are not installed
  (`pydantic_settings` missing).
- `./gradlew --no-daemon :wear:assembleDebug` fails before Gradle starts because
  the machine has no Java Runtime installed.
- Disk pressure was reduced from 142 MB free to 1.7 GB free by deleting
  regenerable Gradle and pip caches, but full Android dependency downloads may
  still require more room.

## Release And Documentation Impact

Update these surfaces when the native port is implemented:

- `docs/ARCHITECTURE.md`: phone app is Kotlin native, web app remains PWA.
- `packaging/android/README.md`: remove Bubblewrap/TWA build language and add
  native Gradle build commands.
- `README.md` and `README.en.md`: describe PWA as web install path and Android
  APK as native app path.
- `.github/workflows/android-release.yml`: remove Node/Bubblewrap setup if the
  phone build no longer needs it.
- `fastlane/metadata/android/*`: replace TWA language with native app language.

## Completion Criteria

- `:app` no longer depends on `androidbrowserhelper`.
- `:app` contains Kotlin native screens for Home, Hike, SOS, AI, and My.
- `:wear` Java sources are replaced by Kotlin sources with equivalent behavior.
- `:core` exists and is consumed by both `:app` and `:wear`.
- No `WebView` or TWA activity is used for the primary phone experience.
- Native phone and watch debug APKs build locally or, if local JDK remains
  unavailable, build in CI with the same Gradle tasks.
- Documentation no longer pitches the phone APK as a web wrapper.

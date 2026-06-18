# Architecture

ForestMate serves a static PWA from a FastAPI backend. The backend exposes
public-data adapters, authentication, hike recording, watch pairing, push
notifications, and dashboard endpoints. The PWA remains the web install path.
The Android APK is a native Kotlin client that talks to the same FastAPI backend
directly, and the Wear OS companion is also native Kotlin. iOS packaging uses
Capacitor.

## Components

- `app/`: static PWA, offline fallback, dashboard, service worker, and public
  web assets.
- `server/`: FastAPI application, routers, SQLAlchemy models, public-data
  adapters, safety services, and ETL helpers.
- `server/data/`: baked catalog and snapshot data used when live public APIs are
  unavailable.
- `packaging/android/`: Kotlin native Android phone app, Kotlin native Wear OS
  companion app, signing scripts, and packaging notes.
- `packaging/ios/`: Capacitor wrapper metadata and lockfile for iOS builds.
- `.github/workflows/`: CI, OpenSSF Scorecard, and CodeQL analysis.

## Runtime Flow

The PWA loads from the same origin as the API. Anonymous device tokens allow
local hike records before account registration. Account sessions can link an
existing device token to a user. Watch sessions are owned by the backend. The
native phone app owns hike start and route selection, while the Wear OS app is a
sensor and glanceable navigation client. Pairing returns a watch-scoped token
plus course summary data; the watch uses that token to upload heart-rate, GPS,
altitude, compass, accelerometer, and battery samples. The repository keeps the
six-digit code as a backup-facing connection path; automatic paired-phone
handoff should be owned by a future Wear OS Data Layer bridge.

Dashboard endpoints expose aggregate operational views. They must not reveal
individual precise location or health data. Safety logic favors explicit
fallbacks over silently presenting simulated or stale data as live data.

## Data Stores

The default local development path uses SQLite. Deployment can use PostgreSQL or
PostGIS. Public-data snapshots keep the app usable without API keys, while live
adapters are enabled only when deployment credentials are configured.

## Deployment

The Docker image runs `uvicorn server.main:app`. `docker-compose.yml` requires a
caller-provided database password. Static assets are served by FastAPI after API
routes, so one deployment can provide the PWA, dashboard, and API.

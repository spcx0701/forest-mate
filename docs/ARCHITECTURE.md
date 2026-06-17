# Architecture

ForestMate serves a static PWA from a FastAPI backend. The backend exposes
public-data adapters, authentication, hike recording, watch pairing, push
notifications, and dashboard endpoints. Android packaging wraps the deployed PWA
as a Trusted Web Activity; iOS packaging uses Capacitor.

## Components

- `app/`: static PWA, offline fallback, dashboard, service worker, and public
  web assets.
- `server/`: FastAPI application, routers, SQLAlchemy models, public-data
  adapters, safety services, and ETL helpers.
- `server/data/`: baked catalog and snapshot data used when live public APIs are
  unavailable.
- `packaging/android/`: Bubblewrap/TWA Android project, Wear OS companion
  module, signing scripts, and packaging notes.
- `packaging/ios/`: Capacitor wrapper metadata and lockfile for iOS builds.
- `.github/workflows/`: CI, OpenSSF Scorecard, and CodeQL analysis.

## Runtime Flow

The PWA loads from the same origin as the API. Anonymous device tokens allow
local hike records before account registration. Account sessions can link an
existing device token to a user. Watch pairing issues a short-lived code that
connects a watch session to a hike or later attaches when a hike starts.

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

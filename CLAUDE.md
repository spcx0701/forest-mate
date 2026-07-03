# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

ForestMate (숲길동무) is a hiking-safety service: a FastAPI backend serving public
hiking/weather/wildfire data plus a static PWA frontend, a Kotlin native Android
app, and a Kotlin native Wear OS companion app. It computes a pre-hike risk
index, tracks GPS during hikes, auto-detects distress, and exposes a B2G
dashboard for municipalities/fire departments. Everything degrades gracefully:
without API keys the backend falls back to baked snapshot data and a rule-based
chat engine instead of failing.

## Commands

### Backend (Python)
```bash
python3 -m venv .venv && .venv/bin/pip install --require-hashes -r requirements-dev.lock
.venv/bin/uvicorn server.main:app --port 5181      # run the app (serves API + app/ at same origin)
.venv/bin/python -m pytest server/tests -q          # run all backend tests
.venv/bin/python -m pytest server/tests/test_scoring.py -q   # run a single test file
.venv/bin/python -m pytest server/tests -q --cov=server --cov-report=xml   # with coverage
```
Local URLs once running: `/home.html` (landing), `/index.html` (PWA), `/dashboard.html` (B2G), `/docs` (OpenAPI).

### Frontend property tests (Node)
```bash
corepack enable && corepack prepare pnpm@11.9.0 --activate
pnpm install --frozen-lockfile
pnpm test:app          # runs `node --test app/*.test.js`
```
`app/*.test.js` files load functions directly out of `app/app.js` by slicing source lines between marker
strings (e.g. `const HTML_ENTITIES` to `const cssToken`) and running them in a `vm` context — there is no
build step or module system for the frontend. When editing `app.js`, keep those marker identifiers stable
or update the corresponding `.test.js` line-range lookups.

### Docker / deploy
```bash
cp .env.example .env
docker compose up            # API + PostgreSQL
docker build -t forestmate:ci .
```
Single uvicorn worker is required — the in-memory `EventBus` (dashboard WebSocket pub/sub) assumes one
process; scaling to multiple workers needs a Redis-backed bus first.

### Android / Wear OS (Kotlin, Gradle)
```bash
cd packaging/android
./gradlew --no-daemon :app:assembleRelease :wear:assembleRelease   # signed release APKs (needs env vars, see packaging/android/README.md)
./gradlew --no-daemon :wear:assembleDebug                          # debug watch APK for local testing
```
Modules: `:core` (shared API client/models/repository), `:app` (phone), `:wear` (Wear OS companion). Watch
default API base is `wear/src/main/res/values/strings.xml` (`default_api_base`).

### Docs/README consistency
```bash
.venv/bin/python -m pytest server/tests/test_readme_parity.py -q
git diff --check
```
Run this when changing README badges/architecture sections — a test asserts README content stays in sync.

## Architecture

```
server/            FastAPI backend (single source of truth for domain logic)
  main.py            App assembly; serves app/ statically same-origin; CSP nonce/hash generation from HTML
  config.py          Settings from .env — presence/absence of keys silently switches modes (see below)
  db.py, models.py   SQLAlchemy; SQLite (dev) or PostgreSQL/PostGIS (prod)
  adapters/          Public-data adapters (KMA weather, wildfire risk, mountain weather) w/ TTL cache + fallback
  geo.py             KMA grid conversion + province coordinates
  data/              Baked catalog.json + per-mountain trail line JSON (server/data/trails/{code}.json) — loaded at
                     startup so redeploys/cold-starts don't lose the catalog even with an ephemeral DB
  services/
    scoring.py         Hike risk index (wildfire 0.3 + landslide 0.25 + weather 0.25 + sunset 0.2) + course recommendation
    safety.py          Distress detection: stalled movement 30+ min -> level1, + abnormal heart rate -> level2 (broadcasts immediately).
                       Runs server-side so it still evaluates using last-known data if the app is killed or network drops.
    chat.py            Rule-based intent engine (used when no ANTHROPIC_API_KEY)
    llm.py             Claude RAG mode (Messages API, cached system prompt + per-request weather/course/risk context)
    bus.py             In-memory EventBus for dashboard WebSocket pub/sub (see single-worker note above)
  routers/           public (no auth) / hikes (device-token auth) / auth (sessions + OAuth) / watch (watch-scoped tokens) / dashboard (WS) / push
  tests/             pytest suite — scoring/safety/k-anonymity unit tests + registration->hike->risk->SOS->dashboard E2E + WebSocket

app/                Static PWA (works standalone with no backend — local fallback engine)
  app.js             API client (health-check detects backend; falls back to local data.js on failure) + all app logic
  data.js, sw.js     Local fallback data / offline service worker
  dashboard.html     B2G control dashboard (live KPIs + WebSocket feed)

packaging/android/   Kotlin native phone app + Kotlin native Wear OS companion (see Commands above)
packaging/ios/       Capacitor wrapper metadata only

deliverables/, legal/, store/, docs/   Competition submission docs, privacy policy/ToS, store metadata, architecture/testing/security notes
```

### Mode switching (no code changes needed)

Backend behavior switches automatically based on which `.env` keys are set (`server/config.py`):

| Feature | No key (default) | Key set |
|---|---|---|
| Risk index / weather / wildfire | Baked public-data **snapshot** (same schema as real API) | `DATA_GO_KR_KEY` -> real public-data API |
| AI chat ("숲이") | Rule-based intent engine (`services/chat.py`) | `ANTHROPIC_API_KEY` -> Claude RAG (`services/llm.py`, model `claude-opus-4-8`) |
| Frontend | Local fallback engine (`app/data.js`) | Detects backend -> cloud mode (server-backed, LIVE badge) |

The same pattern applies client-side: `app/app.js` always tries the backend first and falls back to local
data/rules if the health check fails, so the PWA works fully offline/static-hosted.

### Watch pairing model

Native phone app owns hike start/course selection; Wear OS app is a sensor + glanceable nav client. Pairing
returns a watch-scoped token plus course summary; the watch posts heart-rate/GPS/altitude/compass/accelerometer/
battery samples to `/api/v1/watch/track` and gets back progress + risk level. The 6-digit pairing code is a
backup path, not the primary UX — full automatic paired-phone handoff is intentionally left to a future Wear OS
Data Layer bridge (not yet implemented).

### Privacy/safety constraints that affect design decisions

- Dashboard endpoints must expose only aggregate/k-anonymized views (`K_ANONYMITY`, min cluster 50 in
  production, 1 in dev) — never per-user precise location or health traces.
- Prefer explicit fallback states over silently presenting simulated/stale data as live.
- Test fixtures must use synthetic data; snapshot/mocked adapters are preferred over live third-party calls so
  CI stays deterministic (see `docs/TESTING.md`).

# Contributing to ForestMate

Thanks for helping improve ForestMate. This project combines a FastAPI backend,
a static/PWA frontend, Kotlin native Android and Wear OS packaging, F-Droid
metadata, and forest public data workflows. Contributions are welcome when they
improve safety, reliability, privacy, accessibility, documentation, or
maintainability.

Please also read the [Code of Conduct](CODE_OF_CONDUCT.md).

Project governance, support, roadmap, architecture, security-design, and
testing details are tracked in:

- [GOVERNANCE.md](GOVERNANCE.md)
- [SUPPORT.md](SUPPORT.md)
- [docs/ROADMAP.md](docs/ROADMAP.md)
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
- [docs/SECURITY_DESIGN.md](docs/SECURITY_DESIGN.md)
- [docs/TESTING.md](docs/TESTING.md)

## Good First Contributions

- Fix documentation that is stale, unclear, or hard to follow.
- Improve tests for scoring, safety detection, public-data adapters, or API
  behavior.
- Improve accessibility, mobile layout, offline fallback, or error states.
- Tighten privacy wording, location-data handling, and safety disclaimers.
- Report reproducible bugs with clear steps and screenshots when useful.

## Before You Start

For non-trivial changes, open or comment on an issue first so the scope can be
agreed before implementation. This is especially important for:

- Safety logic, SOS flows, or emergency-contact behavior.
- Location tracking, k-anonymization, or dashboard data.
- Public-data API adapters and fallback datasets.
- Android packaging, F-Droid metadata, signing, or release automation.
- LLM/RAG behavior and prompts.

Do not include secrets, real API keys, private access tokens, personal location
tracks, emergency contacts, or production credentials in issues, pull requests,
tests, screenshots, fixtures, or logs.

## Development Setup

Use Python 3.12 or newer.

```bash
python3 -m venv .venv
.venv/bin/pip install --require-hashes -r requirements-dev.lock
.venv/bin/uvicorn server.main:app --port 5181
```

Useful local URLs:

- App: `http://localhost:5181/index.html`
- Service intro: `http://localhost:5181/home.html`
- Dashboard: `http://localhost:5181/dashboard.html`
- OpenAPI docs: `http://localhost:5181/docs`

The app works without API keys by using snapshot data and rule-based fallbacks.
If you use live public-data APIs or LLM features, keep credentials in `.env` and
out of git.

When dependency pins change, regenerate the hashed lock files:

```bash
uv pip compile --python-version 3.12 --python-platform x86_64-manylinux_2_28 --generate-hashes requirements.txt -o requirements.lock
uv pip compile --python-version 3.12 --python-platform x86_64-manylinux_2_28 --generate-hashes requirements-dev.txt -o requirements-dev.lock
```

## Testing

Run the server test suite before opening a pull request:

```bash
.venv/bin/python -m pytest server/tests -q
```

The GitHub Actions CI also runs:

```bash
python -m pytest server/tests -q --cov=server --cov-report=xml
docker build -t forestmate:ci .
```

For documentation-only changes, run at least:

```bash
git diff --check
.venv/bin/python -m pytest server/tests/test_readme_parity.py -q
```

## Documentation Parity

The repository has Korean and English README entrypoints: `README.md` and
`README.en.md`. Treat them as sibling public surfaces, not independent files.

- When changing the top README banner, status badges, store badges, or primary
  navigation links in one README, update the other README in the same change.
- Keep language-specific prose translated, but keep shared assets and badge
  targets synchronized.
- Before pushing README changes, run `python -m pytest
  server/tests/test_readme_parity.py -q` to catch drift between the two files.

## Android and F-Droid Changes

Android is packaged as Kotlin native phone and Wear OS modules under
`packaging/android/`.

- GitHub direct APK releases use a signed APK built by the project maintainer.
- F-Droid inclusion uses source builds and unsigned release APK output; F-Droid
  signs the final package itself.
- Do not add Firebase, Crashlytics, AdMob, Google Play Services, tracking SDKs,
  or proprietary dependencies without an explicit maintainer decision.
- Keep version updates consistent across phone/wear Gradle metadata,
  `packaging/android/package.json`, release notes, and F-Droid metadata.

When touching Android or F-Droid files, document which build or scanner command
you ran in the pull request.

## Public Data, Safety, and Privacy

ForestMate is safety-adjacent software. Be conservative with claims and clear
about fallback behavior.

- Do not present snapshot or simulated data as live data.
- Do not claim emergency dispatch or official rescue integration unless it is
  implemented and verified.
- Avoid storing precise personal location data unless the change explicitly
  needs it and the privacy impact is reviewed.
- Prefer aggregate or k-anonymized dashboard views.
- Keep public-data sources, adapter behavior, and failure modes documented.

## Pull Request Checklist

Before opening a pull request:

- Keep the change focused and reviewable.
- Update README, store metadata, F-Droid notes, or legal text when behavior
  changes.
- Add or update tests for behavior changes.
- Include screenshots for visible UI changes.
- State the validation commands you ran.
- Explain privacy, safety, public-data, or release impact when relevant.
- Confirm that no secrets, private user data, or generated build artifacts are
  included.

## Coding Style

- Follow the existing structure and naming in the file you edit.
- Prefer small, explicit changes over broad rewrites.
- Keep frontend changes usable on mobile first.
- Keep API and service logic testable without live third-party services.
- Use fallback data intentionally and label it clearly.

## License

By contributing to this repository, you agree that your contribution is licensed
under the repository's [Apache License 2.0](LICENSE), unless a different license
is explicitly stated for a specific file.

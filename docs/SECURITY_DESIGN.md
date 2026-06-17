# Security Design

ForestMate handles location and safety-adjacent data, so the design favors
minimal storage, private vulnerability reporting, explicit fallback states, and
least-privilege automation.

## Authentication

Anonymous device tokens support pre-account usage. Account sessions are bearer
tokens and should be stored only as long as needed by the client. OAuth callback
tokens are validated before use. Logout clears account state, and invalid tokens
trigger one re-registration attempt for guest device flows.

## Sensitive Data

Do not commit API keys, access tokens, signing credentials, personal location
tracks, heart-rate data, emergency contacts, or production logs. Test fixtures
must use synthetic data. Dashboard views should use aggregate or k-anonymized
data instead of per-user traces.

## Client-Side Safety

State loaded from browser storage is normalized before use. Server-provided API
base URLs are restricted to same-origin, HTTPS, or local development HTTP. Chat
HTML is rendered through an allow-list sanitizer before being appended to the
DOM.

## Supply Chain

GitHub Actions are pinned to immutable commit SHAs. Docker base images are pinned
by digest. Dependabot tracks GitHub Actions, Python, Docker, Android, and Node
dependencies. CodeQL and OpenSSF Scorecard run on a schedule and on relevant
repository events.

## Vulnerability Handling

Vulnerabilities should be reported privately through GitHub Private Vulnerability
Reporting or the contact in [SECURITY.md](../SECURITY.md). Critical issues that
can expose location, health, authentication, or SOS data should be triaged before
normal feature work.

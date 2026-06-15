# Security Policy

ForestMate (숲길동무) is a public-safety application. It processes real-time
location and heart-rate signals, raises SOS alerts that can be forwarded to
guardians and emergency services, and exposes a B2G (government) monitoring
dashboard. We take security and privacy seriously and appreciate responsible
disclosure.

## Supported Versions

This project is under active development. Security fixes are applied to the
latest `main` branch and the most recent tagged release. Older releases are not
maintained.

| Version            | Supported          |
| ------------------ | ------------------ |
| `main` (latest)    | :white_check_mark: |
| Latest release tag | :white_check_mark: |
| Older tags         | :x:                |

## Reporting a Vulnerability

**Please do not open a public issue for security vulnerabilities.**

Report privately through one of:

1. **GitHub Private Vulnerability Reporting** (preferred) — go to the
   [Security tab](https://github.com/spcx0701/forest-mate/security/advisories/new)
   and choose **Report a vulnerability**.
2. Email the maintainer at **spcx0701@gmail.com**.

Please include:

- A description of the issue and its impact.
- Steps to reproduce or a proof of concept.
- Affected component, version, or commit.

We aim to acknowledge a report within **5 business days** and to provide a
remediation timeline after triage. Please give us a reasonable window to fix the
issue before any public disclosure. We are happy to credit reporters who wish to
be named.

## Scope

Security reports are especially valuable for areas where a flaw could endanger a
user or expose sensitive data:

- **Authentication & authorization** — device/hike token issuance and the
  `/api/v1/hikes` and dashboard (`/api/v1/dashboard`) endpoints.
- **Distress & SOS pipeline** — server-side distress detection and any
  emergency-contact / 119 fan-out behavior.
- **Privacy & k-anonymization** — the B2G dashboard must never expose individual
  location or health data (cluster statistics only, per Korea's Location
  Information Act).
- **Data handling** — leakage of GPS tracks, heart-rate data, contacts, or API
  keys via responses, logs, caches, or fixtures.
- **Dependency & supply chain** — known CVEs in pinned dependencies, the Docker
  image, or the Android/F-Droid build.
- **LLM/RAG** — prompt injection or data exfiltration through the AI assistant.

### Out of Scope

- Issues that require a rooted/jailbroken device or physical access.
- Findings on third-party public-data APIs we consume (report to the provider).
- Best-practice suggestions without a demonstrable security impact.
- Volumetric denial-of-service (please report logic-level DoS only).

## Handling Sensitive Data

Never include real secrets, API keys, access tokens, personal location tracks,
heart-rate data, or emergency contacts in reports, reproductions, fixtures, logs,
or screenshots. Use synthetic data. See also
[CONTRIBUTING.md](CONTRIBUTING.md).

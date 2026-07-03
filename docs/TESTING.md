# Testing

ForestMate tests should prove behavior without relying on live third-party
services. Snapshot data and mocked adapters are preferred for repeatable CI.

## Local Commands

```bash
python3 -m venv .venv
.venv/bin/pip install --require-hashes -r requirements-dev.lock
.venv/bin/python -m pytest server/tests -q
```

For coverage:

```bash
.venv/bin/python -m pytest server/tests -q --cov=server --cov-report=xml
```

For documentation and badge-surface changes:

```bash
.venv/bin/python -m pytest server/tests/test_readme_parity.py -q
git diff --check
```

## Pull Request Expectations

- Add or update tests for behavior changes.
- Prefer deterministic snapshot or mocked public-data responses.
- Do not use live credentials in CI or local fixtures.
- Include the validation commands in the pull request description.
- For safety, privacy, authentication, or release changes, document the impact
  and any manual verification that remains.

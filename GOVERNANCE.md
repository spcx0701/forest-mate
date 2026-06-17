# Governance

ForestMate is currently maintained by the repository owner. Governance is kept
small and explicit until the contributor base grows.

## Roles

- Maintainer: reviews pull requests, manages releases, triages security reports,
  controls repository settings, and protects user safety and privacy decisions.
- Contributor: proposes issues or pull requests, follows the code of conduct,
  keeps changes focused, and documents validation.
- Security reporter: uses the private vulnerability process in
  [SECURITY.md](SECURITY.md).

## Decision Making

Routine fixes may be merged after review and passing CI. Changes that affect SOS
behavior, personal location data, authentication, release signing, privacy
policy, or public-sector dashboard behavior require explicit maintainer review
and a written impact note in the pull request.

## Access Continuity

Repository and release credentials are held by the maintainer. If additional
maintainers are added, at least two trusted maintainers should have recovery
access for GitHub administration, release signing, package publication, and
private vulnerability reporting. Access should be reviewed after role changes.

## Review Standards

Pull requests should be small enough to review, include tests for behavior
changes, avoid secrets and personal data, and state the validation commands that
were run. Security-sensitive changes should receive a second human review before
release whenever possible.

## Community Standards

All project spaces follow [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md). Reports that
include credentials, personal location data, or potential user harm should be
handled privately.

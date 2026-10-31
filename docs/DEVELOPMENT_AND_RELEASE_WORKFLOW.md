# Development and Release Workflow

This repository uses a review-first workflow. A branch is not a release, and a successful source edit is not a verified build.

## Branch model

| Branch | Purpose | Rule |
|---|---|---|
| `main` | Stable, releasable source | Protected by review; no direct feature work |
| `develop` | Integration of reviewed work | PRs from feature/fix branches |
| `feature/<topic>` | New capability | One independently reviewable capability per branch |
| `fix/<topic>` | Defect correction | One defect and its regression coverage per branch |
| `refactor/<topic>` | Structural migration | No unrelated feature expansion |

If `develop` does not yet exist, create it from the current stable `main` after the repository owner confirms the intended integration baseline. Do not silently repoint or rewrite existing branches.

## Pull request requirements

Every PR must state:

1. Problem and scope.
2. Exact implementation approach and affected components.
3. Compatibility and security considerations.
4. Tests added and commands actually executed.
5. CI status, with links or run IDs where available.
6. Build/signing status and artifact location, if applicable.
7. Device verification status, including Android version, host-app version and LSPosed/framework version when tested.
8. Known limitations and rollback considerations.

Use draft PRs while implementation or validation is incomplete. Mark ready only after the description is complete and the author has run the available local checks. Merge only after required checks pass and review is complete.

## Versioning

Use Semantic Versioning: `MAJOR.MINOR.PATCH`.

- **PATCH**: backward-compatible bug fixes.
- **MINOR**: backward-compatible features.
- **MAJOR**: incompatible behavior, package/application identity changes, or migration requiring user action.

The repository's current displayed application version remains authoritative until a release candidate is built and verified. Do not increment the application version merely because a branch or PR exists. For each release, update the app version metadata, `CHANGELOG.md`, and release notes together.

## Release gates

A release may be tagged only after all applicable gates pass:

- [ ] PR merged into the release branch.
- [ ] Regression tests pass.
- [ ] Android release build completes.
- [ ] APK structure and package/application ID verified.
- [ ] APK alignment and cryptographic signature verified.
- [ ] Signing certificate fingerprint matches the intended release key.
- [ ] SHA-256 checksum generated and independently checked.
- [ ] Install/upgrade and startup smoke tests pass.
- [ ] LSPosed activation and target-process injection verified on a real device.
- [ ] Release notes list changes, compatibility, known issues and artifact checksums.

Tags use `vMAJOR.MINOR.PATCH` and are immutable. GitHub Releases are created from those tags and include the signed APK, checksum file and changelog. Never overwrite an existing published tag or silently replace a release artifact.

## Status vocabulary

Use these terms precisely:

- **Implemented**: source change committed.
- **Tested**: named test command executed with a recorded result.
- **CI passed**: GitHub Actions reports success for the relevant commit/PR.
- **Built**: expected APK exists and has been structurally checked.
- **Signed**: APK signature verification passes and certificate fingerprint is recorded.
- **Device verified**: installed and exercised on a real device with environment details recorded.
- **Released**: immutable tag and GitHub Release published with verified artifacts.

Do not use a stronger status than the evidence supports.

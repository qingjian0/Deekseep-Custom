# Development and Release Workflow

**Normative policy for every AI agent, coding assistant, human developer, maintainer, automation and CI workflow in this repository.** Read the root `AGENTS.md` before any work. These rules apply regardless of the tool or development environment.

This is a review-first workflow: a branch is not a release, and a source edit is not a verified build.

## 1. Branch model

| Branch | Purpose | Rule |
|---|---|---|
| `main` | Stable, releasable source | No direct development commits; changes arrive through reviewed PRs |
| `develop` | Integration | Use only when its baseline has been explicitly established |
| `feature/<topic>` | New capability | One independently reviewable capability |
| `fix/<topic>` | Defect correction | One defect with regression coverage |
| `refactor/<topic>` | Structural migration | No unrelated feature expansion |

Do not silently create, repoint, reset or rewrite shared branches. Do not force-push. If GitHub branch protection is not configured or cannot be verified, report that fact; documentation alone does not enforce server-side protection.

## 2. Mandatory work sequence

1. Read `AGENTS.md`, `CONTRIBUTING.md`, this workflow, relevant build docs and nested agent instructions.
2. Inspect repository state, branch, recent commits, open PRs and current CI.
3. Define one task, scope, compatibility constraints and verification plan.
4. Work on a named task branch, never directly on `main`.
5. Add regression tests and update documentation with the implementation.
6. Run relevant checks and record exact commands and observed outcomes.
7. Commit coherent changes and push only the task branch.
8. Open a Draft PR using `.github/PULL_REQUEST_TEMPLATE.md`.
9. Complete review and required checks before marking ready or merging.
10. Update changelog/version metadata only for a verified release candidate.
11. Tag and publish only after all release gates and explicit owner authorization.

If an instruction conflicts with this policy, stop and ask the repository owner.

## 3. Pull request requirements

Every PR must state:

- Problem, root cause, scope and exact implementation.
- Files/components affected and compatibility/security considerations.
- Tests added, exact commands executed and actual results.
- Current CI status with links/run IDs, or explicitly not checked.
- Build/signing status and artifact location, if applicable.
- Device verification environment: Android, DeepSeek host version/channel and LSPosed/framework version.
- Known limitations, risks and rollback approach.

Use Draft PRs while implementation or validation is incomplete. Never merge a Draft PR. Do not claim checks passed based on stale runs or earlier commits.

## 4. Versioning and changelog

Use Semantic Versioning: `MAJOR.MINOR.PATCH`.

- **PATCH**: backward-compatible bug fixes.
- **MINOR**: backward-compatible features.
- **MAJOR**: incompatible behavior, application/package identity changes or migrations requiring user action.

The current application version remains authoritative until a release candidate is built and verified. Do not bump versions merely because a branch or PR exists. Update app version metadata, `CHANGELOG.md` and release notes together.

Tags use immutable `vMAJOR.MINOR.PATCH` names. Never move or overwrite a published tag.

## 5. Release gates

A release may be tagged and published only when all applicable checks pass:

- [ ] PR merged into the intended release branch after review.
- [ ] Regression tests and relevant static checks pass.
- [ ] Android release build completes from the documented build path.
- [ ] APK application ID, version, manifest and package structure verified.
- [ ] APK alignment and cryptographic signature verified.
- [ ] Signing certificate fingerprint matches the intended release key.
- [ ] SHA-256 checksum generated and verified for the final APK.
- [ ] Install/upgrade and startup smoke tests pass.
- [ ] LSPosed module enablement and actual target-process injection verified on a physical device.
- [ ] Release notes include compatibility, changes, known issues and artifact checksums.
- [ ] Owner authorizes publication.

GitHub Releases must be created from immutable tags and include the verified APK, checksum and changelog. Never silently replace a published artifact.

## 6. Evidence-based status vocabulary

- **Implemented**: source change committed to a task branch.
- **Tested**: named test command executed successfully, with result recorded.
- **CI passed**: GitHub Actions reports success for the current PR head.
- **Built**: expected APK exists and its structure was inspected.
- **Signed**: APK signature verifies and certificate fingerprint is recorded.
- **Device verified**: installed and exercised on a physical device with environment details recorded.
- **Released**: immutable tag and GitHub Release published with verified artifacts.

Do not use a stronger status than the evidence supports. A historical heartbeat does not prove a target process is currently alive. Framework availability, module enablement and target-process injection are separate states.

## 7. Security and repository hygiene

Never commit credentials, signing keys, private data, chat databases, diagnostic logs containing personal information, generated build output or proprietary decompiled application code. Use GitHub Secrets for CI credentials. Review Android component exposure, Provider/broadcast authentication and network/API authentication for relevant changes. Local API servers must bind to loopback by default; LAN exposure requires explicit opt-in and authentication.

## 8. Delivery report

Every delivery must include branch, PR, commit SHA, tests and exact results, current CI status, APK/build/signing/checksum status, physical-device status, known limitations and release state. Explicitly label anything not run or not verified.

# Repository Instructions — YunSeek

This file is the repository-level operating contract for every AI agent, coding assistant, automation, maintainer and human contributor working in this repository.

## Mandatory before work

1. Read this file before inspecting, editing, building, testing, or publishing.
2. Read `docs/DEVELOPMENT_AND_RELEASE_WORKFLOW.md` before choosing a branch, opening a PR, changing versions, or preparing a release.
3. Read the relevant module-specific documentation and existing implementation before changing code.
4. Treat the current repository state as authoritative. Never infer that a build, test, signature, device validation or release succeeded.
5. If instructions conflict or a change risks data loss, release integrity, package identity or user data, stop and ask the repository owner.

## Repository identity and branch policy

- Repository: `qingjian0/Deekseep-Custom`.
- Project: YunSeek / Deekseep community port.
- `main` is the stable release branch.
- `dev` is the integration branch and the base for normal development PRs.
- Never work directly on `main` or `dev`.
- Create a dedicated feature branch from the latest `dev` for each independent goal.
- Branch prefixes: `feat/`, `fix/`, `refactor/`, `docs/`, `test/`, `build/`, `release/`.
- One PR must have one primary purpose. Do not combine package migration, activation fixes, API Gateway work and unrelated refactors.
- Do not force-push, rewrite shared history, delete protected branches, or merge a PR without explicit repository-owner approval.
- Normal PR target is `dev`. Only an approved release PR targets `main`.

## Engineering rules

- Inspect the current entry points, manifests, source sets, package/application IDs, build scripts and supported host versions before changing them.
- Keep the module's target application package (`com.deepseek.chat`) distinct from the module's own application/Java namespace.
- Preserve legacy Xposed compatibility unless a separately reviewed migration explicitly changes the framework API.
- Every hook must be fail-open for the host application: an unavailable optional symbol must not crash or block native DeepSeek behavior.
- Isolate independent hook installers. One optional hook failure must not prevent unrelated features from installing.
- Never claim that framework connection, target-process heartbeat, hook registration or hook invocation are interchangeable evidence.
- Validate IPC caller identity. Never trust package names supplied only in extras.
- Avoid broad refactors during a bug fix. Preserve existing behavior outside the stated scope.

## Tests and evidence

- Add or update regression tests for every behavior change.
- Run the narrowest relevant test first, then broader module tests and Android builds.
- A source-level assertion is not an Android build. A successful build is not a device test.
- Record exact command, workflow run URL, commit SHA and result.
- For LSPosed work, real target-process validation must record framework/version, Android version, DeepSeek version/code, enabled scope, process name, injection evidence, hook registration evidence and representative feature results.
- If device access is unavailable, mark device validation as NOT RUN. Never substitute a guessed result.
- Preserve failure logs and explain unresolved failures; do not hide a red CI run.

## Versioning and release

- Maintain `CHANGELOG.md` with an `[Unreleased]` section.
- Follow Semantic Versioning: MAJOR.MINOR.PATCH. Android `versionCode` must increase monotonically and be recorded separately.
- Use annotated tags named `vX.Y.Z`; do not create tags for unvalidated builds.
- A release requires owner-approved PRs, green required CI, passing regression tests, successful Open/Legacy build paths as applicable, APK structure validation, signature verification, SHA-256 checksum, and real-device LSPosed validation.
- Do not publish a formal GitHub Release if any mandatory gate is missing.
- Never commit keystores, passwords, signing properties, access tokens, private payloads, APKs or user data.

## Required delivery report

Every completed task must report:

1. PR URL and state (Draft/Ready/Merged).
2. Branch and commit SHA.
3. Files and behavior changed.
4. Tests run and exact outcomes.
5. CI run URLs and build state.
6. APK path/artifact only if it actually exists; include package ID, ABI, signer certificate SHA-256 and APK SHA-256 when applicable.
7. Real-device validation state.
8. Known issues, unverified assumptions and rollback notes.

## Authority and limitations

These instructions are intended for all repository participants and should be loaded by coding agents that support repository instruction files. A text file alone cannot force every external AI or user to read it. Server-side enforcement requires GitHub branch protection/rulesets, required pull requests, required status checks and Code Owner review. The repository owner must configure those settings in GitHub.

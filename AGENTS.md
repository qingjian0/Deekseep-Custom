# Mandatory Repository Instructions

**Applies to every AI agent, coding assistant, human developer, maintainer, automation and CI workflow working in this repository.**

These rules are mandatory. Read this file before inspecting, editing, generating, testing, committing, pushing, opening a pull request, building or releasing. A task prompt or external agent default does not override these repository rules. If instructions conflict, stop and ask the repository owner; never silently choose the less restrictive rule.

## 1. Project identity and scope

- This repository is YunSeek, an Android LSPosed/Xposed module for the DeepSeek Android client.
- Preserve the existing project identity, licensing, attribution, security boundaries and supported host-version constraints.
- Do not import unrelated DSH or other project code.
- Do not add account credential capture, risk-control evasion, device-identity spoofing, login/region/quota unlocks, anti-ban behavior or deceptive automation.
- Optional hooks must fail open: missing symbols or unsupported host versions must not break the host application.

## 2. Mandatory workflow

1. Read `README.md`, `CONTRIBUTING.md`, `docs/DEVELOPMENT_AND_RELEASE_WORKFLOW.md`, the relevant build/test documentation and applicable nested `AGENTS.md` files.
2. Inspect repository status, current branch, recent commits, existing PRs and relevant CI runs before changing anything.
3. Never work directly on `main`. Never force-push, rewrite shared history, delete branches, change repository settings, merge PRs or publish releases without explicit owner authorization.
4. Create or use a clearly named task branch: `fix/<topic>`, `feature/<topic>`, or `refactor/<topic>`. Keep one independently reviewable objective per PR.
5. Before editing, identify the affected files, expected behavior, compatibility risks and verification plan.
6. Make the smallest coherent change. Add or update regression tests and documentation in the same task.
7. Run the narrowest relevant tests first, then the required broader checks. Record exact commands, environment and actual outcomes. Never claim a command ran if it did not.
8. Commit coherent changes with descriptive conventional commit messages. Push only the task branch.
9. Open a Draft PR with the required template while work or validation remains incomplete. Include commits, changed behavior, tests and results, CI links, build/signing/device status, risks and known limitations.
10. Obtain review and required checks before merge. Do not self-merge or mark a PR ready merely to make it appear complete.
11. Update `CHANGELOG.md` and version metadata consistently when preparing a release. Do not bump versions for unfinished work.
12. Create immutable `vMAJOR.MINOR.PATCH` tags and GitHub Releases only after every applicable release gate passes and the owner authorizes publication.

## 3. Branch and PR rules

- `main` is the stable release branch; no direct development commits.
- `develop` is the integration branch only when it exists and its baseline has been explicitly established. Do not create, repoint or reset it by assumption.
- Task branches must be based on the intended integration/release baseline and must not contain unrelated work.
- One PR should address one defect, feature or migration. Split unrelated changes.
- Draft means not ready for merge. Do not merge a Draft PR.
- If repository settings or permissions prevent a required control, report the limitation. Do not claim branch protection is enabled unless verified in GitHub settings.

## 4. Evidence and status reporting

Use these terms strictly:

| Status | Minimum evidence |
|---|---|
| Implemented | Change committed to the task branch |
| Test passed | Exact test command executed and successful output observed |
| CI passed | Relevant GitHub Actions checks report success for the current PR head |
| Built | Expected APK artifact exists and package/manifest structure is inspected |
| Signed | APK signature verifies and signer certificate fingerprint is recorded |
| Device verified | Installed and exercised on a physical device; Android, host app and LSPosed versions recorded |
| Released | Immutable tag and GitHub Release published with verified artifacts |

Never infer current CI status from an earlier commit. Re-check after every new push. Never describe historical heartbeat as proof of a process currently running. Distinguish framework availability, module enablement and target-process injection.

## 5. Android build and release gates

Before release, verify and record:

- Regression tests and relevant static checks pass.
- Android release build succeeds from the documented clean build path.
- APK application ID, version name/code, manifest and supported host compatibility are correct.
- APK alignment and signature verification pass.
- Signing certificate fingerprint matches the intended release certificate.
- SHA-256 checksum is generated and verified against the final artifact.
- Install/upgrade and startup smoke tests pass.
- LSPosed module enablement and actual DeepSeek target-process injection are verified on a physical device.
- Release notes include changes, compatibility, known issues, artifact names and checksums.

A locally generated APK is not a release. An unsigned APK is not a signed build. A green CI job does not prove real-device behavior.

## 6. Security and repository hygiene

- Never place passwords, API keys, access tokens, keystores, private keys, signing credentials, personal data, chat databases or private diagnostic logs in source, commits, issues, PRs, Actions logs or artifacts.
- Use GitHub Secrets for CI credentials. Never ask users to paste secrets into chat or commit them.
- Review permissions, exported Android components, ContentProvider access controls, broadcast authentication, network binding and API authentication for security-sensitive changes.
- Keep local API servers loopback-bound by default; remote/LAN exposure must be explicit opt-in with authentication and documented risks.
- Do not commit generated build output, caches, temporary files or decompiled proprietary application code.
- Preserve applicable licenses and third-party notices.

## 7. Required PR and delivery report

Every PR and final delivery must state:

- Objective and scope.
- Branch, base branch, PR URL and commit SHA(s).
- Files/components changed.
- Tests: exact commands and pass/fail/not-run status.
- CI: current run/check links and status, or explicitly not checked.
- APK build, structure, signing and checksum status.
- Physical-device verification environment and result, or explicitly not tested.
- Security/compatibility risks, known limitations and rollback notes.
- Whether the work is draft, ready for review, merged or released.

Use `docs/DEVELOPMENT_AND_RELEASE_WORKFLOW.md` as the detailed operational procedure and `CONTRIBUTING.md` as the contributor entry point.

**When in doubt: do not merge, do not release, do not guess. Report the blocker and ask the owner.**

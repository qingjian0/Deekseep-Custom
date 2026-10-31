# Development, Branch and Release Workflow

## 1. Branch model

| Branch | Purpose | Direct work |
|---|---|---|
| `main` | Stable releases only | Prohibited |
| `dev` | Integration of reviewed work | Prohibited |
| `feat/*` | One feature | Yes, on its own branch |
| `fix/*` | One bug fix | Yes, on its own branch |
| `refactor/*` | One structural change | Yes, on its own branch |
| `docs/*` | Documentation/policy | Yes, on its own branch |
| `test/*` | Test-only change | Yes, on its own branch |
| `build/*` | Build/CI change | Yes, on its own branch |
| `release/*` | Release preparation | Only after owner approval |

Normal flow:

```text
main  <--- approved release PR ---  dev
                                      ^
                                      |
                           feature/fix/docs PR
                                      ^
                                      |
                           dedicated work branch
```

Create work branches from the latest `dev`. Keep each PR limited to one primary objective. A change that depends on another unmerged PR must declare that dependency and must not be merged out of order.

## 2. Required development sequence

1. Inspect repository status, default branch, `dev`, recent commits, open PRs and workflow results.
2. Read `AGENTS.md`, relevant docs, source entry points, manifests, source sets and build scripts.
3. Create a descriptive work branch from current `dev`.
4. Reproduce the issue or establish a baseline before editing.
5. Implement the smallest coherent change and add regression tests.
6. Run targeted tests, module/legacy tests and Android build validation.
7. Open a Draft PR early with scope, evidence, risks and remaining validation.
8. Review the diff for unrelated changes, generated files, secrets, license and package identity.
9. Convert to Ready only after required CI and local validation pass.
10. Merge into `dev` only after repository-owner review.
11. Promote `dev` to `main` only through a separate approved release PR.

Never use force-push to repair a shared branch. Do not push directly to `main` or `dev`.

## 3. PR scope and review

A PR must have:
- one clear goal and a conventional title;
- a root-cause explanation, not only a symptom description;
- exact list of changed behavior and files;
- regression tests and commands;
- CI links and build evidence;
- host-version compatibility impact;
- known risks and rollback plan;
- explicit NOT RUN labels for tests not executed.

Do not combine unrelated package migration, hook repair, API Gateway, UI redesign or release changes. Dependent PRs must state their merge order.

## 4. Testing requirements

### Source and unit tests
- Test policy/state logic with deterministic tests.
- Test package migration and manifest/entry consistency.
- Test hook bootstrap ordering and independent feature failure handling.
- Verify no optional hook failure prevents native host behavior.

### Android build
- Build Open and Legacy paths separately where both are affected.
- Use the documented JDK/Gradle/Android SDK toolchain.
- Check the exact build variant and output path.
- A debug build does not qualify as a release build.

### APK inspection
For every release candidate:
1. `unzip -t app.apk`
2. Verify `AndroidManifest.xml`, DEX files, Xposed entry declaration and required assets.
3. Verify application ID, versionName/versionCode and ABI contents.
4. `zipalign -c -p 4 app.apk`
5. `apksigner verify --verbose --print-certs app.apk`
6. Record signer certificate SHA-256.
7. `sha256sum app.apk` and publish the checksum file.

Never state that an APK exists or is signed unless the exact artifact was inspected.

### LSPosed real-device validation
For module changes, test the actual target application:
- Android and LSPosed version;
- DeepSeek channel/version/versionCode;
- module application ID and Xposed entry;
- enabled LSPosed scope;
- target process and process name;
- injection evidence;
- hook registration count and callback invocation evidence;
- representative feature behavior;
- crash/logcat/LSPosed log review.

Framework Binder connection, target heartbeat, hook registration and hook invocation are separate signals. Missing evidence is UNKNOWN/WAITING, not proof that the module is disabled.

## 5. Version management

Use Semantic Versioning:
- MAJOR: incompatible module/runtime/API contract change;
- MINOR: backward-compatible user-facing feature;
- PATCH: backward-compatible bug fix.

Android `versionCode` is a separate monotonically increasing integer. Do not derive it from SemVer without a documented mapping.

Maintain `CHANGELOG.md`:
- add user-visible changes under `[Unreleased]`;
- move the section into a versioned heading only when preparing a validated release;
- do not claim a fix in the changelog before it is merged and verified.

## 6. Tag and Release process

1. Confirm `dev` is green and all intended PRs are merged.
2. Update versionName, versionCode and CHANGELOG in a dedicated release PR.
3. Build signed release APKs using the private release keystore from GitHub Secrets.
4. Verify APK structure, alignment, signer certificate and SHA-256.
5. Complete real-device LSPosed injection and feature regression.
6. Obtain explicit repository-owner approval.
7. Merge release PR into `main`.
8. Create annotated tag `vX.Y.Z` on the approved main commit.
9. Create GitHub Release with APKs, SHA-256 file, notes and validation evidence.

No formal tag or release may be created while a required gate is pending or failed. CI-generated debug artifacts are not formal releases.

## 7. Security and repository hygiene

Never commit keystores, passwords, tokens, private signing properties, private payloads, personal data, target-app databases, unredacted diagnostics or built APKs. Use repository secrets for signing credentials. Keep third-party license notices and upstream attribution intact.

## 8. GitHub server-side protection

The intended configuration for `main` is:
- require a pull request before merging;
- require at least one approval;
- require Code Owner review;
- require the selected CI status checks;
- require conversation resolution;
- block force pushes and branch deletion;
- prevent bypass where supported.

`CODEOWNERS` only requests ownership. It becomes a merge gate only when the corresponding branch protection/ruleset option is enabled in GitHub Settings. Repository text files cannot enable those server-side controls by themselves.

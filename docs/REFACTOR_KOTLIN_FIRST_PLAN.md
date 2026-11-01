# Kotlin-first Hook Architecture Refactor Plan

> Status: implementation in progress. P0 CI/build integration and P1 per-step Hook installation tracking are active; P2 has a first shared Kotlin runtime migration; P3 has extracted screens and a lifecycle-aware status ViewModel; P4 device validation remains pending.
> Baseline: `dev` at `8e2edf53b4982a11e720f39437ee562df696136c`.
> Work branch: `refactor/kotlin-hook-architecture`.
> Scope: preserve the existing DeepSeek host features and LSPosed/Xposed compatibility while moving new and migrated application logic toward Kotlin.

## 1. Executive decision

Do not rewrite the Hook engine in one pass. The current code has real host-specific behavior, version maps, reflective signatures, and an around-hook compatibility contract. A bulk Java-to-Kotlin rewrite or a direct switch from legacy Xposed to Modern Xposed would make regressions difficult to isolate.

The target is a **Kotlin-first implementation with a narrow Java compatibility boundary**:

- Kotlin for new runtime contracts, hook orchestration, feature logic, state models, diagnostics, and Compose UI.
- Keep the current Java Xposed entry/adapter as a compatibility shim until equivalent Kotlin/Modern API paths pass the same regression matrix and real-device tests.
- Keep host symbol mapping and reflective invocation behavior stable; migrate them only behind tests.
- Keep legacy and Modern Xposed API integrations as separate build/runtime adapters. Do not load both APIs in the same module artifact or mix their hook objects.

## 2. Source baseline findings

### 2.1 Runtime and entry

- `module/src/com/dsmod/probe/Main.java`: 10,724 lines / approximately 535 KB in the audited `dev` snapshot.
- `Main` currently combines package admission, host compatibility initialization, main-thread bootstrap, application lifecycle callbacks, feature-hook orchestration, compatibility forwarders, and remaining feature implementations.
- The existing `TargetProcessGuard` is already a small pure-JVM boundary for target-package admission and duplicate-process suppression.
- `Main.installAllFeatureHooks()` is a large sequential installer. The current refactor introduces stable per-step IDs, a process-local `FeatureHookInstallRegistry`, scoped interceptor registration and independent best-effort groups. The global installed flag is now set only when the tracked steps complete without failures.
- `LegacyXposedModule` scopes registrations by installer ID and interceptor class per reflected `Member`. A retry can revisit a failed feature group while skipping hookers already registered by that same group; unscoped legacy callers retain their historical append behavior.
- Startup preparation with external side effects (local image/session restore, obsolete-trigger cleanup, auto-backup scheduling and history migration) is now represented as separately tracked steps, preventing completed work from being repeated merely because a later Hook group failed.
- Best-effort and top-level installation handlers now rethrow `VirtualMachineError` and `ThreadDeath`; they are not downgraded to recoverable Hook failures. The registry JVM regression now covers fatal-error identity propagation.
- Remaining limitation: several older feature installers still catch/log internal failures and return normally. Until those installers expose completion/degraded results, their registry entry may be marked installed even when an internal sub-hook was unavailable. Convert and test these boundaries incrementally; do not claim the registry proves every reflected host member was installed.

### 2.2 Existing feature extraction

The code is not a single monolith anymore. The following feature classes already exist, but still use package-level access, singleton instances, and extensive direct calls back into `Main`:

| Source file | Approx. lines | Current responsibility |
|---|---:|---|
| `HookChatPipeline.java` | 2,320 | Chat request/response, prompt pipeline, content-filter and reply lifecycle hooks |
| `HookAccountLoginSecurity.java` | 3,934 | Account/login-related hooks, local API/session integration and related runtime state |
| `HookSessionManagement.java` | 3,352 | Session navigation, local session state and history lifecycle |
| `HookAttachmentPipeline.java` | 2,322 | Attachments, image and expert-flow integration |
| `HookUiSurface.java` | 3,836 | Host UI, Compose surface and theme hooks |
| `HookAgentPipeline.java` | to be inventoried | Agent tool/heartbeat integration |
| `HookConfigRollout.java` | to be inventoried | Remote configuration and rollout hooks |

These are useful migration seams, but not yet independent modules. `Main` remains a compatibility facade used by existing features and UI classes. Some version-specific hooks also remain inside `Main`.

### 2.3 Legacy Hook adapter

- `LegacyXposedModule.java` implements an around-hook chain on traditional `XC_MethodHook`.
- One traditional callback is registered per reflected `Member`; hookers are held in a `CopyOnWriteArrayList`.
- Optional interceptor exceptions are isolated individually. If the original method has not run, the remaining chain proceeds; if it has run, its saved result/exception is reused.
- Required interceptor failures and fatal VM/thread errors propagate. The original method is invoked through `XposedBridge.invokeOriginalMethod`, with argument compatibility checks and exception unwrapping.
- The regression harness exercises API levels 82–102 and checks ordering, replacement arguments, original exception behavior, optional failure paths and exactly-once invocation.

This adapter already contains important correctness logic. It must be treated as a compatibility contract and expanded with tests before changing its implementation. In particular, do not replace `param.setThrowable(error)` with blanket exception swallowing: that callback is also how required-hook and original-method failures are returned to the framework.

### 2.4 Host compatibility

- Host identity and symbol resolution are separated into `HostCompat`, `HostRemap`, and version-specific support such as `HostV271`.
- Mapping resources currently include 287, 289 and 290 tables.
- Hook code depends on runtime `ClassLoader`, obfuscated symbols, method signatures, and host-generation checks. These are runtime compatibility boundaries, not ordinary implementation details.
- Unknown/unmaintained host versions are intended to avoid installing uncertain feature hooks.

No mapping table should be regenerated, renamed, or simplified without its source provenance and version-specific regression evidence.

### 2.5 Launcher and UI

- `SettingsActivity.kt` is approximately 1,191 lines and already uses Kotlin, Jetpack Compose and Material 3.
- UI content, bridge/IPC calls, import/export parsing, validation and screen composition are currently concentrated in one Activity source file.
- `DeekseepTheme.kt` already provides Material 3 light/dark schemes and dynamic color on supported Android versions.
- UI refactoring can therefore proceed independently of the host Hook runtime, provided the existing IPC contract, configuration paths, exported components and preference keys remain unchanged.

### 2.6 Build-system constraint

The repository has two materially different compilation paths:

1. The Open launcher Gradle build stages shared `module/src` sources into the Android app build. It already compiles Kotlin and Compose.
2. `module-universal/build.sh` and `module-legacy/build.sh` collect Java sources and invoke `javac` directly before D8. They do not compile Kotlin.

This is the main blocker to moving shared Hook feature code to Kotlin. Adding `.kt` files under the shared module source tree without first unifying or explicitly extending these build paths would silently omit those classes from legacy/universal APKs.

The Gradle launcher currently declares AGP 9.3.0, Kotlin Compose plugin 2.4.10, Gradle 9.8.2 in CI, compile SDK 37, min SDK 24, target SDK 34, Material 3 1.4.0, and an older Compose BOM 2025.10.00. The Compose BOM should be refreshed as a coordinated set; explicit versions should not be scattered across dependencies.

## 2.7 Implementation status (2026-11-01)

### P0 — build baseline and CI

- Added a dedicated refactor validation workflow with separate JVM regression and Android artifact jobs.
- SDK bootstrap now uses the runner's installed command-line tools directly; Android 17 is installed using the platform package ID `platforms;android-37.0`, not the unavailable `platforms;android-37`.
- The CI order builds Legacy and Universal before running `test-thinking-regression.sh`, because that test consumes the Universal class output.
- The workflow verifies APK ZIP structure, at least one DEX file (including multi-dex outputs), 4-byte ZIP alignment and APK signatures. Full Legacy, Universal and Compose build validation remains pending a successful Android job.
- CI signing material is ephemeral and limited to debug verification. No release keystore or publication path is used.

### P1 — Hook install lifecycle

- Added `FeatureHookInstallRegistry` with `NEW / INSTALLING / INSTALLED / SKIPPED / DEGRADED / FAILED` states, attempt counts and a status snapshot.
- Wrapped the main feature installer groups with stable IDs. Best-effort failures are represented as degraded instead of being treated as installed.
- Added a ThreadLocal installer scope to the legacy adapter. Its deduplication key uses `(scope, Member, Hooker class, occurrence ordinal)`, so multiple same-class interceptors on one Member are preserved while a retry skips the exact prior occurrences.
- Best-effort and top-level installers propagate `VirtualMachineError` and `ThreadDeath` instead of treating fatal VM/control signals as recoverable Hook failures.
- Extended the API 82–102 adapter regression with scoped retry and same-class multiplicity tests, and added pure-JVM registry tests for success, failure/retry, skipped state, fatal error propagation and concurrent callers.
- Remaining work: convert installers that swallow internal failures, audit version-specific atomic guards, and add fault-injection tests for partial registration across multi-member installers.

### P2 — Kotlin runtime migration (in progress)

- Added the standalone `module-kotlin-runtime` Gradle JVM project using Kotlin 2.4.10 and JVM 1.8 bytecode.
- Migrated two pure-JVM runtime units from Java to Kotlin: `FeatureHookInstallRegistry` and `TargetProcessGuard`. Their Java-facing names, nested `Admission`/`State` enums, static `admit` entry point, installer SAM, status fields and method calls remain compatible with `Main.java` and the existing Java regression harness.
- Legacy and Universal build scripts now compile the shared runtime JAR, include the resolved Kotlin standard library in javac/D8 inputs, and package all generated `classes*.dex` files.
- The Compose Launcher source set includes the same runtime Kotlin source root through AGP 9 `kotlin.directories`.
- The registry and process-admission JVM test runners build the Kotlin runtime artifact and run the existing Java regressions against it; the Android-dependent thinking test also includes the runtime JAR/stdlib on its classpath.
- Gradle 9.8.2 bootstrap is shared by the runtime tests, Legacy/Universal builds and launcher Open build.
- Validation gate: the first Kotlin runtime CI attempt exposed a Java 21/Kotlin 1.8 target mismatch; the runtime module now explicitly aligns Java and Kotlin output to 1.8. A later JVM CI run passed both Kotlin runtime regression scripts. The Android artifact matrix has not yet completed successfully. Expand the runtime incrementally; do not migrate host-reflection logic until both distribution paths prove the runtime classes and stdlib are present in APK DEX output.

### P3 — Compose launcher decomposition (in progress)

- Extracted stateless building blocks into `LauncherSharedScreens.kt` and Home/Features/Settings/Help/About/Data pages into `LauncherStaticScreens.kt`. Activity behavior is supplied through callbacks and a status slot; visual hierarchy is retained.
- The Data screen emits export/import/full-backup actions through callbacks; ActivityResult launchers, ZIP/JSON codecs, SAF I/O and the Closed full-backup bridge remain owned by the Activity.
- Added `LauncherStatusRepository`, `LauncherStatusViewModel` and immutable `LauncherStatusUiState`. Host-version and activation reads run on `Dispatchers.IO`; Compose observes the state using `collectAsStateWithLifecycle`.
- Added AndroidX Lifecycle 2.11.0 dependencies for ViewModel coroutine scope and lifecycle-aware Compose collection.
- The configuration bridge state, file import/export, Activity Result launchers and full-backup activity results remain in `SettingsActivity`. They are not yet migrated to repositories/ViewModels. Preserve the authenticated broadcast contract, ZIP/JSON shape, backup paths and SAF behavior while extracting that slice.

### P4 — distribution and device validation

Partially prepared only. The CI matrix is in place, but no completed APK matrix or real-device LSPosed validation has been confirmed. Host-version compatibility claims remain limited to existing tested evidence.

## 3. Target architecture

Keep the architecture small. No DI framework, generic plugin framework, service locator, cross-platform SPI, or broad domain rewrite is proposed.

### Layer A — Entry and framework adapters

- Keep a stable entry class and module metadata for each supported framework integration.
- Legacy adapter: retain the current traditional Xposed callback contract and its API 82–102 regression coverage.
- Modern adapter: introduce a separate Modern Xposed API integration using the published `io.github.libxposed:api` artifact, behind a separate build variant/artifact and entry metadata.
- Both adapters call the same Kotlin runtime contracts where their lifecycle and callback semantics permit it. Do not pass framework-specific callback objects into feature code.

### Layer B — Kotlin Hook runtime

Introduce a small Kotlin runtime with explicit types:

- `HookRuntimeContext`: target package/process, host version/channel, host `ClassLoader`, diagnostics and framework adapter.
- `HookFeature`: one feature group with a stable ID, version predicate, required/optional classification and installation operation.
- `HookInstallResult`: installed, skipped (with reason), degraded or failed.
- `HookRegistry`: process-scoped idempotency keyed by stable hook ID and resolved member identity.
- `HookInstallCoordinator`: tracks each feature/hook state and allows retry of failed optional work without registering successful hooks twice.

Required state transitions: `NEW -> INSTALLING -> INSTALLED`, with explicit `SKIPPED`, `DEGRADED` and `FAILED` terminal/observable outcomes. A failed group must not be reported as fully installed. Partial success must be recorded at hook granularity so retry only touches missing registrations.

### Layer C — Host compatibility

- `HostVersionDetector`: version/channel/package/signature facts and explicit supported-generation result.
- `HostMappingRepository`: immutable mapping metadata and validated mapping resource loading.
- `HostClassResolver`: all host class/member lookup bound to the current process ClassLoader.
- `MemberSignature`: parameter/return/modifier checks before a Hook is registered.

Do not globally cache host `Class` or `Member` instances across process ClassLoaders. Avoid retaining Activity/View references in static runtime state.

### Layer D — Kotlin feature domains

Migrate one domain at a time, preserving existing behavior and stable hook IDs:

1. Chat pipeline.
2. Session management.
3. Attachment/image pipeline.
4. Agent pipeline.
5. Host UI surface.
6. Account/security-related features only after a dedicated policy and behavior review.

Each feature receives `HookRuntimeContext` and explicit collaborators. Existing `Main.*` compatibility forwarders remain temporarily; remove them only after all call sites have migrated and tests prove there are no hidden bytecode/resource references.

### Layer E — Kotlin + Material 3 launcher

Split the existing Activity into:
- Activity/IPC bridge and lifecycle owner.
- Typed configuration repository and import/export codec.
- Screen-level Compose files (Home, Features, Settings, Data, Help/About).
- Reusable Material 3 components and theme.

Keep the existing app identity, component names, IPC action names, bundle keys, preference/file paths and export formats. Avoid introducing a ViewModel/DI framework solely for style; use lifecycle-aware state holders where they solve an actual state ownership problem.

## 4. Xposed API and dependency policy

### 4.1 Xposed API

The current public Modern Xposed API artifact is `io.github.libxposed:api:102.0.0`. It is not the same integration as the legacy `de.robv.android.xposed` callback API, and the Modern API's own documentation warns against mixing it with `XposedBridge` in the same module integration.

Decision:

- Do not replace the existing legacy adapter immediately.
- Add Modern API support as a separate artifact/variant, with its own entry metadata and dependency configuration.
- Keep a shared Kotlin feature contract only where lifecycle, hook invocation, and error semantics can be represented consistently.
- Verify actual framework version and entry discovery on-device before changing the default artifact.
- If Modern API is not available on the user's LSPosed installation, the legacy artifact remains the compatibility fallback.

### 4.2 Kotlin, AGP and Compose

As of this audit date, official release sources list Kotlin 2.4.21 as the latest stable Kotlin release, AGP 9.5.0 as the latest stable Android Gradle Plugin, Compose BOM 2026.09.00 as a current stable BOM, and Material 3 1.4.0 as stable. Compose 1.5.0 remains beta/pre-release in the release table.

Upgrade policy:

- Evaluate Kotlin 2.4.21 and AGP 9.5.0 together with the existing Gradle 9.8.2/JDK 21/SDK 37 build.
- Move Compose dependencies to the current stable BOM and remove redundant per-artifact versions where the BOM manages them.
- Keep Material 3 on stable 1.4.0; do not adopt Material 3 beta/alpha for the production launcher.
- Confirm third-party UI libraries, AndroidX Activity/Core and the material-icons trimming task against the selected BOM before upgrading.
- Pin plugin/dependency versions centrally and enable Gradle dependency verification/locking where practical. Do not use dynamic versions or unreviewed snapshots.

## 5. Migration phases and gates

### Phase 0 — Baseline and invariants

- Record exact branch/commit, build variants, package/application IDs, manifests, resources, preference keys, storage paths, host maps and current CI status.
- Run the existing JVM regression scripts and Open Gradle build before functional edits.
- Capture the current Hook installation order and per-host feature matrix.
- Establish behavior tests for install idempotency, partial failure/retry, original-method exactly-once semantics, argument replacement, exception propagation and ClassLoader isolation.

### Phase 1 — Build pipeline convergence

- Make Gradle the canonical compiler for shared Java/Kotlin Hook sources.
- Preserve Legacy and Universal output identities and metadata.
- Replace direct `javac`-only source collection with a Gradle-backed task or a tested Kotlin compiler pipeline; do not silently drop Kotlin sources.
- Retain reproducible AAPT2/D8/zipalign/signing and verify all output artifacts.
- Keep protected/closed signing inputs and secrets isolated; never copy private signing material into the open build.

Gate: all existing Open, Legacy and Universal artifacts compile with byte-for-byte-equivalent metadata/entry declarations where required, and the existing Java regression matrix remains green.

### Phase 2 — Runtime contracts and install coordinator

- Add the Kotlin contracts and coordinator behind the existing Java entry/adapter.
- Model hook groups and required/optional failures explicitly.
- Make installation idempotent at individual hook identity, not only at the whole-module boolean.
- Preserve current install ordering until a documented dependency graph proves a safe change.

Gate: repeated install, concurrent install, mid-group failure and retry do not duplicate registrations or skip failed required hooks.

### Phase 3 — Kotlin feature migration

- Migrate one feature class at a time.
- Keep a Java facade only for remaining call sites.
- Compare old/new hook inventories by host version and method signature.
- Do not change hook target, callback phase, argument/result behavior, thread, timing or side effects in a language-only migration.

Gate: per-domain regression tests, all host-map tests, all build variants, and device validation for changed runtime behavior.

### Phase 4 — Modern Xposed adapter

- Add a separate Modern API artifact/variant and its entry metadata.
- Implement and test the same feature contracts using Modern API callback types.
- Validate framework discovery, scope, process delivery, ClassLoader and unload/reload behavior on actual LSPosed installations.
- Keep legacy as default until the Modern variant passes the supported device/framework matrix.

Gate: no API mixing, no missing entry metadata, no host startup regression, and verified Hook behavior on real devices.

### Phase 5 — Material 3 launcher and dependency refresh

- Split `SettingsActivity.kt` by responsibility.
- Keep Material 3 component/state patterns and existing visual identity; no unrelated redesign.
- Upgrade Kotlin/AGP/Compose as coordinated, independently reversible changes.
- Run lint, UI tests where present, import/export compatibility tests and APK checks.

Gate: no configuration/IPC/export regression, lint/build green, package/signature/manifest checks pass.

## 6. Non-negotiable Hook correctness rules

- A Hook must identify its host version range, class/member signature, callback phase, required/optional status and failure policy.
- A missing symbol or unsupported host version must skip only the relevant optional feature and expose a diagnostic reason.
- Original method invocation must be exactly once unless the hook explicitly and intentionally replaces it.
- Preserve original exceptions; do not confuse an interceptor exception with an original-method exception.
- Do not block the main thread with dex scanning, disk IO, network requests or coroutine waits.
- Host ClassLoader objects and reflected members must be scoped to the process and loader that produced them.
- Installation is idempotent per hook ID/member; partial failure must be retryable without duplicate callbacks.
- Any hook target or timing change requires host-version-specific regression coverage and real-device verification.

## 7. Validation and release boundaries

Use the repository's L0–L5 validation definitions. A green JVM script does not prove host Hook operation; a successful APK build does not prove LSPosed discovery; one device/host version does not prove all mapped generations.

- L0: diff, manifests, metadata, dependency/security review.
- L1: JVM regression and Kotlin unit tests.
- L2: Open/Legacy/Universal Gradle builds.
- L3: APK structure, DEX, package/version, ABI, alignment and signature verification.
- L4: isolated simulated host/ClassLoader/callback tests.
- L5: actual LSPosed + DeepSeek device matrix.

No signed release, Release tag, or main-branch merge is part of this refactor branch.

## 8. Known execution limitation during this audit

The GitHub connector can read and write repository Git objects, but the current container cannot resolve `github.com` for a local clone. Therefore no local compiler/test execution is claimed at this stage. Code changes must not be marked complete until a working source checkout or GitHub Actions run provides actual build/test evidence.

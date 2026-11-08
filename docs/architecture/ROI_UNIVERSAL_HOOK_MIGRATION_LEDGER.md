# ROI Universal Hook — Production Evidence and Feature Migration Ledger

Last audited: 2026-11-08  
Branch: `work/roi-hook` (canonical name; renamed from `work/roi-universal-hook-r1-r9`)  
PR: #18 (Draft; base `dev`)

## 1. Executive status

The Universal Hook runtime and the Xposed shared-callback backend are implemented, but the project is **not yet ready to migrate the existing production feature hooks**. This ledger separates three things that must not be conflated:

1. **Discovery evidence** — static DEX/string/call-graph observations and loaded-class metadata. Useful for narrowing candidates; never grants Hook permission.
2. **Runtime invocation evidence** — payload-free observations emitted by explicitly trusted existing callbacks. Useful for corroborating exact method descriptors and call order; current response/Coroutine coverage is incomplete.
3. **Reviewed activation evidence** — a code-reviewed, version-scoped adapter record that explicitly approves the exact host fingerprint, semantic role, class, method, full descriptor, return/lifecycle contract and activation policy. No general production provider currently supplies this complete record.

The current DexKit candidate constructor intentionally sets role to `unresolved`, `adapterApproved=false`, and an empty approved-fingerprint set. It uses `Policy.discoveryOnly()`. This is correct fail-closed behavior and must not be relaxed to make the new runtime appear active.


## 1.1 2026-11-08 follow-up engineering audit

The following source-level hardening was committed on the stacked implementation branches. These changes have not yet been merged into this ledger's base branch and are not represented as runtime-validated.

- **Lifecycle dependency integrity (PR #14):** dependency/conflict sets are defensively snapshotted. A prerequisite cannot be uninstalled while a dependent is ACTIVE, INSTALLING, UNINSTALLING, or FAILED with a retained unhook handle. This closes the race where a dependent installer runs outside the coordinator lock while another thread removes its prerequisite.
- **Lifecycle regression coverage:** added caller-owned mutable-set coverage, a latch-controlled INSTALLING-dependent teardown race, and a failed-rollback retained-handle dependency case. Tests are authored but intentionally not executed in this phase.
- **Cross-version role integrity (PR #15):** a source semantic method role is resolved only when the target profile independently declares the same role with the same normalized prototype. A structurally similar method cannot override contradictory target role evidence. Regression coverage was added; tests are authored but intentionally not executed.
- **Semantic profile evidence:** missing structural dimensions remain in the score denominator; profiles with missing, detached, or ambiguous method-role evidence are rejected.
- **Universal runtime batch dependency scope:** an already-active dependency must belong to the same ClassLoader scope even if its ID is also present in the submitted batch.
- **Serializer / Provenance (PR #11):** source audit is still in progress. No blanket integration or merge is approved; its shared CI workflow changes remain outside this phase.
- **Validation boundary:** no CI, JVM test suite, APK build, emulator, or physical-device validation is performed until the explicitly requested final validation phase. Source edits and static review must not be described as passing runtime tests.


### Serializer registration discovery hardening (PR #11, 2026-11-08)

A second-pass audit found that `inspectAddElementCall` returned immediately after the first recognized `addElement(String, boolean)` invoke. That could hide a later unresolved invoke in the same method and incorrectly treat registration-candidate discovery as complete/unique. The scanner now continues through every invoke and returns incomplete if any invoke decode or method resolution failed, regardless of whether a registration call was already found. A deterministic decision-helper regression test covers all found/absent × complete/incomplete combinations.

This change is source-reviewed only. The serializer test suite, CI, APK build, emulator, and physical-device checks remain deferred to the final validation phase.


### Lifecycle conflict-domain integrity (PR #14, 2026-11-08)

A further lifecycle audit found that conflict checks previously treated only ACTIVE and INSTALLING targets as occupying a conflict domain. A target remains potentially attached while its unhook callback is running, and a FAILED target with a retained handle has not proven detachment. Conflict checks now reserve the domain for ACTIVE, INSTALLING, UNINSTALLING, and FAILED-with-handle states in both declared conflict directions. Regression tests cover a latch-controlled unhook race and a failed rollback retaining the handle. Tests remain unexecuted until the final validation phase.

## 2. Actual call chains

### 2.1 Existing feature-hook path

```text
Main.handleLoadPackage
  -> HostCompat.initialize(host ClassLoader)
  -> RoiRuntimeInvocationObserver.initialize()
  -> Main.installAllFeatureHooks(host ClassLoader)
  -> installHookStep / installBestEffortHook
  -> FeatureHookInstallRegistry
  -> HookChatPipeline / HookUiSurface / HookSessionManagement / ...
  -> LegacyXposedModule.hook(Member)
  -> per-Member HookTarget.intercept(Hooker)
  -> one shared XposedBridge.hookMethod callback per Member
  -> ordered Hooker chain -> invokeOriginalMethod exactly once
```

The existing production hooks are still registered through the legacy feature pipelines. They are not silently migrated to Universal HookSpec.

### 2.2 Universal Hook opt-in path

```text
Main.installRoiUniversalHookBatch(groupId, specs, loader, fingerprint, adapters,
                                  evidenceSource, policySource)
  -> FeatureHookInstallRegistry
  -> RoiUniversalHookRuntime.Installer.install(...)
  -> exact ClassLoader-scoped Resolver
  -> RoiHookResolverActivationGate
       -> EvidenceSource.find(spec)
       -> PolicySource.policy(spec)
       -> exact sourceScope check
       -> derived candidateId check
       -> fingerprint / role / class / method / params / return check
       -> RoiHookResolver.resolve(candidate, policy)
  -> LegacyXposedModule.universalHookBackend()
  -> existing per-Member HookTarget.intercept(...)
  -> logical handle
```

Uninstall is logical: `uninstallRoiUniversalHookBatch(groupId)` disables the group's callback handles. The shared native Xposed callback remains installed so other features on the same Member are not removed.

### 2.3 Discovery and observation path

```text
Main.handleLoadPackage
  -> RoiRuntimeInvocationObserver.initialize()
  -> existing HookChatPipeline request-construction callback -> recordBuilt(...)
  -> existing HookChatPipeline transport callback -> recordTransport(...) / recordTransportResult(...)
  -> HookAttachmentPipeline.installExpertFlowCollectHook
       -> b41.b(q03, Continuation) collect boundary
       -> suspend emit: preserve original collector + UNSUPPORTED_SUSPEND_COLLECTOR coverage
       -> non-suspend emit: preserve original collector + UNSUPPORTED_NON_SUSPEND_COLLECTOR coverage
  -> HookChatPipeline vv7.e(Object) response deserializer -> rebuilt mv
       -> UNSUPPORTED_RESPONSE_PARSER_CORRELATION coverage only (no request/Flow identity)
  -> no production RESPONSE_PARSED event producer is currently wired
  -> RoiRuntimeInvocationRecorder bounded, payload-free snapshots
  -> RoiDexKitDiscovery
       -> DexKit semantic string anchors
       -> DEX descriptor and invoke/caller relations
       -> runtime evidence exact descriptor + adapter + ClassLoader scope join
       -> RoiHookResolver.Candidate(... unresolved, adapterApproved=false)
       -> Policy.discoveryOnly()
```

The discovery report explicitly records `runtimeEvidence.resolverContribution=none_read_only_diagnostic_only`. The recorder is not currently an activation evidence provider.

## 3. Why production authorization is still blocked

| Required family | Current evidence | Activation status |
|---|---|---|
| Host identity | Adapter generation/channel label; not a cryptographic APK/DEX fingerprint | Not sufficient alone |
| DEX fingerprint | Parsed DEX method/index evidence is available for selected methods | Diagnostic only; not an approved adapter fingerprint |
| Semantic marker | DexKit string anchors and serializer-field anchors | Candidate signal only |
| Method shape | Exact parsed method descriptor and parameter/return shape | Partial corroboration |
| Call graph | Invoke/caller relations; incomplete decode is explicitly surfaced | Not yet reviewed as a semantic contract |
| Runtime invocation | Exact descriptor and ClassLoader scope; request/transport events; collector/parser capability coverage only | Original collector identity is always preserved; no production RESPONSE_RECEIVED or RESPONSE_PARSED event is emitted; no complete response chain |
| Return contract | No complete, reviewed per-adapter return contract supplied by the production evidence path | Missing |
| Lifecycle contract | No complete, reviewed cancellation/error/continuation/ownership contract supplied | Missing |
| Adapter approval | Discovery candidates deliberately set `adapterApproved=false` | Missing |
| Activation policy | Discovery uses `Policy.discoveryOnly()` | Denied |

Important constraints:

- Runtime observation is opt-in via the existing marker file and only enabled for maintained host adapters.
- The observer's `hostFingerprint` field currently carries the adapter identity label from `HostCompat`, not an APK digest. Diagnostics already label it accordingly. Do not treat the name of this field as proof of cryptographic host identity.
- Kotlin suspend `FlowCollector.emit` is detected at the actual `b41.b(q03, Continuation)` collect boundary. The observer preserves the original collector and records `UNSUPPORTED_SUSPEND_COLLECTOR`; it does not infer response delivery from `COROUTINE_SUSPENDED`.
- No collector proxy is installed: both suspend and non-suspend collector shapes preserve the original collector identity. This avoids changing equality, operator fusion, buffering, or coroutine ownership before device-level validation. Consequently, no production `RESPONSE_RECEIVED` event is emitted by this observer yet.
- The existing `vv7.e(Object)` callback is the server-response deserialization boundary: after its original method returns a non-null rebuilt `mv`, the observer records `UNSUPPORTED_RESPONSE_PARSER_CORRELATION` coverage. It deliberately does not emit `RESPONSE_PARSED`, because this callback exposes no proven request/Flow identity and cannot be safely joined to a specific transport chain.
- Non-interface, null, or mismatched collector types record `UNSUPPORTED_COLLECTOR_TYPE` against the actual host collect method, then return the original collector unchanged. These coverage entries describe capability gaps only, not response evidence. `RoiDexKitDiscovery` reports the suspend, non-suspend, collector-shape, and parser-correlation statuses under exact ClassLoader-scoped coverage counters so the diagnostics cannot silently disappear from the discovery report.
- The resolver requires confirmed, high-confidence `HOST_IDENTITY`, `METHOD_SHAPE`, `CALL_GRAPH`, `RUNTIME_INVOCATION`, `RETURN_CONTRACT`, and `LIFECYCLE_CONTRACT` evidence, plus a reviewed adapter approval and explicit allowed role.
- The activation gate additionally checks that the candidate ID is exactly derived from its source scope, owner, method and full descriptor. A forged or stale candidate key is denied.
- A JVM pass validates deterministic policy and lifecycle logic. It does not validate the host's runtime behavior.

## 4. Existing feature-hook migration inventory

All entries below are currently installed through the legacy feature pipeline. **None is automatically converted to Universal HookSpec by this PR.** Migration requires a separate reviewed adapter record with exact host/version evidence and an integration test.

### Chat and request/response

- `chat.request-construction`
- `chat.safety-retraction`
- `chat.server-capture`
- `chat.content-filter-apply`
- `chat.message-rebuild-capture`
- `chat.status-write`
- `chat.template-probe`
- `chat.final-message-merge`
- `chat.final-message-apply`
- `chat.s11-render-filter`
- `chat.cs1-render-filter`

### Agent

- `agent.tool-log.rounded-rect`
- `agent.visible-thread-filter`
- `agent.native-heartbeat-completion`
- Optional group: `optional.agent.render-pipeline`

### Attachment and media

- `attachment.native-image-preview-boundary`
- `attachment.expert-unlock`
- `attachment.local-editor-image-uris`
- Optional groups: `attachment.expert-history-image-preserver`, `attachment.expert-upload-gate`, `attachment.f9-bridge`, `attachment.expert-image-fingerprint`, `attachment.image-credential-bridge`

### Session and navigation

- `host.navigation.bridge`
- `host.v236.live-session-publish`
- `host.v241.live-session-publish`
- `host.v236.feedback-delete-flow`
- `host.v241.feedback-delete-flow`
- `session.local-directory-merge`
- `session.native-refresh`
- `session.remote-reload`
- `session.native-detail-request`
- `session.local-delete-flow`
- `session.local-delete-response`
- `session.active-chat-capture`
- `session.compose-visible-thread`
- `session.native-navigator`
- `session.history-load-diagnostics`
- `session.real-probe.schedule`
- Optional groups: `session.sidebar-multi-select-delete`, `session.sidebar-toggle-cleanup`

### UI and configuration

- `ui.native-dual-chat-root`
- `ui.home-greeting`
- `ui.host-theme-color`
- `ui.settings-navigation`
- `ui.native-settings-entry`
- `ui.settings-composable`
- `config.attachment-guide-rollout`
- `remote.feature-flags`
- `config.home-welcome-rollout`
- `config.hot-update-dialog`
- `config.remote-hot-updates`
- Optional groups: `optional.ui.welcome-whale-motion`, `optional.ui.text-wave-motion`, `optional.ui.appearance-effects`

### Account and security

- `account.native-fake-mute`
- `account.training-opt-out`
- `account.candidate-login-errors`
- `account.fa-compat-hook`
- Conditional generation-specific groups: `account.v236.local-api-routing`, `account.v241.local-api-routing`
- Optional Local API group: `optional.local-api-credential-routing`
- Other optional groups: `account.risk-sdk-neutralizer`, `account.login-risk-logger`

### Host-specific and network diagnostics

- `host.v271.settings-entry`
- `host.v236.local-chat-quota`
- `host.v241.local-chat-quota`
- `host.v236.thinking-code-copy`
- `host.v241.thinking-code-copy`
- `host.v236.all-file-types`
- `host.v241.all-file-types`
- `network.payload-capture`
- `chat.pow-manager-capture`

### Bootstrap and persistence operations

- `bootstrap.restore-local-editor-images`
- `bootstrap.obsolete-trigger-cleanup`
- `bootstrap.restore-local-sessions`
- `bootstrap.auto-backup-schedule`
- `bootstrap.history-migration-v3`

### ROI observation infrastructure (not user-facing feature adapters)

- `roi.class-load-observer`
- `roi.unknown-host.class-load-observer`

The ROI observation infrastructure is diagnostic and must not be counted as a migrated feature hook.

## 5. Migration order and acceptance gates

Migration is staged by risk, not by the number of hooks:

1. **Evidence plumbing first** — make discovery candidate snapshots and runtime evidence joins explicit, bounded, inspectable and source-scoped. Preserve diagnostic-only policy.
2. **Choose one low-risk adapter** — only after a real host build and full invocation lifecycle have been inspected. No candidate is preselected by this document.
3. **Shadow verification** — compare the legacy callback's observed target and behavior with the Universal HookSpec target without enabling a second native callback.
4. **Opt-in migration** — migrate one feature group with an explicit version/fingerprint allowlist and complete rollback.
5. **Regression matrix** — verify request/response behavior, arguments, return values, thrown exceptions, coroutine cancellation, duplicate install, rollback, uninstall/reinstall and coexistence with unrelated callbacks.
6. **Expand only after evidence** — repeat for each feature family and each supported host generation. Unknown versions remain fail closed.

Do not migrate Local API account/credential routing in this ROI PR. It is intentionally isolated from the Local API implementation and CI.

## 6. Immediate engineering priorities

- Keep the exact candidate identity and ClassLoader checks in the activation gate.
- Improve the runtime evidence path for Kotlin suspend/Flow without wrapping or replacing host return objects unsafely. Until that is proven, mark response coverage unsupported and deny authorization that depends on it.
- Build a version-scoped reviewed evidence catalog only from inspected APK/DEX plus verified device traces. Empty catalog is a valid safe state.
- Add one production adapter only when all required evidence families and its explicit policy are present.
- Keep the PR Draft. Do not merge to `dev` or `main` without an explicit request.
- Run the final complete CI only after the source integration and migration work is finished. Automatic branch-triggered workflow runs may still occur; do not use them as a reason to prematurely close the engineering phase.

## 7. Verification commands

- `bash module/test-roi-universal-hook-runtime.sh`
- `bash module/test-hook-install-registry.sh`
- `bash module-legacy/test-adapter-regression.sh`

These commands are the intended local/JVM regression suite. They do not replace Android/Xposed device acceptance.


## Installed host artifact identity (2026-11-07 follow-up)

- `RoiHostArtifactFingerprint` computes a content-based SHA-256 for the base APK and each declared split APK. The aggregate digest binds the base digest and every split name, digest, and byte length; split input ordering is normalized.
- Hashing runs inside the existing background DexKit discovery worker, not synchronously in `handleLoadPackage`. The digest is bounded to 16 APK files, 512 MiB per file, and 1 GiB total. Missing, unreadable, duplicate, changed-during-read, or over-limit artifacts produce an unavailable status and never a partial identity.
- The discovery report exposes only digest metadata and failure reason; it does not expose absolute APK paths.
- This is an evidence/diagnostic primitive only. It is **not yet wired into `RoiReviewedHookEvidenceRegistry` or the activation gate**, and it does not grant authorization. The reviewed registry remains empty until independently reviewed, exact APK-set digests and host symbol evidence exist for each supported channel/version.
- The SHA-256 value identifies bytes, not publisher authenticity. APK signing-certificate verification and PackageManager version metadata remain separate evidence requirements before production authorization.


## Reviewed host-build identity binding (2026-11-07 follow-up)

- Added immutable `RoiHostBuildIdentity`. A complete identity requires the exact target package, positive versionCode, versionName, channel, aggregate base+split APK SHA-256, signing-certificate SHA-256, and exact ClassLoader scope.
- `RoiReviewedHookEvidenceRegistry.Builder.addReviewed` now requires a complete host identity and rejects a ClassLoader scope that differs from the candidate evidence scope. The identity is stored with the exact HookSpec record.
- `RoiHookResolverActivationGate` now requires both the reviewed identity and a separate current-runtime identity source. Activation is denied unless every identity field matches and the current identity scope matches the actual ClassLoader passed to the gate.
- `Main.installRoiUniversalHookBatch` now requires the caller to supply the current runtime identity. This is an intentional fail-closed API change: a missing or stale identity cannot authorize a Hook.
- Added `RoiAndroidHostIdentity.capture(Context, ClassLoader, reviewedChannel)`: it reads the exact target package and version metadata from PackageManager, obtains current APK content signers (or legacy signatures on pre-28 Android), hashes the signer set deterministically, and hashes the installed base+split APK set. It refuses to run on the Android main thread and returns null on any incomplete or inaccessible identity.
- `Main.captureRoiCurrentHostIdentity(...)` exposes the capture operation. `Main.onActivityResumedInternal` now schedules it on a dedicated daemon worker once an Activity context is available; the selected channel comes from the maintained `HostCompat.isInternationalChannel()` distribution selector.
- The captured identity is stored as the process-current host identity and is read by the activation gate at install time. A missing/failed capture leaves the current identity null and therefore denies activation. The installer no longer accepts an arbitrary caller-supplied identity argument.
- **Remaining production evidence gap:** there are still no reviewed production records in the registry, and the Universal Hook batch has no production feature migration call site. The lifecycle capture proves installed package/build identity, not that any candidate Hook symbol is correct. Do not synthesize adapter records from discovery output.
- SHA-256 of the APK set is content identity; SHA-256 of the APK signing certificate is publisher identity. Both are independently required. A version/channel string or ClassLoader scope cannot substitute for either digest.


## Channel classification boundary clarification

- The current lifecycle caller obtains `mainland` / `google-play` from `HostCompat.isInternationalChannel()`. This is a source-controlled adapter classification based on maintained host markers, not a separately signed distribution manifest.
- The classification is only one field in the reviewed build tuple. It cannot authorize a Hook: activation still requires exact PackageManager version metadata, base/split APK content digest, signer-certificate digest, candidate descriptor, and ClassLoader scope to match a source-reviewed catalog record.
- Do not describe this selector as cryptographic channel proof. If a future adapter requires stronger store-channel provenance, add a separately verified installer/store evidence source rather than trusting this label.


## Version-code namespace distinction

- `HostCompat.hostCode271()` values (287 / 289 / 290) are internal adapter/mapping identifiers. They are **not** the Android package's `PackageInfo.versionCode`.
- The identity capture always records the real `PackageInfo.getLongVersionCode()` value and the actual base-manifest `versionName`. Do not infer one from the other or copy the adapter code into the identity tuple.
- Host identity JVM fixtures now use synthetic version codes (42/43) so tests do not accidentally present an adapter identifier as a real APK version code. Actual release values must come from PackageManager on the installed target.


## Prior static APK report scope guard (2026-11-07)

- The available DeepSeek reverse-engineering report for `DeepSeek_2.7.1.apks` identifies that artifact as package `com.deepseek.chat`, versionName `2.7.1`, Android versionCode `288`, Google Play split set.
- That report is useful for protocol research (request DTOs, Flow/SSE consumers and response models), but it is **not** an APK identity record for the maintained `HostCompat` adapter identifiers 287 / 289 / 290.
- Keep the namespaces distinct: the `HostCompat` values are internal mapping IDs; Android versionCode comes from PackageManager. Never relabel the 2.7.1/code288 report as code287, code289 or code290.
- The 2.7.1 report itself explicitly leaves the main completion raw-byte parser, runtime Flow emit item type, cancellation/resume behavior and LSPosed device validation unresolved. It cannot satisfy the runtime invocation, return-contract or lifecycle-contract evidence required by the activation resolver.
- Until exact installed artifacts and device traces exist for each supported 287 / 289 / 290 host, no production adapter record is authorized. Keep the catalog empty and preserve discovery-only policy.


## R10 implementation — cryptographic adapter/build binding (2026-11-07)

- Added `RoiHostBuildIdentity.activationFingerprint()`: deterministic SHA-256 over a domain-separated, length-prefixed tuple of package, real Android versionCode/versionName, channel, complete base+split APK-set digest and signer-set digest.
- The fingerprint excludes process-local ClassLoader scope by design; the gate independently requires the exact scope on both reviewed and current identity.
- Activation now requires `HookSpec.fingerprint == currentIdentity.activationFingerprint()`. Catalog admission independently requires both the spec and candidate fingerprint to equal the reviewed host identity's derived fingerprint.
- Added negative tests for arbitrary labels such as `code289-cn`, plus digest, signer and Android version-code changes.
- The discovery pipeline's existing `hostKey` remains a diagnostic adapter label and its candidates remain unresolved / unapproved / discovery-only. It is intentionally not upgraded into a production fingerprint.
- No production HookSpec records were added. Exact installed APKs, reviewed DEX descriptors, full runtime invocation/return/lifecycle evidence and L5 LSPosed verification remain prerequisites.

## R11–R12 migration disposition

- R11 migration plumbing: the shared `FeatureHookInstallRegistry`, Main group installer and LegacyXposed per-Member callback chain are already the integration seam. Migration must use this seam; it must not create a second native Xposed callback.
- R12 bulk migration: **not authorized in this batch**. The current evidence ledger has no feature with a complete, version-scoped activation record. Mechanically wrapping legacy call sites would only relabel unverified targets and could change invocation order, argument/return/throwable semantics, coroutine ownership or rollback behavior.
- The migration inventory remains a plan, not a completed migration count. A feature may move only after its exact target and complete behavior contract are reviewed for the exact APK identity, then pass shadow comparison, install/uninstall/rollback tests and the host/device matrix.
- This is an intentional fail-closed stop, not a claim that R11–R12 production migration is complete.


## R11 lifecycle correction — rollback-unhook failure (2026-11-07)

- Audit found that the batch rollback path removed a handle from the active map before calling `unhook()`; if `unhook()` threw, the runtime retained only a FAILED state and lost the handle reference. That could make `activeCount()` report zero and allow the outer group registry to claim uninstall completion while a native callback might remain.
- Corrected behavior: remove the handle from active tracking only after successful unhook. On failure, preserve the handle and mark it FAILED; `activeCount()` remains nonzero.
- An installer with an unresolved FAILED handle now rejects another install for that ID until explicit uninstall successfully retries cleanup. This avoids treating an uncertain callback as safely active or silently duplicating it.
- Added regression coverage for injected rollback-unhook failure, retained-handle visibility, duplicate-install rejection, explicit cleanup retry, subsequent clean reinstall, and group-level `uninstallAll()` failure/retry.
- This is runtime lifecycle hardening only. It does not add production HookSpec records or authorize feature migration.


## R11 dependency health propagation (2026-11-07)

- Tightened external dependency validation: a dependency already installed outside the current batch must exist in the active-handle map **and** have lifecycle state `ACTIVE`.
- A retained handle in `FAILED` after an unhook failure no longer satisfies another HookSpec's dependency. This prevents dependent feature Hooks from activating on top of uncertain callback state.
- Added a regression assertion that attempts to install a dependent Hook while its dependency handle is retained in `FAILED`; the backend must not be called.


## R11 dependency-safe teardown and target collision guard (2026-11-07)

- Individual uninstall now refuses to remove a prerequisite while any active direct dependent remains. Callers must uninstall dependents first; `uninstallAll()` traverses reverse installation order.
- Batch target resolution maintains a set of resolved reflective `Member` values and rejects duplicate targets before any backend install call. It also checks existing active registrations during resolution, avoiding partial install followed by a late conflict.
- Tests cover both guards and assert that backend registration counts do not increase on duplicate-target rejection.


## R11 partial native registration quarantine (2026-11-07)

- Found an adapter-level partial-success edge: HookTarget.intercept() stages a Hooker before XposedBridge.hookMethod(); some Xposed registries can publish their callback entry and then throw during native installation. In this path the universal backend returns no Handle, so the runtime cannot run its normal handle rollback.
- Changed the adapter's per-hook activation token to start disabled and become enabled only after HookTarget.intercept() returns normally. If native registration throws after publication, the staged Hooker remains inert/pass-through; HookTarget.registrationFailure prevents unsafe retry for that Member.
- Added a Legacy Xposed regression fixture that injects failure after callback publication, asserts the universal callback is never invoked, confirms original invocation remains exactly once, and verifies retry does not duplicate or activate the callback.
- This quarantines an uncertain callback; it does not claim the native callback was physically removed.


## Resolver array descriptor correctness (2026-11-07)

- Replaced the partial primitive-array special cases with recursive component resolution, covering byte/char/short/float/double arrays, object arrays, and multidimensional arrays.
- Return-type matching now uses a recursive formatter that preserves nested-class JVM binary names, which aligns Java source-style HookSpec types with reflected array types instead of comparing int[] against JVM binary name [I.
- Added an exact-resolution regression fixture combining byte[], char[], int[][], String[][] parameters and int[] return.

- Compatibility follow-up: use Class.getCanonicalName() rather than Class.getTypeName() to avoid raising the Android runtime API floor for array return types.


## ClassLoader identity binding (2026-11-07)

- The runtime previously compared HookSpec fields for idempotency but did not compare the ClassLoader object supplied on a subsequent install. A second loader could therefore be reported as already installed even though the existing Member belonged to another loader scope.
- Active registrations now retain the exact install ClassLoader reference and reject same-ID/same-spec replay through any different loader object. Successful uninstall removes the reference with the active handle.
- Added regression coverage using a delegating child ClassLoader to prove that a different loader object cannot silently reuse the first loader's registration.

- Follow-up: array return-type formatting now recursively appends [] to Class.getName() for each component, preserving '$' in nested-class names so DEX-derived HookSpec types remain exact.


## Dependency graph preflight tests (2026-11-07)

- Added explicit coverage for a missing dependency and a two-node dependency cycle.
- Both cases must fail before the backend receives any install call, preventing partial registration for an invalid graph.


## Scoped logical callback removal and reinstall (2026-11-07)

- Found that successful logical unhook previously only flipped an enabled flag. HookTarget retained the Hooker and its scoped-registration key for process lifetime. Since Main reuses the same stable scope ID on reinstall, the next HookTarget.intercept() could de-duplicate the new Hooker against the stale key and report success without actually re-adding it.
- Added identity-tracked scoped registration keys and a HookTarget removal operation. The universal handle disables its callback, removes that exact Hooker from the CopyOnWriteArrayList, and releases its key. The shared native Xposed callback remains registered.
- Universal backend now requires an affirmative interceptAndReport() result; a duplicate scoped registration is rejected rather than reported as a successful install.
- Added adapter regression coverage for uninstall then reinstall using the same scope/group ID, verifying callback reactivation and exactly one native callback.


## R11 final integration audit (2026-11-07)

- Confirmed Main wraps ROI batch installation in a stable HookRegistrationScope keyed by the registry/group ID. Therefore successful uninstall must release that exact scoped-registration key for a same-ID reinstall; the adapter now does so.
- Confirmed the shared HookTarget native callback remains registered once per Member, while universal logical Hookers are individually removed and can be re-added.
- Fatal Error paths preserve the active handle before propagating. Normal exception paths preserve the handle and report failed cleanup without claiming success.
- Added a dedicated FeatureHookInstallRegistry transition for a FAILED group after Main has explicitly retried cleanup and verified runtime activeCount()==0.


## R11 final delivery and CI boundary (2026-11-07)

- Main's stable group scope and HookTarget's scoped-registration de-duplication were traced end-to-end. Universal unhook now removes the exact Hooker and releases only its own scope key; same-ID reinstall therefore re-registers the logical callback while reusing the single shared native Member callback.
- Legacy hook registrations do not populate the universal removable-registration identity map, preserving their established scope retry behavior without additional long-lived references.
- `Main.uninstallRoiUniversalHookBatch` can complete cleanup for either an INSTALLED group or a FAILED install after all runtime handles have been removed. The new registry transition is distinct and callable only after Main verifies activeCount()==0.
- Fatal errors during rollback/uninstall propagate only after the uncertain handle is retained in runtime state. Normal backend registration errors remain a failed install result, with any partially published Xposed callback quarantined disabled.
- Per the requested execution order, full CI and Android/LSPosed device validation are postponed until the final consolidated source commit.

### Lifecycle transition observability follow-up (2026-11-08)

- `HookLifecycleManager.uninstall(id)` now returns explicit transient details when the target is `INSTALLING` or `UNINSTALLING` (`install_in_progress` / `uninstall_in_progress`). The in-flight entry is not mutated by the rejected uninstall request.
- Regression test source added for uninstall-during-install and reverse-direction conflict declarations.
- This is source/test-code work only. The lifecycle test suite, CI, APK build, emulator, and physical-device validation remain intentionally deferred to the final validation stage.


## Lifecycle retryability correction (2026-11-08)

- Static audit found that `HookLifecycleManager.install()` treated every `SKIPPED` entry as terminal. A target skipped because a dependency was not yet active or because a conflicting target still occupied its conflict domain could never be retried after that condition cleared.
- Corrected the existing-entry guard: only `disabled_by_policy` remains terminal for the same immutable spec. Dependency/conflict skips re-enter preflight on a later install call.
- Added regression tests for retry after dependency activation and after conflicting-target uninstall.
- Source commit: `13b9d31e0d2fbd4bc0b7e368fa5d9d65ca42b585`; test commit: `acc8f3d52446f31a5bf350f249a3f1db9c10d624`.
- PR #14 head has no CI run yet. The current ROI workflow is not configured to trigger on `work/generic-hook-lifecycle-dev`; no test, build, emulator, or device validation is claimed.


## Lifecycle retryability audit correction (2026-11-08)

- Correction to the immediately preceding retryability note: control-flow inspection showed dependency-not-active and conflict-blocked returns occur before `Entry` creation. Those transient `SKIPPED` statuses are therefore not persisted and are naturally re-evaluated on the next install call.
- The proposed production guard change was reverted; no production behavior change remains from that hypothesis.
- The two retry-after-condition-clears tests remain as regression coverage. Their execution is still pending.
- Revert commit: `109b8bc52d5c8714e75ff4b5f8d6519995124341`. The prior ledger paragraph claiming a confirmed lifecycle liveness defect is superseded by this correction.


## Dedicated ROI CI coverage update (2026-11-08)

- Corrected the dedicated ROI workflow branch triggers to the active ROI development branches and stacked PR base branches; the previous push trigger referenced a retired branch.
- Added path coverage and focused runner steps for `HookLifecycleManager`, `RoiSemanticProfileMatcher`, and `RoiDexSerializerPipeline` / mapper regression suites.
- Added `module/test-roi-serializer-pipeline.sh` on the serializer branch to compile and run both pipeline and mapper test mains.
- Test steps are guarded by `hashFiles` / file-presence checks so a base-stage PR does not fail merely because tests introduced by a later stacked PR are absent from its merge checkout.
- First dedicated run #189 failed at shell syntax because PR #18 did not yet contain the semantic runner from PR #15. The workflow guard correction was committed as `3989ac5e895e98d057c6d100776f766ac0b55676`; follow-up CI result is pending/unobserved.
- The general YunSeek CI remains separate and may still be automatically triggered by its existing `work/**` branch rule; the dedicated ROI workflow does not invoke Local API tests.


## Semantic profile descriptor validation (2026-11-08)

- Audit found `methodShape()` preserved malformed method strings unchanged when a complete argument/return descriptor could not be parsed. Two malformed but identical strings could pass role-to-method equality and participate in a high-confidence semantic match.
- `ClassProfile.valid()` now rejects incomplete method descriptors and role prototypes; descriptor parsing validates object/primitive/array parameter types and return types before normalization.
- Added a malformed-return-descriptor regression fixture. The matcher remains discovery-only and never authorizes Hook activation.
- PR #15 source commit: `571e4aa0766432f1b673ac25438d4cc5a0c9d005`; test commit: `0ff8bbe6406d27c99921f6fa91e6320a67f77302`. CI/test result is pending.

## Focused ROI CI verification and semantic fixture correction (2026-11-08)

- Serializer PR #11 dedicated run #201 completed successfully. The serializer pipeline and mapper test step passed, as did the existing ROI runtime and legacy adapter regression steps.
- Lifecycle PR #14 dedicated run #200 completed successfully. The job log confirms module-kotlin-runtime:test completed with BUILD SUCCESSFUL; runtime recorder, feature install registry, universal Hook runtime, and legacy Xposed API 82-102 regression matrix also passed.
- Semantic PR #15 run #197 exposed a test-fixture contract mismatch, not a production matcher failure: testAmbiguousMethodRoleFailsClosed() supplied two methods with the same normalized prototype for one declared role. ClassProfile.valid() correctly rejects this profile before ranking, while the test incorrectly indexed the empty candidate list.
- Updated the ambiguous-role test to assert that duplicate normalized prototypes invalidate the profile and produce no candidates. This preserves the strict production invariant rather than weakening validation.
- Semantic test correction commit: 99c460b1c9c5cebf585676b13c60442586275861. Dedicated rerun #202 and duplicate push-triggered run #203 are pending/in progress; no passing result is claimed yet.
- YunSeek CI runs #2060-#2062 were independently triggered by the repository's existing work/** rule. They are not ROI-specific test evidence and may include Local API checks.
## Semantic fixture follow-up (2026-11-08)

- The first semantic test correction exposed a second stale fixture in testDetachedMethodRoleEvidenceIsNotMatchable(): its method name differed, but its normalized prototype intentionally matched because semantic matching ignores obfuscated method names.
- Corrected that fixture to use a genuinely different parameter descriptor (Integer instead of Long), so it now tests detached structural evidence rather than a harmless method rename.
- Follow-up test commit: 6ee8d72c1aaaad95e38506f7e74b9e9ea0490e4b. Dedicated rerun #208 is queued; semantic suite remains unverified until that run completes.
## Semantic target-role fixture correction (2026-11-08)

- Latest semantic rerun #208/#209 confirmed the prior two fixture corrections were effective, then exposed one more stale fixture in testTargetRoleDeclarationMustAgreeWithResolvedMethod(): its alternate `route(...)` method duplicated the `dispatch(...)` normalized prototype, so the profile was invalid before role-agreement evaluation.
- Corrected the alternate role method to use a distinct `Integer` parameter prototype while preserving the test's intended contradiction against the source `Long` prototype. Production validation remains unchanged.
- Test-only commit: 0af4240fef19c89e646fe8e3727359cc7542cbe0. New ROI run is pending; no semantic pass claimed.
- Latest ROI serializer branch run #213 passed all enabled steps, including serializer pipeline/mapper, runtime recorder, feature install registry, isolated universal Hook tests, and legacy adapter regression matrix. This does not imply the semantic suite passed.
## Semantic structural-evidence validation hardening (2026-11-08)

- Static audit found malformed class/interface/field/external-call descriptors could enter profile scoring. In particular, normalizedExternalCalls() discards malformed entries; if both sides normalize to empty sets, Jaccard similarity returns 1.0 and can award full evidence credit to invalid call data.
- ClassProfile.valid() now validates class and superclass descriptors, interface descriptors, field evidence, and owner-qualified external-call method descriptors before scoring. Added tests for malformed class, interface, field, and external-call evidence.
- Production commit: e9d9f9e504093f194def058662c4ee91bcd11658; test commit: f0482b0e334481c8362f96eea1cfae6066c9590b. Latest semantic CI pending.
## Target-role assertion correction (2026-11-08)

- Run #214 revealed the role-contradiction fixture still assigned the source-equivalent Long prototype in the target role map. Because method names are intentionally ignored, changing only the alternate method's name/prototype list did not create a contradiction.
- Corrected the target role map to declare the Integer prototype, while retaining both distinct method prototypes so the target profile remains valid. This now tests a genuine target-role disagreement.
- Test-only commit: 3b9b970cf9346b75061caf83c4f5da4054f8c4bf. Semantic CI pending.
## External-call evidence provenance hardening (2026-11-08)

- normalizedExternalCalls() intentionally excludes obfuscated owners because they are not stable cross-version anchors. A profile containing only such calls could otherwise normalize to an empty set and receive misleading similarity credit.
- ClassProfile.valid() now rejects obfuscated owners in external-call evidence rather than silently accepting evidence that will later be discarded. Added regression coverage for an obfuscated owner with an otherwise valid method descriptor.
- Production commit: 9dfb3ac9f186912093d4676c0f3a180c4ae08941; test commit: 00e500332fd48fcb51f2cb1cc4a58cfa229d8c82. CI pending.
## Semantic matcher baseline verification (2026-11-08)

- ROI run #224 completed successfully on semantic branch commit 3b9b970cf9346b75061caf83c4f5da4054f8c4bf. The semantic profile matcher step passed together with lifecycle, runtime recorder, feature install registry, isolated universal Hook, and legacy adapter regression steps.
- This run predates the subsequent structural-evidence validation changes (e9d9f9e5 / f0482b0e / 9dfb3ac9 / 00e50033). Those latest changes are being verified separately in runs #230/#231; do not treat #224 as verification of those later commits.
## Semantic matcher CI confirmation and descriptor-name hardening (2026-11-08)

- The target-role contradiction fixture was corrected in commit 3b9b970cf9346b75061caf83c4f5da4054f8c4bf by changing the target role-map prototype itself to Integer (not merely renaming the method). ROI run #224 passed the semantic matcher and all other enabled regression suites.
- Structural evidence validation commits e9d9f9e5 / f0482b0e and external-call owner validation commits 9dfb3ac9 / 00e50033 were verified by ROI runs #230 and #231. Both runs passed lifecycle, semantic matcher, runtime recorder, feature install registry, isolated Universal Hook, and legacy adapter regression steps. Serializer step was correctly skipped because that stacked PR checkout did not contain the serializer branch files.
- Additional static audit found descriptor-name syntax was still permissive: malformed internal class names and whitespace-containing method names could pass the shape parser. Added `isInternalClassName()` and `isMethodName()` checks and tests. A follow-up aligned the checks with JVM/DEX unqualified-name delimiter rules instead of rejecting whitespace categorically; the regression fixture now uses a forbidden slash in a method name.
- Descriptor-name production commit: 77532bdb5798445f48ca6b471191c8ecec70a477; tests: 5bd1da1f1b5303308a2e44e1c94445c6853298ee. ROI runs #236/#237/#238 are pending/in progress; no pass is claimed for this latest hardening yet.
- Follow-up commits: `a32c2abcd0050cfabfaecbb00e3411803d48c609` (production rule precision) and `a326dcce1ee386d307ad3deb0d7751f3aaec5769` (test fixture). Latest runs #244/#245/#246 are pending/queued; no pass claimed for these final commits.

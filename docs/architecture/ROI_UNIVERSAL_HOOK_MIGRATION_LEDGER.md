# ROI Universal Hook — Production Evidence and Feature Migration Ledger

Last audited: 2026-11-07  
Branch: `work/roi-universal-hook-r1-r9`  
PR: #17 (Draft; base `dev`)

## 1. Executive status

The Universal Hook runtime and the Xposed shared-callback backend are implemented, but the project is **not yet ready to migrate the existing production feature hooks**. This ledger separates three things that must not be conflated:

1. **Discovery evidence** — static DEX/string/call-graph observations and loaded-class metadata. Useful for narrowing candidates; never grants Hook permission.
2. **Runtime invocation evidence** — payload-free observations emitted by explicitly trusted existing callbacks. Useful for corroborating exact method descriptors and call order; current response/Coroutine coverage is incomplete.
3. **Reviewed activation evidence** — a code-reviewed, version-scoped adapter record that explicitly approves the exact host fingerprint, semantic role, class, method, full descriptor, return/lifecycle contract and activation policy. No general production provider currently supplies this complete record.

The current DexKit candidate constructor intentionally sets role to `unresolved`, `adapterApproved=false`, and an empty approved-fingerprint set. It uses `Policy.discoveryOnly()`. This is correct fail-closed behavior and must not be relaxed to make the new runtime appear active.

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
       -> non-suspend emit: best-effort proxy observation -> RESPONSE_RECEIVED
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
| Runtime invocation | Exact descriptor and ClassLoader scope; request/transport events plus best-effort non-suspend collector observation | Actual Kotlin suspend emit is preserved but unsupported; no RESPONSE_PARSED producer exists; no complete response chain |
| Return contract | No complete, reviewed per-adapter return contract supplied by the production evidence path | Missing |
| Lifecycle contract | No complete, reviewed cancellation/error/continuation/ownership contract supplied | Missing |
| Adapter approval | Discovery candidates deliberately set `adapterApproved=false` | Missing |
| Activation policy | Discovery uses `Policy.discoveryOnly()` | Denied |

Important constraints:

- Runtime observation is opt-in via the existing marker file and only enabled for maintained host adapters.
- The observer's `hostFingerprint` field currently carries the adapter identity label from `HostCompat`, not an APK digest. Diagnostics already label it accordingly. Do not treat the name of this field as proof of cryptographic host identity.
- Kotlin suspend `FlowCollector.emit` is detected at the actual `b41.b(q03, Continuation)` collect boundary. The observer preserves the original collector and records `UNSUPPORTED_SUSPEND_COLLECTOR`; it does not infer response delivery from `COROUTINE_SUSPENDED`.
- `RESPONSE_RECEIVED` can only be emitted by the non-suspend proxy branch. That branch changes collector identity and therefore remains diagnostic-only until interface/identity behavior is verified on a real host build.
- The existing `vv7.e(Object)` callback is the server-response deserialization boundary: after its original method returns a non-null rebuilt `mv`, the observer records `UNSUPPORTED_RESPONSE_PARSER_CORRELATION` coverage. It deliberately does not emit `RESPONSE_PARSED`, because this callback exposes no proven request/Flow identity and cannot be safely joined to a specific transport chain.
- Non-interface, null, or mismatched collector types record `UNSUPPORTED_COLLECTOR_TYPE` against the actual host collect method, then return the original collector unchanged. These coverage entries describe capability gaps only, not response evidence. `RoiDexKitDiscovery` reports both new statuses under exact ClassLoader-scoped coverage counters so the diagnostics cannot silently disappear from the discovery report.
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

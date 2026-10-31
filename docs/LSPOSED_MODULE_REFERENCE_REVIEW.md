# LSPosed Module Reference Review

**Review date:** 2026-10-31  
**Purpose:** compare current public LSPosed/Xposed module implementations for reliable entry bootstrap, package/process filtering, hook installation isolation, activation evidence and diagnostics.  
**Scope:** 20 public module repositories. Nineteen expose source files relevant to their module runtime; the YadeaHook repository mirror exposes a README but no source tree in the reviewed public repository, so it is treated as a feature/installation reference only.

## Executive findings

The strongest common design is not a single “activated” boolean. Mature modules keep separate evidence for:

1. module entry loaded into a process;
2. target package/process selected;
3. application context/class loader ready;
4. each hook successfully registered;
5. each hook actually invoked;
6. last successful report and its session/boot freshness;
7. per-feature failure or compatibility skip.

A framework service connection is not proof that the target process was injected. A target-process heartbeat is not proof that feature hooks registered. A hook registration count is not proof that those hooks have been invoked. These facts must remain separate in YunSeek's UI and telemetry.

The current YunSeek entry is the traditional `IXposedHookLoadPackage` API. Modern `libxposed XposedModule` callbacks below are architectural references only; do not paste their API classes into the legacy entry. Reuse the principles through the existing compatibility adapter.

## Repository review (20 modules)

| # | Module / source | Entry and implementation observations | Relevance to YunSeek |
|---:|---|---|---|
| 1 | [QuietBox](https://github.com/yigenhuobah/quietbox) | `QuietBoxModule` separates module load from target package readiness, resets its install guard after a failed batch, and reports per-feature registrations, hits, skips and critical misses through a UID-validated `StatusProvider`. | Strong reference for authenticated target telemetry, per-feature state and avoiding a permanently stuck install guard. |
| 2 | [ImeAutoSwitcher](https://github.com/Marukon/ImeAutoSwitcher) | `ImeHookEntry` separates module load, system-server start, package loaded and package ready callbacks. System-server hooks have independent initialization flags and structured logs. | Keep process/scope filtering explicit; module UI/self-checks are not proof of target injection. |
| 3 | [TikTokPatchXposed](https://github.com/hik0w/TiktokPatchXposed) | Large modular hook codebase with feature-specific classes such as `Hooks` and `FeatureStatus`; it separates feature behavior and feature state instead of putting every hook in one monolithic callback. | Useful structural reference for separating feature installation and feature diagnostics. |
| 4 | [PixelInjector](https://github.com/hxreborn/PixelInjector) | `App.kt` uses `XposedServiceHelper` with distinct current-service state and persisted `everBound`; `onServiceDied` clears the live service while preserving historical evidence. `Target.kt` centralizes process/package classification. | Direct example that current framework connection and historical “ever connected” must not be represented by one active boolean. This is a service-lifecycle reference, not a source for YunSeek's feature hook installers. |
| 5 | [MultiRoute](https://github.com/Linoleic/MultiRoute) | `MultiRouteModule` publishes a structured system-server beacon with PID, module version, compiled build ID, boot elapsed time, report time, per-hook counts and stage. `ModuleStateParser` / `ModuleStateTest` detect stale process state and version skew. | One of the strongest references for process/session/build-scoped activation evidence and unit-testable state parsing. |
| 6 | [WeTypeHookR](https://github.com/Hao-o0o/WeTypeHookR) | `HookRuntime` keeps host package, hook-installed state, framework metadata and heartbeat time separate; context-dependent work waits for application readiness. | Direct reference for separating injection from hook installation and waiting for a usable application context. |
| 7 | [Coogle](https://github.com/zopulus/coogle) | `CoogleModule` filters packages in the ready callback; `HookInstaller` tracks hook handles and supports mark/unhook rollback. The manifest declares the libxposed service provider explicitly. | Add rollback/handle tracking for future multi-hook transactions; keep provider authority aligned with the app ID. |
| 8 | [FusionStatusBar](https://github.com/yudigaga/FusionStatusBar) | `FusionActivationStatus` stores boot/session/revision information and per-feature states such as applied, waiting, degraded and failed. `BoundedHookRetry` schedules one retry at a time with a fixed attempt limit. | Best reference for stale-state prevention, per-feature result states and bounded retry. |
| 9 | [ClipBait](https://github.com/pgntgz/ClipBait) | Legacy `XposedEntry` explicitly separates its own package, `system_server` and client apps. Provider/receiver IPC is separate from hook setup. | Useful legacy package/process dispatch pattern; self-app status must not be treated as real target activation. |
| 10 | [VpnAutoSwitch](https://github.com/yaoiwi/VpnAutoSwitch) | Legacy `ModuleEntry` routes system-server work separately and exposes structured runtime diagnostics through `RuntimeDiagnostics`. | Shows how to expose runtime evidence from the process that actually owns the hooks. |
| 11 | [DouYinLiquidTab](https://github.com/xzdw01/DouYinLiquidTab) | Modern entry checks first-package readiness and delegates to `HookCoordinator` / `HookInstaller`; `ModuleActivation` and config transport are separate components. | Keep entry small and delegate hook setup to a coordinator; do not mix service connection with target readiness. |
| 12 | [TwitterHideAds](https://github.com/MrxSiN/TwitterHideAds) | `ModuleMain` filters package/process and `ModuleRuntime` delays context-dependent discovery until the application attach lifecycle is ready. | Direct precedent for deferring bootstrap until application context is available. |
| 13 | [MeowPower](https://github.com/zjkkzk/io.github.yuhj319.meowpower) | `ModuleMain` is a distinct module entry; package/process scope and remote configuration are separate from the launcher UI. | Explicitly account for target sub-processes and remote configuration boundaries. |
| 14 | [XGoogle](https://github.com/Leekin2/XGoogle) | Modular `HookInstaller` and feature classes separate target-specific hook groups; the manifest declares the libxposed provider authority and package visibility queries. | Useful for feature grouping, explicit service declaration and package-visibility requirements. |
| 15 | [Antiburn](https://github.com/Xposed-Modules-Repo/io.github.wumisc.antiburn) | Legacy `XposedInit` routes through a separate `Controller` and target-specific hook classes. | Simple legacy entry/controller separation and target-scoped implementation reference. |
| 16 | [HyperEars](https://github.com/silverpoetry/HyperEars) | Modern `HookEntry` filters first-package callbacks and selects hook objects by package/process; individual hook objects have isolated failure handling. | Good process routing and per-hook-object isolation pattern. |
| 17 | [AppTimeLimiter](https://github.com/TACPR/com.liuml.apptimelimiter) | Supports modern and legacy adapters. `HookStatusPresentationPolicy` distinguishes definite issues such as out-of-scope, stale and failed; `HookReloadTargetPolicy` explicitly says no heartbeat alone is not an error. Includes unit tests for these policies. | Very strong precedent for “no heartbeat ≠ disabled” and testable activation/status presentation. |
| 18 | [TikTools](https://github.com/sunisup666/TikTools) | Legacy `HookEntry` hooks `Application.attach`, then obtains context/class loader; it records per-group installed counts and errors. `SettingsProvider` validates caller UID and checks diagnostic-app signature. | Direct precedent for deferred application bootstrap, per-group hook telemetry and authenticated provider IPC. |
| 19 | [GboardGloss](https://github.com/Xposed-Modules-Repo/io.github.ziyuw_plank.gboardgloss) | Public LSPosed repository mirror is available, but the inspected mirror contains no Java/Kotlin source tree. | Included as a current module-repository reference only; not used to infer internal implementation. |
| 20 | [YadeaHook](https://github.com/Xposed-Modules-Repo/com.ghhccghk.yadeahook) | Public LSPosed repository mirror provides module metadata/README but no source tree in the inspected repository. | Included as a module-repository reference only; not used as code-level evidence. |

### Additional project-specific baseline (not counted among the 20 external modules)

[The public YunSeek/Deekseep module mirror](https://github.com/Xposed-Modules-Repo/com.dsmod.probe) contains the earlier legacy `Main`, `StatusProvider`, `XposedActivationProvider`, `LegacyXposedModule` and regression tests. It is useful for comparing the current `dev` branch against the project's own historical activation and compatibility design, but it is not counted as an independent external module.
 
## Patterns selected for YunSeek

### 1. Keep the legacy entry; defer only when prerequisites are missing

- Preserve `IXposedHookLoadPackage` and the current `LegacyXposedModule` adapter.
- Do not set the per-process duplicate-injection marker before the main Looper/bootstrap prerequisites exist.
- If the package callback arrives before the main Looper, hook `Application.attach(Context)`, let the original attach complete, then re-enter the same package bootstrap.
- Do not mark the PID as injected merely because the entry callback was invoked.

This follows the delayed-context pattern used by TwitterHideAds and TikTools, adapted to the current legacy API.

### 2. One failed feature must not abort all remaining hooks

- Wrap independent feature installers separately.
- Record feature name, registration count and failure reason.
- Continue installing unrelated feature groups after one optional hook fails.
- Keep an outer catch only for unexpected coordinator-level failures.
- Do not automatically retry a partially installed batch unless hook registration is idempotent or the already-installed handles can be rolled back.

This follows QuietBox, PixelInjector, FusionStatusBar, RestoreRoundedSliders, TikTools and HyperEars.

### 3. Activation state is a multi-evidence state machine

| Evidence | What it proves | What it does not prove |
|---|---|---|
| Framework service Binder alive | A framework service endpoint is currently reachable | Target process injected; hooks installed |
| Authenticated target heartbeat | The DeepSeek UID executed the reporter in the target process | Every feature hook registered |
| Hook registration count | The compatibility adapter registered hook members | Those members were invoked |
| Hook invocation count | At least one registered hook callback ran | Every feature works correctly |
| Last-seen timestamp | The target was observed in the past | Target process is alive now |

UI states should include `WAITING`, `FRAMEWORK_CONNECTED`, `TARGET_INJECTED`, `HOOKS_INSTALLING`, `HOOKS_ACTIVE`, `HOOKS_PARTIAL`, `HOOKS_FAILED` and `LAST_SEEN`. Missing Binder or heartbeat evidence is unknown/waiting, not proof that LSPosed disabled the module.

### 4. IPC security and freshness

- Continue validating the calling UID against `com.deepseek.chat` for target-to-module-provider calls.
- Keep hook-status reports behind the same caller-UID check.
- Use bounded report fields and bounded freshness windows.
- Use process heartbeats while the target process remains alive; stale reports must be labelled historical.
- Never accept a package name supplied in extras as the identity of the Binder caller.

This follows QuietBox's `StatusProvider` and FusionStatusBar's boot/session-aware report storage.

## YunSeek implementation status

The following changes are on `fix/activation-evidence-and-bootstrap`, based on `dev`:

- Deferred early bootstrap when `Looper.getMainLooper()` is null; install a one-shot `Application.attach` hook and only set the PID duplicate guard after prerequisites are present.
- Remove the erroneous `BuildInfo.PROTECTED_BUILD` gate from target heartbeat and hook-status reporting, so Open builds can report their own target-process state too.
- Add a 60-second target-process heartbeat while the target process is alive; the module app uses a five-minute freshness window and shows stale evidence as last-seen, not active.
- Separate framework connection, target injection and hook installation in `ActivationStatusPolicy`; apply the same evidence policy in both the Compose launcher card and legacy `SettingsActivity`.
- Authenticate hook-status reports by the actual target UID. The Android 14/15 explicit-broadcast fallback is only target-heartbeat evidence; it cannot promote the UI to `HOOKS_ACTIVE` without a fresh UID-validated hook-status report.
- Expose hook-member registration count, callback invocation count and registration/feature-install failures.
- Isolate the main feature installers so an exception in one feature does not stop the rest of the install list.

### Remaining validation

- Run Android Open debug assembly after the package migration branch is ready.
- Run the legacy APK build path separately.
- Verify manifest entry names and packaged `assets/xposed_init` in the produced APK.
- Install on a real device, enable the exact DeepSeek scope in LSPosed, force-stop/relaunch DeepSeek, and confirm the target heartbeat, hook registration counts and hook invocation counts.
- Exercise representative features (chat request, settings entry, session/history and attachment paths) on the supported DeepSeek builds.
- Review LSPosed logs and the module diagnostics when a hook stage is failed or partial.

No real-device injection result is claimed by this document.

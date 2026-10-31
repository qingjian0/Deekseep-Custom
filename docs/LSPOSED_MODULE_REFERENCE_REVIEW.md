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
| 1 | [QuietBox](https://github.com/yigenhuobah/quietbox) | `QuietBoxModule` separates `onModuleLoaded` from `onPackageReady`, filters the target package, resets its installed guard when a whole install throws, and reports per-feature registered labels, hits, skips and critical misses through a UID-validated `StatusProvider`. | Strong reference for authenticated target telemetry, per-feature state and avoiding a permanently stuck installed flag. |
| 2 | [ImeAutoSwitcher](https://github.com/Marukon/ImeAutoSwitcher) | `ImeHookEntry` separates module load, system-server start, package loaded and package ready callbacks. System-server hooks have independent initialization flags and structured logs. Its self-app hook is only a self-check, not proof of framework scope. | Keep process/scope filtering explicit; do not treat a self-check as external activation evidence. |
| 3 | [TikTokPatchXposed](https://github.com/hik0w/TiktokPatchXposed) | Modern `ModuleMain` delegates through a backend adapter while retaining a legacy `xposed_init` entry path. Package and process dispatch are separated from feature hooks. | Useful example of maintaining legacy/modern adapters without duplicating the business hook implementation. |
| 4 | [PixelInjector](https://github.com/hxreborn/PixelInjector) | `PixelInjectorModule` filters first-package callbacks, persists state for hot reload, isolates each tweak installation and logs the exact tweak that failed. | Feature-level isolation prevents one incompatible target from disabling every other feature. |
| 5 | [MultiRoute](https://github.com/Linoleic/MultiRoute) | System-server hook state records process ID, module version/build ID, boot elapsed time, hook counts and runtime stage. It distinguishes module load from actual installed hook counts. | Use process/build/session-scoped state rather than a timeless boolean. |
| 6 | [WeTypeHookR](https://github.com/Hao-o0o/WeTypeHookR) | `HookRuntime` keeps `hostPackage`, `hookInstalled`, framework metadata and `heartbeatAt` as separate fields. Context-dependent work waits for `withApplicationReady`. | Direct reference for separating injection from hook installation and waiting for application context. |
| 7 | [Coogle](https://github.com/zopulus/coogle) | `CoogleModule` filters packages in the ready callback; `HookInstaller` tracks hook handles, supports mark/unhook rollback and uses an explicit protective exception mode. | Add rollback/handle tracking for future multi-hook transactions; make exception behavior deliberate. |
| 8 | [FusionStatusBar](https://github.com/yudigaga/FusionStatusBar) | `FusionActivationStatus` stores boot/session/revision information and per-feature states such as applied, waiting, degraded and failed. `BoundedHookRetry` schedules one retry at a time with a fixed attempt limit. | Best reference for stale-state prevention, per-feature result states and bounded retry. |
| 9 | [ClipBait](https://github.com/pgntgz/ClipBait) | Legacy `IXposedHookLoadPackage` entry explicitly separates its own package, `system_server`, and client apps. Provider/receiver IPC is separated from hook setup. | Useful legacy package dispatch pattern; its self-app activation override must not be copied as real activation evidence. |
| 10 | [VpnAutoSwitch](https://github.com/yaoiwi/VpnAutoSwitch) | Legacy entry works only in `android` / system_server, reports per-hook counts, and publishes structured runtime diagnostics through `Settings.Secure`. | Shows how to expose system-side runtime evidence without pretending the manager app can infer it. |
| 11 | [DouYinLiquidTab](https://github.com/xzdw01/DouYinLiquidTab) | Modern entry records process name, checks `isFirstPackage`, and delegates to a separate HookInstaller/HookCoordinator. Activation/service and config transport are separate components. | Keep entry small and delegate hook setup to a coordinator; do not mix service connection with target readiness. |
| 12 | [TwitterHideAds](https://github.com/MrxSiN/TwitterHideAds) | `ModuleMain` filters package and process, installs an `Application.attach` guard, and delays class discovery until a real application context exists. | Direct precedent for deferring early bootstrap until Application.attach. |
| 13 | [MeowPower](https://github.com/zjkkzk/io.github.yuhj319.meowpower) | Modern entry covers explicit target sub-processes, uses remote preferences and reports hook registration/hit diagnostics. Individual compatibility targets are checked rather than assumed. | Process name and sub-process scope must be explicit; report actual hook activity. |
| 14 | [XGoogle](https://github.com/Leekin2/XGoogle) | Modular feature installer tracks hook handles, supports rollback and deoptimization, and declares its Xposed service provider explicitly. It is related architecturally to Coogle. | Useful for hook transaction management and exact provider authority matching. |
| 15 | [Antiburn](https://github.com/Xposed-Modules-Repo/io.github.wumisc.antiburn) | Legacy entry dispatches only known target packages, logs the process name and wraps each package's initialization in its own `Throwable` boundary. | Simple legacy fail-isolation pattern and deterministic target-process diagnostics. |
| 16 | [Gboard Gloss](https://github.com/ziyuw-Plank/gboard-gloss) | Legacy entry distinguishes self-app, system and target IME; it filters to the first application and logs class-resolution outcomes. Its exported provider documents caller validation. | Do not run target hooks in the module's own UI process; validate provider callers. |
| 17 | [RestoreRoundedSliders](https://github.com/cgiangreco/RestoreRoundedSliders) | Legacy SystemUI entry wraps independent hook groups in separate `try/catch` blocks, so a missing optional method does not stop later hooks. | Important reference for isolating each hook family instead of one giant installer try block. |
| 18 | [TikTools](https://github.com/sunisup666/TikTools) | Legacy entry hooks `Application.attach`, then obtains context/class loader, installs feature groups independently, and records per-group installed counts and errors. | Direct precedent for delayed application bootstrap plus feature-level install/error telemetry. |
| 19 | [HyperEars](https://github.com/silverpoetry/HyperEars) | Modern `onPackageLoaded` entry filters first package, selects hook objects by package/process, and catches failures independently for each hook object. | Good process routing and per-hook-object isolation pattern. |
| 20 | [YadeaHook](https://github.com/Xposed-Modules-Repo/com.ghhccghk.yadeahook) | Public module-repository mirror contains a feature/installation README but no source files in the inspected tree. The README requires enabling the module, selecting the target scope and restarting the target app. | Installation checklist only; not used as evidence for implementation internals. |

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
- Add a 60-second target-process heartbeat while the target process is alive; the module app uses a five-minute freshness window and shows stale evidence as last-seen, not active.
- Separate framework connection, target injection and hook installation in `ActivationStatusPolicy`; apply the same evidence policy in both the Compose launcher card and legacy `SettingsActivity`.
- Authenticate hook-status reports by the actual target UID.
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

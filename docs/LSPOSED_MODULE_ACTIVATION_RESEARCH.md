# LSPosed Module Entry and Activation-State Research

Research date: 2026-10-31

Scope: inspect 20 public Xposed/LSPosed module repositories and their source entry points, manifests, service bridges, and activation/status code where present. This is a design comparison, not a claim that every repository implements an activation indicator. Some projects are legacy examples; their value is the documented entry contract rather than current API compatibility.

## Findings relevant to YunSeek

1. **A module being enabled in LSPosed, a framework service being connected, and a target process actually receiving hooks are three different facts.** Do not collapse them into one green “Activated” state.
2. **A persisted target heartbeat is historical evidence, not proof that the target process is currently alive.** Display “last seen” and a freshness window; do not retain a seven-day heartbeat as “currently active”.
3. **Missing asynchronous service callbacks are not proof of inactivity.** HyperCeiler explicitly treats a missing callback as pending and waits 10 seconds before presenting an activation warning.
4. **Legacy and modern entry APIs must be handled separately.** Legacy modules use `assets/xposed_init` and `IXposedHookLoadPackage`; libxposed modules use `XposedModule` lifecycle callbacks and service binding. InstaEclipse is a particularly useful example of two entry adapters feeding one shared module core.
5. **Do not abort all target hooks because the main Looper is not ready at the instant of the package callback.** InstaEclipse hooks `Application.attach` and initializes context there; YunSeek now defers and retries its bootstrap after `Application.attach`.
6. **Hook installation should be observable per hook and fail independently.** TMoe initializes core/function hooks in separate loops and catches individual hook errors. YunSeek should preserve this property and not mark the entire hook set installed before a recoverable bootstrap failure.
7. **A self-test inside the module app is not proof that the DeepSeek target was injected.** The XposedSmsCode and PureNetease examples expose self-check patterns, but YunSeek's status must be based on evidence emitted by `com.deepseek.chat` and authenticated at the receiving side.

## Repository-by-repository review

| # | Repository | Source evidence reviewed | Reusable lesson |
|---:|---|---|---|
| 1 | [Hide-My-Applist](https://github.com/Dr-TSNG/Hide-My-Applist) | `xposed/src/main/assets/xposed_init`, `XposedEntry.kt` | Legacy entry is explicit; package-specific hook routing includes both module app and Android system process. Entry initialization is not equivalent to target feature success. |
| 2 | [PixelXpert](https://github.com/siavash79/PixelXpert) | `PixelXpert.java`, `XposedServiceHelper`, `XposedService` | Uses the libxposed service abstraction; framework connection should be tracked from service callbacks, not inferred from target hook heartbeat. |
| 3 | [HyperCeiler](https://github.com/ReChronoRain/HyperCeiler) | `XposedActivateHelper.java`, `FrameworkStatusManager.java`, `Application.java` | Strong activation-state reference: handles service-bound and service-died events; a missing asynchronous Binder callback is pending, with a 10-second timeout, not immediate inactivity. |
| 4 | [NexAlloy](https://github.com/NexAlloy/NexAlloy) | `MainHook.kt`, `AndroidManifest.xml` | Modern `XposedModule` lifecycle; checks `isFirstPackage` and explicit target package allowlists before installing hooks. |
| 5 | [DeviceSpoofLab-Hooks](https://github.com/yubunus/DeviceSpoofLab-Hooks) | `XposedModuleImpl.java`, `MainHook.java`, `XposedServiceBridge.java`, manifest provider | Separates modern framework service reception from the shared hook coordinator. Its service provider authority must match the module application ID plus `.XposedService`; its module class requires a no-argument constructor in the documented Vector runtime. |
| 6 | [ReVancedXposed Spotify](https://github.com/Coldblackice/ReVancedXposed_Spotify) | `MainHook.kt`, manifest, legacy `xposed_init` | Legacy `IXposedHookLoadPackage` entry with first-application and package allowlist checks; keep target selection explicit. |
| 7 | [InstaEclipse](https://github.com/ReSo7200/InstaEclipse) | `LegacyEntry.java`, `ModernEntry.java`, shared `Module.java` | Best dual-runtime architecture in this sample: separate legacy/modern adapters delegate into one framework-neutral core. Its `Application.attach` hook obtains application context, and expensive first-run scanning is moved to a background thread to avoid blocking startup. |
| 8 | [Knot](https://github.com/2b-zipper/Knot) | `Main.java`, manifest | Modern `onModuleLoaded` records module process context; `onPackageReady` checks the exact target package before bootstrapping. |
| 9 | [Android Virtual Cam](https://github.com/w2016561536/android_virtual_cam) | `assets/xposed_init`, `HookMain.java`, manifest | Minimal legacy entry declaration; useful as a baseline for verifying that the packaged entry class exactly matches the manifest asset. |
| 10 | [WechatMagician](https://github.com/Gh0u1L5/WechatMagician) | `assets/xposed_init`, manifest | Legacy entry is an explicit class name; module settings UI and target hook entry are distinct surfaces. |
| 11 | [XposedWechatHelper](https://github.com/wuxiaosu/XposedWechatHelper) | `assets/xposed_init`, `Main.java`, manifest | Legacy entry performs explicit package and process filtering; avoid installing hooks in unrelated/system applications. |
| 12 | [Unblock163MusicClient-Xposed](https://github.com/bin456789/Unblock163MusicClient-Xposed) | `assets/xposed_init`, `Main.java`, manifest | Legacy entry checks module conditions and routes only to the intended host package; hook entry and settings package are intentionally separate. |
| 13 | [YouTubeAdAway](https://github.com/wanam/YouTubeAdAway) | `assets/xposed_init`, `Xposed.java`, manifest | Legacy module can combine zygote initialization with package-scoped hooks; framework initialization alone is not a target-package injection signal. |
| 14 | [TrustMeAlready](https://github.com/ViRb3/TrustMeAlready) | `assets/xposed_init`, `Main.java`, manifest | Zygote-level module demonstrates that not every module is driven by target app package callbacks; status design must reflect the actual entry model. |
| 15 | [DisableTargetAPIBlock](https://github.com/buttercookie42/DisableTargetAPIBlock) | `assets/xposed_init`, manifest | Minimal legacy metadata and explicit scope declaration; validate the packaged scope resource as well as the entry class. |
| 16 | [YukiHook](https://github.com/HighCapable/YukiHook) | legacy and modern sample module manifests, runtime modules | Maintains distinct legacy and libxposed runtime variants; do not assume one API's lifecycle or service behavior exists in the other. |
| 17 | [DisableFlagSecure](https://github.com/Xposed-Modules-Repo/io.github.lsposed.disableflagsecure) | `DisableFlagSecure.java`, manifest | Modern `XposedModule` uses lifecycle callbacks such as `onModuleLoaded` and `onSystemServerStarting`; system-server hooks have a different target lifecycle from app hooks. |
| 18 | [TMoe](https://github.com/cinit/TMoe) | `MainStartInit.java`, `DynamicHookInit.java`, manifest | Separates pre-start and post-start initialization; catches per-hook failures so one incompatible hook does not prevent all other hooks from initializing. |
| 19 | [XposedSmsCode](https://github.com/tianma8023/XposedSmsCode) | manifest, `ModuleUtils.java` | A module-side `isModuleEnabled` self-check can be implemented by a method that changes only when hooked. It proves only that specific self-hook, not injection into an unrelated target app. |
| 20 | [PureNeteaseCloudMusic-Xposed](https://github.com/zjns/PureNeteaseCloudMusic-Xposed) | `HookInit.java`, manifest | Shows separate module-app self-check and target-app hook branches; keep these signals distinct in YunSeek diagnostics. |

## Application to YunSeek

### Required state model

| Signal | What it proves | What it must not claim |
|---|---|---|
| Framework service Binder alive | The modern libxposed service endpoint is currently connected | Does not prove DeepSeek was injected |
| Module listed/enabled in LSPosed | Framework configuration says the module is enabled for a scope (only if a trustworthy API exposes it) | Does not prove the target process has restarted or received hooks |
| Authenticated DeepSeek heartbeat | The target package reached the module provider through an authenticated call | Does not prove every feature hook registered successfully |
| Per-hook registration/trace | A particular hook registration or callback was observed | Does not prove unrelated hooks work |
| Persisted last-seen timestamp | Historical target injection was observed at that time | Does not prove the target process is currently running |

### Implementation rules

- The launcher must never map `frameworkConnected || targetHeartbeat` to a single “Activated” state.
- A recent target heartbeat may be displayed as “Target process injection verified”; framework Binder state is a separate diagnostic.
- No target heartbeat means “Waiting for target verification”, not “Not activated”, unless the framework provides an authoritative disabled-state signal.
- A historical heartbeat is shown with its timestamp and must not be described as current process liveness.
- The heartbeat receiver must authenticate the caller UID as `com.deepseek.chat`; any fallback that accepts a static token without authenticated sender identity remains a security risk and must be separately reviewed.
- The package callback must not permanently abort hook bootstrap solely because `Looper.getMainLooper()` is temporarily null. Defer to `Application.attach`, then retry once.
- Feature-hook registration must have independent success/failure telemetry. A successful heartbeat is not a blanket “all hooks active” indicator.
- Both Open and Protected builds must execute the same target-injection reporting contract; licensing gates must not silently disable activation diagnostics.
- Legacy `xposed_init` and modern libxposed `java_init.list` are separate packaging contracts. Never copy one entry declaration into the other runtime.

## YunSeek findings confirmed on dev

- The legacy entry assets currently name `com.dsmod.probe.Main`.
- `Main.handleLoadPackage()` previously returned permanently when `Looper.getMainLooper()` was null, before installing the host lifecycle hooks.
- `reportActivationHeartbeat()` previously returned immediately for non-Protected builds, even when the target Activity had resumed.
- `SettingsActivity.refreshActivationState()` previously treated framework Binder OR target heartbeat as “Activated”.
- `XposedActivationProvider` previously treated a seven-day-old heartbeat as “recently active”.

The activation repair branch changes these behaviors and adds a pure-Java regression policy. Android APK construction and real LSPosed/DeepSeek injection remain separate release gates.

## Primary source links

- [HyperCeiler activation helper](https://github.com/ReChronoRain/HyperCeiler/blob/main/app/src/main/java/com/sevtinge/hyperceiler/utils/XposedActivateHelper.java)
- [HyperCeiler framework status manager](https://github.com/ReChronoRain/HyperCeiler/blob/main/app/src/main/java/com/sevtinge/hyperceiler/utils/FrameworkStatusManager.java)
- [InstaEclipse legacy entry](https://github.com/ReSo7200/InstaEclipse/blob/main/app/src/main/java/ps/reso/instaeclipse/Xposed/LegacyEntry.java)
- [InstaEclipse modern entry](https://github.com/ReSo7200/InstaEclipse/blob/main/app/src/main/java/ps/reso/instaeclipse/Xposed/ModernEntry.java)
- [InstaEclipse shared module bootstrap](https://github.com/ReSo7200/InstaEclipse/blob/main/app/src/main/java/ps/reso/instaeclipse/Xposed/Module.java)
- [DeviceSpoofLab modern module entry](https://github.com/yubunus/DeviceSpoofLab-Hooks/blob/main/app/src/main/java/com/devicespooflab/hooks/XposedModuleImpl.java)
- [NexAlloy modern entry](https://github.com/NexAlloy/NexAlloy/blob/main/app/src/main/java/io/github/nexalloy/MainHook.kt)
- [YukiHook legacy and modern samples](https://github.com/HighCapable/YukiHook/tree/1.x/samples)

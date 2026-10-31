# Android Package Identity Migration

## Target identity

The installed Android application ID is now:

`ds.dsh.DeepSeek`

This is the package identity seen by PackageManager, LSPosed scope configuration, explicit component addressing, and the module's Xposed provider authority.

## Deliberate namespace boundary

The Gradle `namespace` and the bulk of the implementation Java/Kotlin packages remain `com.dsmod.probe` in this migration.

This is intentional, not an incomplete applicationId change:

- The current hook implementation contains more than 150 source and test files under the existing namespace.
- The launcher relies on generated `R` and `BuildConfig` symbols in that namespace.
- Rewriting all implementation package declarations and source paths in the same change would create a broad, high-risk refactor unrelated to the installed app identity.
- A new public Xposed entry class, `ds.dsh.DeepSeek.Main`, delegates to the existing `com.dsmod.probe.Main` implementation and retains the legacy `IXposedHookLoadPackage` contract.

The migration therefore changes the Android applicationId and all externally visible Xposed identity values, while keeping internal implementation namespace stable. A full Java namespace migration, if ever needed, should be a separate refactor with its own compile and runtime verification.

## Values that must remain aligned

- Gradle `defaultConfig.applicationId`: `ds.dsh.DeepSeek`
- Legacy `assets/xposed_init`: `ds.dsh.DeepSeek.Main`
- Universal `assets/xposed_init`: `ds.dsh.DeepSeek.Main`
- Entry shim: `ds.dsh.DeepSeek.Main extends com.dsmod.probe.Main`
- Provider authority: `ds.dsh.DeepSeek.XposedService`
- Explicit heartbeat broadcast action: `ds.dsh.DeepSeek.action.REPORT_DEEPSEEK_ACTIVE`
- Explicit broadcast component package: `ds.dsh.DeepSeek`

The module manifests and launcher manifest must all declare the same provider authority.

## Upgrade and data migration warning

Android treats a changed applicationId as a different installed application. An existing `com.dsmod.probe` install cannot be updated in place by an APK whose applicationId is `ds.dsh.DeepSeek`.

Consequences:

- Install the new package as a separate application.
- Enable the new module in LSPosed and select the DeepSeek target scope again.
- The old package's private app data and SharedPreferences are not automatically transferred.
- Keep the old module installed until the new package is confirmed to inject and its features work.
- Do not create a release or recommend uninstalling the old package until the APK manifest and real target-process injection have been verified.

## Verification gates

1. Static identity test checks Gradle applicationId, both Xposed entry assets, the wrapper class, provider authority, and broadcast action.
2. Android validation build must succeed.
3. Inspect the built APK's final manifest package using `aapt dump badging`.
4. Inspect `assets/xposed_init` inside the APK and confirm it names `ds.dsh.DeepSeek.Main`.
5. Install on a test device, enable the new module in LSPosed, scope `com.deepseek.chat`, force-stop/relaunch DeepSeek, and confirm authenticated target heartbeat plus actual hook behavior.

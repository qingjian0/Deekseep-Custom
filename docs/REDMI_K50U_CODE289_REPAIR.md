# Redmi K50 Ultra / DeepSeek 2.7.2 code289 repair

## Scope

This change set addresses the device-reported failures from the Redmi K50 Ultra running mainland DeepSeek 2.7.2 (PackageManager versionCode 289), with LSPosed installed.

It is intentionally scoped to `dev`; `main` is unchanged.

## Findings from uploaded device logs

- The host was identified as `2.7.2/code289-cn`, and the ROI host identity recorder independently captured PackageManager versionName `2.7.2`, versionCode `289`.
- The optional Agent tool-log renderer attempted to resolve the code257-only `mf6` mapping. The exception escaped the required feature-hook step and aborted the remainder of the feature-hook batch.
- The OpenAI gateway rejected `stream_options.include_usage=true` even though the streaming implementation already emits an estimated-usage chunk.
- The earlier OpenAPI request log reports `unsupported_feature` for `tools`. Current source already routes Chat Completions function tools through `OpenAiToolBridge.chatPlan`; this log is therefore treated as evidence from an older installed APK, not proof that the current source lacks the tool bridge. Streaming tool calls remain explicitly unsupported.
- A diagnostic-copy action attempted to place a 1,095,388-byte report on the Android clipboard and triggered `TransactionTooLargeException`.
- The floating overlay companion accepted only code249/code257 host identities. It did not recognize code289/code290, and the permission route lacked MIUI / HyperOS-specific fallback.
- The target activation heartbeat was gated behind `PROTECTED_BUILD`; Open/universal builds therefore could load hooks without reporting target injection to the launcher activation page.

## Changes

1. Treat the Agent tool-log renderer as best-effort so a missing optional code257 mapping cannot abort unrelated feature hooks.
2. Report target-process injection heartbeat in Open/universal and protected builds. The module provider still validates the caller UID against `com.deepseek.chat`.
3. Recognize exact 2.7.2 versionCode 289/290 identities in the overlay companion and permit the 2.7.2 version name in the host version allowlist.
4. Add MIUI / HyperOS overlay permission editor fallback, Android overlay settings fallback, Xiaomi per-app power manager / autostart routes, Android per-package battery-exemption routes, and app-details fallback.
5. Declare `SYSTEM_ALERT_WINDOW` and `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` in the universal, legacy, and launcher manifests.
6. Bound diagnostic clipboard payloads to 64 KiB and keep full-report export as the lossless path.
7. Accept boolean `stream_options.include_usage` values because the SSE implementation already supports estimated usage output.
8. Extend the Local API regression script to guard these behaviors and the existing Chat Completions tool bridge.

## Deliberate boundaries

- No speculative code289 obfuscation mapping is added for `mf6`, `rw2`, or `tw2`. Those optional render hooks must degrade independently until exact code289 evidence is reviewed.
- Streaming tool calls remain rejected with a specific unsupported-feature response; no fake tool execution is introduced.
- HTTP listener startup is not treated as proof that the native completion engine is ready. A successful `/v1/models` response does not prove `/v1/chat/completions` can execute. If the native backend still reports `engine_missing` / `model_not_ready`, that remains a separate code289 native-engine integration blocker.
- The uploaded `tools` 400 trace came from a prior runtime build; install the APK produced from this branch's CI before comparing that exact result again.
- MIUI / HyperOS settings intents vary by ROM build. The implementation tries OEM routes and then Android fallbacks; final permission-page behavior must be verified on the physical Redmi K50 Ultra.
- No physical-device result is marked PASS by this source-only change. LSPosed injection, overlay authorization, battery policy, API client interoperability, and end-to-end native completion require a new device run.

## Device acceptance after CI artifact install

1. Confirm launcher shows DeepSeek target injection after opening DeepSeek once.
2. Confirm `dsprobe.log` reports `activation heartbeat accepted by module provider`.
3. Enable Local API floating keeper; confirm MIUI permission editor opens for the module package and the overlay appears after permission is granted.
4. Open battery settings; verify module and host background-power / autostart settings are reachable.
5. Test `GET /v1/models`.
6. Test Chat Completions with a plain text request, then a non-streaming function-tool request.
7. Test streaming with `stream_options.include_usage=true`; verify final estimated usage chunk.
8. Test streaming tool call and verify the documented unsupported response rather than a false success.
9. Copy the native diagnostic report and verify no clipboard transaction-size crash; use file export for the full report.
10. Verify ordinary DeepSeek chat still works and inspect logs for optional Agent renderer failures without feature-batch aborts.

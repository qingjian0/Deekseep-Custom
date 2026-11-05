# Unknown-host automatic Hook (experimental)

This path is separate from the maintained host adapters. Unknown host versions remain read-only unless the operator explicitly enables a feature-specific switch.

## Enable the first unknown-host feature adapter

Create this file inside the DeepSeek host app's private files directory:

`/data/data/com.deepseek.chat/files/deekseep_roi_unknown_host_feature_hook_enabled`

Its complete contents must be the single line:

`ENABLE`

The switch is read at process start and again on each outgoing chat request. Restart DeepSeek after creating the file so the class-load observer is installed before DEX discovery begins. Removing the file immediately disables prompt rewriting; the Xposed callback remains attached but becomes pass-through until the host process is restarted.

## What this adapter does

The first adapter recognizes the outgoing `ChatFullCompletionRequest` constructor using multiple serialized field-name anchors and the complete 11-parameter constructor descriptor. It then binds the candidate to an already-loaded class in the exact runtime ClassLoader scope.

When all checks pass, it injects only the configured system prompt and plugin system prompt at constructor argument index 2.

## Fail-closed conditions

No feature Hook is installed when any of the following occurs:

- The switch is absent, malformed, or contains extra lines.
- The DEX candidate comes only from an APK path and not a runtime loader scope.
- Serializer field anchors or constructor shape do not match.
- The runtime class owner, constructor descriptor, or ClassLoader scope differs.
- Multiple candidates have equal evidence and remain ambiguous.
- The bounded discovery batch is interrupted before finalization.

## Explicit limitations

This adapter does not hook Local API transport, SSE response decoding, authentication, PoW, attachments, session management, or Agent tool dispatch. Those capabilities require independent semantic contracts and feature adapters. The generic unknown-host functional-hook policy remains closed for all other capabilities.

A successful installation log uses:

`[ROI_AUTO_FEATURE_HOOK] ... capability=outgoing_system_prompt`

This is not proof of real-device compatibility. Validate the target DeepSeek build on a device before treating the adapter as production-ready.

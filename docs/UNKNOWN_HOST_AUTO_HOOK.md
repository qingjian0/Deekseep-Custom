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

## Cross-version constructor mapping status

The current adapter is **not a generic constructor adapter**. Its reviewed mapping is still bound to an 11-parameter constructor and prompt argument index 2. The DEX candidate filter also currently accepts only that known constructor descriptor shape. A changed parameter count, order, or type layout must remain unsupported until a new mapping is proven.

The current DexKit integration supplies method descriptors, parameter type names, opcode names, string/field references, and invoke/caller relations. Those summaries do not, by themselves, identify which constructor argument register receives a decoded serializer field. See the [DexKit MethodMatcher API](https://luckypray.org/DexKit-Doc/dexkit-android/org.luckypray.dexkit.query.matchers/-method-matcher/index.html) and [DexKitBridge API](https://luckypray.org/DexKit-Doc/dexkit-android/org.luckypray.dexkit/-dex-kit-bridge/index.html).

A version-independent mapping must establish this evidence chain before installing the prompt adapter:

1. Resolve the serializer descriptor's field-name to element-index mapping (including generated Kotlin serialization descriptors).
2. Resolve the deserializer's decode call and element index to the value register.
3. Follow register definitions and aliases through moves, casts, result moves, and control-flow joins.
4. Prove which constructor argument register receives that value at the exact constructor invoke.
5. Convert the proven invoke register to the reflected constructor parameter index, accounting for receiver and wide arguments.
6. Reject incomplete paths, conflicting paths, multiple mappings, unsupported opcodes, and ambiguous candidates.

A field-name hit plus a constructor reference in the same serializer owner is candidate evidence only; it is not proof of a constructor-parameter mapping. The current index-2 adapter must therefore remain narrowly scoped.

### DEX analysis backend decision

DexKit 2.3.0 remains the bounded candidate-discovery backend. Its documented MethodData summaries are useful for narrowing the search, but the current integration does not receive a register-level instruction stream from those summaries. DexKitBridge.exportDexFile is available as a possible hand-off to a focused instruction reader.

dexlib2 is a viable reference implementation for instruction/register decoding, but its 2.5.2 artifact is approximately 1.1 MB before dependencies, and it has Guava and JSR-305 runtime dependencies. The current manual APK pipelines feed jars directly to D8 rather than running a shrinking R8 pass. Adding dexlib2 without a measured shrink/packaging plan would therefore create unnecessary APK growth. Do not add it blindly.

The next implementation phase must first compare:
- a minimal read-only DEX code_item decoder limited to the required opcodes and descriptor/index tables; and
- dexlib2 with an explicit R8 shrink configuration and measured APK-size/runtime impact.

The selected backend must have fixture-based tests for reordered and added constructor parameters, register moves, move-result-object, casts, field-index decoding, branch joins, wide parameters, and ambiguous/unsupported flows. Until those tests pass, unknown-host prompt mapping remains restricted to the existing reviewed 11-parameter layout.

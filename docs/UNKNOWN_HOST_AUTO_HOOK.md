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
## Repository integration audit (2026-11-05)

The current discovery implementation opens DexKit bridges through `DexKitBridge.create(ClassLoader, true)` for runtime loaders and `DexKitBridge.create(apkPath)` for installed APK paths. The inspected discovery path does not call `exportDexFile`, does not persist an exported DEX artifact, and does not pass instruction bytes to a decoder. Therefore the proposed DexKit-to-instruction-reader hand-off is not implemented yet.

The build scripts compile the shared Java source tree into both Legacy and Universal editions. They currently package DexKit and FlatBuffers into D8 directly; there is no R8 shrinking invocation in these scripts. Any new parser must remain dependency-free unless a separate shrink-and-size validation is added.

### First implementation gate

Before changing feature-hook authorization, add and test a dependency-free instruction layer that:

- decodes the relevant DEX formats with explicit instruction widths and bounds checks;
- extracts argument registers from both `invoke-direct` (`35c`) and `invoke-direct/range` (`3rc`);
- decodes only explicitly supported move, result-move, cast, string, and object-field operations;
- maps invoke argument-word positions to reflected parameter indices while accounting for the receiver and `J`/`D` wide parameters;
- returns an explicit unsupported/invalid result for unknown opcodes, malformed encodings, out-of-range registers, or ambiguous mappings.

This layer is not yet a complete DEX parser and must not authorize a Hook by itself. The next gate is extracting DEX bytes from the DexKit/APK source and resolving serializer descriptor element indices through the deserializer's register flow into a constructor invoke. The fixed 11-parameter/index-2 adapter remains unchanged until that complete evidence chain is tested.

### Integration audit result

- Runtime-loader DexKit bridge: present.
- APK-path DexKit bridge: present.
- `exportDexFile` call in the inspected discovery implementation: absent.
- Instruction-byte reader / register data-flow analyzer: absent.
- Generic constructor parameter mapping: absent.
- Unknown-host authorization: remains restricted to the reviewed constructor shape and explicit opt-in.


## DEX reader implementation status (2026-11-05)

A dependency-free `RoiDexFileReader` now parses the standard little-endian DEX header and the fixed-width string, type, proto, field, method, and class definition ID tables. It validates the DEX magic/header/endian marker, file and data ranges, table alignment and overlap, map/data offsets, and table cross-references. Unsupported endian encodings and malformed offsets fail closed.

The reader is currently a standalone parser with synthetic fixture tests. It does **not** yet parse modified UTF-8 string payloads, type lists, `class_data_item`, `encoded_method`, `code_item`, debug data, or instruction streams. It is not connected to DexKit discovery and does not influence Hook authorization. The next gate is bounded ULEB128/string/type-list parsing followed by class-data and code-item extraction, with malformed/truncated fixture coverage at each layer.

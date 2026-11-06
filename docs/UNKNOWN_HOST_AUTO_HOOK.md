# Unknown-host automatic Hook (experimental)

This path is separate from maintained host adapters. Unknown host versions remain read-only unless the operator explicitly enables a feature-specific switch. Static analysis never grants runtime Hook authorization.

## Enable the first unknown-host feature adapter

Create this file inside the DeepSeek host app's private files directory:

`/data/data/com.deepseek.chat/files/deekseep_roi_unknown_host_feature_hook_enabled`

Its complete contents must be the single line:

`ENABLE`

The switch is read at process start and again on each outgoing chat request. Restart DeepSeek after creating the file so the class-load observer is installed before DEX discovery begins. Removing the file immediately disables prompt rewriting; the Xposed callback remains attached but becomes pass-through until the host process is restarted.

## First adapter scope

The first adapter recognizes the outgoing `ChatFullCompletionRequest` constructor using serialized field-name anchors and the reviewed 11-parameter constructor descriptor. It binds the candidate to an already-loaded class in the exact runtime ClassLoader scope.

When all checks pass, it injects only the configured system prompt and plugin system prompt at constructor argument index 2.

No feature Hook is installed when:
- The switch is absent, malformed, or contains extra lines.
- Evidence comes only from an APK-path scan, without a matching runtime-loader candidate.
- Serializer field anchors or constructor shape do not match.
- Runtime class owner, constructor descriptor, or ClassLoader scope differs.
- Multiple candidates remain ambiguous.
- The bounded discovery batch is interrupted before finalization.

A successful installation log uses:

`[ROI_AUTO_FEATURE_HOOK] ... capability=outgoing_system_prompt`

This log is not proof of real-device compatibility. CI and synthetic fixtures do not replace validation on the target DeepSeek build and Android device.

## Explicit feature limitations

This adapter does not hook Local API transport, SSE response decoding, authentication, PoW, attachments, session management, or Agent tool dispatch. Those capabilities require independent semantic contracts and feature adapters. The generic unknown-host functional-hook policy remains closed for all other capabilities.

## Cross-version constructor mapping status

The current adapter is **not a generic constructor adapter**. Its reviewed mapping is bound to an 11-parameter constructor and prompt argument index 2. A changed parameter count, order, or type layout remains unsupported until a new mapping is proven.

A version-independent mapping must establish this evidence chain before installing a generalized prompt adapter:

1. Resolve the serializer descriptor's field-name to element-index mapping, including generated Kotlin serialization descriptors.
2. Resolve the deserializer's decode call and element index to the value register.
3. Follow register definitions and aliases through moves, casts, result moves, and control-flow joins.
4. Prove which constructor argument register receives that value at the exact constructor invoke.
5. Convert the invoke register-word position to a reflected constructor parameter index, accounting for the receiver and wide arguments.
6. Reject incomplete paths, conflicting paths, multiple mappings, unsupported opcodes, and ambiguous candidates.

A field-name hit plus a constructor reference in the same serializer owner is candidate evidence only; it is not proof of a constructor-parameter mapping. The current index-2 adapter must therefore remain narrowly scoped.

## Repository integration audit (2026-11-06)

### Implemented building blocks

The following dependency-free analysis components exist in the current `dev` source tree:

- `RoiDexFileReader`: validates and parses the standard little-endian DEX header and fixed ID tables; decodes string data and type lists; parses class definitions, class data, encoded methods, and code items; joins defined methods with method/prototype metadata.
- `RoiDexDataReader`: bounded ULEB128, modified UTF-8 string-data, and type-list readers.
- `RoiDexCodeReader`: bounded class-data and code-item parsing, including instruction code units and try/catch metadata.
- `RoiDexInstructionScanner`: instruction-width scanning and conservative control-flow graph construction.
- `RoiDexInstructionDecoder`: selected instruction decoding, invoke register extraction, and descriptor-aware mapping from invoke register-word positions to reflected parameter indices.
- `RoiDexMethodEntryLayout`, `RoiDexRegisterFlowAnalyzer`, `RoiDexReachingDefinitions`, and `RoiDexUseDefChains`: method-entry register layout and static register-flow analysis.
- `RoiDexCandidateEvidence` and `RoiDexMethodAnalysis`: source-scoped candidate graph assembly and per-method analysis.

These are implemented building blocks, not evidence that unknown-host discovery currently runs the full parser/data-flow pipeline.

### Current end-to-end integration boundary

- Runtime-loader and installed APK-path DexKit discovery are present. Only runtime-loader candidates can enter the feature resolver.
- For each scanned runtime loader, `RoiDexKitDiscovery` now calls DexKit 2.3.0 `exportDexFile(outPath)` and passes every exported file through `RoiDexKitExportReader` into `RoiDexFileReader.read(byte[])`.
- The export reader uses a fresh private cache subdirectory, validates the expected DEX file count, checks the `classes_<byte-length>.dex` filename against the actual file length, enforces per-file and aggregate size bounds, parses files one at a time, and removes the temporary directory in a finally block. A same-length export collision is rejected by the file-count check.
- Parsed defined methods are indexed by full method descriptor and exported DEX filename. Duplicate method descriptors across exported DEX files are marked ambiguous and excluded. The index is weakly keyed by the exact ClassLoader and reused while its DexKit DEX count remains unchanged; a changed count forces a fresh export and parse. The per-loader method index is capped at 300,000 entries.
- DexKit anchor candidates must match an exact parsed runtime DEX method descriptor before they can enter the unknown-host resolver. Invoke edges are filtered to methods defined uniquely in the exported runtime DEX set. Serializer method and request-constructor references must both join to unique parsed definitions; their DEX filenames are retained in diagnostics.
- APK-path scans remain diagnostic supplements and do not contribute to runtime feature authorization.
- The discovery path does **not yet** run `RoiDexCandidateEvidence.analyze(...)` on the exact matched serializer/deserializer methods, nor does it trace serializer element indices through register definitions into the constructor invoke.
- The new export-reader fixture cases and updated wiring assertions are committed, but were deliberately not dispatched to CI under the CI-avoidance rule; they remain unexecuted until a safe validation window is available.
- The existing runtime adapter still uses the reviewed fixed constructor layout and prompt argument index 2.

This closes the missing **runtime DEX byte hand-off and exact method-identity join**. It does not close semantic argument mapping. DexKit call summaries plus exact method identity still do not prove which constructor parameter receives a decoded serializer field.

### Next implementation gate

1. Retain the parsed DEX identity for each matched serializer/deserializer method and invoke target; reject duplicate or cross-DEX ambiguous identities.
2. Run bounded `RoiDexMethodAnalysis` only on exact matched methods, rather than analyzing every method in a multidex image.
3. Resolve the serializer descriptor's field-name to element-index mapping, including generated Kotlin serialization descriptors.
4. Resolve deserializer decode calls and element indices to value registers; trace those registers through moves, casts, move-result operations, and control-flow joins.
5. Prove the exact constructor invoke receiving the value, then map invoke register words to reflected parameters while accounting for receiver and wide arguments.
6. Compare the derived constructor descriptor and mapping against the already-loaded runtime class in the same ClassLoader scope.
7. Add fixture tests for reordered and added constructor parameters, register moves, move-result-object, casts, field-index decoding, branch joins, wide parameters, exception-flow uncertainty, duplicate DEX definitions, and ambiguous/unsupported flows.
8. Keep generalized mapping in discovery-only mode until all evidence checks pass. Runtime activation must still require the feature-specific opt-in, exact ClassLoader match, unambiguous candidate, and a separately authorized installer.

### Acceptance boundary

A stage is complete only when the DEX-byte source, parser, candidate join, field-to-register trace, invoke-argument mapping, reflected-constructor match, and fail-closed behavior are exercised together by executable fixtures. Source presence or isolated unit tests are insufficient.

Until that gate passes:
- Unknown-host prompt mapping remains restricted to the reviewed 11-parameter layout and argument index 2.
- Unknown versions are not declared generically auto-Hook compatible.
- Diagnostic probes remain opt-in/disabled by default.
- No static analysis result may independently authorize a feature Hook.
- Real-device compatibility remains unverified unless an actual device test is recorded.

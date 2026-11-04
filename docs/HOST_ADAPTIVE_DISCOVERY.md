# Host Adaptive Discovery

## Goal

Move host compatibility from a version-name-only lookup model toward a layered discovery system that can inspect an unknown DeepSeek Android build, explain candidate Hook points, and preserve verified mappings for later releases.

The system must remain fail-closed: discovery is not permission to install a Hook. A candidate must be independently validated before activation.

## Current baseline

- `module/hostmap/extract.py` already performs offline DEX extraction with per-class superclass, interfaces, fields, methods, access flags, strings, obfuscated references, external references, and per-method instruction-derived call/reference tokens.
- `module/hostmap/match.py` already performs cross-version class matching through unique-string votes, normalized structural signatures, and iterative typed-reference propagation.
- `module/hostmap/fuzzy.py` adds a second-pass score using mapped incoming/outgoing reference neighborhoods, strings, external references, member signatures, and superclass shape. `members.py` derives field/method mappings after class mappings.
- This is a meaningful offline semantic-mapping foundation, not a live runtime auto-adapter. The current scripts depend on external APK-derived pickle/JSON intermediates and hard-coded workspace paths; they are not yet a clean, reproducible end-user pipeline.
- `HostCompat` identifies known host generations and selects a reviewed compatibility path.
- `HostRemap` supplies generated symbol mappings for known host version codes.
- `OpenNativeCompletionProbe` performs bounded runtime reflection and DEX inventory inspection for a narrow native-completion use case.
- The current probe scans the supplied `BaseDexClassLoader.pathList.dexElements`; it does not claim complete coverage of parent, split, or dynamically introduced class loaders.
- `FeatureHookInstallRegistry` tracks installation lifecycle for stable feature groups. It is not a registry of discovered host members.
- `module-kotlin-runtime` is a pure JVM policy module and is the appropriate home for version-neutral discovery contracts. Android/Xposed reflection and DEX access remain in the Android module.

## Target pipeline

1. **Host identity** — package, version name/code, distribution flavor, and a bounded DEX inventory fingerprint.
2. **Code discovery** — enumerate visible class loaders and code units, including split and dynamically loaded code where the runtime exposes them.
3. **Structural extraction** — collect class hierarchy, interfaces, field and method descriptors, access flags, and bounded string/call-site evidence.
4. **Semantic resolution** — match roles such as request model, transport boundary, response stream, and manager using multiple independent features rather than obfuscated names.
5. **Candidate validation** — check exact runtime types, parameter/return compatibility, lifecycle behavior, and observed call-path evidence.
6. **Mapping persistence** — store version-scoped mappings together with evidence and provenance; revalidate when the host fingerprint changes.
7. **Hook activation** — only verified candidates can be handed to the existing Hook installation layer.

## Candidate lifecycle

- `DISCOVERED`: found by static or runtime inspection; never install.
- `VERIFIED`: semantic role and runtime contract have passed explicit validation.
- `ACTIVE`: approved for Hook installation.
- `REJECTED`: disproven candidate; terminal for that evidence set.

Allowed transitions are:

- DISCOVERED -> VERIFIED or REJECTED
- VERIFIED -> ACTIVE or REJECTED
- ACTIVE -> VERIFIED (deactivate) or REJECTED
- REJECTED -> no transition

Confidence is explanatory metadata, not an activation switch. A high score alone must never activate a Hook.

## First implementation slice

`HostDiscoveryModel.kt` introduces pure data contracts for host identity, class-loader inventory, DEX units, evidence, Hook candidates, and discovery snapshots. It intentionally has no Android, Xposed, reflection, DEX parser, persistence, or network dependency.

The model tests enforce:
- valid host identity and inventory counts;
- unique IDs in a discovery snapshot;
- evidence requirements for verified/active candidates;
- explicit candidate lifecycle transitions;
- rejection being terminal.

This is a contract layer, not yet a live scanner. The next implementation slice should add a bounded Android-side class-loader/DEX inventory adapter and feed its results into this model without changing existing Hook activation behavior.

## DEX parsing strategy

### Selected runtime parser: DexKit 2.3.0

DexKit is the preferred candidate for the Android-side semantic DEX adapter:

- Artifact: `org.luckypray:dexkit:2.3.0` (Android AAR).
- It supports loading from an APK path, DEX byte arrays, or a `ClassLoader`.
- The ClassLoader memory-DEX mode (`DexKitBridge.create(loader, true)`) attempts to read DEX through ART runtime cookies and falls back to file-backed DEX when an OatDex cannot be parsed. This is useful for code already visible to ART; it is not a general decryptor for encrypted code that has not been loaded.
- Query capabilities include class, method, and field structure; strings; numeric constants; opcodes; and invoked methods. These features can express semantic signatures without relying on obfuscated names.
- The 2.3.0 release adds numeric-literal matching and raw DEX access-flag matching, and improves string matching and eligible zero-copy DEX loading.

Official references:
- https://github.com/LuckyPray/DexKit
- https://github.com/LuckyPray/DexKit/releases/tag/2.3.0
- https://luckypray.org/DexKit/zh-cn/guide/quick-start

### Integration status and build constraints

DexKit has been evaluated but is **not yet integrated**. Do not mark runtime discovery complete until both distributable APK variants build and load the parser on-device.

The current Legacy and Universal build scripts compile Java/Kotlin runtime sources manually, invoke D8 with an explicit JAR list, and assemble the APK with ZIP operations. A Gradle dependency declaration alone will not place DexKit classes or JNI libraries into these APKs.

Before implementation, the packaging slice must:
1. Resolve and stage the DexKit AAR's `classes.jar`.
2. Resolve and stage its runtime dependencies, including the published FlatBuffers Java and `cxx` artifacts, using the exact versions declared by the selected DexKit release.
3. Merge Java bytecode into the D8 input set without duplicate classes.
4. Package `libdexkit.so` and every required transitive JNI library for the ABIs actually supported by each APK.
5. Verify the final APK contents and native load path for both Legacy and Universal variants.
6. Include third-party license notices. DexKit publishes Apache-2.0 licensing outside `Core/` and LGPL-3.0 for `Core/`; preserve the corresponding notices and review the final distribution obligations.

The project currently targets a minimum API above DexKit's documented minimum API 21 / recommended API 23. The documented Xposed native-library packaging warning for minSdk below 23 is therefore not expected to be the limiting issue, but manual APK assembly still requires explicit verification of JNI library placement and loadability.

### Adapter design

Implement DexKit behind an Android-only adapter (for example, `RoiDexKitDiscovery`); keep `module-kotlin-runtime` free of Android, Xposed, and native parser dependencies.

- Create one bridge per bounded analysis scope, off the main thread, and always close it.
- Prefer ClassLoader memory-DEX inspection; record whether each result came from memory or file fallback.
- Bound query count, result count, execution time, and retained evidence.
- Emit candidates and evidence only. Do not install Hooks from parser results.
- Preserve explicit coverage gaps for inaccessible parent, split, or dynamic loaders.
- Do not describe DexKit as arbitrary DEX decryption. It can inspect DEX exposed through files or the ART runtime; code not yet available to ART remains outside this capability.

## Safety and operational limits

- Never invoke unknown host methods merely to identify them.
- Do not initialize host classes during inventory unless required and explicitly justified.
- Bound class, member, string, and report counts; preserve summary metadata even when details are truncated.
- Treat inaccessible or dynamically loaded code as an explicit coverage gap, not as proof that a class does not exist.
- Do not collect tokens, chat contents, account credentials, or unrelated user data as discovery evidence.
- Do not persist executable Hook instructions from untrusted or unverified discovery output.
- Keep existing known-version mappings as the fast path; discovery is the fallback for unknown or changed versions.

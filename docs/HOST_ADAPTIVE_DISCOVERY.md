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

Do not add a parser dependency until its Android runtime compatibility, API surface, licensing, APK/native-library footprint, and maintenance status have been checked. Keep the first inventory adapter reflection-based and read-only. A dedicated DEX analysis library can later be introduced behind an adapter if it materially improves method/call-site analysis.

## Safety and operational limits

- Never invoke unknown host methods merely to identify them.
- Do not initialize host classes during inventory unless required and explicitly justified.
- Bound class, member, string, and report counts; preserve summary metadata even when details are truncated.
- Treat inaccessible or dynamically loaded code as an explicit coverage gap, not as proof that a class does not exist.
- Do not collect tokens, chat contents, account credentials, or unrelated user data as discovery evidence.
- Do not persist executable Hook instructions from untrusted or unverified discovery output.
- Keep existing known-version mappings as the fast path; discovery is the fallback for unknown or changed versions.

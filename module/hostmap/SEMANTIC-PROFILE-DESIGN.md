# Cross-version semantic Hook profiles

## Why this layer exists

R8 obfuscation makes class and method names version-local. A name collision is not identity: the 2.7.2 runtime observer records `jw3` as a transport-low-level class, while the 2.7.5 static audit identifies `jw3` as an Android ID helper. Likewise, the 2.7.2 `zn3.o(...)` transport entry must not be mapped to the unrelated 2.7.5 `zn3` interface merely because the short name matches.

The source evidence and generated APK artifacts are maintained in [qingjian0/apps](https://github.com/qingjian0/apps). Its 2.7.2 profile is tied to APK SHA-256 `3c2fe31c73575400b9d59e46dde05daa2ef06491498ebfef658801e99eca1aff`.

## 2.7.2/code289-cn evidence anchor

The runtime observation and DEX extraction agree on these version-local descriptors:

| Semantic role | 2.7.2/code289-cn descriptor | Evidence |
|---|---|---|
| Chat request model | `Lly1;` implements `Lwu1;` | Runtime observer + DEX interface and constructor inventory |
| Transport dispatch | `Lzn3;->o(Lwu1;Ljava/lang/Long;)Ly60;` | Runtime observer + DEX method |
| Low-level transport | `Ljw3;->g(Lwu1;Ljava/lang/Long;)Ly60;` | Runtime observer + DEX method |
| Async continuation | `Ly60;` implements `Lel4;`; `b(Lfl4;Lyb3;)Ljava/lang/Object;` | Runtime observer + DEX interface and method |

The static audit found 3 DEX files, 15,365 Smali files and 72,258 method declarations. JADX exited with 346 decompilation errors; the extracted Smali archive remains the fallback evidence for affected code.

## Matcher contract

`RoiSemanticProfileMatcher` is an offline, deterministic comparison primitive. It compares:

- superclass and interface shape;
- field type multiplicity;
- method prototype multiplicity while ignoring obfuscated method names;
- stable external API call references;
- stable semantic markers.

Obfuscated object types are normalized, but platform/library types and primitive types remain distinguishable. Results are ranked using a confidence threshold and a uniqueness margin.

The matcher has four critical fail-closed outcomes:

- `LOW_CONFIDENCE`: insufficient structural agreement;
- `AMBIGUOUS`: two or more candidates are effectively tied;
- `ROLE_MISMATCH`: semantic roles differ;
- `CONTRADICTORY`: stable semantic markers conflict.

A `MATCHED` result is still only a candidate mapping. The API exposes `activationAuthorized=false` for every result. It does not load classes, invoke methods, authorize a Hook, or install a Hook.

## Runtime activation boundary

The existing activation path remains separate:

1. Detect the exact host build and verify APK/DEX identity.
2. Select that build's reviewed profile and mapping table.
3. Resolve the exact runtime member under the host ClassLoader.
4. Collect independent host identity, DEX, method-shape, call-graph, runtime invocation, return-contract and lifecycle evidence.
5. Pass the candidate through the existing ROI authorization policy.
6. Install through the managed Hook lifecycle and verify the registration.
7. Keep ambiguous, incomplete, contradictory or unverified candidates inactive.

No cross-version profile match can bypass the feature-specific opt-in or ROI policy.

## Current implementation status

- Implemented: generic profile comparison, normalized prototype matching, candidate ranking, ambiguity and contradiction rejection, method-role mapping when a prototype is unique.
- Implemented: regression tests for class/method renaming, ambiguity, low confidence, role mismatch, semantic contradiction and no-activation behavior.
- Corrected: startup diagnostics now print the actual `HostCompat.hostLabel()` when the 2.7.x HostRemap table is selected; the previous log text incorrectly called all 287/289/290 maps “2.7.1”.
- Existing behavior retained: code289 uses the separately named `hostmap289.txt.gz`; the matcher does not replace or silently regenerate that table.
- Not yet implemented: a fully automated extractor that emits complete semantic profiles for every feature from each APK, a reviewed 2.7.3/2.7.5 profile catalog, and runtime profile loading into all feature Hook installers.
- Not yet verified: real-device Hook execution for the profile-resolved targets.

This is deliberately not described as universal Hook coverage. The existing source audit identified at least 183 legacy registration call sites; they still require phased migration and per-feature validation.

# DeepSeek 2.7.5 Host Profile — Static-Only Stage

## 1. Purpose and activation status

This document records the evidence boundary for the official DeepSeek Android 2.7.5 APK stored in `qingjian0/apps`.

**Current status: recognized as a static-only host generation; feature Hook activation is disabled.**

The 2.7.5 static audit is sufficient to identify a distinct host generation and prioritize reverse-engineering targets. It is not sufficient to populate the reviewed Hook catalog or to claim runtime compatibility.

The ROI branch adds a conservative `HostCompat` recognition path for the static marker set `qy1 + nw3 + pv1 + b70`. That path:

- sets the diagnostic generation to `2.7.5/code295-static-only`;
- returns before the older 2.5.0 / 2.4.x / 2.3.x / 2.2.x fallback detection;
- does **not** set any maintained-host flag;
- does **not** activate `HostRemap`;
- does **not** authorize any `RoiReviewedHookEvidenceRegistry` entry;
- leaves the host in the existing unknown-host policy path, whose discovery/diagnostic/feature lanes remain independently opt-in.

This is a compatibility safety guard, not 2.7.5 support.

## 2. APK identity evidence

Source APK:

- Repository: `qingjian0/apps`
- Path: `DeepSeek_2.7.5.apk`
- URL: https://github.com/qingjian0/apps/blob/main/DeepSeek_2.7.5.apk
- Size: `22,210,268` bytes
- SHA-256: `ddc52ef868b991fd33e634de834a3e2c022b0cd7ff9e854f4f141996aaeadb70`

The 2.7.5 audit artifact contains the APKTool DEX/smali archive. The binary `AndroidManifest.xml` was read from that archive and its root manifest attributes decoded:

| Field | Value | Evidence |
|---|---|---|
| Package | `com.deepseek.chat` | Binary manifest `package` attribute |
| versionName | `2.7.5` | Binary manifest `versionName` attribute |
| versionCode | `295` | Binary manifest typed integer attribute |

DEX inventory:

| DEX | Bytes |
|---|---:|
| `classes.dex` | 12,697,064 |
| `classes2.dex` | 264,820 |
| `classes3.dex` | 2,759,260 |

Audit quality:

- 3 DEX files.
- 15,364 smali files.
- 13,940 JADX Java source files.
- JADX exit code `3`, with 344 reported decompilation errors.
- 16 native libraries, all under `lib/arm64-v8a/`.
- The static artifact does not establish that every method decompiled successfully.

Audit runs:

- 2.7.5 static audit: https://github.com/qingjian0/apps/actions/runs/42335918440
- 2.7.3 / 2.7.5 comparison audit: https://github.com/qingjian0/apps/actions/runs/42338674344

### Identity fields still missing

The source APK SHA-256 is known, but the following are not yet established:

1. APK signing-certificate SHA-256 digest from the APK signature block.
2. Whether the user's installed host is byte-identical to this repository APK.
3. Whether the installed package is a single APK or a base-plus-split APK set.
4. The installed base/split aggregate fingerprint captured by `RoiAndroidHostIdentity`.

The current audit artifact did not include the original APK bytes or a signer report. The repository APK must be reprocessed by an audit step using Android `apksigner verify --print-certs` (and `aapt dump badging` or equivalent) to preserve signer and manifest metadata as a machine-readable artifact.

Do not substitute the repository APK digest for the installed APK-set digest. Do not infer the signer from the brand, package name, or version.

## 3. Static call-chain evidence

The current `apps` README records these 2.7.5 static paths:

### Request / network

```text
gx1.b
  -> rr2 session / stream orchestration
  -> nw3.e request-type routing and serialization
  -> b70 (Flow)
  -> gr2
  -> pv1.j / pv1.l stream collection
```

Request model candidates:

- `qy1`: ChatFullCompletionRequest
- `rf2`: Regenerate request
- `jx1`: Continue request
- `rx1`: Edit request
- `yf2`: Resume request

### SSE

```text
b70
  -> rr2.d
  -> gr2
  -> pv1.j / pv1.l
  -> ot1 event collector
  -> pv1.a event dispatch
  -> pv1.e fragment / DeltaCommand update
  -> message fragment state
```

### Agent / tool data

The audit identifies `ToolOpenFragment`, `ToolFindFragment`, `SearchFragment` and related reference models. Current evidence supports server-originated structured tool-result fragments being received, merged and rendered by the client. It does not establish a complete local Android tool-execution loop.

### Evidence boundary

The names above are version-local static-analysis observations. They are not yet approved HookSpecs. Before any HookSpec is reviewed, the evidence bundle must include:

- exact owner binary name;
- method name and full JVM descriptor;
- declaring class and inherited/interface relationship;
- static/suspend/bridge/synthetic flags where relevant;
- call-site evidence and semantic role;
- DEX method/code offset or smali source location;
- exact host build identity binding;
- explicit failure-closed policy;
- test coverage for ambiguity and mismatch.

The current README contains useful semantic targets but does not yet provide a complete reviewed HookSpec set for these entries.

## 4. ROI activation requirements

The existing activation boundary requires a complete `RoiHostBuildIdentity`:

- package name;
- Android versionCode and versionName;
- distribution channel;
- aggregate digest of the installed base + split APK set;
- signer-certificate-set digest;
- process-local ClassLoader scope.

The activation fingerprint binds all non-ClassLoader build identity fields. The reviewed evidence registry additionally requires the candidate scope to match and the candidate descriptor to exactly match the HookSpec.

For 2.7.5, do not add a production record to `RoiReviewedHookEvidenceRegistry` until the signer and installed APK-set identity are available and the method descriptors have been reviewed.

## 5. Next implementation sequence

1. Extend the `apps` audit workflow to emit `apk-identity.json` containing package, versionCode, versionName, APK SHA-256, signer certificate SHA-256, and signature-scheme verification output.
2. Re-run the static audit and retain the identity artifact alongside the smali/JADX evidence.
3. Capture the installed host identity through `RoiAndroidHostIdentity` on a worker thread; compare it with the audited APK identity. Keep split-set identity distinct from the base APK digest.
4. Build a version-scoped 2.7.5 static evidence bundle with complete descriptors for request routing, SSE dispatch, continuation, and selected tool-fragment boundaries.
5. Add ambiguity, mismatch, missing-signer, split-set, and wrong-ClassLoader regression tests.
6. Only then add reviewed HookSpecs. Keep discovery-only and diagnostic-only records separate from activation authorization.
7. Real-device / LSPosed validation remains the final stage, after source-level evidence and automated checks.

## 6. Explicit non-claims

At this stage we do **not** claim:

- production support for DeepSeek 2.7.5;
- an activated 2.7.5 HostRemap table;
- a verified 2.7.5 request/SSE Hook;
- local execution of the server's ToolOpen / ToolFind / Search actions;
- a match between the repository APK and the user's installed package;
- signer-certificate identity;
- real-device or LSPosed success.

The 2.7.5 generation marker exists only to prevent accidental fallback to legacy host mappings and to improve diagnostics while the evidence is completed.

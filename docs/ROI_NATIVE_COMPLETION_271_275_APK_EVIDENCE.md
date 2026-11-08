# ROI / Native Completion Adapter — 2.7.1–2.7.5 APK Evidence Handoff

Date: 2026-11-08
Target implementation PR: `qingjian0/Deekseep-Custom#24`
Target branch: `work/roi-suspend-return-semantics-20261108`
Evidence repository: `qingjian0/apps@main`

## Executive result

The `apps` repository contains first-party APK/APKM inputs and static audit indexes for all five labels 2.7.1–2.7.5. This materially advances the multi-version task. It does **not** establish that all five are ready for Native Completion: static candidates are explicitly not runtime-confirmed and not Hook-authorized.

Important correction: the input labelled 2.7.3 is not a verified 2.7.3 APK. Its binary manifest says versionName `2.5.2`, versionCode `273`. The repository's own universal-hook audit excludes it from trusted cross-version matching. Do not build a 2.7.3 production profile from this input.

## APK identity matrix

All listed packages are `com.deepseek.chat`; signing certificate SHA-256 in the audit is `c6893c5368f7b5122fecdafc6c4ecf49e0b3dc4e42bd166cdb646907788c7ffa`.

| Label | Actual manifest identity | Input / SHA-256 | Audit status | Adapter policy |
|---|---|---|---|---|
| 2.7.1 | 2.7.1 / code287 | `deepseek271.apk` / `86e77d419fed957dde8857a14d2f5340499ea40549f10cc3180c596e3f77efc8` | Identity verified; 3 DEX | Keep existing HostV271 evidence as a distinct profile; completion still needs transport/collector/response proof |
| 2.7.2 mainland | 2.7.2 / code289 | `deepseek-2-7-2.apk` / `3c2fe31c73575400b9d59e46dde05daa2ef06491498ebfef658801e99eca1aff` | Identity verified; 3 DEX | Existing dynamic code289 evidence remains isolated to this build |
| 2.7.2 Google Play | HostCompat marker code290 | Separate flavor | Existing HostCompat marker only | Never reuse mainland code289 mappings without independent APK evidence |
| 2.7.3 label | **2.5.2 / code273** | `deepseek 273.apk` / `a6d025c14c98118a5a67849e04aaef6f3c9c13aaf0fa62caaee0e47091374ea3` | Label/manifest mismatch; excluded from trusted matching | Reject as 2.7.3; request correct 2.7.3 APK or verify intended build identity |
| 2.7.4 | 2.7.4 / code294 | APKM base `component-0-base.apk` SHA-256 `2e898602374ed75f53d8e4c162fa625f5f4e1793b16fbd567f6dd02f6277a709`; container SHA-256 `8c3f9665e81c4c7bb76547f70c4d5e52ccc31d981e66c8121ad9f218b646f1ae` | Identity verified; 28 APK components, 4 DEX | Treat as split install set; scan base + required ABI/config splits, not just APKM container |
| 2.7.5 | 2.7.5 / code295 | `DeepSeek_2.7.5.apk` / `ddc52ef868b991fd33e634de834a3e2c022b0cd7ff9e854f4f141996aaeadb70` | Identity verified; 3 DEX | Static-only until its own reviewed runtime adapter and device E2E exist |

Audit scale recorded by `apps/audit/universal-hook/universal-hook-report.md`:

| Label | Classes | Methods | Call edges | Candidate methods |
|---|---:|---:|---:|---:|
| 2.7.1 | 14,906 | 71,243 | 453,718 | 2,233 |
| 2.7.2 | 15,055 | 72,258 | 462,894 | 2,263 |
| 2.7.3 label (actually 2.5.2) | 13,043 | 64,557 | 400,184 | 1,886 |
| 2.7.4 | 15,370 | 73,611 | 472,017 | 2,283 |
| 2.7.5 | 15,054 | 72,257 | 463,096 | 2,262 |

The same audit explicitly reports zero runtime-confirmed candidates and zero Hook-authorized candidates for every row. Candidate count is search scope, not confidence or support status.

## Static native transport evidence already available

### 2.7.5 / code295

The `apps/README.md` sections 19–20 and 23.1 record this static chain:

```text
nw3.e(yu1, Long)
  -> b70 (Flow wrapper)
  -> Ktor request execution
  -> g0.z (HTTP response handler, coroutine branch e=1)
  -> vs1.z (SSE parser, coroutine branch e=0)
  -> Ktor ByteReadChannel kw0
  -> qp0 (line reader)
  -> bia(eventName, data)
  -> t5.a (adapter, branch e=7)
  -> ev1(bia)
  -> r3.c(fv1)
  -> DeltaCommand / named-event dispatch
```

The UI/business delta path is separately recorded as:

```text
gx1.b (message submit orchestration)
  -> rr2 stream orchestration / auto-resume
  -> gr2
  -> pv1.j
  -> pv1.l
  -> ot1 collector
  -> pv1.a(event envelope)
  -> pv1.e(DeltaCommand / fragment)
  -> UI message state
```

Important role distinctions:
- `vs1.z` is the 2.7.5 raw SSE parser; `ot1.z` is a Flow collector coroutine state machine.
- `l5.z` branch e=8 is created from `nw3.e` for request execution; it is not the raw parser.
- `rr2.h` is identified by Kotlin metadata as `executeAutoResumeStreamFlow`.
- `iv1` records SSE lifecycle events: `sse_request`, `sse_created`, `sse_ready`, `sse_first_delta`, `sse_finished`, `sse_disconnected`, `sse_auto_resume`.
- HTTP non-success response is represented as structured error `dv1(gt9)`; it must not be decoded as normal SSE deltas.
- SSE envelope `bia` contains event name/data; `ev1` wraps it; `cv1` is heartbeat/connection state.
- `ready`, `finish`/default delta, `toast`, `hint`, `title`, `update_session`, `update_file`, `update_parent_message`, `close`, `check_client_update`, and `auto_tts_capability` are named/unnamed event paths documented in the audit.

DeltaCommand evidence:
- The polymorphic DeltaCommand is documented as fields `p`, `o`, `v`.
- It enters fragment processing; known result fragment families include ToolOpenFragment, ToolFindFragment, SearchFragment and reference models.
- The audit supports client-side receipt/merge/render of structured tool-result fragments. It does **not** establish an Android local ToolExecutor or a standard OpenAI `tool_calls` contract.
- The exact SET / APPEND / BATCH operation semantics must be derived from the version-local serializer and reducer call sites. Do not translate RFC 6902 operations into these host commands by assumption.

### 2.7.3-labelled input (manifest 2.5.2 / code273)

The apps README sections 23.2–23.4 records a distinct static chain for that APK:

```text
hx2.g(u01, Long)
  -> z40 (Flow wrapper)
  -> e0.A (HTTP response handler, branch e=1)
  -> jz0.A (SSE parser, branch e=0)
  -> Ktor ByteReadChannel zq0
  -> fl0 (line reader)
  -> gw8(eventName, data)
  -> g5.a (adapter, branch e=5)
  -> a11(gw8)
  -> ix
  -> k11.a(k11, gw8, Continuation)
  -> DeltaCommand / named-event dispatch
```

This is useful comparative evidence for the APK whose manifest is 2.5.2/code273, but it is **not** proof of DeepSeek 2.7.3. It must not be registered under the 2.7.3 semantic version.

The audit describes custom SSE parsing: `event:` and `data:` lines are handled; no evidence for `id:`, `retry:`, comment handling, or multiline data joining. Do not advertise full generic SSE compliance.

### 2.7.1 / code287

The existing Deekseep-Custom `HostV271` whitelist has stable-anchor evidence for request DTO `sx1` / serializer `qx1` / marker `du1`, history response `ey1`, static message `gy`, and fragments field `v`. This is not yet a proven Native Completion transport profile. Do not equate the existence of a request DTO with a verified Flow / collector / event reducer path.

### 2.7.4 / code294

The apps audit confirms the 2.7.4 identity and a 28-component APKM install set with 4 DEX. The present shared README does not publish an equally detailed 2.7.4 request → response handler → SSE parser → collector → DeltaCommand chain. Use `audit/2.7.4/{summary.json,hook-candidates.json,classes.jsonl.gz,methods.jsonl.gz,call-edges.jsonl.gz,interprocedural-value-edges.jsonl.gz}` as source evidence for a dedicated analysis. Do not borrow 2.7.5 class names or treat a static candidate as verified.

## Adapter architecture and gating consequences

1. Keep semantic operations separate from version-specific wire decoding:
   `HostVersionIdentity -> VersionProfile -> RequestDecoder -> FlowLifecycle -> EventEnvelopeDecoder -> DeltaCommandDecoder -> existing NativeJsonPatchReducer -> ResponseMapper`.
2. The existing `NativeJsonPatchReducer` remains the sole JSON-patch reducer. Do not create a parallel RFC 6902 implementation.
3. Add version-local DeltaCommand decoders only after identifying serializer descriptors, polymorphic discriminator values, and operation semantics from that APK. Unknown operation/type/value => fail-closed.
4. Extend existing `NativeFlowLifecycle`; do not duplicate it. Preserve request/Flow/collector/continuation identity, terminal state, cancellation, reconnect and auto-resume boundaries per host version.
5. Response mapping must distinguish ready metadata, delta fragments, named events, structured HTTP errors, close/finish, usage/model metadata, and incomplete/failed streams.
6. Profiles must be keyed by package + manifest versionName + versionCode + signing certificate + APK/base DEX digest + distribution. Version label alone is insufficient.
7. Each profile state is one of `DISCOVERED`, `STATIC_VERIFIED`, `RUNTIME_VERIFIED`, `E2E_VERIFIED`. Only `E2E_VERIFIED` enables Native Completion.
8. Until true device E2E, the Open Local API remains reachable but every completion request reports `engine_missing`.

## Immediate next tasks for PR #24

- Correct the compatibility matrix: 2.7.1/287, 2.7.2/289, 2.7.4/294, 2.7.5/295; flag 2.7.3 as blocked by wrong APK identity (manifest 2.5.2/code273).
- Build 2.7.5/code295 decoder/collector/mapper from the evidence above as a separate profile, not a code289 remap.
- Finish code289 DeltaCommand serializer and operation mapping from its own dynamic/static evidence; keep runtime observer code289-specific until other profiles independently pass.
- Analyze 2.7.4 code294 split set for the full request/response/SSE/DeltaCommand path.
- Reconfirm 2.7.1 code287 transport/Flow lifecycle beyond the current DTO whitelist.
- Add per-profile fixture tests: known event names, all known discriminator values, malformed/missing fields, unknown operations, terminal/cancel/reconnect paths, structured HTTP errors, and response mapping.
- Do not change `engine_missing` until each target distribution has a device E2E record.

## Evidence source paths

- `qingjian0/apps/README.md` sections 8, 19–25.
- `qingjian0/apps/audit/universal-hook/universal-hook-report.md` (APK identity matrix and static-candidate authorization policy).
- `qingjian0/apps/audit/universal-hook/version-identity.json` and `version-summary.json`.
- `qingjian0/apps/audit/2.7.1/summary.json`
- `qingjian0/apps/audit/2.7.2/summary.json`
- `qingjian0/apps/audit/2.7.3/summary.json` (manifest mismatch; do not label as 2.7.3)
- `qingjian0/apps/audit/2.7.4/summary.json`
- `qingjian0/apps/audit/2.7.5/summary.json`


## 2.7.5 / code295 isolated profile implementation

The source now contains `HostV275`, a dedicated evidence catalog for the audited mainland APK. It records the exact package/version identity, APK digest, signer digest, and all three DEX digests. The catalog also records the statically traced role chain:

```text
nw3.e(yu1, Long)
  -> b70 Flow wrapper
  -> g0 HTTP response handler
  -> vs1 SSE parser
  -> qp0 line reader
  -> bia SSE envelope
  -> t5 event adapter
  -> ev1 event wrapper
  -> r3 DeltaCommand dispatcher
  -> pv1 / ot1 UI collector path
  -> rr2 auto-resume orchestration
  -> iv1 lifecycle events
```

This implementation is intentionally marked `STATIC_VERIFIED`. The profile's capability methods return false for runtime hooks and Native Completion. `HostCompat` uses the isolated marker catalog only for static-generation recognition; it does not activate legacy symbol remapping or feature hooks. The regression runner `module/test-native-v275-profile.sh` asserts the version identity, APK/DEX digest inventory, key chain roles, and disabled capability gates, and is included in the PR regression workflow.

Remaining blockers before a real 2.7.5 adapter can be enabled:
- Runtime confirmation that the inspected mainland APK is the installed host, including package, versionName, versionCode, signer and APK/DEX identity.
- Runtime observation of the real request object entering `nw3.e`, and confirmation of the returned Flow and its collector/continuation lifecycle.
- Exact serializer/discriminator and field semantics for DeltaCommand operations (`p`, `o`, `v`); no SET/APPEND/BATCH semantics are inferred from names.
- Runtime-verified cancellation, finish/close, reconnect/auto-resume and structured HTTP error behavior.
- Device E2E evidence for streaming and non-streaming completion.

Until these are independently verified, 2.7.5 remains native-disabled and Local API completion remains `engine_missing`. This is a completed static profile handoff, not a claim of a completed native execution adapter.

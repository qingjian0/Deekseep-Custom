# DeepSeek 2.7.5 / code295 DeltaCommand protocol

Status: **STATIC_VERIFIED**. This document records APK-derived serializer and reducer behavior. It does not claim runtime or end-to-end verification and does not authorize a Hook.

## 1. Evidence identity

| Field | Value |
|---|---|
| Package | `com.deepseek.chat` |
| Version | `2.7.5` |
| Version code | `295` |
| Distribution | Mainland |
| APK SHA-256 | `ddc52ef868b991fd33e634de834a3e2c022b0cd7ff9e854f4f141996aaeadb70` |
| Signing certificate SHA-256 | `c6893c5368f7b5122fecdafc6c4ecf49e0b3dc4e42bd166cdb646907788c7ffa` |

Source APK audit: `qingjian0/apps`, `audit/2.7.5/summary.json`, artifact from workflow `42335918440`. The corresponding JADX source bundle was downloaded from artifact `deepseek-275-static-evidence` and inspected locally. JADX exited with code 3 and reported 344 decompilation errors; conclusions below are limited to the inspected methods that decompiled coherently.

## 2. Wire schema: DeltaCommand

The serializer `ut3` declares the fully-qualified model name:

```text
com.deepseek.chat.common.protocol.DeltaCommand
```

It declares exactly three fields:

| Wire field | Model field | Serializer | Presence |
|---|---|---|---|
| `p` | `wt3.a` | String (`gja`) | Optional |
| `o` | `wt3.b` | Operation (`f48`, serialized by `w28`) | Optional |
| `v` | `wt3.c` | `kotlinx.serialization.json.JsonElement` (`h26`) | Required |

Evidence:
- `wt3.java`: model fields and constructor required-bit check; bit 4 must be present for `v`.
- `ut3.java`: serializer descriptor, field order, field serializers, decode and encode paths.
- `f48.java` and `w28.java`: Operation is a string value object. It is not a closed Java/Kotlin enum.
- `h26.java` / `k26.java`: JsonElement hierarchy; JSON primitive, object and array values are represented by `m46`, `f46`, and `n16`.

Therefore `v` is not restricted to a string or an RFC 6902 operation object. Its shape depends on the target path and operation.

## 3. Operation semantics recovered from the reducer

The central reducer is `l83.H(x13, yq1, iv1, y13)`. Its inspected implementation resolves the path, dispatches the operation, and then marks selected UI/message state as changed.

### SET

```text
target.l(lastPathSegment, value, lifecycleContext)
```

This invokes the target model's field-specific setter. It is not a generic JSON object replacement operation.

Confirmed examples:
- Root `response/fragments`: replace the fragment list (`ky.l("fragments", ...)`).
- `RESPONSE/content`: replace response content (`y44.l`).
- `THINK/content`: replace reasoning content (`a54.l`).
- `SEARCH/status|queries|content|results`: update the corresponding Search fragment fields (`z44.l`).
- `TOOL_OPEN/status|content|result|reference`: update ToolOpen fragment fields (`e54.l`).

### APPEND

```text
target.m(value, lastPathSegment)
```

This is also target-model-specific; it is not equivalent to RFC 6902 `add`.

Confirmed examples:
- `RESPONSE/content`: concatenate the new string to the existing content (`y44.m`).
- `THINK/content`: concatenate the new string (`a54.m`).
- `RESPONSE/references` and `THINK/references`: append decoded references.
- `SEARCH/results`: append decoded result entries (`z44.m`).
- Root `response/fragments`: append decoded fragment models (`ky.m`).
- Fragment-list append is implemented by `jy1.m`, which decodes and appends fragment models.

### BATCH

The reducer:
1. Resolves the command path to a nested `x13` target.
2. Decodes `v` as a list of `DeltaCommand` (`wt3`) values.
3. Applies each child command recursively against that resolved target.
4. Returns false if any child application fails.

A BATCH child path is relative to the BATCH target, not automatically a root-level RFC 6901 JSON Pointer. The inspected host loop continues iterating children after a failed child; this adapter deliberately rejects a malformed/unsupported batch before exposing it for application, to avoid partial mutation in the new implementation.

## 4. Path resolution

The reducer splits `p` on `/` and traverses intermediate segments through the host's `x13.c(segment, lifecycleContext, changeFlags)` contract.

The relevant root chain is:

```text
ida.c("response")
  -> ky (message root)
  -> ky.c("fragments")
  -> jy1 (fragment list)
  -> jy1.c(index)
  -> concrete message fragment
  -> field-specific x13 setter/appender
```

`jy1.c` resolves numeric indices and also supports negative indices relative to the end of the fragment list. This is host path behavior; it is not JSON Pointer's `~0` / `~1` escaping or array-index grammar.

## 5. Stream bootstrap and stateful omission rules

The first DeltaCommand in a collected stream is a **snapshot bootstrap**, not a patch:
- `pv1.u` starts at `-1`; the event path increments it before calling `pv1.e`, so the first command enters the `i == 0` branch.
- That branch calls `coc.c0(wt3.v)`, which decodes `v` with the `SseMessageRoot` serializer (`gda`). The root serializer requires a `response` field.
- The first command's `p` and `o` are deserialized as part of `wt3`, but they are not applied as a patch in this branch.
- `coc.c0` resets the remembered path to empty and operation to `SET`.

Only subsequent DeltaCommands enter `coc.W(wt3, iv1)` and apply the carry-forward rules before calling `l83.H`:

- After snapshot bootstrap, initial path is the empty string.
- After snapshot bootstrap, initial operation is `SET`.
- If `p` is absent on a later command, the previous path is reused.
- If `o` is absent, the previous operation is reused.
- The resulting path and operation are stored for the next top-level DeltaCommand in that stream.

For `BATCH`, the host initializes a separate local path to empty and operation to `SET`. Within that batch, omitted child `p` / `o` inherit from the previous child command. The batch-local carry-forward does not overwrite the outer stream's carry-forward state.

This is why the decoder takes an explicit per-stream `State`; a stateless parse of each event would be incorrect.

## 6. Error and change semantics

- Only `SET`, `APPEND`, and `BATCH` are handled by the inspected reducer. An unknown operation returns false.
- An unresolved intermediate path returns false.
- Deserialization failures are caught by the host stream path and reported as delta parse errors where lifecycle metadata is available.
- The reducer marks separate change flags for fragment-list updates, response/think content updates, and selected search/tool-search updates. These flags are UI invalidation signals, not additional wire operations.
- No evidence supports interpreting this protocol as RFC 6902. Do not map `SET` to `replace`, `APPEND` to `add`, or `BATCH` to an RFC patch array by name alone.

## 7. Adapter implementation in Deekseep-Custom

`NativeV275DeltaCommandDecoder` now decodes a parsed JSON object represented as a Java `Map` into an immutable command tree. It:
- recognizes the first command as a full `SseMessageRoot` snapshot (`v.response`), not as a path patch;
- preserves the exact `p` / `o` / `v` field contract for subsequent deltas;
- maintains top-level per-stream path/operation carry-forward;
- gives each BATCH its own default path/operation state and carries fields between its children;
- accepts JSON null as a present `v` value;
- rejects missing `v`, invalid field types, unknown operations, malformed BATCH values and malformed children;
- enforces defensive adapter limits (4,096 path characters, 16 nested BATCH levels, 4,096 commands). These limits are adapter safety limits, not claimed host limits.

The decoder does **not** invoke host methods, mutate a host message model, install Hooks, or enable Native Completion. It intentionally does not call `NativeJsonPatchReducer`: that class implements RFC 6902 and is a separate protocol.

The text/reasoning mapper `NativeV275DeltaMapper` now tracks the fragment list and maps only `RESPONSE/content` to public text and `THINK/content` to reasoning. It honors host-confirmed `SET` replacement, `APPEND` string concatenation, fragment-list replacement/append, negative fragment indices, and BATCH-relative paths. Other fragment types (ToolOpen, ToolFind, Search, and unknown types) do not leak into text or reasoning. Mapper batches are applied to a copy and committed only on full success, avoiding partial output mutation.

Regression coverage:
- `module/tests/nativev275/ds/dsh/DeepSeek/NativeV275DeltaCommandDecoderTest.java`
- `module/tests/nativev275/ds/dsh/DeepSeek/NativeV275DeltaMapperTest.java`
- Both run through `module/test-native-v275-delta-decoder.sh`.

## 8. Remaining work before Native Completion

1. Add a version-specific path/fragment mapper for the exact 2.7.5 message model, including `ready` message identity and fragment IDs.
2. Prove how `RESPONSE` and `THINK` fragments map to public text and reasoning output, including multiple fragments and interleaving.
3. Add tests from captured, sanitized 2.7.5 event fixtures; do not invent fixtures from assumed field names.
4. Verify `BATCH` ordering and failure behavior against the actual host model semantics.
5. Observe the exact runtime event and collector lifecycle on a device running the APK with the matching package/version/signer identity.
6. Run streaming and non-streaming E2E tests before enabling the feature.

Current state remains `STATIC_VERIFIED`; runtime hooks and Native Completion remain disabled. Local API continues to report `engine_missing` until a real engine adapter passes its gates.

## 9. Source inventory

The evidence was recovered from the 2.7.5 JADX source bundle, including:

- `defpackage/wt3.java`, `ut3.java`, `f48.java`, `w28.java`, `h26.java`, `k26.java`
- `defpackage/coc.java` (`W`, `c0`), `l83.java` (`H`)
- `defpackage/ida.java`, `ky.java`, `jy1.java`
- `defpackage/y44.java` (Response), `a54.java` (Think), `z44.java` (Search), `e54.java` (ToolOpen)
- `defpackage/pv1.java`, `r3.java`, `bia.java`, `ev1.java`

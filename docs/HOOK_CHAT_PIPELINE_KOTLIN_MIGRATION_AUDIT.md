# HookChatPipeline / Conversation Relay Kotlin Migration Audit

> Scope: source-level audit of `refactor/kotlin-hook-architecture` at the current branch tip. This is a migration design record, not a claim that the code has been migrated or device-validated.

## Executive decision

Do not translate `HookChatPipeline.java` or `z17.java` wholesale. Preserve the existing Java/Xposed interception boundary and migrate deterministic policy/model seams first. The chat pipeline is a host-integration subsystem, not a self-contained business class.

## Source findings

### HookChatPipeline

- The class is explicitly shared by the domestic and Google Play Universal artifacts.
- Its hook methods use the project's around-hook contract: `Main.MODULE.hook(...).intercept(Hooker)`, `Chain.proceed()`, argument/result preservation, and host-side reflective objects.
- It reaches deeply into process-wide `Main` state (thread-locals, live host managers, logging, reflection helpers, and feature state).
- It uses `HostCompat` / `HostRemap` and version-specific field/method names. The 2.7.x host remap layer is an execution-critical compatibility boundary.
- The source contains request interception, prompt/edit-resend repair, transport wrapping, Flow registration, relay gating, cancellation, history/message inspection, and Local API/content-filter behavior. Those concerns should not be moved together.
- The Flow registration path deliberately returns the host's original Flow instance. Replacing it with a Proxy can cause the host's declared return-type cast to fail. Preserve this invariant.
- Hook installation catches/logs errors in some feature-specific paths; a Kotlin port must not silently turn partial registration into a successful registry result.

### z17 conversation relay

- This is a smaller but still Android-host-bound implementation: configuration is stored under the DeepSeek app's private files directory, enabled state is a flag file, and configuration is represented by `org.json.JSONObject`.
- Network transport uses `HttpURLConnection`, with distinct OpenAI-compatible and Anthropic request headers/endpoints.
- Streaming is parsed as SSE. OpenAI deltas separate `reasoning_content` from `content`; Anthropic uses its event stream shape. Both feed the host thinking panel through a two-channel result.
- The current class combines persistence, endpoint construction, request serialization, transport, SSE parsing, error conversion, and model discovery. These are separable responsibilities.
- Error handling is broad and returns a relay result instead of allowing transport failures to escape into the host hook path. Preserve that boundary unless a regression test explicitly changes it.

## Kotlin migration classification

| Area | Classification | Migration approach |
|---|---|---|
| Relay result / immutable config model | Safe first Kotlin seam | Move to shared runtime only if Java ABI and serialized field names remain compatible; otherwise keep Android-facing adapter in module |
| Endpoint normalization and request/header policy | Pure policy | Kotlin JVM unit tests; no Android or Xposed types |
| SSE framing and event classification | Pure policy with injected JSON decoder | Test chunk boundaries, CRLF, comments, multiple data lines, DONE, malformed events, reasoning/content separation; keep JSON decoding at Android adapter until dependency decision |
| HTTP transport and timeouts | Android/runtime integration | Separate transport interface; preserve current timeouts and disconnect/close behavior |
| File persistence | Android integration | Keep exact paths and JSON keys; atomic replacement and failure semantics require regression tests |
| Request/transport Hook entry points | Xposed boundary | Keep Java initially; do not change callback order, argument identity, original invocation, or return object |
| Host symbol resolution / remapping | Critical compatibility boundary | Keep Java and host tables untouched during first Kotlin phases |
| Flow registration / collection interception | High-risk host integration | Keep Java until dedicated host-version regression and real-device evidence exist |
| Prompt injection / edit-resend repair | Mixed policy + host integration | Extract string policy only after tests; keep reflective field mutation at Java boundary |
| Content-filter / Local API cancellation | Mixed policy + lifecycle | Separate policy from reflective cancellation; retain session-scoped gating and consent checks |

## Recommended execution order

1. **P2a — Relay contracts:** add Kotlin immutable models and pure endpoint/request policy with JVM tests. Keep `z17` as the compatibility facade and preserve all static Java-callable methods.
2. **P2b — SSE parser:** introduce a decoder-independent SSE framing layer and test arbitrary network chunk boundaries. Keep JSON decoding and provider-specific event extraction behind an adapter.
3. **P2c — transport adapter:** move HTTP work only after tests cover headers, endpoint paths, timeout values, non-200 error truncation, stream closure, disconnect, and cancellation.
4. **P2d — Hook integration:** leave `HookChatPipeline` Java. Extract one deterministic policy at a time; do not move `Hooker`, `Chain`, `Method`, `ClassLoader`, host objects, or Flow lifecycle into the shared JVM runtime.
5. **P3+ — Hook orchestration:** only consider Kotlin hook orchestration after the Legacy adapter regression matrix proves callback order, exactly-once original invocation, exception propagation, and retry behavior.

## Non-negotiable compatibility checks

- Host package remains `com.deepseek.chat`; module identity remains `ds.dsh.DeepSeek`.
- Keep all host version gates and HostRemap tables unchanged during policy extraction.
- Preserve original Flow identity and the existing request-to-Flow association semantics.
- Preserve prompt wrapping, edit/resend repair conditions, model/file association, reasoning/content separation, and the exact OpenAI/Anthropic wire contracts.
- Do not change preference keys, file paths, JSON keys, defaults, timeouts, or error semantics as part of a language migration.
- Run the existing Legacy Xposed API 82–102 adapter regression and the relevant relay/host-map tests after each integration change.
- A JVM test or CI build does not establish LSPosed injection or real DeepSeek host behavior; those remain device-validation items.

## Implementation progress (2026-11-01)

- P2a endpoint/header policy is implemented in `ConversationRelayEndpointPolicy.kt`; the legacy `z17` adapter delegates endpoint selection, authentication headers, default model selection, and timeout constants to it.
- P2a immutable `ConversationRelayConfig` exists as a Kotlin contract. The legacy JSON file read/write adapter remains Java and retains its original path and keys.
- P2b line framing is implemented in `ConversationRelaySsePolicy.kt`. It intentionally retains the legacy exact `data: ` prefix and Java `String.trim()` semantics.
- P2b provider event routing is implemented in `ConversationRelayProviderEventPolicy.kt`; OpenAI reasoning/content and Anthropic thinking/text event classification are delegated from the Java adapter.
- JVM tests cover endpoint/header behavior, Java trim compatibility, SSE line framing, OpenAI field routing, and Anthropic block/delta routing.
- CI includes the `module-kotlin-runtime test` task. The GitHub connector has not returned a workflow run for the latest adapter commit, so CI status is not claimed as passing.

Remaining P2b work: integration-level tests for the Android `org.json` decoding adapter and stream lifecycle; preserve JSON decoding and `HttpURLConnection` ownership at the Android boundary.

## Current status

### Implementation progress

- **P2a completed in source:** added `ConversationRelayConfig` and `ConversationRelayEndpointPolicy` to the standalone Kotlin runtime; the existing Java `z17` facade delegates endpoint selection, provider headers, format normalization, model fallback and timeout constants to the Kotlin policy. File paths, JSON keys, provider routes, auth headers and timeout values remain unchanged.
- Added JVM regression coverage for OpenAI/Anthropic endpoint and header contracts, model fallback, API-key redaction, and Java `String.trim()` compatibility (including NBSP behavior).
- **P2b provider policy is now connected:** `ConversationRelayProviderEventPolicy` classifies OpenAI reasoning/content fields and Anthropic thinking/text block-start and delta events. The Java adapter retains `org.json` decoding and delegates classification to Kotlin.
- **P2b accumulation is now Kotlin-owned:** each response gets a separate `ConversationRelayStreamAccumulator`; reasoning and content remain isolated, values are appended verbatim, and the legacy `RelayResult` boundary retains final trimming.
- The Java `z17` SSE readers still own `BufferedReader.readLine()`, `HttpURLConnection`, disconnect, and stream closure. This deliberately preserves transport framing and Android ownership while moving deterministic policy and accumulation into the JVM runtime.
- Regression tests cover provider routing, unknown event rejection, empty values, independent channel ordering, and per-response accumulator isolation. A local Kotlin compiler smoke run passed for the policy and accumulator sources. Full Gradle/JUnit CI and Android integration have not yet been confirmed green.
- Remaining P2b work: exercise the real Java/Android `org.json` adapter and stream lifecycle, including malformed events, truncated streams, EOF without `[DONE]`, disconnect/exception cleanup, and actual provider fixtures. Keep JSON decoding in the Android adapter.

This remains a staged migration. Do not rewrite `HookChatPipeline` or move host reflection, Xposed callbacks, Flow identity, or HostRemap tables into the shared runtime.

## Follow-up implementation (2026-11-01)

- Added `ConversationRelayStreamAccumulator` to the standalone Kotlin runtime and routed both OpenAI and Anthropic stream readers through one per-response instance. Reasoning/content buffers are isolated; appended text is preserved verbatim until the existing `RelayResult` constructor applies its legacy trim.
- Added accumulator tests for interleaved chunks, empty/unknown values, whitespace preservation, and response-instance isolation.
- Hardened `z17.fetchModels` and `z17.relay` connection cleanup with `finally`-based `HttpURLConnection.disconnect()`. This covers setup, response-code, body-read, and parser failures while preserving existing HTTP status/error text and parser ownership. Duplicate disconnect after parser completion is intentional and safe for `HttpURLConnection`.
- Local validation: Kotlin policy/accumulator/SSE sources compiled with the installed Kotlin compiler; smoke assertions passed; a Java caller compiled and ran against the Kotlin accumulator (`@JvmStatic` policy + enum + accumulator interop). Full Gradle/JUnit, Android build, and device/provider integration remain unverified.

## P2c-1 HTTP response policy extraction (2026-11-02)

- Added `ConversationRelayHttpPolicy.kt` to the standalone Kotlin runtime. It owns only deterministic response rules: exact HTTP 200 success classification, 200-character error-body truncation, and the legacy `HTTP <code>[: body]` message format.
- Added `ConversationRelayHttpPolicyTest.kt` covering status values below/above 200, redirects, 2xx other than 200, 4xx/5xx, null/empty bodies, and 199/200/201-character truncation boundaries.
- Extended the regression case to preserve Java UTF-16 substring behavior at a surrogate-pair boundary and to prove error-body whitespace is not trimmed.
- The Java `z17.fetchModels` and `z17.relay` adapters now call the Kotlin policy. Request construction, Android `org.json` serialization/parsing, `HttpURLConnection`, streams, and SSE readers remain in Java.
- No request method, endpoint, header, timeout, payload key, provider routing, or host Hook behavior was changed.
- Local validation: standalone Kotlin 1.9 compilation and policy smoke assertions passed; a Java caller compiled and ran against the Kotlin static facade. This is a focused source-copy harness, not the repository Gradle/JUnit suite or Android build.
- GitHub Actions workflow-runs and combined-status queries for adapter commit `60b6cd0da6945dcd7dc6ff26d8a59e9629fa45a1` returned empty collections. CI is therefore unverified, not green. Android build, device injection, and live provider integration remain pending.

## P2c-2 model-list normalization extraction (2026-11-02)

- Added `ConversationRelayModelListPolicy.kt` and JVM tests. Kotlin now owns trimming, empty-ID filtering, first-seen deduplication, and stable result ordering after the Android JSON adapter extracts raw model IDs.
- Trimming explicitly matches Java `String.trim()` (characters <= U+0020), not Kotlin's broader default whitespace behavior. Deduplication remains case-sensitive.
- `z17.fetchModels` still owns `org.json` parsing and preserves the existing malformed-array-element behavior; the policy does not add lenient JSON handling.
- Local validation: Kotlin 1.9 source-copy smoke assertions and Java-to-Kotlin static-call interoperability both passed. Full Gradle/JUnit, Android build, and live model-discovery checks remain unverified.


## P2c-3 response-body stream ownership (2026-11-02)

- Added `ConversationRelayInputStreamReader` to the standalone Kotlin runtime. Its contract explicitly transfers ownership of a nullable input stream, returns an empty string for null, decodes UTF-8, and closes non-null streams on both success and read failure.
- Added JVM tests for UTF-8 success + closure, read failure + closure, close failure propagation, and null input.
- The Java `z17.readStream` compatibility adapter now delegates to the tested Kotlin helper. Existing callers, HTTP status handling, error formatting, JSON parsing, and connection `disconnect()` ownership remain unchanged.
- This extraction does not move `HttpURLConnection`, request construction, SSE framing, or Android `org.json` decoding into the shared runtime.
- Local validation: Kotlin compiler smoke assertions passed for UTF-8 decoding, close-on-success, close-on-read-failure, close-failure propagation, and null input; a Java caller compiled and ran against the Kotlin `@JvmStatic` facade. This is a focused source-copy harness, not the repository Gradle/JUnit suite. Full Gradle/JUnit, Android build, and CI remain unverified.


## P2c-4 relay fatal-error boundary (2026-11-02)

- Narrowed the network relay's outer failure conversion from `Throwable` to `Exception`. Recoverable transport, JSON, and runtime exceptions still become the existing `RelayResult` error; JVM `Error` types such as `OutOfMemoryError`, linkage failures, and `ThreadDeath` are no longer silently converted into ordinary relay failures.
- Narrowed per-SSE-event parse guards to `Exception`, so malformed JSON/events remain isolated without swallowing fatal VM errors.
- Reader cleanup now catches only `IOException`; connection disconnect remains in the existing `finally` path.
- No endpoint, header, payload, timeout, status, error-message, provider-routing, or Hook callback behavior was changed for ordinary exceptions.
- Validation: source-level diff review only; full Gradle/JUnit, Android build, and real provider/device tests remain unverified.

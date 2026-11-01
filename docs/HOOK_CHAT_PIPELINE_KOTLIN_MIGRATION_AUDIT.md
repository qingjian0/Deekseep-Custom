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
- **P2b started:** added `ConversationRelaySsePolicy` and routed both existing Java SSE consumers through it. The policy intentionally preserves the legacy exact `data: ` prefix and Java-trim semantics rather than broadening SSE parsing behavior.
- Local Kotlin/JVM smoke compilation and runtime assertions passed for the endpoint/config/SSE policy; a Java 8 static-call interop smoke test also passed. The full Gradle/JUnit task and GitHub Actions run have not yet reported green, so P2a is not considered CI-validated.
- Remaining P2b work is provider payload/event classification and arbitrary chunk-boundary tests. Keep JSON decoding in the Android adapter until a deliberate dependency-free parser seam is designed.

This remains a staged migration. Do not rewrite `HookChatPipeline` or move host reflection, Xposed callbacks, Flow identity, or HostRemap tables into the shared runtime.

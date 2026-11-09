# Local API live execution diagnostics

The Open Local API exposes authenticated, value-free request diagnostics:

- `GET /v1/diagnostics/requests` — recent requests (up to 24).
- `GET /v1/diagnostics/requests/{request_id}` — current snapshot and ordered stage events.

Use the same Bearer API key as `/v1/chat/completions`. Poll the recent-request index while a completion is in progress, then poll its request snapshot to observe new events. The API intentionally uses short JSON snapshots rather than a long-lived diagnostics SSE connection: the gateway has a small bounded client executor, and a long-lived diagnostics stream could starve actual completion requests. Normal completion SSE remains unchanged.

Each event includes a monotonic sequence within the retained event list, stage, status, safe error code, timestamp and elapsed milliseconds. Status values include `running`, `succeeded`, `failed`, `cancelled`, `timeout` and `skipped`. Stages are emitted when execution reaches them; stages that do not apply are not fabricated.

The current code295 native stages are:

1. `native_runtime` — captured runtime dependencies.
2. `pow` — host PoW generation.
3. `session_create` — temporary native chat session.
4. `request_build` — clone and populate the native request template.
5. `transport` — invoke the host native transport.
6. `flow_collect` — collect native response fragments.
7. `session_cleanup` — best-effort temporary session deletion.

The gateway reports the same high-level stages for OpenAI Chat Completions, OpenAI Responses and Anthropic Messages: HTTP validation, parameter validation, backend readiness, native execution and response delivery.

The module's floating Local API console polls these endpoints over authenticated loopback HTTP every 1.5 seconds while the panel is open. It displays the newest request, stage order, status, elapsed time and safe error code. Polling runs on a dedicated background executor, never on the Android UI thread. The overlay host gate recognizes mainland DeepSeek 2.7.5/code295. The module manifest grants Internet access for this loopback diagnostics client; the UI does not send diagnostics traffic to a remote host.

A request snapshot also includes `prompt_chars` and `message_count`. These are shape metadata only; prompt content is never included.

Diagnostics are bounded to 64 recent requests and 128 events per request. Details are intentionally value-free: prompt text, request bodies, API key values, authorization headers, cookies, session IDs, PoW values and native response payloads must never be recorded. Authentication is represented only by success/failure stage state. Do not add raw secret logging to the diagnostics UI or export path. A successful HTTP listener or a successful diagnostics response does not imply that the native completion runtime is ready; inspect the `native_runtime` stage and its error code.

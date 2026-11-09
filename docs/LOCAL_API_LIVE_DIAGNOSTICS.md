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

The gateway also reports HTTP validation, parameter validation, backend readiness and response delivery.

Diagnostics are bounded to 64 recent requests and 128 events per request. Details are intentionally value-free: prompts, request bodies, authorization headers, cookies, session IDs, PoW values and native response payloads must never be recorded. A successful HTTP listener or a successful diagnostics response does not imply that the native completion runtime is ready; inspect the `native_runtime` stage and its error code.

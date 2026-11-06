# Code289 native adapter — evidence and blocker register

Date: 2026-11-06  
Target: Official DeepSeek Android mainland 2.7.2 / code289-cn  
Branch: work/local-api-hybrid-api  
Status: Investigation only; native generation remains fail-closed.

## Verified source facts

1. Main.startz1() registers the z2.Backend implementation.
   - isReady() requires BuildInfo.LOCAL_API_INCLUDED, hostClassLoader, liveR92, and liveQ71.
   - Open edition without the protected Local API payload reports that its native adapter is not connected.
   - complete() explicitly returns HTTP 503 / native_adapter_unavailable for Open edition without that payload.
2. HookAccountLoginSecurity.executeLocalApiCompletion() delegates to the encrypted ds.dsh.DeepSeek.z18 payload class through z14.payloadClass(...).
   - That is the protected-build execution engine, not an Open-edition implementation.
   - The Open path now rejects before attempting the z18 lookup, preventing a misleading engine_missing error.
3. The passive code289 probe is explicitly gated to Open edition, code 289, and the diagnostic switch.
   - It observes the ly1 request object at zn3.o(wu1, Long) and jw3.g(wu1, Long) boundaries.
   - It records method descriptors, runtime class names, declared field names/types, and callback method signatures.
   - It does not invoke candidate methods, read request field values, serialize payloads, or collect credentials/cookies/message content.
   - Callback observation occurs at the y60 continuation boundary; it is not proof of event semantics.

## Known candidate signatures (leads, not an invocation contract)

| Role | Candidate | Evidence | Not yet proven |
|---|---|---|---|
| Request implementation | ly1 implements wu1 | DEX/runtime type inventory | Constructor/factory and field mapping |
| Dispatch | zn3.o(wu1, Long) -> y60 | Structural signature and passive call observation | Whether this is the correct externally callable entry point |
| Lower transport | jw3.g(wu1, Long) -> y60 | Structural signature and passive call observation | Whether direct invocation bypasses required setup |
| Return/continuation | y60 implements el4; y60.b(fl4, yb3) -> Object | Runtime class/interface/method metadata | Flow collection contract and terminal/error semantics |

## Explicit blockers

- Exact request factory/constructor and required fields.
- Challenge/PoW acquisition, binding, and response validation.
- Whether zn3.o or jw3.g is the correct call site and the exact second-argument meaning.
- Flow collector interface, coroutine context, and suspension/resumption behavior.
- Callback/event DTO semantics for text, reasoning, tools, completion, and errors.
- Cancellation propagation, timeout behavior, cleanup, and session isolation.
- A genuine successful code289 device completion through the proposed adapter.

## Implementation rules

- Do not instantiate ly1, zn3, or jw3 from guessed constructors.
- Do not call zn3.o or jw3.g until challenge/request preparation and the Flow collector are verified.
- Do not infer event meaning from DTO names or obfuscated fields.
- Do not route Open edition through protected-only z18.
- Do not enable functional hooks from static analysis alone.
- Unknown host versions remain read-only; diagnostic probes remain disabled by default.
- Keep the existing z2.Backend contract and implement the native adapter behind it.

## Next evidence-driven tasks

1. Extract exact ly1 constructor/factory and field assignments from the code289 APK.
2. Trace the official host call stack that creates the request and starts challenge/PoW.
3. Trace y60 collection and continuation callbacks from an ordinary user-initiated generation.
4. Correlate callback types with actual visible text/reasoning/terminal/error outcomes, without logging payload contents.
5. Implement the adapter only after tasks 1–4 are supported by APK and device evidence; then verify cancellation and a real response.

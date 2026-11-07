# ROI Universal Hook Runtime — R1–R9

## Delivery status

The universal Hook runtime now has a production integration path into the repository's existing legacy Xposed callback chain and feature-install lifecycle. It is still an opt-in API: this change does not silently migrate or replace the established feature Hook catalog, and no new host symbol is automatically activated.

| Stage | Implemented | Remaining boundary |
|---|---|---|
| R1 — evidence authorization | `RoiHookResolverActivationGate` delegates to `RoiHookResolver.resolve`; requires an evidence candidate and policy for each exact spec; missing data and identity mismatch deny activation | A production evidence source must supply the verified candidate for each proposed spec; discovery output is not itself authorization |
| R2 — invocation contract | Universal callbacks are adapted through the existing `LegacyXposedModule.HookTarget` chain, preserving its around-call ordering, argument replacement, original-call and throwable handling | No real-device correlation, concurrency or coroutine/cancellation acceptance yet |
| R3 — HookSpec | Immutable spec includes role, owner, method/constructor, exact parameter and return types, fingerprint, dependencies, priority and required/optional status | Host-specific specs still require reviewed evidence |
| R4 — resolver | Exact ClassLoader-scoped reflection resolution without class initialization | No cross-loader cache; host mapping must still be validated |
| R5 — lifecycle | Runtime tracks active specs, idempotency and rollback; Main exposes group-level logical uninstall and the shared install registry supports explicit uninstall/reinstall transitions | Logical unhook does not physically remove the shared native Xposed callback, because doing so could remove other feature callbacks on the same Member |
| R6 — batch install | Resolve-before-install, dependency ordering, member conflict checks and reverse rollback | A native registration failure after Xposed callback publication remains fail-closed, matching the existing adapter's safety contract |
| R7 — health | Main lifecycle entry point requires a non-null handle and successful registration in the shared callback chain | This is registration health, not proof of correct host behavior |
| R8 — adapter allowlist | Exact fingerprint + semantic role allowlist; unknown fingerprints denied | Allowlist entries must be reviewed and version-scoped |
| R9 — validation | Java 8 JVM runtime tests, resolver-gate tests and an isolated Hook workflow | JVM/CI success does not equal Android/Xposed or L5 device acceptance |

## Production flow

`Main.installRoiUniversalHookBatch` -> existing `FeatureHookInstallRegistry` -> universal `Installer` -> `RoiHookResolverActivationGate` -> exact spec/descriptor identity check -> `RoiHookResolver` evidence decision -> `LegacyXposedModule.universalHookBackend` -> existing per-Member `HookTarget` chain -> registration health -> ACTIVE.

The backend intentionally does not call `XposedBridge.hookMethod` itself. The legacy adapter owns one native Xposed callback per Member and composes all logical interceptors behind it. This avoids creating a competing callback path.

## Uninstall semantics

A universal handle's `unhook()` disables its logical interceptor. The shared native Xposed callback remains registered for the process lifetime because the same callback chain may also contain unrelated legacy features. The runtime therefore guarantees logical deactivation, not native callback removal. Main.uninstallRoiUniversalHookBatch(groupId) deactivates the group's handles in reverse install order and verifies that the runtime has no remaining active handles before transitioning the shared registry entry to UNINSTALLED. A failed deactivation leaves the registry entry installed and returns false; callers must not assume the group is gone. The same stable group ID may be installed again after a successful transition. The installer and its evidence/policy sources remain bound to that group ID for the process lifetime, so changing the source contract requires a new group ID. A future physical-unhook API would require reference-counted ownership of the entire Member chain and must not remove other features' hooks.

## Activation contract

Callers must provide:
- a version-scoped `Adapters` allowlist for the exact fingerprint and semantic role;
- an `EvidenceSource` that returns the candidate for the exact spec;
- a `PolicySource` whose policy explicitly enables the role.

The gate verifies fingerprint, role, class, method, parameter descriptors and return descriptor before asking `RoiHookResolver`. Any missing candidate, missing policy, identity mismatch, contradictory/incomplete evidence or discovery-only policy is denied before the backend is called.

No default evidence provider or automatic role policy is enabled by this change. This preserves the existing unknown-host and feature-activation fail-closed behavior.

## Test

Run `bash module/test-roi-universal-hook-runtime.sh` for resolver/runtime and evidence-gate cases. Run `bash module-legacy/test-adapter-regression.sh` for the 21-version Xposed API 82–102 matrix plus a concrete universal-backend test that verifies one native callback, argument/result adaptation, exactly-once original invocation and logical unhook pass-through.


## Group lifecycle API

- `installRoiUniversalHookBatch(groupId, ...)` installs one stable group through `FeatureHookInstallRegistry`.
- `uninstallRoiUniversalHookBatch(groupId)` logically disables all handles owned by that group. It returns `true` only after the runtime reports zero active handles and the registry records `UNINSTALLED`.
- A successful uninstall permits reinstalling the same group ID. The registry attempt counter is retained for diagnostics.
- A failed unhook leaves the group registered as active/failed; do not retry by creating a second installer for the same group ID.
- This lifecycle API does not physically unregister the shared native Xposed callback.

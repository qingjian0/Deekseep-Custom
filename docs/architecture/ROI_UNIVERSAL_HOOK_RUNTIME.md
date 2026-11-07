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

The activation gate receives the exact `ClassLoader` that the installer will use, derives its process-local source scope through `RoiCandidateIdentity`, and rejects a candidate whose `sourceScope` belongs to another loader. It recomputes and verifies `candidateId` from the source scope, owner, method and full descriptor, then verifies fingerprint, role, class, method, parameter descriptors and return descriptor before asking `RoiHookResolver`. Any missing candidate, missing policy, identity mismatch, contradictory/incomplete evidence or discovery-only policy is denied before the backend is called.

No default evidence provider or automatic role policy is enabled by this change. This preserves the existing unknown-host and feature-activation fail-closed behavior.

## Test

Run `bash module/test-roi-universal-hook-runtime.sh` for resolver/runtime and evidence-gate cases. Run `bash module-legacy/test-adapter-regression.sh` for the 21-version Xposed API 82–102 matrix plus a concrete universal-backend test that verifies one native callback, argument/result adaptation, exactly-once original invocation and logical unhook pass-through.


## Activation identity hardening

The activation gate validates not only the candidate's fields but also its stable identity key. It recomputes `RoiCandidateIdentity.candidateId(sourceScope, className, methodName, descriptor)` and rejects a candidate if the stored `candidateId` differs. This prevents stale or forged candidate records from being accepted merely because their visible method fields happen to match the requested HookSpec.

The current DexKit discovery pipeline still emits unresolved, non-approved candidates and a discovery-only policy. It does not populate a production authorization catalog. `RoiReviewedHookEvidenceRegistry` is now the immutable, source-controlled catalog boundary: its builder accepts only exact HookSpec/candidate matches that already pass the resolver's complete evidence and activation policy. Empty catalogs are valid and deny all installs; no runtime preference, discovery report or remote response can add approval records. No reviewed production entries are bundled yet. See [ROI Universal Hook Production Evidence and Feature Migration Ledger](ROI_UNIVERSAL_HOOK_MIGRATION_LEDGER.md) for the audited call chains, existing hook inventory, evidence gaps and migration gates.

## Group lifecycle API

- `installRoiUniversalHookBatch(groupId, ...)` installs one stable group through `FeatureHookInstallRegistry`.
- `uninstallRoiUniversalHookBatch(groupId)` logically disables all handles owned by that group. It returns `true` only after the runtime reports zero active handles and the registry records `UNINSTALLED`.
- A successful uninstall permits reinstalling the same group ID. The registry attempt counter is retained for diagnostics.
- A failed unhook leaves the group registered as active/failed; do not retry by creating a second installer for the same group ID.
- This lifecycle API does not physically unregister the shared native Xposed callback.


## Reviewed evidence catalog

`RoiReviewedHookEvidenceRegistry.Builder.addReviewed(spec, candidate, policy)` is the only catalog population path. It rejects:

- missing spec, candidate or policy;
- source-scope / candidate-ID / owner / method / descriptor mismatches;
- incomplete or unconfirmed required evidence;
- unapproved adapter fingerprints or roles;
- discovery-only and otherwise non-authorizing policies;
- duplicate HookSpec identities.

`build()` returns an immutable catalog implementing both the activation gate's evidence and policy source interfaces. The builder cannot be modified after build. Production adapter declarations must be compiled, code-reviewed, version-scoped records. There are currently no production records in the catalog; this is intentional until real APK/DEX and device evidence is reviewed.


## R10 — Bind adapter fingerprint to installed host identity

The activation fingerprint is derived from a canonical, length-prefixed SHA-256 input containing the exact host package name, Android PackageManager versionCode and versionName, maintained distribution channel, aggregate digest of the complete installed base + split APK set, and aggregate signer-certificate digest.

The process-local ClassLoader scope remains a separate mandatory equality check; it is not folded into the artifact fingerprint. The activation gate rejects a HookSpec unless its fingerprint equals the current installed identity's derived activation fingerprint. The reviewed evidence catalog applies the same check before an entry can be admitted. Human adapter labels such as `code289-cn` are not accepted as fingerprints.

This binds a reviewed method candidate and semantic-role policy to the exact installed host build rather than trusting an independent caller-supplied label. It does not create evidence for an uninspected APK, validate a candidate's behavior, or authorize any production Hook by itself. Discovery remains diagnostic-only and the production catalog remains empty until exact APK/DEX and device evidence is reviewed.

Regression coverage verifies deterministic fingerprint generation and invalidation when APK-set digest, signer, or Android version code changes; it also rejects legacy human-readable labels at both the gate and catalog-builder boundaries.


## R11 lifecycle audit — retain handles after rollback failure

A batch rollback is not complete when `Handle.unhook()` throws. The installer now retains that handle in its active map and marks its state `FAILED`, so `activeCount()` continues to expose the unresolved native registration. A later install attempt using that ID is rejected until explicit uninstall successfully retries cleanup. The group-level `uninstallAll()` path is also covered: a failed unhook remains visible in `activeCount()`, and a later cleanup attempt can finish it. This prevents a retry from silently accepting a possibly live old callback or creating a duplicate registration.

Regression coverage injects a backend install failure followed by a rollback-unhook failure, verifies the uncertain handle remains tracked, verifies duplicate install is blocked, then retries explicit cleanup and proves a fresh installation can proceed. Main's group uninstall gate therefore cannot mark the registry entry uninstalled while a failed rollback handle remains.

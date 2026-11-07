# ROI Universal Hook Runtime — R1–R9

## Delivery status
This change supplies a backend-neutral Java 8 Hook runtime core. It does not claim that a production Xposed backend is wired into every existing feature hook or that any host build has passed device validation. Existing RoiHookResolver remains the evidence/authorization gate. Installer requires a non-null ActivationGate; production integration must delegate that gate to RoiHookResolver, and the runtime rejects activation before calling Backend when the gate denies.

| Stage | Implemented in this change | Remaining integration boundary |
|---|---|---|
| R1 | Reuse evidence resolver as authorization gate | Existing discovery adapters stay separate |
| R2 | Invocation envelope and post-install health contract | Real runtime correlation needs Xposed backend + device evidence |
| R3 | Immutable HookSpec with role, signature, fingerprint, dependencies | No guessed host symbols |
| R4 | Exact ClassLoader-scoped method/constructor resolution | No cross-loader cache |
| R5 | Active registry, idempotency, uninstall/uninstall-all | Backend must provide unhook handle |
| R6 | Resolve-before-install, dependency ordering, conflict checks, reverse rollback | Failed rollback is surfaced |
| R7 | Mandatory health-check gate | Adapter supplies actual behavioral check |
| R8 | Exact fingerprint + role allowlist | Unknown fingerprint denied |
| R9 | Java 8 JVM regression harness and isolated workflow | JVM checks do not equal Android/Xposed/device checks |

## Flow
Host fingerprint + reviewed adapter -> evidence authorization -> HookSpec -> exact resolution -> dependency/conflict checks -> backend install -> health verification -> ACTIVE -> uninstall or rollback.

## Backend contract
Backend is an SPI. A production Xposed adapter must use the supported Xposed API and existing compatibility boundary; preserve original invocation cardinality, arguments, result, throwable, order, thread and cancellation semantics; return a safe unhook handle; and never log prompts, credentials or user content.

Until connected and device-validated, this is a reusable runtime core, not a turnkey automatic Hook engine. Unknown hosts remain fail-closed. Never bypass RoiHookResolver or existing feature authorization.

## Test
Run bash module/test-roi-universal-hook-runtime.sh. Tests cover exact method/constructor resolution, overload rejection, fingerprint fail-closed, dependency install, idempotency, uninstall, injected backend failure rollback and health-check rollback.

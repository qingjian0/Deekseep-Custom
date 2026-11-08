# ADR: Independent runtime gates for unknown-version attempts, diagnostics, and feature Hooks

- Status: Accepted
- Scope: Android runtime Hook activation and diagnostics
- Applies to: `dev` first; stable behavior changes only through an explicitly authorized release/merge

## Context

A single "unknown versions are read-only" rule is too restrictive for deliberate compatibility experiments, while automatically activating diagnostic probes or feature Hooks creates unnecessary runtime risk. Static analysis can identify candidates and rank evidence, but it cannot prove runtime compatibility or grant user authorization.

## Decision

Use three independent runtime switches. Each switch has independent persisted opt-in state and is evaluated at the point where the corresponding operation would be installed or executed. The runtime gates use exact host-private `ENABLE` marker files. The three controls are exposed separately on the module's Engineering → Experimental Features page; the displayed status text reports the current effective gate state.

| Switch | Default | When enabled | Must not enable |
|---|---:|---|---|
| Unknown-version Hook attempts | OFF | Permit the compatibility pipeline to attempt explicitly selected Hook candidates on an unrecognized host build, subject to runtime guards | Diagnostic probes; feature Hooks |
| Diagnostic probes | OFF | Permit diagnostic-only probe installation and collection | Unknown-version attempts; feature Hooks |
| Feature Hook authorization | OFF | Provide the additional authorization required for unknown-host feature Hooks; the unknown-version-attempt switch must also be ON | Diagnostic probes; unknown-version attempts |

### 1. Unknown-version Hook attempts

- The default is OFF. An unknown host version is not attempted unless the user explicitly enables this switch.
- When ON, the system may run the compatibility-resolution and runtime-validation path for unknown versions.
- A candidate, a static-analysis score, a matching method signature, or a successful class scan is not proof of compatibility.
- Missing classes, ambiguous matches, descriptor mismatches, failed runtime invariants, or unsupported host behavior must fail closed for the affected Hook.
- The switch permits an attempt; it does not force installation and does not grant feature-Hook authorization.
- The attempt must be scoped and observable. Do not silently broaden it to all discovered methods.

### 2. Diagnostic probes

- The default is OFF.
- When OFF, diagnostic probe code must not be installed or run, and must not collect probe-only data.
- When ON, only explicitly designated diagnostic probes may run.
- Diagnostic probes are not feature Hooks and must not be used as an implicit path to activate feature behavior.
- Turning this switch OFF must prevent new probe installation and stop/remove active probes where the runtime supports safe teardown.

### 3. Feature Hook authorization

- The default is OFF.
- An unknown-host feature Hook may execute only when both the unknown-version-attempt switch and this authorization switch are ON, and its ordinary feature-specific runtime preconditions pass.
- Static analysis may produce candidates, evidence, confidence, or a suggested mapping. None of those results may write authorization state, enable this switch, or bypass its gate.
- A successful resolver result is not authorization.
- With the switch OFF, feature Hook installation is denied even if every static-analysis check passes.
- With this switch ON but unknown-version attempts OFF, no unknown-host feature Hook attempt may start. With both switches ON, runtime validation is still mandatory; the switches are necessary, not sufficient.

## Independence and precedence

The gates are independent. No switch may mutate or imply the state of another switch.

Effective permission is evaluated as follows:

- Unknown-version candidate attempt: `unknownVersionHookAttemptsEnabled`.
- Diagnostic probe installation/execution: `diagnosticProbesEnabled`.
- Unknown-host feature Hook installation/execution: `unknownVersionHookAttemptsEnabled && featureHooksAuthorized && featureSpecificRuntimeChecksPass`.

A denied gate must fail closed and return a stable, machine-readable reason. Suggested reason identifiers:

- `unknown_version_attempts_disabled`
- `diagnostic_probes_disabled`
- `feature_hooks_not_authorized`
- `feature_runtime_validation_failed`

The unknown-version gate and feature-Hook gate are cumulative for unknown-host feature Hooks: enabling unknown-version attempts must never implicitly authorize feature Hooks, and enabling feature-Hook authorization alone must never start an unknown-version attempt.

## Settings and UX requirements

- Expose three separately labeled switches; do not combine them into one "developer mode" switch.
- Show the default OFF state clearly.
- Explain the scope and risk of each switch adjacent to its control.
- Persist each value independently. Missing, corrupt, or unreadable marker values resolve to OFF. The UI must not rewrite another switch when one control changes.
- Do not change a user's saved preference as a side effect of scanning, resolving, updating the host version, or upgrading the module.
- Provide a visible runtime denial reason when an operation is blocked by a switch.
- The UI must not describe a candidate as "supported" merely because an attempt was enabled.

## Acceptance criteria

1. Fresh install: all three switches are OFF.
2. Enabling unknown-version attempts does not change the diagnostic-probe or feature-Hook authorization switch states; a feature Hook remains denied unless its separate authorization is also ON.
3. Enabling diagnostic probes does not change the unknown-version-attempt or feature-Hook authorization switch states.
4. Enabling feature-Hook authorization alone does not enable diagnostics, start an unknown-version attempt, or activate a feature Hook.
5. Static-analysis success with feature-Hook authorization OFF results in a denied feature Hook.
6. Static-analysis failure or ambiguity never becomes authorization, even when both unknown-version attempts and the feature-Hook switch are ON.
7. Unknown-version attempt enabled + feature-Hook authorization OFF results in no feature Hook.
8. Feature-Hook authorization ON + unknown-version attempts OFF results in no unknown-host feature Hook attempt.
9. Unknown-version attempt enabled + runtime mismatch results in a fail-closed denial, not a forced Hook.
10. Preference read failure defaults to OFF.
11. Tests cover all combinations of the three switches and verify no cross-gate activation.

## Implementation boundary

The dev implementation now includes independent host-private marker persistence, three separate settings controls, a live gate-status explanation, runtime checks at the unknown-host dispatcher/probe/feature adapter entry points, and an eight-combination policy test. Turning diagnostic or feature switches OFF causes already-installed wrappers to pass through when their callback next runs. Full Android build, CI execution, and real-device verification remain separate acceptance steps.

## Unknown-host activation reporting

Unknown/unmaintained host versions return early from the maintained feature bootstrap. That early return must not suppress the module's process-activation signal.

- Before returning from the isolated unknown-host branch, register a read-only `Application.ActivityLifecycleCallbacks` observer.
- On Activity resume, report the target-process heartbeat through the existing authenticated activation provider / explicit-component fallback.
- The observer may write the existing loaded marker for diagnostics.
- It must not install feature Hooks, symbol adapters, UI patches, or enable any unknown-host gate.
- The launcher may therefore report that the module was injected/observed in the target process while continuing to label functional compatibility as unverified.

This is deliberately a process-presence signal, not proof that Native Completion or any feature Hook works.

## Consequences

- Deliberate unknown-version compatibility attempts become possible without making unknown versions implicitly trusted.
- Diagnostics remain opt-in and quiet by default.
- Static analysis remains evidence generation only; explicit user authorization is required for feature Hooks.
- The project must keep the three gates independent across UI, persistence, resolver, dispatcher, and tests.

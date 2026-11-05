# ADR: Independent runtime gates for unknown-version attempts, diagnostics, and feature Hooks

- Status: Accepted
- Scope: Android runtime Hook activation and diagnostics
- Applies to: `dev` first; stable behavior changes only through an explicitly authorized release/merge

## Context

A single "unknown versions are read-only" rule is too restrictive for deliberate compatibility experiments, while automatically activating diagnostic probes or feature Hooks creates unnecessary runtime risk. Static analysis can identify candidates and rank evidence, but it cannot prove runtime compatibility or grant user authorization.

## Decision

Use three independent runtime switches. Each switch is persisted as an explicit user preference and is evaluated at the point where the corresponding operation would be installed or executed.

| Switch | Default | When enabled | Must not enable |
|---|---:|---|---|
| Unknown-version Hook attempts | OFF | Permit the compatibility pipeline to attempt explicitly selected Hook candidates on an unrecognized host build, subject to runtime guards | Diagnostic probes; feature Hooks |
| Diagnostic probes | OFF | Permit diagnostic-only probe installation and collection | Unknown-version attempts; feature Hooks |
| Feature Hook authorization | OFF | Permit feature Hook activation through the normal feature-specific runtime checks | Diagnostic probes; unknown-version attempts |

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
- A feature Hook may execute only when this switch is ON **and** its ordinary feature-specific runtime preconditions pass.
- Static analysis may produce candidates, evidence, confidence, or a suggested mapping. None of those results may write authorization state, enable this switch, or bypass its gate.
- A successful resolver result is not authorization.
- With the switch OFF, feature Hook installation is denied even if every static-analysis check passes.
- With the switch ON, runtime validation is still mandatory; the switch is necessary, not sufficient.

## Independence and precedence

The gates are independent. No switch may mutate or imply the state of another switch.

Effective permission is evaluated as follows:

- Unknown-version candidate attempt: `unknownVersionHookAttemptsEnabled`.
- Diagnostic probe installation/execution: `diagnosticProbesEnabled`.
- Feature Hook installation/execution: `featureHooksAuthorized && featureSpecificRuntimeChecksPass`.

A denied gate must fail closed and return a stable, machine-readable reason. Suggested reason identifiers:

- `unknown_version_attempts_disabled`
- `diagnostic_probes_disabled`
- `feature_hooks_not_authorized`
- `feature_runtime_validation_failed`

The unknown-version gate and feature-Hook gate are separate: enabling unknown-version attempts must never implicitly authorize feature Hooks on that version.

## Settings and UX requirements

- Expose three separately labeled switches; do not combine them into one "developer mode" switch.
- Show the default OFF state clearly.
- Explain the scope and risk of each switch adjacent to its control.
- Persist each value independently. Missing, corrupt, or unreadable preference values resolve to OFF.
- Do not change a user's saved preference as a side effect of scanning, resolving, updating the host version, or upgrading the module.
- Provide a visible runtime denial reason when an operation is blocked by a switch.
- The UI must not describe a candidate as "supported" merely because an attempt was enabled.

## Acceptance criteria

1. Fresh install: all three switches are OFF.
2. Enabling unknown-version attempts does not enable diagnostic probes or feature Hooks.
3. Enabling diagnostic probes does not enable unknown-version attempts or feature Hooks.
4. Enabling feature-Hook authorization does not enable diagnostics or unknown-version attempts.
5. Static-analysis success with feature-Hook authorization OFF results in a denied feature Hook.
6. Static-analysis failure or ambiguity never becomes authorization, even when the feature-Hook switch is ON.
7. Unknown-version attempt enabled + runtime mismatch results in a fail-closed denial, not a forced Hook.
8. Preference read failure defaults to OFF.
9. Tests cover all combinations of the three switches and verify no cross-gate activation.

## Implementation boundary

This ADR records the required policy. It does not claim that the current Hook dispatcher, settings UI, persistence layer, or tests have already been wired to these gates. Implementation is complete only when the actual installation and execution call sites consume the policy and the acceptance tests pass.

## Consequences

- Deliberate unknown-version compatibility attempts become possible without making unknown versions implicitly trusted.
- Diagnostics remain opt-in and quiet by default.
- Static analysis remains evidence generation only; explicit user authorization is required for feature Hooks.
- The project must keep the three gates independent across UI, persistence, resolver, dispatcher, and tests.

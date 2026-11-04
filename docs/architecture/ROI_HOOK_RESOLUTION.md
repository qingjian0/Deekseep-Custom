# ROI Version-independent Hook Resolution

> Status: Phase R0 implemented as a pure-Java, side-effect-free resolver core. This document does not claim automatic host-method discovery, real Hook installation, or cross-version runtime compatibility.

## 1. Objective

Move ROI from DEX discovery to evidence-backed semantic Hook resolution. The resolver must distinguish:

- **Discovery**: a class or method is a candidate.
- **Correlation**: independent structural and call-chain evidence agree.
- **Verification**: the candidate satisfies the required runtime and behavioral contract.
- **Authorization**: a reviewed adapter rule explicitly permits the exact host fingerprint and semantic role.
- **Activation**: a separate installer successfully installs the hook and passes post-install checks.

No earlier state implies a later state.

## 2. Current integration boundary

Existing components remain authoritative for their current scope:

- `RoiHostDiscovery`: bounded, read-only DEX/ClassLoader inventory for unknown hosts.
- `OpenNativeCompletionProbe`: specialized read-only host structure inventory and narrowly gated runtime observation.
- `HostCompat` / `HostRemap`: maintained version and symbol mapping paths.
- `FeatureHookInstallRegistry`: existing installation boundary.

The new `RoiHookResolver` is deliberately pure Java. It does not load host classes, invoke host methods, inspect live object values, or call the Xposed API. It accepts a candidate plus evidence and returns a deterministic decision for a future adapter/installer integration.

## 3. Candidate evidence model

Evidence is deduplicated by family so repeated observations cannot inflate confidence. The current initial weights are:

| Evidence family | Weight |
|---|---:|
| Host identity | 0.10 |
| DEX fingerprint | 0.10 |
| Semantic marker | 0.08 |
| Method shape | 0.16 |
| Call graph | 0.16 |
| Runtime invocation | 0.16 |
| Return contract | 0.12 |
| Lifecycle contract | 0.12 |

The score is an absolute weighted sum. Missing evidence contributes zero; the score is not normalized over only the evidence supplied. Evidence also records whether it is confirmed and the source that produced it.

A high score alone is never sufficient. Verification additionally requires confirmed evidence (confidence >= 0.80) for host identity, method shape, call graph, runtime invocation, return contract, and lifecycle contract. Contradictory evidence rejects the candidate.

## 4. State and activation policy

| State | Meaning | Installer authorization |
|---|---|---|
| `DISCOVERED` | Insufficient correlated evidence | No |
| `CORRELATED` | Evidence correlates but required proof is incomplete | No |
| `VERIFIED` | Score and hard evidence gates pass | Only if separately approved |
| `REJECTED` | Contradiction or invalid input | No |
| `ACTIVE` | Installer outcome after installation and post-install verification | Not represented by this resolver |

The default policy is discovery-only. Authorization requires all of the following:

1. Automatic activation is explicitly enabled by policy.
2. The semantic role is on the policy allowlist.
3. The adapter rule is approved.
4. The candidate's exact host fingerprint is in that adapter's approved fingerprint set.
5. All verification evidence gates pass.

The resolver returns `activationAuthorized`; it never reports `ACTIVE`. A future installer must independently report installation success, verify the original method still executes exactly once, check expected callback/return behavior, and support rollback. Failed or partial installation must remain inactive.

## 5. Non-negotiable runtime contracts

Before any real automatic activation is connected, each adapter must specify and verify:

- Exact host process, channel, versionCode and DEX fingerprint scope.
- Defining ClassLoader identity and class/member descriptor.
- Expected call-chain predecessor/successor and invocation cardinality.
- Original method execution count and ordering.
- Argument and return-value contracts without persisting message content or credentials.
- Exception propagation, cancellation, thread/dispatcher and lifecycle behavior.
- Idempotent installation and rollback behavior.
- A post-install health check with a bounded observation window.

A candidate discovered in one ClassLoader must not be reused for a same-named class from another loader. A mapping approved for one host fingerprint must not transfer to another fingerprint without new evidence and approval.

## 6. Implementation phases

### R0 — Deterministic resolver core (implemented in this branch)

- Pure-Java evidence model and weighted scoring.
- Evidence-family deduplication.
- Hard verification gates and contradiction rejection.
- Discovery-only default policy.
- Exact fingerprint + adapter approval + role allowlist required for installer authorization.
- Executable regression tests for weak, incomplete, conflicting, unapproved, authorized and wrong-fingerprint cases.

### R1 — Discovery evidence adapters

- Bounded DexKit string-anchor candidates enter the resolver as typed evidence under the default discovery-only policy.
- Parse DEX method descriptors and report owner/name/parameter arity/return descriptor; also report bounded access flags, parameter/return type names, and opcode names. These are static structural observations, not proof of semantic equivalence.
- Collect a bounded one-hop static call neighborhood (up to 12 incoming and 12 outgoing edges per emitted candidate). The query explicitly requests DexKit invoke/caller matchers so the lazy native relation indexes are initialized before reading `MethodData.invokes` and `MethodData.callers`.
- Static call edges remain unconfirmed evidence: they do not prove that an edge executes on a live request path. Runtime invocation, return, and lifecycle evidence are not inferred from DEX.
- Loader-scoped stable candidate identity remains in progress; report prefixes currently identify the source pass/loader/APK, but there is not yet a persisted cross-pass candidate registry.
- Unknown-host discovery remains read-only. Host identity is diagnostic-only/unconfirmed, and the resolver cannot authorize activation from this evidence.

### R2 — Call graph and runtime correlation

- Establish the real request -> transport -> response path for each supported host fingerprint.
- Correlate static call graph with passive runtime invocation observations.
- Record only structural metadata; do not capture prompts, completions, tokens or request bodies.
- Validate original invocation count and exception behavior.

### R3 — Adapter contract and registry integration

- Define semantic roles and narrow adapter interfaces for actual required features.
- Store version/fingerprint-scoped reviewed rules with provenance and expiry.
- Integrate authorization with the existing Hook installation registry; do not create a parallel installer.
- Preserve legacy known-version adapters until replacement has equivalent regression evidence.

### R4 — Controlled activation and rollback

- Install only an authorized adapter.
- Verify post-install behavior before marking it active.
- Disable and roll back on mismatch, timeout, exception or health-check failure.
- Keep unknown or unverified hosts in read-only mode.

### R5 — Compatibility matrix

- Test domestic 2.7.1/code287, domestic 2.7.2/code289 and Google Play 2.7.2/code290 independently.
- Add new host fingerprints only with APK/static evidence and device runtime evidence.
- Report CI, emulator and real-device results separately.

## 7. Current limitations

- R0 is a decision engine, not a semantic DEX analyzer.
- Existing DEX discovery output is not yet converted into resolver evidence.
- Existing runtime observations are narrow and version-specific; they do not establish general request-chain equivalence.
- No new automatic Hook installation is enabled by this change.
- No claim is made that arbitrary future DeepSeek versions can be identified or activated without adapter work.

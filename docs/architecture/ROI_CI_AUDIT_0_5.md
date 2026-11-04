# ROI CI Audit Profile 0.5

Date: 2026-11-04
Branch: `work/roi-agent-core-kotlin`

## Policy

Profile 0.5 is a moderate, faster-feedback audit profile. It relaxes only non-critical review friction; it does not waive compilation, executable resolver tests, discovery safety assertions, native ABI/ELF checks, APK integrity checks, or existing host regression suites.

## Changes in this batch

- Added a parallel `roi-fast-audit` job with a 10-minute timeout.
- The fast job syntax-checks and runs the resolver and DexKit discovery regression scripts independently of the full Android/native regression job.
- Added the resolver test script to the full CI shell syntax preflight.
- Connected each bounded DexKit string-anchor candidate to `RoiHookResolver`.
- Added a bounded DEX method-descriptor parser for owner/name/parameter arity/return descriptor; parser evidence is explicitly structural and does not establish semantic equivalence.
- The current adapter deliberately emits only low-confidence host diagnostic, observed string-marker, and unvalidated descriptor evidence. It uses the default discovery-only policy and reports state, score, authorization flag, and reason.
- Extended static discovery checks to assert the resolver connection, descriptor parsing, and fail-closed evidence provenance.
- Fixed a resolver test fixture defect: the `fullEvidence=false` branch previously still supplied the complete evidence set, invalidating the weak-candidate test.

## What profile 0.5 does not do

- It does not turn lint or compile errors into warnings.
- It does not skip native packaging, ABI/ELF checks, APK validation, or full regression tests.
- It does not authorize unknown-host Hook installation.
- It does not treat string anchors or a method descriptor as proof of semantic equivalence.
- It does not claim real-device runtime verification.

## Current implementation boundary

R1 has started: DexKit candidates now enter the resolver as explicitly weak, unresolved evidence. Real method-shape analysis, call-graph correlation, runtime invocation capture, exact APK/DEX fingerprinting, adapter approval, installation verification, and rollback remain outstanding.

## Audit outcome

Source changes are committed to the working branch. CI execution status must be read from the GitHub Actions run associated with the resulting commit; an empty status response is not a pass.

## R1.2 — bounded method structure and call neighborhood

- DexKit 2.3.0 MethodData is used through documented getters to observe raw access flags, parameter type names, return type name, opcode names, outgoing invokes, and incoming callers.
- Each candidate emits at most 12 outgoing and 12 incoming relation descriptors, 16 parameter type names, and 8 opcode names. Omitted counts are reported explicitly.
- The resulting edges are one-hop static DEX references, not proof that a method executes on a live request path. They are recorded as unconfirmed CALL_GRAPH evidence and cannot satisfy the resolver's confirmed-evidence threshold.
- No host class is loaded and no host method is invoked. The discovery policy remains discoveryOnly.
- The static regression assertion was corrected to match the actual descriptor parser contract (0.55 only when descriptor syntax validates), and new assertions cover bounded call relations and method structure accessors.

## Verification boundary

The source and static assertions have been updated on the working branch. The latest queried commit status returned no status entries; this is pending / unverified, not a CI pass. No local Gradle build or device Hook test is claimed.

## R1.3 — bounded candidate graph aggregation

- Added a pure-Java candidate graph builder scoped to one DexKit bridge scan. Repeated string-anchor observations for the same method descriptor merge into one node while retaining their anchor set and unioning bounded invoke/caller references.
- Static edges are oriented caller → callee. An invoke relation contributes candidate → target; a caller relation contributes caller → candidate. Duplicate edges are deduplicated even when the edge cap has already been reached.
- The graph reports observed candidates, unique nodes, duplicate observations, retained edges, unresolved external references, connected components, and omitted node/edge counts.
- Graph limits are 128 nodes and 256 retained edges per bridge scan; diagnostic output emits at most 32 edges. The graph never loads host classes or invokes host methods.
- Added standalone Java regression coverage for anchor deduplication, edge direction, duplicate edges, edge/node caps, unresolved references, cycles, and disconnected components. The fast audit and full regression workflow both invoke the new test script.
- Graph connectivity is descriptive static DEX structure only. It does not classify Request/Transport/Response roles, prove a live request path, or authorize Hook activation.

## R2.0 — conservative network role hints

- Added a separate `RoiSemanticRoleHints` classifier over a candidate's directly invoked method descriptors. It recognizes a narrow set of explicit OkHttp / Retrofit / URLConnection / Gson / Moshi / Jackson / JSONObject references as request-builder, transport, or response-parser hints.
- The classifier emits only positive hints and matched descriptor samples; unknown or obfuscated references remain unresolved. A method may carry multiple hints, and absence of a hint is not evidence that a role is absent.
- Hints are reported per candidate with provenance `direct_static_invoke_reference`. They are not fed into `RoiHookResolver`, do not change candidate role from `unresolved`, and cannot authorize activation.
- Added isolated JVM tests for request-builder, transport, response-parser, and unknown-reference cases. Added the test to both the parallel ROI fast audit and full regression job.

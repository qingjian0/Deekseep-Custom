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
- The current adapter deliberately emits only low-confidence host diagnostic, observed string-marker, and unvalidated descriptor evidence. It uses the default discovery-only policy and reports state, score, authorization flag, and reason.
- Extended static discovery checks to assert the resolver connection and fail-closed evidence provenance.
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

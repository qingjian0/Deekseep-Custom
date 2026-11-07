#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/module/build/roi-universal-hook-test"
mkdir -p "$OUT"
javac -source 8 -target 8 -nowarn -d "$OUT" \
  "$ROOT/module/src/ds/dsh/DeepSeek/RoiUniversalHookRuntime.java" \
  "$ROOT/module/src/ds/dsh/DeepSeek/RoiHookResolver.java" \
  "$ROOT/module/src/ds/dsh/DeepSeek/RoiMethodShape.java" \
  "$ROOT/module/src/ds/dsh/DeepSeek/RoiCandidateIdentity.java" \
  "$ROOT/module/src/ds/dsh/DeepSeek/RoiHookResolverActivationGate.java" \
  "$ROOT/module/src/ds/dsh/DeepSeek/RoiReviewedHookEvidenceRegistry.java" \
  "$ROOT/module/src/ds/dsh/DeepSeek/RoiHostArtifactFingerprint.java" \
  "$ROOT/module/src/ds/dsh/DeepSeek/RoiHostBuildIdentity.java" \
  "$ROOT/module/tests/roiartifact/ds/dsh/DeepSeek/RoiHostArtifactFingerprintTest.java" \
  "$ROOT/module/tests/roiuniversalhook/ds/dsh/DeepSeek/RoiUniversalHookRuntimeTest.java" \
  "$ROOT/module/tests/roiresolveractivationgate/ds/dsh/DeepSeek/RoiHookResolverActivationGateTest.java" \
  "$ROOT/module/tests/roireviewedevidence/ds/dsh/DeepSeek/RoiReviewedHookEvidenceRegistryTest.java"
java -cp "$OUT" ds.dsh.DeepSeek.RoiUniversalHookRuntimeTest
java -cp "$OUT" ds.dsh.DeepSeek.RoiHookResolverActivationGateTest
java -cp "$OUT" ds.dsh.DeepSeek.RoiReviewedHookEvidenceRegistryTest
java -cp "$OUT" ds.dsh.DeepSeek.RoiHostArtifactFingerprintTest

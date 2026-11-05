#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/module/build/roi-class-load-observer-test"
mkdir -p "$OUT"
javac -source 8 -target 8 -nowarn -d "$OUT" \
  "$ROOT/module/src/ds/dsh/DeepSeek/RoiCandidateIdentity.java" \
  "$ROOT/module/src/ds/dsh/DeepSeek/RoiLoadedClassInspector.java" \
  "$ROOT/module/src/ds/dsh/DeepSeek/RoiClassLoadObserver.java" \
  "$ROOT/module/tests/roiclassload/ds/dsh/DeepSeek/RoiClassLoadObserverTest.java"
java -cp "$OUT" ds.dsh.DeepSeek.RoiClassLoadObserverTest

# Integration guards: only approved, explicitly opted-in host loader observations may be installed.
MAIN="$ROOT/module/src/ds/dsh/DeepSeek/Main.java"
OBSERVER="$ROOT/module/src/ds/dsh/DeepSeek/RoiClassLoadObserver.java"
grep -q 'deekseep_roi_class_load_observation_enabled' "$MAIN"
grep -q 'HostCompat.supportsMaintainedFeatureRuntime()' "$MAIN"
grep -q 'dalvik.system.BaseDexClassLoader' "$MAIN"
grep -q 'getDeclaredMethod("findClass", String.class)' "$MAIN"
grep -q 'RoiClassLoadObserver.observe(' "$MAIN"
grep -q 'chain.proceed()' "$MAIN"
grep -q 'isWithinHostScope' "$OBSERVER"
grep -q 'loadedClass.getClassLoader() != definingLoader' "$OBSERVER"
grep -q 'DEFAULT_MAX_EVENTS = 128' "$OBSERVER"
grep -q 'DEFAULT_MAX_EVENTS_PER_WINDOW = 16' "$OBSERVER"
grep -q 'DEFAULT_TTL_MILLIS = 120000L' "$OBSERVER"

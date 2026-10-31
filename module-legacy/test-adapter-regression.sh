#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"

OUT=build/adapter-test
rm -rf "$OUT"
mkdir -p "$OUT"

# The adapter now calls optional tracing/runtime hooks. Compile no-op test seams here;
# the Android-backed production implementations are exercised by the Android build.
javac -source 8 -target 8 -d "$OUT" \
    src/de/robv/android/xposed/XC_MethodHook.java \
    src/de/robv/android/xposed/XposedBridge.java \
    tests/support/com/dsmod/probe/LegacyInstrumentationStubs.java \
    compat/com/dsmod/probe/LegacyXposedModule.java \
    tests/com/dsmod/probe/LegacyXposedModuleRegressionTest.java

matrix_output="$(java -cp "$OUT" com.dsmod.probe.LegacyXposedModuleRegressionTest)"
printf '%s\n' "$matrix_output"
printf '%s\n' "$matrix_output" | grep -q \
    'Legacy Xposed API 82-102 matrix OK (21 versions)'

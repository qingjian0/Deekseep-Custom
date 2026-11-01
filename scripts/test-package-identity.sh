#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

fail() {
  echo "PACKAGE IDENTITY ERROR: $*" >&2
  exit 1
}

# The retired namespace must not remain in executable source, build scripts,
# manifests, tests, or workflow configuration.
if grep -R -n -E 'com[.]dsmod[.](probe|relay)|com/dsmod/(probe|relay)' \
    module module-legacy module-universal module-kotlin-runtime \
    compose-launcher scripts .github \
    --exclude='test-package-identity.sh'; then
  fail "retired internal namespace reference remains"
fi

test ! -d module/src/com/dsmod/probe || fail "old module source directory remains"
test ! -d module/src/com/dsmod/relay || fail "old relay source directory remains"
test -s module/src/ds/dsh/DeepSeek/Main.java || fail "new Xposed entry source is missing"
test -s module/src/ds/dsh/DeepSeek/relay/ExpertRelayGate.java || fail "new relay source is missing"

grep -Fq 'package ds.dsh.DeepSeek;' module/src/ds/dsh/DeepSeek/Main.java \
  || fail "Main.java package declaration is incorrect"
grep -Fq 'package ds.dsh.DeepSeek;' module/src/ds/dsh/DeepSeek/XposedActivationProvider.java \
  || fail "provider package declaration is incorrect"
grep -Fq 'package ds.dsh.DeepSeek.relay;' module/src/ds/dsh/DeepSeek/relay/ExpertRelayGate.java \
  || fail "relay package declaration is incorrect"

for manifest in module-legacy/AndroidManifest.xml module-universal/AndroidManifest.xml \
    compose-launcher/app/src/main/AndroidManifest.xml; do
  grep -Fq 'ds.dsh.DeepSeek.SettingsActivity' "$manifest" \
    || fail "$manifest does not reference the migrated launcher activity"
done

for entry in module-legacy/assets/xposed_init module-universal/assets/xposed_init; do
  test "$(tr -d '\r\n' < "$entry")" = 'ds.dsh.DeepSeek.Main' \
    || fail "$entry does not point to ds.dsh.DeepSeek.Main"
done

for script in module-legacy/build.sh module-universal/build.sh; do
  grep -Fq 'find "$OUT/classes/ds/dsh/DeepSeek"' "$script" \
    || fail "$script does not collect D8 classes from the migrated package"
done

grep -Fq 'namespace = "ds.dsh.DeepSeek"' compose-launcher/app/build.gradle.kts \
  || fail "Compose namespace is incorrect"
grep -Fq 'applicationId = "ds.dsh.DeepSeek"' compose-launcher/app/build.gradle.kts \
  || fail "Compose applicationId is incorrect"
grep -Fq 'android:authorities="ds.dsh.DeepSeek.XposedService"' module-legacy/AndroidManifest.xml \
  || fail "Legacy provider authority changed unexpectedly"
grep -Fq 'android:authorities="ds.dsh.DeepSeek.XposedService"' module-universal/AndroidManifest.xml \
  || fail "Universal provider authority changed unexpectedly"
grep -Fq 'AUTHORITY = "ds.dsh.DeepSeek.XposedService"' \
  module/src/ds/dsh/DeepSeek/XposedActivationProvider.java \
  || fail "provider authority constant does not match the manifests"

grep -Fq 'com.deepseek.chat' module/src/ds/dsh/DeepSeek/Main.java \
  || fail "DeepSeek host package identity was lost"
grep -Fq 'com.deepseek.chat' module/src/ds/dsh/DeepSeek/XposedActivationProvider.java \
  || fail "DeepSeek host package identity was lost from provider"

echo "Package identity checks passed: module=ds.dsh.DeepSeek host=com.deepseek.chat"

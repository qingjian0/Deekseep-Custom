#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
GRADLE="$ROOT/compose-launcher/app/build.gradle.kts"
ENTRY="$ROOT/module/src/ds/dsh/DeepSeek/Main.java"
PROVIDER="$ROOT/module/src/com/dsmod/probe/XposedActivationProvider.java"
RECEIVER="$ROOT/module/src/com/dsmod/probe/XposedActivationReceiver.java"
grep -Fq 'applicationId = "ds.dsh.DeepSeek"' "$GRADLE"
grep -Fq 'namespace = "com.dsmod.probe"' "$GRADLE"
grep -Fq 'public final class Main extends com.dsmod.probe.Main' "$ENTRY"
for init in "$ROOT/module-legacy/assets/xposed_init" "$ROOT/module-universal/assets/xposed_init"; do
  grep -Fxq 'ds.dsh.DeepSeek.Main' "$init"
done
grep -Fq 'static final String SELF = "ds.dsh.DeepSeek";' "$ROOT/module/src/com/dsmod/probe/Main.java"
grep -Fq 'static final String AUTHORITY = "ds.dsh.DeepSeek.XposedService";' "$PROVIDER"
grep -Fq 'static final String ACTION = "ds.dsh.DeepSeek.action.REPORT_DEEPSEEK_ACTIVE";' "$RECEIVER"
for manifest in "$ROOT/module-legacy/AndroidManifest.xml" "$ROOT/module-universal/AndroidManifest.xml" "$ROOT/compose-launcher/app/src/main/AndroidManifest.xml"; do
  grep -Fq 'android:authorities="ds.dsh.DeepSeek.XposedService"' "$manifest"
  if grep -Fq 'android:authorities="com.dsmod.probe.XposedService"' "$manifest"; then
    echo "FAIL: stale activation provider authority in $manifest" >&2
    exit 1
  fi
done
echo "PASS: application id, legacy entry, provider authority and broadcast identity are aligned"

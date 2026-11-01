#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STATUS="$ROOT/compose-launcher/app/src/main/java/ds/dsh/DeepSeek/LauncherStatusViewModel.kt"
ACTIVITY="$ROOT/compose-launcher/app/src/main/java/ds/dsh/DeepSeek/SettingsActivity.kt"
HOOK_SURFACE="$ROOT/module/src/ds/dsh/DeepSeek/HookUiSurface.java"

test -s "$STATUS"
test -s "$ACTIVITY"
test -s "$HOOK_SURFACE"

grep -Fq 'isActive = targetRecentlyActive,' "$STATUS"
grep -Fq 'frameworkConnected = frameworkConnected' "$STATUS"
grep -Fq 'HostCompat.supportsHostVersionName(versionName)' "$STATUS"
grep -Fq '"2.7.2".equals(value)' "$ROOT/module/src/ds/dsh/DeepSeek/HostCompat.java"
grep -Fq 'catch (Throwable error) {' "$HOOK_SURFACE"
grep -Fq 'hook nav state ' "$HOOK_SURFACE"
if grep -Fq 'isActive = frameworkConnected || targetRecentlyActive' "$STATUS"; then
  echo "Framework binder state must not count as target-process injection." >&2
  exit 1
fi

grep -Fq 'LSPosed 框架服务已连接，但尚未验证 DeepSeek 目标进程注入' "$ACTIVITY"
echo "Launcher activation semantics regression passed."

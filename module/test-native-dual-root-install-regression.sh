#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SOURCE="$ROOT_DIR/module/src/ds/dsh/DeepSeek/HookUiSurface.java"

test -s "$SOURCE"
body="$(sed -n '/void hookNativeDualChatRoot/,/^    }/p' "$SOURCE")"
grep -Fq 'NATIVE_DUAL_ROOT_HOOK_INSTALLED.compareAndSet(false, true)' <<< "$body"
grep -Fq 'if (installed == 0)' <<< "$body"
grep -Fq 'throw new NoSuchMethodException("ComposeView.setContent targets")' <<< "$body"
grep -Fq 'NATIVE_DUAL_ROOT_HOOK_INSTALLED.set(false);' <<< "$body"

reset_line="$(grep -n 'NATIVE_DUAL_ROOT_HOOK_INSTALLED.set(false);' <<< "$body" | cut -d: -f1)"
failure_line="$(grep -n 'catch (Throwable error)' <<< "$body" | tail -n 1 | cut -d: -f1)"
test "$reset_line" -gt "$failure_line"

echo "Native dual-root install retry regression checks passed."

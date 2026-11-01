#!/usr/bin/env bash
set -euo pipefail

SOURCE="module/src/ds/dsh/DeepSeek/HookSessionManagement.java"
[[ -f "$SOURCE" ]] || { echo "missing $SOURCE" >&2; exit 1; }

BLOCK="$(sed -n '/void hookNativeSessionClickCallback/,/Reuses DeepSeek.s own gm8/p' "$SOURCE")"
[[ -n "$BLOCK" ]] || { echo "native session click installer not found" >&2; exit 1; }

grep -Fq 'NATIVE_CLICK_HOOKED_CLASSES.contains(callbackClass)' <<<"$BLOCK"
grep -Fq 'NATIVE_CLICK_HOOKED_CLASSES.add(callbackClass)' <<<"$BLOCK"
grep -Fq 'if (installed == 0)' <<<"$BLOCK"
[[ "$(grep -Fc 'NATIVE_CLICK_HOOKED_CLASSES.remove(callbackClass)' <<<"$BLOCK")" -ge 2 ]] || {
  echo "zero-match and total-failure paths must both release the reservation" >&2
  exit 1
}

echo "PASS: native session click hook reservation is released when no method is installed"

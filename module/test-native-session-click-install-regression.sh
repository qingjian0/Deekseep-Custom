#!/usr/bin/env bash
set -euo pipefail

SOURCE="module/src/ds/dsh/DeepSeek/HookSessionManagement.java"
[[ -f "$SOURCE" ]] || { echo "missing $SOURCE" >&2; exit 1; }

BLOCK="$(sed -n '/void hookNativeSessionClickCallback/,/Reuses DeepSeek.s own gm8/p' "$SOURCE")"
[[ -n "$BLOCK" ]] || { echo "native session click installer not found" >&2; exit 1; }

grep -Fq 'NATIVE_CLICK_HOOKED_CLASSES.contains(callbackClass)' <<<"$BLOCK"
grep -Fq 'NATIVE_CLICK_INSTALLING_CLASSES.add(callbackClass)' <<<"$BLOCK"
grep -Fq 'NATIVE_CLICK_HOOKED_METHODS.get(callbackClass)' <<<"$BLOCK"
grep -Fq 'registered.contains(method)' <<<"$BLOCK"
grep -Fq 'NATIVE_CLICK_HOOKED_METHODS.computeIfAbsent(' <<<"$BLOCK"
grep -Fq 'if (matched == 0)' <<<"$BLOCK"
grep -Fq 'else if (complete)' <<<"$BLOCK"
grep -Fq 'retry remains enabled' <<<"$BLOCK"
grep -Fq 'NATIVE_CLICK_INSTALLING_CLASSES.remove(callbackClass)' <<<"$BLOCK"

[[ "$(grep -Fc 'NATIVE_CLICK_HOOKED_CLASSES.add(callbackClass)' <<<"$BLOCK")" -eq 1 ]] || {
  echo "callback class must be marked complete only after all compatible methods install" >&2
  exit 1
}

echo "PASS: native session click installer retries failed methods without duplicating successful methods"

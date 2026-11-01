#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APKS=(
  "$ROOT/module-legacy/ds-probe-legacy.apk"
  "$ROOT/module-universal/ds-probe-universal.apk"
  "$ROOT/module-universal/ds-probe-universal-google-play.apk"
  "$ROOT/compose-launcher/app/build/outputs/apk/release/app-release.apk"
)
LABELS=(
  "Legacy"
  "Domestic Universal"
  "Google Play Universal"
  "Compose Open"
)
RUNTIME_CLASSES=(
  "Lds/dsh/DeepSeek/FeatureHookInstallRegistry;"
  "Lds/dsh/DeepSeek/TargetProcessGuard;"
  "Lds/dsh/DeepSeek/Main;"
)

fail() {
  echo "ANDROID APK PARITY ERROR: $*" >&2
  exit 1
}

assert_dex_contains() {
  local apk="$1"
  local dex_entries="$2"
  local symbol="$3"
  local dex_entry

  while IFS= read -r dex_entry; do
    [[ -n "$dex_entry" ]] || continue
    if unzip -p "$apk" "$dex_entry" | strings -n 8 | grep -F -- "$symbol" >/dev/null; then
      return 0
    fi
  done <<< "$dex_entries"

  fail "$(basename "$apk") is missing DEX symbol $symbol"
}

for index in "${!APKS[@]}"; do
  apk="${APKS[$index]}"
  label="${LABELS[$index]}"

  [[ -s "$apk" ]] || fail "$label APK is missing: $apk"
  unzip -t "$apk" >/dev/null || fail "$label APK has a corrupt ZIP container"

  dex_entries="$(unzip -Z1 "$apk" | grep -E '^classes([0-9]+)?[.]dex$' || true)"
  [[ -n "$dex_entries" ]] || fail "$label APK contains no DEX files"

  for symbol in "${RUNTIME_CLASSES[@]}"; do
    assert_dex_contains "$apk" "$dex_entries" "$symbol"
  done

  xposed_entry="$(unzip -p "$apk" assets/xposed_init 2>/dev/null | tr -d '\r\n' || true)"
  [[ "$xposed_entry" == "ds.dsh.DeepSeek.Main" ]] \
    || fail "$label APK has a missing or incorrect traditional Xposed entry"

  apk_entries="$(unzip -Z1 "$apk")"
  if grep -Fxq 'META-INF/xposed/java_init.list' <<< "$apk_entries"; then
    fail "$label APK unexpectedly contains a modern-only libxposed entry"
  fi

  dex_count="$(grep -Ec '^classes([0-9]+)?[.]dex$' <<< "$dex_entries")"
  echo "PASS: $label contains Main, FeatureHookInstallRegistry and TargetProcessGuard ($dex_count DEX)"
done

echo "Android APK runtime parity passed for all four maintained artifacts."

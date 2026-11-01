#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
source "$ROOT/scripts/android-tools.sh"
AAPT="$(dirname "$AAPT2")/aapt"
test -x "$AAPT"

APKS=(
    "$ROOT/module-universal/ds-probe-universal.apk"
    "$ROOT/module-universal/ds-probe-universal-google-play.apk"
)

for apk in "${APKS[@]}"; do
    [[ -f "$apk" ]] || {
        echo "FAIL: missing universal APK: $apk" >&2
        exit 1
    }
done

mkdir -p "$ROOT/build"
source_list="$(mktemp "$ROOT/build/universal-core-sources.XXXXXX")"
trap 'rm -f "$source_list"' EXIT
find "$ROOT/module/src/ds/dsh/DeepSeek" -maxdepth 1 -name '*.java' \
    ! -name 'BuildInfo.java' -printf '%f\n' | sed 's/[.]java$//' | sort > "$source_list"

expected="$(wc -l < "$source_list" | tr -d '[:space:]')"
[[ "$expected" -gt 0 ]] || {
    echo "FAIL: canonical feature core is empty" >&2
    exit 1
}

for apk in "${APKS[@]}"; do
    dex_strings="$(mktemp "$ROOT/build/universal-dex-strings.XXXXXX")"
    dex_entries="$(unzip -Z1 "$apk" | grep -E '^classes([0-9]+)?[.]dex$' || true)"
    [[ -n "$dex_entries" ]] || {
        echo "FAIL: $(basename "$apk") has no DEX files" >&2
        exit 1
    }
    while IFS= read -r dex_entry; do
        unzip -p "$apk" "$dex_entry" | strings -n 8
    done <<< "$dex_entries" > "$dex_strings"

    while IFS= read -r class_name; do
        grep -Fq -- "Lds/dsh/DeepSeek/${class_name};" "$dex_strings" || {
            echo "FAIL: $(basename "$apk") is missing canonical class ${class_name}" >&2
            rm -f "$dex_strings"
            exit 1
        }
    done < "$source_list"
    grep -Fq -- 'Lds/dsh/DeepSeek/Main;' "$dex_strings" || {
        echo "FAIL: $(basename "$apk") is missing the traditional Xposed entry class" >&2
        rm -f "$dex_strings"
        exit 1
    }
    grep -Fq -- 'Lds/dsh/DeepSeek/relay/ExpertRelayGate;' "$dex_strings" || {
        echo "FAIL: $(basename "$apk") is missing the canonical relay gate" >&2
        rm -f "$dex_strings"
        exit 1
    }
    for runtime_class in FeatureHookInstallRegistry TargetProcessGuard; do
        grep -Fq -- "Lds/dsh/DeepSeek/${runtime_class};" "$dex_strings" || {
            echo "FAIL: $(basename "$apk") is missing Kotlin runtime class ${runtime_class}" >&2
            rm -f "$dex_strings"
            exit 1
        }
    done
    grep -Fq -- 'Lds/dsh/DeepSeek/BuildInfo;' "$dex_strings" || {
        echo "FAIL: $(basename "$apk") is missing generated BuildInfo" >&2
        rm -f "$dex_strings"
        exit 1
    }
    if grep -Eq 'io/github/libxposed' "$dex_strings"; then
        echo "FAIL: $(basename "$apk") contains modern libxposed runtime references" >&2
        rm -f "$dex_strings"
        exit 1
    fi
    rm -f "$dex_strings"

    apk_entries="$(unzip -Z1 "$apk")"
    if grep -Fxq 'META-INF/xposed/java_init.list' <<< "$apk_entries"; then
        echo "FAIL: $(basename "$apk") contains a modern-only Xposed entry" >&2
        exit 1
    fi

    package_metadata="$("$AAPT" dump badging "$apk")"
    grep -Fq "package: name='ds.dsh.DeepSeek'" <<< "$package_metadata" || {
        echo "FAIL: $(basename "$apk") has an unexpected module package identity" >&2
        exit 1
    }

    manifest="$("$AAPT2" dump xmltree "$apk" --file AndroidManifest.xml)"
    printf '%s\n' "$manifest" | grep -Eq '"xposedminversion"' || {
        echo "FAIL: $(basename "$apk") has no xposedminversion" >&2
        exit 1
    }
    printf '%s\n' "$manifest" | grep -Eq 'android:value.*(=82$|0x52$)' || {
        echo "FAIL: $(basename "$apk") does not start at Xposed API 82" >&2
        exit 1
    }
    if printf '%s\n' "$manifest" | grep -Eq '"xposedmaxversion"'; then
        echo "FAIL: $(basename "$apk") unexpectedly caps the Xposed API" >&2
        exit 1
    fi
    unzip -p "$apk" assets/xposed_init | grep -qx 'ds[.]dsh[.]DeepSeek[.]Main' || {
        echo "FAIL: $(basename "$apk") has an invalid traditional Xposed entry" >&2
        exit 1
    }
done

echo "Universal feature parity passed: ${expected} canonical classes in both channels; Xposed API 82-102 uses one uncapped traditional entry"

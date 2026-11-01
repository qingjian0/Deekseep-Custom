#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DIST="$ROOT/dist"
source "$ROOT/scripts/android-tools.sh"

rm -rf "$DIST"
mkdir -p "$DIST"

echo
echo "=== Building Legacy module (Java + shared Kotlin runtime) ==="
(cd "$ROOT/module-legacy" && bash build.sh)

echo
echo "=== Building domestic Universal module ==="
(cd "$ROOT/module-universal" && bash build.sh)

echo
echo "=== Building Google Play Universal module ==="
(cd "$ROOT/module-universal" && GOOGLE_PLAY_BUILD=true bash build.sh)

echo
echo "=== Verifying complete feature parity and Xposed API 82-102 entry ==="
bash "$ROOT/scripts/test-universal-feature-parity.sh"

echo
echo "=== Running chat-editor regression test ==="
(cd "$ROOT/module" && bash test-thinking-regression.sh)

echo
echo "=== Running expert relay multi-turn regression test ==="
(cd "$ROOT/module" && bash test-expert-relay-regression.sh)

echo
echo "=== Running traditional-Xposed adapter regression test ==="
(cd "$ROOT/module-legacy" && bash test-adapter-regression.sh)

echo
echo "=== Verifying shared-core parity and generated entry ==="
for class_name in AccountManager ChatAppearance ChatAppearanceUi DeekseepUi AgentDeviceBridge HeartbeatToolProtocol ResponsePreserver; do
    test -f "$ROOT/module-universal/build/classes/ds/dsh/DeepSeek/$class_name.class"
done
if grep -q '^import io[.]github[.]libxposed' "$ROOT/module-universal/build/generated-src/ds/dsh/DeepSeek/Main.java"; then
    echo "Generated universal entry still imports modern libxposed APIs" >&2
    exit 1
fi

MANIFEST="$ROOT/module-universal/AndroidManifest.xml"
VERSION_NAME="$(grep -o 'android:versionName="[^"]*"' "$MANIFEST" | cut -d '"' -f 2 || true)"
if [[ -z "$VERSION_NAME" ]]; then
    echo "Unable to read Universal module version from $MANIFEST" >&2
    exit 1
fi

LEGACY_APK="$DIST/deekseep-legacy-$VERSION_NAME.apk"
UNIVERSAL_APK="$DIST/deekseep-universal-$VERSION_NAME.apk"
GOOGLE_PLAY_APK="$DIST/deekseep-google-play-universal-$VERSION_NAME.apk"
cp "$ROOT/module-legacy/ds-probe-legacy.apk" "$LEGACY_APK"
cp "$ROOT/module-universal/ds-probe-universal.apk" "$UNIVERSAL_APK"
cp "$ROOT/module-universal/ds-probe-universal-google-play.apk" "$GOOGLE_PLAY_APK"

if [[ -n "$(find "$DIST" -maxdepth 1 -type f \( -name '*test*' -o -name '*probe*' \) -print -quit)" ]]; then
    echo "Refusing to package retired test/diagnostic APKs" >&2
    exit 1
fi

AAPT="$(resolve_android_tool aapt)"
grep -Fq 'android:name="xposedminversion" android:value="82"' "$MANIFEST"

certificate_sha256() {
    local signer_output
    signer_output="$("$APKSIGNER" verify --print-certs "$1")"
    awk -F': ' '/^Signer #1 certificate SHA-256 digest:/ { print $2; exit }' <<< "$signer_output"
}
legacy_cert="$(certificate_sha256 "$LEGACY_APK")"
domestic_cert="$(certificate_sha256 "$UNIVERSAL_APK")"
google_play_cert="$(certificate_sha256 "$GOOGLE_PLAY_APK")"
test -n "$legacy_cert"
test "$legacy_cert" = "$domestic_cert"
test "$domestic_cert" = "$google_play_cert"

for apk in "$LEGACY_APK" "$UNIVERSAL_APK" "$GOOGLE_PLAY_APK"; do
    unzip -t "$apk" >/dev/null
    "$ZIPALIGN" -c -p 4 "$apk"
    "$APKSIGNER" verify --verbose "$apk"
    package_metadata="$("$AAPT" dump badging "$apk")"
    grep -Fq "package: name='ds.dsh.DeepSeek'" <<< "$package_metadata"
    unzip -p "$apk" assets/xposed_init | grep -Fxq "ds.dsh.DeepSeek.Main"
    apk_entries="$(unzip -Z1 "$apk")"
    if grep -Fxq 'META-INF/xposed/java_init.list' <<< "$apk_entries"; then
        echo "Universal APK unexpectedly contains a modern libxposed entry: $apk" >&2
        exit 1
    fi
    dex_count="$(unzip -Z1 "$apk" | grep -Ec '^classes([0-9]+)?[.]dex$' || true)"
    test "$dex_count" -ge 1
done

(cd "$DIST" && sha256sum *.apk > SHA256SUMS.txt)
echo
echo "Domestic and Google Play Universal validation APKs ($VERSION_NAME) are in $DIST"

#!/usr/bin/env bash
# Portable Open debug build. Produces an unsigned, zip-aligned APK for user-controlled signing.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRADLE_BIN="$(bash "$ROOT/scripts/gradle-bootstrap.sh")"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$ANDROID_SDK_ROOT" && -n "${PREFIX:-}" && -d "$PREFIX/tmp/android-sdk" ]]; then
    ANDROID_SDK_ROOT="$PREFIX/tmp/android-sdk"
fi
if [[ -z "$ANDROID_SDK_ROOT" || ! -d "$ANDROID_SDK_ROOT" ]]; then
    echo "Android SDK not found. Set ANDROID_SDK_ROOT or ANDROID_HOME." >&2
    exit 1
fi
export ANDROID_SDK_ROOT ANDROID_HOME="$ANDROID_SDK_ROOT"

BUILD_TOOLS="$(find "$ANDROID_SDK_ROOT/build-tools" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort -V | tail -n 1)"
ZIPALIGN="$BUILD_TOOLS/zipalign"
APKSIGNER="$BUILD_TOOLS/apksigner"
if [[ ! -x "$ZIPALIGN" || ! -x "$APKSIGNER" ]]; then
    echo "zipalign/apksigner not found under $ANDROID_SDK_ROOT/build-tools." >&2
    exit 1
fi
command -v zip >/dev/null 2>&1 || { echo "zip is required to remove the debug signature." >&2; exit 1; }
command -v sha256sum >/dev/null 2>&1 || { echo "sha256sum is required." >&2; exit 1; }

APP_GRADLE="$ROOT/compose-launcher/app/build.gradle.kts"
VERSION_NAME="$(sed -n 's/^[[:space:]]*versionName = "\([^"]*\)".*/\1/p' "$APP_GRADLE" | head -n 1)"
VERSION_CODE="$(sed -n 's/^[[:space:]]*versionCode = \([0-9][0-9]*\).*/\1/p' "$APP_GRADLE" | head -n 1)"
if [[ -z "$VERSION_NAME" || -z "$VERSION_CODE" ]]; then
    echo "Unable to read versionName/versionCode from $APP_GRADLE." >&2
    exit 1
fi

cd "$ROOT/compose-launcher"
"$GRADLE_BIN" --no-daemon --stacktrace \
    -PprotectedBuild=false \
    -PlocalApiIncluded=false \
    :app:assembleDebug

BUILT_APK="$ROOT/compose-launcher/app/build/outputs/apk/debug/app-debug.apk"
test -s "$BUILT_APK" || { echo "Expected APK was not produced: $BUILT_APK" >&2; exit 1; }

OUT_DIR="$ROOT/out"
mkdir -p "$OUT_DIR"
TEMP_APK="$OUT_DIR/.YunSeek-$VERSION_NAME-unsigned.apk"
FINAL_APK="$OUT_DIR/YunSeek-$VERSION_NAME-unsigned-aligned.apk"
cp "$BUILT_APK" "$TEMP_APK"
zip -q -d "$TEMP_APK" 'META-INF/*.SF' 'META-INF/*.RSA' 'META-INF/*.EC' 'META-INF/MANIFEST.MF' 2>/dev/null || true
"$ZIPALIGN" -f -p 4 "$TEMP_APK" "$FINAL_APK"
"$ZIPALIGN" -c -p 4 "$FINAL_APK"
if "$APKSIGNER" verify "$FINAL_APK" >/dev/null 2>&1; then
    echo "Output unexpectedly retains an APK signature: $FINAL_APK" >&2
    exit 1
fi
rm -f "$TEMP_APK"
(
    cd "$OUT_DIR"
    sha256sum "YunSeek-$VERSION_NAME-unsigned-aligned.apk" > SHA256.txt
)
echo "BUILD_DONE"
echo "Version: $VERSION_NAME ($VERSION_CODE)"
echo "APK: $FINAL_APK"
echo "SHA256: $(cat "$OUT_DIR/SHA256.txt")"

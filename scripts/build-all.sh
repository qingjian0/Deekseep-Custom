#!/usr/bin/env bash
# Portable Open build: unsigned + zip-aligned APK. Sign with your own keystore separately.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
bash "$ROOT/scripts/gradle-open-debug.sh"
VERSION_NAME="$(sed -n 's/^[[:space:]]*versionName = "\([^"]*\)".*/\1/p' \
    "$ROOT/compose-launcher/app/build.gradle.kts" | head -n 1)"
APK="$ROOT/out/YunSeek-$VERSION_NAME-unsigned-aligned.apk"
test -s "$APK"
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$SDK_ROOT" && -n "${PREFIX:-}" && -d "$PREFIX/tmp/android-sdk" ]]; then
    SDK_ROOT="$PREFIX/tmp/android-sdk"
fi
if [[ -z "$SDK_ROOT" || ! -d "$SDK_ROOT/build-tools" ]]; then
    echo "Android SDK build-tools directory is unavailable." >&2
    exit 1
fi
BUILD_TOOLS="$(find "$SDK_ROOT/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n 1)"
test -x "$BUILD_TOOLS/apksigner" || { echo "apksigner is unavailable." >&2; exit 1; }
if "$BUILD_TOOLS/apksigner" verify "$APK" >/dev/null 2>&1; then
    echo "ERROR: expected an unsigned APK, but a signature was detected." >&2
    exit 1
fi
echo "OK: unsigned + zipalign -> $APK"

#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 3 ]]; then
  echo "usage: package-dexkit-native.sh <dexkit-stage> <xstage-dir> <unsigned-apk>" >&2
  exit 2
fi

DEXKIT_STAGE="$(cd "$1" && pwd)"
XSTAGE="$2"
UNSIGNED_APK="$(mkdir -p "$(dirname "$3")" && cd "$(dirname "$3")" && pwd)/$(basename "$3")"
ABIS=(arm64-v8a armeabi-v7a x86 x86_64)

mkdir -p "$XSTAGE/lib"
for abi in "${ABIS[@]}"; do
  source_lib="$DEXKIT_STAGE/jni/$abi/libdexkit.so"
  test -s "$source_lib" || {
    echo "DexKit native library missing for ABI $abi: $source_lib" >&2
    exit 1
  }
  mkdir -p "$XSTAGE/lib/$abi"
  install -m 0644 "$source_lib" "$XSTAGE/lib/$abi/libdexkit.so"
done

# Store JNI libraries uncompressed; zipalign will place their payloads on 16 KiB boundaries.
(
  cd "$XSTAGE"
  zip -q -0 -r "$UNSIGNED_APK" lib
)
echo "DexKit JNI payload packaged for: ${ABIS[*]}"

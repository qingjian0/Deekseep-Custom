#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$ANDROID_SDK_ROOT" || ! -d "$ANDROID_SDK_ROOT" ]]; then
    echo "ANDROID_SDK_ROOT/ANDROID_HOME is required for org.json regression tests." >&2
    exit 2
fi
ANDROID_JAR="$(find "$ANDROID_SDK_ROOT/platforms" -mindepth 2 -maxdepth 2 -name android.jar -type f | sort -V | tail -n 1)"
test -s "$ANDROID_JAR" || { echo "Android platform android.jar not found." >&2; exit 2; }

OUT="$ROOT/module/build/native-json-patch-reducer-test"
mkdir -p "$OUT"
javac -source 8 -target 8 -nowarn -cp "$ANDROID_JAR" -d "$OUT" \
    "$ROOT/module/src/ds/dsh/DeepSeek/NativeJsonPatchReducer.java" \
    "$ROOT/module/tests/ds/dsh/DeepSeek/NativeJsonPatchReducerRegressionTest.java"
java -cp "$OUT:$ANDROID_JAR" ds.dsh.DeepSeek.NativeJsonPatchReducerRegressionTest

python3 - "$ROOT/module/src/ds/dsh/DeepSeek/NativeApiPatchDecoder.java" \
    "$ROOT/module/src/ds/dsh/DeepSeek/NativeJsonPatchReducer.java" <<'PY'
from pathlib import Path
import sys
decoder, reducer = (Path(p).read_text(encoding="utf-8") for p in sys.argv[1:])
if "NativeJsonPatchReducer" in decoder:
    raise SystemExit("Reducer must remain disconnected until code289 wire-operation mapping is proven.")
for token in ('"add"', '"remove"', '"replace"', '"move"', '"copy"', '"test"'):
    if token not in reducer:
        raise SystemExit("Normalized JSON Patch operation missing: " + token)
print("Native JSON Patch reducer boundary checks passed")
PY

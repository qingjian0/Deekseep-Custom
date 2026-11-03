#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MODULE="$ROOT/module"
OUT="$MODULE/build/hostmap-regression"
CLASSES="$OUT/classes"
RESOURCES="$OUT/resources"
rm -rf "$OUT"
mkdir -p "$CLASSES" "$RESOURCES"

for code in 287 289 290; do
    table="$MODULE/hostmap/hostmap${code}.txt.gz"
    test -s "$table"
    gzip -t "$table"
    stats="$(gzip -cd "$table" | awk -v code="$code" '
        BEGIN { classes=0; fields=0; methods=0; relocations=0; aliases=0; current=0; bad=0 }
        /^C / { if (NF < 3) bad=1; classes++; current=1; next }
        /^F / { if (!current || NF < 2 || NF > 3) bad=1; fields++; next }
        /^M / { if (!current || NF < 4) bad=1; methods++; next }
        /^N / { if (index($0, "(") == 0 || index($0, ")") == 0) bad=1; relocations++; next }
        /^T / { if (NF < 3) bad=1; aliases++; next }
        END {
            if (bad || classes == 0) exit 1
            if (code == 287 && aliases != 0) exit 1
            if ((code == 289 || code == 290) && aliases == 0) exit 1
            printf "classes=%d fields=%d methods=%d relocations=%d aliases=%d", classes, fields, methods, relocations, aliases
        }')"
    printf 'hostmap%s: %s\n' "$code" "$stats"
    cp "$table" "$RESOURCES/"
done

javac -source 8 -target 8 -nowarn -d "$CLASSES" \
    "$MODULE/tests/hostmap/support/ds/dsh/DeepSeek/Main.java" \
    "$MODULE/tests/hostmap/support/ds/dsh/DeepSeek/HostCompat.java" \
    "$MODULE/src/ds/dsh/DeepSeek/HostRemap.java" \
    "$MODULE/tests/hostmap/ds/dsh/DeepSeek/HostRemapRegressionTest.java"

for code in 287 289 290; do
    java -cp "$CLASSES:$RESOURCES" ds.dsh.DeepSeek.HostRemapRegressionTest "$code"
done

python - "$MODULE/src/ds/dsh/DeepSeek/HostCompat.java" <<'PY'
import pathlib
import re
import sys

source = pathlib.Path(sys.argv[1]).read_text(encoding="utf-8")
branch = re.search(r"if \(code271 != 0\) \{(.*?)\n        \}", source, re.S)
if branch is None:
    raise SystemExit("2.7.x host detection branch is missing")
if not re.search(r"googlePlay\s*=\s*code271\s*==\s*290", branch.group(1)):
    raise SystemExit("2.7.2 code290 must set the Google Play channel flag")
if not re.search(r"return googlePlay;", source):
    raise SystemExit("isGooglePlay must expose the selected host channel")
print("HostCompat 2.7.x channel classification regression OK")
PY

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

python - "$MODULE/src/ds/dsh/DeepSeek/HostCompat.java" "$MODULE/src/ds/dsh/DeepSeek/Main.java" <<'PY'
import pathlib
import re
import sys

source = pathlib.Path(sys.argv[1]).read_text(encoding="utf-8")
branch = re.search(r"if \(code271 != 0\) \{(.*?)\n        \}", source, re.S)
if branch is None:
    raise SystemExit("2.7.x host detection branch is missing")
if re.search(r"googlePlay\s*=\s*code271", branch.group(1)):
    raise SystemExit("2.7.x channel detection must not alter the legacy 2.3.x symbol-mapping selector")
if not re.search(r"static boolean isGooglePlay\(\)\s*\{\s*return googlePlay;", source):
    raise SystemExit("isGooglePlay must remain the legacy symbol-mapping selector")
if not re.search(r"static boolean isInternationalChannel\(\)\s*\{\s*return googlePlay \|\| isIntl27\(\);", source):
    raise SystemExit("international-channel detection must include code290 independently")
if not re.search(r"channel=.*isInternationalChannel\(\)", source):
    raise SystemExit("diagnostics must report the actual international channel")
if not re.search(r"return v271 && hostCode == 290;", source):
    raise SystemExit("isIntl27 must identify only the 2.7.2 international host")
print("HostCompat 2.7.x channel classification regression OK")

main = pathlib.Path(sys.argv[2]).read_text(encoding="utf-8")
host_v271 = pathlib.Path(sys.argv[1]).with_name("HostV271.java").read_text(encoding="utf-8")
if not re.search(r"static boolean isV271LimitedWhitelistSupported\\(\\)\\s*\\{\\s*return isV271Limited\\(\\) && hostCode == 287;", source):
    raise SystemExit("the limited 2.7.x fallback whitelist must be restricted to code287")
if not re.search(r"return \\(v271 && \\(HostRemap\\.active\\(\\) \\|\\| hostCode == 287\\)\\)", source):
    raise SystemExit("2.7.2 without its mapping table must not be reported as a maintained feature runtime")
limited = re.search(r"if \\(HostCompat\\.isV271Limited\\(\\)\\) \\{(.*?)\\n        \\}", main, re.S)
if limited is None or "isV271LimitedWhitelistSupported()" not in limited.group(1):
    raise SystemExit("Main must refuse the 2.7.1 whitelist when a 2.7.2 mapping is unavailable")
if "if (!HostCompat.isV271LimitedWhitelistSupported())" not in host_v271:
    raise SystemExit("HostV271 must independently guard its code287-only fallback")
print("HostCompat limited fallback version isolation regression OK")

probe = pathlib.Path(sys.argv[1]).with_name("OpenNativeCompletionProbe.java").read_text(encoding="utf-8")
if "HostRemap.forName(name, false, loader)" in probe:
    raise SystemExit("native probe must not translate raw DEX class names")
if "appendLoaderChain(out, loader)" not in probe:
    raise SystemExit("native probe must report the supplied class-loader hierarchy")
if "dex.scan.scope" not in probe or "split/dynamic loaders are not recursively enumerated" not in probe:
    raise SystemExit("native probe must state its single-loader DEX coverage boundary")
if probe.count("HostRemap.forDexName(name, loader)") != 1:
    raise SystemExit("native probe must load each eligible DEX class exactly once in its first pass")
if "scannedTypes.add(owner)" not in probe or "for (Class<?> owner : scannedTypes)" not in probe:
    raise SystemExit("native probe manager inventory must reuse first-pass DEX-loaded classes")
main = pathlib.Path(sys.argv[2]).read_text(encoding="utf-8")
if "HostRemap.forName(nm, false, cl)" in main:
    raise SystemExit("transport structural scan must not translate raw DEX class names")
if "HostRemap.forDexName(nm, cl)" not in main:
    raise SystemExit("transport structural scan must use direct DEX loading")
if not re.search(r"static Class<\?> forDexName\(String dexName, ClassLoader loader\).*?Class\.forName\(dexName, false, loader\)", pathlib.Path(sys.argv[1]).with_name("HostRemap.java").read_text(encoding="utf-8"), re.S):
    raise SystemExit("HostRemap.forDexName must load exact names without initialization")
print("Runtime DEX class-name namespace regression OK")
PY

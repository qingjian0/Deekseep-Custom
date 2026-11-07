#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/module/build/roi-invoke-reachability-test"
mkdir -p "$OUT"
javac -source 8 -target 8 -nowarn -d "$OUT" \
 "$ROOT/module/src/ds/dsh/DeepSeek/RoiDexInvokeReachability.java" \
 "$ROOT/module/tests/roidexinvokereachability/ds/dsh/DeepSeek/RoiDexInvokeReachabilityTest.java"
java -cp "$OUT" ds.dsh.DeepSeek.RoiDexInvokeReachabilityTest
python3 - "$ROOT/module/src/ds/dsh/DeepSeek/RoiDexApkCli.java" <<'PY'
from pathlib import Path
import sys
s=Path(sys.argv[1]).read_text(encoding="utf-8")
for label,token in [("CFG call expansion","method.reachableInvokes"),("static inventory",'stringArray(method.invokes)'),("dead invoke report",'stringArray(method.unreachableInvokes)'),("completeness flag",'invoke_reachability_complete')]:
 if token not in s: raise SystemExit("missing ROI reachability contract: "+label)
print("ROI invoke call-graph integration regression: PASS")
PY

#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CLI="$ROOT/module/src/ds/dsh/DeepSeek/RoiDexApkCli.java"

python3 - "$CLI" <<'PY'
from pathlib import Path
import sys

source = Path(sys.argv[1]).read_text(encoding="utf-8")
checks = {
    "bounded analysis budget remains 400": "MAX_ANALYSIS_METHODS = 400" in source,
    "fourth-hop reserve is explicit": "MAX_FOURTH_HOP_RESERVE = 64" in source,
    "hop three stops before consuming the reserve":
        "expandedMethods.size() >= MAX_ANALYSIS_METHODS - MAX_FOURTH_HOP_RESERVE" in source,
    "hop four can consume the remaining bounded budget":
        "if (expandedMethods.size() >= MAX_ANALYSIS_METHODS) break;" in source,
    "report exposes the reserved budget":
        'field(out, "fourth_level_reserved_budget", Integer.toString(MAX_FOURTH_HOP_RESERVE), true);' in source,
}
missing = [label for label, ok in checks.items() if not ok]
if missing:
    raise SystemExit("ROI APK traversal budget regression: " + ", ".join(missing))
print("ROI APK fourth-hop budget reservation regression: PASS")
PY

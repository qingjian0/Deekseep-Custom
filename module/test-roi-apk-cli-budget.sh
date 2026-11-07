#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CLI="$ROOT/module/src/ds/dsh/DeepSeek/RoiDexApkCli.java"
EVIDENCE="$ROOT/module/src/ds/dsh/DeepSeek/RoiDexCandidateEvidence.java"

python3 - "$CLI" "$EVIDENCE" <<'PY'
from pathlib import Path
import sys

source = Path(sys.argv[1]).read_text(encoding="utf-8")
evidence = Path(sys.argv[2]).read_text(encoding="utf-8")
checks = {
    "bounded analysis budget remains 480": "MAX_ANALYSIS_METHODS = 544" in source,
    "selected-method analyzer accepts the bounded 480-method budget": "MAX_SELECTED_METHODS = 544" in evidence and "selectedMethodDescriptors.size() > MAX_SELECTED_METHODS" in evidence,
    "fourth-hop reserve is explicit": "MAX_FOURTH_HOP_RESERVE = 64" in source,
    "fifth-hop reserve is explicit": "MAX_FIFTH_HOP_RESERVE = 64" in source,
    "sixth-hop reserve is explicit": "MAX_SIXTH_HOP_RESERVE = 64" in source,
    "hop three stops before consuming both later-hop reserves":
        "expandedMethods.size() >= MAX_ANALYSIS_METHODS - MAX_FOURTH_HOP_RESERVE - MAX_FIFTH_HOP_RESERVE - MAX_SIXTH_HOP_RESERVE" in source,
    "hop four stops before consuming the fifth-hop reserve":
        "expandedMethods.size() >= MAX_ANALYSIS_METHODS - MAX_FIFTH_HOP_RESERVE - MAX_SIXTH_HOP_RESERVE" in source and
        "expandedMethods.size() >= MAX_ANALYSIS_METHODS - MAX_SIXTH_HOP_RESERVE" in source,
    "fifth-hop traversal is present": "fifthLevelCalleeMethods.add(callee)" in source,
    "sixth-hop traversal is present": "sixthLevelCalleeMethods.add(callee)" in source,
    "hop four can consume the remaining bounded budget":
        "if (expandedMethods.size() >= MAX_ANALYSIS_METHODS) break;" in source,
    "report exposes the reserved budget":
        'field(out, "fourth_level_reserved_budget", Integer.toString(MAX_FOURTH_HOP_RESERVE), true);' in source and
        'field(out, "fifth_level_reserved_budget", Integer.toString(MAX_FIFTH_HOP_RESERVE), true);' in source and
        'field(out, "sixth_level_reserved_budget", Integer.toString(MAX_SIXTH_HOP_RESERVE), true);' in source,
}
missing = [label for label, ok in checks.items() if not ok]
if missing:
    raise SystemExit("ROI APK traversal budget regression: " + ", ".join(missing))
print("ROI APK fourth-hop budget reservation regression: PASS")
PY

#!/usr/bin/env bash
set -euo pipefail

SOURCE="module/src/ds/dsh/DeepSeek/HookSessionManagement.java"
[[ -f "$SOURCE" ]] || { echo "missing $SOURCE" >&2; exit 1; }

FIRST="$(sed -n '/private void hookV234LocalSessionDeletedFlow/,/private void hookV234LocalSessionDeletionPresenter/p' "$SOURCE")"
SECOND="$(sed -n '/private void hookV234LocalSessionDeletionPresenter/,/code249 has a second code-1 deletion path/p' "$SOURCE")"

grep -Fq 'private void hookV234LocalSessionDeletedFlow(final ClassLoader cl) throws Throwable' <<<"$FIRST"
grep -Fq 'new NoSuchMethodException("za1.M 2.3.4 local session deletion guard")' <<<"$FIRST"
grep -Fq 'Main.log("hookV234LocalSessionDeletedFlow failed: " + t);' <<<"$FIRST"
grep -Fq 'throw t;' <<<"$FIRST"

grep -Fq 'private void hookV234LocalSessionDeletionPresenter(final ClassLoader cl) throws Throwable' <<<"$SECOND"
grep -Fq 'new NoSuchMethodException("2.3.4 local session deletion presenter target")' <<<"$SECOND"
grep -Fq 'Main.log("hookV234LocalSessionDeletionPresenter failed: " + t);' <<<"$SECOND"
grep -Fq 'throw t;' <<<"$SECOND"

echo "PASS: 2.3.4 session-deletion installers reject zero matches and propagate failures"

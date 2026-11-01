#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ACTIVITY="$ROOT/compose-launcher/app/src/main/java/ds/dsh/DeepSeek/SettingsActivity.kt"

test -s "$ACTIVITY"

# Guard against duplicate Activity handlers introduced by broad source rewrites.
for declaration in launchImport exportData importData applyImport; do
  count="$(grep -Ec "^[[:space:]]*private (suspend )?fun ${declaration}\\(" "$ACTIVITY" || true)"
  if [[ "$count" != "1" ]]; then
    echo "Expected exactly one ${declaration} declaration, found ${count}." >&2
    exit 1
  fi
done

# SAF result contracts must remain registered on the Activity before STARTED.
grep -Fq 'ActivityResultContracts.CreateDocument("application/zip")' "$ACTIVITY"
grep -Fq 'ActivityResultContracts.CreateDocument("application/json")' "$ACTIVITY"
grep -Fq 'ActivityResultContracts.OpenDocument()' "$ACTIVITY"

# Blocking SAF and archive/file operations must leave the main thread.
grep -Fq 'withContext(Dispatchers.IO)' "$ACTIVITY"
grep -Fq 'contentResolver.openOutputStream(uri, "wt")' "$ACTIVITY"
grep -Fq 'contentResolver.openInputStream(uri)' "$ACTIVITY"
grep -Fq 'backupRepository.parse(it)' "$ACTIVITY"
grep -Fq 'backupRepository.restore(' "$ACTIVITY"

# Work launched from the Activity must be lifecycle-bound, not detached raw threads/handlers.
grep -Fq 'lifecycleScope.launch {' "$ACTIVITY"
grep -Fq 'delay(900L)' "$ACTIVITY"
if grep -Eq '(^|[[:space:]])Thread[[:space:]]*\{|main\.post|main\.postDelayed|android\.os\.Handler|android\.os\.Looper' "$ACTIVITY"; then
  echo "Activity contains detached worker threads or Handler callbacks." >&2
  exit 1
fi

# Coroutine cancellation must not be converted into an ordinary operation failure.
grep -Fq 'catch (cancelled: CancellationException)' "$ACTIVITY"
grep -Fq 'throw cancelled' "$ACTIVITY"
grep -Fq 'finally {' "$ACTIVITY"

echo "Launcher Activity SAF/lifecycle architecture regression passed."

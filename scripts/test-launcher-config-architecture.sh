#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ACTIVITY="$ROOT/compose-launcher/app/src/main/java/ds/dsh/DeepSeek/SettingsActivity.kt"
REPOSITORY="$ROOT/compose-launcher/app/src/main/java/ds/dsh/DeepSeek/LauncherConfigRepository.kt"
VIEW_MODEL="$ROOT/compose-launcher/app/src/main/java/ds/dsh/DeepSeek/LauncherConfigViewModel.kt"

for source in "$ACTIVITY" "$REPOSITORY" "$VIEW_MODEL"; do
  test -s "$source"
done

if grep -Eq 'private fun bridgeRequest|ModuleConfigBridge[.]MODE_(EXPORT|IMPORT)|private var (bridgeStatus|configFiles)' "$ACTIVITY"; then
  echo "Activity still owns config IPC or config screen state." >&2
  exit 1
fi

grep -Fq 'val configState by configViewModel.state.collectAsStateWithLifecycle()' "$ACTIVITY"
grep -Fq 'repository.request(ModuleConfigBridge.MODE_EXPORT)' "$VIEW_MODEL"
grep -Fq 'repository.request(ModuleConfigBridge.MODE_IMPORT' "$VIEW_MODEL"
grep -Fq 'StateFlow<LauncherConfigUiState>' "$VIEW_MODEL"
grep -Fq 'private val operationMutex = Mutex()' "$VIEW_MODEL"
grep -Fq 'catch (cancelled: CancellationException)' "$VIEW_MODEL"
grep -Fq 'suspendCancellableCoroutine' "$REPOSITORY"
grep -Fq 'ModuleConfigBridge.request(mode, json, reply)' "$REPOSITORY"
grep -Fq 'root.optString("format") == CONFIG_FORMAT' "$REPOSITORY"
grep -Fq 'MAX_CONFIG_FILE_BYTES = 96 * 1024' "$REPOSITORY"
grep -Fq 'repository.parseConfigFiles(json)' "$VIEW_MODEL"
if grep -Fq 'runCatching { repository.parseConfigFiles(json) }' "$VIEW_MODEL"; then
  echo "Malformed config payloads must not be silently converted into an empty file map." >&2
  exit 1
fi

echo "Launcher config repository/ViewModel boundary regression passed."

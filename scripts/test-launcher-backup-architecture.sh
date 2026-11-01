#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ACTIVITY="$ROOT/compose-launcher/app/src/main/java/ds/dsh/DeepSeek/SettingsActivity.kt"
REPOSITORY="$ROOT/compose-launcher/app/src/main/java/ds/dsh/DeepSeek/LauncherBackupRepository.kt"

test -s "$ACTIVITY"
test -s "$REPOSITORY"

grep -Fq 'private val backupRepository = LauncherBackupRepository()' "$ACTIVITY"
grep -Fq 'backupRepository.writeZip(' "$ACTIVITY"
grep -Fq 'backupRepository.parse(it)' "$ACTIVITY"
grep -Fq 'backupRepository.restore(' "$ACTIVITY"

if grep -Eq 'private fun (writeZip|parseZip|readLimited)|private class ImportPlan|ZipInputStream\(' "$ACTIVITY"; then
  echo "Activity still owns ZIP codec or import parsing." >&2
  exit 1
fi

grep -Fq 'ZIP_LAUNCHER_PREFS' "$REPOSITORY"
grep -Fq 'sharedPreferences: Map<String, ByteArray>' "$REPOSITORY"
grep -Fq 'plan.sharedPreferences' "$REPOSITORY"
grep -Fq 'safeRelativePath' "$REPOSITORY"
grep -Fq 'Os.rename(temporary.absolutePath, target.absolutePath)' "$REPOSITORY"
grep -Fq 'MAX_IMPORT_BYTES = 64 * 1024 * 1024' "$REPOSITORY"
grep -Fq 'MAX_ENTRIES = 4096' "$REPOSITORY"

echo "Launcher backup repository architecture regression passed."

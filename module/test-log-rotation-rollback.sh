#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
STORE="$ROOT/module/src/ds/dsh/DeepSeek/DshFileLogStore.kt"

test -s "$STORE"
grep -Fq 'val stagedName = "$category.log.rotate-old-${System.nanoTime()}"' "$STORE"
grep -Fq 'val moved = mutableListOf<Pair<Uri, String>>()' "$STORE"
grep -Fq 'for ((uri, originalName) in moved.asReversed())' "$STORE"
grep -Fq 'val moved = mutableListOf<Pair<File, File>>()' "$STORE"
grep -Fq 'for ((source, destination) in moved.asReversed())' "$STORE"
grep -Fq 'if (oldest != null) runCatching { resolver.delete(oldest, null, null) }' "$STORE"
grep -Fq 'if (stagedOldest.exists()) stagedOldest.delete()' "$STORE"
grep -Fq 'val deleted = runCatching { resolver.delete(oldest, null, null) > 0 }' "$STORE"
grep -Fq 'if (stagedOldest.exists() && !stagedOldest.delete())' "$STORE"

echo "Log rotation rollback source guard passed"

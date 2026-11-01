#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
STORE="$ROOT/module/src/ds/dsh/DeepSeek/AgentToolTraceStore.java"

test -s "$STORE"
grep -Fq 'Os.rename(temporary.getAbsolutePath(), target.getAbsolutePath())' "$STORE"
grep -Fq 'JSONArray recovered = readLedger(temporary)' "$STORE"
grep -Fq 'JSONArray values = readLedger(target)' "$STORE"
grep -Fq 'Keep the temporary snapshot and retry on the next access.' "$STORE"
grep -Fq 'Keep a fully-synced temporary snapshot when replacement fails.' "$STORE"
grep -Fq 'trim(fallbackInput.trim(), MAX_INPUT_CHARS)' "$STORE"
grep -Fq 'file.length() > MAX_STORE_BYTES' "$STORE"
if grep -Fq 'target.delete()' "$STORE"; then
    echo "Unsafe delete-before-replace path detected." >&2
    exit 1
fi

echo "Agent tool trace persistence source guard passed"

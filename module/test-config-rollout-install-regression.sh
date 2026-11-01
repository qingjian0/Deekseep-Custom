#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SOURCE="$ROOT_DIR/module/src/ds/dsh/DeepSeek/HookConfigRollout.java"

test -s "$SOURCE"
grep -Fq 'private static final Set<Method> INSTALLED_ROLLOUT_HOOKS' "$SOURCE"
grep -Fq 'Collections.newSetFromMap(new ConcurrentHashMap<Method, Boolean>())' "$SOURCE"
grep -Fq 'synchronized (INSTALLED_ROLLOUT_HOOKS)' "$SOURCE"
grep -Fq 'if (INSTALLED_ROLLOUT_HOOKS.contains(method)) return false;' "$SOURCE"
grep -Fq 'INSTALLED_ROLLOUT_HOOKS.add(method);' "$SOURCE"

remote_body="$(sed -n '/void hookRemoteConfigHotUpdates/,/^    }/p' "$SOURCE")"
grep -Fq 'hookRolloutMethodOnce(method, new Hooker()' <<< "$remote_body"
grep -Fq 'ownerMatches == 0' <<< "$remote_body"
grep -Fq 'matched == 0' <<< "$remote_body"
if grep -Fq 'Main.MODULE.hook(method)' <<< "$remote_body"; then
  echo "remote rollout installer bypasses the idempotent registration helper" >&2
  exit 1
fi

attachment_body="$(sed -n '/void hookAttachmentGuidePromptRollout/,/^    }/p' "$SOURCE")"
grep -Fq 'hookRolloutMethodOnce(method, new Hooker()' <<< "$attachment_body"
grep -Fq 'count == 0' <<< "$attachment_body"
if grep -Fq 'Main.MODULE.hook(method)' <<< "$attachment_body"; then
  echo "attachment rollout installer bypasses the idempotent registration helper" >&2
  exit 1
fi

echo "Config rollout install retry regression checks passed."

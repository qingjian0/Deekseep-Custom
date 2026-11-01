#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SOURCE="$ROOT/module/src/ds/dsh/DeepSeek/HookAccountLoginSecurity.java"
test -s "$SOURCE"

METHOD="$(sed -n '/void installLoginRiskLogger(final ClassLoader cl)/,/private static Throwable appendOptionalHookFailure/p' "$SOURCE")"
test -n "$METHOD"

grep -Fq 'appendOptionalHookFailure(optionalFailure, error)' <<<"$METHOD"
grep -Fq 'if (optionalFailure != null) throw optionalFailure;' <<<"$METHOD"
grep -Fq 'bizCodeKindsInstalled + "/8"' <<<"$METHOD"
grep -Fq 'message_present=' <<<"$METHOD"
grep -Fq 'detail_present=' <<<"$METHOD"
grep -Fq 'google http failed type=' <<<"$METHOD"

if grep -Eq 'google http (ok|failed) url=|hidden-activity " \+ tag|hidden-activity .*\+ kind|\+ " msg=" \+ message|\+ String\.valueOf\(error\.getMessage\(\)\)' <<<"$METHOD"; then
  echo "Login diagnostics contain raw URL, message, or exception detail." >&2
  exit 1
fi

grep -Fq 'if (next instanceof VirtualMachineError)' "$SOURCE"
grep -Fq 'if (next instanceof ThreadDeath)' "$SOURCE"
# Hook installation failures must reach FeatureHookInstallRegistry instead of being logged and swallowed.
REGIONAL_LOGIN="$(sed -n '/void hookRegionalLoginUnlock/,/Injects the password\/email login option/p' "$SOURCE")"
LOGIN_ENTRY="$(sed -n '/void hookLoginEntryPasswordUnlock/,/interface CandidateLoginCallback/p' "$SOURCE")"
grep -Fq 'void hookRegionalLoginUnlock(final ClassLoader cl) throws Throwable' <<<"$REGIONAL_LOGIN"
grep -Fq 'hookRegionalLoginUnlock failed:' <<<"$REGIONAL_LOGIN"
grep -Fq 'throw t;' <<<"$REGIONAL_LOGIN"
grep -Fq 'void hookLoginEntryPasswordUnlock(final ClassLoader cl) throws Throwable' <<<"$LOGIN_ENTRY"
grep -Fq 'hookLoginEntryPasswordUnlock failed:' <<<"$LOGIN_ENTRY"
grep -Fq 'throw t;' <<<"$LOGIN_ENTRY"


echo "Login diagnostics privacy and install-failure regression: PASS"

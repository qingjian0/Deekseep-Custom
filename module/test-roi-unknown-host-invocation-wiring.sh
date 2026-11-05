#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MAIN="$ROOT/module/src/ds/dsh/DeepSeek/Main.java"
DISCOVERY="$ROOT/module/src/ds/dsh/DeepSeek/RoiDexKitDiscovery.java"
POLICY="$ROOT/module/src/ds/dsh/DeepSeek/RoiUnknownHostInvocationProbePolicy.java"
HOOK_POLICY="$ROOT/module/src/ds/dsh/DeepSeek/RoiUnknownHostHookPolicy.java"
INSTALLER="$ROOT/module/src/ds/dsh/DeepSeek/RoiUnknownHostInvocationProbeInstaller.java"

for file in "$MAIN" "$DISCOVERY" "$POLICY" "$HOOK_POLICY" "$INSTALLER"; do
  test -s "$file"
done

grep -Fq 'deekseep_roi_unknown_host_invocation_probe_enabled' "$MAIN"
grep -Fq 'ROI_UNKNOWN_HOST_INVOCATION_PROBE_INSTALLER.install(' "$MAIN"
grep -Fq 'hook(target).intercept(new Hooker()' "$MAIN"
grep -Fq 'return chain.proceed();' "$MAIN"
grep -Fq 'candidate.roleVerified' "$POLICY"
grep -Fq 'candidate.callGraphConfirmed' "$POLICY"
grep -Fq 'candidate.exactRuntimeMatch' "$POLICY"
grep -Fq 'return false;' "$HOOK_POLICY"

# DexKit remains a discovery-only producer: static role/call-graph hints must not
# become verified evidence or call the unknown-host invocation installer.
grep -Fq 'false, false, Collections.<String>emptySet(), evidence' "$DISCOVERY"
if grep -Fq 'installRoiUnknownHostInvocationProbe(' "$DISCOVERY"; then
  echo "FAIL: DexKit discovery must not directly install unknown-host invocation probes" >&2
  exit 1
fi

# The Main entry point is deliberately dormant until a reviewed evidence producer exists.
count="$(grep -Fc 'installRoiUnknownHostInvocationProbe(' "$MAIN")"
test "$count" -eq 1

echo "ROI unknown-host invocation wiring invariants passed"

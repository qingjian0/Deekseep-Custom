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
grep -Fq 'static boolean allowsProbe' "$POLICY"
grep -Fq 'return false;' "$HOOK_POLICY"

COORDINATOR="$ROOT/module/src/ds/dsh/DeepSeek/RoiUnknownHostAutoProbeCoordinator.java"
test -s "$COORDINATOR"
grep -Fq 'RoiUnknownHostAutoProbeCoordinator.registerCandidate' "$DISCOVERY" || true
grep -Fq 'registerRoiUnknownHostAutoProbeCandidate' "$DISCOVERY"
grep -Fq 'ROI_UNKNOWN_HOST_AUTO_PROBE.observeLoadedClass' "$MAIN"
grep -Fq 'RoiUnknownHostAutoProbeCoordinator.Installer' "$MAIN"
grep -Fq 'allowsProbe(true, candidate, runtime)' "$INSTALLER"

# Static hints may open only the transparent diagnostic lane; the functional-hook
# policy remains closed until an independently reviewed feature adapter is available.
if grep -Fq 'roleVerified=true' "$COORDINATOR"; then
  echo "FAIL: static role hints must not be promoted to verified role evidence" >&2
  exit 1
fi
if grep -Fq 'callGraphConfirmed=true' "$COORDINATOR"; then
  echo "FAIL: static graph hints must not be promoted to runtime call-graph evidence" >&2
  exit 1
fi

echo "ROI unknown-host automatic probe wiring invariants passed"

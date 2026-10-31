#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

javac -source 8 -target 8 -d "$TMP" \
  module/src/com/dsmod/probe/ActivationStatusPolicy.java \
  module/tests/com/dsmod/probe/ActivationStatusPolicyRegressionTest.java
java -cp "$TMP" com.dsmod.probe.ActivationStatusPolicyRegressionTest

python3 - <<'PY'
from pathlib import Path

main = Path("module/src/com/dsmod/probe/Main.java").read_text()
settings = Path("module/src/com/dsmod/probe/SettingsActivity.java").read_text()
launcher = Path("compose-launcher/app/src/main/java/com/dsmod/probe/SettingsActivity.kt").read_text()
provider = Path("module/src/com/dsmod/probe/XposedActivationProvider.java").read_text()
manifest = Path("compose-launcher/app/src/main/AndroidManifest.xml").read_text()

assert "deferBootstrapUntilApplicationAttach(param);" in main, "missing deferred bootstrap"
assert "private void deferBootstrapUntilApplicationAttach" in main, "missing attach hook helper"
assert "Looper mainLooper = Looper.getMainLooper();" in main, "missing Looper prerequisite"
assert main.index("if (mainLooper == null)") < main.index('System.setProperty("deekseep.module.injected.pid"'), "PID guard must follow Looper readiness"
assert "ACTIVATION_HEARTBEAT_LOOP" in main, "missing target process heartbeat loop"
assert "if (!BuildInfo.PROTECTED_BUILD || act == null) return;" not in main, "Open builds must report target heartbeat"
assert "if (!BuildInfo.PROTECTED_BUILD || context == null || stage == null) return;" not in main, "Open builds must report hook status"
assert "METHOD_REPORT_HOOK_STATUS" in main, "missing hook status reporting"
assert "XposedActivationProvider" in manifest, "Compose launcher manifest must declare activation provider"
assert "runFeatureInstall(" in main and main.count('runFeatureInstall("') >= 20, "feature installers are not isolated"
assert "FEATURE_HOOK_INSTALL_FAILURES.get() == 0" in main and "hooks_partial" in main, "partial hook installation is not reported distinctly"
assert "registeredHookCount()" in main and "hookInvocationCount()" in main, "hook telemetry counters are missing"
assert "if (framework || target)" not in settings, "framework connection must not imply target injection"
assert "ActivationStatusPolicy.classify(" in settings, "legacy activation UI bypasses evidence policy"
assert "ActivationStatusPolicy.classify(" in launcher, "Compose launcher activation UI bypasses evidence policy"
assert "return framework || target" not in launcher, "framework Binder must not imply target injection"
assert "模块未激活" not in launcher, "missing evidence must not be labelled as module disabled"
assert "HOOKS_PARTIAL" in launcher, "partial hook failures must be visible in launcher"
assert "recordTargetHeartbeat(context, callingUid" in provider, "target heartbeat must validate caller UID"
assert "uidOwnsPackage(context, callingUid, TARGET_PACKAGE)" in provider, "hook status must validate caller UID"
assert "HOOK_STATUS_FRESH_MS" in provider, "hook telemetry freshness is not bounded"

legacy = Path("module-legacy/compat/com/dsmod/probe/LegacyXposedModule.java").read_text()
assert "registeredHooks.incrementAndGet()" in legacy, "registered hook count missing"
assert "hookInvocations.incrementAndGet()" in legacy, "hook invocation count missing"
assert "hookRegistrationFailures.incrementAndGet()" in legacy, "hook registration failure count missing"
print("Activation and hook bootstrap source invariants: PASS")
PY

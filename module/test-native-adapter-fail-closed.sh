#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MAIN="$ROOT/module/src/ds/dsh/DeepSeek/Main.java"
STUB="$ROOT/module/src/ds/dsh/DeepSeek/z14.java"
FACADE="$ROOT/module/src/ds/dsh/DeepSeek/HookAccountLoginSecurity.java"
PROBE="$ROOT/module/src/ds/dsh/DeepSeek/OpenNativeCompletionProbe.java"
Z18="$ROOT/module/src/ds/dsh/DeepSeek/z18.java"
HOST_V271="$ROOT/module/src/ds/dsh/DeepSeek/HostV271.java"
CHAT="$ROOT/module/src/ds/dsh/DeepSeek/HookChatPipeline.java"
REPORT="$ROOT/module/src/ds/dsh/DeepSeek/NativeProbeReport.java"
REPORT_TEST="$ROOT/module/tests/nativeprobe/ds/dsh/DeepSeek/NativeProbeReportTest.java"
DISCOVERY="$ROOT/module/src/ds/dsh/DeepSeek/RoiHostDiscovery.java"

REPORT_OUT="$ROOT/module/build/native-probe-report-test"
mkdir -p "$REPORT_OUT"
javac -source 8 -target 8 -nowarn -d "$REPORT_OUT" "$REPORT" "$REPORT_TEST"
java -cp "$REPORT_OUT" ds.dsh.DeepSeek.NativeProbeReportTest

python - "$MAIN" "$STUB" "$FACADE" "$PROBE" "$Z18" "$HOST_V271" "$CHAT" "$REPORT" "$DISCOVERY" <<'PY'
import pathlib
import re
import sys

main, stub, facade, probe, z18, host_v271, chat, report, discovery = [
    pathlib.Path(path).read_text(encoding="utf-8") for path in sys.argv[1:]
]

def require(source, pattern, label):
    if not re.search(pattern, source, re.S):
        raise SystemExit("Missing native adapter fail-closed contract: " + label)

require(
    stub,
    r"static boolean available\(\)\s*\{\s*return false;\s*\}",
    "Open payload availability remains false",
)
require(
    stub,
    r"static Class<\?> payloadClass\(Context context, String name\)\s*\{\s*return null;\s*\}",
    "Open payload resolver remains unavailable",
)
require(
    main,
    r"@Override\s+public boolean isReady\(\)\s*\{\s*return BuildInfo\.LOCAL_API_INCLUDED\s*&&",
    "Backend readiness requires the native adapter build flag",
)
require(
    main,
    r"@Override\s+public boolean isReady\(\)\s*\{\s*return BuildInfo\.LOCAL_API_INCLUDED\s*&&\s*hostClassLoader != null && liveR92 != null && liveQ71 != null",
    "Backend readiness does not accept missing legacy transport or PoW objects",
)
require(
    main,
    r'if \(!HostCompat\.isV271\(\)\) installBestEffortHook\("network\.payload-capture"',
    "legacy transport capture is not installed for the 2.7.x family",
)
require(
    main,
    r'if \(!HostCompat\.isV271\(\)\) installBestEffortHook\("chat\.pow-manager-capture"',
    "legacy PoW capture is not installed for the 2.7.x family",
)
require(
    host_v271,
    r"static void installFeatureHooks\(final ClassLoader cl\)",
    "2.7.x limited whitelist remains an explicit separate hook path",
)
if "installPowManagerCapture" in host_v271 or "hookTransport(" in host_v271:
    raise SystemExit("2.7.x whitelist must not silently reuse legacy q71/transport capture")
require(
    chat,
    r"void installPowManagerCapture\(ClassLoader cl\)",
    "legacy q71 capture entry point remains identifiable for a future version-specific adapter",
)
require(
    main,
    r'if \(!BuildInfo\.LOCAL_API_INCLUDED && "Open"\.equals\(BuildInfo\.BUILD_EDITION\)\)\s*\{\s*throw new z2\.GatewayException\(503, "native_adapter_unavailable"',
    "Open completion requests fail with HTTP 503",
)
require(
    facade,
    r"Class<\?> cls = z14\.payloadClass\([^;]+;\s*java\.lang\.reflect\.Method m = cls == null \? null",
    "execution facade fails closed when payload class is absent",
)
if "installRetiredHostNotice(unsupported)" in main:
    raise SystemExit("unknown host discovery must not install the retired-version Activity notice hook")
if "RoiHostDiscovery.schedule(cl," not in main:
    raise SystemExit("unknown hosts must enter ROI read-only discovery before feature bootstrap returns")
if main.index("if (!HostCompat.supportsMaintainedFeatureRuntime())") > main.index('installHookStep("ui.native-dual-chat-root"'):
    raise SystemExit("generic Compose host hook must remain behind the maintained-host compatibility gate")
if "MAX_TOTAL_CLASS_ENTRIES = 100000" not in discovery or "MAX_CLASS_NAMES = 60000" not in discovery:
    raise SystemExit("unknown host DEX inventory must have hard entry and unique-name bounds")
if "current.getParent()" not in discovery or "BaseDexClassLoader" not in discovery or "dexElements" not in discovery:
    raise SystemExit("unknown host discovery must inventory the visible ClassLoader parent chain and DEX elements")
if "current.loadClass(" in discovery or "Class.forName(name" in discovery:
    raise SystemExit("unknown host discovery must not load arbitrary host classes")
if "Main.MODULE.hook(" in discovery or "hookNativeDualChatRoot" in discovery:
    raise SystemExit("unknown host discovery must not install hooks")
require(
    probe,
    r"Read-only runtime inventory",
    "probe remains explicitly read-only",
)

require(
    probe,
    r'HostCompat\.hostCode271\(\) != 289.*?Class\.forName\("ly1", false, loader\).*?Class\.forName\("wu1", false, loader\)',
    "runtime call observer is gated to the verified mainland 2.7.2/code289 request contract",
)
require(
    probe,
    r'installObservedMethod\(loader, "jw3", "g".*?installObservedMethod\(loader, "zn3", "o"',
    "runtime observer targets both independently discovered code289 call boundaries",
)
require(
    probe,
    r'Main\.MODULE\.hook\(target\)\.intercept\(new LegacyXposedModule\.Hooker\(\).*?chain\.proceed\(\).*?OBSERVATION_FRAME\.remove\(\)',
    "runtime observer is passive, preserves original execution, and clears per-thread correlation",
)
require(
    probe,
    r"parameters\[0\] == requestMarker.*?installed\.compareAndSet\(false, true\)",
    "runtime observer requires the exact request marker and makes at most one installation attempt",
)
require(
    probe,
    r"catch \(Throwable observerError\).*?Diagnostics must never prevent the original host method from running.*?chain\.proceed\(\)",
    "observer-side failures cannot prevent the original host method from running",
)
require(
    probe,
    r'observedArgumentTypes.*?value\.getClass\(\)\.getName\(\).*?observedStack.*?getClassName\(\).*?getMethodName\(\)',
    "runtime observer records argument types and stack symbols only",
)
for forbidden in ("getResult().toString()", "chain.getArg(0).toString()", "Authorization", "prompt", "requestBody"):
    if forbidden in probe:
        raise SystemExit("runtime observer must not serialize sensitive request data: " + forbidden)
require(
    probe,
    r"static void inspectHost\(ClassLoader loader\)",
    "Open host inventory entry point remains present",
)
inspect = probe.split("static void inspectHost(ClassLoader loader)", 1)[1].split(
    "/** Legacy object-capture entry point", 1
)[0]
require(
    inspect,
    r"catch \(VirtualMachineError fatal\).*?throw fatal;.*?catch \(ThreadDeath fatal\).*?throw fatal;.*?catch \(Throwable error\)",
    "fatal VM/control-flow errors escape the runtime inventory catch boundary",
)
require(
    inspect,
    r"try \{\s*installRuntimeObservation\(loader\);.*?finally \{\s*(?:(?://[^\n]*|/\*.*?\*/)\s*)*INSPECTING\.set\(false\);",
    "runtime inventory always releases its inspection guard after observer setup and reporting",
)
legacy_inspect = probe.split("static void inspect(ClassLoader loader, Object powManager", 1)[1].split(
    "static String status()", 1
)[0]
require(
    legacy_inspect,
    r"catch \(VirtualMachineError fatal\).*?throw fatal;.*?catch \(ThreadDeath fatal\).*?throw fatal;.*?catch \(Throwable error\)",
    "legacy inventory also propagates fatal VM/control-flow errors",
)
require(
    probe,
    r'line\(out, "report\.schema", "2"\)',
    "probe report schema is versioned",
)
require(
    probe,
    r'line\(out, "dex\.eligibleClassNameCount"',
    "probe reports eligible class-name coverage",
)
require(
    probe,
    r'line\(out, "dex\.classLoadFailureCount"',
    "probe reports class-load failures",
)
require(
    probe,
    r'line\(out, "probe\.elapsedMs"',
    "probe reports scan duration",
)
require(
    probe,
    r'transportReturn.*?\.class.*?returnType\.getName\(\).*?appendFields\(out, "transportReturn"',
    "probe inventories transport response type fields without invoking the response",
)
require(
    probe,
    r'if \(candidates < 4\).*?appendFields\(out, "transport" \+ candidates, owner, 1, 6\).*?appendFields\(out, "transportReturn" \+ candidates, returnType, 1, 6\)',
    "transport candidate detail is bounded to four methods and six fields per class",
)
require(
    probe,
    r'if \(managerCandidates < 6\).*?appendConstructors\(out, "manager" \+ managerCandidates, owner, 8\).*?manager\.detailCount.*?Math\.min\(managerCandidates, 6\)',
    "manager constructor detail is bounded while the complete typed-field count remains available",
)
require(
    probe,
    r'appendConstructors\(StringBuilder out, String prefix, Class<\?> type\).*?appendConstructors\(out, prefix, type, 24\).*?int maxDetails',
    "primary request constructor diagnostics retain their existing full detail bound",
)
require(
    probe,
    r'interfaces\(returnType\).*?interfaceMetadataError.*?returnInterfaces.*?appendFields\(out, "transportReturn"',
    "response interface metadata failures are isolated and reported without aborting the probe",
)
require(
    probe,
    r'appendFields\(out, prefix, type, maxDepth, 48\).*?int maxFields',
    "bounded field inventory preserves the wider default for primary request and manager details",
)
require(
    probe,
    r"if \(count >= maxFields\) break;.*?\}\s*if \(count >= maxFields\) break;",
    "candidate field detail limits are enforced across both the field loop and superclass traversal",
)
require(
    facade,
    r'z14\.payloadClass\(Main\.hostApplicationContext, "ds\.dsh\.DeepSeek\.z18"\)',
    "execution facade payload class target remains explicit",
)
if re.search(r"executeLocalApiCompletion\s*\(", z18):
    raise SystemExit("Repository z18 helper must not be mistaken for the Completion payload")

require(
    main,
    r'OpenNativeCompletionProbe\.scheduleHostInspection\(hostClassLoader\);',
    "native host inventory uses the explicit one-shot scheduler",
)
enabled_marker = "if (!new File(HookAccountLoginSecurity.LOCAL_API_ENABLED_FILE).exists()) return;"
scheduled_probe = "OpenNativeCompletionProbe.scheduleHostInspection(hostClassLoader);"
if enabled_marker not in main or scheduled_probe not in main:
    raise SystemExit("native host inventory must be present behind the Local API enablement check")
if main.index(enabled_marker) > main.index(scheduled_probe):
    raise SystemExit("native host inventory must be gated by explicit Local API enablement")
require(
    probe,
    r'static void scheduleHostInspection\(final ClassLoader loader\)\s*\{.*?SCHEDULED\.compareAndSet\(false, true\).*?worker\.setDaemon\(true\).*?worker\.start\(\);',
    "native host inventory uses a one-shot daemon worker",
)
if re.search(r'if \(openEdition\) OpenNativeCompletionProbe\.inspectHost\(hostClassLoader\)', main):
    raise SystemExit("native host inventory must not execute synchronously on the startup/UI caller")

inspect = probe.split("static void inspectHost(ClassLoader loader)", 1)[1].split(
    "/** Legacy object-capture entry point", 1
)[0]
if re.search(r"method\.invoke\(|constructor\.newInstance\(|field\.get\(", inspect):
    raise SystemExit("Probe must not invoke candidate host methods or read live field values")
require(probe, r"MAX_ATTEMPTS\s*=\s*3", "probe retries are bounded to three attempts")
require(probe, r"ATTEMPTS\.get\(\)\s*>=\s*MAX_ATTEMPTS", "probe scheduler enforces retry cap")
require(probe, r"runBoundedInspection\(loader\)", "worker owns bounded retries without another startup callback")
require(probe, r"while\s*\(!REPORTED\.get\(\)\s*&&\s*ATTEMPTS\.get\(\)\s*<\s*MAX_ATTEMPTS\)", "automatic retry loop respects the attempt cap")
require(probe, r"Thread\.sleep\(completedAttempts\s*==\s*1\s*\?\s*1000L\s*:\s*3000L\)", "automatic retries use bounded backoff")
require(probe, r"ATTEMPTS\.incrementAndGet\(\);\s*inspectHost\(loader\)", "each automatic attempt is counted before scanning")
require(probe, r"finally\s*\{\s*SCHEDULED\.set\(false\);", "probe releases scheduling guard after every worker run")
require(probe, r"completed\s*=\s*!names\.isEmpty\(\)\s*&&\s*classLoadFailures\s*==\s*0", "empty and partial DEX inventories remain retryable")
require(probe, r"scannedTypes\.add\(owner\).*?for \(Class<\?> owner : scannedTypes\)", "manager inventory reuses successfully loaded classes without a second class-loader pass")
require(probe, r"owner\.getDeclaredMethods\(\).*?catch \(LinkageError \| SecurityException error\).*?classLoadFailures\+\+", "a single class method-metadata failure does not abort the DEX scan")
require(probe, r"method\.getParameterTypes\(\).*?catch \(LinkageError \| RuntimeException error\).*?dex\.methodMetadataError", "a single method signature failure is counted and skipped")
require(probe, r"method\.getGenericReturnType\(\).*?catch \(LinkageError \| RuntimeException error\).*?dex\.genericMetadataError", "generic return metadata failures are reported without aborting candidate inventory")
require(probe, r"field\.getModifiers\(\).*?field\.getType\(\).*?catch \(LinkageError \| RuntimeException error\).*?manager\.fieldMetadataError", "a single manager field metadata failure is counted and skipped")
if "int memberIndex = fieldIndex++;" not in probe or 'prefix + ".fieldMetadataError." + memberIndex' not in probe:
    raise SystemExit("diagnostic field metadata errors must use unique per-member indexes")
require(probe, r"owner\.getDeclaredFields\(\).*?catch \(LinkageError \| SecurityException error\).*?classLoadFailures\+\+", "a single manager field-metadata failure is counted and skipped")
require(probe, r"getDeclaredConstructors\(\).*?catch \(LinkageError \| SecurityException error\).*?constructorError", "constructor diagnostics tolerate unavailable metadata")
require(probe, r"constructor\.getParameterTypes\(\).*?catch \(LinkageError \| RuntimeException error\).*?constructorMetadataError", "a single constructor signature failure does not abort constructor inventory")
require(probe, r"getDeclaredFields\(\).*?catch \(LinkageError \| SecurityException error\).*?fieldInventoryError", "field diagnostics tolerate unavailable metadata in superclasses")
require(probe, r"fieldType\.isAssignableFrom\(transportOwner\)", "manager field matching only accepts declared types that can hold the transport owner")
if "transportOwner.isAssignableFrom(field.getType())" in probe:
    raise SystemExit("Manager matching must reject reverse assignability, which cannot hold the transport instance")
require(
    probe,
    r"if \(fieldType == Object.class\)\s*\{\s*weakObjectFields\+\+;.*?continue;",
    "Object-typed fields are reported separately and excluded from manager candidates",
)
require(
    probe,
    r'manager\.exactFieldCount.*?manager\.interfaceFieldCount.*?manager\.supertypeFieldCount.*?manager\.objectFieldCount',
    "manager candidates expose exact, interface, supertype, and weak Object counts",
)
require(
    probe,
    r'manager\.candidateUnit.*?typed_non_object_fields.*?manager\.typedFieldCount',
    "manager candidate counts are explicitly typed as non-Object declared fields, not instances",
)
require(
    probe,
    r'candidateRank = fieldType\.isInterface\(\) \? 2 : 1.*?candidateRank > matchRank',
    "manager field match strength is independent of HashSet iteration order",
)
require(
    probe,
    r'matchKind = "exact".*?matchRank = 3.*?candidateRank = fieldType\.isInterface\(\) \? 2 : 1.*?matchKind = fieldType\.isInterface\(\)\s*\? "interface" : "supertype"',
    "typed manager candidates select the strongest exact, interface, or supertype match",
)
require(probe, r'appendConstructors\(out, "request", requestType\).*?appendConstructors\(out, "manager" \+ managerCandidates, owner, 8\).*?appendConstructors\(StringBuilder out, String prefix, Class<\?> type\).*?appendConstructors\(out, prefix, type, 24\).*?int maxDetails.*?prefix \+ "\.constructorCount".*?prefix \+ "\.ctor\."', "request and manager constructor diagnostics use independent namespaces and explicit bounds")
require(
    probe,
    r"appendTransportMethods.*?current\.getDeclaredMethods\(\).*?catch \(LinkageError \| SecurityException error\).*?transport\.inventoryError",
    "legacy transport diagnostics isolate class method inventory failures",
)
require(
    probe,
    r"method\.getParameterTypes\(\).*?method\.getReturnType\(\).*?method\.getGenericReturnType\(\).*?catch \(LinkageError \| RuntimeException error\).*?transport\.methodMetadataError",
    "legacy transport diagnostics isolate individual method metadata failures",
)
require(
    probe,
    r"current\.getSuperclass\(\).*?catch \(LinkageError \| RuntimeException error\).*?transport\.superclassError",
    "legacy transport diagnostics tolerate superclass metadata failures",
)
require(
    probe,
    r"appendPowMethods.*?type\.getDeclaredMethods\(\).*?catch \(LinkageError \| SecurityException error\).*?pow\.inventoryError",
    "legacy PoW diagnostics isolate method inventory failures",
)
require(
    probe,
    r"method\.getParameterTypes\(\).*?method\.getReturnType\(\).*?catch \(LinkageError \| RuntimeException error\).*?pow\.methodMetadataError",
    "legacy PoW diagnostics isolate individual method metadata failures",
)
require(probe, r"if \(completed\) REPORTED\.set\(true\)", "only a complete inventory becomes terminal")
require(
    probe,
    r"static void inspect\(ClassLoader loader, Object powManager, Object transport\).*?REPORTED\.get\(\).*?INSPECTING\.compareAndSet\(false, true\)",
    "legacy object probe serializes inspection without prematurely claiming terminal completion",
)
require(
    probe,
    r"boolean completed = false;.*?appendPowMethods\(out, powManager\.getClass\(\)\);\s*completed = true;.*?if \(completed\) REPORTED\.set\(true\);\s*INSPECTING\.set\(false\)",
    "legacy object probe marks terminal only after a completed inventory and always releases inspection lock",
)
require(
    probe,
    r"appendFields.*?current\.getSuperclass\(\).*?catch \(LinkageError \| RuntimeException superclassError\).*?superclassError",
    "field inventory isolates superclass metadata failures",
)
require(report, r"MAX_REPORT_CHARS\s*=\s*12_000", "probe report has an explicit size bound")
require(report, r"REPORT_TERMINAL_RESERVE\s*=\s*64", "probe report reserves space for terminal diagnostics")
require(report, r"contentLimit\s*=\s*MAX_REPORT_CHARS\s*-\s*REPORT_TERMINAL_RESERVE\s*-\s*marker\.length\(\)", "probe report reserves room for both truncation marker and terminal timing")
require(report, r'"probe\.elapsedMs"\.equals\(key\).*?MAX_REPORT_CHARS.*?return;.*?probe\.truncated=true', "truncated reports retain terminal elapsed time and expose truncation")

require(
    probe,
    r'hostCode271\(\) != 289.*?installObservedMethod\(loader, "jw3", "g".*?installObservedMethod\(loader, "zn3", "o"',
    "runtime observation remains limited to the two reviewed 2.7.2/code289 candidate methods",
)
require(
    probe,
    r'final Deque<String> callStack.*?Object rootRequest.*?boolean hasRootRequest',
    "runtime observer keeps only a thread-local call stack and opaque root request reference",
)
require(
    probe,
    r'parentMethod = frame\.callStack\.isEmpty\(\).*?frame\.callStack\.addLast\(currentMethod\)',
    "runtime observer records the direct synchronous parent method",
)
require(
    probe,
    r'sameRequestObjectAsRoot = frame\.hasRootRequest\s*&&\s*requestArg == frame\.rootRequest',
    "runtime observer correlates argument object identity without reading its fields",
)
require(
    probe,
    r'frame\.callStack\.removeLast\(\).*?if \(root\) OBSERVATION_FRAME\.remove\(\)',
    "runtime call-stack state is removed on normal and observer-error exits",
)
require(
    probe,
    r'observedMethodDescriptor\(Method method\).*?method\.getParameterTypes\(\).*?method\.getReturnType\(\)',
    "runtime trace records a structural method descriptor only",
)
require(
    probe,
    r'observedMethodDescriptor\(Method method\).*?catch \(Throwable ignored\).*?method-descriptor-unavailable',
    "method metadata failures degrade to a marker instead of blocking the host call",
)
if "requestArg.toString()" in probe or "String.valueOf(requestArg)" in probe:
    raise SystemExit("runtime observer must not serialize request objects")


require(
    probe,
    r'observedResultType\\(Object value\\).*?value\\.getClass\\(\\).*?type\\.getInterfaces\\(\\).*?Math\\.min\\(interfaces\\.length, 8\\)',
    "runtime observer records bounded result type/interface metadata without inspecting values",
)
require(
    probe,
    r'" depth=" \\+ depth \\+ " result="\\s*\\+ observedResultType\\(result\\)',
    "transport exit trace includes the actual returned object's runtime type metadata",
)

print("Native probe bounded-retry regression checks passed")
print("Native adapter fail-closed and read-only probe source checks passed")
PY

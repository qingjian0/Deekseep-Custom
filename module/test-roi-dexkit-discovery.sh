#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DISCOVERY="$ROOT/module/src/ds/dsh/DeepSeek/RoiDexKitDiscovery.java"
HOST_DISCOVERY="$ROOT/module/src/ds/dsh/DeepSeek/RoiHostDiscovery.java"
MAIN="$ROOT/module/src/ds/dsh/DeepSeek/Main.java"
GRAPH="$ROOT/module/src/ds/dsh/DeepSeek/RoiCandidateGraph.java"
ROLE_HINTS="$ROOT/module/src/ds/dsh/DeepSeek/RoiSemanticRoleHints.java"
LOADER_PROFILE="$ROOT/module/src/ds/dsh/DeepSeek/RoiClassLoaderProfile.java"

python3 - "$DISCOVERY" "$HOST_DISCOVERY" "$MAIN" "$GRAPH" "$ROLE_HINTS" <<'PY'
import pathlib
import re
import sys

discovery, host_discovery, main, graph, role_hints, loader_profile = [pathlib.Path(p).read_text(encoding="utf-8") for p in sys.argv[1:]]

def require(source, pattern, label):
    if not re.search(pattern, source, re.S):
        raise SystemExit("Missing read-only DexKit discovery contract: " + label)

require(discovery, r'DexKitBridge\.create\(loaders\.get\(i\), true\)', "ART memory DEX is preferred")
require(discovery, r'DexKitBridge\.create\(apkPaths\.get\(i\)\)', "APK and split APK fallback exists")
require(discovery, r'collectApkPaths\(apkPath, splitApkPaths\)', "base and split APK paths are collected")
require(discovery, r'for \(int i = 0; i < apkPaths\.size\(\); i\+\+\)', "APK paths are scanned independently")
require(discovery, r'SEMANTIC_ANCHORS', "semantic query anchors are defined")
require(discovery, r'RoiCandidateIdentity[.]forClassLoader[(]loaders[.]get[(]i[)][)]', "loader identity uses the actual object")
require(discovery, r'RoiCandidateIdentity[.]forApkPath[(]apkPaths[.]get[(]i[)][)]', "APK identity uses its path")
require(loader_profile, r'InMemoryDexClassLoader', "in-memory DEX loader is classified")
require(loader_profile, r'DexClassLoader', "file-backed DEX loader is classified")
require(loader_profile, r'encryptedDexAssessment', "encryption assessment is explicit")
require(loader_profile, r'unconfirmed_custom_loader_requires_runtime_dex_evidence', "custom loader is not treated as encryption proof")
require(discovery, r'RoiClassLoaderProfile[.]inspect[(]loaders[.]get[(]i[)][)]', "live loader profile is reported")
require(discovery, r'profile[.]encryptedDexAssessment=', "encryption assessment is emitted as diagnostic only")
require(discovery, r'RoiCandidateIdentity[.]candidateId[(]', "candidate IDs include source scope")
require(discovery, r'RoiHookResolver[.]Candidate[(][^;]*sourceScope, candidateId', "resolver candidate retains source identity")
require(discovery, r'appendCallRelations[(][^;]*sourceScope', "call relation observations retain source scope")
require(discovery, r'new RoiCandidateGraph[.]Observation[(]sourceScope, descriptor, anchor', "graph observations are scoped to their source")
require(graph, r'qualify[(]node[.]sourceScope, target[)]', "outgoing references cannot cross source scopes")
require(graph, r'qualify[(]node[.]sourceScope, caller[)]', "incoming references cannot cross source scopes")


require(discovery, r'MethodMatcher\.create\(\)\.usingStrings', "DexKit semantic method matching is used")
require(discovery, r'MAX_CANDIDATES_PER_ANCHOR\s*=\s*8', "candidate output is bounded")
require(discovery, r'MAX_TOTAL_QUERIES_PER_SCAN\s*=\s*32', "per-scan native query count is bounded")
require(discovery, r'MAX_TOTAL_CANDIDATES_PER_SCAN\s*=\s*96', "per-scan candidate processing is bounded")
require(discovery, r'SCAN_BUDGET_NANOS\s*=\s*45_000_000_000L', "per-scan soft deadline is fixed")
require(discovery, r'budget[.]tryStartQuery[(]System[.]nanoTime[(][)][)]', "budget checked before native query start")
require(discovery, r'budget[.]tryAcceptCandidate[(]System[.]nanoTime[(][)][)]', "budget checked during candidate iteration")
require(discovery, r'scan[.]budget[.]enforcement=query_start_and_candidate_iteration_only', "native query non-interruptibility is explicit")
require(discovery, r'MAX_DISCOVERY_PASSES[ ]*=[ ]*3', "runtime rescans are bounded")
require(discovery, r'Thread[.]sleep[(]pass == 1 [?] 5000L : 10000L[)]', "late DEX rescan cadence is bounded")
require(discovery, r'inspect[(]root, apkPath, splitApkPaths, pass == 0[)]', "APK scans run only on the initial pass")
require(discovery, r'apk[.]scanSkipped=runtime_rescan', "runtime rescans avoid redundant APK scans")
require(discovery, r'loader[.]rescan=bounded_thread_context_snapshot', "runtime loader rescan scope is explicit")
require(discovery, r'bridge\.close\(\)', "native bridge resources are closed")
require(discovery, r'System\.loadLibrary\("dexkit"\)', "JNI loading is explicit")
require(discovery, r'REGISTERED_LOADERS', "dynamic loader registration API exists")
require(discovery, r'activation=forbidden_without_external_validation', "candidate activation remains gated")
require(discovery, r'RoiHookResolver\.resolve\(', "DexKit candidates are passed to the evidence resolver")
require(discovery, r'RoiHookResolver\.Policy\.discoveryOnly\(', "DexKit evidence remains discovery-only")
require(discovery, r'RoiMethodShape[.]parse[(]descriptor[)]', "DexKit descriptors are structurally parsed")
require(discovery, r'shape[.]parameterCount=', "validated method arity is reported")
require(discovery, r'shape[.]return=', "validated method return descriptor is reported")
require(discovery, r'getAccessFlags', "raw DEX access flags are observed")
require(discovery, r'getParamTypeNames', "parameter type names are observed")
require(discovery, r'getReturnTypeName', "return type name is observed")
require(discovery, r'getOpNames', "bounded opcode names are observed")
require(discovery, r'MAX_RELATIONS_PER_CANDIDATE\s*=\s*12', "call neighborhood is bounded per candidate")
require(discovery, r'getInvokes', "DexKit outgoing invoke relations are read")
require(discovery, r'getCallers', "DexKit incoming caller relations are read")
require(discovery, r'import org[.]luckypray[.]dexkit[.]query[.]matchers[.]MethodsMatcher', "call relation matcher API is imported")
require(discovery, r'[.]invokeMethods[(]MethodsMatcher[.]create[(][)][.]countMin[(]0[)][)]', "invoke index is initialized before MethodData access")
require(discovery, r'[.]callerMethods[(]MethodsMatcher[.]create[(][)][.]countMin[(]0[)][)]', "caller index is initialized before MethodData access")
require(discovery, r'callGraph[.]edge[.]invoke[.]', "outgoing call edges are reported")
require(discovery, r'callGraph[.]edge[.]caller[.]', "incoming call edges are reported")
require(discovery, r'EvidenceKind[.]CALL_GRAPH', "static call relations are typed as call-graph evidence")
require(discovery, r'dexkit_method_data_invokes_and_callers', "call graph evidence provenance is explicit")
require(discovery, r'appendCandidateGraph[(]', "per-anchor observations are merged into a graph")
require(discovery, r'candidateGraph[.]duplicates=', "duplicate method observations are reported")
require(discovery, r'candidateGraph[.]components=', "candidate graph connectivity is reported")
require(discovery, r'candidateGraph[.]edgeSemantics=static_dex_reference_only', "graph edges are not misrepresented as runtime paths")
require(graph, r'DEFAULT_MAX_NODES = 128', "candidate graph node cap is fixed")
require(graph, r'DEFAULT_MAX_EDGES = 512', "candidate graph edge cap is fixed")
require(graph, r'countComponents[(]', "graph connected components are computed")
require(graph, r'unresolvedReferences', "unresolved external references are counted")
require(discovery, r'appendSemanticRoleHints[(]', "candidate invokes are checked for conservative role hints")
require(discovery, r'semanticHints[.]provenance=direct_static_invoke_reference', "role hint provenance is explicit")
require(role_hints, r'REQUEST_BUILDER', "request builder hint family exists")
require(role_hints, r'TRANSPORT', "transport hint family exists")
require(role_hints, r'RESPONSE_PARSER', "response parser hint family exists")
require(discovery, r'resolver[.]activationAuthorized=', "resolver authorization is reported explicitly")
require(discovery, r'RoiHookResolver[.]EvidenceKind[.]SEMANTIC_MARKER', "semantic anchor evidence is typed")
require(discovery, r'0[.]25, false, "host_diagnostic_only"', "host identity is not falsely confirmed")
require(discovery, r'shape == null [?] 0[.]0 : 0[.]55, shape != null', "descriptor parser confidence reflects structural validation only")
require(discovery, r'hooksInstalled=false', "discovery does not install hooks")
require(main, r'RoiDexKitDiscovery\.schedule\(cl,.*?splitSourceDirs\)', "unknown-host path schedules DexKit discovery")
require(host_discovery, r"static void schedule", "DEX inventory remains a separate first layer")

for forbidden in ("getMethodInstance(", "getConstructorInstance(", "Main.MODULE.hook(", "XposedBridge.hookMethod("):
    if forbidden in discovery:
        raise SystemExit("DexKit discovery must not resolve live hook targets: " + forbidden)

print("ROI DexKit read-only discovery regression checks passed")
PY

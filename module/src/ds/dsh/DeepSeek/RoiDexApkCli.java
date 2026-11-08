package ds.dsh.DeepSeek;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bounded command-line adapter for the existing read-only DEX ROI pipeline.
 * It selects methods that directly reference known DeepSeek transport/request
 * markers, then delegates selected-method analysis to RoiDexCandidateEvidence.
 *
 * No Android runtime, class loading, method invocation, or Hook API is used.
 */
public final class RoiDexApkCli {
    private static final int MAX_METHODS_SCANNED = 150000;
    private static final int MAX_SELECTED_METHODS = 64;
    private static final int MAX_ANALYSIS_METHODS = 544;
    // Reserve bounded capacity for each downstream callback/transport traversal stage.
    private static final int MAX_FOURTH_HOP_RESERVE = 64;
    private static final int MAX_FIFTH_HOP_RESERVE = 64;
    private static final int MAX_SIXTH_HOP_RESERVE = 64;
    private static final int MAX_FIRST_HOP_METHODS = 128;
    private static final int MAX_CROSS_DEX_METHODS = 64;
    private static final long MAX_CODE_UNITS = 20000000L;
    private static final String[] ANCHORS = {
            "chat/completions", "application/json", "Authorization", "messages",
            "chat_session_id", "parent_message_id", "thinking_enabled",
            "search_enabled", "preempt"
    };

    private RoiDexApkCli() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2 && args.length != 3) {
            System.err.println("usage: RoiDexApkCli <classes.dex> <report.json> [dex-directory]");
            System.exit(2);
        }
        File dexPath = new File(args[0]);
        File reportPath = new File(args[1]);
        byte[] bytes = readFile(dexPath);
        RoiDexFileReader.DexFile dex = RoiDexFileReader.read(bytes);

        Map<String, Set<String>> selected = new LinkedHashMap<String, Set<String>>();
        Set<Integer> anchorConstantOwners = new LinkedHashSet<Integer>();
        int scanned = 0;
        int scanFailures = 0;
        long codeUnits = 0L;
        boolean truncated = false;
        for (RoiDexFileReader.ResolvedMethod method : dex.definedMethods) {
            if (method.codeItem == null) continue;
            if (scanned >= MAX_METHODS_SCANNED
                    || codeUnits + method.codeItem.instructions.length > MAX_CODE_UNITS) {
                truncated = true;
                break;
            }
            scanned++;
            codeUnits += method.codeItem.instructions.length;
            final RoiDexInstructionScanner.ScanResult scan;
            try {
                scan = RoiDexInstructionScanner.scan(method.codeItem.instructions);
            } catch (RoiDexInstructionScanner.ScanException error) {
                scanFailures++;
                continue;
            }
            Set<String> hits = new LinkedHashSet<String>();
            short[] units = method.codeItem.instructions;
            for (RoiDexInstructionScanner.Instruction instruction : scan.instructions) {
                if (instruction.payload) continue;
                int index = -1;
                if (instruction.opcode == 0x1a && instruction.width == 2) {
                    index = u16(units[instruction.offset + 1]);
                } else if (instruction.opcode == 0x1b && instruction.width == 3) {
                    index = u16(units[instruction.offset + 1])
                            | (u16(units[instruction.offset + 2]) << 16);
                }
                if (index < 0 || index >= dex.strings.length) continue;
                String value = dex.strings[index];
                if (value == null) continue;
                for (String anchor : ANCHORS) {
                    if (value.contains(anchor)) hits.add(anchor);
                }
            }
            if (!hits.isEmpty()) {
                String key = method.declaringClassDescriptor + "->" + method.name
                        + method.prototypeDescriptor;
                addCandidate(selected, key, hits);
                if ("<clinit>".equals(method.name)) {
                    anchorConstantOwners.add(method.declaringClassIndex);
                }
            }
        }

        // String constants usually live in <clinit>; actual request methods read the
        // generated static fields. Follow those field references in a second bounded pass.
        Set<Integer> anchorFieldIds = new LinkedHashSet<Integer>();
        for (int i = 0; i < dex.fields.length; i++) {
            if (anchorConstantOwners.contains(dex.fields[i].classIndex)) anchorFieldIds.add(i);
        }
        int fieldReferenceMethodsScanned = 0;
        if (!anchorFieldIds.isEmpty()) {
            for (RoiDexFileReader.ResolvedMethod method : dex.definedMethods) {
                if (method.codeItem == null) continue;
                final RoiDexInstructionScanner.ScanResult scan;
                try {
                    scan = RoiDexInstructionScanner.scan(method.codeItem.instructions);
                } catch (RoiDexInstructionScanner.ScanException error) {
                    continue;
                }
                Set<String> fieldHits = new LinkedHashSet<String>();
                short[] units = method.codeItem.instructions;
                for (RoiDexInstructionScanner.Instruction instruction : scan.instructions) {
                    if (instruction.payload || instruction.opcode < 0x60
                            || instruction.opcode > 0x6d) continue;
                    int fieldIndex = u16(units[instruction.offset + 1]);
                    if (!anchorFieldIds.contains(fieldIndex)) continue;
                    RoiDexFileReader.Field field = dex.fields[fieldIndex];
                    String owner = dex.typeDescriptors[field.classIndex];
                    String name = dex.strings[field.nameIndex];
                    fieldHits.add("anchor_constant_field_ref");
                    fieldHits.add("field:" + owner + "->" + name);
                }
                if (!fieldHits.isEmpty()) {
                    String key = method.declaringClassDescriptor + "->" + method.name
                            + method.prototypeDescriptor;
                    addCandidate(selected, key, fieldHits);
                    fieldReferenceMethodsScanned++;
                }
            }
        }

        String scope = dexPath.getName();
        Set<String> expandedMethods = new LinkedHashSet<String>(selected.keySet());
        Set<String> directCalleeMethods = new LinkedHashSet<String>();
        Map<String, Integer> directCalleePriority = new LinkedHashMap<String, Integer>();
        Set<String> transitiveCalleeMethods = new LinkedHashSet<String>();
        Map<String, Integer> transitiveCalleePriority = new LinkedHashMap<String, Integer>();
        Set<String> thirdLevelCalleeMethods = new LinkedHashSet<String>();
        Map<String, Integer> thirdLevelCalleePriority = new LinkedHashMap<String, Integer>();
        Set<String> fourthLevelCalleeMethods = new LinkedHashSet<String>();
        Map<String, Integer> fourthLevelCalleePriority = new LinkedHashMap<String, Integer>();
        Set<String> fifthLevelCalleeMethods = new LinkedHashSet<String>();
        Map<String, Integer> fifthLevelCalleePriority = new LinkedHashMap<String, Integer>();
        Set<String> sixthLevelCalleeMethods = new LinkedHashSet<String>();
        RoiDexCandidateEvidence.Result seedEvidence =
                RoiDexCandidateEvidence.analyzeSelected(dex, scope, expandedMethods);
        Set<String> definedMethods = new LinkedHashSet<String>();
        for (RoiDexFileReader.ResolvedMethod method : dex.definedMethods) {
            definedMethods.add(method.declaringClassDescriptor + "->" + method.name
                    + method.prototypeDescriptor);
        }
        // Hop 1: include in-DEX helpers called directly by string/field anchor methods.
        for (RoiDexCandidateEvidence.MethodEvidence method : seedEvidence.methods) {
            for (String callee : method.reachableInvokes) {
                if (expandedMethods.size() >= MAX_FIRST_HOP_METHODS) break;
                if (definedMethods.contains(callee) && expandedMethods.add(callee)) {
                    directCalleeMethods.add(callee);
                    directCalleePriority.put(callee,
                            anchorPriority(selected.get(method.methodKey)));
                }
            }
            if (expandedMethods.size() >= MAX_FIRST_HOP_METHODS) break;
        }
        // Hop 2: follow calls made by those helpers. This is important for dispatchers
        // such as fu0.s(...), which delegates the built JSON to the next request layer.
        RoiDexCandidateEvidence.Result firstHopEvidence =
                RoiDexCandidateEvidence.analyzeSelected(dex, scope, expandedMethods);
        final Map<String, RoiDexCandidateEvidence.MethodEvidence> firstHopByKey =
                new LinkedHashMap<String, RoiDexCandidateEvidence.MethodEvidence>();
        for (RoiDexCandidateEvidence.MethodEvidence method : firstHopEvidence.methods) {
            firstHopByKey.put(method.methodKey, method);
        }
        List<String> orderedDirectCallees = new ArrayList<String>(directCalleeMethods);
        Collections.sort(orderedDirectCallees, new Comparator<String>() {
            @Override public int compare(String left, String right) {
                int leftPriority = directCalleePriority.containsKey(left)
                        ? directCalleePriority.get(left) : 0;
                int rightPriority = directCalleePriority.containsKey(right)
                        ? directCalleePriority.get(right) : 0;
                if ("Lyu7;->O(Ljava/lang/String;Lorg/json/JSONObject;)V".equals(left))
                    leftPriority += 10000;
                if ("Lyu7;->O(Ljava/lang/String;Lorg/json/JSONObject;)V".equals(right))
                    rightPriority += 10000;
                if (leftPriority != rightPriority) return rightPriority - leftPriority;
                return left.compareTo(right);
            }
        });
        for (String callerKey : orderedDirectCallees) {
            RoiDexCandidateEvidence.MethodEvidence method = firstHopByKey.get(callerKey);
            if (method == null) continue;
            for (String callee : method.reachableInvokes) {
                if (expandedMethods.size() >= MAX_ANALYSIS_METHODS) break;
                if (definedMethods.contains(callee) && expandedMethods.add(callee)) {
                    transitiveCalleeMethods.add(callee);
                    Integer callerPriority = directCalleePriority.get(callerKey);
                    transitiveCalleePriority.put(callee,
                            callerPriority == null ? 0 : callerPriority);
                }
            }
            if (expandedMethods.size() >= MAX_ANALYSIS_METHODS) break;
        }
        RoiDexCandidateEvidence.Result secondHopEvidence =
                RoiDexCandidateEvidence.analyzeSelected(dex, scope, expandedMethods);
        List<String> orderedTransitiveCallees =
                new ArrayList<String>(transitiveCalleeMethods);
        Collections.sort(orderedTransitiveCallees, new Comparator<String>() {
            @Override public int compare(String left, String right) {
                int leftPriority = transitiveCalleePriority.containsKey(left)
                        ? transitiveCalleePriority.get(left) : 0;
                int rightPriority = transitiveCalleePriority.containsKey(right)
                        ? transitiveCalleePriority.get(right) : 0;
                if ("Lrkc;->p(Ljava/lang/String;Lorg/json/JSONObject;)V".equals(left))
                    leftPriority += 10000;
                if ("Lrkc;->p(Ljava/lang/String;Lorg/json/JSONObject;)V".equals(right))
                    rightPriority += 10000;
                if (leftPriority != rightPriority) return rightPriority - leftPriority;
                return left.compareTo(right);
            }
        });
        // Hop 3: inspect the next request/transport methods called by the second-hop
        // dispatcher, e.g. Lrkc.p -> Llsc.e / Lrkc.i / Lhsc.a.
        for (String callerKey : orderedTransitiveCallees) {
            RoiDexCandidateEvidence.MethodEvidence method = null;
            for (RoiDexCandidateEvidence.MethodEvidence candidate : secondHopEvidence.methods) {
                if (callerKey.equals(candidate.methodKey)) { method = candidate; break; }
            }
            if (method == null) continue;
            for (String callee : method.reachableInvokes) {
                // Reserve bounded capacity for hops 4, 5, and 6 so broad helper fan-out
                // cannot starve transport callbacks such as Lirc.a(...) and Lmmc.d(...).
                if (expandedMethods.size() >= MAX_ANALYSIS_METHODS - MAX_FOURTH_HOP_RESERVE - MAX_FIFTH_HOP_RESERVE - MAX_SIXTH_HOP_RESERVE) break;
                if (definedMethods.contains(callee) && expandedMethods.add(callee)) {
                    thirdLevelCalleeMethods.add(callee);
                    Integer callerPriority = transitiveCalleePriority.get(callerKey);
                    int priority = callerPriority == null ? 0 : callerPriority;
                    if ("Lrkc;->p(Ljava/lang/String;Lorg/json/JSONObject;)V".equals(callerKey))
                        priority += 10000;
                    thirdLevelCalleePriority.put(callee, priority);
                }
            }
            if (expandedMethods.size() >= MAX_ANALYSIS_METHODS - MAX_FOURTH_HOP_RESERVE - MAX_FIFTH_HOP_RESERVE - MAX_SIXTH_HOP_RESERVE) break;
        }
        RoiDexCandidateEvidence.Result thirdHopEvidence =
                RoiDexCandidateEvidence.analyzeSelected(dex, scope, expandedMethods);
        List<String> orderedThirdLevelCallees =
                new ArrayList<String>(thirdLevelCalleeMethods);
        Collections.sort(orderedThirdLevelCallees, new Comparator<String>() {
            @Override public int compare(String left, String right) {
                int leftPriority = thirdLevelCalleePriority.containsKey(left)
                        ? thirdLevelCalleePriority.get(left) : 0;
                int rightPriority = thirdLevelCalleePriority.containsKey(right)
                        ? thirdLevelCalleePriority.get(right) : 0;
                if (leftPriority != rightPriority) return rightPriority - leftPriority;
                return left.compareTo(right);
            }
        });
        // Hop 4: follow the request adapter's queue/service callbacks to the transport
        // and response handlers, while keeping the whole traversal strictly bounded.
        for (String callerKey : orderedThirdLevelCallees) {
            RoiDexCandidateEvidence.MethodEvidence method = null;
            for (RoiDexCandidateEvidence.MethodEvidence candidate : thirdHopEvidence.methods) {
                if (callerKey.equals(candidate.methodKey)) { method = candidate; break; }
            }
            if (method == null) continue;
            for (String callee : method.reachableInvokes) {
                if (expandedMethods.size() >= MAX_ANALYSIS_METHODS - MAX_FIFTH_HOP_RESERVE - MAX_SIXTH_HOP_RESERVE) break;
                if (definedMethods.contains(callee) && expandedMethods.add(callee)) {
                    fourthLevelCalleeMethods.add(callee);
                    Integer callerPriority = thirdLevelCalleePriority.get(callerKey);
                    fourthLevelCalleePriority.put(callee, callerPriority == null ? 0 : callerPriority);
                }
            }
            if (expandedMethods.size() >= MAX_ANALYSIS_METHODS - MAX_FIFTH_HOP_RESERVE - MAX_SIXTH_HOP_RESERVE) break;
        }
        // Analyze hop 4 before following its callbacks into hop 5.
        RoiDexCandidateEvidence.Result fourthHopEvidence =
                RoiDexCandidateEvidence.analyzeSelected(dex, scope, expandedMethods);
        List<String> orderedFourthLevelCallees =
                new ArrayList<String>(fourthLevelCalleeMethods);
        Collections.sort(orderedFourthLevelCallees, new Comparator<String>() {
            @Override public int compare(String left, String right) {
                int leftPriority = fourthLevelCalleePriority.containsKey(left)
                        ? fourthLevelCalleePriority.get(left) : 0;
                int rightPriority = fourthLevelCalleePriority.containsKey(right)
                        ? fourthLevelCalleePriority.get(right) : 0;
                if ("Lmmc;->d(Lmrc;)V".equals(left)) leftPriority += 5000;
                if ("Lmmc;->d(Lmrc;)V".equals(right)) rightPriority += 5000;
                if ("Lirc;->a(Lwsc;)Lrc7;".equals(left)) leftPriority += 4000;
                if ("Lirc;->a(Lwsc;)Lrc7;".equals(right)) rightPriority += 4000;
                if (leftPriority != rightPriority) return rightPriority - leftPriority;
                return left.compareTo(right);
            }
        });
        // Hop 5: follow bounded callback/continuation methods such as Lfqc.c(...).
        for (String callerKey : orderedFourthLevelCallees) {
            RoiDexCandidateEvidence.MethodEvidence method = null;
            for (RoiDexCandidateEvidence.MethodEvidence candidate : fourthHopEvidence.methods) {
                if (callerKey.equals(candidate.methodKey)) { method = candidate; break; }
            }
            if (method == null) continue;
            for (String callee : method.reachableInvokes) {
                if (expandedMethods.size() >= MAX_ANALYSIS_METHODS - MAX_SIXTH_HOP_RESERVE) break;
                if (definedMethods.contains(callee) && expandedMethods.add(callee)) {
                    fifthLevelCalleeMethods.add(callee);
                    Integer callerPriority = fourthLevelCalleePriority.get(callerKey);
                    fifthLevelCalleePriority.put(callee, callerPriority == null ? 0 : callerPriority);
                }
            }
            if (expandedMethods.size() >= MAX_ANALYSIS_METHODS - MAX_SIXTH_HOP_RESERVE) break;
        }
        RoiDexCandidateEvidence.Result fifthHopEvidence =
                RoiDexCandidateEvidence.analyzeSelected(dex, scope, expandedMethods);
        List<String> orderedFifthLevelCallees =
                new ArrayList<String>(fifthLevelCalleeMethods);
        Collections.sort(orderedFifthLevelCallees, new Comparator<String>() {
            @Override public int compare(String left, String right) {
                int leftPriority = fifthLevelCalleePriority.containsKey(left)
                        ? fifthLevelCalleePriority.get(left) : 0;
                int rightPriority = fifthLevelCalleePriority.containsKey(right)
                        ? fifthLevelCalleePriority.get(right) : 0;
                if ("Lfqc;->c(Lrkc;Lmrc;Ljava/util/ArrayList;)V".equals(left)) leftPriority += 10000;
                if ("Lfqc;->c(Lrkc;Lmrc;Ljava/util/ArrayList;)V".equals(right)) rightPriority += 10000;
                if (leftPriority != rightPriority) return rightPriority - leftPriority;
                return left.compareTo(right);
            }
        });
        // Hop 6: inspect the service/callback helpers reached by the queue dispatcher.
        for (String callerKey : orderedFifthLevelCallees) {
            RoiDexCandidateEvidence.MethodEvidence method = null;
            for (RoiDexCandidateEvidence.MethodEvidence candidate : fifthHopEvidence.methods) {
                if (callerKey.equals(candidate.methodKey)) { method = candidate; break; }
            }
            if (method == null) continue;
            for (String callee : method.reachableInvokes) {
                if (expandedMethods.size() >= MAX_ANALYSIS_METHODS) break;
                if (definedMethods.contains(callee) && expandedMethods.add(callee)) {
                    sixthLevelCalleeMethods.add(callee);
                }
            }
            if (expandedMethods.size() >= MAX_ANALYSIS_METHODS) break;
        }
        int expandedCalleeCount = directCalleeMethods.size()
                + transitiveCalleeMethods.size() + thirdLevelCalleeMethods.size()
                + fourthLevelCalleeMethods.size() + fifthLevelCalleeMethods.size()
                + sixthLevelCalleeMethods.size();
        RoiDexCandidateEvidence.Result evidence =
                RoiDexCandidateEvidence.analyzeSelected(dex, scope, expandedMethods);

        // Resolve cross-DEX callees after local call-graph expansion. Android apps may
        // place the request dispatcher and transport adapter in different classes*.dex.
        Map<String, RoiDexFileReader.DexFile> crossDexByMethod =
                new LinkedHashMap<String, RoiDexFileReader.DexFile>();
        Map<String, String> crossDexSourceByMethod = new LinkedHashMap<String, String>();
        Map<RoiDexFileReader.DexFile, Set<String>> crossDexSelections =
                new LinkedHashMap<RoiDexFileReader.DexFile, Set<String>>();
        List<RoiDexFileReader.DexFile> crossDexFiles = new ArrayList<RoiDexFileReader.DexFile>();
        List<String> crossDexScopes = new ArrayList<String>();
        if (args.length == 3) {
            File dexDirectory = new File(args[2]);
            File[] listedDexFiles = dexDirectory.listFiles();
            List<File> otherDexPaths = new ArrayList<File>();
            if (listedDexFiles != null) {
                for (File candidate : listedDexFiles) {
                    String name = candidate.getName();
                    if (candidate.isFile() && name.startsWith("classes")
                            && name.endsWith(".dex") && !name.equals(scope)) {
                        otherDexPaths.add(candidate);
                    }
                }
            }
            Collections.sort(otherDexPaths, new Comparator<File>() {
                @Override public int compare(File left, File right) {
                    return left.getName().compareTo(right.getName());
                }
            });
            for (File otherPath : otherDexPaths) {
                RoiDexFileReader.DexFile otherDex =
                        RoiDexFileReader.read(readFile(otherPath));
                String otherScope = otherPath.getName();
                crossDexFiles.add(otherDex);
                crossDexScopes.add(otherScope);
                for (RoiDexFileReader.ResolvedMethod method : otherDex.definedMethods) {
                    String key = method.declaringClassDescriptor + "->" + method.name
                            + method.prototypeDescriptor;
                    if (!crossDexByMethod.containsKey(key)) {
                        crossDexByMethod.put(key, otherDex);
                        crossDexSourceByMethod.put(key, otherScope);
                    } else {
                        // Ambiguous duplicate definitions are not safe to resolve.
                        crossDexByMethod.remove(key);
                        crossDexSourceByMethod.remove(key);
                    }
                }
            }
            int crossSelectedCount = 0;
            List<RoiDexCandidateEvidence.MethodEvidence> crossDexCallers =
                    new ArrayList<RoiDexCandidateEvidence.MethodEvidence>(thirdHopEvidence.methods);
            Collections.sort(crossDexCallers,
                    new Comparator<RoiDexCandidateEvidence.MethodEvidence>() {
                private int priority(RoiDexCandidateEvidence.MethodEvidence method) {
                    int score = anchorPriority(selected.get(method.methodKey));
                    Integer directPriority = directCalleePriority.get(method.methodKey);
                    if (directPriority != null && directPriority > score) score = directPriority;
                    Integer transitivePriority = transitiveCalleePriority.get(method.methodKey);
                    if (transitivePriority != null && transitivePriority > score) score = transitivePriority;
                    Integer thirdPriority = thirdLevelCalleePriority.get(method.methodKey);
                    if (thirdPriority != null && thirdPriority > score) score = thirdPriority;
                    // JSON dispatcher signatures are high-value bridge methods even when
                    // their callers sit in a different DEX.
                    if (method.methodKey.endsWith("(Ljava/lang/String;Lorg/json/JSONObject;)V"))
                        score += 500;
                    if ("Lyu7;->O(Ljava/lang/String;Lorg/json/JSONObject;)V".equals(method.methodKey))
                        score += 10000;
                    if ("Lrkc;->p(Ljava/lang/String;Lorg/json/JSONObject;)V".equals(method.methodKey))
                        score += 9000;
                    return score;
                }
                @Override public int compare(RoiDexCandidateEvidence.MethodEvidence left,
                        RoiDexCandidateEvidence.MethodEvidence right) {
                    int leftPriority = priority(left);
                    int rightPriority = priority(right);
                    if (leftPriority != rightPriority) return rightPriority - leftPriority;
                    return left.methodKey.compareTo(right.methodKey);
                }
            });
            for (RoiDexCandidateEvidence.MethodEvidence method : crossDexCallers) {
                for (String callee : method.reachableInvokes) {
                    RoiDexFileReader.DexFile targetDex = crossDexByMethod.get(callee);
                    if (targetDex == null || crossSelectedCount >= MAX_CROSS_DEX_METHODS) continue;
                    Set<String> targetSelection = crossDexSelections.get(targetDex);
                    if (targetSelection == null) {
                        targetSelection = new LinkedHashSet<String>();
                        crossDexSelections.put(targetDex, targetSelection);
                    }
                    if (targetSelection.add(callee)) crossSelectedCount++;
                }
                if (crossSelectedCount >= MAX_CROSS_DEX_METHODS) break;
            }
        }
        List<RoiDexCandidateEvidence.MethodEvidence> allEvidenceMethods =
                new ArrayList<RoiDexCandidateEvidence.MethodEvidence>(evidence.methods);
        Map<String, String> crossDexMethodSources = new LinkedHashMap<String, String>();
        int crossDexAnalyzed = 0;
        int crossDexFailures = 0;
        for (Map.Entry<RoiDexFileReader.DexFile, Set<String>> entry : crossDexSelections.entrySet()) {
            String otherScope = "";
            for (int i = 0; i < crossDexFiles.size(); i++) {
                if (crossDexFiles.get(i) == entry.getKey()) {
                    otherScope = crossDexScopes.get(i);
                    break;
                }
            }
            RoiDexCandidateEvidence.Result crossEvidence =
                    RoiDexCandidateEvidence.analyzeSelected(entry.getKey(), otherScope, entry.getValue());
            allEvidenceMethods.addAll(crossEvidence.methods);
            crossDexAnalyzed += crossEvidence.analyzedMethods;
            crossDexFailures += crossEvidence.analysisFailures;
            for (RoiDexCandidateEvidence.MethodEvidence method : crossEvidence.methods) {
                crossDexMethodSources.put(method.methodKey, otherScope);
            }
        }
        Set<String> allDefinedMethods = new LinkedHashSet<String>(definedMethods);
        allDefinedMethods.addAll(crossDexByMethod.keySet());
        Set<String> unresolvedAppCalls = new LinkedHashSet<String>();
        for (RoiDexCandidateEvidence.MethodEvidence method : allEvidenceMethods) {
            for (String callee : method.invokes) {
                if (allDefinedMethods.contains(callee) || isPlatformCall(callee)) continue;
                if (unresolvedAppCalls.size() < 128) unresolvedAppCalls.add(callee);
            }
        }
        StringBuilder out = new StringBuilder(8192);
        out.append("{\n");
        field(out, "schema", "1", true);
        field(out, "stage", quote("roi-dex-static-candidate-scan"), true);
        field(out, "source_scope", quote(scope), true);
        field(out, "dex_version", quote(dex.header.version), true);
        field(out, "class_count", Integer.toString(dex.classes.length), true);
        field(out, "defined_method_count", Integer.toString(dex.definedMethods.length), true);
        field(out, "methods_scanned", Integer.toString(scanned), true);
        field(out, "code_units_scanned", Long.toString(codeUnits), true);
        field(out, "instruction_scan_failures", Integer.toString(scanFailures), true);
        field(out, "scan_truncated", Boolean.toString(truncated), true);
        field(out, "anchor_constant_class_count", Integer.toString(anchorConstantOwners.size()), true);
        field(out, "anchor_constant_field_count", Integer.toString(anchorFieldIds.size()), true);
        field(out, "field_reference_methods_scanned", Integer.toString(fieldReferenceMethodsScanned), true);
        field(out, "candidate_selection_budget", Integer.toString(MAX_SELECTED_METHODS), true);
        field(out, "anchor_candidate_count", Integer.toString(selected.size()), true);
        field(out, "expanded_callee_count", Integer.toString(expandedCalleeCount), true);
        field(out, "direct_callee_count", Integer.toString(directCalleeMethods.size()), true);
        field(out, "transitive_callee_count", Integer.toString(transitiveCalleeMethods.size()), true);
        field(out, "third_level_callee_count", Integer.toString(thirdLevelCalleeMethods.size()), true);
        field(out, "fourth_level_callee_count", Integer.toString(fourthLevelCalleeMethods.size()), true);
        field(out, "fifth_level_callee_count", Integer.toString(fifthLevelCalleeMethods.size()), true);
        field(out, "sixth_level_callee_count", Integer.toString(sixthLevelCalleeMethods.size()), true);
        field(out, "analysis_method_budget", Integer.toString(MAX_ANALYSIS_METHODS), true);
        field(out, "fourth_level_reserved_budget", Integer.toString(MAX_FOURTH_HOP_RESERVE), true);
        field(out, "fifth_level_reserved_budget", Integer.toString(MAX_FIFTH_HOP_RESERVE), true);
        field(out, "sixth_level_reserved_budget", Integer.toString(MAX_SIXTH_HOP_RESERVE), true);
        field(out, "cross_dex_candidate_count", Integer.toString(crossDexMethodSources.size()), true);
        field(out, "unresolved_app_call_count", Integer.toString(unresolvedAppCalls.size()), true);
        field(out, "unresolved_app_calls", stringArray(unresolvedAppCalls), true);
        field(out, "candidate_count", Integer.toString(allEvidenceMethods.size()), true);
        field(out, "analyzed_methods", Integer.toString(evidence.analyzedMethods + crossDexAnalyzed), true);
        field(out, "analysis_failures", Integer.toString(evidence.analysisFailures + crossDexFailures), true);
        int invokeFailures = 0;
        for (RoiDexCandidateEvidence.MethodEvidence method : allEvidenceMethods) {
            if (method.invokeAnalysisFailure.length() > 0) invokeFailures++;
        }
        field(out, "invoke_analysis_failures", Integer.toString(invokeFailures), true);
        field(out, "hook_authorization_granted", "false", true);
        field(out, "hook_activation", quote("not_performed"), true);
        out.append("  \"candidates\": [");
        for (int i = 0; i < allEvidenceMethods.size(); i++) {
            RoiDexCandidateEvidence.MethodEvidence method = allEvidenceMethods.get(i);
            if (i > 0) out.append(",");
            out.append("\n    {");
            out.append("\"method\":").append(quote(method.methodKey));
            String candidateSourceDex = crossDexMethodSources.get(method.methodKey);
            out.append(",\"source_dex\":").append(quote(
                    candidateSourceDex == null ? scope : candidateSourceDex));
            Set<String> methodAnchors = selected.get(method.methodKey);
            out.append(",\"candidate_origin\":")
                    .append(quote(candidateSourceDex != null ? "cross_dex_callee"
                            : methodAnchors != null ? "anchor"
                            : directCalleeMethods.contains(method.methodKey) ? "direct_callee"
                            : transitiveCalleeMethods.contains(method.methodKey) ? "transitive_callee"
                            : thirdLevelCalleeMethods.contains(method.methodKey) ? "third_level_callee"
                            : fourthLevelCalleeMethods.contains(method.methodKey) ? "fourth_level_callee"
                            : fifthLevelCalleeMethods.contains(method.methodKey) ? "fifth_level_callee"
                            : sixthLevelCalleeMethods.contains(method.methodKey) ? "sixth_level_callee"
                            : "expanded_callee"));
            out.append(",\"anchors\":").append(stringArray(methodAnchors));
            out.append(",\"anchor_priority\":").append(anchorPriority(methodAnchors));
            out.append(",\"analysis_status\":")
                    .append(quote(method.analysis == null ? "failed_or_missing_code" : "analyzed"));
            out.append(",\"analysis_failure\":").append(quote(method.analysisFailure));
            out.append(",\"invoke_analysis_failure\":")
                    .append(quote(method.invokeAnalysisFailure));
            out.append(",\"invokes_complete\":")
                    .append(method.invokeAnalysisFailure.length() == 0);
            out.append(",\"invokes\":").append(stringArray(method.invokes));
            out.append(",\"reachable_invokes\":").append(stringArray(method.reachableInvokes));
            out.append(",\"unreachable_invokes\":").append(stringArray(method.unreachableInvokes));
            out.append(",\"invoke_reachability_complete\":").append(method.invokeReachabilityComplete);
            if (method.analysis != null) {
                out.append(",\"exception_edges_included\":")
                        .append(method.analysis.exceptionEdgesIncluded);
                out.append(",\"complete_for_exception_flow\":")
                        .append(method.analysis.completeForExceptionFlow);
                out.append(",\"candidate_evidence_only\":")
                        .append(method.analysis.candidateEvidenceOnly);
                out.append(",\"hook_authorization_granted\":")
                        .append(method.analysis.hookAuthorizationGranted);
            }
            out.append("}");
        }
        if (!allEvidenceMethods.isEmpty()) out.append("\n  ");
        out.append("]\n}\n");
        File reportParent = reportPath.getAbsoluteFile().getParentFile();
        if (reportParent != null && !reportParent.isDirectory() && !reportParent.mkdirs()
                && !reportParent.isDirectory()) {
            throw new IOException("report_directory_create_failed:" + reportParent);
        }
        FileOutputStream reportOutput = new FileOutputStream(reportPath);
        try {
            reportOutput.write(out.toString().getBytes("UTF-8"));
        } finally {
            reportOutput.close();
        }
        System.out.println("DEX ROI candidate scan: " + scope
                + " candidates=" + allEvidenceMethods.size()
                + " analyzed=" + (evidence.analyzedMethods + crossDexAnalyzed)
                + " failures=" + (evidence.analysisFailures + crossDexFailures)
                + " hookAuthorization=false");
    }

    private static byte[] readFile(File file) throws IOException {
        FileInputStream input = new FileInputStream(file);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toByteArray();
        } finally {
            try { input.close(); } finally { output.close(); }
        }
    }

    private static void addCandidate(Map<String, Set<String>> selected,
            String key, Set<String> hits) {
        Set<String> existing = selected.get(key);
        if (existing != null) {
            existing.addAll(hits);
        } else if (selected.size() < MAX_SELECTED_METHODS) {
            selected.put(key, new LinkedHashSet<String>(hits));
        } else {
            int incomingPriority = anchorPriority(hits);
            String lowestKey = null;
            int lowestPriority = Integer.MAX_VALUE;
            for (Map.Entry<String, Set<String>> entry : selected.entrySet()) {
                int priority = anchorPriority(entry.getValue());
                if (priority < lowestPriority) {
                    lowestPriority = priority;
                    lowestKey = entry.getKey();
                }
            }
            if (lowestKey != null && incomingPriority > lowestPriority) {
                selected.remove(lowestKey);
                selected.put(key, new LinkedHashSet<String>(hits));
            }
        }
    }

    private static int anchorPriority(Set<String> hits) {
        if (hits == null) return 0;
        int score = 0;
        if (hits.contains("chat/completions")) score += 1000;
        if (hits.contains("anchor_constant_field_ref")) score += 750;
        if (hits.contains("parent_message_id")) score += 900;
        if (hits.contains("thinking_enabled")) score += 850;
        if (hits.contains("search_enabled")) score += 850;
        if (hits.contains("preempt")) score += 800;
        if (hits.contains("chat_session_id")) score += 700;
        if (hits.contains("messages")) score += 650;
        if (hits.contains("Authorization")) score += 300;
        if (hits.contains("application/json")) score += 200;
        return score;
    }

    private static boolean isPlatformCall(String methodKey) {
        return methodKey.startsWith("Ljava/") || methodKey.startsWith("Ljavax/")
                || methodKey.startsWith("Landroid/") || methodKey.startsWith("Landroidx/")
                || methodKey.startsWith("Lkotlin/") || methodKey.startsWith("Lkotlinx/")
                || methodKey.startsWith("Lj$/") || methodKey.startsWith("Ldalvik/")
                || methodKey.startsWith("Lsun/") || methodKey.startsWith("Lorg/json/");
    }

    private static int u16(short value) { return value & 0xffff; }

    private static void field(StringBuilder out, String key, String value, boolean comma) {
        out.append("  ").append(quote(key)).append(": ").append(value);
        if (comma) out.append(",");
        out.append("\n");
    }

    private static String stringArray(Iterable<String> values) {
        StringBuilder out = new StringBuilder("[");
        boolean first = true;
        if (values == null) return out.append("]").toString();
        for (String value : values) {
            if (!first) out.append(",");
            first = false;
            out.append(quote(value));
        }
        return out.append("]").toString();
    }

    private static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) out.append(String.format("\\u%04x", (int)c));
                    else out.append(c);
            }
        }
        return out.append('"').toString();
    }
}

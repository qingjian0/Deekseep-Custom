package ds.dsh.DeepSeek;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Instruction-level use-to-definition links derived from normal-CFG reaching definitions.
 * A use with no reaching definition is an entry/unknown value, not proof of an uninitialized read.
 */
public final class RoiDexUseDefChains {
    public enum EntryValueKind { NONE, RECEIVER, PARAMETER, UNDEFINED_OR_UNKNOWN, UNMAPPED_ENTRY }
    private RoiDexUseDefChains() {}

    private static final int MAX_USE_SITES = 200000;
    private static final long MAX_CHAIN_LINKS = 2000000L;
    private static final long MAX_DEFINITE_FLOW_CELLS = 20000000L;

    public static final class AnalysisException extends Exception {
        public AnalysisException(String message) { super(message); }
    }

    public static final class UseSite {
        public final int blockIndex;
        public final int instructionOffset;
        public final int opcode;
        public final int operandIndex;
        public final int register;
        public final RoiDexReachingDefinitions.Definition[] reachingDefinitions;
        /** True means no modeled definition reaches this use; it may be a method-entry value. */
        public final boolean mayBeEntryOrUnknownValue;
        public final boolean reachableFromEntry;
        public final EntryValueKind entryValueKind;
        public final int entryParameterIndex;
        public final String entryDescriptor;

        UseSite(int block, int offset, int opcode, int operand, int register,
                RoiDexReachingDefinitions.Definition[] definitions,
                boolean mayBeUnknown, boolean reachable,
                RoiDexMethodEntryLayout.EntryValue entryValue, boolean hasEntryLayout) {
            blockIndex = block;
            instructionOffset = offset;
            this.opcode = opcode;
            operandIndex = operand;
            this.register = register;
            reachingDefinitions = definitions;
            mayBeEntryOrUnknownValue = mayBeUnknown;
            reachableFromEntry = reachable;
            if (!mayBeUnknown) {
                entryValueKind = EntryValueKind.NONE;
            } else if (entryValue != null) {
                entryValueKind = entryValue.kind == RoiDexMethodEntryLayout.Kind.RECEIVER
                        ? EntryValueKind.RECEIVER : EntryValueKind.PARAMETER;
            } else {
                entryValueKind = hasEntryLayout ? EntryValueKind.UNDEFINED_OR_UNKNOWN
                        : EntryValueKind.UNMAPPED_ENTRY;
            }
            entryParameterIndex = entryValue == null ? -1 : entryValue.parameterIndex;
            entryDescriptor = entryValue == null ? null : entryValue.descriptor;
        }
    }

    public static final class Result {
        public final UseSite[] uses;
        public final long chainLinkCount;
        public final boolean normalControlFlowOnly = true;
        public final boolean exceptionEdgesIncluded = false;
        public final boolean completeForExceptionFlow = false;

        Result(List<UseSite> uses, long links) {
            this.uses = uses.toArray(new UseSite[0]);
            chainLinkCount = links;
        }
    }

    public static Result analyze(RoiDexRegisterFlowAnalyzer.Result flow,
            RoiDexReachingDefinitions.Result reaching) throws AnalysisException {
        return analyze(flow, reaching, null);
    }

    public static Result analyze(RoiDexRegisterFlowAnalyzer.Result flow,
            RoiDexReachingDefinitions.Result reaching,
            RoiDexMethodEntryLayout.Result entryLayout) throws AnalysisException {
        if (flow == null || reaching == null)
            throw new AnalysisException("null_analysis_input");
        if (flow.blocks.length != reaching.blocks.length)
            throw new AnalysisException("block_count_mismatch");

        boolean[][] definitelyIn = computeDefinitelyDefinedIn(flow, reaching);
        if (entryLayout != null) validateEntryLayout(flow, entryLayout);

        Map<Long, RoiDexReachingDefinitions.Definition> definitionAt =
                new HashMap<Long, RoiDexReachingDefinitions.Definition>();
        for (RoiDexReachingDefinitions.Definition definition : reaching.definitions)
            definitionAt.put(key(definition.instructionOffset, definition.register), definition);

        List<UseSite> sites = new ArrayList<UseSite>();
        long links = 0;
        for (int b = 0; b < flow.blocks.length; b++) {
            RoiDexRegisterFlowAnalyzer.BasicBlockFlow block = flow.blocks[b];
            RoiDexReachingDefinitions.BlockResult blockState = reaching.blocks[b];
            if (blockState.blockIndex != b || blockState.startOffset != block.startOffset
                    || blockState.endOffset != block.endOffset)
                throw new AnalysisException("block_identity_mismatch");

            Map<Integer, BitSet> current = new HashMap<Integer, BitSet>();
            BitSet definitelyDefined = bitSetFrom(definitelyIn[b]);
            for (RoiDexReachingDefinitions.Definition definition : blockState.reachingIn) {
                BitSet ids = current.get(definition.register);
                if (ids == null) {
                    ids = new BitSet(reaching.definitions.length);
                    current.put(definition.register, ids);
                }
                ids.set(definition.id);
            }

            for (RoiDexRegisterFlowAnalyzer.InstructionFlow instruction : block.instructions) {
                for (int operand = 0; operand < instruction.uses.length; operand++) {
                    if (sites.size() >= MAX_USE_SITES)
                        throw new AnalysisException("use_site_budget_exceeded");
                    int register = instruction.uses[operand];
                    BitSet ids = current.get(register);
                    int count = ids == null ? 0 : ids.cardinality();
                    links += count;
                    if (links > MAX_CHAIN_LINKS)
                        throw new AnalysisException("use_def_link_budget_exceeded");
                    RoiDexReachingDefinitions.Definition[] candidates =
                            new RoiDexReachingDefinitions.Definition[count];
                    int cursor = 0;
                    if (ids != null) {
                        for (int id = ids.nextSetBit(0); id >= 0; id = ids.nextSetBit(id + 1)) {
                            if (id >= reaching.definitions.length)
                                throw new AnalysisException("definition_id_out_of_bounds");
                            candidates[cursor++] = reaching.definitions[id];
                        }
                    }
                    boolean reachable = blockState.reachableFromEntry;
                    boolean mayBeUnknown = !reachable || register >= definitelyIn[b].length
                            || !definitelyDefined.get(register);
                    RoiDexMethodEntryLayout.EntryValue entryValue = entryLayout == null
                            ? null : entryLayout.atRegister(register);
                    sites.add(new UseSite(b, instruction.offset, instruction.opcode,
                            operand, register, candidates, mayBeUnknown, reachable,
                            entryValue, entryLayout != null));
                }

                for (int register : instruction.definitions) {
                    RoiDexReachingDefinitions.Definition definition =
                            definitionAt.get(key(instruction.offset, register));
                    if (definition == null)
                        throw new AnalysisException("definition_site_missing:"
                                + instruction.offset + ":" + register);
                    BitSet ids = new BitSet(reaching.definitions.length);
                    ids.set(definition.id);
                    current.put(register, ids);
                    definitelyDefined.set(register);
                }
            }
        }
        return new Result(sites, links);
    }



    private static void validateEntryLayout(RoiDexRegisterFlowAnalyzer.Result flow,
            RoiDexMethodEntryLayout.Result layout) throws AnalysisException {
        for (RoiDexRegisterFlowAnalyzer.BasicBlockFlow block : flow.blocks) {
            for (RoiDexRegisterFlowAnalyzer.InstructionFlow instruction : block.instructions) {
                for (int register : instruction.uses)
                    if (register >= layout.registersSize)
                        throw new AnalysisException("entry_layout_register_count_mismatch");
                for (int register : instruction.definitions)
                    if (register >= layout.registersSize)
                        throw new AnalysisException("entry_layout_register_count_mismatch");
            }
        }
    }

    /**
     * Must-analysis: a register word is definitely defined only if every reachable
     * path from method entry defines it before the use. Entry parameters remain
     * unknown until a separate prototype/ins_size mapping is supplied.
     */
    private static boolean[][] computeDefinitelyDefinedIn(
            RoiDexRegisterFlowAnalyzer.Result flow,
            RoiDexReachingDefinitions.Result reaching) throws AnalysisException {
        int blocks = flow.blocks.length;
        int registerCount = 0;
        for (RoiDexRegisterFlowAnalyzer.BasicBlockFlow block : flow.blocks) {
            for (RoiDexRegisterFlowAnalyzer.InstructionFlow instruction : block.instructions) {
                for (int r : instruction.uses) registerCount = Math.max(registerCount, r + 1);
                for (int r : instruction.definitions) registerCount = Math.max(registerCount, r + 1);
            }
        }
        if (registerCount > 0xffff)
            throw new AnalysisException("register_domain_exceeded");
        if ((long) blocks * registerCount > MAX_DEFINITE_FLOW_CELLS)
            throw new AnalysisException("definite_definition_memory_budget_exceeded");

        @SuppressWarnings("unchecked")
        List<Integer>[] predecessors = new List[blocks];
        for (int i = 0; i < blocks; i++) predecessors[i] = new ArrayList<Integer>();
        for (RoiDexInstructionScanner.Edge edge : flow.edges) {
            if (edge.fromBlock < 0 || edge.fromBlock >= blocks
                    || edge.toBlock < 0 || edge.toBlock >= blocks)
                throw new AnalysisException("cfg_edge_out_of_bounds");
            if (!predecessors[edge.toBlock].contains(edge.fromBlock))
                predecessors[edge.toBlock].add(edge.fromBlock);
        }

        BitSet universe = new BitSet(registerCount);
        universe.set(0, registerCount);
        BitSet[] in = new BitSet[blocks];
        BitSet[] out = new BitSet[blocks];
        for (int b = 0; b < blocks; b++) {
            boolean reachable = reaching.blocks[b].reachableFromEntry;
            in[b] = reachable && b != 0 ? (BitSet) universe.clone() : new BitSet(registerCount);
            out[b] = reachable && b != 0 ? (BitSet) universe.clone() : new BitSet(registerCount);
        }

        boolean changed;
        long iterations = 0;
        long maxIterations = Math.max(1L, (long) blocks * Math.max(1, registerCount) + 1L);
        do {
            changed = false;
            if (++iterations > maxIterations)
                throw new AnalysisException("definite_definition_budget_exceeded");
            for (int b = 0; b < blocks; b++) {
                if (!reaching.blocks[b].reachableFromEntry) continue;
                BitSet nextIn;
                if (b == 0) {
                    nextIn = new BitSet(registerCount);
                } else {
                    nextIn = null;
                    for (int predecessor : predecessors[b]) {
                        if (!reaching.blocks[predecessor].reachableFromEntry) continue;
                        if (nextIn == null) nextIn = (BitSet) out[predecessor].clone();
                        else nextIn.and(out[predecessor]);
                    }
                    if (nextIn == null) nextIn = new BitSet(registerCount);
                }
                BitSet nextOut = (BitSet) nextIn.clone();
                for (RoiDexRegisterFlowAnalyzer.InstructionFlow instruction
                        : flow.blocks[b].instructions)
                    for (int register : instruction.definitions) nextOut.set(register);
                if (!nextIn.equals(in[b]) || !nextOut.equals(out[b])) {
                    in[b] = nextIn;
                    out[b] = nextOut;
                    changed = true;
                }
            }
        } while (changed);

        boolean[][] result = new boolean[blocks][registerCount];
        for (int b = 0; b < blocks; b++)
            for (int r = in[b].nextSetBit(0); r >= 0; r = in[b].nextSetBit(r + 1))
                result[b][r] = true;
        return result;
    }

    private static BitSet bitSetFrom(boolean[] values) {
        BitSet result = new BitSet(values.length);
        for (int i = 0; i < values.length; i++) if (values[i]) result.set(i);
        return result;
    }

    private static long key(int offset, int register) {
        return (((long) offset) << 32) ^ (register & 0xffffffffL);
    }
}

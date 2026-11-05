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
    private RoiDexUseDefChains() {}

    private static final int MAX_USE_SITES = 200000;
    private static final long MAX_CHAIN_LINKS = 2000000L;

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

        UseSite(int block, int offset, int opcode, int operand, int register,
                RoiDexReachingDefinitions.Definition[] definitions) {
            blockIndex = block;
            instructionOffset = offset;
            this.opcode = opcode;
            operandIndex = operand;
            this.register = register;
            reachingDefinitions = definitions;
            mayBeEntryOrUnknownValue = definitions.length == 0;
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
        if (flow == null || reaching == null)
            throw new AnalysisException("null_analysis_input");
        if (flow.blocks.length != reaching.blocks.length)
            throw new AnalysisException("block_count_mismatch");

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
                    sites.add(new UseSite(b, instruction.offset, instruction.opcode,
                            operand, register, candidates));
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
                }
            }
        }
        return new Result(sites, links);
    }

    private static long key(int offset, int register) {
        return (((long) offset) << 32) ^ (register & 0xffffffffL);
    }
}

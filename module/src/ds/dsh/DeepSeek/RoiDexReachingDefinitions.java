package ds.dsh.DeepSeek;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bounded forward reaching-definitions analysis over the scanner's normal CFG.
 * Exception-handler edges are not represented by the current CFG.
 */
public final class RoiDexReachingDefinitions {
    private RoiDexReachingDefinitions() {}

    private static final int MAX_DEFINITIONS = 100000;
    private static final long MAX_FLOW_CELLS = 20000000L;
    private static final long MAX_WORKLIST_STEPS = 20000000L;

    public static final class AnalysisException extends Exception {
        public AnalysisException(String message) { super(message); }
    }

    /** A definition is tracked per register word; wide writes have two records. */
    public static final class Definition {
        public final int id;
        public final int register;
        public final int instructionOffset;
        public final int opcode;

        Definition(int id, int register, int offset, int opcode) {
            this.id = id;
            this.register = register;
            this.instructionOffset = offset;
            this.opcode = opcode;
        }
    }

    public static final class BlockResult {
        public final int blockIndex;
        public final int startOffset;
        public final int endOffset;
        public final Definition[] reachingIn;
        public final Definition[] reachingOut;

        BlockResult(int index, int start, int end, Definition[] in, Definition[] out) {
            blockIndex = index;
            startOffset = start;
            endOffset = end;
            reachingIn = in;
            reachingOut = out;
        }
    }

    public static final class Result {
        public final Definition[] definitions;
        public final BlockResult[] blocks;
        public final int worklistSteps;
        public final boolean normalControlFlowOnly = true;
        public final boolean exceptionEdgesIncluded = false;
        public final boolean completeForExceptionFlow = false;

        Result(List<Definition> definitions, List<BlockResult> blocks, int steps) {
            this.definitions = definitions.toArray(new Definition[0]);
            this.blocks = blocks.toArray(new BlockResult[0]);
            worklistSteps = steps;
        }
    }

    public static Result analyze(RoiDexRegisterFlowAnalyzer.Result flow)
            throws AnalysisException {
        if (flow == null) throw new AnalysisException("null_flow");
        final int blockCount = flow.blocks.length;
        if (blockCount == 0)
            return new Result(new ArrayList<Definition>(),
                    new ArrayList<BlockResult>(), 0);

        List<Definition> definitions = new ArrayList<Definition>();
        List<Map<Integer, Integer>> finalDefinitionByRegister =
                new ArrayList<Map<Integer, Integer>>(blockCount);

        for (int b = 0; b < blockCount; b++) {
            Map<Integer, Integer> last = new LinkedHashMap<Integer, Integer>();
            for (RoiDexRegisterFlowAnalyzer.InstructionFlow instruction
                    : flow.blocks[b].instructions) {
                for (int register : instruction.definitions) {
                    if (definitions.size() >= MAX_DEFINITIONS)
                        throw new AnalysisException("definition_budget_exceeded");
                    int id = definitions.size();
                    definitions.add(new Definition(id, register,
                            instruction.offset, instruction.opcode));
                    last.put(register, id);
                }
            }
            finalDefinitionByRegister.add(last);
        }

        long flowCells = (long) blockCount * Math.max(1, definitions.size());
        if (flowCells > MAX_FLOW_CELLS)
            throw new AnalysisException("flow_memory_budget_exceeded");

        @SuppressWarnings("unchecked")
        List<Integer>[] definitionsByRegister =
                new List[Math.max(1, maxRegister(definitions) + 1)];
        for (Definition definition : definitions) {
            List<Integer> ids = definitionsByRegister[definition.register];
            if (ids == null) {
                ids = new ArrayList<Integer>();
                definitionsByRegister[definition.register] = ids;
            }
            ids.add(definition.id);
        }

        BitSet[] gen = new BitSet[blockCount];
        BitSet[] kill = new BitSet[blockCount];
        BitSet[] in = new BitSet[blockCount];
        BitSet[] out = new BitSet[blockCount];
        List<Integer>[] predecessors = lists(blockCount);
        List<Integer>[] successors = lists(blockCount);

        for (RoiDexInstructionScanner.Edge edge : flow.edges) {
            if (edge.fromBlock < 0 || edge.fromBlock >= blockCount
                    || edge.toBlock < 0 || edge.toBlock >= blockCount)
                throw new AnalysisException("cfg_edge_out_of_bounds");
            if (!predecessors[edge.toBlock].contains(edge.fromBlock))
                predecessors[edge.toBlock].add(edge.fromBlock);
            if (!successors[edge.fromBlock].contains(edge.toBlock))
                successors[edge.fromBlock].add(edge.toBlock);
        }

        for (int b = 0; b < blockCount; b++) {
            gen[b] = new BitSet(definitions.size());
            kill[b] = new BitSet(definitions.size());
            in[b] = new BitSet(definitions.size());
            out[b] = new BitSet(definitions.size());

            for (Map.Entry<Integer, Integer> entry
                    : finalDefinitionByRegister.get(b).entrySet()) {
                int register = entry.getKey();
                int finalId = entry.getValue();
                gen[b].set(finalId);
                List<Integer> all = definitionsByRegister[register];
                if (all != null)
                    for (int id : all) if (id != finalId) kill[b].set(id);
            }
            out[b].or(gen[b]);
        }

        ArrayDeque<Integer> worklist = new ArrayDeque<Integer>();
        boolean[] queued = new boolean[blockCount];
        for (int b = 0; b < blockCount; b++) {
            worklist.addLast(b);
            queued[b] = true;
        }

        long steps = 0;
        while (!worklist.isEmpty()) {
            if (++steps > MAX_WORKLIST_STEPS)
                throw new AnalysisException("fixed_point_budget_exceeded");
            int b = worklist.removeFirst();
            queued[b] = false;

            BitSet nextIn = new BitSet(definitions.size());
            for (int predecessor : predecessors[b]) nextIn.or(out[predecessor]);
            BitSet nextOut = (BitSet) nextIn.clone();
            nextOut.andNot(kill[b]);
            nextOut.or(gen[b]);

            boolean outChanged = !nextOut.equals(out[b]);
            if (!nextIn.equals(in[b]) || outChanged) {
                in[b] = nextIn;
                out[b] = nextOut;
                if (outChanged) {
                    for (int successor : successors[b]) {
                        if (!queued[successor]) {
                            queued[successor] = true;
                            worklist.addLast(successor);
                        }
                    }
                }
            }
        }

        List<BlockResult> results = new ArrayList<BlockResult>(blockCount);
        for (int b = 0; b < blockCount; b++) {
            RoiDexRegisterFlowAnalyzer.BasicBlockFlow block = flow.blocks[b];
            results.add(new BlockResult(b, block.startOffset, block.endOffset,
                    materialize(in[b], definitions), materialize(out[b], definitions)));
        }
        return new Result(definitions, results,
                (int) Math.min(Integer.MAX_VALUE, steps));
    }

    private static Definition[] materialize(BitSet ids, List<Definition> definitions) {
        Definition[] result = new Definition[ids.cardinality()];
        int cursor = 0;
        for (int id = ids.nextSetBit(0); id >= 0; id = ids.nextSetBit(id + 1))
            result[cursor++] = definitions.get(id);
        return result;
    }

    private static int maxRegister(List<Definition> definitions) {
        int max = -1;
        for (Definition definition : definitions)
            max = Math.max(max, definition.register);
        return max;
    }

    @SuppressWarnings("unchecked")
    private static List<Integer>[] lists(int size) {
        List<Integer>[] result = new List[size];
        for (int i = 0; i < size; i++) result[i] = new ArrayList<Integer>();
        return result;
    }
}

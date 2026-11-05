package ds.dsh.DeepSeek;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Conservative DEX register def/use analysis within individual basic blocks.
 * Cross-block reaching definitions are deliberately a separate future phase.
 * Unknown instruction semantics fail closed; no register roles are guessed.
 */
public final class RoiDexRegisterFlowAnalyzer {
    private RoiDexRegisterFlowAnalyzer() {}

    public static final class AnalysisException extends Exception {
        public AnalysisException(String message) { super(message); }
    }

    public static final class InstructionFlow {
        public final int offset, opcode;
        public final int[] uses, definitions;
        InstructionFlow(int offset, int opcode, int[] uses, int[] definitions) {
            this.offset = offset; this.opcode = opcode;
            this.uses = uses; this.definitions = definitions;
        }
    }

    public static final class BasicBlockFlow {
        public final int startOffset, endOffset;
        public final InstructionFlow[] instructions;
        public final int[] useBeforeDefinition, definitions;
        BasicBlockFlow(int start, int end, List<InstructionFlow> body,
                Set<Integer> uses, Set<Integer> defs) {
            startOffset = start; endOffset = end;
            instructions = body.toArray(new InstructionFlow[0]);
            useBeforeDefinition = array(uses); definitions = array(defs);
        }
    }

    public static final class Result {
        public final BasicBlockFlow[] blocks;
        public final RoiDexInstructionScanner.Edge[] edges;
        Result(List<BasicBlockFlow> blocks, RoiDexInstructionScanner.Edge[] edges) {
            this.blocks = blocks.toArray(new BasicBlockFlow[0]);
            this.edges = edges.clone();
        }
    }

    public static Result analyze(short[] code, int registerCount) throws AnalysisException {
        if (registerCount < 0 || registerCount > 0xffff)
            throw new AnalysisException("register_count_invalid");
        final RoiDexInstructionScanner.ControlFlowGraph cfg;
        try {
            cfg = RoiDexInstructionScanner.buildControlFlowGraph(code);
        } catch (RoiDexInstructionScanner.ScanException error) {
            throw new AnalysisException("cfg_invalid:" + error.getMessage());
        }
        List<BasicBlockFlow> output = new ArrayList<BasicBlockFlow>();
        for (RoiDexInstructionScanner.BasicBlock block : cfg.blocks) {
            List<InstructionFlow> body = new ArrayList<InstructionFlow>();
            Set<Integer> liveIn = new LinkedHashSet<Integer>();
            Set<Integer> defined = new LinkedHashSet<Integer>();
            Set<Integer> seenDefinitions = new LinkedHashSet<Integer>();
            for (RoiDexInstructionScanner.Instruction instruction : block.instructions) {
                InstructionFlow flow = decode(code, instruction.offset,
                        instruction.opcode, registerCount);
                body.add(flow);
                for (int register : flow.uses)
                    if (!seenDefinitions.contains(register)) liveIn.add(register);
                for (int register : flow.definitions) {
                    seenDefinitions.add(register);
                    defined.add(register);
                }
            }
            output.add(new BasicBlockFlow(block.startOffset, block.endOffset,
                    body, liveIn, defined));
        }
        return new Result(output, cfg.edges);
    }

    private static InstructionFlow decode(short[] code, int offset, int op, int count)
            throws AnalysisException {
        int first = u(code[offset]);
        int a8 = first >>> 8;
        int a4 = (first >>> 8) & 0xf;
        int b4 = (first >>> 12) & 0xf;
        int[] uses = none(), defs = none();

        if (op >= 0x01 && op <= 0x09) { // move family
            if (op == 0x01 || op == 0x04 || op == 0x07) {
                uses = one(b4, count); defs = one(a4, count);
            } else if (op == 0x02 || op == 0x05 || op == 0x08) {
                uses = one(u(code[offset + 1]), count); defs = one(a8, count);
            } else {
                uses = one(u(code[offset + 2]), count);
                defs = one(u(code[offset + 1]), count);
            }
            if (op == 0x04 || op == 0x05 || op == 0x06) {
                uses = wide(uses[0], count); defs = wide(defs[0], count);
            }
            if (op == 0x07 || op == 0x08 || op == 0x09) {
                uses = one(uses[0], count); defs = one(defs[0], count);
            }
        } else if (op >= 0x0a && op <= 0x0d) { // move-result / move-exception
            defs = op == 0x0b ? wide(a8, count) : one(a8, count);
        } else if (op >= 0x0e && op <= 0x11) { // return
            if (op == 0x0f || op == 0x11) uses = one(a8, count);
            if (op == 0x10) uses = wide(a8, count);
        } else if (op == 0x12) { // const/4 (11n)
            defs = one(a4, count);
        } else if (op >= 0x13 && op <= 0x15) { // const/16, const, const/high16
            defs = one(a8, count);
        } else if (op >= 0x16 && op <= 0x19) { // const-wide variants
            defs = wide(a8, count);
        } else if (op >= 0x1a && op <= 0x1c) { // string/class constants
            defs = one(a8, count);
        } else if (op == 0x1d || op == 0x1e || op == 0x26 || op == 0x27) {
            uses = one(a8, count); // monitor, fill-array-data, throw
        } else if (op == 0x1f) { // check-cast mutates the same register
            uses = one(a8, count); defs = one(a8, count);
        } else if (op == 0x20) { // instance-of 22c
            defs = one(a4, count); uses = one(b4, count);
        } else if (op == 0x21) { // array-length 12x
            defs = one(a4, count); uses = one(b4, count);
        } else if (op == 0x22) { // new-instance
            defs = one(a8, count);
        } else if (op == 0x23) { // new-array 22c
            defs = one(a4, count); uses = one(b4, count);
        } else if (op == 0x24 || op == 0x25
                || (op >= 0x6e && op <= 0x72)
                || (op >= 0x74 && op <= 0x78)) {
            uses = invokeRegisters(code, offset, op, count);
        } else if (op == 0x00 || (op >= 0x28 && op <= 0x2a)) { // nop / goto
            // No register operands.
        } else if (op == 0x2b || op == 0x2c) { // switch
            uses = one(a8, count);
        } else if (op >= 0x2d && op <= 0x31) { // compare 23x
            defs = one(a8, count);
            boolean wideSources = op == 0x2f || op == 0x30 || op == 0x31;
            int left = u(code[offset + 1]) & 0xff;
            int right = (u(code[offset + 1]) >>> 8) & 0xff;
            uses = wideSources ? join(wide(left, count), wide(right, count))
                    : pair(left, right, count);
        } else if (op >= 0x32 && op <= 0x37) { // if-test 22t
            uses = pair(a4, b4, count);
        } else if (op >= 0x38 && op <= 0x3d) { // if-testz 21t
            uses = one(a8, count);
        } else if (op >= 0x44 && op <= 0x4a) { // aget
            int value = a8, array = u(code[offset + 1]) & 0xff;
            int index = (u(code[offset + 1]) >>> 8) & 0xff;
            defs = (op == 0x45) ? wide(value, count) : one(value, count);
            uses = pair(array, index, count);
        } else if (op >= 0x4b && op <= 0x51) { // aput
            int value = a8, array = u(code[offset + 1]) & 0xff;
            int index = (u(code[offset + 1]) >>> 8) & 0xff;
            uses = join((op == 0x4c) ? wide(value, count) : one(value, count),
                    pair(array, index, count));
        } else if (op >= 0x52 && op <= 0x58) { // iget
            defs = (op == 0x53) ? wide(a4, count) : one(a4, count);
            uses = one(b4, count);
        } else if (op >= 0x59 && op <= 0x5f) { // iput
            uses = join((op == 0x5a) ? wide(a4, count) : one(a4, count),
                    one(b4, count));
        } else if (op >= 0x60 && op <= 0x66) { // sget
            defs = (op == 0x61) ? wide(a8, count) : one(a8, count);
        } else if (op >= 0x67 && op <= 0x6d) { // sput
            uses = (op == 0x68) ? wide(a8, count) : one(a8, count);
        } else if (op >= 0x7b && op <= 0x8f) { // unary / conversion
            boolean destWide = op == 0x7d || op == 0x7e || op == 0x80
                    || op == 0x81 || op == 0x83 || op == 0x86
                    || op == 0x88 || op == 0x89 || op == 0x8b;
            boolean sourceWide = op == 0x7d || op == 0x7e || op == 0x80
                    || op == 0x84 || op == 0x85 || op == 0x86
                    || op == 0x8a || op == 0x8b || op == 0x8c;
            defs = destWide ? wide(a4, count) : one(a4, count);
            uses = sourceWide ? wide(b4, count) : one(b4, count);
        } else if (op >= 0x90 && op <= 0xaf) { // binary 23x
            boolean wide = (op >= 0x9b && op <= 0xa5) || op >= 0xab;
            defs = wide ? wide(a8, count) : one(a8, count);
            int left = u(code[offset + 1]) & 0xff;
            int right = (u(code[offset + 1]) >>> 8) & 0xff;
            uses = wide ? join(wide(left, count),
                    (op >= 0xa3 && op <= 0xa5) ? one(right, count) : wide(right, count))
                    : pair(left, right, count);
        } else if (op >= 0xb0 && op <= 0xcf) { // binary/2addr
            boolean wide = (op >= 0xbb && op <= 0xc5) || (op >= 0xcb && op <= 0xcf);
            defs = wide ? wide(a4, count) : one(a4, count);
            if (!wide) uses = pair(a4, b4, count);
            else uses = join(wide(a4, count),
                    (op >= 0xc3 && op <= 0xc5) ? one(b4, count) : wide(b4, count));
        } else if (op >= 0xd0 && op <= 0xd7) { // 22s int literal arithmetic
            defs = one(a4, count);
            uses = one(b4, count);
        } else if (op >= 0xd8 && op <= 0xe2) { // 22b int literal arithmetic
            defs = one(a8, count);
            uses = one(u(code[offset + 1]) & 0xff, count);
        } else {
            throw new AnalysisException("unsupported_register_semantics_opcode_0x"
                    + Integer.toHexString(op) + "_at_" + offset);
        }
        return new InstructionFlow(offset, op, uses, defs);
    }

    private static int[] invokeRegisters(short[] code, int offset, int op, int count)
            throws AnalysisException {
        int first = u(code[offset]);
        if (op == 0x24 || (op >= 0x6e && op <= 0x72)) {
            int words = (first >>> 8) & 0xf;
            if (words > 5) throw new AnalysisException("invoke_word_count_invalid");
            int packed = u(code[offset + 2]);
            int[] all = {packed & 0xf, (packed >>> 4) & 0xf,
                    (packed >>> 8) & 0xf, (packed >>> 12) & 0xf,
                    (first >>> 12) & 0xf};
            int[] result = new int[words];
            System.arraycopy(all, 0, result, 0, words);
            return checked(result, count);
        }
        int words = (first >>> 8) & 0xff;
        int start = u(code[offset + 2]);
        if ((long) start + words > count)
            throw new AnalysisException("invoke_range_out_of_bounds");
        int[] result = new int[words];
        for (int i = 0; i < words; i++) result[i] = start + i;
        return result;
    }

    private static int[] one(int register, int count) throws AnalysisException {
        return checked(new int[]{register}, count);
    }
    private static int[] pair(int first, int second, int count) throws AnalysisException {
        return checked(new int[]{first, second}, count);
    }
    private static int[] wide(int register, int count) throws AnalysisException {
        if (register < 0 || register + 1 >= count)
            throw new AnalysisException("wide_register_out_of_bounds:" + register);
        return new int[]{register, register + 1};
    }
    private static int[] join(int[] first, int[] second) {
        int[] result = new int[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
    private static int[] checked(int[] values, int count) throws AnalysisException {
        for (int value : values)
            if (value < 0 || value >= count)
                throw new AnalysisException("register_out_of_bounds:" + value);
        return values;
    }
    private static int[] none() { return new int[0]; }
    private static int[] array(Set<Integer> values) {
        int[] result = new int[values.size()];
        int index = 0;
        for (Integer value : values) result[index++] = value;
        return result;
    }
    private static int u(short value) { return value & 0xffff; }
}

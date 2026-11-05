package ds.dsh.DeepSeek;

import java.util.Arrays;

public final class RoiDexRegisterFlowAnalyzerTest {
    public static void main(String[] args) throws Exception {
        testNoOperandInstructions();
        testSequentialDefUse();
        testUseBeforeDefinition();
        testWideRegisters();
        testWideConversionsAndTwoAddress();
        testBranchBlocksRemainIndependent();
        testCrossBlockDefinitionsAreNotPropagated();
        testUnsupportedOpcodeFailsClosed();
        testRegisterBounds();
        System.out.println("RoiDexRegisterFlowAnalyzer tests passed");
    }

    private static void testNoOperandInstructions() throws Exception {
        RoiDexRegisterFlowAnalyzer.BasicBlockFlow block =
                RoiDexRegisterFlowAnalyzer.analyze(new short[]{0, (short) 0x000e}, 1).blocks[0];
        check(block.useBeforeDefinition.length == 0 && block.definitions.length == 0,
                "nop and return-void have no register operands");
    }

    private static void testSequentialDefUse() throws Exception {
        // const/4 v0, #1; add-int v1, v0, v0; return v1
        short[] code = {(short) 0x0012, (short) 0x0190, (short) 0x0000,
                (short) 0x0000, (short) 0x010f};
        RoiDexRegisterFlowAnalyzer.BasicBlockFlow block =
                RoiDexRegisterFlowAnalyzer.analyze(code, 3).blocks[0];
        check(Arrays.equals(block.useBeforeDefinition, new int[]{1}),
                "return reads v1 before any definition");
        check(Arrays.equals(block.definitions, new int[]{0, 1}),
                "block defines v0 and v1");
        check(block.instructions.length == 3, "all instructions decoded");
    }

    private static void testUseBeforeDefinition() throws Exception {
        // move v1, v0; const/4 v0, #0; return v1
        short[] code = {(short) 0x0101, (short) 0x0012, (short) 0x010f};
        RoiDexRegisterFlowAnalyzer.BasicBlockFlow block =
                RoiDexRegisterFlowAnalyzer.analyze(code, 3).blocks[0];
        check(Arrays.equals(block.useBeforeDefinition, new int[]{0}),
                "move source is live-in before v0 is overwritten");
        check(Arrays.equals(block.definitions, new int[]{1, 0}),
                "move and const definitions retained");
    }

    private static void testWideRegisters() throws Exception {
        // const-wide/16 v0, #0; return-wide v0
        short[] code = {(short) 0x0016, (short) 0x0000, (short) 0x0010};
        RoiDexRegisterFlowAnalyzer.BasicBlockFlow block =
                RoiDexRegisterFlowAnalyzer.analyze(code, 2).blocks[0];
        check(Arrays.equals(block.definitions, new int[]{0, 1}),
                "wide definition covers both register words");
        check(block.useBeforeDefinition.length == 0,
                "wide return is defined within the block");
    }

    private static void testWideConversionsAndTwoAddress() throws Exception {
        // int-to-long v0, v2; return-wide v0
        short[] conversion = {(short) 0x2081, (short) 0x0010};
        RoiDexRegisterFlowAnalyzer.BasicBlockFlow converted =
                RoiDexRegisterFlowAnalyzer.analyze(conversion, 4).blocks[0];
        check(Arrays.equals(converted.definitions, new int[]{0, 1}),
                "int-to-long defines a wide destination");
        check(Arrays.equals(converted.useBeforeDefinition, new int[]{2}),
                "int-to-long reads a single-word source");

        // add-long/2addr v0, v2; return-wide v0
        short[] arithmetic = {(short) 0x20ba, (short) 0x0010};
        RoiDexRegisterFlowAnalyzer.BasicBlockFlow added =
                RoiDexRegisterFlowAnalyzer.analyze(arithmetic, 4).blocks[0];
        check(Arrays.equals(added.definitions, new int[]{0, 1}),
                "long/2addr defines both destination words");
        check(Arrays.equals(added.useBeforeDefinition, new int[]{0, 1, 2, 3}),
                "long/2addr reads both old wide operands");
    }

    private static void testBranchBlocksRemainIndependent() throws Exception {
        // if-eqz v0, +3; const/4 v1, #1; return v1; return-void
        short[] code = {(short) 0x0038, (short) 0x0003,
                (short) 0x0112, (short) 0x010f, (short) 0x000e};
        RoiDexRegisterFlowAnalyzer.Result result =
                RoiDexRegisterFlowAnalyzer.analyze(code, 2);
        check(result.blocks.length >= 2, "conditional branch creates multiple blocks");
        check(result.edges.length >= 2, "conditional branch creates CFG edges");
        boolean branchReadsV0 = false;
        for (RoiDexRegisterFlowAnalyzer.BasicBlockFlow block : result.blocks) {
            if (block.startOffset == 0)
                branchReadsV0 = Arrays.equals(block.useBeforeDefinition, new int[]{0});
        }
        check(branchReadsV0, "branch condition is a block live-in use");
    }

    private static void testCrossBlockDefinitionsAreNotPropagated() throws Exception {
        // const/4 v0; goto target; return-void (unreachable fallthrough); return v0
        short[] code = {(short) 0x0012, (short) 0x0228,
                (short) 0x000e, (short) 0x000f};
        RoiDexRegisterFlowAnalyzer.Result result =
                RoiDexRegisterFlowAnalyzer.analyze(code, 1);
        boolean sourceDefinesV0 = false;
        boolean targetStillRequiresV0 = false;
        for (RoiDexRegisterFlowAnalyzer.BasicBlockFlow block : result.blocks) {
            if (block.startOffset == 0)
                sourceDefinesV0 = Arrays.equals(block.definitions, new int[]{0});
            if (block.startOffset == 3)
                targetStillRequiresV0 = Arrays.equals(block.useBeforeDefinition, new int[]{0});
        }
        check(sourceDefinesV0, "source block records its local definition");
        check(targetStillRequiresV0,
                "target block does not inherit source definitions in phase one");
    }

    private static void testUnsupportedOpcodeFailsClosed() throws Exception {
        // invoke-polymorphic uses a distinct 45cc format not yet modelled.
        expectFailure(new short[]{(short) 0x00fa, 0, 0, 0}, 2,
                "unsupported_register_semantics_opcode");
    }

    private static void testRegisterBounds() throws Exception {
        expectFailure(new short[]{(short) 0x0312}, 3, "register_out_of_bounds");
    }

    private static void expectFailure(short[] code, int registers, String fragment)
            throws Exception {
        try {
            RoiDexRegisterFlowAnalyzer.analyze(code, registers);
            throw new AssertionError("expected failure: " + fragment);
        } catch (RoiDexRegisterFlowAnalyzer.AnalysisException expected) {
            check(expected.getMessage().contains(fragment),
                    "failure contains " + fragment + ": " + expected.getMessage());
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

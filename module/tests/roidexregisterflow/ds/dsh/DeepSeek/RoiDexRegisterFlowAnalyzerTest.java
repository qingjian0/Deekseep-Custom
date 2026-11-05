package ds.dsh.DeepSeek;

import java.util.Arrays;

public final class RoiDexRegisterFlowAnalyzerTest {
    public static void main(String[] args) throws Exception {
        testNoOperandInstructions();
        testSequentialDefUse();
        testUseBeforeDefinition();
        testWideRegisters();
        testWideConversionsAndTwoAddress();
        testLiteralArithmeticUsesOnlySourceRegister();
        testBranchBlocksRemainIndependent();
        testCrossBlockDefinitionsAreNotPropagated();
        testReachingDefinitionsAcrossBlocks();
        testReachingDefinitionsKillOlderSites();
        testReachingDefinitionsMergeAtDiamond();
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
        check(block.useBeforeDefinition.length == 0,
                "v0 and v1 are both defined before use");
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
        short[] arithmetic = {(short) 0x20bb, (short) 0x0010};
        RoiDexRegisterFlowAnalyzer.BasicBlockFlow added =
                RoiDexRegisterFlowAnalyzer.analyze(arithmetic, 4).blocks[0];
        check(Arrays.equals(added.definitions, new int[]{0, 1}),
                "long/2addr defines both destination words");
        check(Arrays.equals(added.useBeforeDefinition, new int[]{0, 1, 2, 3}),
                "long/2addr reads both old wide operands");

        RoiDexRegisterFlowAnalyzer.BasicBlockFlow intShift =
                RoiDexRegisterFlowAnalyzer.analyze(new short[]{(short) 0x20ba, (short) 0x000f}, 4).blocks[0];
        check(Arrays.equals(intShift.definitions, new int[]{0}),
                "ushr-int/2addr defines one register word");
        check(Arrays.equals(intShift.useBeforeDefinition, new int[]{0, 2}),
                "ushr-int/2addr reads narrow operands");

        RoiDexRegisterFlowAnalyzer.BasicBlockFlow longShift =
                RoiDexRegisterFlowAnalyzer.analyze(new short[]{(short) 0x20c5, (short) 0x0010}, 4).blocks[0];
        check(Arrays.equals(longShift.definitions, new int[]{0, 1}),
                "ushr-long/2addr defines wide destination");
        check(Arrays.equals(longShift.useBeforeDefinition, new int[]{0, 1, 2}),
                "long shift count remains a narrow source");
    }

    private static void testLiteralArithmeticUsesOnlySourceRegister() throws Exception {
        RoiDexRegisterFlowAnalyzer.BasicBlockFlow lit16 =
                RoiDexRegisterFlowAnalyzer.analyze(new short[]{(short) 0x10d0, 1, (short) 0x000f}, 2).blocks[0];
        check(Arrays.equals(lit16.definitions, new int[]{0}),
                "lit16 defines destination register");
        check(Arrays.equals(lit16.useBeforeDefinition, new int[]{1}),
                "lit16 reads source, not old destination");

        RoiDexRegisterFlowAnalyzer.BasicBlockFlow lit8 =
                RoiDexRegisterFlowAnalyzer.analyze(new short[]{(short) 0x00d8, (short) 0x0101, (short) 0x000f}, 2).blocks[0];
        check(Arrays.equals(lit8.definitions, new int[]{0}),
                "lit8 defines destination register");
        check(Arrays.equals(lit8.useBeforeDefinition, new int[]{1}),
                "lit8 reads source, not destination");
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

    private static void testReachingDefinitionsAcrossBlocks() throws Exception {
        short[] code = {(short) 0x0012, (short) 0x0228,
                (short) 0x000e, (short) 0x000f};
        RoiDexRegisterFlowAnalyzer.Result flow =
                RoiDexRegisterFlowAnalyzer.analyze(code, 1);
        RoiDexReachingDefinitions.Result result =
                RoiDexReachingDefinitions.analyze(flow);
        RoiDexReachingDefinitions.BlockResult target = blockAt(result, 3);
        check(target != null && hasDefinition(target.reachingIn, 0, 0),
                "definition reaches target block through goto edge");
        check(result.normalControlFlowOnly && !result.exceptionEdgesIncluded
                        && !result.completeForExceptionFlow,
                "result explicitly excludes exception-handler flow");
    }

    private static void testReachingDefinitionsKillOlderSites() throws Exception {
        short[] code = {(short) 0x0012, (short) 0x0228,
                (short) 0x000e, (short) 0x0012, (short) 0x000f};
        RoiDexReachingDefinitions.Result result = RoiDexReachingDefinitions.analyze(
                RoiDexRegisterFlowAnalyzer.analyze(code, 1));
        RoiDexReachingDefinitions.BlockResult target = blockAt(result, 3);
        check(target != null && hasDefinition(target.reachingIn, 0, 0),
                "incoming definition reaches before local overwrite");
        check(target != null && hasDefinition(target.reachingOut, 0, 3),
                "local definition replaces older definition in OUT");
        check(target != null && !hasDefinition(target.reachingOut, 0, 0),
                "older definition is killed after local overwrite");
    }

    private static void testReachingDefinitionsMergeAtDiamond() throws Exception {
        short[] code = {(short) 0x0138, (short) 0x0004,
                (short) 0x0012, (short) 0x0328,
                (short) 0x0012, (short) 0x0128, (short) 0x000f};
        RoiDexReachingDefinitions.Result result = RoiDexReachingDefinitions.analyze(
                RoiDexRegisterFlowAnalyzer.analyze(code, 2));
        RoiDexReachingDefinitions.BlockResult join = blockAt(result, 6);
        check(join != null && hasDefinition(join.reachingIn, 0, 2),
                "left branch definition reaches join");
        check(join != null && hasDefinition(join.reachingIn, 0, 4),
                "right branch definition reaches join");
    }

    private static RoiDexReachingDefinitions.BlockResult blockAt(
            RoiDexReachingDefinitions.Result result, int offset) {
        for (RoiDexReachingDefinitions.BlockResult block : result.blocks)
            if (block.startOffset == offset) return block;
        return null;
    }

    private static boolean hasDefinition(RoiDexReachingDefinitions.Definition[] values,
            int register, int offset) {
        for (RoiDexReachingDefinitions.Definition value : values)
            if (value.register == register && value.instructionOffset == offset) return true;
        return false;
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

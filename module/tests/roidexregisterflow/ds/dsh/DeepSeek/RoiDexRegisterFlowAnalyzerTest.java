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
        testReachingDefinitionsConvergeAcrossLoop();
        testUseDefChainAcrossBlocks();
        testUseDefChainMergesAtDiamond();
        testUseDefChainTracksLoopDefinitions();
        testUseDefChainPreservesEntryUnknown();
        testUseDefChainTracksWideRegisterWords();
        testUnreachableDefinitionsDoNotReachLiveCode();
        testUseDefDetectsPartiallyUndefinedDiamondPath();
        testUseDefDetectsInitialEntryPathAcrossBackEdge();
        testMethodEntryLayoutMapsReceiverAndWideParameters();
        testMethodEntryLayoutRejectsInsSizeMismatch();
        testUseDefClassifiesReceiverEntryUse();
        testUseDefClassifiesWideStaticParameter();
        testUseDefRejectsMismatchedRegisterCount();
        testUnsupportedOpcodeFailsClosed();
        testRegisterBounds();
        testTypedAndCatchAllExceptionEdges();
        testExceptionHandlerDoesNotClaimEntryParameterProvenance();
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
                (short) 0x010f};
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

    private static void testReachingDefinitionsConvergeAcrossLoop() throws Exception {
        // const v0; if-eqz v1, exit; const v0; goto loop-condition; return v0
        short[] code = {(short) 0x0012, (short) 0x0138, (short) 0x0004,
                (short) 0x0012, (short) 0xfd28, (short) 0x000f};
        RoiDexReachingDefinitions.Result result = RoiDexReachingDefinitions.analyze(
                RoiDexRegisterFlowAnalyzer.analyze(code, 2));
        RoiDexReachingDefinitions.BlockResult condition = blockAt(result, 1);
        check(condition != null && hasDefinition(condition.reachingIn, 0, 0),
                "initial definition reaches loop condition");
        check(condition != null && hasDefinition(condition.reachingIn, 0, 3),
                "back-edge definition reaches loop condition after fixed point");
        check(result.worklistSteps >= result.blocks.length,
                "worklist processes the loop graph to convergence");
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


    private static void testUseDefChainAcrossBlocks() throws Exception {
        short[] code = {(short) 0x0012, (short) 0x0228,
                (short) 0x000e, (short) 0x000f};
        RoiDexUseDefChains.Result result = chains(code, 1);
        RoiDexUseDefChains.UseSite use = useAt(result, 3, 0);
        check(use != null && use.reachingDefinitions.length == 1
                        && use.reachingDefinitions[0].instructionOffset == 0,
                "target use links to the definition reaching through goto");
        check(!use.mayBeEntryOrUnknownValue, "resolved use is not marked entry-unknown");
    }

    private static void testUseDefChainMergesAtDiamond() throws Exception {
        short[] code = {(short) 0x0138, (short) 0x0004,
                (short) 0x0012, (short) 0x0328,
                (short) 0x0012, (short) 0x0128, (short) 0x000f};
        RoiDexUseDefChains.UseSite use = useAt(chains(code, 2), 6, 0);
        check(use != null && use.reachingDefinitions.length == 2,
                "join use retains both branch-specific definitions");
        check(use != null && use.reachingDefinitions[0].instructionOffset == 2
                        && use.reachingDefinitions[1].instructionOffset == 4,
                "diamond use links to exact left and right definition sites");
    }

    private static void testUseDefChainTracksLoopDefinitions() throws Exception {
        short[] code = {(short) 0x0012, (short) 0x0138, (short) 0x0004,
                (short) 0x0012, (short) 0xfd28, (short) 0x000f};
        RoiDexUseDefChains.UseSite use = useAt(chains(code, 2), 5, 0);
        check(use != null && use.reachingDefinitions.length == 2,
                "loop-exit use retains entry and back-edge definitions");
    }

    private static void testUseDefChainPreservesEntryUnknown() throws Exception {
        RoiDexUseDefChains.UseSite use = useAt(chains(new short[]{(short) 0x000f}, 1), 0, 0);
        check(use != null && use.reachingDefinitions.length == 0,
                "method-entry register has no fabricated definition site");
        check(use != null && use.mayBeEntryOrUnknownValue,
                "missing reaching definition is classified as entry or unknown, not uninitialized");
    }

    private static void testUseDefChainTracksWideRegisterWords() throws Exception {
        short[] code = {(short) 0x0016, (short) 0x0000, (short) 0x0010};
        RoiDexUseDefChains.Result result = chains(code, 2);
        RoiDexUseDefChains.UseSite low = useAt(result, 2, 0);
        RoiDexUseDefChains.UseSite high = useAt(result, 2, 1);
        check(low != null && high != null
                        && low.reachingDefinitions.length == 1
                        && high.reachingDefinitions.length == 1,
                "wide read creates separately linked register-word uses");
        check(low.reachingDefinitions[0].register == 0
                        && high.reachingDefinitions[0].register == 1,
                "wide read links each word to its matching definition");
    }

    private static void testUnreachableDefinitionsDoNotReachLiveCode() throws Exception {
        // entry goto live return; unreachable goto; dead const/4 v0 + dead goto; return v0
        // All branch displacements are relative to their own instruction addresses.
        short[] code = {(short) 0x0628, (short) 0x0528,
                (short) 0x0012, (short) 0x0328,
                (short) 0x0000, (short) 0x0000, (short) 0x000f};
        RoiDexRegisterFlowAnalyzer.Result flow =
                RoiDexRegisterFlowAnalyzer.analyze(code, 1);
        RoiDexReachingDefinitions.Result reaching =
                RoiDexReachingDefinitions.analyze(flow);
        RoiDexReachingDefinitions.BlockResult dead = blockAt(reaching, 2);
        RoiDexReachingDefinitions.BlockResult live = blockAt(reaching, 6);
        check(dead != null && !dead.reachableFromEntry,
                "dead block is explicitly marked unreachable from method entry");
        check(live != null && live.reachableFromEntry && live.reachingIn.length == 0,
                "definition from unreachable predecessor cannot contaminate live code");
        RoiDexUseDefChains.UseSite use = useAt(chains(code, 1), 6, 0);
        check(use != null && use.reachingDefinitions.length == 0
                        && use.mayBeEntryOrUnknownValue && use.reachableFromEntry,
                "reachable use is not linked to a dead-block definition");
    }


    private static void testUseDefDetectsPartiallyUndefinedDiamondPath() throws Exception {
        // if-eqz v1 -> right; left defines v0; both paths join at return v0.
        short[] code = {(short) 0x0138, (short) 5,
                (short) 0x0012, (short) 0x0429, (short) 4,
                (short) 0x0229, (short) 2, (short) 0x000f};
        RoiDexUseDefChains.UseSite use = useAt(chains(code, 2), 7, 0);
        check(use != null && use.reachingDefinitions.length == 1,
                "partial diamond has one reaching definition from the defining branch");
        check(use != null && use.mayBeEntryOrUnknownValue,
                "partial diamond preserves the path with no definition");
    }

    private static void testUseDefDetectsInitialEntryPathAcrossBackEdge() throws Exception {
        // Entry reads v0; loop body later defines v0 and jumps back to entry.
        short[] code = {(short) 0x0038, (short) 6,
                (short) 0x0012, (short) 0x002a,
                (short) 0xfffd, (short) 0xffff, (short) 0x000f};
        RoiDexUseDefChains.UseSite use = useAt(chains(code, 2), 0, 0);
        check(use != null && use.reachingDefinitions.length == 1,
                "back edge contributes a concrete reaching definition at entry");
        check(use != null && use.mayBeEntryOrUnknownValue,
                "initial method-entry path remains unknown despite a back-edge definition");
    }


    private static void testMethodEntryLayoutMapsReceiverAndWideParameters() throws Exception {
        RoiDexMethodEntryLayout.Result layout = RoiDexMethodEntryLayout.analyze(
                8, 5, 0, "(JLjava/lang/String;[I)D");
        check(layout.firstIncomingRegister == 3 && layout.receiver.firstRegister == 3,
                "instance receiver occupies the first incoming register word");
        check(layout.parameters.length == 3
                        && layout.parameters[0].firstRegister == 4
                        && layout.parameters[0].registerWidth == 2,
                "wide long parameter consumes two consecutive register words");
        check(layout.parameters[1].firstRegister == 6
                        && layout.parameters[2].firstRegister == 7
                        && layout.parameters[2].registerWidth == 1,
                "reference and array parameters consume one word each");
        check(layout.atRegister(5) == layout.parameters[0],
                "both words of a wide parameter map to the same parameter");
    }

    private static void testMethodEntryLayoutRejectsInsSizeMismatch() throws Exception {
        try {
            RoiDexMethodEntryLayout.analyze(4, 1, 0x0008, "(J)V");
            throw new AssertionError("expected ins_size/prototype mismatch");
        } catch (RoiDexMethodEntryLayout.AnalysisException expected) {
            check(expected.getMessage().contains("ins_size_prototype_mismatch"),
                    "method entry layout rejects inconsistent incoming word count");
        }
    }

    private static void testUseDefClassifiesReceiverEntryUse() throws Exception {
        RoiDexRegisterFlowAnalyzer.Result flow =
                RoiDexRegisterFlowAnalyzer.analyze(new short[]{(short) 0x0211}, 3);
        RoiDexReachingDefinitions.Result reaching =
                RoiDexReachingDefinitions.analyze(flow);
        RoiDexMethodEntryLayout.Result layout =
                RoiDexMethodEntryLayout.analyze(3, 1, 0, "()Ljava/lang/Object;");
        RoiDexUseDefChains.UseSite use =
                useAt(RoiDexUseDefChains.analyze(flow, reaching, layout), 0, 2);
        check(use != null && use.mayBeEntryOrUnknownValue
                        && use.entryValueKind == RoiDexUseDefChains.EntryValueKind.RECEIVER,
                "receiver use is explicitly identified as the method entry receiver");
    }


    private static void testUseDefClassifiesWideStaticParameter() throws Exception {
        RoiDexRegisterFlowAnalyzer.Result flow =
                RoiDexRegisterFlowAnalyzer.analyze(new short[]{(short) 0x0110}, 3);
        RoiDexReachingDefinitions.Result reaching =
                RoiDexReachingDefinitions.analyze(flow);
        RoiDexMethodEntryLayout.Result layout =
                RoiDexMethodEntryLayout.analyze(3, 2, 0x0008, "(J)J");
        RoiDexUseDefChains.Result chains =
                RoiDexUseDefChains.analyze(flow, reaching, layout);
        RoiDexUseDefChains.UseSite low = useAt(chains, 0, 1);
        RoiDexUseDefChains.UseSite high = useAt(chains, 0, 2);
        check(layout.receiver == null && low != null && high != null
                        && low.entryValueKind == RoiDexUseDefChains.EntryValueKind.PARAMETER
                        && high.entryValueKind == RoiDexUseDefChains.EntryValueKind.PARAMETER
                        && low.entryParameterIndex == 0 && high.entryParameterIndex == 0,
                "both words of a wide static parameter map to parameter zero");
    }


    private static void testUseDefRejectsMismatchedRegisterCount() throws Exception {
        RoiDexRegisterFlowAnalyzer.Result flow =
                RoiDexRegisterFlowAnalyzer.analyze(new short[]{(short) 0x0211}, 3);
        RoiDexReachingDefinitions.Result reaching =
                RoiDexReachingDefinitions.analyze(flow);
        RoiDexMethodEntryLayout.Result wrongLayout =
                RoiDexMethodEntryLayout.analyze(4, 1, 0, "()Ljava/lang/Object;");
        try {
            RoiDexUseDefChains.analyze(flow, reaching, wrongLayout);
            throw new AssertionError("expected method register-count mismatch");
        } catch (RoiDexUseDefChains.AnalysisException expected) {
            check(expected.getMessage().contains("entry_layout_register_count_mismatch"),
                    "entry mapping requires the exact CodeItem register count");
        }
    }

    private static RoiDexUseDefChains.Result chains(short[] code, int registers)
            throws Exception {
        RoiDexRegisterFlowAnalyzer.Result flow =
                RoiDexRegisterFlowAnalyzer.analyze(code, registers);
        RoiDexReachingDefinitions.Result reaching =
                RoiDexReachingDefinitions.analyze(flow);
        return RoiDexUseDefChains.analyze(flow, reaching);
    }

    private static RoiDexUseDefChains.UseSite useAt(
            RoiDexUseDefChains.Result result, int offset, int register) {
        for (RoiDexUseDefChains.UseSite use : result.uses)
            if (use.instructionOffset == offset && use.register == register) return use;
        return null;
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

    private static void testTypedAndCatchAllExceptionEdges() throws Exception {
        byte[] bytes = new byte[64];
        put16(bytes, 16, 1); // registers_size
        put16(bytes, 18, 0); // ins_size
        put16(bytes, 20, 0); // outs_size
        put16(bytes, 22, 1); // tries_size
        put32(bytes, 24, 0); // debug_info_off
        put32(bytes, 28, 2); // insns_size
        put16(bytes, 32, 0x0000); // nop, protected
        put16(bytes, 34, 0x000e); // return-void
        put32(bytes, 36, 0); // try start_addr
        put16(bytes, 40, 1); // try insn_count
        put16(bytes, 42, 1); // handler_off relative to encoded handler list
        bytes[44] = 1; // encoded_catch_handler_list size
        bytes[45] = (byte) 0x7f; // sleb128 -1: one typed catch plus catch-all
        bytes[46] = 0; // type_idx
        bytes[47] = 0; // typed handler addr
        bytes[48] = 1; // catch-all addr

        RoiDexCodeReader.CodeItem code =
                RoiDexCodeReader.readCodeItem(bytes, 0, bytes.length, 16, 1);
        check(code.catchHandlers.length == 1
                        && code.catchHandlers[0].typedCatches.length == 1
                        && code.catchHandlers[0].catchAllAddress == 1,
                "typed and catch-all clauses are parsed from signed handler size");
        RoiDexRegisterFlowAnalyzer.Result flow =
                RoiDexRegisterFlowAnalyzer.analyze(code);
        check(flow.exceptionEdgesIncluded && !flow.completeForExceptionFlow
                        && flow.exceptionEdges.length == 2,
                "conservative exception CFG reports typed and catch-all edges without claiming completeness");
        boolean typed = false, catchAll = false;
        for (RoiDexExceptionControlFlow.ExceptionEdge edge : flow.exceptionEdges) {
            typed |= !edge.catchAll && edge.catchTypeIndex == 0 && edge.handlerAddress == 0;
            catchAll |= edge.catchAll && edge.handlerAddress == 1;
        }
        check(typed && catchAll, "exception edges preserve handler type and target identity");
        RoiDexReachingDefinitions.Result reaching =
                RoiDexReachingDefinitions.analyze(flow);
        check(reaching.exceptionEdgesIncluded && !reaching.completeForExceptionFlow,
                "reaching definitions explicitly records conservative exception edges");
    }

    private static void testExceptionHandlerDoesNotClaimEntryParameterProvenance()
            throws Exception {
        byte[] bytes = new byte[80];
        put16(bytes, 16, 2); // registers_size
        put16(bytes, 18, 1); // ins_size: v1 is the sole static parameter
        put16(bytes, 20, 1); // outs_size: invoke uses one word
        put16(bytes, 22, 1); // tries_size
        put32(bytes, 24, 0); // debug_info_off
        put32(bytes, 28, 7); // insns_size
        put16(bytes, 32, 0x1012); // const/4 v1, #0
        put16(bytes, 34, 0x0171); // invoke-static {v0}, method@0
        put16(bytes, 36, 0);
        put16(bytes, 38, 0);
        put16(bytes, 40, 0x000e); // normal return
        put16(bytes, 42, 0x0101); // handler: move v0, v1
        put16(bytes, 44, 0x000f); // return v0
        put32(bytes, 48, 0); // try start_addr
        put16(bytes, 52, 4); // protect const + invoke
        put16(bytes, 54, 1); // handler_off relative to encoded handler list
        bytes[56] = 1; // encoded_catch_handler_list size
        bytes[57] = (byte) 0x7f; // one typed catch plus catch-all
        bytes[58] = 0; // type_idx
        bytes[59] = 5; // typed handler addr
        bytes[60] = 5; // catch-all addr

        RoiDexCodeReader.CodeItem code =
                RoiDexCodeReader.readCodeItem(bytes, 0, bytes.length, 16, 1);
        RoiDexRegisterFlowAnalyzer.Result flow = RoiDexRegisterFlowAnalyzer.analyze(code);
        RoiDexReachingDefinitions.Result reaching = RoiDexReachingDefinitions.analyze(flow);
        RoiDexMethodEntryLayout.Result layout =
                RoiDexMethodEntryLayout.analyze(2, 1, 0x0008, "(I)V");
        RoiDexUseDefChains.Result chains = RoiDexUseDefChains.analyze(flow, reaching, layout);
        RoiDexUseDefChains.UseSite handlerUse = useAt(chains, 5, 1);
        check(handlerUse != null && handlerUse.reachableFromEntry,
                "exception handler use is reachable through the declared handler edge");
        check(handlerUse.mayBeEntryOrUnknownValue && handlerUse.mayBeExceptionFlowUnknown,
                "handler use remains explicitly unknown despite a reaching definition candidate");
        check(handlerUse.entryValueKind == RoiDexUseDefChains.EntryValueKind.UNDEFINED_OR_UNKNOWN
                        && handlerUse.entryParameterIndex == -1 && handlerUse.entryDescriptor == null,
                "exception-flow ambiguity must not be mislabeled as a method-entry parameter");
    }

    private static void put16(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) value;
        bytes[offset + 1] = (byte) (value >>> 8);
    }

    private static void put32(byte[] bytes, int offset, int value) {
        for (int i = 0; i < 4; i++) bytes[offset + i] = (byte) (value >>> (8 * i));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

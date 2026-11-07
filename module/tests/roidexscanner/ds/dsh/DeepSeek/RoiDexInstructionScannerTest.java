package ds.dsh.DeepSeek;

public final class RoiDexInstructionScannerTest {
    public static void main(String[] args)throws Exception {
        widths(); payloads(); branches(); cfg(); cfgPayloads(); payloadCfg();
        unreachableNopBeforePayload(); malformed();
        System.out.println("RoiDexInstructionScannerTest: PASS");
    }
    static void widths()throws Exception {
        short[] code={(short)0x0000,(short)0x0013,(short)0x0001,(short)0x0018,
                1,2,3,4,(short)0x00fa,0,0,0,(short)0x00fc,0,0,
                (short)0x00fe,0,(short)0x00ff,0,(short)0x000e};
        RoiDexInstructionScanner.ScanResult r=RoiDexInstructionScanner.scan(code);
        eq(8,r.instructions.length);
        eq(20,r.codeUnits);
        eq(4,r.instructions[3].width);
        eq(3,r.instructions[4].width);
        eq(2,r.instructions[5].width);
        eq(2,r.instructions[6].width);
    }
    static void payloads()throws Exception {
        short[] packed={(short)0x002b,4,0,0,(short)0x0100,1,0,0,10,0,(short)0x000e};
        RoiDexInstructionScanner.ScanResult p=RoiDexInstructionScanner.scan(packed);
        yes(p.isPayloadStart(4));yes(p.isInstructionBoundary(10));
        short[] array={(short)0x0026,4,0,0,(short)0x0300,1,3,0,(short)0x2211,0x0033,(short)0x000e};
        RoiDexInstructionScanner.ScanResult a=RoiDexInstructionScanner.scan(array);
        yes(a.isPayloadStart(4));yes(a.isInstructionBoundary(10));
    }
    static void branches()throws Exception {
        RoiDexInstructionScanner.scan(new short[]{(short)0x0028,(short)0x000e});
        bad(new short[]{(short)0x0029,1,(short)0x000e}); // target lands in goto/16 operand
        bad(new short[]{(short)0x002b,4,0,0,(short)0x0100,1,0,0,4,0,(short)0x000e});
    }
    static void cfg()throws Exception {
        short[] code={(short)0x0038,3,(short)0x000e,(short)0x000e};
        RoiDexInstructionScanner.ControlFlowGraph g=RoiDexInstructionScanner.buildControlFlowGraph(code);
        eq(3,g.blocks.length);eq(2,g.edges.length);
        yes("CONDITIONAL_TRUE".equals(g.edges[0].kind));
        yes("CONDITIONAL_FALSE".equals(g.edges[1].kind));
    }
    static void cfgPayloads()throws Exception {
        short[] valid={(short)0x002b,6,0,(short)0x0029,9,(short)0x0728,
                (short)0x0100,1,0,0,12,0,(short)0x000e};
        RoiDexInstructionScanner.ControlFlowGraph g=RoiDexInstructionScanner.buildControlFlowGraph(valid);
        yes(g.blocks.length>=3);
        boolean switchDefault=false;
        for(RoiDexInstructionScanner.Edge e:g.edges)
            if("SWITCH_DEFAULT".equals(e.kind))switchDefault=true;
        yes(switchDefault);

        short[] fallsIntoPayload={(short)0x002b,4,0,0,(short)0x0100,1,0,0,10,0,(short)0x000e};
        try {
            RoiDexInstructionScanner.buildControlFlowGraph(fallsIntoPayload);
        } catch(RoiDexInstructionScanner.ScanException expected) {
            return;
        }
        throw new AssertionError("expected CFG to reject fallthrough into payload");
    }
    static void payloadCfg()throws Exception {
        // A goto skips an aligned payload; the payload must not become a basic block.
        short[] afterGoto={(short)0x0029,6,(short)0x0100,0,0,0,(short)0x000e};
        RoiDexInstructionScanner.ControlFlowGraph g=
                RoiDexInstructionScanner.buildControlFlowGraph(afterGoto);
        eq(2,g.blocks.length);eq(1,g.edges.length);
        eq(0,g.blocks[0].startOffset);eq(2,g.blocks[0].endOffset);eq(6,g.blocks[1].startOffset);
        eq(1,g.edges[0].toBlock);

        // A switch payload is data, while the default and case paths remain executable.
        short[] switchCode={(short)0x002b,6,0,(short)0x002a,9,0,
                (short)0x0100,1,0,0,12,0,(short)0x000e};
        RoiDexInstructionScanner.ControlFlowGraph sw=
                RoiDexInstructionScanner.buildControlFlowGraph(switchCode);
        eq(3,sw.blocks.length);
        yes(sw.edges.length>=3);
    }
    static void unreachableNopBeforePayload()throws Exception {
        // goto reaches the switch at code unit 8. The NOP at 1 only aligns the
        // packed-switch payload at 2; the payload is referenced by that switch.
        // Neither the NOP nor payload is reachable by fallthrough from the goto.
        short[] code={(short)0x0828,(short)0x0000,(short)0x0100,1,0,0,3,0,
                (short)0x002b,(short)0xfffa,(short)0xffff,(short)0x000e};
        RoiDexInstructionScanner.ControlFlowGraph g=
                RoiDexInstructionScanner.buildControlFlowGraph(code);
        eq(3,g.blocks.length);
        eq(0,g.blocks[0].startOffset);eq(1,g.blocks[0].endOffset);
        eq(8,g.blocks[1].startOffset);eq(11,g.blocks[2].startOffset);
        yes("GOTO".equals(g.edges[0].kind));

        // If an exception handler or explicit control-flow entry targets the
        // alignment NOP, it is reachable and must still fail closed.
        try {
            RoiDexInstructionScanner.buildControlFlowGraph(code,new int[]{1});
        } catch(RoiDexInstructionScanner.ScanException expected) {
            return;
        }
        throw new AssertionError("expected reachable alignment NOP to be rejected");
    }
    static void malformed()throws Exception {
        bad(new short[]{(short)0x0073});
        bad(new short[]{(short)0x0014,0});
        bad(new short[]{(short)0x0000,(short)0x0100});
        bad(new short[]{(short)0x0026,4,0,0,(short)0x0300,0,0,0});
    }
    static void bad(short[] c)throws Exception {
        try { RoiDexInstructionScanner.scan(c); }
        catch(RoiDexInstructionScanner.ScanException expected){return;}
        throw new AssertionError("expected ScanException");
    }
    static void eq(int e,int a){if(e!=a)throw new AssertionError("expected "+e+" got "+a);}
    static void yes(boolean b){if(!b)throw new AssertionError("expected true");}
}

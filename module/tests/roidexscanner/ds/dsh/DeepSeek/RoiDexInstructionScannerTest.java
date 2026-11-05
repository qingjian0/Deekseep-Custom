package ds.dsh.DeepSeek;

public final class RoiDexInstructionScannerTest {
    public static void main(String[] args)throws Exception {
        widths(); payloads(); branches(); cfg(); malformed();
        System.out.println("RoiDexInstructionScannerTest: PASS");
    }
    static void widths()throws Exception {
        short[] code={(short)0x0000,(short)0x0013,(short)0x0001,(short)0x0018,
                1,2,3,4,(short)0x00fa,0,0,0,(short)0x00fc,0,0,
                (short)0x00fe,0,(short)0x00ff,0,(short)0x000e};
        RoiDexInstructionScanner.ScanResult r=RoiDexInstructionScanner.scan(code);
        eq(8,r.instructions.length);
        eq(20,r.codeUnits);
        eq(5,r.instructions[3].width);
        eq(4,r.instructions[4].width);
    }
    static void payloads()throws Exception {
        short[] packed={(short)0x002b,4,0,(short)0x0100,1,0,0,0,2,0,(short)0x000e};
        RoiDexInstructionScanner.ScanResult p=RoiDexInstructionScanner.scan(packed);
        yes(p.isPayloadStart(3));yes(p.isInstructionBoundary(10));
        short[] array={(short)0x0026,3,0,(short)0x0300,1,0,3,0x2211,(short)0x0033,0};
        RoiDexInstructionScanner.ScanResult a=RoiDexInstructionScanner.scan(array);
        yes(a.isPayloadStart(3));yes(a.isInstructionBoundary(8));
    }
    static void branches()throws Exception {
        short[] good={(short)0x0028, (short)0x000e};
        RoiDexInstructionScanner.scan(good);
        bad(new short[]{(short)0x0028,(short)0x0028}); // target lands in operand of goto/8
        bad(new short[]{(short)0x002b,3,0,(short)0x0100,1,0,0,0,1,0,(short)0x000e}); // switch target is payload
    }
    static void cfg()throws Exception {
        short[] code={(short)0x0038,3,(short)0x000e,(short)0x000e};
        RoiDexInstructionScanner.ControlFlowGraph g=RoiDexInstructionScanner.buildControlFlowGraph(code);
        eq(3,g.blocks.length);eq(2,g.edges.length);
        yes("CONDITIONAL_TRUE".equals(g.edges[0].kind));
        yes("CONDITIONAL_FALSE".equals(g.edges[1].kind));
    }
    static void malformed()throws Exception {
        bad(new short[]{(short)0x0073}); // unused opcode
        bad(new short[]{(short)0x0014,0}); // truncated const
        bad(new short[]{(short)0x0000,(short)0x0100}); // payload at odd code unit
        bad(new short[]{(short)0x0026,1,0,(short)0x0300,0,0,0,0}); // zero-width payload
    }
    static void bad(short[] c)throws Exception {
        try { RoiDexInstructionScanner.scan(c); }
        catch(RoiDexInstructionScanner.ScanException expected){return;}
        throw new AssertionError("expected ScanException");
    }
    static void eq(int e,int a){if(e!=a)throw new AssertionError("expected "+e+" got "+a);}
    static void yes(boolean b){if(!b)throw new AssertionError("expected true");}
}

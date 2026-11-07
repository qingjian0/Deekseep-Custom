package ds.dsh.DeepSeek;

public final class RoiDexInvokeResultLinkerTest {
    public static void main(String[] args)throws Exception {
        directLink();rangeLink();typeMismatch();widePairMismatch();branchEntryRejected();noResultIsAllowed();
        System.out.println("RoiDexInvokeResultLinkerTest: PASS");
    }
    static void directLink()throws Exception {
        RoiDexCodeReader.CodeItem code=code(new short[]{(short)0x0071,0,0,(short)0x010a},2);
        RoiDexInvokeResultLinker.Result r=analyze(code,"()I");
        eq(1,r.links.length);eq(0,r.rejected.length);eq(0,r.links[0].invokeOffset);
        eq(3,r.links[0].resultOffset);eq(1,r.links[0].destinationRegister);
        eq("Lx;",r.links[0].ownerDescriptor);eq("decode",r.links[0].methodName);
    }
    static void rangeLink()throws Exception {
        RoiDexCodeReader.CodeItem code=code(new short[]{(short)0x0077,0,0,(short)0x010a},2);
        RoiDexInvokeResultLinker.Result r=analyze(code,"()I");
        eq(1,r.links.length);eq(0,r.rejected.length);
    }
    static void typeMismatch()throws Exception {
        RoiDexCodeReader.CodeItem code=code(new short[]{(short)0x0071,0,0,(short)0x010b},3);
        RoiDexInvokeResultLinker.Result r=analyze(code,"()I");
        eq(0,r.links.length);eq(1,r.rejected.length);
        eq("invoke_result_type_mismatch",r.rejected[0].reason);
    }
    static void widePairMismatch()throws Exception {
        RoiDexCodeReader.CodeItem code=code(new short[]{(short)0x0271,0,(short)0x0020,(short)0x010a},4);
        RoiDexInvokeResultLinker.Result r=analyze(code,"(J)I");
        eq(0,r.links.length);eq(1,r.rejected.length);
        eq("wide_argument_register_pair_invalid",r.rejected[0].reason);
    }
    static void branchEntryRejected()throws Exception {
        RoiDexCodeReader.CodeItem code=code(new short[]{(short)0x0071,0,0,(short)0x010a,
                (short)0x0029,(short)0xffff},2);
        RoiDexInvokeResultLinker.Result r=analyze(code,"()I");
        eq(0,r.links.length);eq(1,r.rejected.length);
        eq("move_result_not_unique_fallthrough",r.rejected[0].reason);
    }
    static void noResultIsAllowed()throws Exception {
        RoiDexCodeReader.CodeItem code=code(new short[]{(short)0x0071,0,0,(short)0x000e},1);
        RoiDexInvokeResultLinker.Result r=analyze(code,"()I");
        eq(0,r.links.length);eq(0,r.rejected.length);
    }
    static RoiDexInvokeResultLinker.Result analyze(RoiDexCodeReader.CodeItem code,String proto)
            throws Exception {
        return RoiDexInvokeResultLinker.analyze(code,index ->
                new RoiDexFileReader.ResolvedMethodId(index,"Lx;","decode",proto));
    }
    static RoiDexCodeReader.CodeItem code(short[] instructions,int registers)throws Exception {
        byte[] b=new byte[16+instructions.length*2];
        put16(b,0,registers);put16(b,2,0);put16(b,4,0);put16(b,6,0);
        put32(b,8,0);put32(b,12,instructions.length);
        for(int i=0;i<instructions.length;i++)put16(b,16+i*2,instructions[i]&0xffff);
        return RoiDexCodeReader.readCodeItem(b,0,b.length,0,1);
    }
    static void put16(byte[] b,int p,int v){b[p]=(byte)v;b[p+1]=(byte)(v>>>8);}
    static void put32(byte[] b,int p,int v){for(int i=0;i<4;i++)b[p+i]=(byte)(v>>>(8*i));}
    static void eq(int e,int a){if(e!=a)throw new AssertionError("expected "+e+" got "+a);}
    static void eq(String e,String a){if(!e.equals(a))throw new AssertionError("expected "+e+" got "+a);}
}

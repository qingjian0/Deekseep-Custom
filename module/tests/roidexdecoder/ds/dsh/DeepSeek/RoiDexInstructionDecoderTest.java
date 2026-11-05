package ds.dsh.DeepSeek;
public final class RoiDexInstructionDecoderTest {
 public static void main(String[] args)throws Exception{test35c();test3rc();testWide();testStatic();testFailures();System.out.println("RoiDexInstructionDecoderTest: PASS");}
 static void test35c()throws Exception{
  // invoke-direct {v1,v2,v3}, method@0x123
  short[] c={(short)0x3070,(short)0x0123,(short)0x0321};
  RoiDexInstructionDecoder.Decoded d=RoiDexInstructionDecoder.decode(c,0,8);
  eq(3,d.width);eq(0x70,d.opcode);eq(0x123,d.referenceIndex);eq(3,d.registers.length);
  eq(1,d.registers[0]);eq(2,d.registers[1]);eq(3,d.registers[2]);
 }
 static void test3rc()throws Exception{
  // invoke-virtual/range {v4 .. v6}, method@0x123
  short[] c={(short)0x0376,(short)0x0123,(short)0x0004};
  RoiDexInstructionDecoder.Decoded d=RoiDexInstructionDecoder.decode(c,0,10);
  eq(3,d.width);eq(0x123,d.referenceIndex);eq(3,d.registers.length);eq(4,d.registers[0]);eq(6,d.registers[2]);
 }
 static void testWide()throws Exception{
  String s="(Ljava/lang/String;JD[Ljava/lang/Object;)V";
  eq(-1,map(s,false,0));eq(0,map(s,false,1));eq(1,map(s,false,2));eq(1,map(s,false,3));eq(2,map(s,false,4));eq(2,map(s,false,5));eq(3,map(s,false,6));
 }
 static void testStatic()throws Exception{eq(0,map("(JD)V",true,0));eq(0,map("(JD)V",true,1));eq(1,map("(JD)V",true,2));}
 static int map(String s,boolean st,int p)throws Exception{return RoiDexInstructionDecoder.parameterIndexForRegisterPosition(s,st,p);}
 static void testFailures()throws Exception{fail(new short[]{(short)0x0028},0,4);fail(new short[]{(short)0x0070},0,4);fail(new short[]{(short)0x0070,0,(short)0x000f},0,4);}
 static void fail(short[] c,int o,int n)throws Exception{try{RoiDexInstructionDecoder.decode(c,o,n);}catch(RoiDexInstructionDecoder.DecodeException e){return;}throw new AssertionError("expected DecodeException");}
 static void eq(int e,int a){if(e!=a)throw new AssertionError("expected "+e+" got "+a);}
}
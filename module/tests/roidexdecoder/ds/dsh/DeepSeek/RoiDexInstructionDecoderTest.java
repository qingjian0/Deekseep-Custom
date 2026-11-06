package ds.dsh.DeepSeek;
public final class RoiDexInstructionDecoderTest {
 public static void main(String[] args)throws Exception{
  test35c();test35cFiveRegisters();test3rc();testWide();testStatic();testMoveResults();testStructuredPositions();testReferenceKinds();testIntegerLiterals();testFailures();testMalformedDescriptors();
  System.out.println("RoiDexInstructionDecoderTest: PASS");
 }
 static void test35c()throws Exception{
  short[] c={(short)0x3370,(short)0x0123,(short)0x0321};
  RoiDexInstructionDecoder.Decoded d=RoiDexInstructionDecoder.decode(c,0,8);
  eq(3,d.width);eq(0x70,d.opcode);eq(0x123,d.referenceIndex);eq(3,d.registers.length);
  eq(1,d.registers[0]);eq(2,d.registers[1]);eq(3,d.registers[2]);
 }
 static void test35cFiveRegisters()throws Exception{
  short[] c={(short)0x5570,(short)0x002a,(short)0x4321};
  RoiDexInstructionDecoder.Decoded d=RoiDexInstructionDecoder.decode(c,0,8);
  eq(5,d.registers.length);for(int i=0;i<5;i++)eq(i+1,d.registers[i]);
 }
 static void test3rc()throws Exception{
  short[] c={(short)0x0376,(short)0x0123,(short)0x0004};
  RoiDexInstructionDecoder.Decoded d=RoiDexInstructionDecoder.decode(c,0,10);
  eq(3,d.width);eq(0x123,d.referenceIndex);eq(3,d.registers.length);
  eq(4,d.registers[0]);eq(6,d.registers[2]);
 }
 static void testWide()throws Exception{
  String s="(Ljava/lang/String;JD[Ljava/lang/Object;)V";
  eq(-1,map(s,false,0));eq(0,map(s,false,1));eq(1,map(s,false,2));
  eq(1,map(s,false,3));eq(2,map(s,false,4));eq(2,map(s,false,5));eq(3,map(s,false,6));
 }
 static void testStatic()throws Exception{
  eq(0,map("(JD)V",true,0));eq(0,map("(JD)V",true,1));eq(1,map("(JD)V",true,2));
  eq(1,map("(JD)Ljava/lang/String;",true,2));
 }
 static void testMalformedDescriptors()throws Exception{
  badDescriptor("(V)V");badDescriptor("([V)V");badDescriptor("(L;)V");
  badDescriptor("(Lfoo//Bar;)V");badDescriptor("(Ljava.lang.String;)V");
  badDescriptor("(I)");badDescriptor("(I)Vjunk");badDescriptor("(I)Q");
  badDescriptor("(Lfoo/Bar)V");badDescriptor("([[[)V");
 }
 static void badDescriptor(String s)throws Exception{
  try{map(s,true,0);}catch(RoiDexInstructionDecoder.DecodeException expected){return;}
  throw new AssertionError("expected invalid descriptor: "+s);
 }
 static int map(String s,boolean st,int p)throws Exception{return RoiDexInstructionDecoder.parameterIndexForRegisterPosition(s,st,p);}
 static void testMoveResults()throws Exception{
  RoiDexInstructionDecoder.Decoded narrow=RoiDexInstructionDecoder.decode(new short[]{(short)0x000a},0,4);
  eq(1,narrow.resultWordCount);eq(0,narrow.registers[0]);
  RoiDexInstructionDecoder.Decoded wide=RoiDexInstructionDecoder.decode(new short[]{(short)0x000b},0,4);
  eq(2,wide.resultWordCount);eq(0,wide.registers[0]);
  RoiDexInstructionDecoder.Decoded object=RoiDexInstructionDecoder.decode(new short[]{(short)0x000c},0,4);
  eq(1,object.resultWordCount);eq(0,object.registers[0]);
  RoiDexInstructionDecoder.Decoded exception=RoiDexInstructionDecoder.decode(new short[]{(short)0x000d},0,4);
  if(!exception.moveException)throw new AssertionError("move-exception marker missing");
  try{RoiDexInstructionDecoder.decode(new short[]{(short)0x020b},0,3);}
  catch(RoiDexInstructionDecoder.DecodeException expected){return;}
  throw new AssertionError("wide result must validate its second register word");
 }
 static void testStructuredPositions()throws Exception{
  RoiDexInstructionDecoder.RegisterPosition receiver=
   RoiDexInstructionDecoder.registerPositionFor("(JD)V",false,0);
  if(receiver.kind!=RoiDexInstructionDecoder.RegisterPositionKind.RECEIVER)throw new AssertionError("receiver");
  RoiDexInstructionDecoder.RegisterPosition first=
   RoiDexInstructionDecoder.registerPositionFor("(JD)V",true,0);
  if(first.kind!=RoiDexInstructionDecoder.RegisterPositionKind.PARAMETER||first.parameterIndex!=0)
   throw new AssertionError("first parameter");
  RoiDexInstructionDecoder.RegisterPosition wideSecond=
   RoiDexInstructionDecoder.registerPositionFor("(JD)V",true,1);
  if(wideSecond.kind!=RoiDexInstructionDecoder.RegisterPositionKind.PARAMETER||wideSecond.parameterIndex!=0)
   throw new AssertionError("wide second word");
  RoiDexInstructionDecoder.RegisterPosition invalid=
   RoiDexInstructionDecoder.registerPositionFor("(JD)V",true,4);
  if(invalid.kind!=RoiDexInstructionDecoder.RegisterPositionKind.INVALID)
   throw new AssertionError("invalid position");
  eq(5,RoiDexInstructionDecoder.registerWordCountFor("(JD)V",false));
  eq(4,RoiDexInstructionDecoder.registerWordCountFor("(JD)V",true));
 }
 static void testReferenceKinds()throws Exception{
  RoiDexInstructionDecoder.Decoded string=RoiDexInstructionDecoder.decode(
   new short[]{(short)0x001a,(short)0x1234},2);
  if(string.referenceKind!=RoiDexInstructionDecoder.Decoded.ReferenceKind.STRING)throw new AssertionError("const-string kind");
  RoiDexInstructionDecoder.Decoded jumbo=RoiDexInstructionDecoder.decode(
   new short[]{(short)0x001b,(short)0x5678,(short)0x1234},2);
  eq(0x12345678,jumbo.referenceIndex);
  if(jumbo.referenceKind!=RoiDexInstructionDecoder.Decoded.ReferenceKind.STRING)throw new AssertionError("jumbo string kind");
  RoiDexInstructionDecoder.Decoded type=RoiDexInstructionDecoder.decode(
   new short[]{(short)0x001c,1},2);
  if(type.referenceKind!=RoiDexInstructionDecoder.Decoded.ReferenceKind.TYPE)throw new AssertionError("type kind");
  RoiDexInstructionDecoder.Decoded method=RoiDexInstructionDecoder.decode(
   new short[]{(short)0x0071,1,0},1);
  if(method.referenceKind!=RoiDexInstructionDecoder.Decoded.ReferenceKind.METHOD)throw new AssertionError("method kind");
 }
 static void testIntegerLiterals()throws Exception{
  RoiDexInstructionDecoder.Decoded small=RoiDexInstructionDecoder.decode(
   new short[]{(short)0x3012},1);
  if(!small.hasLiteral)throw new AssertionError("const/4 literal missing");eq(3,(int)small.literalValue);
  RoiDexInstructionDecoder.Decoded signed=RoiDexInstructionDecoder.decode(
   new short[]{(short)0x0013,(short)0xfffe},1);
  eq(-2,(int)signed.literalValue);
  RoiDexInstructionDecoder.Decoded full=RoiDexInstructionDecoder.decode(
   new short[]{(short)0x0014,(short)0x5678,(short)0x1234},1);
  eq(0x12345678,(int)full.literalValue);
 }
 static void testFailures()throws Exception{
  fail(new short[]{(short)0x0028},0,4);
  fail(new short[]{(short)0x0070},0,4);
  fail(new short[]{(short)0x1170,(short)0x0001,(short)0x000f},0,4);
  fail(new short[]{(short)0x0376,(short)0x0001,(short)0xffff},0,8);
 }
 static void fail(short[] c,int o,int n)throws Exception{
  try{RoiDexInstructionDecoder.decode(c,o,n);}catch(RoiDexInstructionDecoder.DecodeException e){return;}
  throw new AssertionError("expected DecodeException");
 }
 static void eq(int e,int a){if(e!=a)throw new AssertionError("expected "+e+" got "+a);}
}

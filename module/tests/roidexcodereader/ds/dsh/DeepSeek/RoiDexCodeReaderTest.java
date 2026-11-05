package ds.dsh.DeepSeek;
public final class RoiDexCodeReaderTest {
 public static void main(String[] args)throws Exception{
  classData();codeItem();tries();malformed();
  System.out.println("RoiDexCodeReaderTest: PASS");
 }
 static void classData()throws Exception{
  byte[] b=new byte[64];int p=8;
  p=put(b,p,1);p=put(b,p,1);p=put(b,p,1);p=put(b,p,1);
  p=put(b,p,3);p=put(b,p,7);
  p=put(b,p,1);p=put(b,p,2);
  p=put(b,p,2);p=put(b,p,0x10);p=put(b,p,32);
  p=put(b,p,0);p=put(b,p,0x9);p=put(b,p,0);
  put16(b,32,1);put16(b,34,0);put16(b,36,0);put16(b,38,0);
  put32(b,40,0);put32(b,44,1);put16(b,48,0x000e);
  RoiDexCodeReader.ClassData d=RoiDexCodeReader.readClassData(b,0,b.length,8,4,4,1);
  eq(1,d.staticFields.length);eq(3,d.staticFields[0].fieldIndex);
  eq(1,d.instanceFields.length);eq(1,d.instanceFields[0].fieldIndex);
  eq(1,d.directMethods.length);eq(2,d.directMethods[0].methodIndex);eq(32,d.directMethods[0].codeOffset);if(d.directMethods[0].codeItem==null)throw new AssertionError("code item not attached");eq(1,d.directMethods[0].codeItem.instructions.length);
  eq(1,d.virtualMethods.length);eq(0,d.virtualMethods[0].methodIndex);eq(0,d.virtualMethods[0].codeOffset);
 }
 static void codeItem()throws Exception{
  byte[] b=new byte[64];put16(b,16,4);put16(b,18,2);put16(b,20,1);put16(b,22,0);
  put32(b,24,0);put32(b,28,2);put16(b,32,0x000c);put16(b,34,0x000e);
  RoiDexCodeReader.CodeItem c=RoiDexCodeReader.readCodeItem(b,0,b.length,16,1);
  eq(4,c.registersSize);eq(2,c.insSize);eq(2,c.instructions.length);eq(0x0c,c.instructions[0]&65535);eq(36,c.nextOffset);RoiDexInstructionDecoder.Decoded decoded=c.decodeInstructionAt(0);eq(0x0c,decoded.opcode);eq(0,decoded.registers[0]);
 }
 static void tries()throws Exception{
  byte[] b=new byte[64];put16(b,16,1);put16(b,18,1);put16(b,22,1);put32(b,28,2);
  put16(b,32,0x0000);put16(b,34,0x000e);put16(b,36,0);put16(b,38,0);put16(b,40,1);put16(b,42,1);
  // handlers list: one handler, signed size +1, type 0 -> addr 0
  b[44]=1;b[45]=1;b[46]=0;b[47]=1;
  RoiDexCodeReader.CodeItem c=RoiDexCodeReader.readCodeItem(b,0,b.length,16,1);
  eq(1,c.triesSize);eq(1,c.tryItems[0].handlerOffset);eq(48,c.nextOffset);
  eq(1,c.catchHandlers.length);eq(1,c.catchHandlers[0].typedCatches.length);
  eq(0,c.catchHandlers[0].typedCatches[0].typeIndex);
  eq(1,c.catchHandlers[0].typedCatches[0].address);eq(-1,c.catchHandlers[0].catchAllAddress);
  RoiDexExceptionControlFlow.Result graph=RoiDexExceptionControlFlow.analyze(c);
  if(!graph.exceptionEdgesIncluded)throw new AssertionError("exception edges not marked included");
  eq(2,graph.normalGraph.blocks.length);eq(1,graph.exceptionEdges.length);
  eq(0,graph.exceptionEdges[0].fromBlock);eq(1,graph.exceptionEdges[0].toBlock);
  eq(1,graph.exceptionEdges[0].handlerAddress);eq(0,graph.exceptionEdges[0].catchTypeIndex);
  RoiDexRegisterFlowAnalyzer.Result flow=RoiDexRegisterFlowAnalyzer.analyze(c);
  if(!flow.exceptionEdgesIncluded||flow.normalControlFlowOnly)
   throw new AssertionError("register flow omitted exception edges");
  eq(1,flow.exceptionEdges.length);
  RoiDexReachingDefinitions.Result reaching=RoiDexReachingDefinitions.analyze(flow);
  if(!reaching.exceptionEdgesIncluded)throw new AssertionError("reaching definitions omitted exception edges");
  RoiDexUseDefChains.Result chains=RoiDexUseDefChains.analyze(flow,reaching);
  if(!chains.exceptionEdgesIncluded)throw new AssertionError("use-def chains omitted exception edges");
 }
 static void malformed()throws Exception{
  byte[] b=new byte[24];put16(b,16,1);put16(b,18,2);
  badCode(b,16,8);
  byte[] c=new byte[32];put32(c,12,99);badCode(c,0,32);
  byte[] d=new byte[32];put16(d,0,0);put16(d,2,1);put32(d,12,1);put16(d,16,0);badCode(d,0,32);
  byte[] e=new byte[32];put32(e,8,0x7fffffff); // debug offset outside bounded data
  badCode(e,0,32);
  byte[] f=new byte[16];f[0]=1;badClass(f,0,1,0,1,1);
 }
 static int put(byte[] b,int p,int n){b[p++]=(byte)n;return p;}
 static void badCode(byte[] b,int p,int n)throws Exception{try{RoiDexCodeReader.readCodeItem(b,0,b.length,p,1);}catch(RoiDexCodeReader.CodeException e){return;}throw new AssertionError("expected bad code");}
 static void badClass(byte[] b,int o,int n,int p,int f,int m)throws Exception{try{RoiDexCodeReader.readClassData(b,o,n,p,f,m,1);}catch(RoiDexCodeReader.CodeException e){return;}throw new AssertionError("expected bad class");}
 static void put16(byte[] b,int p,int n){b[p]=(byte)n;b[p+1]=(byte)(n>>>8);}
 static void put32(byte[] b,int p,int n){for(int i=0;i<4;i++)b[p+i]=(byte)(n>>>(8*i));}
 static void eq(int a,int b){if(a!=b)throw new AssertionError(a+" != "+b);}
}

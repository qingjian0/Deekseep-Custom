package ds.dsh.DeepSeek;

public final class RoiDexFileReaderTest {
 public static void main(String[] args)throws Exception{
  testEmptyDex();testAllIdTables();testDecodedStringAndProtoTypes();testClassDataIntegration();testBadMagic();testTruncated();testBadHeaderSize();
  testBadEndian();testFileSize();testSectionOverflow();testBadTypeReference();
  testBadStringOffset();testBadClassOffset();testTableOverlap();testMapOutsideData();
  testResolvedMethodDefinition();
  testResolvedMethodEntryLayoutIntegration();
  System.out.println("RoiDexFileReaderTest: PASS");
 }
 static void testEmptyDex()throws Exception{
  RoiDexFileReader.DexFile d=RoiDexFileReader.read(fixture(112));
  eq("039",d.header.version);eq(112,d.header.fileSize);eq(0,d.header.strings.size);
 }
 static void testAllIdTables()throws Exception{
  byte[] b=fixture(192);
  put32(b,56,2);put32(b,60,112);put32(b,64,1);put32(b,68,120);
  put32(b,72,1);put32(b,76,124);put32(b,80,1);put32(b,84,136);
  put32(b,88,1);put32(b,92,144);put32(b,96,1);put32(b,100,152);
  put32(b,104,8);put32(b,108,184);
  put32(b,112,184);put32(b,116,188);put32(b,120,0);
  put32(b,124,0);put32(b,128,0);put32(b,132,0);
  put16(b,136,0);put16(b,138,0);put32(b,140,1);
  put16(b,144,0);put16(b,146,0);put32(b,148,1);
  put32(b,152,0);put32(b,156,1);put32(b,160,-1);put32(b,164,0);
  put32(b,168,-1);put32(b,172,0);put32(b,176,0);put32(b,180,0);
  RoiDexFileReader.DexFile d=RoiDexFileReader.read(b);
  eq(2,d.stringDataOffsets.length);eq(1,d.typeDescriptorStringIndices.length);
  eq(1,d.protos.length);eq(0,d.protoParameterTypeIndices[0].length);eq("",d.strings[0]);eq("",d.typeDescriptors[0]);eq(1,d.fields.length);eq(1,d.methods.length);eq(1,d.classes.length);
  eq(1,d.fields[0].nameIndex);eq(1,d.methods[0].nameIndex);
  eq(-1,d.classes[0].superclassIndex);eq(-1,d.classes[0].sourceFileIndex);
 }
 static void testDecodedStringAndProtoTypes()throws Exception{
  byte[] b=fixture(196);
  put32(b,56,1);put32(b,60,112);put32(b,64,1);put32(b,68,116);
  put32(b,72,1);put32(b,76,120);put32(b,104,12);put32(b,108,184);
  put32(b,112,184);put32(b,116,0);
  put32(b,120,0);put32(b,124,0);put32(b,128,188);
  b[184]=1;b[185]='x';b[186]=0;
  put32(b,188,1);put16(b,192,0);
  RoiDexFileReader.DexFile d=RoiDexFileReader.read(b);
  eq("x",d.strings[0]);eq("x",d.typeDescriptors[0]);
  eq(1,d.protoParameterTypeIndices[0].length);eq(0,d.protoParameterTypeIndices[0][0]);
 }
 static void testClassDataIntegration()throws Exception{
  byte[] b=fixture(188);
  put32(b,56,1);put32(b,60,112);put32(b,64,1);put32(b,68,116);
  put32(b,96,1);put32(b,100,120);put32(b,104,36);put32(b,108,152);
  put32(b,112,152);put32(b,116,0);
  put32(b,120,0);put32(b,124,0);put32(b,128,-1);put32(b,132,0);
  put32(b,136,-1);put32(b,140,0);put32(b,144,160);put32(b,148,0);
  b[152]=3;b[153]='L';b[154]='x';b[155]=';';b[156]=0;
  b[160]=0;b[161]=0;b[162]=0;b[163]=0;
  RoiDexFileReader.DexFile d=RoiDexFileReader.read(b);
  if(d.classData.length!=1||d.classData[0]==null)throw new AssertionError("class_data not connected");
  eq("Lx;",d.typeDescriptors[0]);
  eq(0,d.classData[0].directMethods.length);eq(0,d.classData[0].virtualMethods.length);
 }
 static void testResolvedMethodDefinition()throws Exception{
  byte[] b=fixture(205);
  put32(b,56,3);put32(b,60,112);put32(b,64,2);put32(b,68,124);
  put32(b,72,1);put32(b,76,132);put32(b,88,1);put32(b,92,144);
  put32(b,96,1);put32(b,100,152);put32(b,104,21);put32(b,108,184);
  put32(b,112,192);put32(b,116,197);put32(b,120,200);
  put32(b,124,0);put32(b,128,1);
  put32(b,132,1);put32(b,136,1);put32(b,140,0);
  put16(b,144,0);put16(b,146,0);put32(b,148,2);
  put32(b,152,0);put32(b,156,1);put32(b,160,-1);put32(b,164,0);
  put32(b,168,-1);put32(b,172,0);put32(b,176,184);put32(b,180,0);
  // class_data: no fields, one direct method at method_idx 0, public static, no code_item.
  b[184]=0;b[185]=0;b[186]=1;b[187]=0;b[188]=0;b[189]=9;b[190]=0;
  b[192]=3;b[193]='L';b[194]='x';b[195]=';';b[196]=0;
  b[197]=1;b[198]='V';b[199]=0;
  b[200]=3;b[201]='r';b[202]='u';b[203]='n';b[204]=0;
  RoiDexFileReader.DexFile d=RoiDexFileReader.read(b);
  eq(1,d.definedMethods.length);
  RoiDexFileReader.ResolvedMethod m=d.definedMethods[0];
  eq(0,m.methodIndex);eq("Lx;",m.declaringClassDescriptor);eq("run",m.name);
  eq("()V",m.prototypeDescriptor);
  if(!m.isStatic||!m.isDirect||m.codeItem!=null)throw new AssertionError("method flags/kind/code mismatch");
  try {
   RoiDexMethodAnalysis.analyze(m);
   throw new AssertionError("method without code_item must not enter static flow analysis");
  } catch(RoiDexMethodAnalysis.AnalysisException expected) {
   if(!expected.getMessage().contains("method_code_item_missing"))
    throw new AssertionError("unexpected missing-code failure: "+expected.getMessage());
  }
 }

 static void testResolvedMethodEntryLayoutIntegration()throws Exception{
  byte[] b=fixture(242);
  put32(b,56,4);put32(b,60,112);put32(b,64,2);put32(b,68,128);
  put32(b,72,1);put32(b,76,136);put32(b,88,1);put32(b,92,148);
  put32(b,96,1);put32(b,100,156);put32(b,104,54);put32(b,108,188);
  put32(b,112,196);put32(b,116,201);put32(b,120,204);put32(b,124,209);
  put32(b,128,0);put32(b,132,1);
  put32(b,136,3);put32(b,140,1);put32(b,144,216);
  put16(b,148,0);put16(b,150,0);put32(b,152,2);
  put32(b,156,0);put32(b,160,1);put32(b,164,-1);put32(b,168,0);
  put32(b,172,-1);put32(b,176,0);put32(b,180,188);put32(b,184,0);
  // One static method; the valid shorty is VL and code_item starts at aligned offset 224.
  b[188]=0;b[189]=0;b[190]=1;b[191]=0;b[192]=0;b[193]=9;b[194]=(byte)0xe0;b[195]=1;
  b[196]=3;b[197]='L';b[198]='x';b[199]=';';b[200]=0;
  b[201]=1;b[202]='V';b[203]=0;
  b[204]=3;b[205]='r';b[206]='u';b[207]='n';b[208]=0;
  b[209]=2;b[210]='V';b[211]='L';b[212]=0;
  put32(b,216,1);put16(b,220,0);
  put16(b,224,2);put16(b,226,1);put16(b,228,0);put16(b,230,0);
  put32(b,232,0);put32(b,236,1);put16(b,240,0x0111); // return-object v1
  RoiDexFileReader.DexFile d=RoiDexFileReader.read(b);
  eq(1,d.definedMethods.length);
  RoiDexFileReader.ResolvedMethod method=d.definedMethods[0];
  eq("(Lx;)V",method.prototypeDescriptor);
  RoiDexMethodEntryLayout.Result layout=method.methodEntryLayout();
  eq(1,layout.firstIncomingRegister);eq(1,layout.parameters[0].firstRegister);
  RoiDexMethodAnalysis.Result analysis=RoiDexMethodAnalysis.analyze(method);
  eq(0,analysis.methodIndex);eq("Lx;",analysis.declaringClassDescriptor);
  eq("run",analysis.methodName);eq("(Lx;)V",analysis.prototypeDescriptor);
  if(!analysis.isStatic||!analysis.isDirect||analysis.codeOffset==0)
   throw new AssertionError("resolved method identity was not preserved");
  if(!analysis.candidateEvidenceOnly||analysis.hookAuthorizationGranted)
   throw new AssertionError("static analysis must never grant Hook authorization");
  if(!analysis.exceptionEdgesIncluded||analysis.completeForExceptionFlow)
   throw new AssertionError("exception-flow completeness must remain explicitly conservative");
  boolean mapped=false;
  for(RoiDexUseDefChains.UseSite use:analysis.useDefChains.uses)
   if(use.instructionOffset==0&&use.register==1)
    mapped=use.entryValueKind==RoiDexUseDefChains.EntryValueKind.PARAMETER
      &&use.entryParameterIndex==0&&"Lx;".equals(use.entryDescriptor);
  if(!mapped)throw new AssertionError("resolved method pipeline did not map the code_item parameter use");
  RoiDexCandidateEvidence.Result evidence=RoiDexCandidateEvidence.analyze(d,"apk:test-fixture");
  eq(1,evidence.graph.uniqueNodes);
  eq(0,evidence.graph.edgeCount);
  eq(1,evidence.methods.size());
  eq(1,evidence.analyzedMethods);
  eq(0,evidence.analysisFailures);
  if(!evidence.candidateEvidenceOnly||evidence.hookAuthorizationGranted)
   throw new AssertionError("DEX candidate evidence must not authorize Hook");
  if(!evidence.graph.descriptors.contains(
      "apk:test-fixture|Lx;->run(Lx;)V"))
   throw new AssertionError("candidate graph lost source scope or resolved method identity");
 }
 static void testBadMagic()throws Exception{byte[] b=fixture(112);b[0]='x';bad(b);}
 static void testTruncated()throws Exception{bad(new byte[111]);}
 static void testBadHeaderSize()throws Exception{byte[] b=fixture(112);put32(b,36,0x71);bad(b);}
 static void testBadEndian()throws Exception{byte[] b=fixture(112);put32(b,40,0x78563412);bad(b);}
 static void testFileSize()throws Exception{byte[] b=fixture(112);put32(b,32,0x7fffffff);bad(b);}
 static void testSectionOverflow()throws Exception{byte[] b=fixture(112);put32(b,56,0x7fffffff);put32(b,60,112);bad(b);}
 static void testBadTypeReference()throws Exception{
  byte[] b=fixture(140);put32(b,56,1);put32(b,60,112);put32(b,64,1);put32(b,68,116);
  put32(b,104,4);put32(b,108,136);put32(b,112,136);put32(b,116,1);bad(b);
 }
 static void testBadStringOffset()throws Exception{
  byte[] b=fixture(140);put32(b,56,1);put32(b,60,112);put32(b,104,4);put32(b,108,136);
  put32(b,112,140);bad(b);
 }
 static void testBadClassOffset()throws Exception{
  byte[] b=fixture(180);put32(b,64,1);put32(b,68,112);put32(b,96,1);put32(b,100,116);
  put32(b,104,4);put32(b,108,176);put32(b,116,0);put32(b,128,177);bad(b);
 }
 static void testTableOverlap()throws Exception{
  byte[] b=fixture(140);put32(b,56,1);put32(b,60,112);put32(b,64,1);put32(b,68,112);
  put32(b,104,4);put32(b,108,136);put32(b,112,136);bad(b);
 }
 static void testMapOutsideData()throws Exception{
  byte[] b=fixture(140);put32(b,52,112);put32(b,104,4);put32(b,108,136);bad(b);
 }
 static byte[] fixture(int size){
  byte[] b=new byte[size];b[0]='d';b[1]='e';b[2]='x';b[3]='\n';b[4]='0';b[5]='3';b[6]='9';
  put32(b,32,size);put32(b,36,112);put32(b,40,0x12345678);return b;
 }
 static void put16(byte[] b,int p,int v){b[p]=(byte)v;b[p+1]=(byte)(v>>>8);}
 static void put32(byte[] b,int p,int v){for(int i=0;i<4;i++)b[p+i]=(byte)(v>>>(8*i));}
 static void bad(byte[] b)throws Exception{
  try{RoiDexFileReader.read(b);}catch(RoiDexFileReader.DexException expected){return;}
  throw new AssertionError("expected DexException");
 }
 static void eq(int e,int a){if(e!=a)throw new AssertionError("expected "+e+" got "+a);}
 static void eq(String e,String a){if(!e.equals(a))throw new AssertionError("expected "+e+" got "+a);}
}

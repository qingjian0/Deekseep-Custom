package ds.dsh.DeepSeek;

public final class RoiDexFileReaderTest {
 public static void main(String[] args)throws Exception{
  testEmptyDex();testAllIdTables();testBadMagic();testTruncated();testBadHeaderSize();
  testBadEndian();testFileSize();testSectionOverflow();testBadTypeReference();
  testBadStringOffset();testBadClassOffset();testTableOverlap();testMapOutsideData();
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

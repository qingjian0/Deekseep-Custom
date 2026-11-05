package ds.dsh.DeepSeek;
public final class RoiDexDataReaderTest {
 public static void main(String[] x)throws Exception{
  uleb();strings();surrogates();stringFailures();typeLists();
  System.out.println("RoiDexDataReaderTest: PASS");
 }
 static void uleb()throws Exception{
  eq(0,RoiDexDataReader.readUleb128(new byte[]{0},0,1));
  eq(128,RoiDexDataReader.readUleb128(new byte[]{(byte)128,1},0,2));
  eq(0xffffffffL,RoiDexDataReader.readUleb128(new byte[]{-1,-1,-1,-1,15},0,5));
  badUleb(new byte[]{-128},1);badUleb(new byte[]{-128,-128,-128,-128,-128},5);
  badUleb(new byte[]{-128,-128,-128,-128,16},5);
 }
 static void strings()throws Exception{
  byte[] b={3,'A',(byte)192,(byte)128,'B',0};
  RoiDexDataReader.StringData s=RoiDexDataReader.readStringData(b,0,b.length,0);
  eq(3,s.utf16Size);eq("A\u0000B",s.value);eq(6,s.nextOffset);
 }
 static void surrogates()throws Exception{
  byte[] b={2,(byte)237,(byte)160,(byte)189,(byte)237,(byte)184,(byte)128,0};
  eq(new String(new char[]{'\ud83d','\ude00'}),RoiDexDataReader.readStringData(b,0,b.length,0).value);
 }
 static void stringFailures()throws Exception{
  badString(new byte[]{1,(byte)194},2);badString(new byte[]{1,(byte)194,0},3);
  badString(new byte[]{1,(byte)224,(byte)128,(byte)128,0},5);
  badString(new byte[]{1,(byte)240,(byte)144,(byte)128,(byte)128,0},6);
  badString(new byte[]{1,'A'},2);badString(new byte[]{1,'A','B',0},4);
  badString(new byte[]{2,'A',0},3);badString(new byte[]{0,(byte)192,(byte)128,0},4);
 }
 static void typeLists()throws Exception{
  byte[] b=new byte[20];put32(b,4,3);put16(b,8,0);put16(b,10,2);put16(b,12,4);
  RoiDexDataReader.TypeList t=RoiDexDataReader.readTypeList(b,0,20,4,5);
  eq(3,t.typeIndices.length);eq(4,t.typeIndices[2]);eq(14,t.nextOffset);
  byte[] c=new byte[8];put32(c,0,4);badList(c,0,8,0,1);
  byte[] d=new byte[8];put32(d,0,1);put16(d,4,1);badList(d,0,8,0,1);
  badList(d,0,3,0,1);badList(d,0,8,1,1);
 }
 static void badUleb(byte[] b,int l)throws Exception{try{RoiDexDataReader.readUleb128(b,0,l);}catch(RoiDexDataReader.DataException e){return;}throw new AssertionError();}
 static void badString(byte[] b,int l)throws Exception{try{RoiDexDataReader.readStringData(b,0,l,0);}catch(RoiDexDataReader.DataException e){return;}throw new AssertionError();}
 static void badList(byte[] b,int o,int l,int p,int n)throws Exception{try{RoiDexDataReader.readTypeList(b,o,l,p,n);}catch(RoiDexDataReader.DataException e){return;}throw new AssertionError();}
 static void put16(byte[] b,int p,int v){b[p]=(byte)v;b[p+1]=(byte)(v>>>8);}
 static void put32(byte[] b,int p,int v){for(int i=0;i<4;i++)b[p+i]=(byte)(v>>>(i*8));}
 static void eq(long a,long b){if(a!=b)throw new AssertionError(a+" != "+b);}
 static void eq(String a,String b){if(!a.equals(b))throw new AssertionError(a+" != "+b);}
}

package ds.dsh.DeepSeek;

/** Bounded read-only readers for DEX variable-width data items. */
public final class RoiDexDataReader {
 private RoiDexDataReader(){}
 public static final class DataException extends Exception { public DataException(String m){super(m);} }
 public static final class StringData {
  public final int utf16Size,nextOffset; public final String value;
  StringData(int n,String s,int p){utf16Size=n;value=s;nextOffset=p;}
 }
 public static final class TypeList {
  public final int[] typeIndices; public final int nextOffset;
  TypeList(int[] a,int p){typeIndices=a;nextOffset=p;}
 }
 public static long readUleb128(byte[] b,int p,int limit)throws DataException {
  if(b==null||p<0||limit<0||limit>b.length||p>=limit)throw new DataException("uleb_offset_out_of_range");
  long v=0;
  for(int i=0;i<5;i++){
   if(p+i>=limit)throw new DataException("uleb_truncated");
   int x=b[p+i]&255;
   if(i==4&&(x&0xf0)!=0)throw new DataException("uleb_overflow");
   v|=((long)(x&0x7f))<<(7*i);
   if((x&0x80)==0)return v;
  }
  throw new DataException("uleb_too_long");
 }
 public static StringData readStringData(byte[] b,int dataOff,int dataSize,int stringOff)throws DataException {
  int limit=dataLimit(b,dataOff,dataSize);
  if(stringOff<dataOff||stringOff>=limit)throw new DataException("string_offset_out_of_data");
  long declared=readUleb128(b,stringOff,limit);
  if(declared>Integer.MAX_VALUE)throw new DataException("string_utf16_size_overflow");
  int n=(int)declared,p=ulebEnd(b,stringOff,limit),count=0;
  if(n>limit-p)throw new DataException("string_declared_size_exceeds_data");
  char[] chars=new char[n];
  while(true){
   if(p>=limit)throw new DataException("string_unterminated");
   int a=b[p++]&255;if(a==0)break;int ch;
   if(a<=0x7f){ch=a;}
   else if(a>=0xc0&&a<=0xdf){
    if(p>=limit)throw new DataException("mutf8_truncated_pair");
    int c=b[p++]&255;if((c&0xc0)!=0x80)throw new DataException("mutf8_invalid_continuation");
    ch=((a&31)<<6)|(c&63);
    if(ch==0){if(a!=0xc0||c!=0x80)throw new DataException("mutf8_invalid_nul");}
    else if(ch<0x80)throw new DataException("mutf8_overlong_two_byte");
   }else if(a>=0xe0&&a<=0xef){
    if(limit-p<2)throw new DataException("mutf8_truncated_triple");
    int c=b[p++]&255,d=b[p++]&255;
    if((c&0xc0)!=0x80||(d&0xc0)!=0x80)throw new DataException("mutf8_invalid_continuation");
    ch=((a&15)<<12)|((c&63)<<6)|(d&63);
    if(ch<0x800)throw new DataException("mutf8_overlong_three_byte");
   }else throw new DataException("mutf8_invalid_lead");
   if(count>=n)throw new DataException("string_utf16_size_mismatch");
   chars[count++]=(char)ch;
  }
  if(count!=n)throw new DataException("string_utf16_size_mismatch");
  return new StringData(n,new String(chars),p);
 }
 public static TypeList readTypeList(byte[] b,int dataOff,int dataSize,int itemOff,int typeCount)throws DataException {
  int limit=dataLimit(b,dataOff,dataSize);
  if(itemOff<dataOff||itemOff>=limit||(itemOff&3)!=0)throw new DataException("type_list_offset_invalid");
  if(limit-itemOff<4)throw new DataException("type_list_truncated_header");
  long n=u32(b,itemOff,limit);if(n>Integer.MAX_VALUE)throw new DataException("type_list_size_overflow");
  long end=(long)itemOff+4L+n*2L;if(end>limit)throw new DataException("type_list_out_of_data");
  int[] values=new int[(int)n];
  for(int i=0;i<values.length;i++){
   int p=itemOff+4+i*2,idx=(b[p]&255)|((b[p+1]&255)<<8);
   if(idx>=typeCount)throw new DataException("type_list_index_out_of_range");
   values[i]=idx;
  }
  return new TypeList(values,(int)end);
 }
 private static int ulebEnd(byte[] b,int p,int limit)throws DataException {
  readUleb128(b,p,limit);for(int i=0;i<5;i++)if((b[p+i]&128)==0)return p+i+1;
  throw new DataException("uleb_too_long");
 }
 private static long u32(byte[] b,int p,int limit)throws DataException {
  if(p<0||limit-p<4)throw new DataException("type_list_truncated_header");
  return ((long)b[p]&255)|(((long)b[p+1]&255)<<8)|(((long)b[p+2]&255)<<16)|(((long)b[p+3]&255)<<24);
 }
 private static int dataLimit(byte[] b,int off,int size)throws DataException {
  if(b==null||off<0||size<0||off>b.length||(long)off+size>b.length)throw new DataException("data_region_invalid");
  return off+size;
 }
}

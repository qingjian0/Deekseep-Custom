package ds.dsh.DeepSeek;

import java.util.HashSet;
import java.util.Set;

/** Bounded, read-only parser for class_data_item and code_item payloads. */
public final class RoiDexCodeReader {
 private RoiDexCodeReader(){}
 public static final class CodeException extends Exception { public CodeException(String m){super(m);} }
 public static final class EncodedField {
  public final int fieldIndex,accessFlags;
  EncodedField(int i,int a){fieldIndex=i;accessFlags=a;}
 }
 public static final class EncodedMethod {
  public final int methodIndex,accessFlags,codeOffset; public final CodeItem codeItem;
  EncodedMethod(int i,int a,int c,CodeItem item){methodIndex=i;accessFlags=a;codeOffset=c;codeItem=item;}
 }
 public static final class ClassData {
  public final EncodedField[] staticFields,instanceFields;
  public final EncodedMethod[] directMethods,virtualMethods;
  public final int nextOffset;
  ClassData(EncodedField[] sf,EncodedField[] inf,EncodedMethod[] dm,EncodedMethod[] vm,int n){
   staticFields=sf;instanceFields=inf;directMethods=dm;virtualMethods=vm;nextOffset=n;
  }
 }
 public static final class TryItem {
  public final long startAddress; public final int instructionCount,handlerOffset;
  TryItem(long s,int n,int h){startAddress=s;instructionCount=n;handlerOffset=h;}
 }
 public static final class TypedCatch {
  public final int typeIndex,address;
  TypedCatch(int t,int a){typeIndex=t;address=a;}
 }
 public static final class CatchHandler {
  /** Offset from the beginning of encoded_catch_handler_list, including its size ULEB128. */
  public final int relativeOffset;
  public final int signedSize;
  public final TypedCatch[] typedCatches;
  /** -1 means the handler has no catch-all clause. */
  public final int catchAllAddress;
  CatchHandler(int o,int s,TypedCatch[] t,int a){relativeOffset=o;signedSize=s;typedCatches=t;catchAllAddress=a;}
 }
 public static final class CodeItem {
  public final int registersSize,insSize,outsSize,triesSize,debugInfoOffset,instructionsOffset;
  public final short[] instructions; public final TryItem[] tryItems;
  public final CatchHandler[] catchHandlers; public final int nextOffset;
  CodeItem(int r,int in,int out,int tr,int dbg,int io,short[] ins,TryItem[] ti,
           CatchHandler[] handlers,int next){
   registersSize=r;insSize=in;outsSize=out;triesSize=tr;debugInfoOffset=dbg;
   instructionsOffset=io;instructions=ins;tryItems=ti;catchHandlers=handlers;nextOffset=next;
  }
  public RoiDexInstructionDecoder.Decoded decodeInstructionAt(int codeUnitOffset)throws RoiDexInstructionDecoder.DecodeException{
   return RoiDexInstructionDecoder.decode(instructions,codeUnitOffset,registersSize);
  }
 }
 public static ClassData readClassData(byte[] b,int dataOff,int dataSize,int itemOff,int fieldCount,int methodCount,int typeCount)throws CodeException{
  int limit=dataLimit(b,dataOff,dataSize);
  if(itemOff<dataOff||itemOff>=limit)throw new CodeException("class_data_offset_out_of_data");
  Cursor c=new Cursor(itemOff,limit);
  int staticCount=count(b,c),instanceCount=count(b,c),directCount=count(b,c),virtualCount=count(b,c);
  EncodedField[] sf=fields(b,c,staticCount,fieldCount),inf=fields(b,c,instanceCount,fieldCount);
  EncodedMethod[] dm=methods(b,c,directCount,methodCount,dataOff,dataSize,fieldCount,methodCount,typeCount),vm=methods(b,c,virtualCount,methodCount,dataOff,dataSize,fieldCount,methodCount,typeCount);
  return new ClassData(sf,inf,dm,vm,c.p);
 }
 public static CodeItem readCodeItem(byte[] b,int dataOff,int dataSize,int itemOff,int typeCount)throws CodeException{
  int limit=dataLimit(b,dataOff,dataSize);
  if(itemOff<dataOff||itemOff>=limit||(itemOff&3)!=0)throw new CodeException("code_item_offset_invalid");
  if(limit-itemOff<16)throw new CodeException("code_item_header_truncated");
  int registers=u16(b,itemOff),ins=u16(b,itemOff+2),outs=u16(b,itemOff+4),tries=u16(b,itemOff+6);
  long debug=u32(b,itemOff+8),size=u32(b,itemOff+12);
  if(ins>registers)throw new CodeException("code_item_ins_exceeds_registers");
  if(debug!=0&&(debug<dataOff||debug>=limit))throw new CodeException("debug_info_offset_out_of_data");
  if(size>Integer.MAX_VALUE||size>(limit-(itemOff+16))/2)throw new CodeException("code_item_instructions_out_of_data");
  int instructionOffset=itemOff+16,n=(int)size;
  short[] units=new short[n];
  for(int i=0;i<n;i++)units[i]=(short)u16(b,instructionOffset+i*2);
  long after=(long)instructionOffset+n*2L;
  TryItem[] items=new TryItem[tries];
  CatchHandler[] handlers=new CatchHandler[0];
  if(tries>0){
   if((n&1)!=0)after+=2;
   if(after>limit||((long)tries*8)>limit-after)throw new CodeException("code_item_try_items_out_of_data");
   int p=(int)after;
   for(int i=0;i<tries;i++){
    long start=u32(b,p+i*8);int count=u16(b,p+i*8+4),handler=u16(b,p+i*8+6);
    if(start>=n||(long)start+count>n||count==0)throw new CodeException("try_item_instruction_range_invalid");
    items[i]=new TryItem(start,count,handler);
   }
   int handlersStart=p+tries*8;
   HandlerList parsed=parseHandlers(b,handlersStart,limit,items,typeCount,n);
   handlers=parsed.handlers;
   after=parsed.nextOffset;
  }
  return new CodeItem(registers,ins,outs,tries,(int)debug,instructionOffset,units,items,handlers,(int)after);
 }
 private static final class HandlerList {
  final CatchHandler[] handlers; final int nextOffset;
  HandlerList(CatchHandler[] h,int n){handlers=h;nextOffset=n;}
 }
 private static HandlerList parseHandlers(byte[] b,int start,int limit,TryItem[] tries,
        int typeCount,int insns)throws CodeException {
  Cursor c=new Cursor(start,limit);int count=count(b,c);
  if(count>limit-c.p)throw new CodeException("catch_handler_count_invalid");
  Set<Integer> starts=new HashSet<Integer>();
  CatchHandler[] handlers=new CatchHandler[count];
  for(int i=0;i<count;i++){
   int rel=c.p-start;starts.add(rel);int signed=sleb(b,c);
   long pairs=Math.abs((long)signed);
   if(pairs>typeCount)throw new CodeException("catch_handler_count_invalid");
   TypedCatch[] typed=new TypedCatch[(int)pairs];
   for(int j=0;j<typed.length;j++){
    int type=ulebInt(b,c);if(type>=typeCount)throw new CodeException("catch_type_index_out_of_range");
    int addr=ulebInt(b,c);if(addr>=insns)throw new CodeException("catch_handler_address_invalid");
    typed[j]=new TypedCatch(type,addr);
   }
   int catchAll=-1;
   if(signed<=0){catchAll=ulebInt(b,c);if(catchAll>=insns)throw new CodeException("catch_all_address_invalid");}
   if(c.p<=start+rel)throw new CodeException("catch_handler_no_progress");
   handlers[i]=new CatchHandler(rel,signed,typed,catchAll);
  }
  for(TryItem t:tries)if(!starts.contains(t.handlerOffset))
   throw new CodeException("try_handler_offset_invalid");
  return new HandlerList(handlers,c.p);
 }
 private static EncodedField[] fields(byte[] b,Cursor c,int count,int max)throws CodeException{
  EncodedField[] out=new EncodedField[count];long index=0;
  for(int i=0;i<count;i++){
   long delta=uleb(b,c);if(i>0&&delta==0)throw new CodeException("duplicate_encoded_field_index");
   index+=delta;if(index>=max)throw new CodeException("encoded_field_index_out_of_range:index="+index+",max="+max+",delta="+delta+",position="+i);
   out[i]=new EncodedField((int)index,ulebInt(b,c));
  }return out;
 }
 private static EncodedMethod[] methods(byte[] b,Cursor c,int count,int max,int dataOff,int dataSize,int fieldCount,int methodCount,int typeCount)throws CodeException{
  EncodedMethod[] out=new EncodedMethod[count];long index=0;
  for(int i=0;i<count;i++){
   long delta=uleb(b,c);if(i>0&&delta==0)throw new CodeException("duplicate_encoded_method_index");
   index+=delta;if(index>=max)throw new CodeException("encoded_method_index_out_of_range");
   int access=ulebInt(b,c),code=ulebInt(b,c);
   CodeItem item=null;if(code!=0){if(code<dataOff||code>=dataOff+dataSize||(code&3)!=0)throw new CodeException("encoded_method_code_offset_out_of_data");item=readCodeItem(b,dataOff,dataSize,code,typeCount);}
   out[i]=new EncodedMethod((int)index,access,code,item);
  }return out;
 }
 private static int count(byte[] b,Cursor c)throws CodeException{
  long n=uleb(b,c);if(n>Integer.MAX_VALUE||n>c.limit-c.p)throw new CodeException("encoded_item_count_invalid");return (int)n;
 }
 private static int ulebInt(byte[] b,Cursor c)throws CodeException{
  long n=uleb(b,c);if(n>Integer.MAX_VALUE)throw new CodeException("uleb_signed_overflow");return (int)n;
 }
 private static long uleb(byte[] b,Cursor c)throws CodeException{
  long value=0;
  for(int i=0;i<5;i++){
   if(c.p>=c.limit)throw new CodeException("uleb_truncated");
   int x=b[c.p++]&255;if(i==4&&(x&0xf0)!=0)throw new CodeException("uleb_overflow");
   value|=((long)(x&127))<<(i*7);if((x&128)==0)return value;
  }throw new CodeException("uleb_too_long");
 }
 private static int sleb(byte[] b,Cursor c)throws CodeException{
  long value=0;int shift=0,x;
  for(int i=0;i<5;i++){
   if(c.p>=c.limit)throw new CodeException("sleb_truncated");
   x=b[c.p++]&255;value|=((long)(x&127))<<shift;shift+=7;
   if((x&128)==0){if(shift<64&&(x&64)!=0)value|=(-1L)<<shift;
    if(value<Integer.MIN_VALUE||value>Integer.MAX_VALUE)throw new CodeException("sleb_overflow");return (int)value;}
  }throw new CodeException("sleb_too_long");
 }
 private static final class Cursor{int p;final int limit;Cursor(int p,int l){this.p=p;limit=l;}}
 private static int dataLimit(byte[] b,int off,int size)throws CodeException{
  if(b==null||off<0||size<0||off>b.length||(long)off+size>b.length)throw new CodeException("data_region_invalid");return off+size;
 }
 private static int u16(byte[] b,int p)throws CodeException{
  if(p<0||p>b.length-2)throw new CodeException("u16_truncated");return (b[p]&255)|((b[p+1]&255)<<8);
 }
 private static long u32(byte[] b,int p)throws CodeException{
  if(p<0||p>b.length-4)throw new CodeException("u32_truncated");
  return ((long)b[p]&255)|(((long)b[p+1]&255)<<8)|(((long)b[p+2]&255)<<16)|(((long)b[p+3]&255)<<24);
 }
}

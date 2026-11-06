package ds.dsh.DeepSeek;

/** Bounds-checked decoder for the DEX instruction subset used by ROI analysis. */
public final class RoiDexInstructionDecoder {
    private RoiDexInstructionDecoder() {}
    public static final class Decoded {
        public enum ReferenceKind { NONE, STRING, TYPE, FIELD, METHOD }
        public final int offset, opcode, width, referenceIndex;
        public final ReferenceKind referenceKind;
        /** Number of destination register words defined by move-result; zero for other opcodes. */
        public final int resultWordCount;
        public final long literalValue;
        public final boolean hasLiteral;
        /** True only for move-exception (0x0d). */
        public final boolean moveException;
        public final int[] registers;
        private Decoded(int o,int p,int w,int[] r,int ref,int resultWords,boolean exception,
                ReferenceKind kind,long literal,boolean literalPresent){
            offset=o;opcode=p;width=w;registers=r;referenceIndex=ref;
            resultWordCount=resultWords;moveException=exception;referenceKind=kind;
            literalValue=literal;hasLiteral=literalPresent;
        }
    }
    public static final class DecodeException extends Exception {
        public DecodeException(String message){super(message);}
    }
    private static final class TypeInfo {
        final int next, words;
        TypeInfo(int next,int words){this.next=next;this.words=words;}
    }
    public static Decoded decode(short[] code,int offset,int registerCount)throws DecodeException{
        if(code==null||offset<0||offset>=code.length||registerCount<0)throw new DecodeException("invalid_input");
        int first=u(code[offset]),op=first&255,width,ref=-1,resultWords=0;
        boolean moveException=false,hasLiteral=false; long literalValue=0L;
        int[] regs=new int[0]; ReferenceKind referenceKind=ReferenceKind.NONE;
        switch(op){
            case 0x01: case 0x04: case 0x07:
                width=1; regs=checked(new int[]{(first>>>8)&15,(first>>>12)&15},registerCount); break;
            case 0x02: case 0x05: case 0x08:
                width=2; require(code,offset,width); regs=checked(new int[]{(first>>>8)&255,u(code[offset+1])},registerCount); break;
            case 0x03: case 0x06: case 0x09:
                width=3; require(code,offset,width); regs=checked(new int[]{u(code[offset+1]),u(code[offset+2])},registerCount); break;
            case 0x0a: case 0x0c:
                width=1; resultWords=1;
                regs=checked(new int[]{(first>>>8)&255},registerCount); break;
            case 0x0b: {
                width=1; resultWords=2;
                int wideResult=(first>>>8)&255;
                if(wideResult+1>=registerCount)throw new DecodeException("wide_result_register_out_of_bounds");
                regs=checked(new int[]{wideResult},registerCount); break;
            }
            case 0x0d:
                width=1; moveException=true;
                regs=checked(new int[]{(first>>>8)&255},registerCount); break;
            case 0x12:
                width=1; regs=checked(new int[]{(first>>>8)&15},registerCount);
                int literal4=(first>>>12)&15;literalValue=literal4>=8?literal4-16:literal4;
                hasLiteral=true;break;
            case 0x13:
                width=2;require(code,offset,width);regs=checked(new int[]{(first>>>8)&255},registerCount);
                literalValue=(short)u(code[offset+1]);hasLiteral=true;break;
            case 0x14:
                width=3;require(code,offset,width);regs=checked(new int[]{(first>>>8)&255},registerCount);
                literalValue=(int)(u(code[offset+1])|(u(code[offset+2])<<16));hasLiteral=true;break;
            case 0x15:
                width=2;require(code,offset,width);regs=checked(new int[]{(first>>>8)&255},registerCount);
                literalValue=((long)(short)u(code[offset+1]))<<16;hasLiteral=true;break;
            case 0x1a:
                width=2; require(code,offset,width); regs=checked(new int[]{(first>>>8)&255},registerCount);
                ref=u(code[offset+1]); referenceKind=ReferenceKind.STRING; break;
            case 0x1b:
                width=3; require(code,offset,width); regs=checked(new int[]{(first>>>8)&255},registerCount);
                ref=u(code[offset+1])|(u(code[offset+2])<<16); referenceKind=ReferenceKind.STRING; break;
            case 0x1c: case 0x1f:
                width=2; require(code,offset,width); regs=checked(new int[]{(first>>>8)&255},registerCount);
                ref=u(code[offset+1]); referenceKind=ReferenceKind.TYPE; break;
            case 0x54: case 0x5b:
                width=2; require(code,offset,width);
                regs=checked(new int[]{(first>>>8)&15,(first>>>12)&15},registerCount);
                ref=u(code[offset+1]); referenceKind=ReferenceKind.FIELD; break;
            case 0x6e: case 0x6f: case 0x70: case 0x71: case 0x72:
                width=3; require(code,offset,width);
                int count=(first>>>8)&15, packed=u(code[offset+2]);
                if(count>5)throw new DecodeException("invoke_35c_argument_count");
                int[] all={packed&15,(packed>>>4)&15,(packed>>>8)&15,(packed>>>12)&15,(first>>>12)&15};
                regs=new int[count]; System.arraycopy(all,0,regs,0,count); regs=checked(regs,registerCount);
                ref=u(code[offset+1]); referenceKind=ReferenceKind.METHOD; break;
            case 0x74: case 0x75: case 0x76: case 0x77: case 0x78:
                width=3; require(code,offset,width);
                int rangeCount=(first>>>8)&255, start=u(code[offset+2]);
                if(start+rangeCount>registerCount)throw new DecodeException("invoke_3rc_register_range");
                regs=new int[rangeCount]; for(int i=0;i<rangeCount;i++)regs[i]=start+i;
                ref=u(code[offset+1]); referenceKind=ReferenceKind.METHOD; break;
            default: throw new DecodeException("unsupported_opcode_0x"+Integer.toHexString(op));
        }
        return new Decoded(offset,op,width,regs,ref,resultWords,moveException,referenceKind,
                literalValue,hasLiteral);
    }

    /**
     * Maps an invoke register-word position to a reflected parameter index.
     * The receiver (when present) maps to -1. The entire method descriptor is
     * validated, including its return type and absence of trailing garbage.
     */
    public enum RegisterPositionKind { RECEIVER, PARAMETER, INVALID }

    /** Unambiguous mapping of an invoke register-word position. */
    public static final class RegisterPosition {
        public final RegisterPositionKind kind;
        /** -1 unless kind is PARAMETER. Wide second words map to the same parameter index. */
        public final int parameterIndex;
        private RegisterPosition(RegisterPositionKind k,int p){kind=k;parameterIndex=p;}
    }

    public static RegisterPosition registerPositionFor(String descriptor,boolean isStatic,int position)
            throws DecodeException {
        if(descriptor==null||descriptor.length()<3||descriptor.charAt(0)!='('||position<0)
            throw new DecodeException("invalid_descriptor_or_position");
        int cursor=1,word=isStatic?0:1,parameter=0,mapped=-1;
        while(cursor<descriptor.length()&&descriptor.charAt(cursor)!=')'){
            TypeInfo type=parseType(descriptor,cursor,false);
            if(position>=word&&position<word+type.words)mapped=parameter;
            word+=type.words;cursor=type.next;parameter++;
        }
        if(cursor>=descriptor.length()||descriptor.charAt(cursor)!=')')
            throw new DecodeException("missing_descriptor_close");
        cursor++;
        TypeInfo result=parseType(descriptor,cursor,true);
        if(result.next!=descriptor.length())throw new DecodeException("trailing_descriptor_data");
        if(!isStatic&&position==0)
            return new RegisterPosition(RegisterPositionKind.RECEIVER,-1);
        if(mapped>=0)return new RegisterPosition(RegisterPositionKind.PARAMETER,mapped);
        return new RegisterPosition(RegisterPositionKind.INVALID,-1);
    }

    /** @deprecated Use registerPositionFor to distinguish receiver from invalid positions. */
    @Deprecated
    public static int parameterIndexForRegisterPosition(String descriptor,boolean isStatic,int position)
            throws DecodeException {
        RegisterPosition mapped=registerPositionFor(descriptor,isStatic,position);
        return mapped.kind==RegisterPositionKind.PARAMETER?mapped.parameterIndex:-1;
    }

    /** Register-word offsets for each declared parameter, excluding the receiver. */
    public static int[] parameterWordWidthsFor(String descriptor)throws DecodeException {
        registerWordCountFor(descriptor,true);
        java.util.ArrayList<Integer> widths=new java.util.ArrayList<Integer>();
        int cursor=1;
        while(cursor<descriptor.length()&&descriptor.charAt(cursor)!=')') {
            TypeInfo type=parseType(descriptor,cursor,false);
            widths.add(type.words);cursor=type.next;
        }
        int[] result=new int[widths.size()];
        for(int i=0;i<result.length;i++)result[i]=widths.get(i);
        return result;
    }

    /** Absolute invoke register-word position of a zero-based parameter. */
    public static int parameterWordPositionFor(String descriptor,boolean isStatic,int parameterIndex)
            throws DecodeException {
        if(parameterIndex<0)throw new DecodeException("parameter_index_invalid");
        int[] widths=parameterWordWidthsFor(descriptor);
        if(parameterIndex>=widths.length)throw new DecodeException("parameter_index_out_of_range");
        int word=isStatic?0:1;
        for(int i=0;i<parameterIndex;i++)word+=widths[i];
        return word;
    }

    /** Exact descriptor substring for one zero-based parameter. */
    public static String parameterDescriptorAt(String descriptor,int parameterIndex)throws DecodeException {
        if(parameterIndex<0)throw new DecodeException("parameter_index_invalid");
        registerWordCountFor(descriptor,true);
        int cursor=1,index=0;
        while(cursor<descriptor.length()&&descriptor.charAt(cursor)!=')') {
            int start=cursor;TypeInfo type=parseType(descriptor,cursor,false);
            if(index==parameterIndex)return descriptor.substring(start,type.next);
            cursor=type.next;index++;
        }
        throw new DecodeException("parameter_index_out_of_range");
    }

    /** Returns the exact invoke register-word count implied by a method prototype. */
    public static int registerWordCountFor(String descriptor,boolean isStatic)throws DecodeException {
        if(descriptor==null||descriptor.length()<3||descriptor.charAt(0)!='(')
            throw new DecodeException("invalid_method_descriptor");
        int cursor=1,words=isStatic?0:1;
        while(cursor<descriptor.length()&&descriptor.charAt(cursor)!=')') {
            TypeInfo type=parseType(descriptor,cursor,false);
            words+=type.words;cursor=type.next;
        }
        if(cursor>=descriptor.length()||descriptor.charAt(cursor)!=')')
            throw new DecodeException("missing_descriptor_close");
        TypeInfo result=parseType(descriptor,cursor+1,true);
        if(result.next!=descriptor.length())throw new DecodeException("trailing_descriptor_data");
        return words;
    }

    private static TypeInfo parseType(String value,int start,boolean allowVoid)throws DecodeException{
        if(start<0||start>=value.length())throw new DecodeException("truncated_descriptor");
        int cursor=start,dimensions=0;
        while(cursor<value.length()&&value.charAt(cursor)=='['){
            dimensions++; cursor++;
            if(dimensions>255)throw new DecodeException("array_dimensions_exceeded");
        }
        if(cursor>=value.length())throw new DecodeException("truncated_descriptor");
        char type=value.charAt(cursor++);
        if(type=='V'){
            if(!allowVoid||dimensions!=0)throw new DecodeException("invalid_void_descriptor");
            return new TypeInfo(cursor,0);
        }
        if(type=='L'){
            int nameStart=cursor;
            int end=value.indexOf(';',cursor);
            if(end<0)throw new DecodeException("unterminated_object_descriptor");
            if(end==nameStart)throw new DecodeException("empty_object_descriptor");
            boolean segmentStart=true;
            for(int i=nameStart;i<end;i++){
                char c=value.charAt(i);
                if(c=='/' ){
                    if(segmentStart)throw new DecodeException("invalid_object_descriptor");
                    segmentStart=true;
                } else {
                    if(c=='.'||c=='['||c==';'||c=='('||c==')'||c=='\\')
                        throw new DecodeException("invalid_object_descriptor");
                    segmentStart=false;
                }
            }
            if(segmentStart)throw new DecodeException("invalid_object_descriptor");
            cursor=end+1;
        } else if("ZBCSIFJD".indexOf(type)<0){
            throw new DecodeException("invalid_type_descriptor");
        }
        int words=dimensions==0&&(type=='J'||type=='D')?2:1;
        return new TypeInfo(cursor,words);
    }

    private static int[] checked(int[] rs,int count)throws DecodeException{
        for(int r:rs)if(r<0||r>=count)throw new DecodeException("register_out_of_bounds");
        return rs;
    }
    private static void require(short[] code,int offset,int width)throws DecodeException{
        if(offset>code.length-width)throw new DecodeException("truncated_instruction");
    }
    private static int u(short value){return value&0xffff;}
}

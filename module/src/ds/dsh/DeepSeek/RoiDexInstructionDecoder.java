package ds.dsh.DeepSeek;

/** Bounds-checked decoder for the DEX instruction subset used by ROI analysis. */
public final class RoiDexInstructionDecoder {
    private RoiDexInstructionDecoder() {}
    public static final class Decoded {
        public final int offset, opcode, width, referenceIndex;
        public final int[] registers;
        private Decoded(int o,int p,int w,int[] r,int ref){offset=o;opcode=p;width=w;registers=r;referenceIndex=ref;}
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
        int first=u(code[offset]),op=first&255,width,ref=-1; int[] regs=new int[0];
        switch(op){
            case 0x01: case 0x04: case 0x07:
                width=1; regs=checked(new int[]{(first>>>8)&15,(first>>>12)&15},registerCount); break;
            case 0x02: case 0x05: case 0x08:
                width=2; require(code,offset,width); regs=checked(new int[]{(first>>>8)&255,u(code[offset+1])},registerCount); break;
            case 0x03: case 0x06: case 0x09:
                width=3; require(code,offset,width); regs=checked(new int[]{u(code[offset+1]),u(code[offset+2])},registerCount); break;
            case 0x0c:
                width=1; regs=checked(new int[]{(first>>>8)&255},registerCount); break;
            case 0x1f: case 0x1a:
                width=2; require(code,offset,width); regs=checked(new int[]{(first>>>8)&255},registerCount); ref=u(code[offset+1]); break;
            case 0x54: case 0x5b:
                width=2; require(code,offset,width);
                regs=checked(new int[]{(first>>>8)&15,(first>>>12)&15},registerCount); ref=u(code[offset+1]); break;
            case 0x6e: case 0x6f: case 0x70: case 0x71: case 0x72:
                width=3; require(code,offset,width);
                int count=(first>>>8)&15, packed=u(code[offset+2]);
                if(count>5)throw new DecodeException("invoke_35c_argument_count");
                int[] all={packed&15,(packed>>>4)&15,(packed>>>8)&15,(packed>>>12)&15,(first>>>12)&15};
                regs=new int[count]; System.arraycopy(all,0,regs,0,count); regs=checked(regs,registerCount);
                ref=u(code[offset+1]); break;
            case 0x74: case 0x75: case 0x76: case 0x77: case 0x78:
                width=3; require(code,offset,width);
                int rangeCount=(first>>>8)&255, start=u(code[offset+2]);
                if(start+rangeCount>registerCount)throw new DecodeException("invoke_3rc_register_range");
                regs=new int[rangeCount]; for(int i=0;i<rangeCount;i++)regs[i]=start+i;
                ref=u(code[offset+1]); break;
            default: throw new DecodeException("unsupported_opcode_0x"+Integer.toHexString(op));
        }
        return new Decoded(offset,op,width,regs,ref);
    }

    /**
     * Maps an invoke register-word position to a reflected parameter index.
     * The receiver (when present) maps to -1. The entire method descriptor is
     * validated, including its return type and absence of trailing garbage.
     */
    public static int parameterIndexForRegisterPosition(String descriptor,boolean isStatic,int position)throws DecodeException{
        if(descriptor==null||descriptor.length()<3||descriptor.charAt(0)!='('||position<0)
            throw new DecodeException("invalid_descriptor_or_position");
        int cursor=1,word=isStatic?0:1,parameter=0;
        if(!isStatic&&position==0) {
            // Still validate the descriptor before returning a receiver mapping.
        }
        int mapped=-1;
        while(cursor<descriptor.length()&&descriptor.charAt(cursor)!=')'){
            TypeInfo type=parseType(descriptor,cursor,false);
            if(position>=word&&position<word+type.words)mapped=parameter;
            word+=type.words;
            cursor=type.next;
            parameter++;
        }
        if(cursor>=descriptor.length()||descriptor.charAt(cursor)!=')')
            throw new DecodeException("missing_descriptor_close");
        cursor++;
        TypeInfo result=parseType(descriptor,cursor,true);
        if(result.next!=descriptor.length())throw new DecodeException("trailing_descriptor_data");
        if(!isStatic&&position==0)return -1;
        return mapped;
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

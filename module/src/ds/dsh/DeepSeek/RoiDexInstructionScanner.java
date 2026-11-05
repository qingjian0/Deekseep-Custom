package ds.dsh.DeepSeek;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Structural DEX 035/038 instruction scanner and conservative CFG builder.
 * Width recognition is deliberately separate from semantic decoding.
 */
public final class RoiDexInstructionScanner {
    private RoiDexInstructionScanner() {}

    public static final class ScanException extends Exception {
        public ScanException(String message) { super(message); }
    }
    public static final class Instruction {
        public final int offset, opcode, width;
        public final boolean payload;
        public final int payloadKind;
        private final int[] targets;
        Instruction(int o, int op, int w, boolean p, int k, int[] t) {
            offset=o; opcode=op; width=w; payload=p; payloadKind=k; targets=t;
        }
        public int[] branchTargets() { return targets.clone(); }
    }
    public static final class ScanResult {
        public final Instruction[] instructions;
        public final int codeUnits;
        private final short[] code;
        private final Map<Integer,Instruction> byOffset;
        ScanResult(List<Instruction> list,short[] source) {
            instructions=list.toArray(new Instruction[0]); codeUnits=source.length; code=source.clone();
            Map<Integer,Instruction> m=new LinkedHashMap<Integer,Instruction>();
            for(Instruction i:instructions)m.put(i.offset,i);
            byOffset=Collections.unmodifiableMap(m);
        }
        public Instruction at(int offset) { return byOffset.get(offset); }
        public boolean isInstructionBoundary(int offset) {
            Instruction i=byOffset.get(offset); return i!=null&&!i.payload;
        }
        public boolean isPayloadStart(int offset) {
            Instruction i=byOffset.get(offset); return i!=null&&i.payload;
        }
    }
    public static final class BasicBlock {
        public final int startOffset, endOffset;
        public final Instruction[] instructions;
        BasicBlock(int s,int e,List<Instruction> body) {
            startOffset=s;endOffset=e;instructions=body.toArray(new Instruction[0]);
        }
    }
    public static final class Edge {
        public final int fromBlock,toBlock;
        public final String kind;
        Edge(int f,int t,String k){fromBlock=f;toBlock=t;kind=k;}
    }
    public static final class ControlFlowGraph {
        public final ScanResult scan;
        public final BasicBlock[] blocks;
        public final Edge[] edges;
        ControlFlowGraph(ScanResult s,List<BasicBlock>b,List<Edge>e){
            scan=s;blocks=b.toArray(new BasicBlock[0]);edges=e.toArray(new Edge[0]);
        }
    }

    /** Scans every code unit, including payload extents, and validates all control targets. */
    public static ScanResult scan(short[] code) throws ScanException {
        if(code==null)throw new ScanException("null_code");
        List<Instruction> list=new ArrayList<Instruction>();
        Map<Integer,Instruction> byOffset=new LinkedHashMap<Integer,Instruction>();
        int p=0;
        while(p<code.length) {
            int word=u(code[p]),op=word&255,width,kind=0;
            boolean payload=false;
            if(op==0 && (word>>>8)!=0) {
                if((p&1)!=0)throw new ScanException("unaligned_payload:"+p);
                kind=word>>>8;payload=true;
                width=payloadWidth(code,p,kind);
            } else {
                width=opcodeWidth(op);
                if(width==0)throw new ScanException("reserved_or_unused_opcode_0x"+hex(op)+":"+p);
                if(p>code.length-width)throw new ScanException("truncated_instruction:"+p);
            }
            int[] targets=payload?new int[0]:branchTargets(code,p,op,width);
            Instruction ins=new Instruction(p,op,width,payload,kind,targets);
            list.add(ins);byOffset.put(p,ins);p+=width;
        }
        if(p!=code.length)throw new ScanException("instruction_extent_mismatch");
        ScanResult result=new ScanResult(list,code);
        validatePayloadReferences(code,result);
        validateBranchTargets(result);
        return result;
    }

    public static ControlFlowGraph buildControlFlowGraph(short[] code) throws ScanException {
        ScanResult scan=scan(code);
        if(code.length==0)return new ControlFlowGraph(scan,new ArrayList<BasicBlock>(),new ArrayList<Edge>());
        Set<Integer> starts=new LinkedHashSet<Integer>();
        starts.add(0);
        for(Instruction i:scan.instructions) {
            if(i.payload)continue;
            int next=i.offset+i.width;
            for(int target:i.targets)starts.add(target);
            if(isConditional(i.opcode)||isGoto(i.opcode)||isSwitch(i.opcode)||isTerminal(i.opcode)) {
                if(next<code.length && scan.isInstructionBoundary(next))starts.add(next);
            }
            if(isSwitch(i.opcode)) {
                int payloadOffset=checkedTarget(i.offset,s32(code,i.offset+1));
                Instruction payload=scan.at(payloadOffset);
                for(int target:payloadTargets(code,payloadOffset,payload.payloadKind,i.offset))starts.add(target);
            }
        }
        List<Integer> ordered=new ArrayList<Integer>(starts);
        Collections.sort(ordered);
        Map<Integer,Integer> blockAt=new LinkedHashMap<Integer,Integer>();
        List<BasicBlock> blocks=new ArrayList<BasicBlock>();
        for(int n=0;n<ordered.size();n++) {
            int start=ordered.get(n);
            if(!scan.isInstructionBoundary(start))throw new ScanException("block_starts_outside_instruction:"+start);
            int end=n+1<ordered.size()?ordered.get(n+1):code.length;
            List<Instruction> body=new ArrayList<Instruction>();
            int p=start;
            while(p<end) {
                Instruction ins=scan.at(p);
                if(ins==null||ins.payload||p+ins.width>end)throw new ScanException("invalid_block_extent:"+p);
                body.add(ins);p+=ins.width;
            }
            if(body.isEmpty())throw new ScanException("empty_basic_block:"+start);
            blockAt.put(start,blocks.size());blocks.add(new BasicBlock(start,end,body));
        }
        List<Edge> edges=new ArrayList<Edge>();
        for(int bi=0;bi<blocks.size();bi++) {
            BasicBlock b=blocks.get(bi);Instruction last=b.instructions[b.instructions.length-1];
            int next=last.offset+last.width;
            if(isGoto(last.opcode)) {
                addEdge(edges,bi,blockAt,last.targets[0],"GOTO");
            } else if(isConditional(last.opcode)) {
                addEdge(edges,bi,blockAt,last.targets[0],"CONDITIONAL_TRUE");
                addFallthrough(edges,bi,next,blockAt,"CONDITIONAL_FALSE");
            } else if(isSwitch(last.opcode)) {
                int payloadOffset=checkedTarget(last.offset,s32(code,last.offset+1));
                Instruction payload=scan.at(payloadOffset);
                int[] targets=payloadTargets(code,payloadOffset,payload.payloadKind,last.offset);
                for(int target:targets)addEdge(edges,bi,blockAt,target,"SWITCH");
                addFallthrough(edges,bi,next,blockAt,"SWITCH_DEFAULT");
            } else if(!isTerminal(last.opcode)) {
                addFallthrough(edges,bi,next,blockAt,"FALLTHROUGH");
            }
        }
        return new ControlFlowGraph(scan,blocks,edges);
    }

    private static void addFallthrough(List<Edge> e,int from,int target,Map<Integer,Integer> blocks,String kind)throws ScanException {
        Integer to=blocks.get(target);
        if(to!=null)e.add(new Edge(from,to,kind));
        else if(target<blocks.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1))
            throw new ScanException("fallthrough_not_block_boundary:"+target);
    }
    private static void addEdge(List<Edge> e,int from,Map<Integer,Integer> blocks,int target,String kind)throws ScanException {
        Integer to=blocks.get(target);
        if(to==null)throw new ScanException("cfg_target_missing:"+target);
        e.add(new Edge(from,to,kind));
    }

    private static int opcodeWidth(int op) {
        if(op==0x00)return 1;
        if(op>=0x01&&op<=0x09)return (op==0x02||op==0x05||op==0x08)?2:(op==0x03||op==0x06||op==0x09)?3:1;
        if(op>=0x0a&&op<=0x12)return 1;
        if(op==0x13||op==0x15||op==0x16||op==0x19||op==0x1a||op==0x1c||op==0x1f||op==0x20||op==0x22||op==0x23)return 2;
        if(op==0x14||op==0x17||op==0x1b||op==0x24||op==0x25||op==0x26||op==0x2a||op==0x2b||op==0x2c)return 3;
        if(op==0x18)return 5;
        if(op==0x1d||op==0x1e||op==0x21||op==0x27||op==0x28)return 1;
        if(op==0x29)return 2;
        if(op>=0x2d&&op<=0x3d)return 2;
        if(op>=0x44&&op<=0x6d)return 2;
        if(op>=0x6e&&op<=0x72)return 3;
        if(op>=0x74&&op<=0x78)return 3;
        if(op>=0x7b&&op<=0x8f)return 1;
        if(op>=0x90&&op<=0xaf)return 2;
        if(op>=0xb0&&op<=0xcf)return 1;
        if(op>=0xd0&&op<=0xe2)return 2;
        if(op==0xfa||op==0xfb)return 4;
        if(op==0xfc||op==0xfd)return 3;
        if(op==0xfe||op==0xff)return 2;
        return 0;
    }
    private static int payloadWidth(short[] c,int p,int kind)throws ScanException {
        if(kind==1) {
            require(c,p,4);int size=u(c[p+1]);
            long width=4L+2L*size;
            if(width>Integer.MAX_VALUE||width>c.length-p)throw new ScanException("packed_switch_payload_truncated:"+p);
            return (int)width;
        }
        if(kind==2) {
            require(c,p,2);int size=u(c[p+1]);long width=2L+4L*size;
            if(width>Integer.MAX_VALUE||width>c.length-p)throw new ScanException("sparse_switch_payload_truncated:"+p);
            int previous=0;
            for(int i=0;i<size;i++) {
                int key=s32(c,p+2+i*2);
                if(i>0&&key<=previous)throw new ScanException("sparse_switch_keys_not_strictly_increasing:"+p);
                previous=key;
            }
            return (int)width;
        }
        if(kind==3) {
            require(c,p,4);int elementWidth=u(c[p+1]);long size=u32(c,p+2);
            if(elementWidth!=1&&elementWidth!=2&&elementWidth!=4&&elementWidth!=8)
                throw new ScanException("array_payload_element_width:"+p);
            long bytes=size*elementWidth;
            if(size!=0&&bytes/size!=elementWidth)throw new ScanException("array_payload_size_overflow:"+p);
            long width=4L+(bytes+1L)/2L;
            if(width>Integer.MAX_VALUE||width>c.length-p)throw new ScanException("array_payload_truncated:"+p);
            return (int)width;
        }
        throw new ScanException("unknown_payload_ident:"+kind+":"+p);
    }
    private static void validatePayloadReferences(short[] c,ScanResult s)throws ScanException {
        for(Instruction i:s.instructions) {
            if(i.payload)continue;
            int op=i.opcode;
            if(op==0x26||op==0x2b||op==0x2c) {
                int target=checkedTarget(i.offset,s32(c,i.offset+1));Instruction p=s.at(target);
                int expected=op==0x2b?1:op==0x2c?2:3;
                if(p==null||!p.payload||p.payloadKind!=expected)
                    throw new ScanException("payload_reference_mismatch:"+i.offset);
                if((target&1)!=0)throw new ScanException("payload_reference_unaligned:"+target);
            }
        }
    }
    private static void validateBranchTargets(ScanResult s)throws ScanException {
        for(Instruction i:s.instructions)if(!i.payload) {
            for(int target:i.targets)validateTarget(s,i.offset,target);
            if(isSwitch(i.opcode)) {
                int payloadOffset=checkedTarget(i.offset,s32(s.code,i.offset+1));
                Instruction payload=s.at(payloadOffset);
                if(payload==null||!payload.payload)throw new ScanException("switch_payload_missing:"+i.offset);
                for(int target:payloadTargets(s.code,payloadOffset,payload.payloadKind,i.offset))
                    validateTarget(s,i.offset,target);
            }
        }
    }
    private static void validateTarget(ScanResult s,int source,int target)throws ScanException {
        if(target<0||target>=s.codeUnits||!s.isInstructionBoundary(target))
            throw new ScanException("branch_target_not_instruction_boundary:"+source+"->"+target);
    }
    private static int checkedTarget(int base,int relative)throws ScanException {
        long target=(long)base+(long)relative;
        if(target<Integer.MIN_VALUE||target>Integer.MAX_VALUE)throw new ScanException("branch_target_overflow:"+base);
        return (int)target;
    }
    private static int[] branchTargets(short[] c,int p,int op,int width)throws ScanException {
        if(op==0x28)return new int[]{checkedTarget(p,(byte)(u(c[p])>>>8))};
        if(op==0x29)return new int[]{checkedTarget(p,(short)u(c[p+1]))};
        if(op==0x2a)return new int[]{checkedTarget(p,s32(c,p+1))};
        if(op>=0x32&&op<=0x3d)return new int[]{checkedTarget(p,(short)u(c[p+1]))};
        return new int[0];
    }
    private static int[] payloadTargets(short[] c,int p,int kind,int switchOffset)throws ScanException {
        if(kind==1) {
            int n=u(c[p+1]);int[] out=new int[n];
            for(int i=0;i<n;i++)out[i]=checkedTarget(switchOffset,s32(c,p+4+i*2));
            return out;
        }
        if(kind==2) {
            int n=u(c[p+1]);int[] out=new int[n];
            int targets=p+2+n*2;
            for(int i=0;i<n;i++)out[i]=checkedTarget(switchOffset,s32(c,targets+i*2));
            return out;
        }
        throw new ScanException("not_switch_payload:"+kind);
    }
    private static boolean isGoto(int op){return op>=0x28&&op<=0x2a;}
    private static boolean isConditional(int op){return op>=0x32&&op<=0x3d;}
    private static boolean isSwitch(int op){return op==0x2b||op==0x2c;}
    private static boolean isTerminal(int op){return (op>=0x0e&&op<=0x11)||op==0x27;}
    private static void require(short[] c,int p,int n)throws ScanException{
        if(p<0||p>c.length-n)throw new ScanException("truncated_payload_header:"+p);
    }
    private static int s32(short[] c,int p)throws ScanException {
        require(c,p,2);return u(c[p])|(u(c[p+1])<<16);
    }
    private static long u32(short[] c,int p)throws ScanException {
        require(c,p,2);return ((long)u(c[p]))|(((long)u(c[p+1]))<<16);
    }
    private static int u(short x){return x&0xffff;}
    private static String hex(int n){return Integer.toHexString(n);}
}

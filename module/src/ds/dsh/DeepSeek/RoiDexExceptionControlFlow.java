package ds.dsh.DeepSeek;

import java.util.ArrayList;
import java.util.List;

/**
 * Conservative exception CFG overlay for parsed DEX code_item metadata.
 * Every basic block intersecting a protected try range is connected to each
 * declared typed/catch-all handler. This intentionally over-approximates throws.
 */
public final class RoiDexExceptionControlFlow {
    private RoiDexExceptionControlFlow() {}

    public static final class AnalysisException extends Exception {
        public AnalysisException(String message) { super(message); }
    }

    public static final class ExceptionEdge {
        public final int fromBlock, toBlock, tryIndex, catchTypeIndex, handlerAddress;
        public final boolean catchAll;
        ExceptionEdge(int from,int to,int ti,int type,int address,boolean all) {
            fromBlock=from;toBlock=to;tryIndex=ti;catchTypeIndex=type;
            handlerAddress=address;catchAll=all;
        }
    }

    public static final class Result {
        public final RoiDexInstructionScanner.ControlFlowGraph normalGraph;
        public final ExceptionEdge[] exceptionEdges;
        public final boolean exceptionEdgesIncluded;
        Result(RoiDexInstructionScanner.ControlFlowGraph g,List<ExceptionEdge> e) {
            normalGraph=g;exceptionEdges=e.toArray(new ExceptionEdge[0]);
            exceptionEdgesIncluded=true;
        }
    }

    public static Result analyze(RoiDexCodeReader.CodeItem code)
            throws AnalysisException,RoiDexInstructionScanner.ScanException {
        if(code==null)throw new AnalysisException("null_code_item");
        RoiDexInstructionScanner.ControlFlowGraph graph=
                RoiDexInstructionScanner.buildControlFlowGraph(code.instructions);
        List<ExceptionEdge> edges=new ArrayList<ExceptionEdge>();
        if(code.triesSize==0) {
            if(code.tryItems.length!=0||code.catchHandlers.length!=0)
                throw new AnalysisException("exception_metadata_inconsistent");
            return new Result(graph,edges);
        }
        if(code.tryItems.length!=code.triesSize||code.catchHandlers.length==0)
            throw new AnalysisException("exception_metadata_incomplete");
        for(int ti=0;ti<code.tryItems.length;ti++) {
            RoiDexCodeReader.TryItem t=code.tryItems[ti];
            long start=t.startAddress,end=start+t.instructionCount;
            if(start<0||end>code.instructions.length||start>=end)
                throw new AnalysisException("try_range_out_of_code");
            if(!graph.scan.isInstructionBoundary((int)start)
                    ||(end<code.instructions.length&&!graph.scan.isInstructionBoundary((int)end)))
                throw new AnalysisException("try_range_not_instruction_aligned");
            RoiDexCodeReader.CatchHandler handler=findHandler(code.catchHandlers,t.handlerOffset);
            if(handler==null)throw new AnalysisException("try_handler_not_resolved");
            int handlerCount=handler.typedCatches.length+(handler.catchAllAddress>=0?1:0);
            if(handlerCount==0)throw new AnalysisException("empty_catch_handler");
            for(RoiDexCodeReader.TypedCatch typed:handler.typedCatches)
                addEdgesForTarget(graph,edges,ti,(int)start,(int)end,typed.typeIndex,
                        typed.address,false);
            if(handler.catchAllAddress>=0)
                addEdgesForTarget(graph,edges,ti,(int)start,(int)end,-1,
                        handler.catchAllAddress,true);
        }
        return new Result(graph,edges);
    }

    private static RoiDexCodeReader.CatchHandler findHandler(
            RoiDexCodeReader.CatchHandler[] handlers,int offset) {
        for(RoiDexCodeReader.CatchHandler h:handlers)
            if(h.relativeOffset==offset)return h;
        return null;
    }

    private static void addEdgesForTarget(RoiDexInstructionScanner.ControlFlowGraph graph,
            List<ExceptionEdge> out,int tryIndex,int start,int end,int type,int target,
            boolean catchAll)throws AnalysisException {
        if(!graph.scan.isInstructionBoundary(target))
            throw new AnalysisException("handler_target_not_instruction_boundary:"+target);
        int targetBlock=blockAt(graph,target);
        if(targetBlock<0)throw new AnalysisException("handler_target_block_missing:"+target);
        for(int bi=0;bi<graph.blocks.length;bi++) {
            RoiDexInstructionScanner.BasicBlock block=graph.blocks[bi];
            if(block.startOffset<end&&block.endOffset>start)
                out.add(new ExceptionEdge(bi,targetBlock,tryIndex,type,target,catchAll));
        }
    }

    private static int blockAt(RoiDexInstructionScanner.ControlFlowGraph graph,int offset) {
        for(int i=0;i<graph.blocks.length;i++)
            if(graph.blocks[i].startOffset==offset)return i;
        return -1;
    }
}

package ds.dsh.DeepSeek;
import java.util.Arrays;
import java.util.Collections;
public final class RoiDexInvokeReachabilityTest {
 public static void main(String[] args){
  int[] s={0,4,8,12},e={4,8,12,16};boolean[] r={true,true,false,true};
  RoiDexInvokeReachability.Partition p=RoiDexInvokeReachability.partition(Arrays.asList(
   new RoiDexInvokeReachability.Invocation(1,"Llive;->entry()V"),
   new RoiDexInvokeReachability.Invocation(5,"Llive;->branch()V"),
   new RoiDexInvokeReachability.Invocation(9,"Ldead;->afterReturn()V"),
   new RoiDexInvokeReachability.Invocation(13,"Lhandler;->onError()V")),s,e,r,true);
  check(p.complete,"complete");
  check(p.reachableInvokes.equals(Arrays.asList("Llive;->entry()V","Llive;->branch()V","Lhandler;->onError()V")),"live branches and conservative handler");
  check(p.unreachableInvokes.equals(Collections.singletonList("Ldead;->afterReturn()V")),"dead code separated");
  RoiDexInvokeReachability.Partition f=RoiDexInvokeReachability.partition(Collections.singletonList(new RoiDexInvokeReachability.Invocation(1,"Lx;->m()V")),s,e,r,false);
  check(!f.complete&&f.reachableInvokes.isEmpty()&&f.unreachableInvokes.isEmpty(),"analysis failure fails closed");
  RoiDexInvokeReachability.Partition m=RoiDexInvokeReachability.partition(Collections.singletonList(new RoiDexInvokeReachability.Invocation(99,"Lx;->m()V")),s,e,r,true);
  check(!m.complete&&m.reachableInvokes.isEmpty()&&m.unreachableInvokes.isEmpty(),"unmapped invoke fails closed");
  System.out.println("ROI invoke reachability regression: PASS");
 }
 private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
}

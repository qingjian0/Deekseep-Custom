package ds.dsh.DeepSeek;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
/** Partitions static invoke references by CFG block reachability; incomplete inputs fail closed. */
public final class RoiDexInvokeReachability {
 private RoiDexInvokeReachability() {}
 public static final class Invocation {
  public final int offset; public final String methodKey;
  public Invocation(int offset,String key){this.offset=offset;this.methodKey=key==null?"":key;}
 }
 public static final class Partition {
  public final List<String> reachableInvokes,unreachableInvokes; public final boolean complete;
  private Partition(List<String> r,List<String> u,boolean c){
   reachableInvokes=Collections.unmodifiableList(new ArrayList<String>(r));
   unreachableInvokes=Collections.unmodifiableList(new ArrayList<String>(u));complete=c;
  }
  public static Partition incomplete(){return new Partition(Collections.<String>emptyList(),Collections.<String>emptyList(),false);}
 }
 public static Partition partition(List<Invocation> calls,int[] starts,int[] ends,boolean[] reachable,boolean complete){
  if(!complete||calls==null||starts==null||ends==null||reachable==null||starts.length==0||starts.length!=ends.length||starts.length!=reachable.length)return Partition.incomplete();
  for(int i=0;i<starts.length;i++)if(starts[i]<0||ends[i]<=starts[i]||(i>0&&starts[i]<ends[i-1]))return Partition.incomplete();
  List<String> live=new ArrayList<String>(),dead=new ArrayList<String>();
  for(Invocation call:calls){
   if(call==null||call.offset<0||call.methodKey.length()==0)return Partition.incomplete();
   int lo=0,hi=starts.length-1,found=-1;
   while(lo<=hi){int mid=(lo+hi)>>>1;if(call.offset<starts[mid])hi=mid-1;else if(call.offset>=ends[mid])lo=mid+1;else{found=mid;break;}}
   if(found<0)return Partition.incomplete();
   if(reachable[found])live.add(call.methodKey);else dead.add(call.methodKey);
  }
  return new Partition(live,dead,true);
 }
}

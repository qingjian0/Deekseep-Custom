package ds.dsh.DeepSeek;
import java.lang.reflect.*;
import java.util.*;

/** Backend-neutral, ClassLoader-scoped Hook runtime. A production Xposed adapter must implement Backend. */
public final class RoiUniversalHookRuntime {
 private RoiUniversalHookRuntime(){}
 public enum Kind { METHOD, CONSTRUCTOR }
 public enum State { RESOLVED, INSTALLING, ACTIVE, FAILED, ROLLED_BACK, UNINSTALLED }
 public interface Callback { void before(Invocation call)throws Throwable; void after(Invocation call)throws Throwable; }
 public static final class Invocation {
  public final Member member; public final Object receiver; public final Object[] args;
  public Object result; public Throwable throwable; public boolean skipOriginal;
  public Invocation(Member m,Object r,Object[] a){member=m;receiver=r;args=a==null?new Object[0]:a;}
 }
 public static final class Spec {
  public final String id,role,className,name,fingerprint,returnType;
  public final Kind kind; public final List<String> params; public final Set<String> dependencies;
  public final int priority; public final boolean required; public final Callback callback;
  private Spec(Builder b){
   id=text(b.id,"id");role=text(b.role,"role");className=text(b.className,"className");
   kind=b.kind;name=kind==Kind.CONSTRUCTOR?"<init>":text(b.name,"method");
   fingerprint=text(b.fingerprint,"fingerprint");returnType=b.returnType==null?"":b.returnType;
   if(kind==Kind.CONSTRUCTOR&&!returnType.isEmpty())throw new IllegalArgumentException("constructor return type");
   params=Collections.unmodifiableList(new ArrayList<String>(b.params));
   dependencies=Collections.unmodifiableSet(new HashSet<String>(b.dependencies));
   priority=b.priority;required=b.required;callback=Objects.requireNonNull(b.callback,"callback");
   if(dependencies.contains(id))throw new IllegalArgumentException("self dependency");
  }
  public static Builder builder(String id){return new Builder(id);}
  public static final class Builder {
   final String id; String role="unspecified",className,name,fingerprint,returnType;
   Kind kind=Kind.METHOD; int priority; boolean required=true; Callback callback;
   List<String> params=new ArrayList<String>(); Set<String> dependencies=new HashSet<String>();
   Builder(String id){this.id=id;}
   public Builder role(String v){role=v;return this;} public Builder className(String v){className=v;return this;}
   public Builder method(String v){kind=Kind.METHOD;name=v;return this;} public Builder constructor(){kind=Kind.CONSTRUCTOR;return this;}
   public Builder params(String...v){params.clear();if(v!=null)params.addAll(Arrays.asList(v));return this;}
   public Builder returns(String v){returnType=v;return this;} public Builder fingerprint(String v){fingerprint=v;return this;}
   public Builder priority(int v){priority=v;return this;} public Builder required(boolean v){required=v;return this;}
   public Builder dependsOn(String...v){if(v!=null)dependencies.addAll(Arrays.asList(v));return this;}
   public Builder callback(Callback v){callback=v;return this;} public Spec build(){return new Spec(this);}
  }
 }
 public static final class Target { public final Spec spec; public final Class<?> owner; public final Member member;
  Target(Spec s,Class<?> c,Member m){spec=s;owner=c;member=m;} }
 public static final class ResolveException extends Exception { ResolveException(String m,Throwable t){super(m,t);} ResolveException(String m){super(m);} }
 public static final class Resolver {
  public Target resolve(Spec s,ClassLoader loader)throws ResolveException{
   if(s==null||loader==null)throw new ResolveException("spec and loader required");
   try{
    Class<?> owner=Class.forName(s.className,false,loader);Class<?>[] p=new Class<?>[s.params.size()];
    for(int i=0;i<p.length;i++)p[i]=type(s.params.get(i),loader);
    if(s.kind==Kind.CONSTRUCTOR)return new Target(s,owner,owner.getDeclaredConstructor(p));
    List<Method> found=new ArrayList<Method>();
    for(Class<?> c=owner;c!=null;c=c.getSuperclass())try{
     Method m=c.getDeclaredMethod(s.name,p);
     if(s.returnType.isEmpty()||s.returnType.equals(m.getReturnType().getName()))found.add(m);
    }catch(NoSuchMethodException ignored){}
    if(found.size()!=1)throw new ResolveException(found.isEmpty()?"exact method not found":"ambiguous inherited target");
    return new Target(s,owner,found.get(0));
   }catch(ResolveException e){throw e;}catch(Throwable e){throw new ResolveException("target resolution failed: "+s.className+"#"+s.name,e);}
  }
  private static Class<?> type(String n,ClassLoader l)throws Exception{
   if("boolean".equals(n))return boolean.class;if("byte".equals(n))return byte.class;
   if("char".equals(n))return char.class;if("short".equals(n))return short.class;
   if("int".equals(n))return int.class;if("long".equals(n))return long.class;
   if("float".equals(n))return float.class;if("double".equals(n))return double.class;
   if("void".equals(n))return void.class;
   if(n.endsWith("[]")){String b=n.substring(0,n.length()-2);if("int".equals(b))return int[].class;if("long".equals(b))return long[].class;if("boolean".equals(b))return boolean[].class;return Array.newInstance(Class.forName(b,false,l),0).getClass();}
   return Class.forName(n,false,l);
  }
 }
 public interface Handle { void unhook()throws Exception; }
 public interface Backend { Handle install(Target target,Callback callback)throws Exception; }
 public interface Health { boolean verify(Target target,Handle handle)throws Exception; }
 /** Mandatory authorization bridge; production implementations must delegate to RoiHookResolver. */
 public interface ActivationGate { boolean authorized(Spec spec,ClassLoader loader); }
 public static final class Adapters {
  private final Map<String,Set<String>> rules=new HashMap<String,Set<String>>();
  public synchronized void approve(String fp,Set<String> roles){if(roles==null||roles.isEmpty())throw new IllegalArgumentException("roles required");rules.put(text(fp,"fingerprint"),Collections.unmodifiableSet(new HashSet<String>(roles)));}
  public synchronized boolean allows(String fp,String role){Set<String>s=rules.get(fp);return s!=null&&s.contains(role);}
 }
 public static final class Result {
  public final boolean success;public final String failedId,message;public final Map<String,State> states;
  Result(boolean ok,String id,String msg,Map<String,State>s){success=ok;failedId=id;message=msg;states=Collections.unmodifiableMap(new LinkedHashMap<String,State>(s));}
 }
 private static final class Active {final Target target;final Handle handle;Active(Target t,Handle h){target=t;handle=h;}}
 public static final class Installer {
  private final Resolver resolver;private final Backend backend;private final Health health;private final ActivationGate gate;
  private final Map<String,Active> active=new LinkedHashMap<String,Active>();private final Map<String,State> states=new LinkedHashMap<String,State>();
  public Installer(Resolver r,Backend b,Health h,ActivationGate g){resolver=Objects.requireNonNull(r);backend=Objects.requireNonNull(b);health=Objects.requireNonNull(h);gate=Objects.requireNonNull(g,"activation gate is mandatory");}
  public synchronized Result install(List<Spec> specs,ClassLoader loader,String fp,Adapters adapters){
   if(specs==null||specs.isEmpty())return result(false,null,"empty batch");
   Map<String,Spec> byId=new LinkedHashMap<String,Spec>();
   for(Spec s:specs){if(s==null||byId.put(s.id,s)!=null)return result(false,s==null?null:s.id,"null or duplicate id");
    if(fp==null||!fp.equals(s.fingerprint)||adapters==null||!adapters.allows(fp,s.role))return result(false,s.id,"fingerprint/role not approved");
    if(!gate.authorized(s,loader))return result(false,s.id,"evidence resolver denied activation");}
   for(Spec s:byId.values())for(String d:s.dependencies)if(!byId.containsKey(d)&&!active.containsKey(d))return result(false,s.id,"missing dependency "+d);
   List<Spec> ordered;try{ordered=order(byId);}catch(Exception e){return result(false,null,e.getMessage());}
   List<Target> targets=new ArrayList<Target>();
   try{for(Spec s:ordered){Active old=active.get(s.id);if(old!=null){if(!same(old.target.spec,s))return result(false,s.id,"id already bound to another spec");continue;}targets.add(resolver.resolve(s,loader));}}
   catch(ResolveException e){return result(false,null,e.getMessage());}
   List<Active> batch=new ArrayList<Active>();String current=null;
   try{for(Target t:targets){current=t.spec.id;for(Active a:active.values())if(a.target.member.equals(t.member))throw new IllegalStateException("target conflict with "+a.target.spec.id);
    states.put(current,State.INSTALLING);Handle h=backend.install(t,t.spec.callback);if(h==null)throw new IllegalStateException("backend returned null handle");
    Active item=new Active(t,h);batch.add(item);if(!health.verify(t,h))throw new IllegalStateException("post-install health failed");
    active.put(current,item);states.put(current,State.ACTIVE);}
    return result(true,null,"installed");
   }catch(Throwable e){for(int i=batch.size()-1;i>=0;i--){Active a=batch.get(i);active.remove(a.target.spec.id);try{a.handle.unhook();states.put(a.target.spec.id,State.ROLLED_BACK);}catch(Exception rollback){states.put(a.target.spec.id,State.FAILED);}}
    if(current!=null)states.put(current,State.FAILED);return result(false,current,e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage()));}
  }
  public synchronized boolean uninstall(String id){Active a=active.remove(id);if(a==null)return false;try{a.handle.unhook();states.put(id,State.UNINSTALLED);return true;}catch(Exception e){active.put(id,a);states.put(id,State.FAILED);return false;}}
  public synchronized void uninstallAll(){List<String> ids=new ArrayList<String>(active.keySet());Collections.reverse(ids);for(String id:ids)uninstall(id);}
  public synchronized State state(String id){return states.get(id);}public synchronized int activeCount(){return active.size();}
  private Result result(boolean b,String id,String m){return new Result(b,id,m,states);}
  private static boolean same(Spec a,Spec b){return a.kind==b.kind&&a.className.equals(b.className)&&a.name.equals(b.name)&&a.params.equals(b.params)&&a.returnType.equals(b.returnType)&&a.fingerprint.equals(b.fingerprint)&&a.role.equals(b.role)&&a.dependencies.equals(b.dependencies)&&a.priority==b.priority&&a.required==b.required&&a.callback==b.callback;}
  private static List<Spec> order(Map<String,Spec> specs){List<Spec> out=new ArrayList<Spec>();Set<String>done=new HashSet<String>();while(out.size()<specs.size()){Spec next=null;for(Spec candidate:specs.values()){if(done.contains(candidate.id))continue;boolean ready=true;for(String dep:candidate.dependencies)if(specs.containsKey(dep)&&!done.contains(dep)){ready=false;break;}if(ready&&(next==null||candidate.priority<next.priority))next=candidate;}if(next==null)throw new IllegalArgumentException("dependency cycle");out.add(next);done.add(next.id);}return out;}
 }
 private static String text(String s,String field){if(s==null||s.trim().isEmpty())throw new IllegalArgumentException(field+" required");return s.trim();}
}

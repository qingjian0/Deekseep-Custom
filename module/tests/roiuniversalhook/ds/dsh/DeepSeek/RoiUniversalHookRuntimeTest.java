package ds.dsh.DeepSeek;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
public final class RoiUniversalHookRuntimeTest {
 public static final class Sample {public static final class Nested{} public Sample(String s){} private String convert(int i){return ""+i;} public void ping(){} public int[] arrays(byte[] b,char[] c,int[][] matrix,String[][] labels){return new int[0];} public Nested[] nested(Nested[] values){return values;}}
 static void ok(boolean b,String m){if(!b)throw new AssertionError(m);}
 static RoiUniversalHookRuntime.Callback cb(){return new RoiUniversalHookRuntime.Callback(){public void before(RoiUniversalHookRuntime.Invocation i){}public void after(RoiUniversalHookRuntime.Invocation i){}};}
 static RoiUniversalHookRuntime.Spec method(String id,String name,String ret,String...p){return RoiUniversalHookRuntime.Spec.builder(id).role("chat").className(Sample.class.getName()).method(name).params(p).returns(ret).fingerprint("fp").callback(cb()).build();}
 static final class Fake implements RoiUniversalHookRuntime.Backend {AtomicInteger adds=new AtomicInteger(),removes=new AtomicInteger();int failAt=-1,failUnhookAt=-1,failUnhookErrorAt=-1;
 public RoiUniversalHookRuntime.Handle install(RoiUniversalHookRuntime.Target t,RoiUniversalHookRuntime.Callback c)throws Exception{if(adds.incrementAndGet()==failAt)throw new Exception("injected");return new RoiUniversalHookRuntime.Handle(){public void unhook()throws Exception{int n=removes.incrementAndGet();if(n==failUnhookErrorAt)throw new AssertionError("fatal unhook injected");if(n==failUnhookAt)throw new Exception("unhook injected");}};}}
 public static void main(String[]z)throws Exception{
  RoiUniversalHookRuntime.Resolver r=new RoiUniversalHookRuntime.Resolver();
  ok(r.resolve(method("m","convert","java.lang.String","int"),Sample.class.getClassLoader()).member.getName().equals("convert"),"exact private method");
  RoiUniversalHookRuntime.Spec arrays=RoiUniversalHookRuntime.Spec.builder("arrays").role("chat").className(Sample.class.getName()).method("arrays").params("byte[]","char[]","int[][]","java.lang.String[][]").returns("int[]").fingerprint("fp").callback(cb()).build();
  ok(r.resolve(arrays,Sample.class.getClassLoader()).member.getName().equals("arrays"),"primitive and multidimensional array descriptors");
  RoiUniversalHookRuntime.Spec nested=RoiUniversalHookRuntime.Spec.builder("nested").role("chat").className(Sample.class.getName()).method("nested").params(Sample.Nested.class.getName()+"[]").returns(Sample.Nested.class.getName()+"[]").fingerprint("fp").callback(cb()).build();
  ok(r.resolve(nested,Sample.class.getClassLoader()).member.getName().equals("nested"),"nested-class array names preserve JVM binary class identity");
  RoiUniversalHookRuntime.Spec ctor=RoiUniversalHookRuntime.Spec.builder("c").role("chat").className(Sample.class.getName()).constructor().params("java.lang.String").fingerprint("fp").callback(cb()).build();
  ok(r.resolve(ctor,Sample.class.getClassLoader()).member instanceof java.lang.reflect.Constructor,"constructor");
  boolean rejected=false;try{r.resolve(method("bad","convert","java.lang.String","long"),Sample.class.getClassLoader());}catch(RoiUniversalHookRuntime.ResolveException e){rejected=true;}ok(rejected,"overload mismatch");
  RoiUniversalHookRuntime.Adapters a=new RoiUniversalHookRuntime.Adapters();a.approve("fp",Collections.singleton("chat"));ok(!a.allows("other","chat"),"unknown fingerprint fail closed");
  Fake deniedBackend=new Fake();RoiUniversalHookRuntime.Installer denied=new RoiUniversalHookRuntime.Installer(r,deniedBackend,new RoiUniversalHookRuntime.Health(){public boolean verify(RoiUniversalHookRuntime.Target t,RoiUniversalHookRuntime.Handle h){return true;}},new RoiUniversalHookRuntime.ActivationGate(){public boolean authorized(RoiUniversalHookRuntime.Spec s,ClassLoader loader){return false;}});
  ok(!denied.install(Collections.singletonList(method("denied","convert","java.lang.String","int")),Sample.class.getClassLoader(),"fp",a).success&&deniedBackend.adds.get()==0,"evidence gate must run before backend");
  Fake graphBackend=new Fake();RoiUniversalHookRuntime.Installer graphGuard=new RoiUniversalHookRuntime.Installer(r,graphBackend,new RoiUniversalHookRuntime.Health(){public boolean verify(RoiUniversalHookRuntime.Target t,RoiUniversalHookRuntime.Handle h){return true;}},new RoiUniversalHookRuntime.ActivationGate(){public boolean authorized(RoiUniversalHookRuntime.Spec s,ClassLoader loader){return true;}});
  RoiUniversalHookRuntime.Spec cycleA=RoiUniversalHookRuntime.Spec.builder("cycleA").role("chat").className(Sample.class.getName()).method("ping").returns("void").fingerprint("fp").dependsOn("cycleB").callback(cb()).build();
  RoiUniversalHookRuntime.Spec cycleB=RoiUniversalHookRuntime.Spec.builder("cycleB").role("chat").className(Sample.class.getName()).method("convert").params("int").returns("java.lang.String").fingerprint("fp").dependsOn("cycleA").callback(cb()).build();
  ok(!graphGuard.install(Arrays.asList(cycleA,cycleB),Sample.class.getClassLoader(),"fp",a).success&&graphBackend.adds.get()==0,"dependency cycle must be rejected before backend calls");
  RoiUniversalHookRuntime.Spec missing=RoiUniversalHookRuntime.Spec.builder("missing").role("chat").className(Sample.class.getName()).method("ping").returns("void").fingerprint("fp").dependsOn("not-installed").callback(cb()).build();
  ok(!graphGuard.install(Collections.singletonList(missing),Sample.class.getClassLoader(),"fp",a).success&&graphBackend.adds.get()==0,"missing dependency must be rejected before backend calls");
  Fake f=new Fake();RoiUniversalHookRuntime.Installer i=new RoiUniversalHookRuntime.Installer(r,f,new RoiUniversalHookRuntime.Health(){public boolean verify(RoiUniversalHookRuntime.Target t,RoiUniversalHookRuntime.Handle h){return true;}},new RoiUniversalHookRuntime.ActivationGate(){public boolean authorized(RoiUniversalHookRuntime.Spec s,ClassLoader loader){return true;}});
  RoiUniversalHookRuntime.Spec one=method("one","convert","java.lang.String","int");
  RoiUniversalHookRuntime.Spec two=RoiUniversalHookRuntime.Spec.builder("two").role("chat").className(Sample.class.getName()).method("ping").returns("void").fingerprint("fp").dependsOn("one").callback(cb()).build();
  List<RoiUniversalHookRuntime.Spec> batch=Arrays.asList(one,two);
  ok(i.install(batch,Sample.class.getClassLoader(),"fp",a).success&&i.activeCount()==2,"install batch");
  ok(!i.uninstall("one")&&i.activeCount()==2&&i.state("one")==RoiUniversalHookRuntime.State.ACTIVE,"cannot uninstall dependency while dependent hook is active");
  ok(i.install(batch,Sample.class.getClassLoader(),"fp",a).success&&f.adds.get()==2,"idempotency");
  Fake loaderBackend=new Fake();RoiUniversalHookRuntime.Installer loaderBound=new RoiUniversalHookRuntime.Installer(r,loaderBackend,new RoiUniversalHookRuntime.Health(){public boolean verify(RoiUniversalHookRuntime.Target t,RoiUniversalHookRuntime.Handle h){return true;}},new RoiUniversalHookRuntime.ActivationGate(){public boolean authorized(RoiUniversalHookRuntime.Spec s,ClassLoader loader){return true;}});
  ok(loaderBound.install(batch,Sample.class.getClassLoader(),"fp",a).success,"initial loader-bound install");
  ClassLoader alternateLoader=new ClassLoader(Sample.class.getClassLoader()){};
  ok(!loaderBound.install(batch,alternateLoader,"fp",a).success&&loaderBackend.adds.get()==2,"same HookSpec cannot be treated idempotent across ClassLoader scopes");
  loaderBound.uninstallAll();
  RoiUniversalHookRuntime.Spec collision=RoiUniversalHookRuntime.Spec.builder("one").role("chat").className(Sample.class.getName()).method("convert").params("int").returns("void").fingerprint("fp").callback(cb()).build();
  ok(!i.install(Collections.singletonList(collision),Sample.class.getClassLoader(),"fp",a).success&&f.adds.get()==2,"same id with changed return contract must be rejected");
  RoiUniversalHookRuntime.Spec callbackReplacement=method("one","convert","java.lang.String","int");
  ok(!i.install(Collections.singletonList(callbackReplacement),Sample.class.getClassLoader(),"fp",a).success
      &&f.adds.get()==2,"same ID and target with a different callback binding must be rejected");
  i.uninstallAll();
  ok(i.activeCount()==0&&f.removes.get()==2,"group uninstallAll");
  ok(i.install(batch,Sample.class.getClassLoader(),"fp",a).success
      &&i.activeCount()==2&&f.adds.get()==4,"reinstall after group uninstall");
  RoiUniversalHookRuntime.Spec duplicateTarget=RoiUniversalHookRuntime.Spec.builder("duplicate").role("chat").className(Sample.class.getName()).method("convert").params("int").returns("java.lang.String").fingerprint("fp").callback(cb()).build();
  Fake collisionBackend=new Fake();RoiUniversalHookRuntime.Installer collisionGuard=new RoiUniversalHookRuntime.Installer(r,collisionBackend,new RoiUniversalHookRuntime.Health(){public boolean verify(RoiUniversalHookRuntime.Target t,RoiUniversalHookRuntime.Handle h){return true;}},new RoiUniversalHookRuntime.ActivationGate(){public boolean authorized(RoiUniversalHookRuntime.Spec s,ClassLoader loader){return true;}});
  ok(!collisionGuard.install(Arrays.asList(one,duplicateTarget),Sample.class.getClassLoader(),"fp",a).success&&collisionBackend.adds.get()==0,"same Member cannot be installed twice in one batch");
  i.uninstallAll();
  ok(i.activeCount()==0&&f.removes.get()==4,"second group uninstall");
  Fake fail=new Fake();fail.failAt=2;RoiUniversalHookRuntime.Installer rollback=new RoiUniversalHookRuntime.Installer(r,fail,new RoiUniversalHookRuntime.Health(){public boolean verify(RoiUniversalHookRuntime.Target t,RoiUniversalHookRuntime.Handle h){return true;}},new RoiUniversalHookRuntime.ActivationGate(){public boolean authorized(RoiUniversalHookRuntime.Spec s,ClassLoader loader){return true;}});
  ok(!rollback.install(batch,Sample.class.getClassLoader(),"fp",a).success&&rollback.activeCount()==0&&fail.removes.get()==1,"reverse rollback");
  Fake fatalRollbackBackend=new Fake();fatalRollbackBackend.failAt=2;fatalRollbackBackend.failUnhookErrorAt=1;
  RoiUniversalHookRuntime.Installer fatalRollback=new RoiUniversalHookRuntime.Installer(r,fatalRollbackBackend,new RoiUniversalHookRuntime.Health(){public boolean verify(RoiUniversalHookRuntime.Target t,RoiUniversalHookRuntime.Handle h){return true;}},new RoiUniversalHookRuntime.ActivationGate(){public boolean authorized(RoiUniversalHookRuntime.Spec s,ClassLoader loader){return true;}});
  boolean fatalRollbackPropagated=false;try{fatalRollback.install(batch,Sample.class.getClassLoader(),"fp",a);}catch(AssertionError expected){fatalRollbackPropagated=true;}
  ok(fatalRollbackPropagated&&fatalRollback.activeCount()==1&&fatalRollback.state("one")==RoiUniversalHookRuntime.State.FAILED,"fatal rollback failure must propagate without losing handle");
  ok(fatalRollback.uninstall("one")&&fatalRollback.activeCount()==0,"retained handle must be cleanable after fatal rollback");
  Fake rollbackUnhookFailure=new Fake();rollbackUnhookFailure.failAt=2;rollbackUnhookFailure.failUnhookAt=1;
  RoiUniversalHookRuntime.Installer uncertain=new RoiUniversalHookRuntime.Installer(r,rollbackUnhookFailure,new RoiUniversalHookRuntime.Health(){public boolean verify(RoiUniversalHookRuntime.Target t,RoiUniversalHookRuntime.Handle h){return true;}},new RoiUniversalHookRuntime.ActivationGate(){public boolean authorized(RoiUniversalHookRuntime.Spec s,ClassLoader loader){return true;}});
  ok(!uncertain.install(batch,Sample.class.getClassLoader(),"fp",a).success&&uncertain.activeCount()==1&&uncertain.state("one")==RoiUniversalHookRuntime.State.FAILED,"failed rollback must retain uncertain handle");
  ok(!uncertain.install(batch,Sample.class.getClassLoader(),"fp",a).success&&rollbackUnhookFailure.adds.get()==2,"uncertain handle must block duplicate installation");
  ok(!uncertain.install(Collections.singletonList(two),Sample.class.getClassLoader(),"fp",a).success&&rollbackUnhookFailure.adds.get()==2,"dependent hook must reject an unresolved FAILED dependency");
  ok(uncertain.uninstall("one")&&uncertain.activeCount()==0,"explicit cleanup must retry failed rollback unhook");
  ok(uncertain.install(batch,Sample.class.getClassLoader(),"fp",a).success&&uncertain.activeCount()==2,"installation may resume after uncertain handle is cleared");
  uncertain.uninstallAll();ok(uncertain.activeCount()==0,"cleanup after recovered installation");
  Fake fatalUninstallBackend=new Fake();RoiUniversalHookRuntime.Installer fatalUninstall=new RoiUniversalHookRuntime.Installer(r,fatalUninstallBackend,new RoiUniversalHookRuntime.Health(){public boolean verify(RoiUniversalHookRuntime.Target t,RoiUniversalHookRuntime.Handle h){return true;}},new RoiUniversalHookRuntime.ActivationGate(){public boolean authorized(RoiUniversalHookRuntime.Spec s,ClassLoader loader){return true;}});
  ok(fatalUninstall.install(Collections.singletonList(one),Sample.class.getClassLoader(),"fp",a).success,"fatal uninstall fixture install");
  fatalUninstallBackend.failUnhookErrorAt=1;boolean fatalUninstallPropagated=false;try{fatalUninstall.uninstall("one");}catch(AssertionError expected){fatalUninstallPropagated=true;}
  ok(fatalUninstallPropagated&&fatalUninstall.activeCount()==1&&fatalUninstall.state("one")==RoiUniversalHookRuntime.State.FAILED,"fatal uninstall failure must propagate and retain handle");
  fatalUninstallBackend.failUnhookErrorAt=-1;ok(fatalUninstall.uninstall("one")&&fatalUninstall.activeCount()==0,"fatal uninstall handle must remain retryable");
  Fake bulkUnhookFailure=new Fake();bulkUnhookFailure.failUnhookAt=1;
  RoiUniversalHookRuntime.Installer bulkUninstall=new RoiUniversalHookRuntime.Installer(r,bulkUnhookFailure,new RoiUniversalHookRuntime.Health(){public boolean verify(RoiUniversalHookRuntime.Target t,RoiUniversalHookRuntime.Handle h){return true;}},new RoiUniversalHookRuntime.ActivationGate(){public boolean authorized(RoiUniversalHookRuntime.Spec s,ClassLoader loader){return true;}});
  ok(bulkUninstall.install(batch,Sample.class.getClassLoader(),"fp",a).success,"bulk uninstall fixture install");
  bulkUninstall.uninstallAll();
  ok(bulkUninstall.activeCount()==2&&bulkUninstall.state("two")==RoiUniversalHookRuntime.State.FAILED&&bulkUninstall.state("one")==RoiUniversalHookRuntime.State.ACTIVE,"failed dependent uninstall must retain its dependency until retry");
  bulkUninstall.uninstallAll();ok(bulkUninstall.activeCount()==0,"bulk uninstall retry must clear retained handle");
  RoiUniversalHookRuntime.Installer unhealthy=new RoiUniversalHookRuntime.Installer(r,new Fake(),new RoiUniversalHookRuntime.Health(){public boolean verify(RoiUniversalHookRuntime.Target t,RoiUniversalHookRuntime.Handle h){return false;}},new RoiUniversalHookRuntime.ActivationGate(){public boolean authorized(RoiUniversalHookRuntime.Spec s,ClassLoader loader){return true;}});
  ok(!unhealthy.install(Collections.singletonList(one),Sample.class.getClassLoader(),"fp",a).success&&unhealthy.activeCount()==0,"health failure rollback");
  System.out.println("ROI universal hook runtime tests passed");
 }
}

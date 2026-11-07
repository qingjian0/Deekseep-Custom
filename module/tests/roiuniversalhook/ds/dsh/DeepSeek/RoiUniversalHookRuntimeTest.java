package ds.dsh.DeepSeek;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
public final class RoiUniversalHookRuntimeTest {
 public static final class Sample {public Sample(String s){} private String convert(int i){return ""+i;} public void ping(){}}
 static void ok(boolean b,String m){if(!b)throw new AssertionError(m);}
 static RoiUniversalHookRuntime.Callback cb(){return new RoiUniversalHookRuntime.Callback(){public void before(RoiUniversalHookRuntime.Invocation i){}public void after(RoiUniversalHookRuntime.Invocation i){}};}
 static RoiUniversalHookRuntime.Spec method(String id,String name,String ret,String...p){return RoiUniversalHookRuntime.Spec.builder(id).role("chat").className(Sample.class.getName()).method(name).params(p).returns(ret).fingerprint("fp").callback(cb()).build();}
 static final class Fake implements RoiUniversalHookRuntime.Backend {AtomicInteger adds=new AtomicInteger(),removes=new AtomicInteger();int failAt=-1,failUnhookAt=-1;
 public RoiUniversalHookRuntime.Handle install(RoiUniversalHookRuntime.Target t,RoiUniversalHookRuntime.Callback c)throws Exception{if(adds.incrementAndGet()==failAt)throw new Exception("injected");return new RoiUniversalHookRuntime.Handle(){public void unhook()throws Exception{if(removes.incrementAndGet()==failUnhookAt)throw new Exception("unhook injected");}};}}
 public static void main(String[]z)throws Exception{
  RoiUniversalHookRuntime.Resolver r=new RoiUniversalHookRuntime.Resolver();
  ok(r.resolve(method("m","convert","java.lang.String","int"),Sample.class.getClassLoader()).member.getName().equals("convert"),"exact private method");
  RoiUniversalHookRuntime.Spec ctor=RoiUniversalHookRuntime.Spec.builder("c").role("chat").className(Sample.class.getName()).constructor().params("java.lang.String").fingerprint("fp").callback(cb()).build();
  ok(r.resolve(ctor,Sample.class.getClassLoader()).member instanceof java.lang.reflect.Constructor,"constructor");
  boolean rejected=false;try{r.resolve(method("bad","convert","java.lang.String","long"),Sample.class.getClassLoader());}catch(RoiUniversalHookRuntime.ResolveException e){rejected=true;}ok(rejected,"overload mismatch");
  RoiUniversalHookRuntime.Adapters a=new RoiUniversalHookRuntime.Adapters();a.approve("fp",Collections.singleton("chat"));ok(!a.allows("other","chat"),"unknown fingerprint fail closed");
  Fake deniedBackend=new Fake();RoiUniversalHookRuntime.Installer denied=new RoiUniversalHookRuntime.Installer(r,deniedBackend,new RoiUniversalHookRuntime.Health(){public boolean verify(RoiUniversalHookRuntime.Target t,RoiUniversalHookRuntime.Handle h){return true;}},new RoiUniversalHookRuntime.ActivationGate(){public boolean authorized(RoiUniversalHookRuntime.Spec s,ClassLoader loader){return false;}});
  ok(!denied.install(Collections.singletonList(method("denied","convert","java.lang.String","int")),Sample.class.getClassLoader(),"fp",a).success&&deniedBackend.adds.get()==0,"evidence gate must run before backend");
  Fake f=new Fake();RoiUniversalHookRuntime.Installer i=new RoiUniversalHookRuntime.Installer(r,f,new RoiUniversalHookRuntime.Health(){public boolean verify(RoiUniversalHookRuntime.Target t,RoiUniversalHookRuntime.Handle h){return true;}},new RoiUniversalHookRuntime.ActivationGate(){public boolean authorized(RoiUniversalHookRuntime.Spec s,ClassLoader loader){return true;}});
  RoiUniversalHookRuntime.Spec one=method("one","convert","java.lang.String","int");
  RoiUniversalHookRuntime.Spec two=RoiUniversalHookRuntime.Spec.builder("two").role("chat").className(Sample.class.getName()).method("ping").returns("void").fingerprint("fp").dependsOn("one").callback(cb()).build();
  List<RoiUniversalHookRuntime.Spec> batch=Arrays.asList(one,two);
  ok(i.install(batch,Sample.class.getClassLoader(),"fp",a).success&&i.activeCount()==2,"install batch");
  ok(i.install(batch,Sample.class.getClassLoader(),"fp",a).success&&f.adds.get()==2,"idempotency");
  RoiUniversalHookRuntime.Spec collision=RoiUniversalHookRuntime.Spec.builder("one").role("chat").className(Sample.class.getName()).method("convert").params("int").returns("void").fingerprint("fp").callback(cb()).build();
  ok(!i.install(Collections.singletonList(collision),Sample.class.getClassLoader(),"fp",a).success&&f.adds.get()==2,"same id with changed return contract must be rejected");
  RoiUniversalHookRuntime.Spec callbackReplacement=method("one","convert","java.lang.String","int");
  ok(!i.install(Collections.singletonList(callbackReplacement),Sample.class.getClassLoader(),"fp",a).success
      &&f.adds.get()==2,"same ID and target with a different callback binding must be rejected");
  i.uninstallAll();
  ok(i.activeCount()==0&&f.removes.get()==2,"group uninstallAll");
  ok(i.install(batch,Sample.class.getClassLoader(),"fp",a).success
      &&i.activeCount()==2&&f.adds.get()==4,"reinstall after group uninstall");
  i.uninstallAll();
  ok(i.activeCount()==0&&f.removes.get()==4,"second group uninstall");
  Fake fail=new Fake();fail.failAt=2;RoiUniversalHookRuntime.Installer rollback=new RoiUniversalHookRuntime.Installer(r,fail,new RoiUniversalHookRuntime.Health(){public boolean verify(RoiUniversalHookRuntime.Target t,RoiUniversalHookRuntime.Handle h){return true;}},new RoiUniversalHookRuntime.ActivationGate(){public boolean authorized(RoiUniversalHookRuntime.Spec s,ClassLoader loader){return true;}});
  ok(!rollback.install(batch,Sample.class.getClassLoader(),"fp",a).success&&rollback.activeCount()==0&&fail.removes.get()==1,"reverse rollback");
  Fake rollbackUnhookFailure=new Fake();rollbackUnhookFailure.failAt=2;rollbackUnhookFailure.failUnhookAt=1;
  RoiUniversalHookRuntime.Installer uncertain=new RoiUniversalHookRuntime.Installer(r,rollbackUnhookFailure,new RoiUniversalHookRuntime.Health(){public boolean verify(RoiUniversalHookRuntime.Target t,RoiUniversalHookRuntime.Handle h){return true;}},new RoiUniversalHookRuntime.ActivationGate(){public boolean authorized(RoiUniversalHookRuntime.Spec s,ClassLoader loader){return true;}});
  ok(!uncertain.install(batch,Sample.class.getClassLoader(),"fp",a).success&&uncertain.activeCount()==1&&uncertain.state("one")==RoiUniversalHookRuntime.State.FAILED,"failed rollback must retain uncertain handle");
  ok(!uncertain.install(batch,Sample.class.getClassLoader(),"fp",a).success&&rollbackUnhookFailure.adds.get()==2,"uncertain handle must block duplicate installation");
  ok(uncertain.uninstall("one")&&uncertain.activeCount()==0,"explicit cleanup must retry failed rollback unhook");
  ok(uncertain.install(batch,Sample.class.getClassLoader(),"fp",a).success&&uncertain.activeCount()==2,"installation may resume after uncertain handle is cleared");
  uncertain.uninstallAll();ok(uncertain.activeCount()==0,"cleanup after recovered installation");
  Fake bulkUnhookFailure=new Fake();bulkUnhookFailure.failUnhookAt=1;
  RoiUniversalHookRuntime.Installer bulkUninstall=new RoiUniversalHookRuntime.Installer(r,bulkUnhookFailure,new RoiUniversalHookRuntime.Health(){public boolean verify(RoiUniversalHookRuntime.Target t,RoiUniversalHookRuntime.Handle h){return true;}},new RoiUniversalHookRuntime.ActivationGate(){public boolean authorized(RoiUniversalHookRuntime.Spec s,ClassLoader loader){return true;}});
  ok(bulkUninstall.install(batch,Sample.class.getClassLoader(),"fp",a).success,"bulk uninstall fixture install");
  bulkUninstall.uninstallAll();
  ok(bulkUninstall.activeCount()==1&&bulkUninstall.state("two")==RoiUniversalHookRuntime.State.FAILED,"bulk uninstall failure must retain unresolved handle");
  bulkUninstall.uninstallAll();ok(bulkUninstall.activeCount()==0,"bulk uninstall retry must clear retained handle");
  RoiUniversalHookRuntime.Installer unhealthy=new RoiUniversalHookRuntime.Installer(r,new Fake(),new RoiUniversalHookRuntime.Health(){public boolean verify(RoiUniversalHookRuntime.Target t,RoiUniversalHookRuntime.Handle h){return false;}},new RoiUniversalHookRuntime.ActivationGate(){public boolean authorized(RoiUniversalHookRuntime.Spec s,ClassLoader loader){return true;}});
  ok(!unhealthy.install(Collections.singletonList(one),Sample.class.getClassLoader(),"fp",a).success&&unhealthy.activeCount()==0,"health failure rollback");
  System.out.println("ROI universal hook runtime tests passed");
 }
}

package ds.dsh.DeepSeek;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

public final class RoiUnknownHostChatRequestResolverTest {
    public static final class RequestFixture {
        public RequestFixture(String session, Integer parent, String prompt, Object refs,
                boolean thinking, boolean search, String audio, boolean preempt,
                String model, String action, int mask) {}
    }

    public static final class WrongFixture {
        public WrongFixture(String session, String prompt, Object refs, boolean thinking,
                boolean search, String audio, boolean preempt, String model,
                String action, int mask) {}
    }

    public static void main(String[] args) throws Throwable {
        testSerializerEvidenceAndLateReconciliation();
        testClassLoadAfterCandidate();
        testWeakIdentityAndDuplicateSuppression();
        testFailClosedInputs();
        System.out.println("ROI unknown-host chat request resolver tests passed");
    }

    private static void testSerializerEvidenceAndLateReconciliation() throws Throwable {
        RoiUnknownHostChatRequestResolver resolver = new RoiUnknownHostChatRequestResolver();
        ClassLoader root = RequestFixture.class.getClassLoader();
        String scope = RoiCandidateIdentity.forClassLoader(root);
        String owner = descriptor(RequestFixture.class);
        String constructor = constructorDescriptor(RequestFixture.class.getDeclaredConstructors()[0]);

        AtomicInteger installs = new AtomicInteger();
        resolver.observeLoadedClass(RequestFixture.class, (candidate, ctor) -> {
            installs.incrementAndGet();
            return true;
        });
        check(resolver.candidateCount() == 0, "class observation alone must not hook");

        String[] anchors = {"chat_session_id", "parent_message_id",
                "thinking_enabled", "search_enabled"};
        for (int i = 0; i < anchors.length; i++) {
            resolver.observeDexCandidate(scope, anchors[i],
                    "Lserializer/RequestSerializer;->deserialize()V",
                    Arrays.asList(constructor));
        }
        RoiUnknownHostChatRequestResolver.Candidate candidate = resolver.bestCandidate(scope);
        check(candidate != null, "four independent schema anchors must produce one candidate");
        check(candidate.evidenceCount() == 4, "all anchors retained");
        check(candidate.ownerDescriptor.equals(owner), "constructor owner resolved");
        check(candidate.constructorDescriptor.equals(constructor), "full constructor descriptor retained");

        int installed = resolver.registerCandidate(candidate, root, (resolved, ctor) -> {
            check(ctor.getDeclaringClass() == RequestFixture.class, "exact runtime class");
            check(RoiUnknownHostChatRequestResolver.hasExpectedConstructorShape(
                    ctor.getParameterTypes()), "constructor shape validated again at install");
            installs.incrementAndGet();
            return true;
        });
        check(installed == 1, "late candidate must reconcile against previously observed class");
        check(installs.get() == 2, "only exact candidate installed");
        check(resolver.installedCount() == 1, "installation recorded");
    }

    private static void testClassLoadAfterCandidate() throws Throwable {
        RoiUnknownHostChatRequestResolver resolver = new RoiUnknownHostChatRequestResolver();
        ClassLoader root = RequestFixture.class.getClassLoader();
        String scope = RoiCandidateIdentity.forClassLoader(root);
        String constructor = constructorDescriptor(RequestFixture.class.getDeclaredConstructors()[0]);
        String[] anchors = {"chat_session_id", "thinking_enabled", "search_enabled", "preempt"};
        for (String anchor : anchors) {
            resolver.observeDexCandidate(scope, anchor,
                    "Lserializer/RequestSerializer;->deserialize()V",
                    Arrays.asList(constructor));
        }
        RoiUnknownHostChatRequestResolver.Candidate candidate = resolver.bestCandidate(scope);
        check(candidate != null, "candidate discovered before class load");
        AtomicInteger installs = new AtomicInteger();
        check(resolver.registerCandidate(candidate, root, (resolved, ctor) -> {
            installs.incrementAndGet();
            return true;
        }) == 0, "no runtime class means no hook");
        check(resolver.observeLoadedClass(RequestFixture.class, (resolved, ctor) -> {
            installs.incrementAndGet();
            return true;
        }) == 1, "later class load completes exact binding");
        check(installs.get() == 1, "late install exactly once");
    }

    private static void testWeakIdentityAndDuplicateSuppression() throws Throwable {
        RoiUnknownHostChatRequestResolver resolver = new RoiUnknownHostChatRequestResolver();
        ClassLoader root = RequestFixture.class.getClassLoader();
        String scope = RoiCandidateIdentity.forClassLoader(root);
        String constructor = constructorDescriptor(RequestFixture.class.getDeclaredConstructors()[0]);
        for (String anchor : new String[]{"chat_session_id", "parent_message_id",
                "thinking_enabled", "search_enabled"}) {
            resolver.observeDexCandidate(scope, anchor,
                    "Lserializer/RequestSerializer;->deserialize()V",
                    Arrays.asList(constructor));
        }
        RoiUnknownHostChatRequestResolver.Candidate candidate = resolver.bestCandidate(scope);
        AtomicInteger installs = new AtomicInteger();
        RoiUnknownHostChatRequestResolver.Installer installer = (resolved, ctor) -> {
            installs.incrementAndGet();
            return true;
        };
        resolver.registerCandidate(candidate, root, installer);
        resolver.observeLoadedClass(RequestFixture.class, root, installer);
        resolver.observeLoadedClass(RequestFixture.class, root, installer);
        check(installs.get() == 1, "same runtime constructor must never be installed twice");
        check(resolver.observedClassCount() == 1, "class identity is deduplicated");
    }

    private static void testFailClosedInputs() {
        RoiUnknownHostChatRequestResolver resolver = new RoiUnknownHostChatRequestResolver();
        String constructor = constructorDescriptor(RequestFixture.class.getDeclaredConstructors()[0]);
        resolver.observeDexCandidate("apk:/base.apk", "chat_session_id",
                "Lserializer/RequestSerializer;->deserialize()V", Arrays.asList(constructor));
        resolver.observeDexCandidate("loader:fixture", "prompt",
                "Lserializer/RequestSerializer;->deserialize()V", Arrays.asList(constructor));
        resolver.observeDexCandidate("loader:fixture", "chat_session_id",
                "Lserializer/RequestSerializer;->deserialize()V",
                Arrays.asList("Lbad/Request;-><init>(Ljava/lang/String;Ljava/lang/String;)V"));
        check(resolver.candidateCount() == 0, "APK-only, unknown anchor and wrong shape rejected");
        check(RoiUnknownHostChatRequestResolver.bestCandidate("loader:fixture") == null,
                "no unsupported candidate can be activated");
        check(!RoiUnknownHostChatRequestResolver.isChatRequestConstructor(
                "Lbad/Request;-><init>(Ljava/lang/String;)V"), "short constructor rejected");
    }

    private static String descriptor(Class<?> type) {
        return "L" + type.getName().replace('.', '/') + ";";
    }

    private static String constructorDescriptor(Class<?> type) {
        String owner = descriptor(type);
        return owner + "-><init>(Ljava/lang/String;Ljava/lang/Integer;Ljava/lang/String;"
                + "Ljava/lang/Object;ZZLjava/lang/String;ZLjava/lang/String;Ljava/lang/String;I)V";
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}

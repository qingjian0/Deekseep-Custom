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

    public static final class AlternateRequestFixture {
        public AlternateRequestFixture(String session, Integer parent, String prompt, Object refs,
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
        testEvidenceChangesInvalidateFinalization();
        testClassLoadAfterCandidate();
        testWeakIdentityAndDuplicateSuppression();
        testFailClosedInputs();
        testFeatureHookPolicy();
        testAmbiguousCandidatesStayDisabled();
        System.out.println("ROI unknown-host chat request resolver tests passed");
    }

    private static void testSerializerEvidenceAndLateReconciliation() throws Throwable {
        RoiUnknownHostChatRequestResolver resolver = new RoiUnknownHostChatRequestResolver();
        ClassLoader root = RequestFixture.class.getClassLoader();
        String scope = RoiCandidateIdentity.forClassLoader(root);
        String owner = descriptor(RequestFixture.class);
        String constructor = constructorDescriptor(RequestFixture.class.getDeclaredConstructors()[0]);

        AtomicInteger installs = new AtomicInteger();
        resolver.observeLoadedClass(RequestFixture.class, root, (candidate, ctor) -> {
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
        resolver.finalizeDiscovery();
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
        check(installs.get() == 1, "only exact candidate installed");
        check(resolver.installedCount() == 1, "installation recorded");
    }

    private static void testEvidenceChangesInvalidateFinalization() throws Throwable {
        RoiUnknownHostChatRequestResolver resolver = new RoiUnknownHostChatRequestResolver();
        ClassLoader root = RequestFixture.class.getClassLoader();
        String scope = RoiCandidateIdentity.forClassLoader(root);
        String constructor = constructorDescriptor(RequestFixture.class.getDeclaredConstructors()[0]);
        String serializer = "Lserializer/RequestSerializer;->deserialize()V";
        String[] anchors = {"chat_session_id", "parent_message_id",
                "thinking_enabled", "search_enabled"};
        for (String anchor : anchors) {
            resolver.observeDexCandidate(scope, anchor, serializer, Arrays.asList(constructor));
        }
        resolver.finalizeDiscovery();
        RoiUnknownHostChatRequestResolver.Candidate initial = resolver.bestCandidate(scope);
        check(initial != null, "initial complete evidence authorizes discovery");

        // An identical duplicate is not new evidence and must not reopen the scope.
        resolver.observeDexCandidate(scope, "chat_session_id", serializer, Arrays.asList(constructor));
        check(!resolver.bestCandidates().isEmpty(), "duplicate evidence preserves finalization");

        // New evidence changes the candidate snapshot; the old finalization must be revoked.
        resolver.observeDexCandidate(scope, "preempt",
                "Lserializer/RequestSerializer;->serialize()V", Arrays.asList(constructor));
        check(resolver.bestCandidates().isEmpty(),
                "new evidence invalidates stale finalized-scope authorization");
        check(resolver.registerCandidate(initial, root, (candidate, ctor) -> true) == 0,
                "stale candidate cannot install before a new complete scan");

        resolver.finalizeDiscovery();
        RoiUnknownHostChatRequestResolver.Candidate refreshed = resolver.bestCandidate(scope);
        check(refreshed != null && refreshed.evidenceCount() == 5,
                "re-finalization publishes the refreshed evidence snapshot");
        check(resolver.registerCandidate(refreshed, root, (candidate, ctor) -> true) == 1,
                "refreshed candidate installs only after re-finalization");
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
        resolver.finalizeDiscovery();
        RoiUnknownHostChatRequestResolver.Candidate candidate = resolver.bestCandidate(scope);
        check(candidate != null, "candidate discovered before class load");
        AtomicInteger installs = new AtomicInteger();
        check(resolver.registerCandidate(candidate, root, (resolved, ctor) -> {
            installs.incrementAndGet();
            return true;
        }) == 0, "no runtime class means no hook");
        check(resolver.observeLoadedClass(RequestFixture.class, root, (resolved, ctor) -> {
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
        resolver.finalizeDiscovery();
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

    private static void testFeatureHookPolicy() throws Throwable {
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
        resolver.finalizeDiscovery();
        RoiUnknownHostChatRequestResolver.Candidate candidate = resolver.bestCandidate(scope);
        Constructor<?> target = RequestFixture.class.getDeclaredConstructors()[0];
        java.io.File switchFile = java.io.File.createTempFile("roi-feature-hook", ".switch");
        try {
            write(switchFile, "DISABLED\n");
            check(!RoiUnknownHostFeatureHookPolicy.allowsChatRequestPromptHook(
                    false, switchFile, candidate, target, root), "feature switch defaults closed");
            write(switchFile, "ENABLE\n");
            check(RoiUnknownHostFeatureHookPolicy.allowsChatRequestPromptHook(
                    false, switchFile, candidate, target, root), "exact adapter candidate authorized");
            check(!RoiUnknownHostFeatureHookPolicy.allowsChatRequestPromptHook(
                    true, switchFile, candidate, target, root), "maintained host does not use unknown lane");
            check(!RoiUnknownHostFeatureHookPolicy.allowsChatRequestPromptHook(
                    false, switchFile, null, target, root), "missing candidate rejected");
            check(!RoiUnknownHostFeatureHookPolicy.allowsChatRequestPromptHook(
                    false, switchFile, candidate, WrongFixture.class.getDeclaredConstructors()[0], root),
                    "different constructor rejected");
            write(switchFile, "ENABLE\nEXTRA\n");
            check(!RoiUnknownHostFeatureHookPolicy.allowsChatRequestPromptHook(
                    false, switchFile, candidate, target, root), "extra switch line rejected");
        } finally {
            switchFile.delete();
        }
    }

    private static void write(java.io.File file, String value) throws Exception {
        java.io.FileWriter writer = new java.io.FileWriter(file, false);
        try { writer.write(value); } finally { writer.close(); }
    }

    private static void testAmbiguousCandidatesStayDisabled() throws Throwable {
        RoiUnknownHostChatRequestResolver resolver = new RoiUnknownHostChatRequestResolver();
        ClassLoader root = RequestFixture.class.getClassLoader();
        String scope = RoiCandidateIdentity.forClassLoader(root);
        String first = constructorDescriptor(RequestFixture.class.getDeclaredConstructors()[0]);
        String second = constructorDescriptor(AlternateRequestFixture.class.getDeclaredConstructors()[0]);
        for (String anchor : new String[]{"chat_session_id", "parent_message_id",
                "thinking_enabled", "search_enabled"}) {
            resolver.observeDexCandidate(scope, anchor,
                    "Lserializer/RequestSerializer;->deserialize()V",
                    Arrays.asList(first, second));
        }
        resolver.finalizeDiscovery();
        check(resolver.bestCandidate(scope) == null,
                "two equally supported constructor owners remain ambiguous");
        check(resolver.bestCandidates().isEmpty(),
                "ambiguous scope yields no authorized feature adapter");
        AtomicInteger installs = new AtomicInteger();
        check(resolver.observeLoadedClass(RequestFixture.class, root, (candidate, ctor) -> {
            installs.incrementAndGet();
            return true;
        }) == 0, "ambiguous candidates never install");
        check(installs.get() == 0, "no installer invocation for ambiguous scope");
    }

    private static void testFailClosedInputs() throws Throwable {
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
        check(resolver.bestCandidate("loader:fixture") == null,
                "no unsupported candidate can be activated");
        check(!RoiUnknownHostChatRequestResolver.isChatRequestConstructor(
                "Lbad/Request;-><init>(Ljava/lang/String;)V"), "short constructor rejected");
        ClassLoader root = RequestFixture.class.getClassLoader();
        check(resolver.observeLoadedClass(WrongFixture.class, root, (candidate, ctor) -> true) == 0,
                "runtime classes without the constructor contract are ignored");
        check(resolver.observedClassCount() == 0,
                "unrelated classes are not retained in the resolver cache");
    }

    private static String descriptor(Class<?> type) {
        return "L" + type.getName().replace('.', '/') + ";";
    }

    private static String constructorDescriptor(Constructor<?> constructor) {
        StringBuilder out = new StringBuilder(descriptor(constructor.getDeclaringClass()))
                .append("-><init>(");
        for (Class<?> parameter : constructor.getParameterTypes()) {
            if (parameter.isArray()) {
                out.append(parameter.getName().replace('.', '/'));
            } else if (!parameter.isPrimitive()) {
                out.append('L').append(parameter.getName().replace('.', '/')).append(';');
            } else if (parameter == boolean.class) out.append('Z');
            else if (parameter == byte.class) out.append('B');
            else if (parameter == char.class) out.append('C');
            else if (parameter == short.class) out.append('S');
            else if (parameter == int.class) out.append('I');
            else if (parameter == long.class) out.append('J');
            else if (parameter == float.class) out.append('F');
            else if (parameter == double.class) out.append('D');
            else throw new IllegalArgumentException("Unsupported constructor parameter");
        }
        return out.append(")V").toString();
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}

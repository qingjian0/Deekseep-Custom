package ds.dsh.DeepSeek;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.util.List;

public final class RoiClassLoadObserverTest {
    private RoiClassLoadObserverTest() {}

    public static void main(String[] args) throws Exception {
        testApprovalAndOptInGates();
        testHostRootAndDescendantScope();
        testSiblingAndDelegatedClassesAreRejected();
        testDuplicateClassIsIgnored();
        testCapacityAndTtl();
        testRateLimit();
        testReentrancyGuard();
        testSnapshotsAreImmutableAndMetadataOnly();
        testNullAndClear();
        System.out.println("RoiClassLoadObserverTest: PASS");
    }

    private static void testApprovalAndOptInGates() {
        ClassLoader root = RoiClassLoadObserverTest.class.getClassLoader();
        RoiClassLoadObserver observer = new RoiClassLoadObserver();
        assertNull(observer.observe(root, RoiClassLoadObserverTest.class, root,
                false, true, 1000L), "unapproved adapter");
        assertNull(observer.observe(root, RoiClassLoadObserverTest.class, root,
                true, false, 1001L), "opt-in disabled");
        assertEquals(0, observer.snapshot(1001L).size(), "gated events");
    }

    private static void testHostRootAndDescendantScope() {
        ClassLoader root = RoiClassLoadObserverTest.class.getClassLoader();
        ClassLoader child = new ClassLoader(root) {};
        assertTrue(RoiClassLoadObserver.isWithinHostScope(root, root), "root scope");
        assertTrue(RoiClassLoadObserver.isWithinHostScope(child, root), "child scope");
        assertFalse(RoiClassLoadObserver.isWithinHostScope(
                RoiClassLoadObserverTest.class.getClassLoader(), new ClassLoader(null) {}),
                "unrelated root");
    }

    private static void testSiblingAndDelegatedClassesAreRejected() throws Exception {
        ClassLoader root = RoiClassLoadObserverTest.class.getClassLoader();
        ClassLoader sibling = new ClassLoader(null) {};
        RoiClassLoadObserver observer = new RoiClassLoadObserver();
        assertNull(observer.observe(sibling, RoiClassLoadObserverTest.class, root,
                true, true, 2000L), "sibling loader");
        assertNull(observer.observe(root, String.class, root,
                true, true, 2001L), "bootstrap-defined delegated class");
    }

    private static void testDuplicateClassIsIgnored() throws Exception {
        ClassLoader root = RoiClassLoadObserverTest.class.getClassLoader();
        ClassLoader child = new ClassLoader(root) {};
        Class<?> childDefined = defineProxyClass(child);
        RoiClassLoadObserver observer = new RoiClassLoadObserver();
        assertNotNull(observer.observe(child, childDefined, root,
                true, true, 3000L), "first class event");
        assertNull(observer.observe(child, childDefined, root,
                true, true, 3001L), "duplicate class event");
        assertEquals(1, observer.snapshot(3001L).size(), "deduplicated count");
    }

    private static void testCapacityAndTtl() throws Exception {
        ClassLoader root = RoiClassLoadObserverTest.class.getClassLoader();
        RoiClassLoadObserver observer = new RoiClassLoadObserver(2, 10, 1000L, 100L);
        ClassLoader one = new ClassLoader(root) {};
        ClassLoader two = new ClassLoader(root) {};
        ClassLoader three = new ClassLoader(root) {};
        observer.observe(one, defineProxyClass(one), root, true, true, 4000L);
        observer.observe(two, defineProxyClass(two), root, true, true, 4001L);
        observer.observe(three, defineProxyClass(three), root, true, true, 4002L);
        List<RoiClassLoadObserver.EventSnapshot> bounded = observer.snapshot(4002L);
        assertEquals(2, bounded.size(), "capacity");
        assertEquals(4001L, bounded.get(0).observedAtMillis, "oldest retained timestamp");
        assertEquals(0, observer.snapshot(4200L).size(), "TTL expiry");
    }

    private static void testRateLimit() throws Exception {
        ClassLoader root = RoiClassLoadObserverTest.class.getClassLoader();
        RoiClassLoadObserver observer = new RoiClassLoadObserver(8, 1, 1000L, 5000L);
        ClassLoader one = new ClassLoader(root) {};
        ClassLoader two = new ClassLoader(root) {};
        assertNotNull(observer.observe(one, defineProxyClass(one), root,
                true, true, 5000L), "first rate event");
        assertNull(observer.observe(two, defineProxyClass(two), root,
                true, true, 5001L), "rate-limited event");
        assertNotNull(observer.observe(two, defineProxyClass(two), root,
                true, true, 6000L), "new rate window");
    }

    private static void testReentrancyGuard() throws Exception {
        ClassLoader root = RoiClassLoadObserverTest.class.getClassLoader();
        RoiClassLoadObserver observer = new RoiClassLoadObserver();
        java.lang.reflect.Field field = RoiClassLoadObserver.class.getDeclaredField("inspecting");
        field.setAccessible(true);
        ThreadLocal<Boolean> guard = (ThreadLocal<Boolean>) field.get(observer);
        guard.set(Boolean.TRUE);
        try {
            assertNull(observer.observe(root, RoiClassLoadObserverTest.class, root,
                    true, true, 6500L), "recursive inspection");
            assertEquals(0, observer.snapshot(6500L).size(), "recursive event count");
        } finally {
            guard.remove();
        }
    }

    private static void testSnapshotsAreImmutableAndMetadataOnly() throws Exception {
        ClassLoader root = RoiClassLoadObserverTest.class.getClassLoader();
        ClassLoader child = new ClassLoader(root) {};
        Class<?> loaded = defineProxyClass(child);
        RoiClassLoadObserver observer = new RoiClassLoadObserver();
        RoiClassLoadObserver.EventSnapshot event = observer.observe(
                child, loaded, root, true, true, 7000L);
        assertNotNull(event, "metadata event");
        assertTrue(event.classDescriptor.startsWith("L"), "class descriptor");
        assertTrue(event.sourceScope.startsWith("loader:"), "loader scope");
        assertTrue(event.methods.size() > 0, "declared method metadata");
        assertFalse(event.toReport().contains("Class@"), "no Class object retention");
        assertFalse(event.toReport().contains("fieldValueRead"), "no field value access");
        boolean immutable = false;
        try { event.methods.add("mutate"); }
        catch (UnsupportedOperationException expected) { immutable = true; }
        assertTrue(immutable, "method metadata immutable");
    }

    private static void testNullAndClear() {
        ClassLoader root = RoiClassLoadObserverTest.class.getClassLoader();
        RoiClassLoadObserver observer = new RoiClassLoadObserver();
        assertNull(observer.observe(root, null, root, true, true, 8000L), "null class");
        observer.clear();
        assertEquals(0, observer.snapshot(8000L).size(), "clear");
    }

    private static Class<?> defineProxyClass(ClassLoader loader) {
        return Proxy.getProxyClass(loader, Runnable.class);
    }

    private static void assertTrue(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }

    private static void assertFalse(boolean value, String label) {
        if (value) throw new AssertionError(label);
    }

    private static void assertNull(Object value, String label) {
        if (value != null) throw new AssertionError(label + ": expected null, got " + value);
    }

    private static void assertNotNull(Object value, String label) {
        if (value == null) throw new AssertionError(label + ": expected non-null");
    }

    private static void assertEquals(long expected, long actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }
}

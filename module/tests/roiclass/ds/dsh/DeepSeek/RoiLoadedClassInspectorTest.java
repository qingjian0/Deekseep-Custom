package ds.dsh.DeepSeek;

import java.util.List;

public final class RoiLoadedClassInspectorTest {
    private static final class Sample implements Runnable {
        static boolean initialized;
        private String secretField;
        public int count;

        static { initialized = true; }

        @Override public void run() {}
        private String transform(int value, String[] labels) { return String.valueOf(value); }
    }

    private RoiLoadedClassInspectorTest() {}

    public static void main(String[] args) {
        testMetadataAndDescriptors();
        testDoesNotInitializeClass();
        testNullIsRejected();
        testSnapshotsAreImmutable();
        System.out.println("ROI loaded class inspector tests passed");
    }

    private static void testMetadataAndDescriptors() {
        RoiLoadedClassInspector.Snapshot snapshot =
                RoiLoadedClassInspector.inspect(Sample.class);
        equal(Sample.class.getName(), snapshot.className, "class name");
        equal("L" + Sample.class.getName().replace('.', '/') + ";",
                snapshot.classDescriptor, "class descriptor");
        check(snapshot.interfaces.contains("java.lang.Runnable"), "interface metadata");
        check(contains(snapshot.methods, "transform(I,[Ljava/lang/String;)"),
                "method parameters must include primitive and array descriptors");
        check(contains(snapshot.fields, "secretField"), "private field name is metadata");
        check(snapshot.loaderScope != null && snapshot.loaderScope.length() > 0,
                "loader scope is retained");
        equal("metadata_only", snapshot.inspectionStatus, "metadata-only status");
    }

    private static void testDoesNotInitializeClass() {
        check(!Sample.initialized, "class must not be initialized before inspection");
        RoiLoadedClassInspector.inspect(Sample.class);
        check(!Sample.initialized, "inspection must not initialize host class");
    }

    private static void testNullIsRejected() {
        RoiLoadedClassInspector.Snapshot snapshot = RoiLoadedClassInspector.inspect(null);
        equal("rejected_null_class", snapshot.inspectionStatus, "null class status");
        check(snapshot.methods.isEmpty(), "null class has no method metadata");
    }

    private static void testSnapshotsAreImmutable() {
        RoiLoadedClassInspector.Snapshot snapshot =
                RoiLoadedClassInspector.inspect(RoiLoadedClassInspectorTest.class);
        try {
            snapshot.methods.add("mutation");
            throw new AssertionError("method snapshot must be immutable");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    private static boolean contains(List<String> values, String fragment) {
        for (String value : values) if (value.contains(fragment)) return true;
        return false;
    }

    private static void equal(Object expected, Object actual, String label) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}

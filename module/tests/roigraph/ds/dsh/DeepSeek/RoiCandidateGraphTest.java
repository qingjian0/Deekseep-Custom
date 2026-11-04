package ds.dsh.DeepSeek;

import java.util.Arrays;
import java.util.Collections;

public final class RoiCandidateGraphTest {
    public static void main(String[] args) {
        testDedupAndDirection();
        testBoundsAndUnresolvedReferences();
        testCyclesAndDisconnectedNodes();
        testSourceScopeIsolation();
        System.out.println("ROI candidate graph tests passed");
    }

    private static void testDedupAndDirection() {
        RoiCandidateGraph.Result result = RoiCandidateGraph.build(Arrays.asList(
                observation("A", "messages", Arrays.asList("B", "B"), Collections.<String>emptyList()),
                observation("A", "Authorization", Arrays.asList("B"), Collections.<String>emptyList()),
                observation("B", "chat/completions", Collections.<String>emptyList(), Arrays.asList("A"))
        ));
        equal(3, result.observed, "observed count");
        equal(2, result.uniqueNodes, "deduplicated nodes");
        equal(1, result.duplicateObservations, "duplicate observations");
        equal(1, result.edgeCount, "duplicate edge merged");
        equal(1, result.connectedComponents, "connected component count");
        check(result.edges.get(0).caller.equals("A"), "invoke edge caller direction");
        check(result.edges.get(0).callee.equals("B"), "invoke edge callee direction");
    }

    private static void testBoundsAndUnresolvedReferences() {
        RoiCandidateGraph.Result result = RoiCandidateGraph.build(Arrays.asList(
                observation("A", "one", Arrays.asList("B", "external"), Collections.<String>emptyList()),
                observation("B", "two", Collections.<String>emptyList(), Arrays.asList("A")),
                observation("C", "three", Collections.<String>emptyList(), Collections.<String>emptyList())
        ), 2, 0);
        equal(2, result.uniqueNodes, "node cap");
        equal(1, result.omittedNodes, "omitted node count");
        equal(1, result.unresolvedReferences, "external reference count");
        equal(0, result.edgeCount, "edge cap");
        equal(1, result.omittedEdges, "duplicate overflow edge counted once");
    }

    private static void testCyclesAndDisconnectedNodes() {
        RoiCandidateGraph.Result result = RoiCandidateGraph.build(Arrays.asList(
                observation("A", "one", Arrays.asList("B"), Collections.<String>emptyList()),
                observation("B", "two", Arrays.asList("A"), Collections.<String>emptyList()),
                observation("C", "three", Collections.<String>emptyList(), Collections.<String>emptyList())
        ));
        equal(2, result.edgeCount, "cycle edges");
        equal(2, result.connectedComponents, "cycle and isolated component");
    }

    private static void testSourceScopeIsolation() {
        RoiCandidateGraph.Result result = RoiCandidateGraph.build(Arrays.asList(
                new RoiCandidateGraph.Observation("loader:one", "Lx/Y;->call()V", "one",
                        Collections.<String>emptyList(), Collections.<String>emptyList()),
                new RoiCandidateGraph.Observation("loader:two", "Lx/Y;->call()V", "two",
                        Collections.<String>emptyList(), Collections.<String>emptyList())
        ));
        equal(2, result.uniqueNodes, "same descriptor in distinct loader scopes must remain separate");
        equal(2, result.connectedComponents, "different source scopes must not be connected");
        check(result.descriptors.contains("loader:one|Lx/Y;->call()V"),
                "first scoped graph identity must be retained");
        check(result.descriptors.contains("loader:two|Lx/Y;->call()V"),
                "second scoped graph identity must be retained");
    }

    private static RoiCandidateGraph.Observation observation(
            String descriptor, String anchor, java.util.List<String> invokes,
            java.util.List<String> callers) {
        return new RoiCandidateGraph.Observation(descriptor, anchor, invokes, callers);
    }

    private static void equal(int expected, int actual, String label) {
        if (expected != actual) throw new AssertionError(label + ": expected "
                + expected + ", got " + actual);
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}

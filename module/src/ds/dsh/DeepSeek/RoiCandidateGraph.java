package ds.dsh.DeepSeek;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic, bounded graph over the candidate methods observed in one DexKit scan scope.
 * Edges are static DEX references only; this class never loads or invokes host code.
 */
final class RoiCandidateGraph {
    static final int DEFAULT_MAX_NODES = 128;
    static final int DEFAULT_MAX_EDGES = 512;

    static final class Observation {
        final String descriptor;
        final String anchor;
        final List<String> invokes;
        final List<String> callers;

        Observation(String descriptor, String anchor, List<String> invokes, List<String> callers) {
            this.descriptor = clean(descriptor);
            this.anchor = clean(anchor);
            this.invokes = immutableCopy(invokes);
            this.callers = immutableCopy(callers);
        }
    }

    static final class Edge {
        final String caller;
        final String callee;

        Edge(String caller, String callee) {
            this.caller = caller;
            this.callee = callee;
        }

        @Override public boolean equals(Object other) {
            if (!(other instanceof Edge)) return false;
            Edge edge = (Edge) other;
            return caller.equals(edge.caller) && callee.equals(edge.callee);
        }

        @Override public int hashCode() {
            return 31 * caller.hashCode() + callee.hashCode();
        }
    }

    static final class Result {
        final int observed;
        final int uniqueNodes;
        final int duplicateObservations;
        final int edgeCount;
        final int unresolvedReferences;
        final int omittedNodes;
        final int omittedEdges;
        final int connectedComponents;
        final List<Edge> edges;
        final Set<String> descriptors;

        Result(int observed, int uniqueNodes, int duplicateObservations, int edgeCount,
               int unresolvedReferences, int omittedNodes, int omittedEdges,
               int connectedComponents, List<Edge> edges, Set<String> descriptors) {
            this.observed = observed;
            this.uniqueNodes = uniqueNodes;
            this.duplicateObservations = duplicateObservations;
            this.edgeCount = edgeCount;
            this.unresolvedReferences = unresolvedReferences;
            this.omittedNodes = omittedNodes;
            this.omittedEdges = omittedEdges;
            this.connectedComponents = connectedComponents;
            this.edges = Collections.unmodifiableList(new ArrayList<Edge>(edges));
            this.descriptors = Collections.unmodifiableSet(
                    new LinkedHashSet<String>(descriptors));
        }
    }

    private static final class Node {
        final String descriptor;
        final Set<String> anchors = new LinkedHashSet<String>();
        final Set<String> invokes = new LinkedHashSet<String>();
        final Set<String> callers = new LinkedHashSet<String>();

        Node(String descriptor) { this.descriptor = descriptor; }
    }

    private RoiCandidateGraph() {}

    static Result build(List<Observation> observations) {
        return build(observations, DEFAULT_MAX_NODES, DEFAULT_MAX_EDGES);
    }

    static Result build(List<Observation> observations, int maxNodes, int maxEdges) {
        if (maxNodes < 1 || maxEdges < 0) {
            throw new IllegalArgumentException("invalid graph limits");
        }
        Map<String, Node> nodes = new LinkedHashMap<String, Node>();
        int observed = observations == null ? 0 : observations.size();
        int duplicates = 0;
        int omittedNodes = 0;
        if (observations != null) {
            for (Observation observation : observations) {
                if (observation == null || observation.descriptor.length() == 0) continue;
                Node node = nodes.get(observation.descriptor);
                if (node == null) {
                    if (nodes.size() >= maxNodes) {
                        omittedNodes++;
                        continue;
                    }
                    node = new Node(observation.descriptor);
                    nodes.put(observation.descriptor, node);
                } else {
                    duplicates++;
                }
                if (observation.anchor.length() > 0) node.anchors.add(observation.anchor);
                node.invokes.addAll(observation.invokes);
                node.callers.addAll(observation.callers);
            }
        }

        Set<Edge> edges = new LinkedHashSet<Edge>();
        Set<Edge> seenEdges = new LinkedHashSet<Edge>();
        int unresolved = 0;
        int omittedEdges = 0;
        for (Node node : nodes.values()) {
            for (String target : node.invokes) {
                if (!nodes.containsKey(target)) {
                    unresolved++;
                    continue;
                }
                if (node.descriptor.equals(target)) continue;
                Edge edge = new Edge(node.descriptor, target);
                if (seenEdges.add(edge)) {
                    if (edges.size() >= maxEdges) omittedEdges++;
                    else edges.add(edge);
                }
            }
            for (String caller : node.callers) {
                if (!nodes.containsKey(caller)) {
                    unresolved++;
                    continue;
                }
                if (node.descriptor.equals(caller)) continue;
                Edge edge = new Edge(caller, node.descriptor);
                if (seenEdges.add(edge)) {
                    if (edges.size() >= maxEdges) omittedEdges++;
                    else edges.add(edge);
                }
            }
        }

        int components = countComponents(nodes, edges);
        return new Result(observed, nodes.size(), duplicates, edges.size(), unresolved,
                omittedNodes, omittedEdges, components, new ArrayList<Edge>(edges),
                nodes.keySet());
    }

    private static int countComponents(Map<String, Node> nodes, Set<Edge> edges) {
        Map<String, Set<String>> adjacent = new LinkedHashMap<String, Set<String>>();
        for (String descriptor : nodes.keySet()) {
            adjacent.put(descriptor, new LinkedHashSet<String>());
        }
        for (Edge edge : edges) {
            adjacent.get(edge.caller).add(edge.callee);
            adjacent.get(edge.callee).add(edge.caller);
        }
        Set<String> visited = new LinkedHashSet<String>();
        int components = 0;
        for (String start : nodes.keySet()) {
            if (visited.contains(start)) continue;
            components++;
            List<String> pending = new ArrayList<String>();
            pending.add(start);
            visited.add(start);
            for (int i = 0; i < pending.size(); i++) {
                for (String next : adjacent.get(pending.get(i))) {
                    if (visited.add(next)) pending.add(next);
                }
            }
        }
        return components;
    }

    private static List<String> immutableCopy(List<String> values) {
        if (values == null || values.isEmpty()) return Collections.emptyList();
        Set<String> unique = new LinkedHashSet<String>();
        for (String value : values) {
            String item = clean(value);
            if (item.length() > 0) unique.add(item);
        }
        return Collections.unmodifiableList(new ArrayList<String>(unique));
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}

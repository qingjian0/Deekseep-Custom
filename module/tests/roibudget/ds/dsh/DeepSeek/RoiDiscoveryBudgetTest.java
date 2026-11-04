package ds.dsh.DeepSeek;

public final class RoiDiscoveryBudgetTest {
    private RoiDiscoveryBudgetTest() {}

    public static void main(String[] args) {
        testQueryLimit();
        testCandidateLimit();
        testSoftDeadline();
        testZeroLimits();
        testFinalQueryCanReturnCandidates();
        testIndependentCounters();
        System.out.println("ROI discovery budget tests passed");
    }

    private static void testQueryLimit() {
        RoiDiscoveryBudget budget = new RoiDiscoveryBudget(2, 10, 1000L);
        check(budget.tryStartQuery(1L), "first query admitted");
        check(budget.tryStartQuery(2L), "second query admitted");
        check(!budget.tryStartQuery(3L), "query over limit rejected");
        equal(2, budget.queriesStarted(), "query count");
        equal("query_limit", budget.stopReason(), "query stop reason");
    }

    private static void testCandidateLimit() {
        RoiDiscoveryBudget budget = new RoiDiscoveryBudget(5, 2, 1000L);
        check(budget.tryAcceptCandidate(1L), "first candidate admitted");
        check(budget.tryAcceptCandidate(2L), "second candidate admitted");
        check(!budget.tryAcceptCandidate(3L), "candidate over limit rejected");
        equal(2, budget.candidatesAccepted(), "candidate count");
        equal("candidate_limit", budget.stopReason(), "candidate stop reason");
    }

    private static void testSoftDeadline() {
        RoiDiscoveryBudget budget = new RoiDiscoveryBudget(5, 5, 10L);
        check(budget.tryStartQuery(9L), "query before deadline admitted");
        check(!budget.tryAcceptCandidate(10L), "candidate at deadline rejected");
        equal("soft_deadline", budget.stopReason(), "deadline stop reason");
        check(budget.isExhausted(11L), "expired budget remains exhausted");
    }

    private static void testZeroLimits() {
        RoiDiscoveryBudget noQueries = new RoiDiscoveryBudget(0, 5, 100L);
        check(!noQueries.tryStartQuery(1L), "zero query limit rejects");
        equal("query_limit", noQueries.stopReason(), "zero query reason");
        RoiDiscoveryBudget noCandidates = new RoiDiscoveryBudget(5, 0, 100L);
        check(noCandidates.tryStartQuery(1L), "query and candidate budgets are independent");
        check(!noCandidates.tryAcceptCandidate(2L), "zero candidate limit rejects");
        equal("candidate_limit", noCandidates.stopReason(), "zero candidate reason");
    }

    private static void testFinalQueryCanReturnCandidates() {
        RoiDiscoveryBudget budget = new RoiDiscoveryBudget(1, 3, 100L);
        check(budget.tryStartQuery(1L), "last allowed query starts");
        check(budget.tryAcceptCandidate(2L),
                "last admitted query results must still be processed");
        check(!budget.canContinue(3L), "no further query may start");
        equal(1, budget.queriesStarted(), "last query count");
        equal(1, budget.candidatesAccepted(), "last query candidate count");
        equal("query_limit", budget.stopReason(), "query limit after result processing");
    }

    private static void testIndependentCounters() {
        RoiDiscoveryBudget budget = new RoiDiscoveryBudget(3, 4, 100L);
        check(budget.tryStartQuery(1L), "query admitted");
        check(budget.tryAcceptCandidate(2L), "candidate admitted");
        check(budget.tryAcceptCandidate(3L), "second candidate admitted");
        equal(1, budget.queriesStarted(), "query counter");
        equal(2, budget.candidatesAccepted(), "candidate counter");
        check(budget.canContinue(4L), "remaining budget usable");
    }

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
    private static void equal(int expected, int actual, String label) {
        if (expected != actual) throw new AssertionError(label + ": " + expected + " != " + actual);
    }
    private static void equal(String expected, String actual, String label) {
        if (!expected.equals(actual)) throw new AssertionError(label + ": " + expected + " != " + actual);
    }
}

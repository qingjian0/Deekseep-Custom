package ds.dsh.DeepSeek;

public final class AgentManualDeliveryPolicyRegressionTest {
    public static void main(String[] args) {
        allowsOnlyExplicitAutoOrApproval();
        refusalWinsOverPending();
        pendingDoesNotOpenAnotherConfirmation();
        requestsConfirmationForNewManualTurn();
        System.out.println("AgentManualDeliveryPolicyRegressionTest OK");
    }

    private static void allowsOnlyExplicitAutoOrApproval() {
        check(AgentManualDeliveryPolicy.decide(true, false, false, false)
                == AgentManualDeliveryPolicy.ALLOW, "acknowledged auto mode");
        check(AgentManualDeliveryPolicy.decide(false, true, false, false)
                == AgentManualDeliveryPolicy.ALLOW, "one-shot approval");
        check(AgentManualDeliveryPolicy.decide(false, false, false, false)
                != AgentManualDeliveryPolicy.ALLOW, "manual default must not auto-send");
    }

    private static void refusalWinsOverPending() {
        check(AgentManualDeliveryPolicy.decide(false, false, true, true)
                == AgentManualDeliveryPolicy.DECLINED, "refusal must win over pending");
        check(AgentManualDeliveryPolicy.decide(true, false, true, true)
                == AgentManualDeliveryPolicy.ALLOW, "explicit auto retains highest priority");
    }

    private static void pendingDoesNotOpenAnotherConfirmation() {
        check(AgentManualDeliveryPolicy.decide(false, false, false, true)
                == AgentManualDeliveryPolicy.WAIT, "pending confirmation");
        check(AgentManualDeliveryPolicy.decide(false, true, true, true)
                == AgentManualDeliveryPolicy.ALLOW, "approval is consumed before refusal");
    }

    private static void requestsConfirmationForNewManualTurn() {
        check(AgentManualDeliveryPolicy.decide(false, false, false, false)
                == AgentManualDeliveryPolicy.SHOW_CONFIRMATION, "new manual turn prompts");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}

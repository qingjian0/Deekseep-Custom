package ds.dsh.DeepSeek;

public final class ReplyReadyPolicyRegressionTest {
    public static void main(String[] args) {
        require(ReplyReadyPolicy.shouldNotify(
                        "WIP", "FINISHED", "ASSISTANT", false),
                "a background assistant WIP -> FINISHED transition must notify");
        require(ReplyReadyPolicy.shouldNotify(
                        "CHECKING", "FINISHED", "ASSISTANT", false),
                "a background checking -> finished transition must notify");
        require(ReplyReadyPolicy.shouldNotify(
                        "INCOMPLETE", "FINISHED", "ASSISTANT", false),
                "a background streamed response finishing must notify");

        require(!ReplyReadyPolicy.shouldNotify(
                        "WIP", "FINISHED", "ASSISTANT", true),
                "foreground completion must not disturb the user");
        require(!ReplyReadyPolicy.shouldNotify(
                        "WIP", "FINISHED", "USER", false),
                "a user-message status update must not notify");
        require(!ReplyReadyPolicy.shouldNotify(
                        "FINISHED", "FINISHED", "ASSISTANT", false),
                "loading an already-finished history item must not notify");
        require(!ReplyReadyPolicy.shouldNotify(
                        null, "FINISHED", "ASSISTANT", false),
                "a completion without a preceding generating state must not notify");
        require(!ReplyReadyPolicy.shouldNotify(
                        "WIP", "CONTENT_FILTER", "ASSISTANT", false),
                "a failed or filtered generation must not masquerade as ready");
        require(ReplyReadyPolicy.shouldNotify(
                        " wip\\t", " finished ", " assistant ", false),
                "ASCII whitespace and case normalization must remain compatible");
        require(!ReplyReadyPolicy.shouldNotify(
                        "\\u00A0WIP\\u00A0", "FINISHED", "ASSISTANT", false),
                "normalization must preserve Java String.trim semantics for non-ASCII whitespace");
        require(ReplyReadyPolicy.isGenerating("  streaming\\t"),
                "generating-state normalization must trim ASCII whitespace");
        require(!ReplyReadyPolicy.isGenerating("\\u00A0WIP\\u00A0"),
                "generating-state normalization must not broaden Java trim semantics");
        require(ReplyReadyPolicy.isCompleted(" success "),
                "completed-state normalization must trim ASCII whitespace");

        System.out.println("Reply-ready notification policy regression passed.");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}

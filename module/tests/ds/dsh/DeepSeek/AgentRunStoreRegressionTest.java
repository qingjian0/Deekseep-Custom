package ds.dsh.DeepSeek;

import java.io.File;
import java.util.Collections;
import java.util.List;

public final class AgentRunStoreRegressionTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void write(File file, String value) throws Exception {
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(file, false)) {
            output.write(value.getBytes("UTF-8"));
            output.flush();
            output.getFD().sync();
        }
    }

    private static String read(File file) throws Exception {
        byte[] bytes = new byte[(int) file.length()];
        try (java.io.FileInputStream input = new java.io.FileInputStream(file)) {
            int offset = 0;
            while (offset < bytes.length) {
                int count = input.read(bytes, offset, bytes.length - offset);
                if (count < 0) break;
                offset += count;
            }
            return new String(bytes, "UTF-8");
        }
    }

    private static HeartbeatToolProtocol.ToolCall call(
            String id, String tool, String scope) {
        return new HeartbeatToolProtocol.ToolCall(
                id, tool, scope, "", "", 0, "", "",
                -1, -1, -1, -1, 0,
                Collections.<HeartbeatToolProtocol.Question>emptyList());
    }

    public static void main(String[] args) throws Exception {
        File ledger = File.createTempFile("deekseep-agent-runs", ".json");
        try {
            AgentRunStore.Store store = new AgentRunStore.Store(ledger);
            HeartbeatToolProtocol.ToolCall first = call(
                    "call-1", HeartbeatToolProtocol.TOOL_ASK_USER, "scope-1");
            check(store.claim(first), "new call id was not durably claimed");
            check(!store.claim(first),
                    "the same call id bypassed durable side-effect deduplication");
            store.waitingUser(first);
            List<AgentRunStore.Record> records = store.snapshot();
            check(records.size() == 1
                            && AgentRunStore.STATE_WAITING_USER.equals(records.get(0).state),
                    "question step was not recorded as waiting for the user");

            String outbox = store.queueResult(
                    first, true, "[[private-result-1]]", "answer received");
            check(outbox.length() > 0 && store.pending().size() == 1,
                    "completed side effect did not enter the durable outbox");

            store.invalidateCacheForTest();
            AgentRunStore.Record restored = store.findOutbox(outbox);
            check(restored != null && "[[private-result-1]]".equals(restored.event),
                    "pending result did not survive a cold ledger reload");
            check(store.markDelivering(outbox, 3),
                    "restored result could not enter delivery state");
            store.waitingForChat(outbox, "return to original chat");
            check(AgentRunStore.STATE_WAITING_CHAT.equals(
                            store.findOutbox(outbox).state),
                    "undelivered result did not remain recoverable");
            store.delivered(outbox);
            check(store.pending().isEmpty()
                            && AgentRunStore.STATE_COMPLETED.equals(
                            store.findOutbox(outbox).state),
                    "successful result was not finalized after delivery");

            HeartbeatToolProtocol.ToolCall failed = call(
                    "call-2", HeartbeatToolProtocol.TOOL_SHELL, "scope-1");
            store.start(failed);
            String failedOutbox = store.queueResult(
                    failed, false, "[[private-result-2]]", "exit 1");
            store.delivered(failedOutbox);
            check(AgentRunStore.STATE_FAILED.equals(
                            store.findOutbox(failedOutbox).state),
                    "a delivered tool failure was incorrectly marked successful");

            HeartbeatToolProtocol.ToolCall cancelled = call(
                    "call-3", HeartbeatToolProtocol.TOOL_READ_FILE, "scope-2");
            store.start(cancelled);
            String cancelledOutbox = store.queueResult(
                    cancelled, true, "[[private-result-3]]", "ready");
            check(store.cancel(cancelledOutbox) && store.pending().isEmpty()
                            && AgentRunStore.STATE_CANCELLED.equals(
                            store.findOutbox(cancelledOutbox).state),
                    "cancelled outbox item remained deliverable");

            check(store.clearFinished() == 3 && store.snapshot().isEmpty(),
                    "finished run history was not cleared deterministically");
            check(!store.claim(first),
                    "clearing visible history deleted the durable execution claim");
            // A failed durable claim must fail closed, otherwise a process restart can
            // forget the claim and repeat a side effect.
            File blockedParent = File.createTempFile("deekseep-agent-blocked", ".tmp");
            try {
                AgentRunStore.Store failingStore = new AgentRunStore.Store(
                        new File(blockedParent, "runs.json"));
                HeartbeatToolProtocol.ToolCall blocked = call(
                        "call-disk-failure", HeartbeatToolProtocol.TOOL_SHELL, "scope-disk");
                check(!failingStore.claim(blocked),
                        "claim must fail when the ledger cannot be persisted");
                failingStore.invalidateCacheForTest();
                check(!failingStore.claim(blocked),
                        "failed claim must not remain as an in-memory-only reservation");
            } finally {
                blockedParent.delete();
            }

            // A malformed or empty existing ledger must never be interpreted as empty history.
            File corruptLedger = File.createTempFile("deekseep-agent-corrupt", ".json");
            try {
                HeartbeatToolProtocol.ToolCall corruptCall = call(
                        "call-corrupt-ledger", HeartbeatToolProtocol.TOOL_SHELL, "scope-corrupt");
                write(corruptLedger, "{broken");
                AgentRunStore.Store corruptStore = new AgentRunStore.Store(corruptLedger);
                check(!corruptStore.claim(corruptCall),
                        "malformed ledger must block a new execution claim");
                corruptStore.start(corruptCall);
                check("".equals(corruptStore.queueResult(
                                corruptCall, true, "[[must-not-overwrite]]", "blocked")),
                        "malformed ledger must block outbox mutation");
                check("{broken".equals(read(corruptLedger)),
                        "mutation replaced the corrupt ledger instead of preserving it");

                corruptStore.invalidateCacheForTest();
                write(corruptLedger, "{\"version\":2,\"records\":[]}");
                check(!corruptStore.claim(corruptCall),
                        "unsupported ledger version must fail closed");
                check("{\"version\":2,\"records\":[]}".equals(read(corruptLedger)),
                        "unsupported ledger was overwritten");

                corruptStore.invalidateCacheForTest();
                write(corruptLedger, "");
                check(!corruptStore.claim(corruptCall),
                        "empty existing ledger must fail closed");

                corruptStore.invalidateCacheForTest();
                write(corruptLedger, "{\"version\":1,\"records\":[]}");
                check(corruptStore.claim(corruptCall),
                        "store did not recover after a valid ledger was restored");
            } finally {
                corruptLedger.delete();
                new File(corruptLedger.getAbsolutePath() + ".tmp").delete();
            }

            System.out.println("Agent run store regression tests passed");
        } finally {
            if (ledger.exists()) ledger.delete();
            File temporary = new File(ledger.getAbsolutePath() + ".tmp");
            if (temporary.exists()) temporary.delete();
        }
    }
}

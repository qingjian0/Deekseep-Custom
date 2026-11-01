package ds.dsh.DeepSeek;

public final class ChatBackupRecordPolicyJavaInteropRegression {
    public static void main(String[] args) {
        check(ChatBackupRecordPolicy.isValidSessionId("session_01"), "valid ASCII id");
        check(!ChatBackupRecordPolicy.isValidSessionId("../manifest"), "path traversal rejected");
        check(!ChatBackupRecordPolicy.isValidSessionId(null), "null rejected");
        System.out.println("Chat backup record Java interop regression passed");
    }

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
}

package ds.dsh.DeepSeek

/** Dependency-free regression entry point, consistent with the runtime's policy tests. */
object ChatBackupRecordPolicyTest {
    @JvmStatic
    fun main(args: Array<String>) {
        check(ChatBackupRecordPolicy.isValidSessionId("a"))
        check(ChatBackupRecordPolicy.isValidSessionId("A09-_"))
        check(ChatBackupRecordPolicy.isValidSessionId("x".repeat(128)))
        check(!ChatBackupRecordPolicy.isValidSessionId(null))
        check(!ChatBackupRecordPolicy.isValidSessionId(""))
        check(!ChatBackupRecordPolicy.isValidSessionId("x".repeat(129)))
        check(!ChatBackupRecordPolicy.isValidSessionId("has space"))
        check(!ChatBackupRecordPolicy.isValidSessionId("a/b"))
        check(!ChatBackupRecordPolicy.isValidSessionId("a\\b"))
        check(!ChatBackupRecordPolicy.isValidSessionId("a.b"))
        check(!ChatBackupRecordPolicy.isValidSessionId("中文"))
        check(!ChatBackupRecordPolicy.isValidSessionId("a\u0000"))
        println("Chat backup record policy tests passed")
    }
}

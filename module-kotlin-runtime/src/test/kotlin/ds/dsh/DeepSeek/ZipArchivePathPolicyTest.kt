package ds.dsh.DeepSeek

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZipArchivePathPolicyTest {
    @Test
    fun acceptsRelativeNestedArchivePaths() {
        assertTrue(ZipArchivePathPolicy.isSafePath("accounts/alice.json"))
        assertTrue(ZipArchivePathPolicy.isSafePath("nested/./account.txt"))
        assertTrue(ZipArchivePathPolicy.isSafePath("account.ds-account"))
    }

    @Test
    fun rejectsAbsoluteDriveNullAndTraversalPaths() {
        listOf("", "/", "/accounts/a.json", "C:/accounts/a.json",
            "C:relative.json", "../a.json", "accounts/../a.json",
            "a\u0000b.json")
            .forEach { assertFalse("unsafe path accepted: $it", ZipArchivePathPolicy.isSafePath(it)) }
    }
}

package ds.dsh.DeepSeek

import android.content.Context
import android.os.Bundle

/** Android-backed log file storage. */
object DshFileLogStore {
    @JvmStatic fun appendPrivate(context: Context?, category: String?, line: String?): Boolean = false
    @JvmStatic fun appendPublic(context: Context?, category: String?, line: String?): Boolean = false
    @JvmStatic fun readPrivateCrashTail(context: Context?, requestedMaxBytes: Int): String = ""
}

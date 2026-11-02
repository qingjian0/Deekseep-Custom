package ds.dsh.DeepSeek

import java.io.BufferedReader

/**
 * Minimal line-reading seam for the legacy SSE parsers.
 *
 * It deliberately delegates framing to BufferedReader.readLine() and does not parse SSE fields.
 * Reader closure is owned by ConversationRelayStreamLifecycle; the Java relay owns the connection.
 */
object ConversationRelaySseLineReader {
    fun interface LineHandler {
        /** Return false to stop reading immediately after this line. */
        fun onLine(line: String): Boolean
    }

    @JvmStatic
    fun readLines(reader: BufferedReader, handler: LineHandler) {
        while (true) {
            val line = reader.readLine() ?: return
            if (!handler.onLine(line)) return
        }
    }
}

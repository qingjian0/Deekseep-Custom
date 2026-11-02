package ds.dsh.DeepSeek

import java.io.BufferedReader

/**
 * Minimal line-reading seam for the legacy SSE parsers.
 *
 * It deliberately delegates framing to BufferedReader.readLine(), does not parse SSE fields,
 * and does not close the reader: the Java provider adapter retains stream ownership.
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

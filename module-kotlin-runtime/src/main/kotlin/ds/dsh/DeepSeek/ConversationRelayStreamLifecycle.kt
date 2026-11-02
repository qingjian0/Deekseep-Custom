package ds.dsh.DeepSeek

import java.io.BufferedReader
import java.io.IOException

/**
 * Owns only the response reader lifecycle for the legacy provider SSE adapters.
 *
 * The Java relay adapter owns HttpURLConnection and disconnects it exactly once in its
 * outer finally block. Keeping connection ownership outside this helper also covers failures
 * while obtaining the response InputStream or constructing the reader.
 */
object ConversationRelayStreamLifecycle {
    @JvmStatic
    fun readLinesAndClose(
        reader: BufferedReader,
        handler: ConversationRelaySseLineReader.LineHandler,
    ) {
        try {
            ConversationRelaySseLineReader.readLines(reader, handler)
        } finally {
            try {
                reader.close()
            } catch (_: IOException) {
                // Preserve the legacy adapter's best-effort reader-close behavior.
            }
        }
    }
}

package ds.dsh.DeepSeek

import java.io.BufferedReader
import java.io.IOException
import java.net.HttpURLConnection

/**
 * Owns the response-side lifecycle for the legacy provider SSE adapters.
 *
 * The Java adapter retains provider decoding and result construction. This seam only guarantees
 * the same cleanup contract for EOF, early termination, read failures, and handler failures.
 */
object ConversationRelayStreamLifecycle {
    @JvmStatic
    fun readLinesAndClose(
        reader: BufferedReader,
        connection: HttpURLConnection,
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
            connection.disconnect()
        }
    }
}
